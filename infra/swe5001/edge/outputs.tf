output "distribution_id" {
  description = "CloudFront distribution ID (SPA invalidation in the deploy workflow)"
  value       = aws_cloudfront_distribution.this.id
}

output "distribution_domain" {
  description = "Stable *.cloudfront.net domain; register the Google redirect URI and Stripe webhook against it"
  value       = aws_cloudfront_distribution.this.domain_name
}

output "url" {
  description = "Base URL for the SPA, the API and the smoke suite"
  value       = "https://${aws_cloudfront_distribution.this.domain_name}"
}

output "spa_bucket" {
  description = "SPA bucket the deploy workflow syncs into"
  value       = aws_s3_bucket.spa.bucket
}

output "api_origin_domain" {
  description = "Current /api/* origin: the runtime ALB, or the placeholder while runtime is absent"
  value       = local.api_origin_domain
}
