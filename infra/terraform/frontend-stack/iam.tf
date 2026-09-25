data "aws_iam_policy_document" "github_trust" {
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
      values   = ["${local.github_repo_slug}:environment:frontend-dev"]
    }
  }
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "ListFrontendBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.frontend.arn]
  }

  statement {
    sid       = "WriteFrontendObjects"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid       = "InvalidateIndexHtml"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [aws_cloudfront_distribution.dev.arn]
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "BlocBuyGitHubFrontendDeployDev"
  assume_role_policy = data.aws_iam_policy_document.github_trust.json
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "frontend-s3-cloudfront-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
