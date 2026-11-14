#!/usr/bin/env bash
# update-prod-endpoints.sh
#
# Run from /mnt/e/Projects/AI/ai-microservices-platform/terraform after terraform apply.
# Reads new RDS/MSK endpoints from terraform outputs and updates all prod overlay YAML files.
# After running, commit the changes and push — ArgoCD detects the diff and resyncs automatically.
#
# Usage:
#   cd /mnt/e/Projects/AI/ai-microservices-platform/terraform
#   bash ../scripts/update-prod-endpoints.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OVERLAY="$REPO_ROOT/k8s/overlays/prod"

echo "==> Fetching terraform outputs..."
# terraform endpoint outputs include :port — strip it to get the hostname only
MYSQL_HOST=$(terraform output -raw rds_mysql_endpoint | cut -d: -f1)
PG_HOST=$(terraform output -raw rds_postgres_endpoint | cut -d: -f1)
# MSK bootstrap broker includes :9098 — keep the hostname only for sed replacement
MSK_HOST=$(terraform output -raw msk_bootstrap_brokers | cut -d: -f1)

echo "    MySQL:      $MYSQL_HOST"
echo "    PostgreSQL: $PG_HOST"
echo "    MSK:        $MSK_HOST"

echo ""
echo "==> Updating prod overlay YAML files..."

# Replace any RDS MySQL hostname (pattern: ai-platform-prod-mysql.<id>.us-east-1.rds.amazonaws.com)
find "$OVERLAY" -name "*.yaml" -exec sed -i \
  "s/ai-platform-prod-mysql\.[^.:]*\.us-east-1\.rds\.amazonaws\.com/$MYSQL_HOST/g" {} +

# Replace any RDS PostgreSQL hostname
find "$OVERLAY" -name "*.yaml" -exec sed -i \
  "s/ai-platform-prod-postgres\.[^.:]*\.us-east-1\.rds\.amazonaws\.com/$PG_HOST/g" {} +

# Replace MSK Serverless bootstrap hostname (pattern: boot-<id>.<c>.kafka-serverless.us-east-1.amazonaws.com)
find "$OVERLAY" -name "*.yaml" -exec sed -i \
  "s/boot-[^.:]*\.[^.]*\.kafka-serverless\.us-east-1\.amazonaws\.com/$MSK_HOST/g" {} +

echo ""
echo "==> Updated files:"
grep -rl "rds\.amazonaws\.com\|kafka-serverless" "$OVERLAY" --include="*.yaml" | sort
echo ""
echo "==> Spot-check (first match per file):"
grep -r "rds\.amazonaws\.com\|kafka-serverless" "$OVERLAY" --include="*.yaml" -h | head -20

echo ""
echo "==> Push changes so ArgoCD can sync:"
echo "    cd $REPO_ROOT"
echo "    git add k8s/overlays/prod"
echo "    git commit -m 'config(phase11): update prod overlay endpoints for new AWS session'"
echo "    git push origin main"
