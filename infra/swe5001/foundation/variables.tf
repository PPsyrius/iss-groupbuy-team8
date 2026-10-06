# Non-secret values live in ../envs/demo.tfvars. Secrets are never Terraform inputs:
# an operator seeds them in SSM (../README.md, "Operator-seeded parameters").

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

variable "github_owner" {
  description = "GitHub organization/user owning the repository"
  type        = string
}

variable "github_owner_id" {
  description = "Numeric GitHub owner ID (immutable OIDC subject component)"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_owner_id))
    error_message = "github_owner_id must be numeric."
  }
}

variable "github_repo" {
  description = "Repository name"
  type        = string
}

variable "github_repo_id" {
  description = "Numeric GitHub repository ID (immutable OIDC subject component)"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_repo_id))
    error_message = "github_repo_id must be numeric."
  }
}

variable "tf_state_bucket" {
  description = "Existing Terraform state bucket; SWE5001 state keys live under swe5001/"
  type        = string
}

variable "images" {
  description = "ECR repositories under swe5001/: the monolith (strangler step 0) and the three services"
  type        = set(string)
  default     = ["monolith", "identity-svc", "commerce-svc", "notification-svc"]
}

variable "enable_budget" {
  description = "Create the optional cost budget; requires payer-account activation of the Course tag and budget recipients in SSM"
  type        = bool
  default     = false
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget for resources tagged Course=swe5001"
  type        = number
  default     = 50
}

variable "budget_alert_thresholds_usd" {
  description = "Actual-spend alert thresholds in USD"
  type        = list(number)
  default     = [20, 50]
}
