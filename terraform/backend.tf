# Remote state backend — bootstrapped 2026-09-18.
# Bucket: ai-platform-terraform-state-922024167800 (S3 versioning + AES256 + public-access-block)
# Locking: S3 native conditional writes (use_lockfile=true, Terraform >=1.10; replaces DynamoDB)

terraform {
  backend "s3" {
    bucket       = "ai-platform-terraform-state-922024167800"
    key          = "prod/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
