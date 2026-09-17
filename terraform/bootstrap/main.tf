# Bootstrap — run ONCE to create the Terraform remote state backend.
# After apply, the bucket name and table name are wired into terraform/backend.tf.
#
# Usage:
#   cd terraform/bootstrap
#   terraform init
#   terraform apply
#   terraform output backend_config   # copy into terraform/backend.tf

terraform {
  required_version = ">= 1.16"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "ai-platform"
      ManagedBy = "terraform"
      Purpose   = "state-backend"
    }
  }
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

data "aws_caller_identity" "current" {}

locals {
  bucket_name = "ai-platform-terraform-state-${data.aws_caller_identity.current.account_id}"
  table_name  = "ai-platform-terraform-locks"
}

resource "aws_s3_bucket" "state" {
  bucket = local.bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "locks" {
  name         = local.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

output "state_bucket_name" {
  value = aws_s3_bucket.state.bucket
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.locks.name
}

output "aws_region" {
  value = var.aws_region
}

output "backend_config" {
  value = <<-EOT
    # Paste into terraform/backend.tf:
    terraform {
      backend "s3" {
        bucket         = "${aws_s3_bucket.state.bucket}"
        key            = "prod/terraform.tfstate"
        region         = "${var.aws_region}"
        dynamodb_table = "${aws_dynamodb_table.locks.name}"
        encrypt        = true
      }
    }
  EOT
}
