# #456 PR 3: service images, the `distributed` runtime and demo data per database (steps C1 + C2 + C3) — implementation plan

Date: 2026-10-12 · Branch: `feature/microservice-c1c3-20261012` from `dev` at `45324227` (#471 merged) · Issue: #456 (Sprint 4 container #465) · Owner: @l0sgAi · Related: steps C1–C3 of the [microservice split plan](microservice-split-plan-20261012.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md), [SWE5001 AWS plan](../../swe5001/impl-aws-infrastructure.md) sections 6.3–6.5, [A3/A4 plan](service-assemblies-plan-20261012.md)

> **Status (2026-10-12): reviewed (all §9 defaults accepted) and implemented on `feature/microservice-c1c3-20261012`. Results: [day log](log/2026-10-12.md).**

## 1. Goal and acceptance criteria

Ship the three service assemblies from #471 as images and run them on ECS as `topology = "distributed"`. The service split plan's definition of done is three services in steady state, Google sign-in through `identity-svc`, commerce accepting identity's session, and a green smoke suite. The `dev` EC2 monolith, its image and its workflows do not change.

| # | Criterion | Delivered by |
| --- | --- | --- |
| AC1 | `swe5001-service-images.yml` builds and Trivy-scans four ARM64 images (`monolith`, `identity-svc`, `commerce-svc`, `notification-svc`) from one `Dockerfile.ci`; its `services` output lists all four, so `publish` and `release` write `/candidate/<service>` and `/release/<service>` per service | §4 C1-1…C1-5, V3, V6 |
| AC2 | `terraform test` covers `topology = "distributed"`: three services, three databases, ALB priorities 10/20/30 with the `/internal/*` block at 5, Service Connect DNS `identity-svc`, `IDENTITY_BASE_URL` on commerce only, Google secrets on identity only, Stripe secrets on commerce only, `classpath:db/swe5001` kept wherever `SPRING_FLYWAY_LOCATIONS` is set, scheduling on commerce only | §4 C2-1…C2-3, V1 |
| AC3 | `lib.sh` returns the three service names for `distributed`; `env-up.sh` and the two workflows accept it; checkov, Trivy config, shellcheck and actionlint clean | §4 C2-4…C2-7, V1–V2 |
| AC4 | Catalogue seed only in the commerce database; a documented, scripted admin grant against the identity database; a documented way to make one listing startable | §4 C3-1…C3-4, V5 |
| AC5 | `dev` unchanged: `Dockerfile.ci` with no build argument produces the same image as today; `ci-backend` green; `dev` health `UP` after merge | V4, V7 |
| AC6 | One manual session reaches steady state for all three services, Google sign-in works through `identity-svc`, an admin request on `commerce-svc` succeeds after the grant and a second sign-in, smoke green, clean `env-down.sh` | Operator session, needs B1 #467 and B3 #468 (§6) |

Mapped to #456: AC1 is scope item 2; AC2 + AC3 are items 1, 3 (without events), 4 and 5; AC4 is item 6; AC6 is the session criteria. The "two commerce tasks, one lease holder" criterion needs #457's scaling (or a manual desired count in the session) and is recorded in the same session.

Non-goals: events (`BLOCBUY_EVENTS_TRANSPORT`, topic ARN, queue URL and the task-role policies, all from track D: #444, #445, #447); scaling policies and the dashboard (#457, see §9 Q3); session roles (B1 #467), the nickname port (B2 #469), the remote identity adapter (B3 #468); a migration baseline per service.

## 2. Baseline (read on 2026-10-12 from `45324227`)

| Area | Today | Gap |
| --- | --- | --- |
| Images | `swe5001-service-images.yml` matrix has one entry, `monolith`, `Dockerfile.ci` copying `blocbuy-bootstrap/target/blocbuy-backend-exec.jar`; output `services: monolith` is a literal | Three entries, a JAR per entry, output from the matrix |
| Build context | `backend/.dockerignore` excludes `**/target/` except the exec JAR | `services/*/target/<service>.jar` must be admitted, nothing else |
| Image CI | The reusable workflow runs only from `swe5001-release-gate.yml` (PRs into `prod`) and `swe5001-deploy.yml` (push to `prod`) | A matrix change would first run at the 10-19 promotion |
| ECR | `foundation/ecr.tf` default `images` already has `monolith`, `identity-svc`, `commerce-svc`, `notification-svc` | None: the 2026-10-05 operator evidence in the handoff lists the four repositories |
| Runtime | `services.tf` maps `none` and `monolith`; `variables.tf` validates `none \| monolith` | `distributed` map and validation |
| Golden path | `ecs-service` already has Service Connect (port name `http`, DNS = service name), a target group, a listener rule with the origin header and up to four paths, a circuit breaker and the read-only root with `/tmp` | None |
| Cluster | `ecs-cluster` creates namespace `blocbuy-swe5001.local` and one execution role reading `/blocbuy/swe5001/<scope>/*` for every scope in `parameter_scopes` | None |
| Data stores | `rds-postgres` creates an owner and app password per entry in `local.databases`; `db-init` creates every database in `DATABASES` | None: three map entries give three databases automatically |
| Scripts | `services_for_topology` knows `none \| monolith`; `env-up.sh` defaults to `monolith`; `swe5001-env.yml` has no topology input; `swe5001-deploy.yml` sets none | `distributed` everywhere |
| Smoke | Health and products (commerce), `auth/me` 401 (identity), `/internal/...` not served | Covers identity and commerce; notification has no endpoint until #215 (§3.6) |
| Seed | `db/seed/R__demo_catalog_seed.sql`: 10 products, 6 deals, policies, no users, no team terms | Commerce only; START needs team terms |
| Admin grant | `scripts/grant-dev-admin.sql` (psql, idempotent), run by hand on `dev` through an SSM tunnel | No path into the private RDS on ECS |

Answered from code (the open questions of the last update): Service Connect exists; `db-init` creates the three databases and roles on every `env-up.sh`; ECR repositories exist in the foundation configuration; `GOOGLE_REDIRECT_URI` stays `{frontend_url}/api/v1/auth/oauth2/google/callback`, which the ALB now routes to `identity-svc`, so the Google console needs no change.

## 3. Design decisions

### 3.1 One Dockerfile, one argument

`Dockerfile.ci` gains `ARG JAR=blocbuy-bootstrap/target/blocbuy-backend-exec.jar` and copies `${JAR}`. Every existing caller (`ci-backend` `docker`, `package-dev`, the monolith matrix entry) passes nothing and gets the same image. Service entries pass `services/<service>/target/<service>.jar`. The runtime user, `/tmp` volume, port and entrypoint are shared, so all four images behave the same under the read-only root. `backend/Dockerfile` (local Compose) is untouched.

`.dockerignore` admits exactly `services/*/target/*-svc.jar`; the plain library JAR stays excluded.

### 3.2 Image matrix

| `service` | Maven module (`-pl … -am`) | `jar` |
| --- | --- | --- |
| `monolith` | `blocbuy-bootstrap` | `blocbuy-bootstrap/target/blocbuy-backend-exec.jar` |
| `identity-svc` | `services/identity-svc` | `services/identity-svc/target/identity-svc.jar` |
| `commerce-svc` | `services/commerce-svc` | `services/commerce-svc/target/commerce-svc.jar` |
| `notification-svc` | `services/notification-svc` | `services/notification-svc/target/notification-svc.jar` |

Each job packages only its module and dependencies, runs `assert-boot-jar.sh` on its JAR, builds with `build-args: JAR=…` and scans with the same Trivy gate. The job output `services` becomes `monolith identity-svc commerce-svc notification-svc`; the service name equals the ECR repository suffix and the runtime map key, which `publish` and `env-up.sh` already rely on.

The monolith stays in the matrix as the fallback image (split plan, 10-18 fallback; §9 Q1).

The workflow also gets a `pull_request` trigger into `dev` limited to its own file, `backend/Dockerfile.ci` and `backend/.dockerignore`, with `upload-images` off. The export and upload steps already skip `pull_request`. This PR then builds and scans all four images before merge instead of at the promotion (§9 Q5).

### 3.3 The `distributed` map

| Key (= ECS service, ECR repository, Service Connect DNS) | `database` | Priority | Paths | Own environment | Secrets |
| --- | --- | --- | --- | --- | --- |
| `identity-svc` | `identity` | 10 | `/api/v1/auth/*`, `/api/v1/users/*` | `SPRING_PROFILES_ACTIVE=ec2,identity,aws`, `GOOGLE_REDIRECT_URI`, `BLOCBUY_SCHEDULING_ENABLED=false` | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` |
| `notification-svc` | `notification` | 20 | `/api/v1/notifications*` | `SPRING_PROFILES_ACTIVE=ec2,notification,aws`, `BLOCBUY_SCHEDULING_ENABLED=false` | none |
| `commerce-svc` | `commerce` | 30 | `/api/*` | `SPRING_PROFILES_ACTIVE=ec2,commerce,aws`, `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/swe5001,classpath:db/seed`, `IDENTITY_BASE_URL=http://identity-svc:8589`, `BLOCBUY_SCHEDULING_ENABLED=true`, `BLOCBUY_STRIPE_CHECKOUT_ENABLED=false`, `TYPESAFE_ENABLED=false` | `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` |

- Priorities and paths follow AWS plan 6.3. The `/internal/*` and `/api/internal/*` block stays at 5, before all three.
- `/api/actuator/health` through CloudFront reaches commerce (the catch-all); each target group checks its own task's health.
- The ec2 profile is first because it is the only profile that maps `DB_*` and `REDIS_*` (A4 handoff row); the service profile sets the topology, scans and Flyway placeholders; `aws` switches Redis TLS and JSON logs.
- Only commerce overrides `SPRING_FLYWAY_LOCATIONS`, and the override keeps `classpath:db/swe5001` (ADR 0008 amendment). A `terraform test` assertion guards it.
- `IDENTITY_BASE_URL` is the name #468 specifies. The property it binds to is #468's choice; this PR only provides the variable.
- Scheduling is on for commerce only (#456 item 4). Identity and notification do not scan `bootstrap.scheduling`, so `false` there is explicit rather than load-bearing.
- `task_policy_json = null` for all three until track D adds `sns:Publish` and SQS permissions.
- Desired count 1 each; `services_enabled` keeps its meaning. Scaling bounds are #457's (§9 Q3).
- Target group names are `blocbuy-swe5001-<key>`. `notification-svc` makes 32 characters, the AWS maximum; a test asserts the bound so a longer key fails offline.

The `monolith` map is unchanged and stays selectable.

### 3.4 Topology selection

- `variables.tf` and `services_for_topology` accept `distributed`.
- `swe5001-env.yml` gets a `topology` input (`distributed`, `monolith`, `none`; default `distributed`) passed as `SWE5001_TOPOLOGY`.
- `swe5001-deploy.yml` sets `SWE5001_TOPOLOGY: ${{ vars.SWE5001_TOPOLOGY || 'distributed' }}` on `up`. The fallback to the monolith is then a repository variable the operator sets, not a PR into `prod` (§9 Q2).
- `env-up.sh` keeps `monolith` as its own default, so the README commands and any old checkout behave as before; the runbook and the workflows pass `distributed` explicitly.
- `env-down.sh` and `env-status.sh` need no topology: down destroys whatever is in state with `none`, status lists by tag.

### 3.5 Demo data per database (C3)

- **Catalogue:** commerce only, through `db/seed` in its Flyway locations (§3.3). Identity and notification load `db/migration` and `db/swe5001` only.
- **Admin grant:** new `infra/swe5001/scripts/grant-admin.sh <google-email>`. It runs the existing `db-init` task definition once with container overrides: `PGDATABASE=identity` and a command that feeds `scripts/grant-dev-admin.sql` to `psql` with `-v admin_email=…`. It reuses the master credentials, network and log group the task already has, adds no Terraform, waits for the task and fails on a non-zero exit. The SQL is unchanged; its header gains the ECS usage line. The override size (about 2 KB) is well under the 8 KiB limit. The email appears in the `db-init` log group (7-day retention, operator-only); the script prints the `granted` / `already_admin` result line from that log.
- **Second sign-in:** after the grant the admin signs in again, so #467 writes the role into the session.
- **Startable listing:** run the existing `scripts/seed-dev-demo-listings.sh` with `BLOCBUY_BASE_URL=<SWE5001 URL>/api/v1` and the admin's token. It sets team terms and a policy in force through the audited admin API, and is idempotent. This uses no new seed SQL and leaves the SWE5006 `local` seed alone (§9 Q4).
- The runbook records the order: env-up, first sign-in, grant, second sign-in, listing seed, START check.

### 3.6 Smoke suite

Unchanged. It already exercises commerce (health, products non-empty, which also proves the seed reached the commerce database), identity (`auth/me` 401 through the identity rule) and the `/internal` block. Notification has no endpoint until #215, so a request to it answers 404 before Sa-Token runs and cannot be told apart from a missing route. Its health is proven by `aws ecs wait services-stable` and its target group. A `notifications` 401 check is added when #215 lands (noted in the handoff).

## 4. Changes (one PR, about 22 files, est. 700 lines)

| Id | File | Change |
| --- | --- | --- |
| C1-1 | `backend/Dockerfile.ci` | `ARG JAR` with the current path as default |
| C1-2 | `backend/.dockerignore` | Admit `services/*/target/*-svc.jar` only |
| C1-3 | `.github/workflows/swe5001-service-images.yml` | Four-entry matrix, `-pl <module> -am`, build argument, output from the matrix, `pull_request` trigger on its own paths |
| C1-4 | `.github/workflows/swe5001-deploy.yml` | `SWE5001_TOPOLOGY` on `up`; header comment |
| C1-5 | `.github/workflows/swe5001-env.yml` | `topology` input |
| C2-1 | `infra/swe5001/runtime/services.tf` | `distributed` map (§3.3) |
| C2-2 | `infra/swe5001/runtime/variables.tf` | Validation and description |
| C2-3 | `infra/swe5001/runtime/tests/plan.tftest.hcl` | `distributed_topology` run (AC2 assertions) and a missing-digest failure |
| C2-4 | `infra/swe5001/scripts/lib.sh` | `services_for_topology distributed` |
| C2-5 | `infra/swe5001/scripts/env-up.sh` | Usage text |
| C2-6 | `infra/swe5001/scripts/test-lib.sh` (new) | Offline test of `services_for_topology` |
| C2-7 | `.github/workflows/infra-swe5001.yml` | Run `test-lib.sh` next to `test-env-status.sh` |
| C3-1 | `infra/swe5001/scripts/grant-admin.sh` (new) | §3.5 |
| C3-2 | `scripts/grant-dev-admin.sql` | Usage header for ECS |
| C3-3 | `docs/swe5001/runbook-environment-session.md` | Distributed session: hand-pushed images, env-up, grant, listing seed, checks, down |
| C3-4 | `infra/swe5001/README.md` | Topologies, per-service hand push loop, `grant-admin.sh`, workflow table |
| D-1 | `docs/swe5001/HANDOFF.md` | C1–C3 rows; notification smoke check after #215 |
| D-2 | `docs/swe5001/impl-aws-infrastructure.md` | Dated note: priorities, environment, deferred scaling |
| D-3 | `docs/agile/sprint-04/microservice-split-plan-20261012.md` | A4 row: `afterMigrate` callback, not `R__swe5001_grants.sql` |
| D-4 | this plan | Status |
| D-5 | `docs/agile/sprint-04/log/2026-10-1x.md`, `log.md` | Day log entry |

## 5. Verification

| # | Check | Where |
| --- | --- | --- |
| V1 | `terraform fmt -check`, `validate` and `test` for `runtime` (existing four runs plus the new ones); checkov and Trivy config on `runtime` as `infra-swe5001.yml` runs them | Local, offline |
| V2 | `shellcheck` on the scripts, `test-env-status.sh`, `test-lib.sh`, `actionlint` on the four workflows | Local |
| V3 | `docker buildx build --platform linux/arm64` of all four images from `Dockerfile.ci` (native on this Apple silicon host), `assert-boot-jar.sh` on each JAR, Trivy image scan with the CI flags | Local |
| V4 | `Dockerfile.ci` without the argument: same `app.jar` digest as `blocbuy-backend-exec.jar`; `ci-backend` `docker` job unchanged | Local, then PR CI |
| V5 | `identity-svc` image runs read-only with `/tmp` tmpfs, profiles `ec2,identity,aws`, against PostgreSQL prepared by `db-init/init.sh` and Redis: health `UP`. `commerce-svc` image with the §3.3 environment: Flyway applies `db/seed` and the grants, then startup stops at the missing `RoleRepository`/`UserAppService` beans (expected until B1/B3). `grant-dev-admin.sql` against that identity database as in `grant-admin.sh` | Local Docker |
| V6 | PR CI: `swe5001-service-images` (four ARM64 builds and scans through the new trigger), `infra-swe5001`, `ci-backend` | GitHub |
| V7 | `dev` health `UP` after merge | @l0sgAi |
| V8 | AC6 session | Operator, after B1 and B3 |

Not verifiable by me: anything in AWS (plan against real state, images in ECR, the session, Google sign-in, scheduler leases with two tasks).

## 6. Dependencies

| Needed for | Dependency | Owner, date |
| --- | --- | --- |
| Writing and merging this PR | None | — |
| `commerce-svc` and `notification-svc` to start | B1 #467 (`RoleRepository` replaced by session roles outside identity) | @PPsyrius, 10-15 |
| `commerce-svc` to start and call identity | B3 #468 (`RemoteUserAppService`, binds `IDENTITY_BASE_URL`) | @EricWang1358, 10-16, go/no-go 10-15 |
| Admin attention page under grants | B2 #469 | @Yorick-Yu, 10-15 |
| AC6 session | Operator: push the three images by hand, write `/blocbuy/swe5001/candidate/<service>`, run `SWE5001_TOPOLOGY=distributed env-up.sh --candidate`, `grant-admin.sh`, the listing seed and `env-down.sh` from a `dev` checkout; confirm foundation's ECR repositories exist | Operator, 10-16 |
| Scaling, two commerce tasks | #457 builds on this map | @PPsyrius, 10-16 |

## 7. Risks and rollback

| Risk | Mitigation |
| --- | --- |
| B1 or B3 late: the session cannot reach steady state | Code merges independently. If B3 is not green by 10-18, the split plan's fallback applies: set `SWE5001_TOPOLOGY=monolith` (repository variable or env) and present identity as a limitation |
| A matrix or Dockerfile mistake breaks the promotion | PR trigger builds all four images here (§3.2); the monolith default path is byte-checked (V4) |
| `SPRING_FLYWAY_LOCATIONS` drops `db/swe5001` and the grants silently stop | `terraform test` assertion; ADR 0008 obligation |
| Commerce scheduling on two tasks | `LeasedJobRunner` leases per job; checked in the session when #457 sets two tasks |
| RDS connections: three pools (four with two commerce tasks) on one small instance | Default Hikari pool of 10 per task, about 40 connections; record `DatabaseConnections` in the session |
| Cost: three tasks instead of one | Sessions stay short and the nightly down still runs; three tasks of 0.5 vCPU / 1 GiB, mostly Spot |
| Target group name limit | Asserted offline (§3.3) |

Rollback: revert the PR. `dev` never reads any of it; `prod` keeps the `monolith` map and image.

## 8. Evidence

Day log entry with V1–V6 results, the plan status, ADR 0008 untouched (no new decision beyond its text) unless §9 changes it, handoff rows C1–C3, and the session run links in the log of the session day.

## 9. Questions for the reviewer

| # | Question | Default |
| --- | --- | --- |
| Q1 | Keep the monolith image in the matrix (one extra build per promotion) as the fallback? | Yes |
| Q2 | Promotion topology from `vars.SWE5001_TOPOLOGY`, defaulting to `distributed`, so the fallback needs no PR into `prod`? | Yes (the operator sets the variable only if needed) |
| Q3 | Identity scaling 1/1/3 on CPU 50% (#456 item 1): leave it to #457, which adds scaling to the `ecs-service` module once for all services, to avoid two PRs editing the module? | Leave to #457 and tell @PPsyrius |
| Q4 | Startable listing through `seed-dev-demo-listings.sh` against the session (no new seed SQL)? | Yes |
| Q5 | `pull_request` trigger on `swe5001-service-images.yml` for its own paths (about 10 minutes of CI when those files change)? | Yes |
| Q6 | Admin grant as a script over the `db-init` task definition (no new Terraform), rather than ECS Exec or a second task definition? | Script |
