# Golden path for one SWE5001 service on Fargate: log group, task role, task definition,
# target group, listener rule and service with Service Connect (AWS plan, section 6.4).
# Scaling policies are added with the observability work (R6).

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

locals {
  full_name = "${var.name_prefix}-${var.name}"

  environment = merge({ JAVA_TOOL_OPTIONS = "-XX:MaxRAMPercentage=75" }, var.environment)
}

resource "aws_cloudwatch_log_group" "this" {
  # checkov:skip=CKV_AWS_158: AWS-owned encryption; no customer-managed keys in a disposable stack
  # checkov:skip=CKV_AWS_338: 7-day retention; logs needed later go into the promotion evidence artifact
  name              = "/blocbuy/swe5001/${var.name}"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "assume" {
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

resource "aws_iam_role" "task" {
  name                 = "${local.full_name}-task"
  path                 = var.role_path
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy" "task" {
  count = var.task_policy_json == null ? 0 : 1

  name   = "service"
  role   = aws_iam_role.task.id
  policy = var.task_policy_json
}

data "aws_iam_policy_document" "exec" {
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
}

resource "aws_iam_role_policy" "exec" {
  count = var.enable_exec ? 1 : 0

  name   = "ecs-exec"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.exec.json
}

resource "aws_ecs_task_definition" "this" {
  family                   = local.full_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  # The root filesystem is read-only; Tomcat and the JVM write only to /tmp.
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([{
    name                   = var.name
    image                  = var.image
    essential              = true
    readonlyRootFilesystem = true
    stopTimeout            = 30

    portMappings = [{
      name          = "http"
      containerPort = var.container_port
      protocol      = "tcp"
      appProtocol   = "http"
    }]

    environment = [for k in sort(keys(local.environment)) : { name = k, value = local.environment[k] }]
    secrets     = [for k in sort(keys(var.secrets)) : { name = k, valueFrom = var.secrets[k] }]

    mountPoints = [{
      sourceVolume  = "tmp"
      containerPath = "/tmp"
      readOnly      = false
    }]

    linuxParameters = {
      initProcessEnabled = var.enable_exec
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = var.name
      }
    }
  }])
}

resource "aws_lb_target_group" "this" {
  # checkov:skip=CKV_AWS_378: ALB-to-task traffic stays inside the private VPC; TLS ends at CloudFront
  name                 = local.full_name
  port                 = var.container_port
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 30

  health_check {
    path                = var.health_check_path
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener_rule" "this" {
  listener_arn = var.listener_arn
  priority     = var.listener_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Secret"
      values           = [var.origin_secret]
    }
  }

  condition {
    path_pattern {
      values = var.path_patterns
    }
  }
}

resource "aws_ecs_service" "this" {
  name                              = var.name
  cluster                           = var.cluster_name
  task_definition                   = aws_ecs_task_definition.this.arn
  desired_count                     = var.desired_count
  enable_execute_command            = var.enable_exec
  health_check_grace_period_seconds = 120
  propagate_tags                    = "SERVICE"
  enable_ecs_managed_tags           = true

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 3
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = var.name
    container_port   = var.container_port
  }

  service_connect_configuration {
    enabled   = true
    namespace = var.namespace_arn

    service {
      port_name      = "http"
      discovery_name = var.name

      client_alias {
        port     = var.container_port
        dns_name = var.name
      }
    }
  }

  # The target group must be attached to the listener before the service registers.
  depends_on = [aws_lb_listener_rule.this]
}
