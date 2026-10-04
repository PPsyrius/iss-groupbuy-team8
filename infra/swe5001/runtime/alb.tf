# Internet-facing ALB, reachable only from CloudFront (security group) and only with the
# origin secret (listener rules); the default action is 403 (AWS plan, section 6.3).
# HTTP only: there is no custom domain for an ACM certificate (same limitation as SWE5006
# NFR-37; CloudFront terminates TLS for users).

# Public by design: it is the CloudFront origin.
# tfsec:ignore:aws-0053
resource "aws_lb" "this" {
  # checkov:skip=CKV_AWS_91: access logs are off for the disposable demo environment (plan section 3.3)
  # checkov:skip=CKV_AWS_150: deletion protection would block the destroy at the end of every run
  # checkov:skip=CKV2_AWS_28: no WAF; only CloudFront can reach the ALB, and only with the origin secret
  # checkov:skip=CKV2_AWS_20: no HTTPS listener to redirect to; no custom domain for an ACM certificate (NFR-37)
  name                       = local.name_prefix
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [aws_security_group.alb.id]
  subnets                    = module.network.public_subnet_ids
  drop_invalid_header_fields = true
}

# tfsec:ignore:aws-0054
resource "aws_lb_listener" "http" {
  # checkov:skip=CKV_AWS_2: HTTP origin leg; no custom domain for an ACM certificate (NFR-37)
  # checkov:skip=CKV_AWS_103: no TLS listener, see CKV_AWS_2
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "forbidden"
      status_code  = "403"
    }
  }
}

# Service-to-service endpoints are never exposed, with or without the origin secret.
resource "aws_lb_listener_rule" "block_internal" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 5

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "not found"
      status_code  = "404"
    }
  }

  condition {
    path_pattern {
      values = ["/internal/*", "/api/internal/*"]
    }
  }
}
