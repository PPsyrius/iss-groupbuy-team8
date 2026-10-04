# Offline plan of the edge stack with a mocked AWS provider and an overridden runtime
# state. Run: terraform -chdir=infra/swe5001/edge test

mock_provider "aws" {
  override_data {
    target = data.aws_ssm_parameter.origin_secret
    values = {
      value = "test-origin-secret"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      arn                         = "arn:aws:s3:::blocbuy-swe5001-spa-402178232802"
      bucket_regional_domain_name = "blocbuy-swe5001-spa-402178232802.s3.ap-southeast-1.amazonaws.com"
    }
  }
}

variables {
  aws_account_id  = "402178232802"
  aws_region      = "ap-southeast-1"
  tf_state_bucket = "blocbuy-tfstate-402178232802"
}

run "runtime_absent" {
  command = plan

  override_data {
    target = data.terraform_remote_state.runtime
    values = {
      outputs = {}
    }
  }

  assert {
    condition     = local.api_origin_domain == var.absent_alb_domain
    error_message = "Without runtime, /api/* must point at the unresolvable placeholder."
  }

  assert {
    condition     = aws_s3_bucket.spa.bucket == "blocbuy-swe5001-spa-402178232802"
    error_message = "The SPA bucket name must match the foundation roles' scope."
  }
}

run "runtime_present" {
  command = plan

  override_data {
    target = data.terraform_remote_state.runtime
    values = {
      outputs = {
        alb_dns_name = "blocbuy-swe5001-123456789.ap-southeast-1.elb.amazonaws.com"
      }
    }
  }

  assert {
    condition     = local.api_origin_domain == "blocbuy-swe5001-123456789.ap-southeast-1.elb.amazonaws.com"
    error_message = "With runtime up, /api/* must point at its ALB."
  }

  assert {
    condition = anytrue([
      for o in aws_cloudfront_distribution.this.origin :
      o.origin_id == "alb-runtime" && anytrue([for h in o.custom_header : h.name == "X-Origin-Secret" && h.value == "test-origin-secret"])
    ])
    error_message = "The ALB origin must send the origin secret."
  }

  assert {
    condition = anytrue([
      for b in aws_cloudfront_distribution.this.ordered_cache_behavior :
      b.path_pattern == "/api/*" && b.target_origin_id == "alb-runtime" && b.cache_policy_id == local.caching_disabled_policy_id
    ])
    error_message = "/api/* must go to the ALB uncached."
  }
}
