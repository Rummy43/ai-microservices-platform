resource "aws_security_group" "msk" {
  name        = "${var.project}-${var.environment}-msk-sg"
  description = "Allow Kafka traffic from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Kafka SASL/IAM from EKS nodes"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.eks_node_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-${var.environment}-msk-sg" }
}

resource "aws_msk_serverless_cluster" "this" {
  cluster_name = "${var.project}-${var.environment}"

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [aws_security_group.msk.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }

  tags = { Name = "${var.project}-${var.environment}-msk" }
}
