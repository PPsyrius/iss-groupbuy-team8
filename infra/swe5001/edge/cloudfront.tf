# CloudFront in front of the SPA bucket and the runtime ALB (AWS plan, section 5). The
# behaviours mirror the SWE5006 dev distribution (infra/terraform/frontend-stack).

locals {
  headers_policy = jsondecode(file("${path.module}/../../../deployment/frontend/cloudfront/response-headers-policy.json"))
  security       = local.headers_policy.SecurityHeadersConfig
  hsts           = local.security.StrictTransportSecurity

  # AWS managed policies.
  caching_optimized_policy_id      = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  caching_disabled_policy_id       = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  all_viewer_except_host_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
}

resource "aws_cloudfront_origin_access_control" "spa" {
  name                              = "${local.name_prefix}-spa"
  description                       = "OAC for the SWE5001 SPA bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_fallback" {
  name    = "${local.name_prefix}-spa-fallback"
  comment = "SPA fallback for the SWE5001 distribution"
  runtime = "cloudfront-js-2.0"
  code    = file("${path.module}/../../../deployment/frontend/cloudfront/spa-fallback.js")
  publish = true
}

# Same headers as the SWE5006 dev policy, under a SWE5001 name.
resource "aws_cloudfront_response_headers_policy" "security" {
  # checkov:skip=CKV_AWS_259: HSTS is on (1 year); includeSubdomains and preload do not apply to a *.cloudfront.net name
  name    = "${local.name_prefix}-security-headers"
  comment = "SWE5001: same security headers as ${local.headers_policy.Name}"

  security_headers_config {
    content_security_policy {
      content_security_policy = local.security.ContentSecurityPolicy.ContentSecurityPolicy
      override                = local.security.ContentSecurityPolicy.Override
    }

    content_type_options {
      override = local.security.ContentTypeOptions.Override
    }

    frame_options {
      frame_option = local.security.FrameOptions.FrameOption
      override     = local.security.FrameOptions.Override
    }

    referrer_policy {
      referrer_policy = local.security.ReferrerPolicy.ReferrerPolicy
      override        = local.security.ReferrerPolicy.Override
    }

    strict_transport_security {
      access_control_max_age_sec = local.hsts.AccessControlMaxAgeSec
      include_subdomains         = local.hsts.IncludeSubdomains
      override                   = local.hsts.Override
      preload                    = local.hsts.Preload
    }
  }

  remove_headers_config {
    items {
      header = local.headers_policy.RemoveHeadersConfig.Items[0].Header
    }
  }
}

# No WAF (cost; only the SPA and an origin-secret-protected API sit behind it), and the
# default *.cloudfront.net certificate, which fixes the minimum protocol at TLSv1.
# tfsec:ignore:aws-0011
# tfsec:ignore:aws-0013
# tfsec:ignore:aws-0010
resource "aws_cloudfront_distribution" "this" {
  # checkov:skip=CKV_AWS_68: no WAF for the course demo; the API needs the origin secret
  # checkov:skip=CKV2_AWS_47: no WAF, see CKV_AWS_68
  # checkov:skip=CKV_AWS_86: access logging is off for the course demo
  # checkov:skip=CKV_AWS_174: the default CloudFront certificate allows only TLSv1 as the minimum
  # checkov:skip=CKV2_AWS_42: no custom domain, so no custom certificate
  # checkov:skip=CKV_AWS_310: one origin per behaviour; the API is down by design between sessions
  # checkov:skip=CKV_AWS_374: no geo restriction for a public demo
  enabled             = true
  comment             = "BlocBuy SWE5001 - SPA from S3, /api/* to the ECS runtime ALB"
  default_root_object = "index.html"
  http_version        = "http2"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_200"

  origin {
    origin_id                = "s3-spa"
    domain_name              = aws_s3_bucket.spa.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.spa.id
  }

  origin {
    origin_id   = "alb-runtime"
    domain_name = local.api_origin_domain

    # The provider hashes the whole origin set; a sensitive mark on this member makes
    # every plan show a false origin replacement (same workaround as the SWE5006
    # frontend-stack). The edge state stays private to the operator and the apply role.
    custom_header {
      name  = "X-Origin-Secret"
      value = nonsensitive(data.aws_ssm_parameter.origin_secret.value)
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id           = "s3-spa"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.caching_optimized_policy_id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  # Cookies, query strings and every viewer header except Host (including Authorization
  # and Stripe-Signature) reach the API; nothing is cached.
  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = "alb-runtime"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.caching_disabled_policy_id
    origin_request_policy_id   = local.all_viewer_except_host_policy_id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
    minimum_protocol_version       = "TLSv1"
  }
}
