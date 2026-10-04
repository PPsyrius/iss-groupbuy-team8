# Shared non-secret values come from ../envs/demo.tfvars.

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

variable "tf_state_bucket" {
  description = "Unused here; shared with foundation through ../envs/demo.tfvars"
  type        = string
  default     = null
}

variable "runtime_alb_dns_name" {
  description = "Non-secret ALB DNS name passed by env-up.sh after runtime is created; null during initial edge bootstrap"
  type        = string
  default     = null
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

variable "absent_alb_domain" {
  description = "API origin while runtime does not exist; resolves nowhere, so /api/* returns 502"
  type        = string
  default     = "runtime-absent.blocbuy-swe5001.invalid"
}
