output "topic_arn" {
  description = "Domain-event publish target"
  value       = aws_sns_topic.events.arn
}

output "queue_urls" {
  description = "Consumer queue URL by consumer name"
  value       = { for name, queue in aws_sqs_queue.consumer : name => queue.url }
}

output "queue_arns" {
  description = "Consumer queue ARN by consumer name"
  value       = { for name, queue in aws_sqs_queue.consumer : name => queue.arn }
}

output "queue_names" {
  description = "Consumer queue name for CloudWatch metrics"
  value       = { for name, queue in aws_sqs_queue.consumer : name => queue.name }
}

output "dlq_urls" {
  description = "Dead-letter queue URL by consumer name"
  value       = { for name, queue in aws_sqs_queue.dlq : name => queue.url }
}

output "alert_subscription_arns" {
  description = "Subscription ARNs for readiness checks; confirmation still requires the recipients"
  value       = [for subscription in aws_sns_topic_subscription.email : subscription.arn]
}
