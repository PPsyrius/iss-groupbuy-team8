# The mocked provider does not render policy JSON, so these assertions read the
# configured statement blocks of the apply role's inline policy. Statements that use
# not_actions or not_resources have null actions or resources, hence the try() guards.
mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
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

run "apply_role_reads_parameter_metadata" {
  command = plan

  assert {
    condition = anytrue([
      for s in data.aws_iam_policy_document.apply.statement :
      s.sid == "DescribeParameters" && s.effect != "Deny" &&
      toset(s.actions) == toset(["ssm:DescribeParameters"]) && toset(s.resources) == toset(["*"])
    ])
    error_message = "The apply role must allow ssm:DescribeParameters on *, which has no resource-level permission."
  }
}

run "security_group_rule_guardrail" {
  command = plan

  assert {
    condition = alltrue([
      for s in data.aws_iam_policy_document.apply.statement :
      try(length(setintersection(s.actions, toset([
        "ec2:AuthorizeSecurityGroupEgress",
        "ec2:AuthorizeSecurityGroupIngress",
        "ec2:ModifySecurityGroupRules",
        "ec2:RevokeSecurityGroupEgress",
        "ec2:RevokeSecurityGroupIngress",
      ]))), 0) == 0
      if s.effect == "Deny" && try(contains(s.resources, "*"), false)
    ])
    error_message = "Security group rule actions must not be in a deny on *: new rules are untagged when authorised."
  }

  assert {
    condition = alltrue([
      for s in data.aws_iam_policy_document.apply.statement :
      try(length(setintersection(s.actions, toset([
        "ec2:AuthorizeSecurityGroupEgress",
        "ec2:AuthorizeSecurityGroupIngress",
      ]))), 0) == 0
      if s.effect == "Deny" && anytrue(try([for r in s.resources : strcontains(r, ":security-group-rule/")], []))
    ])
    error_message = "Authorize actions must not be denied on security-group-rule resources: new rules are untagged when authorised."
  }

  assert {
    condition = anytrue([
      for s in data.aws_iam_policy_document.apply.statement :
      s.sid == "DenyRuleChangesOutsideSwe5001" && s.effect == "Deny" &&
      toset(s.actions) == toset([
        "ec2:AuthorizeSecurityGroupEgress",
        "ec2:AuthorizeSecurityGroupIngress",
        "ec2:ModifySecurityGroupRules",
        "ec2:RevokeSecurityGroupEgress",
        "ec2:RevokeSecurityGroupIngress",
      ]) &&
      toset(s.resources) == toset(["arn:aws:ec2:*:402178232802:security-group/*"]) &&
      anytrue([
        for c in s.condition :
        c.test == "StringNotEquals" && c.variable == "aws:ResourceTag/Course" && toset(c.values) == toset(["swe5001"])
      ])
    ])
    error_message = "Rule actions must stay denied on security groups not tagged Course=swe5001 (the SWE5006 groups)."
  }
}

run "domain_event_key_boundary" {
  command = plan
  assert {
    condition = anytrue([
      for s in data.aws_iam_policy_document.workload_boundary.statement :
      s.sid == "UseDomainEventKeyViaSns" &&
      toset(s.actions) == toset(["kms:GenerateDataKey*", "kms:Decrypt"]) &&
      toset(s.resources) == toset(["arn:aws:kms:ap-southeast-1:402178232802:key/*"]) &&
      anytrue([for c in s.condition : c.variable == "kms:ViaService" && toset(c.values) == toset(["sns.ap-southeast-1.amazonaws.com"])]) &&
      anytrue([for c in s.condition : c.variable == "kms:EncryptionContext:aws:sns:topicArn" && toset(c.values) == toset(["arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-domain-events"])])
    ])
    error_message = "The boundary must allow KMS only through SNS for the exact domain-topic context."
  }

  assert {
    condition = anytrue([
      for s in data.aws_iam_policy_document.apply.statement :
      s.sid == "VerifyDomainEventKeyViaSns" &&
      toset(s.actions) == toset(["kms:GenerateDataKey*", "kms:Decrypt"]) &&
      toset(s.resources) == toset(["arn:aws:kms:ap-southeast-1:402178232802:key/*"]) &&
      anytrue([for c in s.condition : c.variable == "kms:ViaService" && toset(c.values) == toset(["sns.ap-southeast-1.amazonaws.com"])]) &&
      anytrue([for c in s.condition : c.variable == "kms:EncryptionContext:aws:sns:topicArn" && toset(c.values) == toset(["arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-domain-events"])])
    ])
    error_message = "The apply-role probe publisher needs the same restricted KMS constraints."
  }
}
