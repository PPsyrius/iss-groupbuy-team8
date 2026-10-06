# The alert recipients are personal data, so they are not committed: an operator stores
# them once as a comma-separated String parameter (../README.md).
data "aws_ssm_parameter" "budget_alert_emails" {
  count = var.enable_budget ? 1 : 0
  name  = "${local.ssm_prefix}/shared/BUDGET_ALERT_EMAILS"
}

locals {
  budget_alert_emails = var.enable_budget ? [for e in split(",", nonsensitive(data.aws_ssm_parameter.budget_alert_emails[0].value)) : trimspace(e)] : []
}

# Opt-in: linked accounts cannot activate the Course cost allocation tag themselves.
# Deployment can proceed without billing access or budget-recipient parameters.
resource "aws_budgets_budget" "monthly" {
  count        = var.enable_budget ? 1 : 0
  name         = "${local.name_prefix}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_filter {
    name   = "TagKeyValue"
    values = ["user:Course$swe5001"]
  }

  dynamic "notification" {
    for_each = toset(var.budget_alert_thresholds_usd)

    content {
      comparison_operator        = "GREATER_THAN"
      notification_type          = "ACTUAL"
      threshold                  = notification.value
      threshold_type             = "ABSOLUTE_VALUE"
      subscriber_email_addresses = local.budget_alert_emails
    }
  }
}

# Preserve the address of a budget created before it became optional.
moved {
  from = aws_budgets_budget.monthly
  to   = aws_budgets_budget.monthly[0]
}
