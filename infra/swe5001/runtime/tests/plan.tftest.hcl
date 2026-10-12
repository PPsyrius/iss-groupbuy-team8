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
    condition     = length(module.service) == 0 && length(local.databases) == 0
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
