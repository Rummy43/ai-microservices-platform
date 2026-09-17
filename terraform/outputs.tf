output "eks_cluster_name" {
  description = "EKS cluster name — use in kubectl config and GitHub Actions"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_cluster_ca_certificate" {
  value     = module.eks.cluster_ca_certificate
  sensitive = true
}

output "node_security_group_id" {
  value = module.eks.node_security_group_id
}

output "rds_mysql_endpoint" {
  description = "MySQL endpoint for user-service — inject into Secrets Manager"
  value       = module.rds.mysql_endpoint
}

output "rds_postgres_endpoint" {
  description = "PostgreSQL endpoint for notification-service + Keycloak"
  value       = module.rds.postgres_endpoint
}

output "msk_bootstrap_brokers" {
  description = "MSK Serverless bootstrap brokers (SASL/IAM) — inject into app config"
  value       = module.msk.bootstrap_brokers_sasl_iam
}

output "ecr_repository_urls" {
  description = "Map of service name → ECR repository URL"
  value       = module.ecr.repository_urls
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "private_subnet_ids" {
  value = module.vpc.private_subnet_ids
}

output "public_subnet_ids" {
  value = module.vpc.public_subnet_ids
}

output "kubeconfig_command" {
  description = "Run this to configure kubectl for the EKS cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}
