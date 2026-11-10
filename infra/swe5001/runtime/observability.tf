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

# The November V3 saturated commerce before the delayed three-point ALB alarm.
# This opt-in guard requests one task from an earlier CPU signal; it never scales in
# or replaces/calibrates the existing request policy. Defaults leave live policy unchanged.
resource "aws_appautoscaling_policy" "commerce_cpu_headroom" {
  count = var.topology == "distributed" && var.commerce_cpu_scale_out_enabled ? 1 : 0

  name               = "${local.name_prefix}-commerce-cpu-headroom"
  policy_type        = "StepScaling"
  service_namespace  = aws_appautoscaling_target.commerce[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.commerce[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.commerce[0].resource_id

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "commerce_cpu_headroom" {
  count = var.topology == "distributed" && var.commerce_cpu_scale_out_enabled ? 1 : 0

  alarm_name          = "${local.name_prefix}-commerce-cpu-headroom"
  alarm_description   = "Optional early commerce CPU scale-out; not a performance acceptance alarm"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.commerce_cpu_scale_out_threshold
  treat_missing_data  = "notBreaching"
  actions_enabled     = var.services_enabled
  alarm_actions       = [aws_appautoscaling_policy.commerce_cpu_headroom[0].arn]

  dimensions = {
    ClusterName = module.cluster.cluster_name
    ServiceName = module.client_service["commerce-svc"].service_name
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
