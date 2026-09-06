# Security groups that reference, never enumerate (design doc §6.3): the only
# ingress for 8589 names the ALB security group, and ports 22/5432/6379 have no
# ingress rules anywhere — the host is reachable only through the ALB and SSM.

resource "aws_security_group" "alb" {
  name_prefix = "blocbuy-prod-alb-"
  description = "BlocBuy prod ALB: public entry point"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "Public entry from the internet (443 with TLS, 80 interim without)"
    from_port   = local.alb_listener_port
    to_port     = local.alb_listener_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
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

# Interim HTTP :80 listener when no ACM certificate exists yet; switching to
# HTTPS :443 is set acm_certificate_arn in tfvars and re-planning (design doc §6.4).
# Stripe live mode requires the HTTPS switch before real users.
# tfsec:ignore:aws-0054
resource "aws_lb_listener" "backend" {
  load_balancer_arn = aws_lb.backend.arn
  port              = local.alb_listener_port
  protocol          = local.alb_listener_protocol
  certificate_arn   = var.acm_certificate_arn != "" ? var.acm_certificate_arn : null

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
