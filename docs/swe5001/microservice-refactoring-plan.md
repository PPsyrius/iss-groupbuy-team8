# SWE5001 Microservice Refactoring Plan

This is the engineering companion to the [project proposal](project-proposal.typ). Its sections follow the proposal's structure (Overview, General Architecture, Scope of Work, Effort Estimates, Risks): the proposal states *what* is built and *why* for the reviewers, and this plan records *how* for the team. It covers the service decomposition, the implementation approach that keeps `dev` syncs cheap, the integration and data design, the AWS deployment model, the Terraform layout and the branch and sync conventions for this fork.

Status: **Draft, revised 2026-09-29** (first draft 2026-09-28). Cost and effort figures are estimates; confirm cost with the AWS Pricing Calculator before committing to a budget.

---

## 1. Overview

### 1.1 Why this fork exists

BlocBuy is developed for SWE5006 as a COLA modular monolith on the `dev` branch. SWE5001 grades a different set of outcomes: platform thinking (seed, producers, consumers, reusable services), demonstrated scalability (auto-scaling under scripted load), cloud-native use of managed services, automated DevSecOps, and minimum security controls.

The SWE5001 work lives on the long-lived branch `swe5001/main`, forked from `dev` at `71ddfed` (tag `swe5001-fork-base`). SWE5006 business features continue on `dev` and flow one way into this branch through sync PRs ([section 3.6](#36-branch-conventions-and-dev-sync)). Because the fork keeps receiving `dev` changes until SWE5006 ends, the refactoring is designed to keep those syncs cheap ([section 2.3](#23-implementation-approach-split-by-assembly-not-by-relocation)).

### 1.2 Course constraints that shape the plan

| Constraint | Consequence |
| --- | --- |
| About 10 person-days per member (50 person-days in total) | The service count is capped at three, and the split must avoid sagas and distributed transactions. |
| Demo must show auto-scaling and scripted performance testing | Every service has a scaling policy, and k6 scripts and a CloudWatch dashboard are deliverables. |
| DevSecOps pipeline scripts are deliverables | Infrastructure is Terraform-only, and deployment runs through GitHub Actions with OIDC. |
| Demo project with no real traffic | The runtime environment is disposable: created before use and destroyed after. |
| SWE5006 feature work continues on `dev` in parallel | Fork changes must not move or rewrite code that `dev` is still changing ([section 2.3](#23-implementation-approach-split-by-assembly-not-by-relocation)). |
| Key dates: proposal 4 Oct, presentation 23 Oct (Team 8, 16:15), report 16 Nov | See the timeline in [section 4](#4-effort-estimates-and-timeline). |

### 1.3 Deliberate deviations from SWE5006 rules (this branch only)

- **Messaging.** The SWE5006 backend constraints exclude message brokers. This branch introduces Amazon SNS and SQS for cross-service events. RabbitMQ and Elasticsearch stay out.
- **Deployment unit.** The single deployable becomes three services. Inside each service, the COLA layering, DDD, `Result` envelope, `ErrorCode` enums and Sa-Token RBAC rules still apply unchanged.

### 1.4 Baseline

- **Backend:** a Java 21 / Spring Boot 4 modular monolith with six bounded contexts: `identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`.
- **Context integration:** in-process ports and Spring events. `TeamSucceeded` and `TeamExpired` subscribers run at `BEFORE_COMMIT` ([backend ADR 0002](../../backend/docs/adr/0002-team-lifecycle-events.md)).
- **Runtime (on `dev`):** one EC2 host running Docker Compose with the backend, PostgreSQL 18 and Redis 8. CloudFront routes `/api/*` to an ALB and the EC2 host, and serves everything else from S3. The EC2 deployment was removed from this branch.
- **Existing Terraform (on `dev`):** `infra/terraform/backend-app-stack` (EC2, ALB, ECR, OIDC) and `infra/terraform/frontend-stack` (S3, CloudFront).
- **Last sync:** `dev` at `c66bb40`, merged on 2026-09-29.

---

## 2. General Architecture

### 2.1 Architectural drivers

The drivers (scalability, consistency, availability, security, cost efficiency, modifiability) are stated in the proposal, section 5.1. Their checkable scenarios are rows QA-01 to QA-16 of the [NFR register](../quality/nfr-register.md). This plan adds one internal driver, **sync cost**: a `dev` sync must stay a routine merge, not a re-port.

### 2.2 Decomposition decision

#### Principle

**Split along transactional consistency boundaries, not along nouns.** A context boundary becomes a network boundary only when no write transaction crosses it today. A five-service split (one per major context) was evaluated first and rejected, because it would need sagas and compensation that exceed the effort budget.

#### Coupling evidence (from code at `71ddfed`)

| Finding | Where | Implication |
| --- | --- | --- |
| A Stripe webhook runs payment → `OrderFulfilmentAdapter.fulfil` (`Propagation.MANDATORY`) → `CatalogPurchaseSupport.consumeOneUnit` (stock) → groupbuy admission, all in **one transaction**. | `StripeCheckoutService`, `OrderFulfilmentAdapter` | Payment, order, catalog (write side) and groupbuy cannot be separated without a saga. |
| `TeamExpired` is consumed at `BEFORE_COMMIT` by both order and payment (refund). | `OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber` | Expiry, order cancellation and refund initiation share one transaction. |
| Order calls `CatalogPurchaseSupport.lockStartQuote` inside its transaction. | `PurchaseIntentAppService` | Catalog pricing and stock are part of the purchase write path. |
| Other contexts use identity through one method only: `UserAppService.requireActive(userId)`, at 10 call sites. | catalog `AdminListingAppServiceImpl`, `TeamPriceAppServiceImpl`; order `PurchaseIntentAppService`, `OrderFulfilmentAdapter` | Identity can move behind a network interface without changing its callers. |
| No SQL joins cross context tables. `RbacMapper` joins only identity tables. | `mapper/**` | The database can be split per service. |
| Notification is isolated: nothing calls it and it consumes no events. | `notification/*` | Extraction is almost free. Event consumers must be added to give it a purpose. |

#### Target services

| Service | Contexts | Owned tables | Platform role |
| --- | --- | --- | --- |
| `identity-svc` | identity | `sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission` | **Platform service.** One identity for producers (admins, merchants) and consumers (buyers). |
| `commerce-svc` | catalog, groupbuy, order, payment | all `biz_*` except `biz_notification`; `log_payment_*`, `log_refund_attempt`, `stripe_local_expiry_request` | **Business application.** The transactional core, which keeps ADR 0002 semantics intact. |
| `notification-svc` | notification | `biz_notification` | **Platform service.** An event-driven notification capability that future business seeds can reuse. |

Each service keeps its own copy of `sys_scheduler_lock` and `sys_scheduled_item_retry` if it runs scheduled work.

#### Rejected alternatives

| Alternative | Reason for rejection |
| --- | --- |
| Five services (identity, catalog, trade = groupbuy + order, payment, notification) | Payment and catalog extraction need sagas and stock-reservation compensation. Estimated at 50–55 person-days, which is over budget. |
| identity + catalog / groupbuy + order + payment / notification | `lockStartQuote` and `consumeOneUnit` run inside purchase transactions, so extracting catalog needs reservation and compensation. |
| catalog read service (CQRS) / write core / notification | The best read-scaling story, but the listing path also reads groupbuy's `GroupDealQueryService`, which needs a separate read model or shared read tables. |

#### Anticipated challenge: "commerce-svc is still a monolith"

**Response:** the boundary follows transactional consistency, not a naming scheme. `commerce-svc` remains COLA-modular inside. Extracting `payment` next (transactional outbox plus a saga for fulfilment and refund) is recorded as roadmap work, not in scope for the demo.

### 2.3 Implementation approach: split by assembly, not by relocation

#### Why

The split must not fight the `dev` sync. Between 2026-09-01 and 2026-09-29, `dev` changed main code in the commerce contexts in 46 commits (catalog 13, groupbuy 16, order 7, payment 10) and in identity and notification in 7 (identity 4, notification 3). Moving the commerce code into new directories would turn every later `dev` change there into a conflict or a manual re-port. The two contexts that do move are the ones `dev` rarely touches.

#### How

A deployable service is an **assembly**: a Spring Boot entry point plus configuration that selects which bounded contexts run in that process. Business code is written once, in the existing COLA modules.

```
backend/
  blocbuy-client / domain / application / adapter / infrastructure   # shared, unchanged paths
  blocbuy-bootstrap/          # monolith assembly (topology=monolith): baseline tests, strangler step 0
  blocbuy-platform-starter/   # Result envelope, ErrorCode base, Sa-Token config, trace ID, outbox, idempotency
  services/
    commerce-svc/             # scans catalog, groupbuy, order, payment (topology=distributed)
    identity-svc/             # scans identity
    notification-svc/         # scans notification
```

| Seam | `topology=monolith` (default) | `topology=distributed` |
| --- | --- | --- |
| Which contexts load | All six (`scanBasePackages = "com.blocbuy"`) | Only the service's own contexts plus `com.blocbuy.platform` |
| Identity checks (`UserAppService`) | Existing in-process implementation | HTTP adapter to `identity-svc` `/internal/**` ([section 2.4](#24-integration)) |
| Cross-service events | Spring events only | Spring events plus the outbox relay to SNS/SQS |
| Database | One database | One database and role per service ([section 2.6](#26-persistence-design)) |

- **Selection:** the property `blocbuy.topology` selects the adapter through `@ConditionalOnProperty`. The monolith value is the default (`matchIfMissing = true`), so code and tests merged from `dev` run unchanged.
- **Callers do not change:** a port such as `UserAppService` keeps its interface; only its implementation differs by topology. The same holds for the in-process `@TransactionalEventListener` subscribers inside `commerce-svc`.
- **Shared configuration:** generic configuration now under `com.blocbuy.bootstrap` (trace-ID filter, Sa-Token, scheduling infrastructure) moves into `blocbuy-platform-starter` so every assembly reuses it. Context-specific configuration (Stripe, team-expiry and refund jobs) moves into the owning context's package so `commerce-svc` picks it up by scanning.
- **Relocation is deferred:** moving `identity` and `notification` code into their own Maven aggregates is optional and happens only after SWE5006 feature work on `dev` stops. The commerce contexts stay in place.

#### Guardrails

- **ArchUnit:** commerce contexts must not depend on identity or notification internals, except through `UserAppService` and domain events. This makes the assembly split safe even though the code is co-located.
- **CI builds every assembly:** the monolith and the three service images build on every PR. A `dev` change that breaks the distributed topology fails in the sync PR, not later.
- **One behaviour suite for both topologies:** the existing integration tests run against the monolith assembly; a smoke suite runs against the three services in Docker Compose.

### 2.4 Integration

#### Synchronous calls

- **Mechanism:** ECS Service Connect (Cloud Map namespace, Envoy sidecar), which provides retries and per-service metrics.
- **Identity checks:** `commerce-svc` keeps the `UserAppService` interface and swaps in an HTTP adapter that calls `identity-svc` at `GET /internal/users/{id}/active`, so the 10 call sites stay unchanged.
- **Internal endpoints:** `/internal/**` is reachable only inside the VPC. It is excluded from ALB routing and the security groups only admit peer services.
- **Risk:** `OrderFulfilmentAdapter.fulfil` calls `requireActive` inside the webhook transaction. The mitigation is a 1–2 s timeout: on failure, the existing reject branch runs and Stripe retries the webhook.

#### Asynchronous events

- **Transactional outbox:** in `commerce-svc`, a `BEFORE_COMMIT` listener writes an outbox row in the same transaction as the business change. A scheduled poller (guarded by the scheduler lock) publishes pending rows to SNS and marks them sent. The listener is active only under `topology=distributed`.
- **Topology:** one SNS topic per event type, one SQS queue per consumer, and a dead-letter queue per queue (`maxReceiveCount = 5`).
- **Idempotency:** consumers are idempotent. A `consumed_message` table keyed by event ID is written in the same transaction as the side effect.
- **Initial events:**

| Event | Producer | Consumer | Effect |
| --- | --- | --- | --- |
| `TeamSucceeded` | commerce | notification | Notify members that the team formed. |
| `TeamExpired` | commerce | notification | Notify members of expiry and refund. |
| `PaymentSucceeded` | commerce | notification | Payment confirmation notice. |

In-process subscribers inside `commerce-svc` stay as they are, keeping ADR 0002 `BEFORE_COMMIT` semantics. The outbox adds cross-service delivery without replacing them.

#### Authentication and authorisation

- **Shared session store:** Sa-Token sessions live in a shared ElastiCache (Valkey) instance, so every service validates the same `blocbuy-token`.
- **Login:** `identity-svc` owns Google OAuth login and writes the user's roles and permissions into the session at login.
- **Authorisation elsewhere:** in the other services, `StpInterface` reads roles and permissions from the session and does not call `identity-svc` per request.

### 2.5 Physical architecture (AWS, ap-southeast-1)

```
CloudFront (persistent) ─┬─ /*       → S3 SPA bucket
                         └─ /api/*   → ALB (X-Origin-Secret header required; default action 403)
                                        ├─ /api/v1/auth/*, /api/v1/users/*   → identity-svc
                                        ├─ /api/v1/notifications*            → notification-svc
                                        └─ /api/*  (default)                 → commerce-svc
VPC, 2 AZs
  public subnets : ALB, fck-nat instance
  private subnets: ECS Fargate tasks, RDS PostgreSQL, ElastiCache Valkey
Messaging        : SNS topics → SQS queues (+ DLQs) → notification-svc
```

| Decision | Choice | Reason |
| --- | --- | --- |
| Compute | ECS Fargate on ARM64 (Graviton) | No control-plane fee (unlike EKS at about $73/month), native Service Auto Scaling, no node management, about 20% cheaper than x86. |
| Task size | 0.5 vCPU / 1 GB, `-XX:MaxRAMPercentage=75`, health-check grace 120 s | The smallest size that is safe for Spring Boot; startup is slow at 0.5 vCPU. |
| Scale-out capacity | Fargate Spot for tasks above the minimum; on-demand for the minimum task | Cheap burst capacity without risking the baseline. |
| Egress | fck-nat on `t4g.nano` instead of a NAT Gateway | About $4/month instead of about $43/month. Private tasks also avoid about $3.6/month per public IPv4 address. |
| Secrets | SSM Parameter Store SecureString | Free at the standard tier, already used by the existing deployment. Secrets Manager adds cost and a 7-day deletion window. |
| Cache | ElastiCache for Valkey `cache.t4g.micro` | Redis-compatible and cheaper than the Redis engine. |
| Edge | CloudFront built-in certificate, ALB reachable only with the origin secret header | Same pattern as the current dev environment. |

#### Scaling policies

| Service | Policy | Min / max |
| --- | --- | --- |
| `commerce-svc` | Target tracking on `ALBRequestCountPerTarget`, with CPU at 50% as a fallback; scale-out cooldown 60 s | 1 / 6 |
| `identity-svc` | Target tracking on CPU at 50% | 1 / 3 |
| `notification-svc` | Step scaling on SQS backlog per task (`ApproximateNumberOfMessagesVisible` divided by the running task count) | 1 / 4 |

### 2.6 Persistence design

- **Instance:** one RDS PostgreSQL instance (`db.t4g.micro`, single-AZ) hosting three databases: `identity`, `commerce` and `notification`.
- **Roles:** each service gets its own DB role, which can connect only to its own database.
- **Schema:** each database keeps the baseline PostgreSQL schema `domain`, so JDBC `currentSchema` and Flyway `defaultSchema` settings carry over unchanged.
- **Migrations, while `dev` is active:** the baseline `db/migration` history stays one ordered stream, because `dev` keeps adding versions to it (for example V19 and later). Every service database applies the full stream, and ownership is enforced by grants: each service role gets privileges only on the tables it owns (section 2.2), so foreign tables exist but stay empty and unreachable. This keeps every `dev` migration a clean merge.
- **Migrations, after `dev` freezes:** each service gets its own migration location with a fresh baseline for its tables only, and foreign tables are no longer created. This is the end state stated in the proposal (section 5.4).
- **Trade-off:** isolation is logical, but the instance is physically shared. The report records this cost-driven decision and names separate instances as the scale-out path.
- **Other stores:** Valkey holds sessions and caches only. It keeps no durable business state.
- **API contract:** `contracts/openapi.json` stays one aggregated contract covering every service's public `/api/v1` paths behind the single edge, so the frontend client is unaffected by the split. `/internal/**` endpoints are never part of it.

---

## 3. Scope of Work

### 3.1 Platform components and use cases

Seed, producers, consumers, the core interaction and the use-case-to-service mapping (UC1–UC9 plus event-driven notifications) are defined in the proposal, sections 6.1 and 6.2. Business use cases are inherited from `dev` and are not re-implemented here; the fork only changes where they run.

### 3.2 Scalability demonstration

1. **HTTP scaling:** k6 ramps load on `GET /api/v1/products`. The dashboard shows `commerce-svc` scaling from 1 task to N and p95 latency recovering.
2. **Queue scaling:** a burst of team expiries grows the SQS backlog; `notification-svc` scales out and drains it.
3. **Self-healing:** stopping a task manually; ECS replaces it with no failed requests behind the ALB.
4. **Safe deployment:** deploying a deliberately broken image; the circuit breaker rolls back.
5. **Pipeline:** a PR run showing the plan comment, security scans, the approval gate and the apply.

k6 scripts live under `perf/` and run through `workflow_dispatch` or locally.

### 3.3 Cloud-native design

#### Stack layering

The environment is **disposable**: the runtime layer is destroyed when not in use and recreated with seeded data. The layers are split so that nothing with a stable external identity is ever destroyed.

| Stack | Contents | Lifecycle |
| --- | --- | --- |
| `foundation` | Terraform state bucket (`use_lockfile = true`), GitHub OIDC provider and roles, ECR repositories (lifecycle: keep the last 10 images), SSM parameters (Stripe test keys, Google client secret, Stripe webhook secret, origin secret), AWS Budgets alerts | Persistent |
| `edge` | S3 SPA bucket, CloudFront distribution; reads the ALB DNS name from `runtime` remote state | Persistent (re-applied after each `runtime` apply) |
| `runtime` | VPC, fck-nat, ALB, ECS cluster and services, RDS, Valkey, SNS/SQS, Service Connect namespace, CloudWatch log groups, dashboard, alarms | Created before use, destroyed after |

CloudFront must stay persistent. Recreating it changes the `*.cloudfront.net` domain, which breaks the Google OAuth redirect URI and the Stripe webhook endpoint. While `runtime` is down, `/api/*` returns 502. Optionally, the origin can point at an S3 maintenance page instead.

#### Directory layout

```
infra/swe5001/
  foundation/
  edge/
  runtime/
  modules/
    network/          # VPC, subnets, fck-nat
    ecs-cluster/
    ecs-service/      # task definition, service, target group, listener rule, autoscaling, log group, Service Connect
    rds-postgres/
    cache/            # ElastiCache Valkey
    messaging/        # SNS topic, SQS queue, DLQ, subscription, queue policy
    observability/    # dashboard, alarms
```

#### Destroy and recreate rules

- **RDS:** `skip_final_snapshot = true` and `deletion_protection = false`. The DB password comes from `random_password` and is written to SSM. Do not use `manage_master_user_password`.
- **KMS:** no customer-managed keys; use AWS-managed keys. Customer-managed keys have a minimum 7-day deletion window.
- **CloudWatch logs:** Terraform creates the log groups explicitly with 7-day retention. Otherwise ECS creates them implicitly and they collide on the next apply.
- **S3:** any bucket inside `runtime` sets `force_destroy = true`. ALB access logs are off for the demo environment.
- **ECR:** repositories live in `foundation`, so images survive a `runtime` destroy.
- **Service Connect:** destroying the namespace can fail while ECS services are still draining, so the destroy job retries once.

#### Expected timings

- **Apply:** about 15–25 minutes in total. RDS and Valkey take 5–10 minutes each, in parallel; the ALB about 3 minutes; ECS service start 3–5 minutes; the CloudFront origin update 3–5 minutes.
- **Destroy:** about 10–15 minutes.

#### Test data

- **Seed scripts:** each service owns a Flyway repeatable seed script at `db/seed/demo/R__demo_seed.sql`, loaded only in the demo environment through `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed/demo`. Production configuration never includes this location.
- **identity seed:** admin and merchant role bindings for team members' Google accounts, applied on first login.
- **commerce seed:** products, team price policies, and group deals in representative states: open, one member short of success, and about to expire (for the live expiry, refund and notification demo). Bulk rows for load testing are generated with `generate_series`, for example 10,000 products and 1,000 deals.
- **notification:** no seed; its data is produced by events.
- **Load-test authentication:** the main load test targets public `GET /api/v1/products` and needs no authentication. If authenticated flows must be load-tested, a token-minting endpoint may be added under these conditions:
  - it is annotated with `@Profile("demo")`;
  - it requires a shared header secret stored in SSM;
  - a test asserts that it is absent under the default and production profiles.

  If that guard cannot be made reliable, authenticated load tests are dropped.

#### Cost estimate

| Item | Hourly (approx.) |
| --- | --- |
| Fargate ARM64, 3 tasks at 0.5 vCPU / 1 GB | $0.075 |
| ALB | $0.025 |
| RDS `db.t4g.micro` | $0.026 |
| ElastiCache Valkey `cache.t4g.micro` | $0.020 |
| fck-nat and public IPv4 | $0.015 |
| **Runtime total** | **about $0.17–0.20** |

- **Persistent layer while idle:** about $1–3/month (ECR storage, S3, SSM; CloudFront is near zero without traffic).
- **Usage pattern:** 30 four-hour sessions a month come to about $25/month, compared with about $125/month for running 24/7.
- **Guardrails:** AWS Budgets alerts at $50 and $100, the nightly `env-down`, and `terraform destroy` of every stack after the report deadline.

### 3.4 DevOps and DevSecOps

| Workflow | Trigger | Steps |
| --- | --- | --- |
| `ci-swe5001` | PRs to and pushes on `swe5001/main` | Path-filtered build and test of every assembly (monolith and the three services), Sonar (separate project keys), Trivy image scan, `terraform fmt`/`validate`/`plan`, checkov; the plan is posted as a PR comment. |
| Image publish and deploy | Push to `swe5001/main` | Build ARM64 images, push to ECR by digest, record the digest as the last known good release in SSM. If `runtime` is up, deploy with `amazon-ecs-deploy-task-definition`; the ECS deployment circuit breaker rolls back automatically. |
| `env-up` | Manual | Apply `runtime` with the last known good digests, apply `edge`, wait for healthy targets, run a smoke test, print the URLs to the job summary. |
| `env-down` | Manual and nightly at 23:00 SGT | Destroy `runtime`. |
| `env-cycle` | Every 2–3 days | Full up, smoke test, down, to catch apply drift early and to evidence rebuild-from-zero time. |

**Constraints:**

- **CI is paused on this branch** (2026-09-28) until the SWE5001 SonarCloud projects exist; see the [progress log entry](log/2026-09-28-ci-pause-branch-workflows.md). Until it is restored, every PR records local `./mvnw verify` (JDK 21) and `pnpm check` results.
- **Default-branch requirement:** `workflow_dispatch` and `schedule` only register from workflow files on the default branch (`dev`). A thin stub `.github/workflows/swe5001-env.yml` on `dev` that checks out `swe5001/main` is required for `env-up`, `env-down` and `env-cycle`. This touches the SWE5006 mainline and needs team agreement.
- **OIDC:** new roles are created in `foundation` with GitHub environment `swe5001-demo` (environment-type subject). The existing `dev` roles are not reused.
- **Tags:** tags must not match `v*`, because `ci-backend.yml` and `security.yml` start the production release chain on `v*` tags. Use `swe5001-*` tags only.

### 3.5 Platform engineering

- **`blocbuy-platform-starter`:** carries the `Result` envelope, `ErrorCode` base, Sa-Token configuration, trace-ID propagation, outbox and idempotency support. A new service inherits these by adding one dependency. It is also what makes the assembly approach in section 2.3 work.
- **Golden-path Terraform module:** `runtime` declares services as a map consumed by `for_each` over the `ecs-service` module. Adding a service is a map entry of about ten lines.

### 3.6 Branch conventions and dev sync

| Purpose | Name |
| --- | --- |
| Long-lived trunk | `swe5001/main` |
| Topic branches | `swe5001/<type>/<desc>-<yyyymmdd>`, where `<type>` is one of `feat`, `fix`, `infra`, `perf`, `refactor`, `docs`, `chore` |
| Sync from `dev` | `swe5001/sync/dev-<yyyymmdd>`, merged through a PR into `swe5001/main` |
| Fork-point tag | `swe5001-fork-base` on `71ddfed` |
| Milestone tags | `swe5001-demo-20261023`, `swe5001-report-20261116` |

**Rules:**

- **Sync direction:** changes flow one way only, `dev` → `swe5001/main`. Generic fixes land on `dev` first and arrive through a sync branch.
- **PR targets:** topic PRs target `swe5001/main`, never `dev`.
- **Commits:** Conventional Commits with the service as scope, for example `feat(identity-svc): ...` or `infra(runtime): ...`.
- **Branch protection:** a ruleset on `swe5001/main` requires a PR and green `ci-swe5001`.

#### Sync procedure

- **Cadence:** at least weekly while SWE5006 is active, and always before an extraction PR, so each sync stays small.
- **Method:** a true merge (`git merge --no-ff origin/dev` on the sync branch), never a rebase or squash, so the next sync starts from the recorded merge base.
- **Verification:** the synced branch passes `./mvnw verify` (JDK 21) and `pnpm check`, and every assembly still builds. A `dev` change that needs adaptation for the distributed topology (for example a new cross-context call) is fixed in the sync PR.
- **Log:** each sync gets a progress log entry listing the `dev` range, the conflicts and their resolution.

#### Standing conflict-resolution rules

These come from the first sync (2026-09-29) and apply to every later one.

| Conflict | Resolution |
| --- | --- |
| Workflow logic changed on `dev` (`ci-*.yml`, `security.yml`) | Take the `dev` logic, retarget `refs/heads/dev` to `refs/heads/swe5001/main`, and keep the fork's paused triggers until CI is restored. |
| `dev` changes EC2, SSM or S3 deploy jobs that the fork removed | Keep them removed. |
| SWE5006-only artifacts (`docs/agile/**`, `docs/ci/**`, `docs/quality/evidence/**`, sprint plans, board snapshots) | Keep them deleted, including new files `dev` adds there. |
| `docs/quality/nfr-register.md` | Keep the fork's QA register. Add a QA row only when a `dev` change affects a SWE5001 quality scenario. |
| New Flyway versions from `dev` | Accept as-is into the shared stream ([section 2.6](#26-persistence-design)); update the ownership grants if the migration creates a table. |
| Code in shared COLA modules | Merge normally; this is why section 2.3 keeps these paths stable. |

### 3.7 Out of scope

- Extracting payment or catalog into separate services (documented as the evolution path).
- Multi-region deployment and database read replicas.
- A native mobile application.
- A production launch with real payments; Stripe runs in test mode throughout.

---

## 4. Effort Estimates and Timeline

The authoritative work breakdown is the proposal's Table 7 (50 person-days); the [progress log](log/README.md) records actual hours against the same WBS numbers. The mapping below shows where the plan sections land.

| WBS | Work item | Plan section |
| --- | --- | --- |
| 2.1 | `blocbuy-platform-starter` | 2.3, 3.5 |
| 2.2 | Outbox, SNS/SQS publishing, idempotent consumption | 2.4 |
| 3.1–3.3 | Extract `notification-svc`, `identity-svc`; package `commerce-svc` | 2.3, 2.4 |
| 3.4 | Databases and Flyway per service | 2.6 |
| 3.5 | Demo and load-test seed data | 3.3 |
| 4.1–4.5 | Terraform stacks, modules, scaling, dashboard | 2.5, 3.3 |
| 5.1–5.3 | CI, Terraform pipeline, ECS deploy and environment workflows | 3.4 |
| 6.1–6.3 | k6, resilience drills, end-to-end regression | 3.2 |
| n/a | `dev` syncs (about 0.5 person-days each, estimated) | 3.6 |

| Window | Work |
| --- | --- |
| 28 Sep – 4 Oct | Proposal. First `dev` sync (done 2026-09-29). `foundation`, `network` and `ecs-cluster` modules. Run the monolith assembly (`topology=monolith`) on Fargate: strangler step 0. |
| 5 – 11 Oct | `blocbuy-platform-starter` and the `blocbuy.topology` switch. Outbox, SNS and SQS. `notification-svc` assembly with event consumers. |
| 12 – 18 Oct | `identity-svc` assembly (HTTP `UserAppService` adapter, shared session). Per-service databases and grants. ALB routing. Scaling policies. |
| 19 – 22 Oct | k6 scripts, dashboard, self-healing and rollback drills, full rehearsal on 22 Oct. |
| 23 Oct | `env-up` before noon; no destroy that day; presentation at 16:15. |
| by 16 Nov | Per-service Flyway baselines once `dev` freezes. Report; final `terraform destroy`. |

---

## 5. Risks and Open Questions

| Risk or question | Mitigation or owner |
| --- | --- |
| The `env-up` apply fails on presentation day | Regular `env-cycle` runs; apply before noon on 23 Oct; a recorded fallback video of the scaling demo. |
| The default-branch workflow stub needs SWE5006 team approval | Raise at the next stand-up; the fallback is running Terraform locally with the same scripts. |
| Synchronous `requireActive` inside the webhook transaction | 1–2 s timeout; failure takes the existing reject path; Stripe retries. |
| A `dev` change adds a new cross-context call that the distributed topology cannot serve | ArchUnit rule and all-assembly CI build fail in the sync PR; the fix is a port plus HTTP adapter or an event, made in that PR. |
| Syncs grow large while CI is paused | Weekly cadence; local `verify` and `check` recorded in each sync's log entry; restore CI before the first feature PR. |
| When does SWE5006 feature work on `dev` stop? | Open. It gates the per-service Flyway baselines and any code relocation (sections 2.3 and 2.6). |
| AWS account budget or credits under the organisation SCP | Confirm with the account owner before the first `runtime` apply. |
| Proposal reviewers challenge the three-service scope | The rationale is in [section 2.2](#22-decomposition-decision); record it as an ADR on this branch. |
| Sonar configuration for the new services | Create separate project keys (`blocbuy-swe5001-*`). |
