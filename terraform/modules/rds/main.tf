resource "aws_db_subnet_group" "this" {
  name       = "${var.project}-${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnet_ids
  tags       = { Name = "${var.project}-${var.environment}-db-subnet-group" }
}

resource "aws_security_group" "rds" {
  name        = "${var.project}-${var.environment}-rds-sg"
  description = "Allow inbound DB traffic from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "MySQL from EKS nodes"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.eks_node_sg_id]
  }

  ingress {
    description     = "PostgreSQL from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_node_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-${var.environment}-rds-sg" }
}

# MySQL 8.0 — user-service
resource "aws_db_instance" "mysql" {
  identifier        = "${var.project}-${var.environment}-mysql"
  engine            = "mysql"
  engine_version    = "8.0"
  instance_class    = "db.t3.micro"
  allocated_storage = 20

  db_name  = var.mysql_db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  skip_final_snapshot     = true
  backup_retention_period = 0
  multi_az                = false
  publicly_accessible     = false
  deletion_protection     = false

  tags = { Name = "${var.project}-${var.environment}-mysql" }
}

# PostgreSQL 17 — notification-service + Keycloak
resource "aws_db_instance" "postgres" {
  identifier        = "${var.project}-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "17"
  instance_class    = "db.t3.micro"
  allocated_storage = 20

  db_name  = var.postgres_db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  skip_final_snapshot     = true
  backup_retention_period = 0
  multi_az                = false
  publicly_accessible     = false
  deletion_protection     = false

  tags = { Name = "${var.project}-${var.environment}-postgres" }
}
