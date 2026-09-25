variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "project" {
  description = "Project name — used as prefix on all resource names and tags"
  type        = string
  default     = "ai-platform"
}

variable "eks_cluster_version" {
  description = "Kubernetes version — match the kind baseline (1.31 LTS)"
  type        = string
  default     = "1.31"
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS managed node group"
  type        = string
  default     = "t3.medium"
}

variable "node_desired_count" {
  type    = number
  default = 2
}

variable "node_min_count" {
  type    = number
  default = 1
}

variable "node_max_count" {
  type    = number
  default = 3
}

variable "use_spot_instances" {
  description = "Use spot instances for EKS nodes — ~80% cost saving for demo runs"
  type        = bool
  default     = true
}

variable "mysql_db_name" {
  type    = string
  default = "userdb"
}

variable "postgres_db_name" {
  type    = string
  default = "notificationdb"
}

variable "db_username" {
  description = "Master username for both RDS instances"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Master password for both RDS instances — stored in Secrets Manager post-bootstrap"
  type        = string
  sensitive   = true
}

variable "keycloak_admin_password" {
  description = "Keycloak admin console password — stored in Secrets Manager"
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "Email address for Alertmanager SES notifications (must be verified in SES sandbox)"
  type        = string
  default     = "yara.ramesh92@gmail.com"
}
