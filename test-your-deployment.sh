#!/bin/bash

# Container Platform Deployment Test Script
# Using your specific container and credentials

BASE_URL="http://localhost:8085"
API_KEY="sk_b531d9d76c8141bda1c92f2fbe2d80eb36318f0f45754b36"
CONTAINER_ID="76ae4bb7-34d8-4fba-9be6-0f5210050db4"

echo "=== Container Platform Deployment Test ==="
echo "Container ID: $CONTAINER_ID"
echo "API Key: ${API_KEY:0:20}..."

# Step 1: Get container details using API key
echo -e "\n1. Getting container details..."
CONTAINER_RESPONSE=$(curl -s -X GET $BASE_URL/api/containers/$CONTAINER_ID \
  -H "X-API-Key: $API_KEY")

if [ $? -ne 0 ]; then
  echo "Failed to connect to API. Is the application running?"
  exit 1
fi

CONTAINER_NAME=$(echo $CONTAINER_RESPONSE | jq -r '.name')
CONTAINER_STATUS=$(echo $CONTAINER_RESPONSE | jq -r '.status')
CONTAINER_URL=$(echo $CONTAINER_RESPONSE | jq -r '.url')

echo "✓ Container found"
echo "  Name: $CONTAINER_NAME"
echo "  Status: $CONTAINER_STATUS"
echo "  URL: $CONTAINER_URL"

# Step 2: Deploy the container
echo -e "\n2. Deploying container..."
DEPLOY_RESPONSE=$(curl -s -X POST $BASE_URL/api/containers/$CONTAINER_ID/deploy \
  -H "X-API-Key: $API_KEY")

DEPLOYMENT_ID=$(echo $DEPLOY_RESPONSE | jq -r '.deployment.deploymentId')

if [ "$DEPLOYMENT_ID" == "null" ] || [ -z "$DEPLOYMENT_ID" ]; then
  echo "Deployment failed. Response:"
  echo $DEPLOY_RESPONSE | jq '.'
  echo ""
  echo "Common issues:"
  echo "- Container might already be running (check status above)"
  echo "- AWS credentials not configured"
  echo "- Target group ARN not set in application.properties"
  exit 1
fi

echo "✓ Deployment started"
echo "  Deployment ID: $DEPLOYMENT_ID"

# Step 3: Monitor deployment progress
echo -e "\n3. Monitoring deployment progress..."
echo "This may take 2-5 minutes..."

for i in {1..60}; do
  STATUS_RESPONSE=$(curl -s -X GET $BASE_URL/api/deployments/$DEPLOYMENT_ID/status \
    -H "X-API-Key: $API_KEY")
  
  if [ $? -ne 0 ]; then
    echo -e "\nFailed to get deployment status"
    break
  fi
  
  COMPLETED=$(echo $STATUS_RESPONSE | jq -r '.completed')
  FAILED=$(echo $STATUS_RESPONSE | jq -r '.failed')
  PROGRESS=$(echo $STATUS_RESPONSE | jq -r '.progressPercentage')
  CURRENT_STEP=$(echo $STATUS_RESPONSE | jq -r '.currentStep')
  
  printf "\r  Progress: %3.0f%% - Step: %-30s" "$PROGRESS" "$CURRENT_STEP"
  
  if [ "$COMPLETED" == "true" ]; then
    if [ "$FAILED" == "true" ]; then
      echo -e "\n✗ Deployment failed!"
      echo "Check logs for details:"
      echo "  tail -f logs/application.log | grep $DEPLOYMENT_ID"
      break
    else
      echo -e "\n✓ Deployment completed successfully!"
      break
    fi
  fi
  
  sleep 10
done

# Step 4: Get detailed deployment info
echo -e "\n4. Getting deployment details..."
DEPLOYMENT_DETAILS=$(curl -s -X GET $BASE_URL/api/deployments/$DEPLOYMENT_ID \
  -H "X-API-Key: $API_KEY")

echo "Deployment steps:"
echo $DEPLOYMENT_DETAILS | jq -r '.steps[] | "  - \(.stepName): \(.status)"'

# Step 5: Check container health
echo -e "\n5. Checking container health..."
HEALTH_RESPONSE=$(curl -s -X GET $BASE_URL/api/deployments/container/$CONTAINER_ID/health \
  -H "X-API-Key: $API_KEY")

HEALTHY=$(echo $HEALTH_RESPONSE | jq -r '.healthy')
LAST_CHECK=$(echo $HEALTH_RESPONSE | jq -r '.lastCheckTime')
echo "  Health Status: $HEALTHY"
echo "  Last Check: $LAST_CHECK"

# Step 6: Get updated container info
echo -e "\n6. Getting updated container info..."
UPDATED_CONTAINER=$(curl -s -X GET $BASE_URL/api/containers/$CONTAINER_ID \
  -H "X-API-Key: $API_KEY")

NEW_STATUS=$(echo $UPDATED_CONTAINER | jq -r '.status')
LAST_DEPLOYED=$(echo $UPDATED_CONTAINER | jq -r '.lastDeployedAt')

echo "  Container Status: $NEW_STATUS"
echo "  Last Deployed: $LAST_DEPLOYED"

# Step 7: Test the deployed container (if healthy)
if [ "$NEW_STATUS" == "RUNNING" ] && [ "$HEALTHY" == "true" ]; then
  echo -e "\n7. Testing deployed container..."
  echo "  Your container should be accessible at: $CONTAINER_URL"
  
  # Test with curl (might fail if DNS not propagated yet)
  echo "  Testing health endpoint..."
  HEALTH_TEST=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "$CONTAINER_URL/health" || echo "TIMEOUT")
  echo "  Health check response: $HEALTH_TEST"
fi

echo -e "\n=== Deployment Summary ==="
echo "Container ID: $CONTAINER_ID"
echo "Container Name: $CONTAINER_NAME"
echo "Deployment ID: $DEPLOYMENT_ID"
echo "Final Status: $NEW_STATUS"
echo "Container URL: $CONTAINER_URL"

if [ "$NEW_STATUS" == "RUNNING" ]; then
  echo -e "\n✅ SUCCESS! Your container is deployed and running."
  echo "Access it at: $CONTAINER_URL"
else
  echo -e "\n⚠️  Deployment completed but container is not running."
  echo "Check application logs for details."
fi

echo -e "\nUseful commands:"
echo "- View logs: curl -X GET $BASE_URL/api/containers/$CONTAINER_ID/logs -H \"X-API-Key: $API_KEY\""
echo "- Stop container: curl -X POST $BASE_URL/api/containers/$CONTAINER_ID/stop -H \"X-API-Key: $API_KEY\""
echo "- Delete container: curl -X DELETE $BASE_URL/api/containers/$CONTAINER_ID -H \"X-API-Key: $API_KEY\""
