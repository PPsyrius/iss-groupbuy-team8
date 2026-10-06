mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_data "aws_ssm_parameter" {
    defaults = {
      value = "operator@example.com"
    }
  }
}

variables {
  aws_account_id  = "402178232802"
  aws_region      = "ap-southeast-1"
  github_owner    = "PPsyrius"
  github_owner_id = "19505219"
  github_repo     = "iss-groupbuy-team8"
  github_repo_id  = "1344446887"
  tf_state_bucket = "blocbuy-tfstate-402178232802"
}

run "bootstrap_without_billing" {
  command = plan

  assert {
    condition     = length(aws_budgets_budget.monthly) == 0 && length(data.aws_ssm_parameter.budget_alert_emails) == 0
    error_message = "Default bootstrap must neither create a budget nor read budget recipients from SSM."
  }
}

run "optional_budget" {
  command = plan

  variables {
    enable_budget = true
  }

  assert {
    condition     = length(aws_budgets_budget.monthly) == 1 && length(data.aws_ssm_parameter.budget_alert_emails) == 1
    error_message = "Enabling the budget must create it and read the recipient parameter."
  }

  assert {
    condition     = local.budget_alert_emails == tolist(["operator@example.com"])
    error_message = "The enabled budget must use the operator-seeded recipients."
  }
}
