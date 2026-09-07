provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      ManagedBy   = "terraform"
      Environment = "prod"
      Stack       = "backend-app-stack"
    }
  }
}

locals {
  # Immutable OIDC subject prefix for this repository (post-2026-07 GitHub format).
  github_repo_slug = "repo:${var.github_owner}@${var.github_owner_id}/${var.github_repo}@${var.github_repo_id}"

  ecr_registry = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"

  # Origin-leg protocol (design doc §6.4): the CloudFront entry pattern keeps it
  # HTTP :80 (user-facing TLS is CloudFront's job); setting an ACM certificate
  # upgrades the origin leg to HTTPS :443. Switching is a tfvars + plan review
  # plus a CloudFront origin-protocol change, not a rewrite.
  alb_listener_port     = var.acm_certificate_arn != "" ? 443 : 80
  alb_listener_protocol = var.acm_certificate_arn != "" ? "HTTPS" : "HTTP"
}

# The default VPC is the environment the dev bootstrap already lives in; the prod
# stack adds only what design doc §6.3 defines (ALB + backend SGs).
data "aws_vpc" "default" {
  default = true
}

# CloudFront origin-facing managed prefix list: the ALB accepts origin traffic from
# CloudFront only (the dev entry-layer pattern, dev-cloudfront-deployment.md §3.1).
# Referencing the managed list means AWS updates the IP set automatically.
data "aws_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

data "aws_subnets" "public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "map-public-ip-on-launch"
    values = ["true"]
  }
}
