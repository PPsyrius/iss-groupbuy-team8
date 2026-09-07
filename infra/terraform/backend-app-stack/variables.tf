# Every value from design doc §4 / runbook section 0. The actual production values
# live in ../envs/prod.tfvars — never inline here.

variable "aws_account_id" {
  description = "AWS account ID that owns every resource in this stack"
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must contain exactly 12 digits."
  }
}

variable "aws_region" {
  description = "AWS region for every resource in this stack"
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

variable "ecr_repository" {
  description = "ECR repository name for the prod backend image (separate from dev)"
  type        = string
  default     = "prod/blocbuy"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9._/-]*$", var.ecr_repository))
    error_message = "ecr_repository must be a valid ECR repository name."
  }
}

variable "release_branch" {
  description = "Branch release tags (v*) are cut from; pinned in the ECR push trust policy"
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$", var.release_branch))
    error_message = "release_branch must be a plain branch name without refs/ prefix."
  }
}

variable "instance_type" {
  description = "EC2 instance type (prod floor t3.medium: Spring Boot + PostgreSQL + Redis on one host)"
  type        = string
  default     = "t3.medium"
}

variable "ami_id" {
  description = "Pinned Ubuntu 24.04 AMI ID — deliberately NOT a wildcard lookup (runbook section 0)"
  type        = string

  validation {
    condition     = can(regex("^ami-[a-f0-9]{8,17}$", var.ami_id))
    error_message = "ami_id must be a pinned AMI ID (ami-...); pin one in envs/prod.tfvars before planning."
  }
}

variable "key_name" {
  description = "Existing EC2 key pair for break-glass SSH; empty string = SSM-only access"
  type        = string
  default     = ""
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for the ALB listener (origin leg). Empty = CloudFront entry pattern: CloudFront built-in TLS, HTTP :80 origin. Setting it later upgrades the origin leg to HTTPS (design doc §6.4)."
  type        = string
  default     = ""

  validation {
    condition     = var.acm_certificate_arn == "" || can(regex("^arn:aws:acm:", var.acm_certificate_arn))
    error_message = "acm_certificate_arn must be empty or an ACM certificate ARN."
  }
}
