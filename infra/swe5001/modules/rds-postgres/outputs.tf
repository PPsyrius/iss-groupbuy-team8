output "address" {
  description = "Instance hostname"
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Instance port"
  value       = aws_db_instance.this.port
}

output "master_username" {
  description = "Master user, used only by db-init"
  value       = aws_db_instance.this.username
}

output "master_password_arn" {
  description = "SSM parameter holding the master password"
  value       = aws_ssm_parameter.master_password.arn
}

output "role_password_arns" {
  description = "SSM parameter ARN per \"<database>/<OWNER|APP>\""
  value       = { for k, p in aws_ssm_parameter.role_password : k => p.arn }
}
