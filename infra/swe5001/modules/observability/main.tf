terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

# Three rows, not three overloaded graphs. Keep percentages, seconds and counts
# apart; CloudFront metrics live in us-east-1 regardless of the origin Region.
locals {
  widgets = [
    {
      type = "metric", x = 0, y = 0, width = 8, height = 6
      properties = {
        title = "Edge - CloudFront", view = "timeSeries", region = "us-east-1", period = 60
        yAxis = { left = { label = "Requests/min" }, right = { label = "Errors %", min = 0, max = 100 } }
        metrics = [
          ["AWS/CloudFront", "Requests", "DistributionId", var.cloudfront_distribution_id, "Region", "Global", { stat = "Sum" }],
          ["AWS/CloudFront", "5xxErrorRate", "DistributionId", var.cloudfront_distribution_id, "Region", "Global", { stat = "Average", yAxis = "right" }]
        ]
      }
    },
    {
      type = "metric", x = 8, y = 0, width = 8, height = 6
      properties = {
        title = "Edge - ALB traffic", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Requests/min" }, right = { label = "5xx/min" } }
        metrics = concat([
          ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix, { stat = "Sum", label = "ALB requests" }]
          ], concat([for name, suffix in var.target_groups : [
            ["AWS/ApplicationELB", "RequestCountPerTarget", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", suffix, { stat = "Sum", label = "${name} requests/target" }],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", suffix, { stat = "Sum", label = "${name} 5xx", yAxis = "right" }]
        ]]...))
      }
    },
    {
      type = "metric", x = 16, y = 0, width = 8, height = 6
      properties = {
        title = "Edge - ALB latency", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Seconds", min = 0 } }
        metrics = concat([for name, suffix in var.target_groups : [
          ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", suffix, { stat = "p50", label = "${name} p50" }],
          ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", suffix, { stat = "p95", label = "${name} p95" }]
        ]]...)
      }
    },
    {
      type = "metric", x = 0, y = 6, width = 12, height = 6
      properties = {
        title = "Services - task counts", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Running tasks", min = 0 } }
        metrics = [for name in keys(var.target_groups) :
          ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", var.cluster_name, "ServiceName", name, { stat = "Average", label = name }]
        ]
      }
    },
    {
      type = "metric", x = 12, y = 6, width = 12, height = 6
      properties = {
        title = "Services - utilisation", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Percent", min = 0 } }
        metrics = concat([for name in keys(var.target_groups) : [
          ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", name, { stat = "Average", label = "${name} CPU %" }],
          ["AWS/ECS", "MemoryUtilization", "ClusterName", var.cluster_name, "ServiceName", name, { stat = "Average", label = "${name} memory %" }]
        ]]...)
      }
    },
    {
      type = "metric", x = 0, y = 12, width = 12, height = 6
      properties = {
        title = "Messaging - volumes", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Messages", min = 0 } }
        metrics = concat([
          ["AWS/SNS", "NumberOfMessagesPublished", "TopicName", var.topic_name, { stat = "Sum" }]
          ], [for name, queue in var.queue_names :
          ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", queue, { stat = "Average", label = "${name} backlog" }]
          ], [for name, queue in var.dlq_names :
          ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", queue, { stat = "Maximum", label = "${name} DLQ" }]
        ])
      }
    },
    {
      type = "metric", x = 12, y = 12, width = 12, height = 6
      properties = {
        title = "Messaging - queue age", view = "timeSeries", region = var.aws_region, period = 60
        yAxis = { left = { label = "Seconds", min = 0 } }
        metrics = [for name, queue in var.queue_names :
          ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", queue, { stat = "Maximum", label = "${name} age" }]
        ]
      }
    }
  ]
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = var.name
  dashboard_body = jsonencode({ start = "-PT1H", periodOverride = "inherit", widgets = local.widgets })
}
