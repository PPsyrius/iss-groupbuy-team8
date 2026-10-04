# Copied into GitHub variables by an operator (../README.md, "GitHub settings").

output "plan_role_arn" {
  description = "SWE5001_TF_PLAN_ROLE_ARN (environment swe5001-plan)"
  value       = aws_iam_role.github_plan.arn
}

output "apply_role_arn" {
  description = "SWE5001_TF_APPLY_ROLE_ARN (environment swe5001-demo)"
  value       = aws_iam_role.github_apply.arn
}

output "deploy_role_arn" {
  description = "SWE5001_DEPLOY_ROLE_ARN (environment swe5001-demo)"
  value       = aws_iam_role.github_deploy.arn
}

output "ecr_repository_urls" {
  description = "Image repositories, keyed by image name"
  value       = { for k, r in aws_ecr_repository.image : k => r.repository_url }
}

output "tf_state_bucket" {
  description = "SWE5001_TF_STATE_BUCKET"
  value       = var.tf_state_bucket
}
