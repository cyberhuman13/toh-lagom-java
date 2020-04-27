#!/usr/bin/env bash

export TOH_VERSION=$1
if [[ -z ${TOH_VERSION} ]]
then
    export TOH_VERSION='latest'
fi
echo "Using the toh-lagom version ${TOH_VERSION}."

# For the purposes of conciseness, we won't check exit code
# of commands in this script. This is, however, useful, since
# you don't want to begin, for example, creating a cluster if
# schema initialization fails. We leave error handling as an exercise.

echo "Initializing AWS Cassandra schema and ECR repository..."
cd ..; sbt ecr:createRepository initializeSchema; cd ./deployment
repositoryUri=$(aws ecr describe-repositories --repository-names toh-lagom-java \
    | jq -r '.repositories[0].repositoryUri')
echo "AWS ECR repository: ${repositoryUri}"

echo 'Creating AWS Kubernetes cluster...'
yq write k8cluster.yaml metadata.region ${AWS_REGION} > kubernetes.yaml
# Potentially may have to configure specific availability zones, depending on your AWS account.
echo 'availabilityZones:' >> kubernetes.yaml
echo "- ${AWS_REGION}a" >> kubernetes.yaml
echo "- ${AWS_REGION}f" >> kubernetes.yaml
echo '---' >> kubernetes.yaml
yq write k8deployment.yaml spec.template.spec.containers[0].image "${repositoryUri}:${TOH_VERSION}" >> kubernetes.yaml

eksctl create cluster -f kubernetes.yaml
kubectl apply -f k8roles.yaml
rm -rf kubernetes.yaml

# To enable CloudWatch logging, execute this command:
#eksctl utils update-cluster-logging --region=${AWS_REGION} --cluster=toh-lagom

# For a GPU instance type and the Amazon EKS-optimized AMI with GPU support:
#echo 'Applying NVIDIA device plugin for Kubernetes...'
#kubectl apply -f https://raw.githubusercontent.com/NVIDIA/k8s-device-plugin/1.0.0-beta/nvidia-device-plugin.yml
echo 'Successfully created an AWS Kubernetes cluster.'

# Collect the information from the resources created with the Kubernetes cluster.
stackInfo=$(aws cloudformation describe-stacks --stack-name eksctl-toh-lagom-cluster)
subnetsPrivate=$(echo ${stackInfo} \
    | jq -r '.Stacks[0].Outputs | .[] | select(.OutputKey == "SubnetsPrivate") | .OutputValue' \
    | sed 's/,/ /g')
securityGroups=$(echo ${stackInfo} \
    | jq -r '.Stacks[0].Outputs | .[] | select(.OutputKey | endswith("SecurityGroup")) | .OutputValue')

echo 'Creating a database subnet group...'
aws rds create-db-subnet-group \
    --db-subnet-group-name toh-lagom-db-subnets \
    --db-subnet-group-description 'Subnets for a database cluster.' \
    --subnet-ids ${subnetsPrivate}

echo "Retrieving database credentials..."
dbSecret=$(aws secretsmanager get-secret-value --secret-id toh-lagom-postgresql \
    | jq -r '.SecretString')
dbUsername=$(echo ${dbSecret} | jq -r '.username')
dbPassword=$(echo ${dbSecret} | jq -r '.password')

echo "Creating an Aurora database cluster..."
aws rds create-db-cluster \
    --db-cluster-identifier toh-lagom --database-name toh_lagom \
    --master-username ${dbUsername} --master-user-password ${dbPassword} \
    --engine aurora-postgresql --engine-version 10.7 --engine-mode serverless \
    --scaling-configuration MinCapacity=2,MaxCapacity=8,SecondsUntilAutoPause=1000,AutoPause=true \
    --vpc-security-group-ids ${securityGroups} --db-subnet-group-name toh-lagom-db-subnets --enable-http-endpoint

# Since there is no 'aws rds wait' command...
dbStatus=''
until [[ ${dbStatus} == 'available' ]]
do
    sleep 10
    dbStatus=$(aws rds describe-db-clusters --db-cluster-identifier toh-lagom \
        | jq -r '.DBClusters[0].Status')
    echo "Cluster status: ${dbStatus}..."
done
echo "Successfully created an Aurora Serverless cluster."

echo 'Updating the production config with the database data...'
dbEndpoint=$(aws rds describe-db-cluster-endpoints --db-cluster-identifier toh-lagom \
    | jq -r '.DBClusterEndpoints[0].Endpoint')
export POSTGRESQL_URL="jdbc:postgresql://${dbEndpoint}/toh_lagom"
echo "AWS Aurora database: ${POSTGRESQL_URL}"

if [[ -z $(aws ecr describe-images --repository-name toh-lagom --image-ids imageTag=${TOH_VERSION}) ]]
then
    echo "The Docker image with version ${TOH_VERSION} is NOT found."
    echo 'Building the ECR Docker image...'
    cd ..; sbt clean ecr:push; cd ./deployment
    echo 'Pushed the Docker image into AWS ECR.'
else
    echo "The Docker image with version ${TOH_VERSION} is found."
fi

echo 'Starting the Kubernetes service...'
kubectl run toh-lagom \
    --image=${repositoryUri}:${TOH_VERSION} \
    --requests=cpu=500m --expose --port=9000

echo 'Configuring autoscaling...'
minReplicas=2
kubectl autoscale deployment toh-lagom --cpu-percent=50 --min=${minReplicas} --max=3

readyReplicas=0
until [[ ${readyReplicas} == ${minReplicas} ]]
do
    sleep 10
    readyReplicas=$(kubectl get deployment toh-lagom -o json \
        | jq '.status.readyReplicas')
    echo "Ready replicas: ${readyReplicas}..."
done
echo 'The Kubernetes service is up and ready.'

echo 'Exposing the Kubernetes service...'
kubectl expose deployment toh-lagom --type=LoadBalancer --name=toh-lagom-elb
elbEndpoint=$(kubectl get service toh-lagom-elb -o json \
    | jq -r '.status.loadBalancer.ingress[0].hostname')

echo 'Creating an S3 bucket to serve as a static website...'
export LC_CTYPE=C
suffix=$(head /dev/urandom | tr -dc a-z0-9 | head -c 13)
bucket="toh-lagom-${suffix}"
s3endpoint="${bucket}.s3.amazonaws.com"

aws s3 mb "s3://${bucket}" --region ${AWS_REGION}
aws s3api wait bucket-exists --bucket ${bucket}
aws s3 cp ../dist "s3://${bucket}" --recursive --storage-class INTELLIGENT_TIERING

echo 'Creating a CloudFront origin access identity...'
originAccessIdentity=$(aws cloudfront create-cloud-front-origin-access-identity \
    --cloud-front-origin-access-identity-config CallerReference=toh-lagom,Comment=toh-lagom \
    | jq -r '.CloudFrontOriginAccessIdentity.Id')

echo 'Authorizing the CloudFront origin access identity to read from the S3 bucket...'
jq --arg resource "arn:aws:s3:::${bucket}/*" --arg principal "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity ${originAccessIdentity}" \
    '.Statement[0].Resource = $resource | .Statement[0].Principal.AWS = $principal' \
    s3policy.json > policy.json
aws s3api put-bucket-policy --bucket ${bucket} --policy file://policy.json
rm -rf policy.json

echo 'Creating a CloudFront distribution...'
jq --arg s3 ${s3endpoint} --arg elb ${elbEndpoint} --arg oai "origin-access-identity/cloudfront/${originAccessIdentity}" \
    '.Origins.Items[0].DomainName = $s3 | .Origins.Items[1].DomainName = $elb | .Origins.Items[0].S3OriginConfig.OriginAccessIdentity = $oai' \
    cloudfront.json > distribution.json
distributionId=$(aws cloudfront create-distribution --distribution-config file://distribution.json \
    | jq -r '.Distribution.Id')
rm -rf distribution.json

echo 'Waiting for the CloudFront distribution to deploy...'
aws cloudfront wait distribution-deployed --id ${distributionId}
export CF_ENDPOINT=$(aws cloudfront get-distribution --id ${distributionId} \
    | jq -r '.Distribution.DomainName')
echo "Successfully deployed a Cloudfront distribution: ${CF_ENDPOINT}"
