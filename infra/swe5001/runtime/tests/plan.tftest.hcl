# Offline plan of the runtime stack with mocked providers: checks the wiring that
# `validate` cannot see (service settings, db-init secrets, desired counts) without AWS
# credentials (random stays real: it is local). Run: terraform -chdir=infra/swe5001/runtime test

mock_provider "aws" {
  override_data {
    target = data.aws_ssm_parameter.origin_secret
    values = {
      value = "test-origin-secret"
    }
  }

  override_data {
    target = module.network.data.aws_ami.fck_nat
    values = {
      id               = "ami-0123456789abcdef0"
      root_device_name = "/dev/xvda"
    }
  }

  override_data {
    target = module.network.data.aws_region.current
    values = {
      name = "ap-southeast-1"
    }
  }

  override_data {
    target = module.service.data.aws_region.current
    values = {
      name = "ap-southeast-1"
    }
  }

  override_data {
    target = module.cluster.data.aws_caller_identity.current
    values = {
      account_id = "402178232802"
    }
  }

  override_data {
    target = module.service.data.aws_caller_identity.current
    values = {
      account_id = "402178232802"
    }
  }

  override_data {
    target = module.client_service.data.aws_region.current
    values = {
      name = "ap-southeast-1"
    }
  }

  override_data {
    target = module.client_service.data.aws_caller_identity.current
    values = {
      account_id = "402178232802"
    }
  }

  # Mocked policy documents return random strings, which the IAM resources reject.
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}


variables {
  aws_account_id = "402178232802"
  aws_region     = "ap-southeast-1"
}

run "topology_none" {
  command = plan

  assert {
    condition     = length(module.service) == 0 && length(module.client_service) == 0 && length(local.databases) == 0
    error_message = "Topology none plans no service and no database."
  }

  assert {
    condition     = aws_lb_listener_rule.block_internal.priority == 5
    error_message = "The /internal/* block rule must have the lowest priority."
  }
}

run "monolith_topology" {
  command = plan

  variables {
    topology         = "monolith"
    services_enabled = true
    frontend_url     = "https://d111111abcdef8.cloudfront.net"
    image_digests = {
      monolith = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    }
  }

  assert {
    condition     = module.service["monolith"].service_name == "monolith"
    error_message = "The monolith service should be planned."
  }

  assert {
    condition     = tolist(local.databases) == tolist(["monolith"])
    error_message = "Strangler step 0 uses one database, monolith."
  }

  assert {
    condition     = local.service_environment["monolith"]["DB_USERNAME"] == "blocbuy_monolith_app" && local.service_environment["monolith"]["SPRING_FLYWAY_USER"] == "blocbuy_monolith_owner"
    error_message = "The service must connect with its database's app role and migrate with the owner role."
  }

  assert {
    condition     = local.service_environment["monolith"]["GOOGLE_REDIRECT_URI"] == "https://d111111abcdef8.cloudfront.net/api/v1/auth/oauth2/google/callback"
    error_message = "{frontend_url} must be replaced in service settings."
  }

  assert {
    condition     = local.service_environment["monolith"]["SPRING_DATA_REDIS_SSL_ENABLED"] == "true"
    error_message = "Valkey requires TLS."
  }

  assert {
    condition     = local.service_environment["monolith"]["BLOCBUY_EVENTS_ENABLED"] == "false"
    error_message = "The ECS monolith must not produce cross-service outbox events."
  }

  assert {
    condition     = local.service_secrets["monolith"]["STRIPE_API_KEY"] == "arn:aws:ssm:ap-southeast-1:402178232802:parameter/blocbuy/swe5001/commerce/STRIPE_API_KEY"
    error_message = "Service secrets must resolve to parameter ARNs under /blocbuy/swe5001/."
  }

  assert {
    condition     = toset(local.parameter_scopes) == toset(["shared", "monolith", "identity", "commerce"])
    error_message = "The execution role must read the database scope and every scope the service's secrets use."
  }

  assert {
    condition     = toset(keys(local.db_init_secrets)) == toset(["PGPASSWORD", "MONOLITH_OWNER_PASSWORD", "MONOLITH_APP_PASSWORD"])
    error_message = "db-init must receive the master password and both role passwords per database."
  }
}

run "requires_frontend_url" {
  command = plan

  variables {
    topology = "monolith"
    image_digests = {
      monolith = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    }
  }

  expect_failures = [var.frontend_url]
}

run "rejects_unpinned_image" {
  command = plan

  variables {
    topology     = "monolith"
    frontend_url = "https://d111111abcdef8.cloudfront.net"
    image_digests = {
      monolith = "latest"
    }
  }

  expect_failures = [var.image_digests]
}

run "distributed_topology" {
  command = plan

  variables {
    topology         = "distributed"
    services_enabled = true
    frontend_url     = "https://d111111abcdef8.cloudfront.net"
    image_digests = {
      identity-svc     = "sha256:1111111111111111111111111111111111111111111111111111111111111111"
      commerce-svc     = "sha256:2222222222222222222222222222222222222222222222222222222222222222"
      notification-svc = "sha256:3333333333333333333333333333333333333333333333333333333333333333"
    }
  }

  assert {
    condition     = toset(keys(module.service)) == toset(["identity-svc", "notification-svc"]) && toset(keys(module.client_service)) == toset(["commerce-svc"])
    error_message = "The distributed topology plans the three services of backend ADR 0008; commerce-svc calls identity-svc, so it is a client created after it."
  }

  assert {
    condition     = alltrue([for name, s in local.clients : length(s.connects_to) > 0 && alltrue([for target in s.connects_to : contains(keys(local.servers), target)])]) && local.clients["commerce-svc"].connects_to == ["identity-svc"]
    error_message = "A client may only call services of the first module call, so their Service Connect endpoints exist before its first deployment."
  }

  assert {
    condition     = toset(local.databases) == toset(["identity", "commerce", "notification"])
    error_message = "Each service has its own database."
  }

  assert {
    condition     = local.service_environment["commerce-svc"]["DB_USERNAME"] == "blocbuy_commerce_app" && local.service_environment["identity-svc"]["SPRING_FLYWAY_USER"] == "blocbuy_identity_owner"
    error_message = "Each service connects with its own database's app role and migrates with its owner role."
  }

  assert {
    condition     = alltrue([for name in ["identity", "commerce", "notification"] : local.service_environment["${name}-svc"]["SPRING_PROFILES_ACTIVE"] == "ec2,${name},aws"])
    error_message = "Each service runs ec2 (DB_* and REDIS_* mapping), its own profile, then aws."
  }

  assert {
    condition     = local.services["identity-svc"].listener_priority < local.services["notification-svc"].listener_priority && local.services["notification-svc"].listener_priority < local.services["commerce-svc"].listener_priority && aws_lb_listener_rule.block_internal.priority < local.services["identity-svc"].listener_priority
    error_message = "Rule order: the /internal/* block, identity, notification, then the commerce catch-all."
  }

  assert {
    condition     = local.services["commerce-svc"].path_patterns == ["/api/*"] && contains(local.services["identity-svc"].path_patterns, "/api/v1/auth/*")
    error_message = "identity-svc takes /api/v1/auth/*; commerce-svc takes the remaining /api/*."
  }

  assert {
    condition     = alltrue([for name in keys(local.services) : length("${local.name_prefix}-${name}") <= 32])
    error_message = "Target group names (blocbuy-swe5001-<service>) are limited to 32 characters."
  }

  assert {
    condition     = local.service_environment["commerce-svc"]["IDENTITY_BASE_URL"] == "http://identity-svc:8589" && !contains(keys(local.service_environment["identity-svc"]), "IDENTITY_BASE_URL") && !contains(keys(local.service_environment["notification-svc"]), "IDENTITY_BASE_URL")
    error_message = "Only commerce-svc calls identity-svc, over Service Connect."
  }

  assert {
    condition     = toset(keys(local.service_secrets["identity-svc"])) == toset(["DB_PASSWORD", "SPRING_FLYWAY_PASSWORD", "REDIS_PASSWORD", "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET"])
    error_message = "identity-svc gets the Google secrets and no Stripe secret."
  }

  assert {
    condition     = toset(keys(local.service_secrets["commerce-svc"])) == toset(["DB_PASSWORD", "SPRING_FLYWAY_PASSWORD", "REDIS_PASSWORD", "STRIPE_API_KEY", "STRIPE_WEBHOOK_SECRET"])
    error_message = "commerce-svc gets the Stripe secrets and no Google secret."
  }

  assert {
    condition     = toset(keys(local.service_secrets["notification-svc"])) == toset(["DB_PASSWORD", "SPRING_FLYWAY_PASSWORD", "REDIS_PASSWORD"])
    error_message = "notification-svc gets only its database and cache credentials."
  }

  assert {
    condition     = local.service_environment["identity-svc"]["GOOGLE_REDIRECT_URI"] == "https://d111111abcdef8.cloudfront.net/api/v1/auth/oauth2/google/callback"
    error_message = "The Google callback keeps its public path, now routed to identity-svc."
  }

  assert {
    condition     = alltrue([for name, env in local.service_environment : !contains(keys(env), "SPRING_FLYWAY_LOCATIONS") || strcontains(env["SPRING_FLYWAY_LOCATIONS"], "classpath:db/swe5001")])
    error_message = "A service that overrides the Flyway locations must keep classpath:db/swe5001 (per-service grants, backend ADR 0008)."
  }

  assert {
    condition     = strcontains(local.service_environment["commerce-svc"]["SPRING_FLYWAY_LOCATIONS"], "classpath:db/seed") && !contains(keys(local.service_environment["identity-svc"]), "SPRING_FLYWAY_LOCATIONS") && !contains(keys(local.service_environment["notification-svc"]), "SPRING_FLYWAY_LOCATIONS")
    error_message = "The demo catalogue is seeded only into the commerce database."
  }

  assert {
    condition     = [for name in ["identity-svc", "notification-svc", "commerce-svc"] : local.service_environment[name]["BLOCBUY_SCHEDULING_ENABLED"]] == ["false", "false", "true"]
    error_message = "Scheduled jobs run only in commerce-svc."
  }

  assert {
    condition     = toset(local.parameter_scopes) == toset(["shared", "identity", "commerce", "notification"])
    error_message = "The execution role reads the shared scope and one scope per service database and secret owner."
  }

  assert {
    condition     = length(local.db_init_secrets) == 7 && contains(keys(local.db_init_secrets), "NOTIFICATION_APP_PASSWORD")
    error_message = "db-init receives the master password and both role passwords of all three databases."
  }
}
