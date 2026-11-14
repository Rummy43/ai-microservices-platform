data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

module "vpc" {
  source = "./modules/vpc"

  project     = var.project
  environment = var.environment
  azs         = local.azs
}

module "ecr" {
  source = "./modules/ecr"

  project = var.project
}

module "eks" {
  source = "./modules/eks"

  project             = var.project
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  private_subnet_ids  = module.vpc.private_subnet_ids
  cluster_version     = var.eks_cluster_version
  node_instance_type  = var.node_instance_type
  node_desired_count  = var.node_desired_count
  node_min_count      = var.node_min_count
  node_max_count      = var.node_max_count
  use_spot_instances  = var.use_spot_instances
}

module "rds" {
  source = "./modules/rds"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_node_sg_id     = module.eks.node_security_group_id
  mysql_db_name      = var.mysql_db_name
  postgres_db_name   = var.postgres_db_name
  db_username        = var.db_username
  db_password        = var.db_password
}

module "msk" {
  source = "./modules/msk"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_node_sg_id     = module.eks.node_security_group_id
}

module "observability" {
  source = "./modules/observability"

  project           = var.project
  environment       = var.environment
  aws_region        = var.aws_region
  account_id        = data.aws_caller_identity.current.account_id
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider_url = module.eks.oidc_provider_url
  alert_email       = var.alert_email
}

# IRSA role shared by user-service, notification-service, and schema-registry
# for SASL/IAM authentication to MSK Serverless.
# Phase 10 tech debt: this role was manually recreated because it was outside TF state.
# Moving it here makes it deterministic and idempotent across AWS sessions.
resource "aws_iam_role" "kafka_client" {
  name = "ai-platform-kafka-client"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = module.eks.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(module.eks.oidc_provider_url, "https://", "")}:sub" = "system:serviceaccount:microservices:kafka-client-sa"
          "${replace(module.eks.oidc_provider_url, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "kafka_client_msk" {
  name = "ai-platform-kafka-client-msk"
  role = aws_iam_role.kafka_client.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kafka-cluster:Connect",
        "kafka-cluster:DescribeCluster",
        "kafka-cluster:AlterCluster",
        "kafka-cluster:DescribeTopic",
        "kafka-cluster:CreateTopic",
        "kafka-cluster:WriteData",
        "kafka-cluster:ReadData",
        "kafka-cluster:AlterGroup",
        "kafka-cluster:DescribeGroup"
      ]
      Resource = "*"
    }]
  })
}
