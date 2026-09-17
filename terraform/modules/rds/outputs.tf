output "mysql_endpoint"    { value = aws_db_instance.mysql.endpoint }
output "postgres_endpoint" { value = aws_db_instance.postgres.endpoint }
output "mysql_db_name"     { value = aws_db_instance.mysql.db_name }
output "postgres_db_name"  { value = aws_db_instance.postgres.db_name }
