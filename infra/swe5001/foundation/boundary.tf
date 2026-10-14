# Permissions boundary for every role the apply role creates under /swe5001/ (ECS task
# and execution roles, the fck-nat instance role). It is the ceiling of what a SWE5001
# workload may do; a runtime role that needs more requires a reviewed change here,
# applied by an operator. The policy lives at the root path, outside the apply role's
# IAM scope, so the apply role cannot edit it.

locals {
  # Managed policies the apply role may attach to a bounded role.
  workload_attachable_policies = [
    "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy",
  ]
}

data "aws_iam_policy_document" "workload_boundary" {
  statement {
    sid       = "AuthenticateToEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid       = "PullSwe5001Images"
    actions   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
    resources = [for r in aws_ecr_repository.image : r.arn]
  }

  statement {
    sid     = "WriteSwe5001Logs"
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/blocbuy/swe5001/*",
      "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/blocbuy/swe5001/*:log-stream:*",
    ]
  }

  statement {
    sid       = "ReadSwe5001Parameters"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = ["${local.ssm_arn}/*"]
  }

  statement {
    sid       = "DecryptParametersViaSsm"
    actions   = ["kms:Decrypt"]
    resources = ["arn:aws:kms:${var.aws_region}:${var.aws_account_id}:key/*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.aws_region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "PublishSwe5001Events"
    actions   = ["sns:Publish"]
    resources = ["arn:aws:sns:${var.aws_region}:${var.aws_account_id}:${local.name_prefix}-*"]
  }

  # SNS's AWS-managed key; no CMK lifecycle. Only SNS may use the key on behalf of
  # a publisher, and only with the exact course domain-topic encryption context.
  statement {
    sid       = "UseDomainEventKeyViaSns"
    actions   = ["kms:GenerateDataKey*", "kms:Decrypt"]
    resources = ["arn:aws:kms:${var.aws_region}:${var.aws_account_id}:key/*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["sns.${var.aws_region}.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:EncryptionContext:aws:sns:topicArn"
      values   = ["arn:aws:sns:${var.aws_region}:${var.aws_account_id}:${local.name_prefix}-domain-events"]
    }
  }

  statement {
    sid = "ConsumeSwe5001Events"
    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:ReceiveMessage",
    ]
    resources = ["arn:aws:sqs:${var.aws_region}:${var.aws_account_id}:${local.name_prefix}-*"]
  }

  statement {
    sid       = "PublishSwe5001Metrics"
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["BlocBuy/SWE5001"]
    }
  }

  # ECS Exec, used only when enable_exec = true (AWS plan, section 6.4).
  statement {
    sid = "EcsExecChannels"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }

  # fck-nat attaches its static ENI and Elastic IP at boot.
  statement {
    sid       = "DescribeNatResources"
    actions   = ["ec2:DescribeAddresses", "ec2:DescribeNetworkInterfaces"]
    resources = ["*"]
  }

  statement {
    sid = "AttachSwe5001NatResources"
    actions = [
      "ec2:AssociateAddress",
      "ec2:AttachNetworkInterface",
      "ec2:DisassociateAddress",
      "ec2:ModifyNetworkInterfaceAttribute",
    ]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Course"
      values   = ["swe5001"]
    }
  }
}

resource "aws_iam_policy" "workload_boundary" {
  name        = "${local.name_prefix}-workload-boundary"
  description = "Permissions boundary for SWE5001 runtime roles under /swe5001/"
  policy      = data.aws_iam_policy_document.workload_boundary.json
}
