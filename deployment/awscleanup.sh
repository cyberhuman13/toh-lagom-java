#!/usr/bin/env bash

echo 'Disabling the CloudFront distribution...'
distributionId=$(aws cloudfront list-distributions \
    | jq -r '.DistributionList.Items | .[] | select(.Comment == "Tour of Heroes - Lagom Backend") | .Id')
distributionEtag=$(aws cloudfront get-distribution --id ${distributionId} | jq -r '.ETag')
aws cloudfront get-distribution-config --id ${distributionId} \
    | jq -r '.DistributionConfig' | jq '.Enabled=false' > config.json
distribution=$(aws cloudfront update-distribution --id ${distributionId} \
    --if-match ${distributionEtag} --distribution-config file://config.json)
aws cloudfront wait distribution-deployed --id ${distributionId}
rm -rf config.json

echo 'Deleting the CloudFront distribution...'
distributionEtag=$(aws cloudfront get-distribution --id ${distributionId} | jq -r '.ETag')
aws cloudfront delete-distribution --id ${distributionId} --if-match ${distributionEtag}

echo 'Deleting the CloudFront Origin Access Identity...'
originAccessIdentity=$(aws cloudfront list-cloud-front-origin-access-identities \
    | jq -r '.CloudFrontOriginAccessIdentityList.Items | .[] | select(.Comment == "toh-lagom-java") | .Id')
oaiEtag=$(aws cloudfront get-cloud-front-origin-access-identity --id ${originAccessIdentity} \
    | jq -r '.ETag')
aws cloudfront delete-cloud-front-origin-access-identity \
    --id ${originAccessIdentity} --if-match ${oaiEtag}

echo 'Deleting the load balancer...'
vpcId=$(aws cloudformation describe-stacks --stack-name eksctl-toh-lagom-java-cluster \
    | jq -r '.Stacks[0].Outputs | .[] | select(.OutputKey == "VPC") | .OutputValue')
elbInfo=$(aws elb describe-load-balancers \
    | jq -r --arg vpc ${vpcId} '.LoadBalancerDescriptions | .[] | select(.VPCId == $vpc)')
elbName=$(echo ${elbInfo} | jq -r '.LoadBalancerName')
elbSecurityGroup=$(echo ${elbInfo} | jq -r '.SecurityGroups[0]')
dependentSecurityGroup=$(aws ec2 describe-security-groups \
    --filters Name=ip-permission.group-id,Values=${elbSecurityGroup} \
    | jq -r '.SecurityGroups[0].GroupId')
aws elb delete-load-balancer --load-balancer-name ${elbName}
aws ec2 revoke-security-group-ingress --group-id ${dependentSecurityGroup} \
    --ip-permissions IpProtocol=-1,UserIdGroupPairs=[{GroupId=${elbSecurityGroup}}]

echo 'Deleting the Aurora database cluster...'
until [[ -z $(aws rds delete-db-cluster --db-cluster-identifier toh-lagom-java --skip-final-snapshot) ]]
do
    sleep 10
done
aws rds delete-db-subnet-group --db-subnet-group-name toh-lagom-java-db-subnets

echo 'Deleting the Kubernetes cluster...'
aws ec2 delete-security-group --group-id ${elbSecurityGroup}
eksctl delete cluster --region=${AWS_REGION} --name=toh-lagom-java --wait
