output "service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.this.name
}

output "task_definition_family" {
  description = "Task definition family; the deploy workflow registers new revisions in it"
  value       = aws_ecs_task_definition.this.family
}

output "task_role_arn" {
  description = "Task role ARN"
  value       = aws_iam_role.task.arn
}

output "target_group_arn" {
  description = "Target group ARN"
  value       = aws_lb_target_group.this.arn
}

output "target_group_arn_suffix" {
  description = "ALB metric dimension and target-tracking resource label suffix"
  value       = aws_lb_target_group.this.arn_suffix
}

output "initial_capacity" {
  description = "Initial ECS count and placement configuration for offline wiring checks; not a live placement claim"
  value = {
    desired_count                 = aws_ecs_service.this.desired_count
    availability_zone_rebalancing = aws_ecs_service.this.availability_zone_rebalancing
    capacity_provider_strategy    = aws_ecs_service.this.capacity_provider_strategy
  }
}
