# SWE5001 Microservice Refactoring Plan

This is the engineering companion to the [project proposal](project-proposal.typ). Its sections follow the proposal's structure (Overview, General Architecture, Scope of Work, Effort Estimates, Risks): the proposal states *what* is built and *why* for the reviewers, and this plan records *how* for the team. It covers the service decomposition, the implementation approach that lets the monolith and the services coexist on one trunk, the integration and data design, the AWS deployment model, the Terraform layout and the working conventions shared with SWE5006.

Implementation detail is split into four plans, one per aspect:

| Aspect | Plan |
| --- | --- |
| Backend code architecture | [impl-backend-architecture.md](impl-backend-architecture.md) |
| GitHub repository configuration and pipelines | [impl-github-configuration.md](impl-github-configuration.md) |
| AWS infrastructure | [impl-aws-infrastructure.md](impl-aws-infrastructure.md) |
| Verification, performance testing and observability | [impl-verification-observability.md](impl-verification-observability.md) |

Status: **Draft, revised 2026-10-04** (first draft 2026-09-28; 2026-10-04: the distributed shape is deployed only by a `dev` → `prod` merge, once per two-week sprint; each deployment creates the runtime environment, validates it and destroys it again, [section 3.4](#34-devops-and-devsecops)). Cost and effort figures are estimates; confirm cost with the AWS Pricing Calculator before committing to a budget.

---

## 1. Overview

### 1.1 One codebase, two deployment shapes

BlocBuy is developed for SWE5006 as a COLA modular monolith on the `dev` branch. SWE5001 grades a different set of outcomes: platform thinking (seed, producers, consumers, reusable services), demonstrated scalability (auto-scaling under scripted load), cloud-native use of managed services, automated DevSecOps, and minimum security controls.

The SWE5001 work lands on the same trunk, `dev`, instead of a long-lived fork. A fork was tried first (2026-09-28) and dropped on 2026-09-29: its first sync already produced seven conflicts, and every SWE5006 feature would have had to be merged twice. On one trunk, business features are written once. SWE5001 adds a second deployment shape, three services on ECS, next to the SWE5006 monolith on EC2, and the shape is chosen at deployment time ([section 2.3](#23-implementation-approach-split-by-assembly-not-by-relocation)).

Development and release are separated. All work merges into `dev`; the distributed shape is released by merging `dev` into a long-lived `prod` branch, once per two-week sprint, and only that merge deploys to ECS ([section 3.4](#34-devops-and-devsecops)). To minimise cost, the ECS runtime exists only while it is used: a promotion creates it from Terraform, validates the deployment and destroys it, and the final presentation brings it up again with a manual script. The SWE5006 monolith keeps its own release path (`dev` pushes for the EC2 dev host, `v*` tags for EC2 production) and is not affected by `prod`.

### 1.2 Course constraints that shape the plan

| Constraint | Consequence |
| --- | --- |
| About 10 person-days per member (50 person-days in total) | The service count is capped at three, and the split must avoid sagas and distributed transactions. |
| Demo must show auto-scaling and scripted performance testing | Every service has a scaling policy, and k6 scripts and a CloudWatch dashboard are deliverables. |
| DevSecOps pipeline scripts are deliverables | Infrastructure is Terraform-only, and deployment runs through GitHub Actions with OIDC. |
| Demo project with no real traffic; cost must stay minimal | The runtime environment is disposable and normally absent. It exists only during a sprint promotion run (about one hour) or a manual session (baseline measurement, rehearsal, presentation), and is destroyed afterwards ([section 3.4](#34-devops-and-devsecops)). |
| SWE5006 feature work continues on the same trunk | SWE5001 changes are additive: they must not move or rewrite code that SWE5006 is still changing, and must keep the monolith deployment and its tests green ([section 2.3](#23-implementation-approach-split-by-assembly-not-by-relocation)). |
| Key dates: proposal 4 Oct, presentation 23 Oct (Team 8, 16:15), report 16 Nov | See the timeline in [section 4](#4-effort-estimates-and-timeline). |

### 1.3 Deliberate deviations from SWE5006 rules (distributed shape only)

Both deviations need a backend ADR and an amendment to `backend-constraints-skill` before the first code PR, because they change rules the SWE5006 team works under.

- **Messaging.** The SWE5006 backend constraints exclude message brokers. The distributed shape introduces Amazon SNS and SQS for cross-service events; the monolith keeps in-process Spring events only. RabbitMQ and Elasticsearch stay out.
- **Deployment unit.** Besides the single monolith deployable, the same code is also packaged as three services. Inside each service, the COLA layering, DDD, `Result` envelope, `ErrorCode` enums and Sa-Token RBAC rules still apply unchanged.

### 1.4 Baseline

- **Backend:** a Java 21 / Spring Boot 4 modular monolith with six bounded contexts: `identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`.
- **Context integration:** in-process ports and Spring events. `TeamSucceeded` and `TeamExpired` subscribers run at `BEFORE_COMMIT` ([backend ADR 0002](../../backend/docs/adr/0002-team-lifecycle-events.md)).
- **Runtime:** one EC2 host running Docker Compose with the backend, PostgreSQL 18 and Redis 8. CloudFront routes `/api/*` to an ALB and the EC2 host, and serves everything else from S3. This stays the SWE5006 deployment and is not changed by SWE5001.
- **Existing Terraform:** `infra/terraform/backend-app-stack` (EC2, ALB, ECR, OIDC) and `infra/terraform/frontend-stack` (S3, CloudFront). The SWE5001 stacks live separately under `infra/swe5001/`.
- **Starting point:** `dev` at `c66bb40` (2026-09-29).

---

## 2. General Architecture

### 2.1 Architectural drivers

The drivers (scalability, consistency, availability, security, cost efficiency, modifiability) are stated in the proposal, section 5.1. Their checkable scenarios are added to the shared [NFR register](../quality/nfr-register.md) as new rows, in the PR that implements each one. This plan adds one internal driver, **coexistence**: every SWE5001 change keeps the SWE5006 monolith, its deployment and its test suite working.

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

The split must not disrupt SWE5006 work on the same trunk. Between 2026-09-01 and 2026-09-29, `dev` changed main code in the commerce contexts in 46 commits (catalog 13, groupbuy 16, order 7, payment 10) and in identity and notification in 7 (identity 4, notification 3). Moving the commerce code into new directories would conflict with every open SWE5006 pull request in those contexts. Adding entry points and adapters next to the existing code does not.

#### How

A deployable service is an **assembly**: a Spring Boot entry point plus configuration that selects which bounded contexts run in that process. Business code is written once, in the existing COLA modules.

```
backend/
  blocbuy-client / domain / application / adapter / infrastructure   # shared, unchanged paths
  blocbuy-bootstrap/          # monolith assembly (topology=monolith): SWE5006 deployment, baseline tests, strangler step 0
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

- **Selection:** the property `blocbuy.topology` selects the adapter through `@ConditionalOnProperty`. The monolith value is the default (`matchIfMissing = true`), so the SWE5006 deployment and its tests run unchanged.
- **Callers do not change:** a port such as `UserAppService` keeps its interface; only its implementation differs by topology. The same holds for the in-process `@TransactionalEventListener` subscribers inside `commerce-svc`.
- **Shared configuration:** nothing moves. `blocbuy-bootstrap` is also published as a plain library jar, and each service imports only the configuration it needs from `com.blocbuy.bootstrap.config` and `com.blocbuy.bootstrap.scheduling` (Stripe and the team-expiry and refund jobs go to `commerce-svc`). `com.blocbuy.common` (Sa-Token, Redis, MyBatis) is already shared through `blocbuy-infrastructure`. New cross-cutting code goes into `blocbuy-platform-starter`. Details: [backend plan](impl-backend-architecture.md), section 3.
- **Relocation is deferred:** moving `identity` and `notification` code into their own Maven aggregates is optional and happens only after SWE5006 feature work stops. The commerce contexts stay in place.

#### Guardrails

- **ArchUnit:** commerce contexts must not depend on identity or notification internals, except through `UserAppService` and domain events. This makes the assembly split safe even though the code is co-located.
- **CI builds every assembly:** the monolith and the three service images build on every PR. A SWE5006 change that breaks the distributed shape, or a SWE5001 change that breaks the monolith, fails in its own PR.
- **One behaviour suite for both topologies:** the existing integration tests run against the monolith assembly; a smoke suite runs against the three services in Docker Compose.

### 2.4 Integration

#### Synchronous calls

- **Mechanism:** ECS Service Connect (Cloud Map namespace, Envoy sidecar), which provides retries and per-service metrics.
- **Identity checks:** `commerce-svc` keeps the `UserAppService` interface and swaps in an HTTP adapter that calls `identity-svc` at `GET /internal/users/{id}/active`, so the 10 call sites stay unchanged.
- **Internal endpoints:** `/internal/**` is reachable only inside the VPC. It is excluded from ALB routing and the security groups only admit peer services.
- **Risk:** `OrderFulfilmentAdapter.fulfil` calls `requireActive` inside the webhook transaction, and its reject branch turns any `BizException` into `BUYER_INACTIVE` and answers Stripe with 2xx. The HTTP adapter therefore maps only `404` (missing) and `409` (disabled) to the identity `BizException`s. A timeout (1–2 s) or `5xx` throws a separate unchecked exception that bypasses the reject branch, rolls back the webhook transaction and returns 500 through the global exception handler, so Stripe redelivers the event.

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
- **Roles:** each service gets its own runtime DB role, which can connect only to its own database. Flyway never uses it: each service assembly configures a separate migration role (`SPRING_FLYWAY_USER`/`PASSWORD`) that owns the database and can apply the full migration stream.
- **Schema:** each database keeps the baseline PostgreSQL schema `domain`, so JDBC `currentSchema` and Flyway `defaultSchema` settings carry over unchanged.
- **Migrations, while SWE5006 is active:** the baseline `db/migration` history stays one ordered stream. The SWE5006 databases have already applied the existing versions (V1–V19 at `c66bb40`), so those scripts cannot be split or rewritten without breaking Flyway checksums, and SWE5006 keeps adding versions. Every service database applies the full stream, and ownership is enforced by grants: each service's runtime role gets privileges only on the tables it owns (section 2.2), so foreign tables exist but stay empty and unreachable. A new SWE5006 migration then needs no SWE5001 follow-up beyond a grant for any new table.
- **Migrations, after SWE5006 feature work stops:** each service gets its own migration location with a fresh baseline for its tables only, and foreign tables are no longer created. This is the end state stated in the proposal (section 5.4).
- **Trade-off:** isolation is logical, but the instance is physically shared. The report records this cost-driven decision and names separate instances as the scale-out path.
- **Other stores:** Valkey holds sessions and caches only. It keeps no durable business state.
- **API contract:** `contracts/openapi.json` stays one aggregated contract covering every service's public `/api/v1` paths behind the single edge, so the frontend client is unaffected by the split. `/internal/**` endpoints are never part of it.

---

## 3. Scope of Work

### 3.1 Platform components and use cases

Seed, producers, consumers, the core interaction and the use-case-to-service mapping (UC1–UC9 plus event-driven notifications) are defined in the proposal, sections 6.1 and 6.2. Business use cases are shared with SWE5006 and are not re-implemented; SWE5001 only changes where they run.

### 3.2 Scalability demonstration

Validation is deliberately split in two, to keep runtime hours low:

- **Per promotion (each sprint):** only a simple validation. The smoke suite runs through CloudFront against the freshly created environment, and the run checks that every ECS service reached steady state on the new images. Nothing below runs in a promotion.
- **Final presentation:** the demonstrations below run only in manual sessions (one baseline measurement, the rehearsal on 22 Oct and the presentation on 23 Oct), each created and destroyed with the manual scripts.

1. **HTTP scaling:** k6 ramps load on `GET /api/v1/products`. The dashboard shows `commerce-svc` scaling from 1 task to N and p95 latency recovering.
2. **Queue scaling:** a burst of team expiries grows the SQS backlog; `notification-svc` scales out and drains it.
3. **Self-healing:** with `commerce-svc` held at two tasks (minimum raised to 2 for the run), stopping one task manually; the ALB keeps routing to the healthy task while ECS replaces the stopped one, with no failed requests. With a single task the ALB has no healthy target during replacement and returns 503.
4. **Safe deployment:** deploying a deliberately broken image; the circuit breaker rolls back.
5. **Pipeline:** a `dev` → `prod` promotion PR showing the release gate (service images, Trivy, offline runtime validation and a foundation-only `terraform plan` comment), the review approval, and the merge whose trusted `prod` run privately plans and creates the runtime, rolls the new images onto ECS, validates them and destroys the environment again.

k6 scripts live under `perf/` and run through `workflow_dispatch` or locally.

### 3.3 Cloud-native design

#### Stack layering

The environment is **disposable**: the runtime layer is normally absent, is created with seeded data only for a promotion run or a manual session, and is destroyed afterwards. The layers are split so that nothing with a stable external identity is ever destroyed.

| Stack | Contents | Lifecycle |
| --- | --- | --- |
| `foundation` | Terraform state keys under `swe5001/` in the existing state bucket (`use_lockfile = true`), GitHub OIDC roles (the existing OIDC provider is reused), ECR repositories (lifecycle: keep the last 10 images), AWS Budgets alerts. Secret SSM parameters (Stripe test keys, Google client secret, Stripe webhook secret, origin secret) are seeded by an operator, not by Terraform | Persistent |
| `edge` | S3 SPA bucket and CloudFront distribution; initially uses an unavailable placeholder, then `env-up.sh` supplies the new ALB DNS output explicitly without reading runtime state | Persistent (re-applied after each `runtime` apply) |
| `runtime` | VPC, fck-nat, ALB, ECS cluster and services, RDS, Valkey, SNS/SQS, Service Connect namespace, CloudWatch log groups, dashboard, alarms | Created by a promotion run or `env-up.sh`; destroyed at the end of the run or by `env-down.sh` |

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
- **Usage pattern:** two promotion runs of about one hour (Sprint 3 and Sprint 4) plus about 12 hours of manual sessions (baseline about 3 h, rehearsal about 3 h, presentation day about 6 h) come to about 14 runtime hours, or **under US$5 in runtime charges** for the course, compared with about $125/month for running 24/7. The persistent layer above is billed separately.
- **Guardrails:** every promotion run destroys `runtime` in an always-run final step; `env-status.sh` fails if any resource tagged `Stack=runtime` remains; the nightly `env-down` catches a forgotten manual session; AWS Budgets alerts at $20 and $50; `terraform destroy` of every stack after the report deadline.

### 3.4 DevOps and DevSecOps

The existing pipelines are extended, not duplicated. SWE5001 adds jobs and workflows next to the SWE5006 ones and leaves the EC2 deployment untouched.

#### Release flow: `dev` → `prod`

```text
topic branch ──PR──▶ dev ──promotion PR──▶ prod ──push──▶ swe5001-deploy.yml ──▶ ECS (distributed shape)
                      │                       ▲
                      │                       └─ release gate: promotion-source, service-images, infra-plan
                      ├─ push ──▶ ci-backend / ci-frontend ──▶ EC2 dev host (SWE5006, unchanged)
                      └─ v* tag ──▶ ci-backend ──▶ EC2 production (SWE5006, unchanged)
```

- **`dev` is the integration trunk.** Every change, including SWE5001 infrastructure and workflow changes, lands on `dev` through the normal PR rules. A `dev` push never touches the SWE5001 AWS environment; it only runs CI (build, test, image smoke-boot, Trivy, `terraform plan`).
- **`prod` is the SWE5001 release branch.** It is created once from `dev` and only ever advances by a promotion PR whose head is `dev`. No topic branch targets `prod`, and there are no direct pushes or hotfixes on it: a fix lands on `dev` and is promoted.
- **Promotion merges with a merge commit**, never squash or rebase. `prod` then stays an ancestor-compatible snapshot of `dev`, and the next promotion PR shows exactly the `dev` commits not yet released, without conflicts.
- **The merge is the only deployment trigger.** Nothing else deploys to ECS automatically. The push to `prod` starts one self-contained **promotion run** (below).
- **Cadence: once per sprint.** One promotion at the end of each two-week sprint: Sprint 3 on Fri 9 Oct (after the 8 Oct code freeze) and Sprint 4 on Tue 20 Oct (before the 22 Oct rehearsal). An extra promotion is the exception and needs the team's agreement, because each one costs a full environment build.
- **Separate from SWE5006 production.** The `prod` branch is not the SWE5006 `backend-prod` environment. No existing workflow triggers on `prod` (they listen to `dev` pushes, PRs into `dev` and `v*` tags), so promotion cannot start an EC2 release, and `v*` tags cannot start an ECS release.

#### Promotion run (one per sprint)

`swe5001-deploy.yml` on the push to `prod` runs these steps in order. The environment never outlives the run.

| Step | Action | Approx. time |
| --- | --- | --- |
| 1. Build | Build the three ARM64 service images from the merge commit, push to ECR by digest, record them as candidates in SSM `/blocbuy/swe5001/candidate/<service>` | 10 min |
| 2. Create | `env-up.sh`: apply `runtime` from zero with the last known good digests (the candidates on the first run), run the DB-init task with the demo seed, apply `edge`, wait for healthy targets | 20–25 min |
| 3. Deploy | Roll the candidate digests onto ECS with the deployment circuit breaker, wait for steady state on the new task definitions. This exercises a real rolling deployment rather than a first install | 5 min |
| 4. Validate | Smoke suite through CloudFront ([verification plan](impl-verification-observability.md), section 3); on success copy the candidates to `/blocbuy/swe5001/release/<service>` (last known good). Publish the SWE5001 SPA if `frontend/**` or `contracts/**` changed | 2 min |
| 5. Evidence | Upload the smoke output, ECS service events, deployed digests and step timings as a workflow artifact, because the log groups are destroyed with `runtime` | 1 min |
| 6. Destroy | `env-down.sh` then `env-status.sh`; runs with `if: always()`, so a failed build, deploy or smoke test still tears the environment down. The run fails if any runtime resource remains | 10–15 min |

A failed deploy or smoke test leaves the release digests unchanged and fails the run; the fix lands on `dev` and is promoted again. Each promotion run is also a full rebuild-from-zero, so it replaces the periodic `env-cycle` the first draft had.

#### Manual environment scripts

For sessions that need a live environment outside a promotion run (baseline measurement, the rehearsal, the final presentation), operators use scripts under `infra/swe5001/scripts/`. The workflows call the same scripts, so manual and automated runs cannot drift.

| Script | Does |
| --- | --- |
| `env-up.sh [--release\|--candidate]` | `terraform init` and apply `runtime` with the release (default) or candidate digests, run the DB-init task, scale services to their minimum, apply `edge`, wait for healthy targets, run the smoke suite, print the CloudFront URL |
| `env-down.sh` | Destroy `runtime`, retrying once on Service Connect namespace errors, then run `env-status.sh` |
| `env-status.sh` | List resources tagged `Course=swe5001,Stack=runtime` and the ECS service states; exits non-zero if anything remains after a destroy |

The scripts are idempotent and refuse to run unless the caller is in account `402178232802`, region `ap-southeast-1`. Locally they use the operator's own AWS credentials (the same ones used for `foundation`); in GitHub they run under the `swe5001-demo` roles. Concurrent runs are blocked by the Terraform S3 state lock. The same actions are available from GitHub as `swe5001-env.yml` (`up`, `down`, `status`) dispatched on the `prod` ref.

#### Workflows

| Workflow | Trigger | Steps |
| --- | --- | --- |
| `ci-backend.yml` (extended) | Existing triggers (PRs into `dev`, `dev` pushes, `v*` tags) | Existing gates unchanged. New: calls the reusable `swe5001-service-images.yml` to build and smoke-boot every assembly image (monolith and the three services) and scan each with Trivy, without pushing. New modules join the existing JaCoCo aggregate and SonarCloud project. |
| `infra-swe5001.yml` (new) | PRs into `dev` or `prod` touching `infra/swe5001/**` | `terraform fmt`/`validate` and mocked tests for both existing stacks, checkov; the account-backed PR plan and redacted comment cover `foundation` only. No PR role reads `runtime.tfstate` or applies infrastructure. |
| `swe5001-release-gate.yml` (new) | PRs into `prod` | `promotion-source` (fails unless the head branch is `dev` in this repository), `service-images` (same reusable workflow), `infra-plan` (offline `edge`/`runtime` validation and foundation-only plan). These are the required checks on `prod`; secret-bearing account-backed plans run privately after promotion under the `prod`-only role or by an operator. |
| `swe5001-deploy.yml` (new) | Push to `prod` (each promotion merge); manual on the `prod` ref only for the V6 drill | The promotion run above: build, create, deploy, validate, evidence, destroy. |
| `swe5001-env.yml` (new): `up`, `down`, `status` | Manual, dispatched on the `prod` ref | Wrappers around `env-up.sh`, `env-down.sh` and `env-status.sh` for manual sessions. |
| `swe5001-env.yml` (new): nightly `down` | Nightly at 23:00 SGT | Safety net: destroys `runtime` if a manual session was left running; a no-op when nothing exists. |

**Constraints:**

- **Default branch:** `workflow_dispatch` and `schedule` only register from workflow files on the default branch (`dev`), and scheduled runs always execute on `dev`. Every job that touches the SWE5001 AWS environment is therefore guarded by `github.ref == 'refs/heads/prod'`. A scheduled trigger runs a small job without AWS access that dispatches the same workflow on the `prod` ref (`gh workflow run swe5001-env.yml --ref prod`, allowed for `GITHUB_TOKEN`). The workflow version that runs is the one on `prod`, so workflow changes also take effect only on promotion.
- **OIDC:** new roles are created in `foundation` with GitHub environment `swe5001-demo` (environment-type subject), whose deployment branch policy admits `prod` only. The existing SWE5006 roles and environments are not reused, and the new roles cannot touch the EC2 stack.
- **Tags:** tags must not match `v*`, because `ci-backend.yml` and `security.yml` start the SWE5006 production release chain on `v*` tags. Use `swe5001-*` tags only, cut on `prod` commits.
- **Required checks:** the SWE5006 required checks (`build-quality`, `contract-check`, `frontend-check`) stay required on `dev`. New SWE5001 jobs on `dev` start as non-required and become required once stable. On `prod`, the release-gate checks are required from the start.
- **Rollback:** the circuit breaker handles a failed rollout within a run. Because the environment is destroyed after each promotion, there is no long-running release to roll back; a bad release is reverted on `dev`, and the next `env-up.sh` uses the last known good digests by default.
- **Deviation from the proposal:** the proposal lists a periodic full rebuild cycle. It is replaced by the per-sprint promotion run, which rebuilds from zero every time, to save runtime hours.

### 3.5 Platform engineering

- **`blocbuy-platform-starter`:** carries the `Result` envelope, `ErrorCode` base, Sa-Token configuration, trace-ID propagation, outbox and idempotency support. A new service inherits these by adding one dependency. It is also what makes the assembly approach in section 2.3 work.
- **Golden-path Terraform module:** `runtime` declares services as a map consumed by `for_each` over the `ecs-service` module. Adding a service is a map entry of about ten lines.

### 3.6 Working conventions

SWE5001 follows the repository's existing rules in `CONTRIBUTING.md`: PRs target `dev`, stay small, and need green required checks and CODEOWNERS review. The only PRs into `prod` are promotion PRs from `dev`.

| Purpose | Convention |
| --- | --- |
| Trunk | `dev`, shared with SWE5006 |
| Release branch | `prod`, SWE5001 only; advanced by `dev` → `prod` promotion PRs merged with a merge commit ([section 3.4](#34-devops-and-devsecops)) |
| Topic branches | The repository's existing pattern, with `swe5001` in the name, for example `feature/swe5001-identity-svc-20261012` or `chore/swe5001-foundation-stack-20261001` |
| Commits | Conventional Commits with the service or stack as scope, for example `feat(identity-svc): ...` or `chore(infra-runtime): ...` |
| Promotion PRs | Head `dev`, base `prod`, title `chore(release): promote dev to prod (<yyyy-mm-dd>)`; the body lists the included PRs and links the release-gate plan comment |
| Milestone tags | `swe5001-demo-20261023`, `swe5001-report-20261116`, on `prod` commits |

**Coexistence rules:**

- **Additive only:** SWE5001 PRs add modules, adapters, configuration, workflows and Terraform. They do not move, rename or rewrite shared business code or existing migrations (sections 2.3 and 2.6).
- **Monolith stays the default:** `blocbuy.topology` defaults to `monolith`, and the SWE5006 EC2 deployment, its workflows and its Terraform are not changed by SWE5001 PRs.
- **Shared rules still apply:** SWE5001 PRs follow the same backend constraints, contract-regeneration rule and evidence practice as SWE5006 work. The deviations in section 1.3 are recorded as ADRs before code relies on them.
- **Progress tracking:** each SWE5001 PR cites its proposal WBS item (section 4) and records hours per member, so fortnightly progress reports can be built from the PR history.

### 3.7 Out of scope

- Extracting payment or catalog into separate services (documented as the evolution path).
- Multi-region deployment and database read replicas.
- A native mobile application.
- A production launch with real payments; Stripe runs in test mode throughout.

---

## 4. Effort Estimates and Timeline

The authoritative work breakdown is the proposal's Table 7 (50 person-days); PRs record actual hours against the same WBS numbers (section 3.6). The mapping below shows where the plan sections land.

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

| Window | Work |
| --- | --- |
| 28 Sep – 4 Oct | Proposal. `dev` → `prod` release flow agreed. ADRs for section 1.3. `foundation`, `network` and `ecs-cluster` modules. Run the monolith assembly (`topology=monolith`) on Fargate: strangler step 0. |
| 5 – 11 Oct | `blocbuy-platform-starter` and the `blocbuy.topology` switch. Outbox, SNS and SQS. `notification-svc` assembly with event consumers. |
| 12 – 18 Oct | `identity-svc` assembly (HTTP `UserAppService` adapter, shared session). Per-service databases and grants. ALB routing. Scaling policies. |
| 9 Oct | Sprint 3 promotion run: first deployment of the distributed shape (create, validate, destroy). |
| 19 – 22 Oct | Sprint 4 promotion run on Tue 20 Oct. Manual session for the baseline measurement if not done earlier. Rehearsal on 22 Oct in a manual session: k6, dashboard, self-healing and rollback drills, fallback recording, then `env-down.sh`. |
| 23 Oct | `env-up.sh` before noon; presentation at 16:15; `env-down.sh` and `env-status.sh` after it. |
| by 16 Nov | Per-service Flyway baselines once SWE5006 feature work stops. Report; final `terraform destroy`. |

---

## 5. Risks and Open Questions

| Risk or question | Mitigation or owner |
| --- | --- |
| A promotion run fails half-way and leaves billable resources | The destroy step runs with `if: always()` and is followed by `env-status.sh`, which fails the run if anything tagged `Stack=runtime` remains; the nightly `down` is the second line of defence. |
| Only two promotions happen, so a broken distributed shape is found late | `service-images` builds and smoke-boots every service on each `dev` PR; the Sprint 3 promotion is scheduled early enough (9 Oct) to fix issues before the Sprint 4 promotion. |
| `prod` diverges from `dev` (squash merge, direct push, hotfix on `prod`) | The `prod` ruleset blocks direct and force pushes and allows only merge commits; the `promotion-source` check rejects any head other than `dev`. |
| The `env-up.sh` apply fails on presentation day | Every promotion run and the 22 Oct rehearsal are full rebuilds with the same script; apply before noon on 23 Oct; a recorded fallback video of the scaling demo. |
| SWE5001 changes on the shared trunk break SWE5006 work, its deployment or its evidence | Additive changes only; the monolith stays the default topology; CI builds the monolith and the SWE5006 required checks stay required; the EC2 stack and workflows are not touched (section 3.6). |
| The SWE5006 team does not agree to SNS/SQS and the new modules on `dev` | Agree the section 1.3 ADRs at the next stand-up, before the first code PR. |
| Synchronous `requireActive` inside the webhook transaction | 1–2 s timeout; a timeout or `5xx` fails the webhook with 500 instead of taking the `BUYER_INACTIVE` reject path, so Stripe retries (section 2.4). |
| A SWE5006 change adds a new cross-context call that the distributed shape cannot serve | The ArchUnit rule and the all-assembly CI build fail in that PR; the fix is a port plus HTTP adapter or an event. |
| When does SWE5006 feature work stop? | Open. It gates the per-service Flyway baselines and any code relocation (sections 2.3 and 2.6). |
| AWS account budget or credits under the organisation SCP | Confirm with the account owner before the first `runtime` apply. |
| Proposal reviewers challenge the three-service scope | The rationale is in [section 2.2](#22-decomposition-decision); record it as a backend ADR. |
| Coverage of the new modules pulls down the SonarCloud gate for SWE5006 PRs | New modules ship with tests in the same PR; the ≥ 80% new-code gate applies to them like any other code. |
