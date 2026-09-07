# Security groups that reference, never enumerate (design doc §6.3): the only
# ingress for 8589 names the ALB security group, and ports 22/5432/6379 have no
# ingress rules anywhere — the host is reachable only through the ALB and SSM.
#
# Entry layer = the dev CloudFront pattern (dev-cloudfront-deployment.md): user-facing
# TLS terminates on CloudFront's built-in certificate, CloudFront forwards to the ALB
# over HTTP with the shared X-Origin-Secret header, and the ALB's default listener
# action is 403 — the ALB DNS name is publicly resolvable but useless without the secret.

resource "aws_security_group" "alb" {
  name_prefix = "blocbuy-prod-alb-"
  description = "BlocBuy prod ALB: CloudFront origin only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Origin traffic from CloudFront only (managed prefix list)"
    from_port       = local.alb_listener_port
    to_port         = local.alb_listener_port
    protocol        = "tcp"
    prefix_list_ids = [data.aws_prefix_list.cloudfront_origin.id]
  }

  egress {
    description = "Allow forwarded traffic to the backend target"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    # Default VPC has no NAT gateway; unrestricted egress is required for the
    # backend host's apt/docker/SSM/ECR traffic. Ingress is the actual control.
    # tfsec:ignore:aws-0104
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "backend" {
  name_prefix = "blocbuy-prod-backend-"
  description = "BlocBuy prod backend EC2: backend port from the ALB only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Backend port from the ALB only"
    from_port       = 8589
    to_port         = 8589
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Package updates, ECR pull, SSM endpoints"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    # Default VPC has no NAT gateway; the host needs outbound internet at first
    # boot (apt, docker pull, SSM agent). Ingress stays 8589-from-ALB only.
    # tfsec:ignore:aws-0104
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lb_target_group" "backend" {
  name     = "blocbuy-prod-backend"
  port     = 8589
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id

  health_check {
    # The container serves everything under the /api servlet context path
    # (implementation plan §0.1) — /actuator/health would 404.
    path                = "/api/actuator/health"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# ALB access logs: the ALB is the public entry point, so request-level logging
# is the audit trail for incident investigation (design doc §12). Kept cheap:
# SSE-S3, no versioning, 90-day expiry.
resource "aws_s3_bucket" "alb_access_logs" {
  bucket = "blocbuy-prod-alb-logs-${var.aws_account_id}"

  tags = {
    Name = "blocbuy-prod-alb-access-logs"
  }
}

resource "aws_s3_bucket_public_access_block" "alb_access_logs" {
  bucket                  = aws_s3_bucket.alb_access_logs.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3 is sufficient for ALB access logs; a customer-managed KMS key is not
# worth its cost for this data.
# tfsec:ignore:aws-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "alb_access_logs_delivery" {
  statement {
    sid    = "ELBAccessLogWrite"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["logdelivery.elasticloadbalancing.amazonaws.com"]
    }

    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.alb_access_logs.arn}/blocbuy-prod/AWSLogs/${var.aws_account_id}/*"]

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }
}

resource "aws_s3_bucket_policy" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id
  policy = data.aws_iam_policy_document.alb_access_logs_delivery.json
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    id     = "expire-alb-access-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 90
    }
  }
}

# Public by design: the ALB is the internet entry point (CloudFront origin).
# tfsec:ignore:aws-0053
resource "aws_lb" "backend" {
  name               = "blocbuy-prod"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = data.aws_subnets.public.ids

  # Drop malformed headers before they reach Spring behind the host network.
  drop_invalid_header_fields = true

  # The ELB service validates bucket permissions when logging is enabled, so the
  # bucket policy must exist first.
  depends_on = [aws_s3_bucket_policy.alb_access_logs]

  access_logs {
    bucket  = aws_s3_bucket.alb_access_logs.id
    prefix  = "blocbuy-prod"
    enabled = true
  }
}

resource "aws_lb_target_group_attachment" "backend" {
  target_group_arn = aws_lb_target_group.backend.arn
  target_id        = aws_instance.backend.id
  port             = 8589
}

# The shared CloudFront->ALB origin secret (dev pattern §2/§4.1). Seeded as a
# SecureString in runbook section 3 BEFORE the first plan — this data source reads
# it at plan time, so a missing parameter fails the plan, not the apply. The value
# lands in tfstate; the state bucket is KMS-encrypted with public access fully
# blocked, which is the accepted trade-off for keeping the entry layer in IaC.
data "aws_ssm_parameter" "origin_secret" {
  name = "/blocbuy/prod/ORIGIN_SECRET"
}

# HTTP :80 listener — CloudFront terminates user-facing TLS and forwards over
# plain HTTP (OriginProtocolPolicy: http-only). Switching the origin leg to HTTPS
# later means setting acm_certificate_arn (443/HTTPS) AND changing the CloudFront
# origin protocol (dev-cloudfront-deployment.md §12).
# tfsec:ignore:aws-0054
resource "aws_lb_listener" "backend" {
  load_balancer_arn = aws_lb.backend.arn
  port              = local.alb_listener_port
  protocol          = local.alb_listener_protocol
  certificate_arn   = var.acm_certificate_arn != "" ? var.acm_certificate_arn : null

  # Default-deny: anyone who learns the ALB DNS name and connects directly (not
  # via CloudFront) gets a 403 — business endpoints are unreachable.
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "forbidden"
      status_code  = "403"
    }
  }
}

# Only requests carrying the shared origin secret are forwarded to the backend.
resource "aws_lb_listener_rule" "origin_secret" {
  listener_arn = aws_lb_listener.backend.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Secret"
      values           = [data.aws_ssm_parameter.origin_secret.value]
    }
  }
}
