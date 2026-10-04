output "address" {
  description = "Primary endpoint hostname"
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "port" {
  description = "Port"
  value       = aws_elasticache_replication_group.this.port
}

output "auth_token_arn" {
  description = "SSM parameter holding the AUTH token"
  value       = aws_ssm_parameter.auth.arn
}
