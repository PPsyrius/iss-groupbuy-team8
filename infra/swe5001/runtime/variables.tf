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
  description = "Service map from services.tf: none (network and data stores only) or monolith (strangler step 0)"
  type        = string
  default     = "none"

  validation {
    condition     = contains(["none", "monolith"], var.topology)
    error_message = "topology must be none or monolith."
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
