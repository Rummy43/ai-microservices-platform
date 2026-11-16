# update-endpoints.ps1
# Run this from the terraform/ directory immediately after terraform apply.
# Updates all K8s YAML files with the new RDS and MSK endpoints.
# These values change on every destroy+recreate cycle, so they cannot be hardcoded.

param(
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"
Write-Host "=== Epoch J endpoint update ===" -ForegroundColor Cyan

# Extract new endpoints from Terraform outputs
Write-Host "Reading terraform outputs..." -ForegroundColor Yellow
$MYSQL_ENDPOINT    = (terraform output -raw rds_mysql_endpoint).Trim()
$POSTGRES_ENDPOINT = (terraform output -raw rds_postgres_endpoint).Trim()
$MSK_BROKERS       = (terraform output -raw msk_bootstrap_brokers).Trim()

# The RDS endpoint includes the port (e.g. hostname:3306) — strip the port for env var use
$MYSQL_HOST    = $MYSQL_ENDPOINT.Split(':')[0]
$POSTGRES_HOST = $POSTGRES_ENDPOINT.Split(':')[0]
# MSK broker is host:9098 — keep as-is for bootstrap-servers

Write-Host "MySQL host:     $MYSQL_HOST"
Write-Host "Postgres host:  $POSTGRES_HOST"
Write-Host "MSK brokers:    $MSK_BROKERS"

# Old placeholder values (from Epoch I — the suffix changes on every recreate)
# We replace the entire hostname pattern to avoid partial matches
$OLD_MYSQL_HOST    = "ai-platform-prod-mysql.cifqagsacu3i.us-east-1.rds.amazonaws.com"
$OLD_POSTGRES_HOST = "ai-platform-prod-postgres.cifqagsacu3i.us-east-1.rds.amazonaws.com"
$OLD_MSK_BROKERS   = "boot-hhi6hm5z.c1.kafka-serverless.us-east-1.amazonaws.com:9098"

$yamlRoot = "..\k8s\overlays\prod"
$files = @(
  "$yamlRoot\user-service\deployment.yaml",
  "$yamlRoot\user-service\kafka-configmap.yaml",
  "$yamlRoot\notification-service\deployment.yaml",
  "$yamlRoot\notification-service\kafka-configmap.yaml",
  "$yamlRoot\keycloak\deployment.yaml"
)

foreach ($file in $files) {
  $content = Get-Content $file -Raw
  $original = $content

  $content = $content -replace [regex]::Escape($OLD_MYSQL_HOST),    $MYSQL_HOST
  $content = $content -replace [regex]::Escape($OLD_POSTGRES_HOST), $POSTGRES_HOST
  $content = $content -replace [regex]::Escape($OLD_MSK_BROKERS),   $MSK_BROKERS

  if ($content -ne $original) {
    Write-Host "Updated: $file" -ForegroundColor Green
    if (-not $DryRun) {
      Set-Content -Path $file -Value $content -Encoding utf8 -NoNewline
    } else {
      Write-Host "  [DRY RUN — not written]" -ForegroundColor DarkYellow
    }
  } else {
    Write-Host "No changes: $file" -ForegroundColor DarkGray
  }
}

Write-Host ""
Write-Host "=== Done. Next steps ===" -ForegroundColor Cyan
Write-Host "1. Update kubeconfig:"
Write-Host "   aws eks update-kubeconfig --region us-east-1 --name ai-platform-prod"
Write-Host ""
Write-Host "2. ECR login + image push:"
Write-Host "   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 922024167800.dkr.ecr.us-east-1.amazonaws.com"
Write-Host "   docker push 922024167800.dkr.ecr.us-east-1.amazonaws.com/ai-platform/user-service:1.6.0"
Write-Host "   docker push 922024167800.dkr.ecr.us-east-1.amazonaws.com/ai-platform/notification-service:2.0.0"
Write-Host "   docker push 922024167800.dkr.ecr.us-east-1.amazonaws.com/ai-platform/api-gateway:1.6.0"
Write-Host "   docker push 922024167800.dkr.ecr.us-east-1.amazonaws.com/ai-platform/schema-registry:7.5.0-msk"
Write-Host ""
Write-Host "3. Apply K8s prod overlay:"
Write-Host "   kubectl apply -k k8s/overlays/prod"
Write-Host ""
$TEMPO_BUCKET    = (terraform output -raw tempo_bucket_name).Trim()
$LOKI_BUCKET     = (terraform output -raw loki_bucket_name).Trim()
$TEMPO_IRSA      = (terraform output -raw tempo_irsa_role_arn).Trim()
$LOKI_IRSA       = (terraform output -raw loki_irsa_role_arn).Trim()
$SMTP_USER       = (terraform output -raw ses_smtp_username).Trim()
$SMTP_PASS       = (terraform output -raw ses_smtp_password).Trim()

Write-Host "4. Apply gp3 StorageClass (patch gp2 to non-default first):"
Write-Host "   kubectl patch storageclass gp2 -p '{`"metadata`":{`"annotations`":{`"storageclass.kubernetes.io/is-default-class`":`"false`"}}}'"
Write-Host "   kubectl apply -f k8s/overlays/prod/observability/storageclass-gp3.yaml"
Write-Host ""
Write-Host "5. Create SMTP secret:"
Write-Host "   kubectl create secret generic alertmanager-smtp-secret --namespace monitoring \`"
Write-Host "     --from-literal=smtp-username=$SMTP_USER \`"
Write-Host "     --from-literal=smtp-password=$SMTP_PASS"
Write-Host ""
Write-Host "6. Helm install kube-prometheus-stack:"
Write-Host "   helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \"
Write-Host "     --namespace monitoring --create-namespace \"
Write-Host "     -f k8s/helm/kube-prometheus-stack/values.yaml \"
Write-Host "     -f k8s/helm/kube-prometheus-stack/values-eks.yaml"
Write-Host ""
Write-Host "7. Helm install Tempo:"
Write-Host "   helm upgrade --install tempo grafana/tempo --namespace monitoring \"
Write-Host "     -f k8s/helm/tempo/values.yaml -f k8s/helm/tempo/values-eks.yaml \"
Write-Host "     --set tempo.storage.trace.s3.bucket=$TEMPO_BUCKET \"
Write-Host "     --set `"serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$TEMPO_IRSA`""
Write-Host ""
Write-Host "8. Helm install Loki:"
Write-Host "   helm upgrade --install loki grafana/loki --namespace monitoring \"
Write-Host "     -f k8s/helm/loki/values.yaml -f k8s/helm/loki/values-eks.yaml \"
Write-Host "     --set loki.storage.s3.bucketNames.chunks=$LOKI_BUCKET \"
Write-Host "     --set loki.storage.s3.bucketNames.ruler=$LOKI_BUCKET \"
Write-Host "     --set `"serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$LOKI_IRSA`""
Write-Host ""
Write-Host "9. Install Promtail:"
Write-Host "   helm upgrade --install promtail grafana/promtail --namespace monitoring \"
Write-Host "     -f k8s/helm/promtail/values.yaml"
Write-Host ""
Write-Host "10. Apply AlertmanagerConfig CR:"
Write-Host "    kubectl apply -f k8s/overlays/prod/observability/alertmanager-config.yaml"
