resource "aws_appautoscaling_target" "commerce" {
  count = var.topology == "distributed" ? 1 : 0

  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${module.cluster.cluster_name}/${module.client_service["commerce-svc"].service_name}"
  min_capacity       = var.services_enabled ? var.commerce_min_capacity : 0
  max_capacity       = 6

  suspended_state {
    dynamic_scaling_in_suspended  = !var.services_enabled
    dynamic_scaling_out_suspended = !var.services_enabled
    scheduled_scaling_suspended   = !var.services_enabled
  }
}

resource "aws_appautoscaling_policy" "commerce_requests" {
  count = var.topology == "distributed" ? 1 : 0

  name               = "${local.name_prefix}-commerce-requests-per-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.commerce[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.commerce[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.commerce[0].resource_id

  target_tracking_scaling_policy_configuration {
    target_value       = var.commerce_requests_per_target
    scale_out_cooldown = 60
    scale_in_cooldown  = 180

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.this.arn_suffix}/${module.client_service["commerce-svc"].target_group_arn_suffix}"
    }
  }
}

module "observability" {
  source = "../modules/observability"
  count  = var.topology == "distributed" ? 1 : 0

  name                       = local.name_prefix
  aws_region                 = var.aws_region
  cloudfront_distribution_id = var.cloudfront_distribution_id
  alb_arn_suffix             = aws_lb.this.arn_suffix
  cluster_name               = module.cluster.cluster_name
  target_groups              = { for name, service in merge(module.service, module.client_service) : name => service.target_group_arn_suffix }
  topic_name                 = "${local.name_prefix}-domain-events"
  queue_names                = module.messaging[0].queue_names
  dlq_names                  = { for name in ["notification", "audit"] : name => "${local.name_prefix}-${name}-dlq" }
}
