# Shared non-secret values come from ../envs/demo.tfvars; values that differ per run
# (image digests, services_enabled) are passed by the environment scripts.

variable "aws_account_id" {
  description = "AWS account ID that owns every SWE5001 resource"
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must contain exactly 12 digits."
  }
}

variable "aws_region" {
  description = "AWS region for every SWE5001 resource"
  type        = string

  validation {
    condition     = can(regex("^[a-z]{2}(-gov)?-[a-z]+-[0-9]$", var.aws_region))
    error_message = "aws_region must look like a valid AWS region, e.g. ap-southeast-1."
  }
}

# Declared so the shared var file applies without warnings; unused by this stack.
variable "github_owner" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "github_owner_id" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "github_repo" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "github_repo_id" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "tf_state_bucket" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "postgres_major_version" {
  description = "RDS PostgreSQL major version; fall back to 17 if 18 is not offered in the region"
  type        = string
  default     = "18"
}

variable "valkey_version" {
  description = "ElastiCache Valkey engine version"
  type        = string
  default     = "8.0"
}

variable "databases" {
  description = "Databases (and SSM scopes) created by db-init, each with an owner and an app role"
  type        = list(string)
  default     = ["identity", "commerce", "notification"]
}

variable "db_init_image" {
  description = "Image for the one-off db-init task; its psql major version must match the server"
  type        = string
  default     = "public.ecr.aws/docker/library/postgres:18-alpine"
}

variable "services" {
  description = <<-EOT
    ECS services behind the ALB. Empty until strangler step 0 adds the monolith
    (#376, PR 6); the service split replaces it with identity-svc, commerce-svc and
    notification-svc (PR 9). The golden-path module supplies DB_*, SPRING_FLYWAY_*
    and REDIS_* from the service's database; `secrets` maps an environment variable to
    a parameter name under /blocbuy/swe5001/.
  EOT
  type = map(object({
    repository        = string
    database          = string
    listener_priority = number
    path_patterns     = list(string)
    environment       = optional(map(string), {})
    secrets           = optional(map(string), {})
    task_policy_json  = optional(string)
  }))
  default = {}

  validation {
    condition     = alltrue([for s in values(var.services) : s.listener_priority > 5])
    error_message = "Priorities up to 5 are reserved for the /internal/* block rule."
  }
}

variable "image_digests" {
  description = "Image digest (sha256:...) per service, from SSM /blocbuy/swe5001/release/* or /candidate/*"
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for d in values(var.image_digests) : can(regex("^sha256:[0-9a-f]{64}$", d))])
    error_message = "Every image digest must look like sha256:<64 hex>; services are never deployed by tag."
  }
}

variable "services_enabled" {
  description = "false: services exist with desired count 0 (before db-init); true: desired count 1"
  type        = bool
  default     = false
}

variable "enable_exec" {
  description = "Allow ECS Exec into service tasks for debugging"
  type        = bool
  default     = false
}
