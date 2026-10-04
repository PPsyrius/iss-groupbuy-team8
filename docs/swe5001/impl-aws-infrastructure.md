# SWE5001 Implementation Plan: AWS Infrastructure

Detailed Terraform and AWS work for the [refactoring plan](microservice-refactoring-plan.md), sections 2.5 and 3.3. Companion plans: [backend architecture](impl-backend-architecture.md), [GitHub configuration](impl-github-configuration.md), [verification and observability](impl-verification-observability.md).

Status: **Draft, revised 2026-10-04** (first draft 2026-09-29; 2026-10-04: apply and deploy roles are reachable only from the `prod` release branch; `runtime` exists only during a promotion run or a manual session, driven by the scripts in section 7). Account `402178232802`, region `ap-southeast-1`. Service availability (PostgreSQL 18 on RDS, Valkey versions, Graviton Fargate Spot) must be confirmed in the region before the first apply.

---

## 1. Principles

- **Separate from SWE5006.** New stacks under `infra/swe5001/`, new state keys, new VPC, new CloudFront distribution. `infra/terraform/backend-app-stack` and `frontend-stack` and the EC2 dev environment are not modified.
- **Three lifecycles.** `foundation` and `edge` are persistent; `runtime` is normally absent. It is created by the once-per-sprint promotion run or by `env-up.sh` for a manual session, and destroyed at the end of either (plan sections 3.3 and 3.4).
- **Tags on everything:** `Project=blocbuy`, `Course=swe5001`, `Stack=<foundation|edge|runtime>`, `ManagedBy=terraform`. Default tags are set in the provider block.
- **Naming:** `blocbuy-swe5001-<component>`; SSM paths `/blocbuy/swe5001/<scope>/<NAME>`.
- **Versions:** Terraform `~> 1.16.4` and AWS provider `~> 5.100`, matching the existing stacks.

---

## 2. Existing resources to reuse (read through data sources, never managed here)

| Resource | How it is used |
| --- | --- |
| GitHub OIDC provider `token.actions.githubusercontent.com` | `data "aws_iam_openid_connect_provider"`, as `backend-app-stack/github-oidc.tf` does. An account allows one provider per URL, so SWE5001 must not create another |
| Terraform state bucket (passed with `-backend-config bucket=...`) | New keys `swe5001/foundation.tfstate`, `swe5001/edge.tfstate`, `swe5001/runtime.tfstate`, with `use_lockfile = true` (S3-native locking; no DynamoDB table needed) |
| Organisation SCP | Denies `access-analyzer:ValidatePolicy`; IAM changes go through Terraform or the CLI, not the console policy editor |

---

## 3. Directory layout

```
infra/swe5001/
  README.md                 # init commands, apply order, destroy rules
  foundation/               # persistent
  edge/                     # persistent
  runtime/                  # disposable
  db-init/                  # SQL for the one-off database bootstrap task
  scripts/                  # env-up.sh, env-down.sh, env-status.sh, lib.sh (section 7)
  modules/
    network/  ecs-cluster/  ecs-service/  rds-postgres/  cache/  messaging/  observability/
```

---

## 4. Stack `foundation` (persistent)

| Resource | Details |
| --- | --- |
| ECR repositories | `swe5001/monolith` (strangler step 0), `swe5001/identity-svc`, `swe5001/commerce-svc`, `swe5001/notification-svc`; `IMMUTABLE` tags, scan on push, encrypted with the AWS-managed `aws/ecr` KMS key, lifecycle keeps the last 10 images |
| IAM role `blocbuy-swe5001-gh-plan` | Trust: subject `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:environment:swe5001-plan`, audience `sts.amazonaws.com`. Policy: `ReadOnlyAccess`, state read under `swe5001/`, and an explicit deny on reading any other S3 object (PRs from any branch can assume it, and the SWE5006 state shares the bucket). Plans run with `-lock=false`, so the role needs no write access |
| IAM role `blocbuy-swe5001-gh-apply` | Trust: `...:environment:swe5001-demo`; the environment admits only the `prod` branch, so `dev` pushes and PRs cannot assume this role or the deploy role (GitHub plan, section 3). Policy: AWS managed policies for the services the stacks use (`AmazonEC2FullAccess`, `AmazonECS_FullAccess`, `AmazonRDSFullAccess`, `AmazonElastiCacheFullAccess`, `AmazonSNSFullAccess`, `AmazonSQSFullAccess`, `CloudWatchFullAccessV2`, `AWSCloudMapFullAccess`, `CloudFrontFullAccess`); inline: IAM roles and instance profiles under path `/swe5001/`, which can only be created or given policies with the workload permissions boundary (below) set, attach only allowlisted managed policies, and be passed only to ECS tasks and EC2; boundary changes are denied; service-linked roles for ECS, Application Auto Scaling, ElastiCache, ELB and RDS, SSM under `/blocbuy/swe5001/*`, the SPA bucket, state read/write under `swe5001/`; an explicit deny on mutating or deleting EC2 instances, security groups, VPC parts, ELB resources, CloudFront distributions, RDS instances and clusters, and ElastiCache clusters and replication groups not tagged `Course=swe5001`, and on deleting or reconfiguring any log group outside `/blocbuy/swe5001/*`, so the SWE5006 stack stays out of reach. The GitHub roles live at the root IAM path, outside `/swe5001/`, so the apply role cannot edit its own policies |
| IAM policy `blocbuy-swe5001-workload-boundary` | Permissions boundary for every runtime role under `/swe5001/`: ECR pull from the SWE5001 repositories, logs under `/blocbuy/swe5001/*`, SSM reads under `/blocbuy/swe5001/*` (with KMS decrypt via SSM), `sns:Publish` and SQS consume on `blocbuy-swe5001-*`, metrics in namespace `BlocBuy/SWE5001`, ECS Exec channels, and the fck-nat ENI and Elastic IP actions on `Course=swe5001` resources. A runtime role needing more requires a reviewed change to this policy |
| IAM role `blocbuy-swe5001-gh-deploy` | Trust: `...:environment:swe5001-demo`. Policy as listed in the GitHub plan, section 4 |
| SSM parameters (SecureString), **not managed by Terraform** | Seeded once by an operator with `aws ssm put-parameter` (commands in `infra/swe5001/README.md`): `/blocbuy/swe5001/identity/GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`; `/blocbuy/swe5001/commerce/STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`; `/blocbuy/swe5001/shared/INTERNAL_TOKEN`, `ORIGIN_SECRET` (random values from `openssl rand`). Keeping them out of Terraform keeps secrets out of the state that the plan role reads |
| SSM parameters (String), operator-seeded | `/blocbuy/swe5001/shared/BUDGET_ALERT_EMAILS` (comma-separated; read by the budget, so no address is committed) |
| SSM parameters (String), written by workflows | `/blocbuy/swe5001/candidate/<service>` latest pushed image digest; `/blocbuy/swe5001/release/<service>` last known good image digest, promoted from the candidate by the deploy workflow only after a healthy ECS deployment and smoke test |
| AWS Budgets | Monthly cost budget filtered by tag `Course=swe5001` (the `Course` cost allocation tag must be activated once in Billing), alerts at US$20 and US$50 to the addresses in `BUDGET_ALERT_EMAILS` (expected spend is under US$10 per month) |
| Outputs | Role ARNs, repository URLs, state bucket name; copied into GitHub variables (GitHub plan, section 4) |

---

## 5. Stack `edge` (persistent)

| Resource | Details |
| --- | --- |
| S3 bucket `blocbuy-swe5001-spa-<account>` | Private, OAC access only, versioning off, `force_destroy = true` (safe to rebuild from CI) |
| CloudFront distribution | Default behaviour → S3 (SPA fallback CloudFront Function copied from `frontend-stack`). Behaviour `/api/*` → ALB origin over HTTP with custom header `X-Origin-Secret` from SSM; caching disabled; all methods; forward cookies, query strings and `Authorization`, `Stripe-Signature` headers. Response-headers policy mirrors the dev policy |
| ALB origin | Read from `runtime` remote state (`alb_dns_name`). While `runtime` is down, `/api/*` returns 502. A placeholder `alb_dns_name` variable default lets `edge` apply first |

Origin TLS: the ALB listener is HTTP because there is no custom domain for an ACM certificate. This is the same limitation as SWE5006 NFR-37. The upgrade path is a CloudFront VPC origin to an internal ALB, which removes the public ALB entirely; it is recorded as a considered option, not in scope.

---

## 6. Stack `runtime` (disposable)

### 6.1 Network (`modules/network`)

| Item | Value |
| --- | --- |
| VPC CIDR | `10.50.0.0/16` (does not overlap the default VPC used by the EC2 dev host) |
| Public subnets | `10.50.0.0/24` (a), `10.50.1.0/24` (b): ALB, fck-nat |
| Private subnets | `10.50.10.0/24` (a), `10.50.11.0/24` (b): ECS tasks, RDS, ElastiCache |
| Egress | fck-nat on `t4g.nano` in public subnet a, route `0.0.0.0/0` from both private route tables. Needed for Stripe, Google OAuth and AWS APIs |
| VPC endpoints | Gateway endpoint for S3 (free; ECR layer pulls). Interface endpoints are skipped for cost; traffic to ECR API, SSM, SNS, SQS and CloudWatch goes through fck-nat |

### 6.2 Security groups

| SG | Inbound | From |
| --- | --- | --- |
| `alb` | TCP 80 | CloudFront origin-facing managed prefix list `com.amazonaws.global.cloudfront.origin-facing` |
| `svc` (shared by the three services) | TCP 8589 | `alb`, and `svc` itself (Service Connect traffic) |
| `rds` | TCP 5432 | `svc`, `dbinit` |
| `cache` | TCP 6379 | `svc` |
| `dbinit` | none | — |
| `nat` | all TCP/UDP | private subnet CIDRs |

The ALB additionally returns 403 unless `X-Origin-Secret` matches (listener default action 403; rules require the header).

### 6.3 Load balancer

| Listener rule (priority) | Conditions | Target group |
| --- | --- | --- |
| 10 | header `X-Origin-Secret`, path `/api/v1/auth/*` or `/api/v1/users/*` | `identity-svc` |
| 20 | header, path `/api/v1/notifications*` | `notification-svc` |
| 30 | header, path `/api/*` | `commerce-svc` |
| default | — | fixed 403 |

Target groups: type `ip`, port 8589, health check `GET /api/actuator/health`, matcher 200, interval 15 s, healthy 2, unhealthy 3, deregistration delay 30 s. `/internal/*` matches no rule that forwards it: rules 10–30 are placed after an explicit rule (priority 5) that returns 404 for `/internal/*`.

### 6.4 ECS (`modules/ecs-cluster`, `modules/ecs-service`)

- Cluster with Container Insights (enhanced observability off for cost), capacity providers `FARGATE` and `FARGATE_SPOT`; strategy base 1 on `FARGATE`, weight 1 `FARGATE` : 3 `FARGATE_SPOT` above the base.
- Service Connect namespace `blocbuy-swe5001.local`; each service publishes port name `http` with DNS name equal to the service name (`identity-svc:8589`).
- Task definition per service: `ARM64`, 512 CPU / 1024 MiB, container port 8589, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, `awslogs` to `/blocbuy/swe5001/<service>` (7-day retention, created by Terraform), health check grace 120 s, `stopTimeout` 30 s.
- Image: `<repo>@<digest>` from SSM `/blocbuy/swe5001/release/<service>`; the deploy workflow replaces it later.
- Deployment: rolling, minimum healthy 100%, maximum 200%, circuit breaker with rollback.
- Environment and secrets follow the backend plan, section 7. Secrets use `valueFrom` SSM ARNs.

| Service | Desired / min / max | Scaling |
| --- | --- | --- |
| `commerce-svc` | 1 / 1 / 6 | Target tracking `ALBRequestCountPerTarget` = 200 (revised after the baseline run); second policy CPU 50%; scale-out cooldown 60 s, scale-in 180 s |
| `identity-svc` | 1 / 1 / 3 | CPU 50% |
| `notification-svc` | 1 / 1 / 4 | Target tracking on a metric-math expression: `ApproximateNumberOfMessagesVisible` / `RunningTaskCount`, target 50 |

IAM per service (all under path `/swe5001/` with `permissions_boundary` set to the foundation output `workload_permissions_boundary_arn`): one shared **execution role** (ECR pull, CloudWatch Logs, `ssm:GetParameters` on `/blocbuy/swe5001/<service>/*` and `/blocbuy/swe5001/shared/*`) and a **task role** per service: commerce `sns:Publish` on `blocbuy-swe5001-*` topics; notification `sqs:ReceiveMessage`, `DeleteMessage`, `ChangeMessageVisibility`, `GetQueueAttributes` on its queue; identity none. All roles enable ECS Exec only if `enable_exec = true` (default false).

### 6.5 Data stores

| Store | Settings |
| --- | --- |
| RDS PostgreSQL (`modules/rds-postgres`) | Engine 18 if available in the region, otherwise 17 (then update `docs` and the Testcontainers image to match); `db.t4g.micro`, 20 GiB gp3, single-AZ, private subnets, storage encrypted with the AWS-managed key, `skip_final_snapshot = true`, `deletion_protection = false`, backups 0 days, master password from `random_password` stored in SSM `/blocbuy/swe5001/shared/DB_MASTER_PASSWORD`, parameter group with `rds.force_ssl = 1` |
| Database bootstrap | One-off ECS task `db-init` (image `postgres:<version>-alpine`, SG `dbinit`) run by `env-up` after RDS is available. `db-init/init.sql` creates databases `identity`, `commerce`, `notification`; schema `domain` in each; owner roles `blocbuy_<svc>_owner` (Flyway) and runtime roles `blocbuy_<svc>_app`; passwords from SSM. Idempotent (`IF NOT EXISTS` guards). The Terraform `postgresql` provider is not used because runners cannot reach the private instance |
| ElastiCache Valkey (`modules/cache`) | Replication group, 1 node `cache.t4g.micro`, engine Valkey, in-transit encryption on, AUTH token from `random_password` in SSM `/blocbuy/swe5001/shared/REDIS_PASSWORD`, no snapshots |

### 6.6 Messaging (`modules/messaging`)

| Topic | Subscribed queue | DLQ |
| --- | --- | --- |
| `blocbuy-swe5001-team-succeeded` | `blocbuy-swe5001-notification` | `blocbuy-swe5001-notification-dlq` |
| `blocbuy-swe5001-team-expired` | same queue | same DLQ |
| `blocbuy-swe5001-payment-succeeded` | same queue | same DLQ |

- One queue for the notification consumer, subscribed to three topics with raw message delivery on; the event type travels in the envelope.
- Queue: SSE-SQS, visibility timeout 60 s, long polling 20 s, retention 4 days, redrive `maxReceiveCount = 5`. DLQ retention 14 days.
- Queue policy allows `sns:SendMessage` only from the three topic ARNs.

### 6.7 Observability (`modules/observability`)

Dashboard and alarms are defined in the [verification plan](impl-verification-observability.md); this module implements them.

---

## 7. Apply and destroy order

Steps 1–5 are `env-up.sh`; the destroy row is `env-down.sh`. The promotion run and `swe5001-env.yml` call the same scripts.

| Step | Command | Expected time |
| --- | --- | --- |
| 1 | `terraform -chdir=infra/swe5001/runtime apply` (network, ALB, ECS cluster, RDS, Valkey, messaging, services at desired count 0) | 12–18 min |
| 2 | `aws ecs run-task` for `db-init`; wait for exit code 0 | 1–2 min |
| 3 | Set desired count to 1 per service (second `apply` with `-var services_enabled=true`) | 3–5 min |
| 4 | `terraform -chdir=infra/swe5001/edge apply` (origin DNS update) | 3–5 min |
| 5 | Smoke test through CloudFront (verification plan, section 3) | 1 min |
| Destroy | `terraform -chdir=infra/swe5001/runtime destroy`, retried once on Service Connect namespace errors | 10–15 min |

Destroy rules from plan section 3.3 apply unchanged (no customer-managed KMS keys, explicit log groups, `force_destroy` buckets, ECR outside `runtime`).

### Environment scripts (`infra/swe5001/scripts/`)

| Script | Behaviour | Exit status |
| --- | --- | --- |
| `lib.sh` | Sourced by the others. Fails unless `aws sts get-caller-identity` returns account `402178232802` and `AWS_REGION` is `ap-southeast-1`; runs `terraform init` with `-backend-config bucket=$SWE5001_TF_STATE_BUCKET` | — |
| `env-up.sh [--release\|--candidate]` | Steps 1–5 above. Reads image digests from SSM `/blocbuy/swe5001/release/*` (default) or `/candidate/*`. Safe to re-run: a second run on an existing environment converges instead of failing | 0 when the smoke suite passes |
| `env-down.sh` | `terraform destroy` of `runtime`, one retry on Service Connect namespace errors, then `env-status.sh` | 0 when nothing remains |
| `env-status.sh` | Lists resources tagged `Course=swe5001,Stack=runtime` (Resource Groups Tagging API) and the ECS service desired and running counts | 0 when no runtime resource exists, 1 otherwise; with `--expect-up` the check is inverted for use after `env-up.sh` |

Local use, with the operator's own AWS credentials:

```bash
export AWS_PROFILE=<operator profile> AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=<bucket>
infra/swe5001/scripts/env-up.sh      # before a session; prints the CloudFront URL
infra/swe5001/scripts/env-down.sh    # after the session; fails if anything is left
```

---

## 8. Work packages

| WP | Content | WBS | Depends on |
| --- | --- | --- | --- |
| F1 | `foundation` stack: state keys, three GitHub roles, ECR, SSM placeholders, budget. Applied by an operator from a workstation, because it creates the roles the pipelines assume; CI only plans it | 4.1 | none |
| R1 | `modules/network` and security groups | 4.2 | F1 |
| R2 | `modules/ecs-cluster`, `modules/ecs-service` (golden path), ALB and listener rules | 4.2 | R1 |
| R3 | `modules/rds-postgres`, `db-init` task and SQL | 4.3 | R1 |
| R4 | `modules/cache` | 4.3 | R1 |
| R5 | `modules/messaging` | 4.3 | R1 |
| R6 | Scaling policies and `modules/observability` | 4.5 | R2, verification plan |
| E1 | `edge` stack, SPA bucket, CloudFront | 4.4 | F1 |
| S1 | `scripts/`: `lib.sh` (account and region guard, `terraform init` with the state bucket), `env-up.sh`, `env-down.sh`, `env-status.sh`; usage in `infra/swe5001/README.md` | 5.3 | R1–R5, E1 |
| S0 | Strangler step 0: run the monolith image (`topology=monolith`) as one ECS service behind the same ALB, before the service split lands | 4.2 | R2, R3, R4, E1 |

Every stack PR shows a clean `terraform plan` in `infra-swe5001.yml` and passes checkov with documented skips only.

## 9. Cost guardrails

- Hourly runtime estimate about US$0.17–0.20 (plan section 3.3); persistent layer about US$1–3 per month.
- Runtime hours: about 14 for the whole course (two one-hour promotion runs and about 12 hours of manual sessions), so under US$5 in runtime charges for the course. The persistent layer is billed separately at about US$1–3 per month.
- Every promotion run ends with `env-down.sh` and `env-status.sh`, also on failure.
- `ENABLE_SWE5001_NIGHTLY_DOWN` destroys `runtime` at 23:00 SGT if a manual session was left running.
- Budget alerts at US$20 and US$50.
- After 16 Nov: destroy `runtime`, `edge`, then `foundation` (empty ECR first), and remove the GitHub variables and environments.

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| PostgreSQL 18 not offered by RDS in the region | Fall back to 17; the app uses no 18-only features (verify with the IT suite on a 17 Testcontainers image) |
| fck-nat instance failure cuts egress | Single instance is accepted for the demo; ASG of 1 recreates it; alarm on instance status |
| Fargate Spot interruption during the scaling demo | Base task on on-demand; interruptions are a live self-healing example |
| Apply role too broad | AWS managed policies per service, IAM limited to path `/swe5001/` with a mandatory permissions boundary, and tag-conditioned denies on changes to non-SWE5001 EC2, ELB, CloudFront, RDS and ElastiCache resources, and a name-based deny for log groups outside `/blocbuy/swe5001/*`; narrow the managed policies to the observed action set after the first `runtime` apply |
