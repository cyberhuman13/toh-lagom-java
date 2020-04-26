#!/usr/bin/env bash

export TOH_VERSION=$1
if [[ -z ${TOH_VERSION} ]]
then
    TOH_VERSION='latest'
fi

repositoryUri=$(aws ecr describe-repositories --repository-names toh-lagom \
    | jq -r '.repositories[0].repositoryUri')
echo "AWS ECR repository: ${repositoryUri}"

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

echo 'Configuring Kube config to toh-lagom...'
aws eks update-kubeconfig --name toh-lagom
kubectl set image deployment.apps/toh-lagom toh-lagom=${repositoryUri}:${TOH_VERSION}
echo "Upgraded the Docker image in the Kubernetes cluster to ${TOH_VERSION}."
