#!/bin/bash

# Real-time deployment monitoring script

DEPLOYMENT_ID="$1"
API_KEY="sk_b531d9d76c8141bda1c92f2fbe2d80eb36318f0f45754b36"
BASE_URL="http://localhost:8085"

if [ -z "$DEPLOYMENT_ID" ]; then
  echo "Usage: ./monitor-deployment.sh <deployment-id>"
  echo "Getting latest deployment for container 76ae4bb7-34d8-4fba-9be6-0f5210050db4..."
  
  # Get the latest deployment
  DEPLOYMENTS=$(curl -s -X GET $BASE_URL/api/deployments/container/76ae4bb7-34d8-4fba-9be6-0f5210050db4?limit=1 \
    -H "X-API-Key: $API_KEY")
  
  DEPLOYMENT_ID=$(echo $DEPLOYMENTS | jq -r '.[0].deploymentId')
  
  if [ "$DEPLOYMENT_ID" == "null" ] || [ -z "$DEPLOYMENT_ID" ]; then
    echo "No deployments found for this container"
    exit 1
  fi
  
  echo "Found deployment: $DEPLOYMENT_ID"
fi

echo "Monitoring deployment: $DEPLOYMENT_ID"
echo "Press Ctrl+C to stop"
echo ""

# Function to draw progress bar
draw_progress_bar() {
  local progress=$1
  local width=50
  local filled=$((progress * width / 100))
  local empty=$((width - filled))
  
  printf "["
  printf "%${filled}s" | tr ' ' '='
  printf "%${empty}s" | tr ' ' '-'
  printf "] %3d%%\n" $progress
}

# Monitor loop
while true; do
  # Get deployment status
  STATUS=$(curl -s -X GET $BASE_URL/api/deployments/$DEPLOYMENT_ID/status \
    -H "X-API-Key: $API_KEY")
  
  # Get deployment details
  DETAILS=$(curl -s -X GET $BASE_URL/api/deployments/$DEPLOYMENT_ID \
    -H "X-API-Key: $API_KEY")
  
  # Clear screen and move cursor to top
  clear
  
  echo "=== Deployment Monitor ==="
  echo "Deployment ID: $DEPLOYMENT_ID"
  echo "Time: $(date)"
  echo ""
  
  # Extract status info
  COMPLETED=$(echo $STATUS | jq -r '.completed')
  FAILED=$(echo $STATUS | jq -r '.failed')
  PROGRESS=$(echo $STATUS | jq -r '.progressPercentage // 0')
  CURRENT_STEP=$(echo $STATUS | jq -r '.currentStep // "Starting..."')
  
  # Display progress
  echo "Progress: "
  draw_progress_bar $PROGRESS
  echo ""
  echo "Current Step: $CURRENT_STEP"
  echo ""
  
  # Display all steps
  echo "Deployment Steps:"
  echo $DETAILS | jq -r '.steps[]? | "  [\(.status | .[0:1])] \(.stepName) - \(.message // "")"' | while read line; do
    case "$line" in
      *"[C]"*) echo -e "\033[32m$line\033[0m" ;;  # Green for completed
      *"[I]"*) echo -e "\033[33m$line\033[0m" ;;  # Yellow for in progress
      *"[F]"*) echo -e "\033[31m$line\033[0m" ;;  # Red for failed
      *) echo "$line" ;;
    esac
  done
  
  echo ""
  
  # Display metadata if available
  METRICS=$(echo $STATUS | jq -r '.metrics // {}')
  if [ "$METRICS" != "{}" ]; then
    echo "Metrics:"
    echo $METRICS | jq -r 'to_entries[] | "  \(.key): \(.value)"'
    echo ""
  fi
  
  # Check if completed
  if [ "$COMPLETED" == "true" ]; then
    if [ "$FAILED" == "true" ]; then
      echo -e "\033[31m✗ Deployment FAILED\033[0m"
      ERROR_MSG=$(echo $DETAILS | jq -r '.errorMessage // "Unknown error"')
      echo "Error: $ERROR_MSG"
    else
      echo -e "\033[32m✓ Deployment COMPLETED\033[0m"
      DURATION=$(echo $DETAILS | jq -r '.durationMillis // 0')
      echo "Duration: $((DURATION / 1000)) seconds"
    fi
    echo ""
    echo "Monitoring complete. Press Ctrl+C to exit."
    break
  fi
  
  # Wait before next update
  sleep 5
done
