# Decision record: frontend/docs/adr/0007-hand-managed-cloudfront-then-terraform-import.md
resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "blocbuy-dev-frontend-oac"
  description                       = "OAC for blocbuy dev frontend bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_fallback" {
  name    = "blocbuy-dev-spa-fallback"
  comment = "SPA fallback for BlocBuy dev frontend"
  runtime = "cloudfront-js-2.0"
  code    = file("${path.module}/../../../deployment/frontend/cloudfront/spa-fallback.js")
  # The provider defaults publish to true but never reads it back into imported state.
  # Keep LIVE untouched during import; publishing later requires a reviewed change.
  publish = false

  lifecycle {
    ignore_changes = [publish]
  }
}

locals {
  headers_policy = jsondecode(file("${path.module}/../../../deployment/frontend/cloudfront/response-headers-policy.json"))
  security       = local.headers_policy.SecurityHeadersConfig
  hsts           = local.security.StrictTransportSecurity
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name    = local.headers_policy.Name
  comment = local.headers_policy.Comment

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

resource "aws_cloudfront_distribution" "dev" {
  enabled             = true
  comment             = "BlocBuy dev - frontend S3 + backend ALB (/api)"
  default_root_object = "index.html"
  http_version        = "http2"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_All"

  origin {
    origin_id                = "s3-frontend"
    domain_name              = "${local.bucket_name}.s3.${var.aws_region}.amazonaws.com"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    origin_id                = "alb-backend"
    domain_name              = var.alb_dns
    origin_access_control_id = null

    # The AWS provider hashes the entire origin TypeSet; a sensitive mark on
    # this member forces a false origin replacement on every import plan.
    # Only declassify at the provider boundary. Plans/state MUST stay private.
    custom_header {
      name  = "X-Origin-Secret"
      value = nonsensitive(var.origin_secret)
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id           = "s3-frontend"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = "alb-backend"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    origin_request_policy_id   = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
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
