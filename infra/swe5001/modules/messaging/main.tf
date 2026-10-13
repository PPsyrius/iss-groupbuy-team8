# One domain topic, raw SQS subscriptions and per-consumer redrive (ADR 0007, #447).
terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_caller_identity" "current" {}

# tfsec:ignore:aws-sns-topic-encryption-use-cmk -- AWS-managed SNS key required by the merged SWE5001 no-CMK rule (refactoring plan 3.3, PR 379)
resource "aws_sns_topic" "events" {
  name              = "${var.name_prefix}-domain-events"
  kms_master_key_id = "alias/aws/sns"
}

locals {
  consumers = toset(["notification", "audit"])
  filters = {
    notification = { eventType = ["TeamSucceeded", "TeamExpired", "PaymentSucceeded"] }
    audit        = { eventType = [{ prefix = "Team" }] }
  }
}

resource "aws_sqs_queue" "dlq" {
  for_each = local.consumers

  name                      = "${var.name_prefix}-${each.key}-dlq"
  sqs_managed_sse_enabled   = true
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "consumer" {
  for_each = local.consumers

  name                       = "${var.name_prefix}-${each.key}"
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  for_each = local.consumers

  queue_url = aws_sqs_queue.dlq[each.key].url
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.consumer[each.key].arn]
  })
}

resource "aws_sqs_queue_policy" "topic" {
  for_each = local.consumers

  queue_url = aws_sqs_queue.consumer[each.key].url
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowDomainTopicOnly"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.consumer[each.key].arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.events.arn } }
      }, {
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "sqs:*"
      Resource  = aws_sqs_queue.consumer[each.key].arn
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_sns_topic_subscription" "consumer" {
  for_each = local.consumers

  topic_arn            = aws_sns_topic.events.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.consumer[each.key].arn
  raw_message_delivery = true
  filter_policy_scope  = "MessageAttributes"
  filter_policy        = jsonencode(local.filters[each.key])

  depends_on = [aws_sqs_queue_policy.topic]
}

# tfsec:ignore:aws-sns-enable-topic-encryption -- CloudWatch requires a CMK for encrypted alarm delivery; metadata only, no CMK in disposable runtime
resource "aws_sns_topic" "alerts" {
  # checkov:skip=CKV_AWS_26: CloudWatch cannot publish alarms through the AWS-managed SNS key; no CMK in disposable runtime, alarm metadata only
  name = "${var.name_prefix}-alerts"
}

resource "aws_sns_topic_policy" "alerts" {
  arn = aws_sns_topic.alerts.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCourseAlarms"
      Effect    = "Allow"
      Principal = { Service = "cloudwatch.amazonaws.com" }
      Action    = "sns:Publish"
      Resource  = aws_sns_topic.alerts.arn
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        ArnLike      = { "aws:SourceArn" = "arn:aws:cloudwatch:*:${data.aws_caller_identity.current.account_id}:alarm:${var.name_prefix}-*" }
      }
    }]
  })
}

resource "aws_sns_topic_subscription" "email" {
  for_each = var.alert_email_addresses

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "${var.name_prefix}-notification-dlq-not-empty"
  alarm_description   = "Notification delivery exhausted its five-receive retry budget"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dlq["notification"].name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "age" {
  alarm_name          = "${var.name_prefix}-notification-queue-age"
  alarm_description   = "Notification backlog exceeds the session delivery-age target"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = aws_sqs_queue.consumer["notification"].name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.oldest_message_age_seconds
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
