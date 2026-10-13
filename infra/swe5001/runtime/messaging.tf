# Disposable with the databases: a later session must not consume an earlier one's events.
module "messaging" {
  source = "../modules/messaging"
  count  = var.topology == "distributed" ? 1 : 0

  name_prefix                = local.name_prefix
  alert_email_addresses      = var.alert_email_addresses
  oldest_message_age_seconds = var.notification_oldest_message_age_seconds
}

locals {
  messaging = var.topology == "distributed" ? {
    topic_arn        = module.messaging[0].topic_arn
    notification_url = module.messaging[0].queue_urls["notification"]
  } : { topic_arn = "", notification_url = "" }
  # jsonencode keeps policy structure testable under the mocked provider too.
  commerce_event_policy = var.topology == "distributed" ? jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish"]
      Resource = [module.messaging[0].topic_arn]
      }, {
      Effect   = "Allow"
      Action   = ["kms:GenerateDataKey*", "kms:Decrypt"]
      Resource = ["arn:aws:kms:${var.aws_region}:${var.aws_account_id}:key/*"]
      Condition = { StringEquals = {
        "kms:ViaService"                         = "sns.${var.aws_region}.amazonaws.com"
        "kms:EncryptionContext:aws:sns:topicArn" = "arn:aws:sns:${var.aws_region}:${var.aws_account_id}:${local.name_prefix}-domain-events"
      } }
    }]
  }) : null
  notification_event_policy = var.topology == "distributed" ? jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
      Resource = [module.messaging[0].queue_arns["notification"]]
    }]
  }) : null
}

# Keep target/policy identities across reruns. Before db-init and routing readiness the
# target is suspended in every direction with min 0, so it cannot start tasks early.
resource "aws_appautoscaling_target" "notification" {
  count = var.topology == "distributed" ? 1 : 0

  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${module.cluster.cluster_name}/${module.service["notification-svc"].service_name}"
  min_capacity       = var.services_enabled ? 1 : 0
  max_capacity       = 4

  suspended_state {
    dynamic_scaling_in_suspended  = !var.services_enabled
    dynamic_scaling_out_suspended = !var.services_enabled
    scheduled_scaling_suspended   = !var.services_enabled
  }
}

resource "aws_appautoscaling_policy" "notification_backlog" {
  count = var.topology == "distributed" ? 1 : 0

  name               = "${local.name_prefix}-notification-backlog-per-task"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.notification[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.notification[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.notification[0].resource_id

  target_tracking_scaling_policy_configuration {
    target_value       = 50
    scale_out_cooldown = 60
    scale_in_cooldown  = 180

    customized_metric_specification {
      metrics {
        id          = "backlog"
        return_data = false
        metric_stat {
          stat = "Average"
          metric {
            namespace   = "AWS/SQS"
            metric_name = "ApproximateNumberOfMessagesVisible"
            dimensions {
              name  = "QueueName"
              value = module.messaging[0].queue_names["notification"]
            }
          }
        }
      }
      metrics {
        id          = "tasks"
        return_data = false
        metric_stat {
          stat = "Average"
          metric {
            namespace   = "ECS/ContainerInsights"
            metric_name = "RunningTaskCount"
            dimensions {
              name  = "ClusterName"
              value = module.cluster.cluster_name
            }
            dimensions {
              name  = "ServiceName"
              value = module.service["notification-svc"].service_name
            }
          }
        }
      }
      metrics {
        id          = "backlog_per_task"
        expression  = "backlog / IF(tasks > 0, tasks, 1)"
        return_data = true
      }
    }
  }
}
