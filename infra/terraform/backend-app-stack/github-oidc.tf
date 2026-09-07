# GitHub OIDC roles for prod — placeholder-free by construction (design doc §6.1).
# The account-wide OIDC provider already exists from the dev bootstrap; this stack
# only looks it up. Isolation between dev and prod comes from the exact-match `sub`
# conditions, never from wildcard patterns.
#
# The ECR push role is assumed by the `publish-ecr-prod` workflow job, which publishes
# on `v*` tag pushes. Tag names vary per release, so a `ref:refs/tags/...` subject can
# never be exact-matched; the job therefore carries the stable `environment:` subject
# `backend-prod-publish` (restricted to `v*` tags in GitHub, no reviewers — the human
# approval gate stays on the deploy job's `backend-prod` environment).

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "github_ecr_push_trust" {
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
      values   = ["${local.github_repo_slug}:environment:backend-prod-publish"]
    }
  }
}

data "aws_iam_policy_document" "github_deploy_trust" {
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
      values   = ["${local.github_repo_slug}:environment:backend-prod"]
    }
  }
}

data "aws_iam_policy_document" "github_ecr_push_permissions" {
  statement {
    sid       = "AuthenticateToEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PublishOnlyBlocBuyBackendProd"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }
}

data "aws_iam_policy_document" "github_deploy_permissions" {
  statement {
    sid     = "RunOnlyAwsShellDocumentOnBackendInstance"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
      aws_instance.backend.arn,
    ]
  }

  statement {
    sid       = "ReadCommandResult"
    actions   = ["ssm:GetCommandInvocation"]
    resources = ["*"]
  }
}

resource "aws_iam_role" "github_ecr_push" {
  name                 = "BlocBuyGitHubEcrPushProd"
  description          = "GitHub OIDC role: push release-tag images to the prod ECR repository"
  assume_role_policy   = data.aws_iam_policy_document.github_ecr_push_trust.json
  max_session_duration = 3600

  inline_policy {
    name   = "ecr-push-prod-repository"
    policy = data.aws_iam_policy_document.github_ecr_push_permissions.json
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "BlocBuyGitHubBackendDeployProd"
  description          = "GitHub OIDC role: run the deployment bundle on the prod EC2 instance via SSM"
  assume_role_policy   = data.aws_iam_policy_document.github_deploy_trust.json
  max_session_duration = 3600

  inline_policy {
    name   = "ssm-deploy-prod-instance"
    policy = data.aws_iam_policy_document.github_deploy_permissions.json
  }
}
