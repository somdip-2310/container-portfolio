#!/bin/bash

# AWS Deployment Debugging Script
# This helps debug issues with ECS deployment

CONTAINER_ID="76ae4bb7-34d8-4fba-9be6-0f5210050db4"
CONTAINER_NAME="nginx-test"  # Update this based on your container name
CLUSTER="somdip-dev-cluster"
REGION="us-east-1"

echo "=== AWS Deployment Debug ==="
echo "Container ID: $CONTAINER_ID"
echo "Cluster: $CLUSTER"
echo ""

# Step 1: Check if service exists
echo "1. Checking ECS Service..."
SERVICE_NAME="service-$CONTAINER_ID"
SERVICE_INFO=$(aws ecs describe-services --cluster $CLUSTER --services $SERVICE_NAME --region $REGION 2>/dev/null)

if [ $? -eq 0 ]; then
  SERVICE_STATUS=$(echo $SERVICE_INFO | jq -r '.services[0].status')
  DESIRED_COUNT=$(echo $SERVICE_INFO | jq -r '.services[0].desiredCount')
  RUNNING_COUNT=$(echo $SERVICE_INFO | jq -r '.services[0].runningCount')
  PENDING_COUNT=$(echo $SERVICE_INFO | jq -r '.services[0].pendingCount')
  
  echo "✓ Service found: $SERVICE_NAME"
  echo "  Status: $SERVICE_STATUS"
  echo "  Desired: $DESIRED_COUNT, Running: $RUNNING_COUNT, Pending: $PENDING_COUNT"
  
  # Get recent events
  echo ""
  echo "  Recent events:"
  echo $SERVICE_INFO | jq -r '.services[0].events[0:5][] | "    \(.createdAt | split("T")[0]) - \(.message)"'
else
  echo "✗ Service not found: $SERVICE_NAME"
fi

# Step 2: Check running tasks
echo -e "\n2. Checking Running Tasks..."
TASKS=$(aws ecs list-tasks --cluster $CLUSTER --service-name $SERVICE_NAME --desired-status RUNNING --region $REGION 2>/dev/null)

if [ $? -eq 0 ] && [ "$(echo $TASKS | jq -r '.taskArns | length')" -gt 0 ]; then
  TASK_ARNS=$(echo $TASKS | jq -r '.taskArns[]')
  
  for TASK_ARN in $TASK_ARNS; do
    echo "  Task: $(basename $TASK_ARN)"
    
    # Get task details
    TASK_DETAILS=$(aws ecs describe-tasks --cluster $CLUSTER --tasks $TASK_ARN --region $REGION)
    TASK_STATUS=$(echo $TASK_DETAILS | jq -r '.tasks[0].lastStatus')
    HEALTH_STATUS=$(echo $TASK_DETAILS | jq -r '.tasks[0].healthStatus // "UNKNOWN"')
    
    echo "    Status: $TASK_STATUS, Health: $HEALTH_STATUS"
    
    # Get task IP
    TASK_IP=$(echo $TASK_DETAILS | jq -r '.tasks[0].attachments[0].details[] | select(.name=="privateIPv4Address") | .value' 2>/dev/null)
    if [ ! -z "$TASK_IP" ]; then
      echo "    Private IP: $TASK_IP"
    fi
  done
else
  echo "  No running tasks found"
fi

# Step 3: Check task definition
echo -e "\n3. Checking Task Definition..."
TASK_DEF_ARN=$(echo $SERVICE_INFO | jq -r '.services[0].taskDefinition' 2>/dev/null)

if [ ! -z "$TASK_DEF_ARN" ] && [ "$TASK_DEF_ARN" != "null" ]; then
  echo "  Task Definition: $(basename $TASK_DEF_ARN)"
  
  # Get container definition
  TASK_DEF=$(aws ecs describe-task-definition --task-definition $TASK_DEF_ARN --region $REGION)
  CONTAINER_DEF=$(echo $TASK_DEF | jq -r '.taskDefinition.containerDefinitions[0]')
  
  IMAGE=$(echo $CONTAINER_DEF | jq -r '.image')
  CPU=$(echo $CONTAINER_DEF | jq -r '.cpu // "N/A"')
  MEMORY=$(echo $CONTAINER_DEF | jq -r '.memory // "N/A"')
  PORT=$(echo $CONTAINER_DEF | jq -r '.portMappings[0].containerPort // "N/A"')
  
  echo "    Image: $IMAGE"
  echo "    CPU: $CPU, Memory: $MEMORY MB"
  echo "    Port: $PORT"
fi

# Step 4: Check target group health
echo -e "\n4. Checking Target Group Health..."
# Get target group ARN from your configuration
TARGET_GROUP_ARN="arn:aws:elasticloadbalancing:us-east-1:257394460825:targetgroup/container-platform-tg/*"

# Try to find the actual target group
TARGET_GROUPS=$(aws elbv2 describe-target-groups --region $REGION --query "TargetGroups[?contains(TargetGroupName, 'container-platform')]" 2>/dev/null)

if [ $? -eq 0 ] && [ "$(echo $TARGET_GROUPS | jq -r '. | length')" -gt 0 ]; then
  TG_ARN=$(echo $TARGET_GROUPS | jq -r '.[0].TargetGroupArn')
  TG_NAME=$(echo $TARGET_GROUPS | jq -r '.[0].TargetGroupName')
  
  echo "  Target Group: $TG_NAME"
  
  # Get target health
  HEALTH=$(aws elbv2 describe-target-health --target-group-arn $TG_ARN --region $REGION 2>/dev/null)
  
  if [ $? -eq 0 ]; then
    echo $HEALTH | jq -r '.TargetHealthDescriptions[] | "    Target: \(.Target.Id):\(.Target.Port) - \(.TargetHealth.State)"'
  fi
else
  echo "  No target group found"
fi

# Step 5: Check CloudWatch logs
echo -e "\n5. Checking Recent Logs..."
LOG_GROUP="/ecs/user-containers"
LOG_STREAM_PREFIX="$CONTAINER_ID"

# Get recent log streams
STREAMS=$(aws logs describe-log-streams --log-group-name $LOG_GROUP --log-stream-name-prefix $LOG_STREAM_PREFIX --order-by LastEventTime --descending --limit 1 --region $REGION 2>/dev/null)

if [ $? -eq 0 ] && [ "$(echo $STREAMS | jq -r '.logStreams | length')" -gt 0 ]; then
  STREAM_NAME=$(echo $STREAMS | jq -r '.logStreams[0].logStreamName')
  echo "  Log Stream: $STREAM_NAME"
  echo "  Recent logs:"
  
  # Get last 10 log events
  LOGS=$(aws logs get-log-events --log-group-name $LOG_GROUP --log-stream-name $STREAM_NAME --limit 10 --region $REGION 2>/dev/null)
  
  if [ $? -eq 0 ]; then
    echo $LOGS | jq -r '.events[] | "    \(.timestamp | . / 1000 | strftime("%Y-%m-%d %H:%M:%S")) - \(.message | gsub("\n"; " "))"'
  fi
else
  echo "  No log streams found"
fi

echo -e "\n=== Debug Summary ==="
echo "If deployment is failing, check:"
echo "1. AWS credentials and permissions"
echo "2. Target group ARN in application.properties"
echo "3. Security group allows traffic on container port"
echo "4. Container image is accessible"
echo "5. Health check endpoint returns 200"
echo ""
echo "To view real-time service events:"
echo "watch -n 5 'aws ecs describe-services --cluster $CLUSTER --services $SERVICE_NAME --region $REGION | jq \".services[0].events[0:5]\""'
