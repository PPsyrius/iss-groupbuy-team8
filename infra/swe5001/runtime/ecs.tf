module "cluster" {
  source = "../modules/ecs-cluster"

  name                      = local.name_prefix
  service_connect_namespace = "${local.name_prefix}.local"
  role_path                 = local.iam_role_path
  permissions_boundary_arn  = local.workload_boundary_arn
  parameter_arns            = [for s in local.parameter_scopes : "${local.ssm_arn}/${s}/*"]
}

locals {
  # Settings every service gets from its database and the shared cache (backend plan,
  # section 7); a service's own `environment` and `secrets` are merged on top, with
  # "{frontend_url}" in a value replaced by var.frontend_url.
  service_environment = {
    for name, s in local.services : name => merge(
      {
        DB_URL             = "jdbc:postgresql://${module.postgres.address}:${module.postgres.port}/${s.database}?currentSchema=domain&sslmode=require"
        DB_USERNAME        = "blocbuy_${s.database}_app"
        SPRING_FLYWAY_USER = "blocbuy_${s.database}_owner"
        REDIS_HOST         = module.cache.address
        REDIS_PORT         = tostring(module.cache.port)
        REDIS_SSL          = "true"
        # Valkey requires TLS (transit encryption); Spring Boot's own switch for Lettuce.
        SPRING_DATA_REDIS_SSL_ENABLED = "true"
        FRONTEND_URL                  = var.frontend_url
      },
      { for k, v in s.environment : k => replace(v, "{frontend_url}", var.frontend_url) },
      # This final map prevents any other service definition from enabling the outbox.
      { BLOCBUY_EVENTS_ENABLED = name == "commerce-svc" ? "true" : "false" },
      # SWE5001 distributed maximum: (1 + 6 + 4) * 5 = 55 runtime connections.
      # Keep the shared backend/monolith default unchanged; see load-preflight evidence.
      var.topology == "distributed" ? { SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE = "5" } : {},
    )
  }

  service_secrets = { for name, s in local.services : name => merge({
    DB_PASSWORD            = module.postgres.role_password_arns["${s.database}/APP"]
    SPRING_FLYWAY_PASSWORD = module.postgres.role_password_arns["${s.database}/OWNER"]
    REDIS_PASSWORD         = module.cache.auth_token_arn
  }, { for k, p in s.secrets : k => "${local.ssm_arn}/${p}" }) }
}

# Service Connect gives a task only the endpoints that existed when its deployment was
# created (AWS: "Service Connect components"), and the later services_enabled=true apply only
# changes the desired count. Services that call others (`connects_to`) are therefore a
# second module call, each created after the services it calls; one level is enough here.
locals {
  servers = { for k, s in local.services : k => s if length(s.connects_to) == 0 }
  clients = { for k, s in local.services : k => s if length(s.connects_to) > 0 }
}

module "service" {
  source   = "../modules/ecs-service"
  for_each = local.servers

  name                     = each.key
  name_prefix              = local.name_prefix
  cluster_name             = module.cluster.cluster_name
  namespace_arn            = module.cluster.namespace_arn
  image                    = "${local.ecr_registry}/swe5001/${each.value.repository}@${var.image_digests[each.key]}"
  environment              = local.service_environment[each.key]
  secrets                  = local.service_secrets[each.key]
  task_policy_json         = each.value.task_policy_json
  task_policy_enabled      = contains(["commerce-svc", "notification-svc"], each.key)
  execution_role_arn       = module.cluster.execution_role_arn
  role_path                = local.iam_role_path
  permissions_boundary_arn = local.workload_boundary_arn
  vpc_id                   = module.network.vpc_id
  subnet_ids               = module.network.private_subnet_ids
  security_group_ids       = [aws_security_group.svc.id]
  listener_arn             = aws_lb_listener.http.arn
  listener_priority        = each.value.listener_priority
  path_patterns            = each.value.path_patterns
  origin_secret            = data.aws_ssm_parameter.origin_secret.value
  desired_count            = var.services_enabled ? 1 : 0
  enable_exec              = var.enable_exec
}

module "client_service" {
  source   = "../modules/ecs-service"
  for_each = local.clients

  name                     = each.key
  name_prefix              = local.name_prefix
  cluster_name             = module.cluster.cluster_name
  namespace_arn            = module.cluster.namespace_arn
  image                    = "${local.ecr_registry}/swe5001/${each.value.repository}@${var.image_digests[each.key]}"
  environment              = local.service_environment[each.key]
  secrets                  = local.service_secrets[each.key]
  task_policy_json         = each.value.task_policy_json
  task_policy_enabled      = contains(["commerce-svc", "notification-svc"], each.key)
  execution_role_arn       = module.cluster.execution_role_arn
  role_path                = local.iam_role_path
  permissions_boundary_arn = local.workload_boundary_arn
  vpc_id                   = module.network.vpc_id
  subnet_ids               = module.network.private_subnet_ids
  security_group_ids       = [aws_security_group.svc.id]
  listener_arn             = aws_lb_listener.http.arn
  listener_priority        = each.value.listener_priority
  path_patterns            = each.value.path_patterns
  origin_secret            = data.aws_ssm_parameter.origin_secret.value
  desired_count            = var.services_enabled ? var.commerce_min_capacity : 0
  fargate_base_capacity    = var.commerce_min_capacity
  enable_exec              = var.enable_exec
  after_services           = [for name in each.value.connects_to : module.service[name].service_name]
}
