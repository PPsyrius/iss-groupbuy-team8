variable "name" {
  description = "Disposable course dashboard name"
  type        = string
}

variable "aws_region" {
  description = "Region containing origin, service and messaging metrics"
  type        = string
}

variable "cloudfront_distribution_id" {
  description = "Persistent edge distribution ID; its metrics use us-east-1/Global"
  type        = string
}

variable "alb_arn_suffix" {
  description = "LoadBalancer metric dimension (app/name/id)"
  type        = string
}

variable "cluster_name" {
  description = "ECS ClusterName metric dimension"
  type        = string
}

variable "target_groups" {
  description = "Service name to TargetGroup ARN suffix"
  type        = map(string)
}

variable "topic_name" {
  description = "Domain-event TopicName metric dimension"
  type        = string
}

variable "queue_names" {
  description = "Consumer name to SQS queue name"
  type        = map(string)
}

variable "dlq_names" {
  description = "Consumer name to SQS dead-letter queue name"
  type        = map(string)
}
