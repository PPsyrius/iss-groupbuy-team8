variable "aws_account_id" {
  description = "AWS account that owns the dev frontend edge"
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must contain 12 digits."
  }
}

variable "aws_region" {
  description = "Region of the S3 bucket and the AWS provider"
  type        = string

  validation {
    condition     = can(regex("^[a-z]{2}(-gov)?-[a-z]+-[0-9]$", var.aws_region))
    error_message = "aws_region must look like a valid AWS region, e.g. ap-southeast-1."
  }
}

variable "github_owner" {
  description = "GitHub account owning the repository"
  type        = string
}

variable "github_owner_id" {
  description = "Immutable numeric GitHub owner ID in the OIDC subject"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_owner_id))
    error_message = "github_owner_id must be numeric."
  }
}

variable "github_repo" {
  description = "GitHub repository name"
  type        = string
}

variable "github_repo_id" {
  description = "Immutable numeric GitHub repository ID in the OIDC subject"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_repo_id))
    error_message = "github_repo_id must be numeric."
  }
}

variable "oac_id" {
  description = "ID of the existing blocbuy-dev-frontend-oac (discover from AWS before import)"
  type        = string

  validation {
    condition     = length(var.oac_id) > 0
    error_message = "Set oac_id to the ID of the existing CloudFront OAC."
  }
}

variable "alb_dns" {
  description = "Existing dev backend ALB origin domain name, without scheme"
  type        = string
}

variable "origin_secret" {
  description = "Existing X-Origin-Secret value used by the CloudFront ALB origin; never commit this value or a plan/state file"
  type        = string
  sensitive   = true
}
