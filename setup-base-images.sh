#!/bin/bash
# Setup Base Images in Private ECR
# This script pulls common base images and pushes them to your private ECR
# Run this ONCE to populate your ECR with base images

set -e

AWS_REGION="us-east-1"
AWS_ACCOUNT_ID="257394460825"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY

# Function to create repository if it doesn't exist
create_repo_if_not_exists() {
    local repo_name=$1
    if ! aws ecr describe-repositories --repository-names "$repo_name" --region $AWS_REGION 2>/dev/null; then
        echo "Creating repository: $repo_name"
        aws ecr create-repository \
            --repository-name "$repo_name" \
            --region $AWS_REGION \
            --image-scanning-configuration scanOnPush=true \
            --encryption-configuration encryptionType=AES256 \
            --lifecycle-policy-text '{
                "rules": [{
                    "rulePriority": 1,
                    "description": "Keep last 10 images",
                    "selection": {
                        "tagStatus": "any",
                        "countType": "imageCountMoreThan",
                        "countNumber": 10
                    },
                    "action": {"type": "expire"}
                }]
            }'
        echo "Repository created: $repo_name"
    else
        echo "Repository already exists: $repo_name"
    fi
}

# Function to pull, tag, and push image
push_image_to_ecr() {
    local docker_hub_image=$1
    local ecr_repo=$2
    local tag=${3:-latest}

    echo "Processing: $docker_hub_image -> $ecr_repo:$tag"

    # Create repository
    create_repo_if_not_exists "$ecr_repo"

    # Pull from Docker Hub
    echo "Pulling from Docker Hub: $docker_hub_image"
    docker pull $docker_hub_image

    # Tag for ECR
    docker tag $docker_hub_image $ECR_REGISTRY/$ecr_repo:$tag

    # Push to ECR
    echo "Pushing to ECR: $ECR_REGISTRY/$ecr_repo:$tag"
    docker push $ECR_REGISTRY/$ecr_repo:$tag

    # Clean up local image
    docker rmi $docker_hub_image
    docker rmi $ECR_REGISTRY/$ecr_repo:$tag

    echo "Completed: $ecr_repo:$tag"
    echo "---"
}

# Base images for different languages
echo "Starting base image setup..."
echo "This will take 10-15 minutes..."
echo ""

# Node.js
push_image_to_ecr "node:18-alpine" "base-images/node" "18-alpine"

# Python
push_image_to_ecr "python:3.11-slim" "base-images/python" "3.11-slim"

# Java
push_image_to_ecr "eclipse-temurin:17-jdk-alpine" "base-images/eclipse-temurin" "17-jdk-alpine"
push_image_to_ecr "eclipse-temurin:17-jre-alpine" "base-images/eclipse-temurin" "17-jre-alpine"

# Go
push_image_to_ecr "golang:1.21-alpine" "base-images/golang" "1.21-alpine"
push_image_to_ecr "alpine:latest" "base-images/alpine" "latest"

# PHP
push_image_to_ecr "php:8.2-fpm-alpine" "base-images/php" "8.2-fpm-alpine"
push_image_to_ecr "composer:latest" "base-images/composer" "latest"

# Ruby
push_image_to_ecr "ruby:3.2-alpine" "base-images/ruby" "3.2-alpine"

# .NET
push_image_to_ecr "mcr.microsoft.com/dotnet/sdk:7.0-alpine" "base-images/dotnet-sdk" "7.0-alpine"
push_image_to_ecr "mcr.microsoft.com/dotnet/aspnet:7.0-alpine" "base-images/dotnet-aspnet" "7.0-alpine"

# Nginx (for static sites)
push_image_to_ecr "nginx:alpine" "base-images/nginx" "alpine"

echo ""
echo "✅ Base images setup complete!"
echo ""
echo "Cost estimation:"
echo "- Total storage: ~2-3 GB"
echo "- Monthly cost: ~$0.20 - $0.30"
echo ""
echo "Benefits:"
echo "- No Docker Hub rate limits"
echo "- Faster builds (images in same region)"
echo "- Full security control with image scanning"
echo "- Images are scanned on push automatically"
