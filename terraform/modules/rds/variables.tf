variable "project"            { type = string }
variable "environment"        { type = string }
variable "vpc_id"             { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "eks_node_sg_id"     { type = string }
variable "mysql_db_name"      { type = string }
variable "postgres_db_name"   { type = string }

variable "db_username" {
  type      = string
  sensitive = true
}

variable "db_password" {
  type      = string
  sensitive = true
}
