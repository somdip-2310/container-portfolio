#!/bin/bash

# Script to add Zoho MX records to Route 53
# Usage: ./add-mx-records.sh <HOSTED_ZONE_ID>

set -e

ZONE_ID=${1:-""}

if [ -z "$ZONE_ID" ]; then
    echo "❌ Error: Please provide your Route 53 Hosted Zone ID"
    echo ""
    echo "Usage: $0 <HOSTED_ZONE_ID>"
    echo ""
    echo "To find your Hosted Zone ID, run:"
    echo "  aws route53 list-hosted-zones | grep snapdeploy"
    echo ""
    echo "Or get it from AWS Console:"
    echo "  https://console.aws.amazon.com/route53/v2/hostedzones"
    exit 1
fi

echo "🔧 Adding Zoho MX records to Route 53..."
echo "Hosted Zone ID: $ZONE_ID"
echo ""

# Apply the DNS changes
aws route53 change-resource-record-sets \
    --hosted-zone-id "$ZONE_ID" \
    --change-batch file://route53-mx-records.json

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ DNS records added successfully!"
    echo ""
    echo "📋 Records added:"
    echo "  • MX: mx.zoho.com (priority 10)"
    echo "  • MX: mx2.zoho.com (priority 20)"
    echo "  • MX: mx3.zoho.com (priority 50)"
    echo "  • SPF: v=spf1 include:zoho.com ~all"
    echo "  • DMARC: v=DMARC1; p=none"
    echo ""
    echo "⏳ DNS propagation may take 5-10 minutes"
    echo ""
    echo "🔍 Verify with:"
    echo "  nslookup -type=MX snapdeploy.dev"
    echo ""
    echo "📧 Test by sending email to: contact@snapdeploy.dev"
else
    echo ""
    echo "❌ Failed to add DNS records"
    echo "Please check your AWS credentials and hosted zone ID"
fi
