output "cluster_name"          { value = aws_eks_cluster.this.name }
output "cluster_endpoint"      { value = aws_eks_cluster.this.endpoint }
output "cluster_ca_certificate" { value = aws_eks_cluster.this.certificate_authority[0].data }
output "oidc_provider_arn"     { value = aws_iam_openid_connect_provider.this.arn }
output "oidc_provider_url"     { value = aws_iam_openid_connect_provider.this.url }
output "node_security_group_id" {
  # remote_access_security_group_id is null when no SSH remote_access block is configured.
  # The cluster_security_group_id is auto-created by EKS and attached to all managed nodes.
  value = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}
