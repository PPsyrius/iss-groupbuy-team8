provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {
  lifecycle {
    postcondition {
      condition     = self.account_id == var.aws_account_id
      error_message = "The active AWS account does not match the frontend-dev account."
    }
  }
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

locals {
  bucket_name      = "blocbuy-dev-frontend-${data.aws_caller_identity.current.account_id}"
  github_repo_slug = "repo:${var.github_owner}@${var.github_owner_id}/${var.github_repo}@${var.github_repo_id}"

  # Stable across distribution updates: policy JSON must not become unknown
  # when only the ALB origin header is rotated.
  distribution_arn = "arn:aws:cloudfront::${var.aws_account_id}:distribution/E3U5W7UWDL8LRV"
}
