output "frontend_bucket" {
  value = aws_s3_bucket.frontend.id
}

output "distribution_id" {
  value = aws_cloudfront_distribution.dev.id
}

output "distribution_domain" {
  value = aws_cloudfront_distribution.dev.domain_name
}

output "deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
