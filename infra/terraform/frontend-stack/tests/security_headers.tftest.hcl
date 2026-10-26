# Mocked provider only: no AWS credentials, remote state or cloud mutations.
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "402178232802"
    }
  }
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{}"
    }
  }
}

variables {
  aws_account_id  = "402178232802"
  aws_region      = "ap-southeast-1"
  github_owner    = "test-owner"
  github_owner_id = "1"
  github_repo     = "test-repo"
  github_repo_id  = "2"
  oac_id          = "test-oac"
  alb_dns         = "test-alb.example.test"
  origin_secret   = "test-only-placeholder"
}

# The production module retains historical import blocks. Supply fake imported
# identities explicitly; no provider import/read is allowed to contact AWS.
override_resource {
  target = aws_s3_bucket.frontend
  values = { id = "test-bucket", arn = "arn:aws:s3:::test-bucket", bucket_regional_domain_name = "test-bucket.s3.ap-southeast-1.amazonaws.com" }
}
override_resource {
  target = aws_s3_bucket_policy.frontend
}
override_resource {
  target = aws_s3_bucket_public_access_block.frontend
}
override_resource {
  target = aws_cloudfront_origin_access_control.frontend
  values = { id = "test-oac" }
}
override_resource {
  target = aws_cloudfront_function.spa_fallback
  values = { arn = "arn:aws:cloudfront::402178232802:function/test-function" }
}
override_resource {
  target = aws_cloudfront_response_headers_policy.security
  values = { id = "test-policy" }
}
override_resource {
  target = aws_cloudfront_distribution.dev
}
override_resource {
  target = aws_iam_role.github_deploy
}
override_resource {
  target = aws_iam_role_policy.github_deploy
}

run "rendered_security_headers" {
  command = plan

  assert {
    condition     = aws_cloudfront_response_headers_policy.security.security_headers_config[0].content_security_policy[0].content_security_policy == jsondecode(file("../../../deployment/frontend/cloudfront/response-headers-policy.json")).SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy
    error_message = "The planned CSP must match the canonical JSON, including the image allowlist."
  }

  assert {
    condition = one([
      for header in aws_cloudfront_response_headers_policy.security.custom_headers_config[0].items : header.value
      if header.header == "Permissions-Policy"
    ]) == "camera=(), microphone=(), geolocation=(), payment=(), usb=()"
    error_message = "The planned policy must actually emit Permissions-Policy."
  }

  assert {
    condition = alltrue([
      for header in aws_cloudfront_response_headers_policy.security.custom_headers_config[0].items : header.override
    ])
    error_message = "Custom headers must replace conflicting origin values."
  }

  assert {
    condition = (
      aws_cloudfront_distribution.dev.default_cache_behavior[0].response_headers_policy_id == aws_cloudfront_response_headers_policy.security.id &&
      one([
        for behavior in aws_cloudfront_distribution.dev.ordered_cache_behavior : behavior.response_headers_policy_id
        if behavior.path_pattern == "/api/*"
      ]) == aws_cloudfront_response_headers_policy.security.id
    )
    error_message = "The planned SPA and API behaviors must both attach the security policy."
  }
}
