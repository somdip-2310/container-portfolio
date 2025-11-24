# Health Proxy Deployment Guide

## Overview

The health-proxy is a **one-time deployment** sidecar container that gets automatically attached to all user container deployments.

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     ONE-TIME DEPLOYMENT                      │
│                                                              │
│  GitHub Actions → Build health-proxy → Push to ECR          │
│                                                              │
│  Result: health-proxy:latest image in ECR                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              EVERY USER CONTAINER DEPLOYMENT                 │
│                                                              │
│  User deploys container → EcsService.java creates task      │
│  definition with TWO containers:                             │
│    1. User's app container                                   │
│    2. Health-proxy sidecar (pulls pre-built image from ECR) │
│                                                              │
│  ✅ No rebuild needed!                                       │
└─────────────────────────────────────────────────────────────┘
```

## Initial Deployment (ONE-TIME)

### Option 1: Using GitHub Actions (Recommended)

1. Go to your GitHub repository
2. Navigate to **Actions** tab
3. Select **"Deploy Health Proxy (One-time)"** workflow
4. Click **"Run workflow"** button
5. Wait for the build to complete (~2-3 minutes)

### Option 2: Manual Deployment via CLI

```bash
# Navigate to health-proxy directory
cd health-proxy

# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  257394460825.dkr.ecr.us-east-1.amazonaws.com

# Create repository (if it doesn't exist)
aws ecr create-repository \
  --repository-name health-proxy \
  --region us-east-1 \
  --image-scanning-configuration scanOnPush=true || true

# Build and push
docker build -t health-proxy:latest .
docker tag health-proxy:latest \
  257394460825.dkr.ecr.us-east-1.amazonaws.com/health-proxy:latest
docker push 257394460825.dkr.ecr.us-east-1.amazonaws.com/health-proxy:latest
```

## Verification

After deployment, verify the image exists in ECR:

```bash
aws ecr describe-images \
  --repository-name health-proxy \
  --region us-east-1
```

## How It Works After Deployment

1. **User creates a container** via the platform UI
2. **EcsService.java automatically**:
   - Creates a task definition with the user's app container
   - Adds the health-proxy sidecar container (references pre-built image)
   - Configures environment variables (USER_APP_PORT, USER_APP_HOST)
3. **ECS launches the task** with both containers
4. **Health-proxy listens on port 9090** and proxies health checks to the user's app
5. **ALB health checks** query `health-proxy:9090/health` instead of the user's app

## When to Rebuild

You only need to rebuild and redeploy the health-proxy if:

- ✅ You add new health check paths to the HEALTH_PATHS array
- ✅ You change the health check logic (timeout, retry behavior, etc.)
- ✅ You update the Node.js base image version
- ✅ You add new features to the health-proxy

You **DO NOT** need to rebuild when:

- ❌ A user deploys a new container
- ❌ You modify the main platform code (ContainerService.java, EcsService.java)
- ❌ You change resource limits or other platform settings
- ❌ You update the main CI/CD pipeline

## Resource Impact

Each health-proxy sidecar consumes:
- **64 CPU units** (0.064 vCPU)
- **128 MB memory**
- **~10 MB disk space** (Node.js Alpine image + server.js)

These resources are automatically reserved from the user's container allocation in EcsService.java.

## Troubleshooting

### Image not found error
```
Error: Failed to pull image "257394460825.dkr.ecr.us-east-1.amazonaws.com/health-proxy:latest"
```

**Solution**: Run the one-time deployment workflow to push the image to ECR.

### Health checks still failing
```
Target health check failed: Connection refused
```

**Solution**:
1. Check CloudWatch logs for the health-proxy container
2. Verify USER_APP_PORT environment variable matches the user's app port
3. Ensure the user's app is actually running and listening on the configured port
