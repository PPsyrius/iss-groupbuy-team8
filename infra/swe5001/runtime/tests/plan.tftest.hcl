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

run "empty_service_map" {
  command = plan

  assert {
    condition     = length(module.service) == 0
    error_message = "No service should be planned until a service map is passed."
  }

  assert {
    condition     = aws_lb_listener_rule.block_internal.priority == 5
    error_message = "The /internal/* block rule must have the lowest priority."
  }
}

run "monolith_service" {
  command = plan

  variables {
    services_enabled = true
    databases        = ["monolith"]
    services = {
      monolith = {
        repository        = "monolith"
        database          = "monolith"
        listener_priority = 10
        path_patterns     = ["/api/*"]
        environment       = { SPRING_PROFILES_ACTIVE = "ec2" }
        secrets           = { STRIPE_API_KEY = "commerce/STRIPE_API_KEY" }
      }
    }
    image_digests = {
      monolith = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    }
  }

  assert {
    condition     = module.service["monolith"].service_name == "monolith"
    error_message = "The monolith service should be planned."
  }

  assert {
    condition     = local.service_environment["monolith"]["DB_USERNAME"] == "blocbuy_monolith_app"
    error_message = "The service must connect with its database's app role."
  }

  assert {
    condition     = local.service_environment["monolith"]["SPRING_PROFILES_ACTIVE"] == "ec2"
    error_message = "Service-specific environment must be merged on top of the golden-path defaults."
  }

  assert {
    condition     = local.service_secrets["monolith"]["STRIPE_API_KEY"] == "arn:aws:ssm:ap-southeast-1:402178232802:parameter/blocbuy/swe5001/commerce/STRIPE_API_KEY"
    error_message = "Service secrets must resolve to parameter ARNs under /blocbuy/swe5001/."
  }

  assert {
    condition     = toset(keys(local.db_init_secrets)) == toset(["PGPASSWORD", "MONOLITH_OWNER_PASSWORD", "MONOLITH_APP_PASSWORD"])
    error_message = "db-init must receive the master password and both role passwords per database."
  }
}

run "rejects_unpinned_image" {
  command = plan

  variables {
    databases = ["monolith"]
    services = {
      monolith = {
        repository        = "monolith"
        database          = "monolith"
        listener_priority = 10
        path_patterns     = ["/api/*"]
      }
    }
    image_digests = {
      monolith = "latest"
    }
  }

  expect_failures = [var.image_digests]
}
