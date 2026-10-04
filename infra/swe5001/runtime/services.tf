# Service maps per topology (`var.topology`). The ecs-service golden path supplies DB_*,
# SPRING_FLYWAY_*, REDIS_* and FRONTEND_URL from the service's database and the shared
# cache (ecs.tf); entries here add the service's own settings. `secrets` maps an
# environment variable to a parameter name under /blocbuy/swe5001/.
#
#   none      network, ALB, cluster and data stores only
#   monolith  strangler step 0: the SWE5006 monolith image as one ECS service on /api/*
#             (#376, PR 6). The service split (PR 9) adds a `distributed` map with
#             identity-svc, commerce-svc and notification-svc.

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
  }

  services  = local.topologies[var.topology]
  databases = distinct([for s in values(local.services) : s.database])
}
