provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "blocbuy"
      Course    = "swe5001"
      Stack     = "foundation"
      ManagedBy = "terraform"
    }
  }
}

locals {
  # Immutable OIDC subject prefix for this repository (post-2026-07 GitHub format).
  github_repo_slug = "repo:${var.github_owner}@${var.github_owner_id}/${var.github_repo}@${var.github_repo_id}"

  name_prefix = "blocbuy-swe5001"
  ssm_prefix  = "/blocbuy/swe5001"
  ssm_arn     = "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:parameter${local.ssm_prefix}"

  state_bucket_arn  = "arn:aws:s3:::${var.tf_state_bucket}"
  state_objects_arn = "${local.state_bucket_arn}/swe5001/*"

  # Created by later stacks; named here so the roles can be scoped before they exist.
  spa_bucket_name = "${local.name_prefix}-spa-${var.aws_account_id}"
  ecs_cluster     = local.name_prefix
  iam_role_path   = "/swe5001/"
}
