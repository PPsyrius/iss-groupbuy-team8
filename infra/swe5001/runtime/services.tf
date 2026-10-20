# Service maps per topology (`var.topology`). The ecs-service golden path supplies DB_*,
# SPRING_FLYWAY_*, REDIS_* and FRONTEND_URL from the service's database and the shared
# cache (ecs.tf); entries here add the service's own settings. `secrets` maps an
# environment variable to a parameter name under /blocbuy/swe5001/. `connects_to` lists the
# Service Connect endpoints a service calls; ecs.tf creates those services first.
#
#   none         network, ALB, cluster and data stores only
#   monolith     strangler step 0: the SWE5006 monolith image as one ECS service on /api/*
#                (#376, PR 6); kept as the fallback topology
#   distributed  the three service assemblies of backend ADR 0008 (#456), each with its own
#                database; ALB rules and priorities follow the AWS plan, section 6.3

locals {
  topologies = {
    none = {}

    monolith = {
      monolith = {
        repository        = "monolith"
        database          = "monolith"
        listener_priority = 100
        path_patterns     = ["/api/*"]
        task_policy_json  = null
        connects_to       = []

        environment = {
          # Same profile as the EC2 host: CloudFront client address header, env-only secrets.
          SPRING_PROFILES_ACTIVE = "ec2"
          # Every environment starts empty, so the demo catalogue is seeded with the schema.
          SPRING_FLYWAY_LOCATIONS = "classpath:db/migration,classpath:db/seed"
          GOOGLE_REDIRECT_URI     = "{frontend_url}/api/v1/auth/oauth2/google/callback"
          # Same safe defaults as deployment/backend/backend.env.example.
          BLOCBUY_SCHEDULING_ENABLED      = "false"
          BLOCBUY_STRIPE_CHECKOUT_ENABLED = "false"
          TYPESAFE_ENABLED                = "false"
        }

        secrets = {
          GOOGLE_CLIENT_ID      = "identity/GOOGLE_CLIENT_ID"
          GOOGLE_CLIENT_SECRET  = "identity/GOOGLE_CLIENT_SECRET"
          STRIPE_API_KEY        = "commerce/STRIPE_API_KEY"
          STRIPE_WEBHOOK_SECRET = "commerce/STRIPE_WEBHOOK_SECRET"
        }
      }
    }

    # Profiles: ec2 maps DB_* and REDIS_* (as for the monolith), <service> selects the
    # assembly's scans, topology and grants callback, aws switches Redis TLS and JSON logs.
    # A service that sets SPRING_FLYWAY_LOCATIONS must keep classpath:db/swe5001 (grants).
    # Events and notification backlog scaling are #447; HTTP scaling is #457.
    distributed = {
      identity-svc = {
        repository        = "identity-svc"
        database          = "identity"
        listener_priority = 10
        path_patterns     = ["/api/v1/auth/*", "/api/v1/users/*"]
        task_policy_json  = null
        connects_to       = []

        environment = {
          SPRING_PROFILES_ACTIVE     = "ec2,identity,aws"
          GOOGLE_REDIRECT_URI        = "{frontend_url}/api/v1/auth/oauth2/google/callback"
          BLOCBUY_SCHEDULING_ENABLED = "false"
        }

        secrets = {
          GOOGLE_CLIENT_ID     = "identity/GOOGLE_CLIENT_ID"
          GOOGLE_CLIENT_SECRET = "identity/GOOGLE_CLIENT_SECRET"
        }
      }

      notification-svc = {
        repository        = "notification-svc"
        database          = "notification"
        listener_priority = 20
        path_patterns     = ["/api/v1/notifications*"]
        task_policy_json  = local.notification_event_policy
        connects_to       = []

        environment = {
          SPRING_PROFILES_ACTIVE       = "ec2,notification,aws"
          BLOCBUY_SCHEDULING_ENABLED   = "false"
          AWS_REGION                   = var.aws_region
          BLOCBUY_EVENTS_TRANSPORT     = "sns"
          BLOCBUY_EVENTS_SQS_QUEUE_URL = local.messaging.notification_url
          # Same gate as the commerce relay: the consumer stays off until env-up's
          # routing check has passed, so it never deletes the verifier's probes.
          BLOCBUY_EVENTS_CONSUMER_ENABLED = tostring(var.services_enabled && var.messaging_verified)
        }

        secrets = {}
      }

      # Catch-all after identity and notification; also answers /api/actuator/health.
      commerce-svc = {
        repository        = "commerce-svc"
        database          = "commerce"
        listener_priority = 30
        path_patterns     = ["/api/*"]
        task_policy_json  = local.commerce_event_policy
        connects_to       = ["identity-svc"]

        environment = merge({
          SPRING_PROFILES_ACTIVE = "ec2,commerce,aws"
          # The demo catalogue lives only in the commerce database.
          SPRING_FLYWAY_LOCATIONS = "classpath:db/migration,classpath:db/swe5001,classpath:db/seed"
          # identity-svc over Service Connect (#468); /internal/** is never routed by the ALB.
          IDENTITY_BASE_URL = "http://identity-svc:8589"
          # Leased jobs (LeasedJobRunner) run here only: one runner per job across tasks.
          BLOCBUY_SCHEDULING_ENABLED = "true"
          # Stripe test-mode Checkout for the demo: Start/Join need it, and payment and team
          # events feed notifications. The return URLs are mandatory once it is on.
          BLOCBUY_STRIPE_CHECKOUT_ENABLED      = "true"
          BLOCBUY_PAYMENT_CHECKOUT_SUCCESS_URL = "{frontend_url}/payments/return"
          BLOCBUY_PAYMENT_CHECKOUT_CANCEL_URL  = "{frontend_url}/payments/return"
          TYPESAFE_ENABLED                     = "false"
          AWS_REGION                           = var.aws_region
          BLOCBUY_EVENTS_TRANSPORT             = "sns"
          BLOCBUY_EVENTS_RELAY_ENABLED         = tostring(var.services_enabled && var.messaging_verified)
          BLOCBUY_EVENTS_SNS_TOPIC_ARN         = local.messaging.topic_arn
          }, var.v4_demo_enabled ? {
          SPRING_PROFILES_ACTIVE                    = "ec2,commerce,aws,demo"
          BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE = "500"
          BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL   = "PT1S"
        } : {})

        secrets = {
          STRIPE_API_KEY        = "commerce/STRIPE_API_KEY"
          STRIPE_WEBHOOK_SECRET = "commerce/STRIPE_WEBHOOK_SECRET"
        }
      }
    }
  }

  services  = local.topologies[var.topology]
  databases = distinct([for s in values(local.services) : s.database])
}
