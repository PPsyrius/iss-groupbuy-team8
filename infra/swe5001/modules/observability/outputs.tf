output "dashboard_name" {
  value = aws_cloudwatch_dashboard.this.dashboard_name
}

output "dashboard_body" {
  description = "Non-secret dashboard configuration, also inspected by offline tests"
  value       = aws_cloudwatch_dashboard.this.dashboard_body
}
