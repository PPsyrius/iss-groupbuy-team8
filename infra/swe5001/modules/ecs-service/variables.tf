variable "name" {
  description = "Service name; also the container name, the Service Connect DNS name and the log group suffix"
  type        = string
}

variable "name_prefix" {
  description = "Prefix for the target group, roles and task family, e.g. blocbuy-swe5001"
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name"
  type        = string
}

variable "namespace_arn" {
  description = "Service Connect namespace ARN"
  type        = string
}

variable "image" {
  description = "Container image pinned by digest, <repository>@sha256:<digest>"
  type        = string

  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.image))
    error_message = "image must be pinned by digest (<repository>@sha256:<64 hex>)."
  }
}

variable "cpu" {
  description = "Task CPU units"
  type        = number
  default     = 512
}

variable "memory" {
  description = "Task memory in MiB"
  type        = number
  default     = 1024
}

variable "container_port" {
  description = "Container port, also the target group and Service Connect port"
  type        = number
  default     = 8589
}

variable "health_check_path" {
  description = "Target group health check path"
  type        = string
  default     = "/api/actuator/health"
}

variable "environment" {
  description = "Plain environment variables"
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Environment variables injected from SSM: variable name to parameter ARN"
  type        = map(string)
  default     = {}
}

variable "task_policy_json" {
  description = "Inline policy for the task role; null for a role with no permissions"
  type        = string
  default     = null
}

variable "task_policy_enabled" {
  description = "Plan-time switch for an inline policy whose resource ARNs may not be known yet"
  type        = bool
  default     = false
}

variable "execution_role_arn" {
  description = "Shared execution role from the ecs-cluster module"
  type        = string
}

variable "role_path" {
  description = "IAM path for the task role; the apply role may only create roles here"
  type        = string
}

variable "permissions_boundary_arn" {
  description = "Workload permissions boundary from the foundation stack"
  type        = string
}

variable "vpc_id" {
  description = "VPC for the target group"
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets for the tasks"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups for the tasks"
  type        = list(string)
}

variable "listener_arn" {
  description = "ALB listener that receives CloudFront traffic"
  type        = string
}

variable "listener_priority" {
  description = "Listener rule priority (AWS plan, section 6.3)"
  type        = number
}

variable "path_patterns" {
  description = "Path patterns routed to this service (at most four)"
  type        = list(string)

  validation {
    condition     = length(var.path_patterns) > 0 && length(var.path_patterns) <= 4
    error_message = "path_patterns needs one to four entries: an ALB rule allows five condition values and the origin header uses one."
  }
}

variable "origin_secret" {
  description = "Value CloudFront sends in X-Origin-Secret; the rule forwards only when it matches"
  type        = string
  sensitive   = true
}

variable "desired_count" {
  description = "Initial task count; 0 during bootstrap, the service minimum after database initialisation"
  type        = number
  default     = 0
}

variable "fargate_base_capacity" {
  description = "Tasks placed on regular Fargate before weighted Spot allocation; commerce uses its configured minimum"
  type        = number
  default     = 1

  validation {
    condition     = contains([1, 2], var.fargate_base_capacity)
    error_message = "fargate_base_capacity must be 1 or 2 for the baseline/demo floor."
  }
}

variable "after_services" {
  description = "Service Connect endpoints this service calls; the service is created only after them, because a task resolves only endpoints that existed when its deployment was created"
  type        = list(string)
  default     = []
}

variable "enable_exec" {
  description = "Allow ECS Exec into the tasks"
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "CloudWatch log retention"
  type        = number
  default     = 7
}
