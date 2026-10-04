# One-off task that creates the service databases, their owner and app roles and the
# `domain` schema (AWS plan, section 6.5). env-up.sh runs it with `aws ecs run-task` once
# RDS is available; the outputs below carry the network settings it needs. The script is
# idempotent, so a re-run converges.

resource "aws_cloudwatch_log_group" "db_init" {
  # checkov:skip=CKV_AWS_158: AWS-owned encryption; no customer-managed keys in a disposable stack
  # checkov:skip=CKV_AWS_338: 7-day retention; the environment lives about an hour
  name              = "/blocbuy/swe5001/db-init"
  retention_in_days = 7
}

locals {
  db_init_secrets = merge(
    { PGPASSWORD = module.postgres.master_password_arn },
    { for k, arn in module.postgres.role_password_arns :
    "${upper(split("/", k)[0])}_${split("/", k)[1]}_PASSWORD" => arn },
  )
}

resource "aws_ecs_task_definition" "db_init" {
  family                   = "${local.name_prefix}-db-init"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = module.cluster.execution_role_arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name                   = "db-init"
    image                  = var.db_init_image
    essential              = true
    readonlyRootFilesystem = true
    command                = ["sh", "-c", file("${path.module}/../db-init/init.sh")]

    environment = [
      { name = "PGHOST", value = module.postgres.address },
      { name = "PGPORT", value = tostring(module.postgres.port) },
      { name = "PGUSER", value = module.postgres.master_username },
      { name = "PGDATABASE", value = "postgres" },
      { name = "PGSSLMODE", value = "require" },
      { name = "DATABASES", value = join(" ", var.databases) },
    ]

    secrets = [for k in sort(keys(local.db_init_secrets)) : { name = k, valueFrom = local.db_init_secrets[k] }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.db_init.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "db-init"
      }
    }
  }])
}
