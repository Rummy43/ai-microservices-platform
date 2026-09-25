variable "project"     { type = string }
variable "environment" { type = string }
variable "aws_region"  { type = string }
variable "account_id"  { type = string }

variable "oidc_provider_arn" {
  description = "OIDC provider ARN from the EKS module — required to issue IRSA trust policies"
  type        = string
}

variable "oidc_provider_url" {
  description = "OIDC provider URL (without https://) — used in the IRSA condition key"
  type        = string
}

variable "k8s_namespace" {
  description = "Namespace where Tempo and Loki pods run"
  type        = string
  default     = "monitoring"
}

variable "alert_email" {
  description = "Email address to receive Alertmanager SES notifications"
  type        = string
}
