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

variable "topology" {
  description = "Service map from services.tf: none (network and data stores only), monolith (strangler step 0, fallback) or distributed (identity-svc, commerce-svc, notification-svc)"
  type        = string
  default     = "none"

  validation {
    condition     = contains(["none", "monolith", "distributed"], var.topology)
    error_message = "topology must be none, monolith or distributed."
  }
}

variable "frontend_url" {
  description = "https:// URL of the edge distribution; services use it for FRONTEND_URL and OAuth redirects"
  type        = string
  default     = ""

  validation {
    condition     = var.topology == "none" || can(regex("^https://[a-z0-9.-]+$", var.frontend_url))
    error_message = "frontend_url must be https://<domain> without a trailing slash when services run."
  }
}

variable "db_init_image" {
  description = "Image for the one-off db-init task; its psql major version must match the server"
  type        = string
  default     = "public.ecr.aws/docker/library/postgres:18-alpine"
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

variable "v4_demo_enabled" {
  description = "Explicit V4-only commerce demo profile and bounded faster expiry sweep; never seeds data automatically. Keep false for V2/V3 and normal sessions."
  type        = bool
  default     = false

  validation {
    condition     = !var.v4_demo_enabled || var.topology == "distributed"
    error_message = "v4_demo_enabled requires the distributed topology."
  }
}

variable "services_enabled" {
  description = "Bootstrap switch: services stopped/started at their minimum, scaling suspended/enabled. Scripts own counts after creation and explicitly pause existing services before db-init."
  type        = bool
  default     = false
}

variable "notification_http_trace_logging_enabled" {
  description = "Opt-in notification-only DispatcherServlet DEBUG for an authorized routing check; request details stay masked. Never enable broad web/SQL logging or send secrets in URL query strings."
  type        = bool
  default     = false

  validation {
    condition     = !var.notification_http_trace_logging_enabled || var.topology == "distributed"
    error_message = "Notification HTTP trace logging requires distributed topology."
  }
}

variable "commerce_http_trace_logging_enabled" {
  description = "Opt-in commerce-only masked DispatcherServlet request/status logging for an authorized signed-webhook drill; no payload/header details. Restore false afterward."
  type        = bool
  default     = false

  validation {
    condition     = !var.commerce_http_trace_logging_enabled || var.topology == "distributed"
    error_message = "Commerce HTTP trace logging requires distributed topology."
  }
}

variable "commerce_min_capacity" {
  description = "Commerce task minimum: 2 for the demo/V5; set 1 for V2/V3 baseline sessions"
  type        = number
  default     = 2

  validation {
    condition     = contains([1, 2], var.commerce_min_capacity)
    error_message = "commerce_min_capacity must be 1 (baseline) or 2 (availability demo)."
  }
}

variable "commerce_requests_per_target" {
  description = "ALB requests per target per minute; provisional 200 until replaced by the measured V2 knee"
  type        = number
  default     = 200

  validation {
    condition     = var.commerce_requests_per_target > 0
    error_message = "commerce_requests_per_target must be positive."
  }
}

variable "commerce_target_slow_start_seconds" {
  description = "Warm-up for newly healthy commerce targets; does not change offered load, health checks, scaling policy or worker counts"
  type        = number
  default     = 120

  validation {
    condition     = var.commerce_target_slow_start_seconds == 0 || (var.commerce_target_slow_start_seconds >= 30 && var.commerce_target_slow_start_seconds <= 900 && floor(var.commerce_target_slow_start_seconds) == var.commerce_target_slow_start_seconds)
    error_message = "Commerce target slow start must be zero or an integer from 30 to 900 seconds."
  }
}

variable "cloudfront_distribution_id" {
  description = "Persistent edge distribution ID for the runtime dashboard; supplied by env-up.sh"
  type        = string
  default     = ""

  validation {
    condition     = var.topology != "distributed" || can(regex("^[A-Z0-9]+$", var.cloudfront_distribution_id))
    error_message = "Distributed runtime requires the edge cloudfront_distribution_id."
  }
}

variable "commerce_cpu_scale_out_enabled" {
  description = "Opt-in early CPU scale-out guard for distributed commerce; requires a reviewed performance session"
  type        = bool
  default     = false

  validation {
    condition     = !var.commerce_cpu_scale_out_enabled || var.topology == "distributed"
    error_message = "The commerce CPU scale-out guard requires distributed topology."
  }
}

variable "commerce_cpu_scale_out_threshold" {
  description = "Planning CPU headroom threshold (%), not a measured capacity or acceptance threshold"
  type        = number
  default     = 70

  validation {
    condition     = var.commerce_cpu_scale_out_threshold > 0 && var.commerce_cpu_scale_out_threshold < 100 && floor(var.commerce_cpu_scale_out_threshold) == var.commerce_cpu_scale_out_threshold
    error_message = "CPU headroom threshold must be an integer strictly between zero and 100 percent."
  }
}

variable "enable_exec" {
  description = "Allow ECS Exec into service tasks for debugging"
  type        = bool
  default     = false
}

variable "messaging_verified" {
  description = "Set true only after the stopped-service fan-out gate passes; false keeps the commerce relay off"
  type        = bool
  default     = false
}

variable "alert_email_addresses" {
  description = "Operator-supplied team alarm recipients; confirm subscriptions on every distributed session"
  type        = set(string)
  default     = []
}

variable "notification_oldest_message_age_seconds" {
  description = "Notification age alarm threshold (seconds)"
  type        = number
  default     = 60
}
