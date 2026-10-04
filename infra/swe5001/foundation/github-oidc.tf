# GitHub OIDC roles for SWE5001 (AWS plan, section 4; GitHub plan, sections 3-4).
# The account-wide OIDC provider already exists from the SWE5006 bootstrap; this stack
# only looks it up. Each role trusts exactly one GitHub environment subject:
#   - swe5001-plan: any branch (PRs into dev or prod), read-only;
#   - swe5001-demo: the prod branch only (environment branch policy), apply and deploy.
#
# The roles themselves live at the root IAM path, while everything the apply role may
# create lives under /swe5001/. The apply role therefore cannot edit its own policies.

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "github_trust" {
  for_each = toset(["swe5001-plan", "swe5001-demo"])

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${local.github_repo_slug}:environment:${each.key}"]
    }
  }
}

# ---------------------------------------------------------------- plan role

data "aws_iam_policy_document" "plan" {
  statement {
    sid       = "ListSwe5001StateKeys"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["swe5001/*"]
    }
  }

  statement {
    sid       = "ReadSwe5001State"
    actions   = ["s3:GetObject"]
    resources = [local.state_objects_arn]
  }

  # ReadOnlyAccess can read any S3 object, including the SWE5006 state files. PRs from
  # any branch can assume this role, so object reads are limited to the SWE5001 state.
  statement {
    sid           = "DenyOtherObjectReads"
    effect        = "Deny"
    actions       = ["s3:GetObject", "s3:GetObjectVersion"]
    not_resources = [local.state_objects_arn]
  }
}

resource "aws_iam_role" "github_plan" {
  name                 = "${local.name_prefix}-gh-plan"
  description          = "GitHub OIDC role (swe5001-plan): read-only terraform plan for the SWE5001 stacks"
  assume_role_policy   = data.aws_iam_policy_document.github_trust["swe5001-plan"].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "github_plan_read_only" {
  role       = aws_iam_role.github_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role_policy" "github_plan" {
  name   = "swe5001-state-read"
  role   = aws_iam_role.github_plan.id
  policy = data.aws_iam_policy_document.plan.json
}

# ---------------------------------------------------------------- apply role
# Creates and destroys `runtime` and re-applies `edge` (promotion run, env-up.sh,
# env-down.sh). Service access comes from AWS managed policies, one per service the
# stacks use; IAM, SSM, S3 and state access are scoped inline. Tag-level narrowing is
# a follow-up once the first runtime apply shows the exact action set.

locals {
  apply_managed_policies = toset([
    "AmazonEC2FullAccess",         # VPC, fck-nat, security groups, ALB, CloudWatch alarms
    "AmazonECS_FullAccess",        # cluster, services, task definitions, Application Auto Scaling
    "AmazonRDSFullAccess",         # PostgreSQL instance, subnet and parameter groups
    "AmazonElastiCacheFullAccess", # Valkey replication group
    "AmazonSNSFullAccess",         # event topics
    "AmazonSQSFullAccess",         # consumer queues and DLQs
    "CloudWatchFullAccessV2",      # log groups, dashboard, alarms
    "AWSCloudMapFullAccess",       # Service Connect namespace
    "CloudFrontFullAccess",        # edge distribution and functions
  ])
}

data "aws_iam_policy_document" "apply" {
  statement {
    sid       = "ListSwe5001StateKeys"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["swe5001/*"]
    }
  }

  statement {
    sid       = "ReadWriteSwe5001State"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = [local.state_objects_arn]
  }

  statement {
    sid = "ManageSpaBucket"
    actions = [
      "s3:CreateBucket",
      "s3:DeleteBucket",
      "s3:DeleteBucketPolicy",
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:GetAccelerateConfiguration",
      "s3:GetBucketAcl",
      "s3:GetBucketCORS",
      "s3:GetBucketLogging",
      "s3:GetBucketObjectLockConfiguration",
      "s3:GetBucketOwnershipControls",
      "s3:GetBucketPolicy",
      "s3:GetBucketPublicAccessBlock",
      "s3:GetBucketRequestPayment",
      "s3:GetBucketTagging",
      "s3:GetBucketVersioning",
      "s3:GetBucketWebsite",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:GetReplicationConfiguration",
      "s3:ListBucket",
      "s3:ListBucketVersions",
      "s3:PutBucketOwnershipControls",
      "s3:PutBucketPolicy",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:PutBucketVersioning",
      "s3:PutEncryptionConfiguration",
    ]
    resources = [
      "arn:aws:s3:::${local.spa_bucket_name}",
      "arn:aws:s3:::${local.spa_bucket_name}/*",
    ]
  }

  # Roles under /swe5001/ (task, execution and fck-nat roles) are bounded by
  # aws_iam_policy.workload_boundary, so the apply role cannot mint a role more powerful
  # than a SWE5001 workload and pass it to a task or instance.
  statement {
    sid = "ManageSwe5001Roles"
    actions = [
      "iam:AddRoleToInstanceProfile",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetInstanceProfile",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:ListRolePolicies",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
    ]
    resources = [
      "arn:aws:iam::${var.aws_account_id}:role${local.iam_role_path}*",
      "arn:aws:iam::${var.aws_account_id}:instance-profile${local.iam_role_path}*",
    ]
  }

  statement {
    sid       = "CreateBoundedSwe5001Roles"
    actions   = ["iam:CreateRole", "iam:PutRolePolicy"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role${local.iam_role_path}*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.workload_boundary.arn]
    }
  }

  statement {
    sid       = "AttachAllowlistedPolicies"
    actions   = ["iam:AttachRolePolicy"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role${local.iam_role_path}*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.workload_boundary.arn]
    }

    condition {
      test     = "ArnEquals"
      variable = "iam:PolicyARN"
      values   = local.workload_attachable_policies
    }
  }

  statement {
    sid       = "PassSwe5001Roles"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role${local.iam_role_path}*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com", "ecs-tasks.amazonaws.com"]
    }
  }

  statement {
    sid       = "DenyBoundaryChanges"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary", "iam:PutRolePermissionsBoundary"]
    resources = ["*"]
  }

  statement {
    sid       = "CreateServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role/aws-service-role/*"]

    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values = [
        "ecs.amazonaws.com",
        "ecs.application-autoscaling.amazonaws.com",
        "elasticache.amazonaws.com",
        "elasticloadbalancing.amazonaws.com",
        "rds.amazonaws.com",
      ]
    }
  }

  statement {
    sid = "ManageSwe5001Parameters"
    actions = [
      "ssm:AddTagsToResource",
      "ssm:DeleteParameter",
      "ssm:DeleteParameters",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:ListTagsForResource",
      "ssm:PutParameter",
      "ssm:RemoveTagsFromResource",
    ]
    resources = ["${local.ssm_arn}/*"]
  }

  # Guardrail for the shared account: the managed policies above would also reach the
  # SWE5006 EC2 host, its ALB and its CloudFront distribution. Mutating or deleting a
  # resource that is not tagged Course=swe5001 is denied. Creation is unaffected
  # because the stacks tag every resource at creation through provider default_tags.
  statement {
    sid    = "DenyChangesOutsideSwe5001"
    effect = "Deny"
    actions = [
      "cloudfront:DeleteDistribution",
      "cloudfront:UpdateDistribution",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:DeleteInternetGateway",
      "ec2:DeleteRouteTable",
      "ec2:DeleteSecurityGroup",
      "ec2:DeleteSubnet",
      "ec2:DeleteVpc",
      "ec2:ModifyInstanceAttribute",
      "ec2:ModifySecurityGroupRules",
      "ec2:RebootInstances",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:StopInstances",
      "ec2:TerminateInstances",
      "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:DeleteLoadBalancer",
      "elasticloadbalancing:DeleteRule",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:DeregisterTargets",
      "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:ModifyLoadBalancerAttributes",
      "elasticloadbalancing:ModifyRule",
      "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:RegisterTargets",
    ]
    resources = ["*"]

    condition {
      test     = "StringNotEquals"
      variable = "aws:ResourceTag/Course"
      values   = ["swe5001"]
    }
  }

  # CloudWatchFullAccessV2 also reaches the SWE5006 log groups (/blocbuy/backend/*).
  # Log groups carry no Course tag in the SWE5006 stacks, so this deny goes by name:
  # only /blocbuy/swe5001/* may be deleted or reconfigured.
  statement {
    sid    = "DenyLogGroupChangesOutsideSwe5001"
    effect = "Deny"
    actions = [
      "logs:AssociateKmsKey",
      "logs:DeleteLogGroup",
      "logs:DeleteLogStream",
      "logs:DeleteMetricFilter",
      "logs:DeleteRetentionPolicy",
      "logs:DeleteSubscriptionFilter",
      "logs:DisassociateKmsKey",
      "logs:PutMetricFilter",
      "logs:PutRetentionPolicy",
      "logs:PutSubscriptionFilter",
    ]
    not_resources = ["arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/blocbuy/swe5001/*"]
  }

  # Same guardrail for RDS and ElastiCache. Resources are limited to instances, clusters
  # and replication groups: AWS-created default option and parameter groups carry no
  # tags and take part in Modify calls, so a "*" deny would block SWE5001's own changes.
  statement {
    sid    = "DenyDataStoreChangesOutsideSwe5001"
    effect = "Deny"
    actions = [
      "elasticache:DeleteCacheCluster",
      "elasticache:DeleteReplicationGroup",
      "elasticache:ModifyCacheCluster",
      "elasticache:ModifyReplicationGroup",
      "elasticache:RebootCacheCluster",
      "rds:DeleteDBCluster",
      "rds:DeleteDBInstance",
      "rds:ModifyDBCluster",
      "rds:ModifyDBInstance",
      "rds:RebootDBInstance",
      "rds:StopDBCluster",
      "rds:StopDBInstance",
    ]
    resources = [
      "arn:aws:elasticache:${var.aws_region}:${var.aws_account_id}:cluster:*",
      "arn:aws:elasticache:${var.aws_region}:${var.aws_account_id}:replicationgroup:*",
      "arn:aws:rds:${var.aws_region}:${var.aws_account_id}:cluster:*",
      "arn:aws:rds:${var.aws_region}:${var.aws_account_id}:db:*",
    ]

    condition {
      test     = "StringNotEquals"
      variable = "aws:ResourceTag/Course"
      values   = ["swe5001"]
    }
  }
}

resource "aws_iam_role" "github_apply" {
  name                 = "${local.name_prefix}-gh-apply"
  description          = "GitHub OIDC role (swe5001-demo, prod only): create and destroy the SWE5001 runtime and edge"
  assume_role_policy   = data.aws_iam_policy_document.github_trust["swe5001-demo"].json
  max_session_duration = 7200
}

resource "aws_iam_role_policy_attachment" "github_apply" {
  for_each = local.apply_managed_policies

  role       = aws_iam_role.github_apply.name
  policy_arn = "arn:aws:iam::aws:policy/${each.key}"
}

resource "aws_iam_role_policy" "github_apply" {
  name   = "swe5001-scoped"
  role   = aws_iam_role.github_apply.id
  policy = data.aws_iam_policy_document.apply.json
}

# ---------------------------------------------------------------- deploy role
# Publishes images, rolls ECS services, records candidate and release digests, and
# publishes the SPA (GitHub plan, section 4).

data "aws_iam_policy_document" "deploy" {
  statement {
    sid       = "AuthenticateToEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushSwe5001Images"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [for r in aws_ecr_repository.image : r.arn]
  }

  # Task definition actions do not support resource-level permissions.
  statement {
    sid       = "RegisterTaskDefinitions"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid     = "RollSwe5001Services"
    actions = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [
      "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:service/${local.ecs_cluster}/*",
    ]
  }

  statement {
    sid       = "PassSwe5001TaskRoles"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${var.aws_account_id}:role${local.iam_role_path}*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  statement {
    sid     = "RecordReleaseDigests"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:PutParameter"]
    resources = [
      "${local.ssm_arn}/candidate/*",
      "${local.ssm_arn}/release/*",
    ]
  }

  statement {
    sid       = "ListSpaBucket"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.spa_bucket_name}"]
  }

  statement {
    sid       = "SyncSpaObjects"
    actions   = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
    resources = ["arn:aws:s3:::${local.spa_bucket_name}/*"]
  }

  statement {
    sid       = "InvalidateSwe5001Distribution"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = ["arn:aws:cloudfront::${var.aws_account_id}:distribution/*"]

    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Course"
      values   = ["swe5001"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "${local.name_prefix}-gh-deploy"
  description          = "GitHub OIDC role (swe5001-demo, prod only): publish images and roll the SWE5001 ECS services"
  assume_role_policy   = data.aws_iam_policy_document.github_trust["swe5001-demo"].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "swe5001-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
