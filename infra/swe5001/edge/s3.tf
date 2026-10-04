# Private SPA bucket, readable only by this distribution through origin access control.
# The deploy workflow syncs the built SPA into it, so nothing here is precious.

# Access logging is off for the course demo.
# tfsec:ignore:aws-0089
resource "aws_s3_bucket" "spa" {
  # checkov:skip=CKV_AWS_18: access logging is off for the course demo
  # checkov:skip=CKV_AWS_21: versioning is off; every object is rebuilt by the deploy workflow
  # checkov:skip=CKV_AWS_144: no cross-region replication for a rebuildable SPA
  # checkov:skip=CKV_AWS_145: SSE-S3, as for the SWE5006 frontend bucket; no customer-managed keys
  # checkov:skip=CKV2_AWS_61: no lifecycle rules; the deploy sync deletes stale objects
  # checkov:skip=CKV2_AWS_62: no event notifications needed
  bucket        = local.spa_bucket_name
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "spa" {
  bucket                  = aws_s3_bucket.spa.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "spa" {
  bucket = aws_s3_bucket.spa.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3 (AWS-owned keys), as for the SWE5006 frontend bucket; the SPA is public content.
# tfsec:ignore:aws-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "spa" {
  bucket = aws_s3_bucket.spa.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "spa_read" {
  statement {
    sid       = "AllowCloudFrontOac"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.spa.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "spa" {
  bucket = aws_s3_bucket.spa.id
  policy = data.aws_iam_policy_document.spa_read.json

  depends_on = [aws_s3_bucket_public_access_block.spa]
}
