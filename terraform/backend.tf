# Remote state backend — populated after running terraform/bootstrap.
# Run bootstrap first: cd bootstrap && terraform init && terraform apply
# Then copy the output here and run: terraform init

terraform {
  backend "s3" {
    bucket         = "ai-platform-terraform-state-922024167800"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ai-platform-terraform-locks"
    encrypt        = true
  }
}
