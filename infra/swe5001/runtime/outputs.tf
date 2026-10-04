output "alb_dns_name" {
  description = "ALB DNS name; the edge stack points the /api/* origin at it"
  value       = aws_lb.this.dns_name
}

output "cluster_name" {
  description = "ECS cluster name"
  value       = module.cluster.cluster_name
}

output "service_names" {
  description = "ECS service names"
  value       = [for s in module.service : s.service_name]
}

output "task_definition_families" {
  description = "Task definition family per service; the deploy workflow registers new revisions in them"
  value       = { for k, s in module.service : k => s.task_definition_family }
}

output "db_init_task_definition" {
  description = "Task definition ARN for `aws ecs run-task` in env-up.sh"
  value       = aws_ecs_task_definition.db_init.arn
}

output "db_init_network" {
  description = "awsvpc settings for the db-init task"
  value = {
    subnets         = module.network.private_subnet_ids
    security_groups = [aws_security_group.dbinit.id]
  }
}

output "nat_public_ip" {
  description = "Egress IP of the private subnets"
  value       = module.network.nat_public_ip
}
