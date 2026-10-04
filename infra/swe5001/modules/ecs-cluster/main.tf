# ECS cluster, Service Connect namespace and the execution role shared by every task
# (AWS plan, section 6.4).

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_ecs_cluster" "this" {
  name = var.name

  # Container Insights without enhanced observability, which costs extra.
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# One task on on-demand Fargate as the base, then one on-demand for every three Spot.
resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 3
  }
}

resource "aws_service_discovery_http_namespace" "this" {
  name        = var.service_connect_namespace
  description = "ECS Service Connect namespace for ${var.name}"
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "execution" {
  name                 = "${var.name}-task-execution"
  path                 = var.role_path
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# ECR pull and log writes; the boundary narrows both to SWE5001 repositories and log groups.
resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadInjectedParameters"
    actions   = ["ssm:GetParameters"]
    resources = var.parameter_arns
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-injected-parameters"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}
