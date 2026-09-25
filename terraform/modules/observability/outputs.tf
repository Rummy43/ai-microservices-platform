output "tempo_bucket_name" { value = aws_s3_bucket.tempo.id }
output "tempo_bucket_arn"  { value = aws_s3_bucket.tempo.arn }
output "tempo_irsa_role_arn" {
  description = "Annotate the Tempo K8s ServiceAccount with this ARN"
  value       = aws_iam_role.tempo.arn
}

output "loki_bucket_name"  { value = aws_s3_bucket.loki.id }
output "loki_bucket_arn"   { value = aws_s3_bucket.loki.arn }
output "loki_irsa_role_arn" {
  description = "Annotate the Loki K8s ServiceAccount with this ARN"
  value       = aws_iam_role.loki.arn
}

output "ses_smtp_username" {
  description = "SES SMTP username — set in K8s alertmanager-smtp-secret"
  value       = aws_iam_access_key.ses_smtp.id
  sensitive   = true
}

output "ses_smtp_password" {
  description = "SES SMTP password (derived from IAM secret key) — set in K8s alertmanager-smtp-secret"
  value       = aws_iam_access_key.ses_smtp.ses_smtp_password_v4
  sensitive   = true
}

output "ses_identity_arn" { value = aws_ses_email_identity.alert_sender.arn }

output "deploy_instructions" {
  description = "Manual steps after terraform apply — run these before helm installs"
  value       = <<-EOT
    After terraform apply, run these commands to wire observability into EKS:

    # 1. Create Alertmanager SMTP secret (values from terraform output)
    kubectl create secret generic alertmanager-smtp-secret \
      --namespace monitoring \
      --from-literal=smtp-username=$(terraform output -raw ses_smtp_username) \
      --from-literal=smtp-password=$(terraform output -raw ses_smtp_password)

    # 2. Install kube-prometheus-stack (EKS values)
    helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
      --namespace monitoring --create-namespace \
      -f k8s/helm/kube-prometheus-stack/values.yaml \
      -f k8s/helm/kube-prometheus-stack/values-eks.yaml

    # 3. Install Tempo with S3 backend
    helm upgrade --install tempo grafana/tempo \
      --namespace monitoring \
      -f k8s/helm/tempo/values.yaml \
      -f k8s/helm/tempo/values-eks.yaml \
      --set tempo.storage.trace.s3.bucket=$(terraform output -raw tempo_bucket_name) \
      --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=$(terraform output -raw tempo_irsa_role_arn)

    # 4. Install Loki with S3 backend
    helm upgrade --install loki grafana/loki \
      --namespace monitoring \
      -f k8s/helm/loki/values.yaml \
      -f k8s/helm/loki/values-eks.yaml \
      --set loki.storage.s3.bucketNames.chunks=$(terraform output -raw loki_bucket_name) \
      --set loki.storage.s3.bucketNames.ruler=$(terraform output -raw loki_bucket_name) \
      --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=$(terraform output -raw loki_irsa_role_arn)

    # 5. Check SES sender verification email arrived in your inbox and click the link
    #    aws ses get-identity-verification-attributes --identities ${var.alert_email}
  EOT
}
