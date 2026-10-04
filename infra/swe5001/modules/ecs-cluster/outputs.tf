# The name comes from the capacity-provider resource, so services that use the
# capacity-provider strategy are created only after the providers are attached.
output "cluster_name" {
  description = "Cluster name"
  value       = aws_ecs_cluster_capacity_providers.this.cluster_name
}

output "cluster_arn" {
  description = "Cluster ARN"
  value       = aws_ecs_cluster.this.arn
}

output "namespace_arn" {
  description = "Service Connect namespace ARN"
  value       = aws_service_discovery_http_namespace.this.arn
}

output "execution_role_arn" {
  description = "Execution role shared by every task definition"
  value       = aws_iam_role.execution.arn
}
