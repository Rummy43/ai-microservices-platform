output "cluster_name"          { value = aws_eks_cluster.this.name }
output "cluster_endpoint"      { value = aws_eks_cluster.this.endpoint }
output "cluster_ca_certificate" { value = aws_eks_cluster.this.certificate_authority[0].data }
output "oidc_provider_arn"     { value = aws_iam_openid_connect_provider.this.arn }
output "oidc_provider_url"     { value = aws_iam_openid_connect_provider.this.url }
output "node_security_group_id" {
  value = aws_eks_node_group.this.resources[0].remote_access_security_group_id
}
