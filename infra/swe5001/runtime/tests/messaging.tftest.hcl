mock_provider "aws" {
  override_during = plan

  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-domain-events" }
  }

  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:ap-southeast-1:402178232802:test-queue"
      url = "https://sqs.ap-southeast-1.amazonaws.com/402178232802/test-queue"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = { account_id = "402178232802" }
  }
}

override_resource {
  target          = aws_sns_topic.alerts
  values          = { arn = "arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-alerts" }
  override_during = plan
}

override_resource {
  target = aws_sqs_queue.consumer["notification"]
  values = {
    arn = "arn:aws:sqs:ap-southeast-1:402178232802:blocbuy-swe5001-notification"
    url = "https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification"
  }
  override_during = plan
}

override_resource {
  target = aws_sqs_queue.consumer["audit"]
  values = {
    arn = "arn:aws:sqs:ap-southeast-1:402178232802:blocbuy-swe5001-audit"
    url = "https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-audit"
  }
  override_during = plan
}

override_resource {
  target = aws_sqs_queue.dlq["notification"]
  values = {
    arn = "arn:aws:sqs:ap-southeast-1:402178232802:blocbuy-swe5001-notification-dlq"
    url = "https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification-dlq"
  }
  override_during = plan
}

override_resource {
  target = aws_sqs_queue.dlq["audit"]
  values = {
    arn = "arn:aws:sqs:ap-southeast-1:402178232802:blocbuy-swe5001-audit-dlq"
    url = "https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-audit-dlq"
  }
  override_during = plan
}

run "fanout_security_and_redrive" {
  command = plan
  module { source = "../modules/messaging" }
  variables { name_prefix = "blocbuy-swe5001" }

  assert {
    condition     = aws_sns_topic.events.name == "blocbuy-swe5001-domain-events" && aws_sns_topic.events.kms_master_key_id == "alias/aws/sns" && length(aws_sns_topic_subscription.consumer) == 2
    error_message = "One AWS-managed-key encrypted domain topic fans out to exactly two consumers."
  }

  assert {
    condition     = alltrue([for q in aws_sqs_queue.consumer : q.sqs_managed_sse_enabled && q.visibility_timeout_seconds == 60 && q.receive_wait_time_seconds == 20 && q.message_retention_seconds == 345600 && jsondecode(q.redrive_policy).maxReceiveCount == 5])
    error_message = "Both queues use SSE-SQS, 60-second visibility, 20-second polling, four-day retention and five receives."
  }

  assert {
    condition     = alltrue([for name, q in aws_sqs_queue.dlq : q.sqs_managed_sse_enabled && q.message_retention_seconds == 1209600 && jsondecode(aws_sqs_queue.consumer[name].redrive_policy).deadLetterTargetArn == q.arn && jsondecode(aws_sqs_queue_redrive_allow_policy.dlq[name].redrive_allow_policy).redrivePermission == "byQueue" && jsondecode(aws_sqs_queue_redrive_allow_policy.dlq[name].redrive_allow_policy).sourceQueueArns == [aws_sqs_queue.consumer[name].arn]])
    error_message = "Each consumer has its own encrypted 14-day DLQ with a restricted redrive source."
  }

  assert {
    condition = alltrue([for name, q in aws_sqs_queue_policy.topic :
      jsondecode(q.policy).Statement[0].Principal.Service == "sns.amazonaws.com" &&
      jsondecode(q.policy).Statement[0].Action == "sqs:SendMessage" &&
      jsondecode(q.policy).Statement[0].Resource == aws_sqs_queue.consumer[name].arn &&
      jsondecode(q.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"] == aws_sns_topic.events.arn &&
      jsondecode(q.policy).Statement[1].Effect == "Deny" &&
      jsondecode(q.policy).Statement[1].Condition.Bool["aws:SecureTransport"] == "false"
    ])
    error_message = "SNS may send only from the exact domain topic ARN, never another topic or a wildcard principal."
  }

  assert {
    condition     = jsondecode(aws_sns_topic_policy.alerts.policy).Statement[0].Principal.Service == "cloudwatch.amazonaws.com" && jsondecode(aws_sns_topic_policy.alerts.policy).Statement[0].Condition.StringEquals["aws:SourceAccount"] == "402178232802" && jsondecode(aws_sns_topic_policy.alerts.policy).Statement[0].Condition.ArnLike["aws:SourceArn"] == "arn:aws:cloudwatch:*:402178232802:alarm:blocbuy-swe5001-*"
    error_message = "CloudWatch alert publication is limited to this account's course-prefixed alarms."
  }

  assert {
    condition     = alltrue([for s in aws_sns_topic_subscription.consumer : s.raw_message_delivery && s.filter_policy_scope == "MessageAttributes" && s.topic_arn == aws_sns_topic.events.arn]) && jsondecode(aws_sns_topic_subscription.consumer["audit"].filter_policy).eventType[0].prefix == "Team" && toset(jsondecode(aws_sns_topic_subscription.consumer["notification"].filter_policy).eventType) == toset(["TeamSucceeded", "TeamExpired", "PaymentSucceeded"])
    error_message = "Raw subscriptions route Team* to audit and supported team/payment events to notification."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.dlq.threshold == 0 && aws_cloudwatch_metric_alarm.dlq.dimensions.QueueName == "blocbuy-swe5001-notification-dlq" && aws_cloudwatch_metric_alarm.age.threshold == 60 && aws_cloudwatch_metric_alarm.age.dimensions.QueueName == "blocbuy-swe5001-notification" && aws_cloudwatch_metric_alarm.age.alarm_actions == toset([aws_sns_topic.alerts.arn]) && length(aws_sns_topic_subscription.email) == 0
    error_message = "Alarms target the notification queue/DLQ and the alerts topic; no recipient is committed."
  }
}

run "operator_recipients_and_threshold" {
  command = plan
  module { source = "../modules/messaging" }

  variables {
    name_prefix                = "blocbuy-swe5001"
    alert_email_addresses      = ["team@example.invalid"]
    oldest_message_age_seconds = 120
  }

  assert {
    condition     = aws_sns_topic_subscription.email["team@example.invalid"].protocol == "email" && aws_cloudwatch_metric_alarm.age.threshold == 120
    error_message = "An operator can supply email recipients and a measured age threshold."
  }
}

run "reject_nonpositive_age" {
  command = plan
  module { source = "../modules/messaging" }
  variables {
    name_prefix                = "blocbuy-swe5001"
    oldest_message_age_seconds = 0
  }
  expect_failures = [var.oldest_message_age_seconds]
}
