# Persistent entry point for the SWE5001 shape (AWS plan, section 5): the SPA bucket and
# the CloudFront distribution. Its *.cloudfront.net domain must never change, because
# the Google OAuth redirect URI and the Stripe webhook point at it.
#
# Ownership: an operator creates this stack once with admin credentials. The prod-only
# apply role may then only read it and update the distribution, which is all env-up.sh
# needs to point /api/* at a new ALB. CloudFront origin access controls and response
# headers policies cannot be scoped by name or tag, so the apply role gets no write
# access to them.

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "blocbuy"
      Course    = "swe5001"
      Stack     = "edge"
      ManagedBy = "terraform"
    }
  }
}

locals {
  name_prefix     = "blocbuy-swe5001"
  spa_bucket_name = "${local.name_prefix}-spa-${var.aws_account_id}"

  # Empty while runtime has never been applied or has been destroyed.
  runtime_alb_dns_name = try(data.terraform_remote_state.runtime.outputs.alb_dns_name, null)
  api_origin_domain    = coalesce(local.runtime_alb_dns_name, var.absent_alb_domain)
}

data "terraform_remote_state" "runtime" {
  backend = "s3"

  config = {
    bucket = var.tf_state_bucket
    key    = "swe5001/runtime.tfstate"
    region = var.aws_region
  }

  defaults = {
    alb_dns_name = null
  }
}

# Operator-seeded (../README.md). CloudFront sends it to the ALB, so the value is in the
# edge state; the PR-assumable plan role cannot read that state.
data "aws_ssm_parameter" "origin_secret" {
  name = "/blocbuy/swe5001/shared/ORIGIN_SECRET"
}
