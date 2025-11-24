#!/bin/bash

# Configuration
AWS_REGION="us-east-1"
AWS_ACCOUNT_ID="257394460825"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_NAME="health-proxy"
IMAGE_TAG="latest"

set -e

echo "========================================="
echo "Building and Pushing Health Proxy Image"
echo "========================================="

# Get AWS account ID
echo "Using AWS Account: ${AWS_ACCOUNT_ID}"
echo "Using AWS Region: ${AWS_REGION}"

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region ${AWS_REGION} | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# Create repository if it doesn't exist
echo "Ensuring ECR repository exists..."
aws ecr describe-repositories --repository-names ${IMAGE_NAME} --region ${AWS_REGION} 2>/dev/null || \
    aws ecr create-repository --repository-name ${IMAGE_NAME} --region ${AWS_REGION}

# Build the image
echo "Building Docker image..."
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .

# Tag for ECR
echo "Tagging image for ECR..."
docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}

# Push to ECR
echo "Pushing image to ECR..."
docker push ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}

echo ""
echo "✅ Success! Health proxy image pushed to:"
echo "   ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
echo ""
echo "The health-proxy sidecar is now ready to use in ECS task definitions."
