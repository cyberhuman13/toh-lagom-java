#!/usr/bin/env bash

export TOH_VERSION=$1
if [[ -z ${TOH_VERSION} ]]
then
    echo 'Must provide a version.'
    exit 1
else
    echo "Using the toh-lagom-java version ${TOH_VERSION}."
fi

echo "Retrieving AWS Cassandra credentials..."
cassandraSecret=$(aws secretsmanager get-secret-value --secret-id toh-lagom-cassandra \
    | jq -r '.SecretString')
export AWS_MCS_USERNAME=$(echo ${cassandraSecret} | jq -r '.username')
export AWS_MCS_PASSWORD=$(echo ${cassandraSecret} | jq -r '.password')

echo "Retrieving Aurora database credentials..."
dbSecret=$(aws secretsmanager get-secret-value --secret-id toh-lagom-postgresql \
    | jq -r '.SecretString')
export POSTGRESQL_USERNAME=$(echo ${dbSecret} | jq -r '.username')
export POSTGRESQL_PASSWORD=$(echo ${dbSecret} | jq -r '.password')

echo "Initializing AWS Cassandra schema and ECR repository..."
cd ..; sbt ecr:createRepository initializeSchema; cd ./deployment
export REPOSITORY_URI=$(aws ecr describe-repositories --repository-names toh-lagom-java \
    | jq -r '.repositories[0].repositoryUri')
echo "AWS ECR repository: ${REPOSITORY_URI}"

if [[ -z $(aws ecr describe-images --repository-name toh-lagom-java --image-ids imageTag=${TOH_VERSION}) ]]
then
    echo "The Docker image with tag ${TOH_VERSION} is NOT found."
    echo 'Building the ECR Docker image...'
    cd ..; sbt clean ecr:push; cd ./deployment
    echo 'Pushed the Docker image into AWS ECR.'
else
    echo "The Docker image with tag ${TOH_VERSION} is found."
fi

echo 'Creating AWS Kubernetes cluster...'
envsubst < k8cluster.yaml | eksctl create cluster -f -
kubectl apply -f k8roles.yaml

# To enable CloudWatch logging, execute this command:
#eksctl utils update-cluster-logging --region=${AWS_REGION} --cluster=toh-lagom-java

# For a GPU instance type and the Amazon EKS-optimized AMI with GPU support:
#echo 'Applying NVIDIA device plugin for Kubernetes...'
#kubectl apply -f https://raw.githubusercontent.com/NVIDIA/k8s-device-plugin/1.0.0-beta/nvidia-device-plugin.yml
echo 'Successfully created an AWS Kubernetes cluster.'

# Collect the information from the resources created with the Kubernetes cluster.
stackInfo=$(aws cloudformation describe-stacks --stack-name eksctl-toh-lagom-java-cluster)
subnetsPrivate=$(echo ${stackInfo} \
    | jq -r '.Stacks[0].Outputs | .[] | select(.OutputKey == "SubnetsPrivate") | .OutputValue' \
    | sed 's/,/ /g')
securityGroups=$(echo ${stackInfo} \
    | jq -r '.Stacks[0].Outputs | .[] | select(.OutputKey | endswith("SecurityGroup")) | .OutputValue')

echo 'Creating a database subnet group...'
dbSubnets=$(aws rds create-db-subnet-group \
    --db-subnet-group-name toh-lagom-java-db-subnets \
    --db-subnet-group-description 'Subnets for a database cluster.' \
    --subnet-ids ${subnetsPrivate})

echo "Creating an Aurora database cluster..."
dbCluster=$(aws rds create-db-cluster \
    --db-cluster-identifier toh-lagom-java --database-name toh_lagom_java \
    --engine aurora-postgresql --engine-version 10.7 --engine-mode serverless \
    --master-username ${POSTGRESQL_USERNAME} --master-user-password ${POSTGRESQL_PASSWORD} \
    --scaling-configuration MinCapacity=2,MaxCapacity=8,SecondsUntilAutoPause=1000,AutoPause=true \
    --vpc-security-group-ids ${securityGroups} --db-subnet-group-name toh-lagom-java-db-subnets --enable-http-endpoint)

dbStatus=''
until [[ ${dbStatus} == 'available' ]]
do
    sleep 30
    dbStatus=$(aws rds describe-db-clusters --db-cluster-identifier toh-lagom-java \
        | jq -r '.DBClusters[0].Status')
    echo "Cluster status: ${dbStatus}..."
done
echo "Successfully created an Aurora Serverless cluster."

echo 'Updating the production config with the database data...'
dbEndpoint=$(aws rds describe-db-cluster-endpoints --db-cluster-identifier toh-lagom-java \
    | jq -r '.DBClusterEndpoints[0].Endpoint')
export POSTGRESQL_URL="jdbc:postgresql://${dbEndpoint}/toh_lagom_java"
echo "AWS Aurora database: ${POSTGRESQL_URL}"

echo 'Starting the Kubernetes service...'
envsubst < k8deployment.yaml | kubectl apply -f -
kubectl autoscale deployment toh-lagom-java \
    --cpu-percent=50 --min=2 --max=3

readyReplicas=0
until [[ ${readyReplicas} == 2 ]]
do
    sleep 10
    readyReplicas=$(kubectl get deployment toh-lagom-java -o json \
        | jq '.status.readyReplicas')
    echo "Ready replicas: ${readyReplicas}..."
done
echo 'The Kubernetes service is up and ready.'

echo 'Exposing the Kubernetes service...'
kubectl expose deployment toh-lagom-java --type=LoadBalancer --name=toh-lagom-java-elb
export ELB_ENDPOINT=$(kubectl get service toh-lagom-java-elb -o json \
    | jq -r '.status.loadBalancer.ingress[0].hostname')

until [[ ${ELB_ENDPOINT} != 'null' ]]
do
    sleep 5
    export ELB_ENDPOINT=$(kubectl get service toh-lagom-java-elb -o json \
        | jq -r '.status.loadBalancer.ingress[0].hostname')
done

bucket=$2
if [[ -z ${TOH_VERSION} ]]
then
    export LC_CTYPE=C
    suffix=$(head /dev/urandom | tr -dc a-z0-9 | head -c 13)
    bucket="toh-lagom-java-${suffix}"

    echo "Creating S3 bucket ${bucket} to serve as a static website..."
    aws s3 mb "s3://${bucket}" --region ${AWS_REGION}
    aws s3api wait bucket-exists --bucket ${bucket}
    aws s3 cp ../dist "s3://${bucket}" --recursive --storage-class INTELLIGENT_TIERING
else
    echo "Using S3 bucket ${bucket} to serve as a static website..."
fi

echo 'Creating a CloudFront origin access identity...'
originAccessIdentity=$(aws cloudfront create-cloud-front-origin-access-identity \
    --cloud-front-origin-access-identity-config CallerReference=toh-lagom-java,Comment=toh-lagom-java \
    | jq -r '.CloudFrontOriginAccessIdentity.Id')
sleep 10

echo 'Authorizing the CloudFront origin access identity to read from the S3 bucket...'
export S3_POLICY_RESOURCE="arn:aws:s3:::${bucket}/*"
export S3_POLICY_PRINCIPAL="arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity ${originAccessIdentity}"
envsubst < s3policy.json > policy.json
aws s3api put-bucket-policy --bucket ${bucket} --policy file://policy.json
rm -rf policy.json

echo 'Creating a CloudFront distribution...'
export S3_ENDPOINT="${bucket}.s3.amazonaws.com"
export ORIGIN_ACCESS_IDENTITY="origin-access-identity/cloudfront/${originAccessIdentity}"
envsubst < cloudfront.json > distribution.json
distributionId=$(aws cloudfront create-distribution --distribution-config file://distribution.json \
    | jq -r '.Distribution.Id')
rm -rf distribution.json

echo 'Waiting for the CloudFront distribution to deploy...'
aws cloudfront wait distribution-deployed --id ${distributionId}
export CF_ENDPOINT=$(aws cloudfront get-distribution --id ${distributionId} \
    | jq -r '.Distribution.DomainName')
echo "Successfully deployed a Cloudfront distribution: ${CF_ENDPOINT}"

unset AWS_MCS_USERNAME
unset AWS_MCS_PASSWORD
unset POSTGRESQL_USERNAME
unset POSTGRESQL_PASSWORD
unset cassandraSecret
unset dbSecret
