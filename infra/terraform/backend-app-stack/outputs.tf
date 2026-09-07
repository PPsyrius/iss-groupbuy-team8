# The only bridge to GitHub (design doc §6.8): the runbook's `gh variable set`
# step consumes exactly these values — they are derived, never retyped.

output "instance_id" {
  description = "Prod backend EC2 instance ID -> GitHub BACKEND_EC2_INSTANCE_ID_PROD"
  value       = aws_instance.backend.id
}

output "ecr_push_role_arn" {
  description = "GitHub OIDC ECR push role -> GitHub AWS_ECR_PUSH_ROLE_ARN_PROD"
  value       = aws_iam_role.github_ecr_push.arn
}

output "deploy_role_arn" {
  description = "GitHub OIDC SSM deploy role -> GitHub AWS_DEPLOY_ROLE_ARN_PROD"
  value       = aws_iam_role.github_deploy.arn
}

output "backend_url" {
  description = "ALB URL — consumed as the CloudFront origin DomainName (runbook section 6.5). NOT GitHub BACKEND_URL_PROD: that variable is the CloudFront domain, set after the distribution exists"
  value       = "http${var.acm_certificate_arn != "" ? "s" : ""}://${aws_lb.backend.dns_name}"
}

output "backend_sg_name" {
  description = "Backend security group name for post-apply ingress verification"
  value       = aws_security_group.backend.name
}

output "target_group_arn" {
  description = "ALB target group ARN for post-apply target-health verification"
  value       = aws_lb_target_group.backend.arn
}
