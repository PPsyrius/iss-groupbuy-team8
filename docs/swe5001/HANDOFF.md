# SWE5001 Deployment Handoff

Current state of the SWE5001 distributed deployment (Amazon ECS, released by `dev` → `prod` promotion). Read this before picking up any item of [#376](https://github.com/PPsyrius/iss-groupbuy-team8/issues/376). Update it in the same PR whenever a step lands.

Last updated: 2026-10-12. Phase A (the monolith on ECS) is done; Phase B (the three-service split) starts in Sprint 4 under the [split execution plan](../agile/sprint-04/microservice-split-plan-20261012.md).

## Where to read the design

| Topic | Document |
| --- | --- |
| Release flow, ephemeral runtime, validation split | [Refactoring plan](microservice-refactoring-plan.md), sections 3.2–3.4 |
| Stacks, network, ECS, data stores, scripts, cost | [AWS infrastructure plan](impl-aws-infrastructure.md) |
| Environments, `prod` ruleset, workflows | [GitHub configuration plan](impl-github-configuration.md) |
| Smoke suite, drills, presentation checklist | [Verification plan](impl-verification-observability.md) |
| Start, check and destroy a session (demo, presentation) | [Environment session runbook](runbook-environment-session.md) |
| Operator commands (bootstrap, offline checks, `runtime` inputs) | [`infra/swe5001/README.md`](../../infra/swe5001/README.md) |
| Microservice split: steps, PR order, owners, `dev` guards | [Split execution plan](../agile/sprint-04/microservice-split-plan-20261012.md) (2026-10-12) |
| Backend work packages WP-B0–B9, findings F1–F7 | [Backend implementation plan](impl-backend-architecture.md) |
| Sprint 4 scope, priorities and cuts | [Sprint 4 plan](../agile/sprint-04/plan.md) §4, §6 |

## Progress against the #376 PR breakdown

| # | Item | State |
| --- | --- | --- |
| 1 | Plan docs | Merged in [#377](https://github.com/PPsyrius/iss-groupbuy-team8/pull/377) |
| 2 | `foundation` stack, plan-only `infra-swe5001.yml` | Operator applied on 2026-10-05: **23 creates, zero drift**; optional-budget change merged in #402 at `605c8e27`; operator-initiated GitHub OIDC plan succeeded and reported `No changes.` on 2026-10-06 |
| 3 | `runtime` part 1: network, security groups, ECS cluster, `ecs-service` module, ALB | Merged in #377 (from #379); applied and destroyed in every session since 2026-10-06 (first: promotion [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078)) |
| 4 | `runtime` part 2: RDS, `db-init`, Valkey | Merged in #377 (from #379); applied and destroyed in every session since 2026-10-06 (first: [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078)) |
| 5 | `edge` stack: CloudFront and SPA bucket | Merged in [#380](https://github.com/PPsyrius/iss-groupbuy-team8/pull/380); operator applied from `605c8e27` on 2026-10-06: **9 creates, zero drift**; edge GitHub variables configured |
| 6 | `env-up.sh`, `env-down.sh`, `env-status.sh`, `lib.sh`; monolith as one ECS service; first up/down proof | Merged in #380 and fixes #405–#417; **done**: promotion #418 → [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) green end to end (2026-10-06); manual `swe5001-env` up [37569411094](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37569411094) and down [37571726284](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37571726284) green (2026-10-07) |
| 7 | CI: `swe5001-service-images.yml`, `swe5001-release-gate.yml`, `swe5001-deploy.yml`, `swe5001-env.yml`; create `prod` and its ruleset | Merged in [#384](https://github.com/PPsyrius/iss-groupbuy-team8/pull/384); foundation OIDC plan passed; operator created `prod` at `605c8e27` and active `prod-release` ruleset `24551375`; release gate and promotion exercised by #418; nightly `down` green 2026-10-09 to 2026-10-11 ([37983414307](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37983414307), [38078270152](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38078270152), [38167284298](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38167284298)) |
| — | Sprint 3 promotion, Fri 2026-10-09 | **Not run**; `prod` is still at #418 (`6c7a99ab`). The next promotion is the Sprint 4 one on Mon 2026-10-19 |
| 8 | Messaging: SNS topic, notification and audit queues, DLQ, DLQ alarm | [#447](https://github.com/PPsyrius/iss-groupbuy-team8/issues/447); tier 2 behind the split; planned |
| 9 | Service split: `identity-svc`, `commerce-svc`, `notification-svc` | #455, #456, #467, #468, #469; see Phase B progress below |
| 10 | Scaling and dashboard (alarms reduced to the DLQ alarm on 2026-10-11) | [#457](https://github.com/PPsyrius/iss-groupbuy-team8/issues/457); planned |

## Phase B progress (microservice split)

Steps and dependencies: [split execution plan](../agile/sprint-04/microservice-split-plan-20261012.md) §2–§3. Update the state in the same PR that lands a step.

| Step | Content | Issue | Owner | Due | State |
| --- | --- | --- | --- | --- | --- |
| A1 | `exec` classifier; consumers on `blocbuy-backend-exec.jar`; boot-JAR guard | #455 | @l0sgAi | 10-12 | Merged in #470 (`c8f529ac`): implemented per the [exec-jar-classifier plan](../agile/sprint-04/exec-jar-classifier-plan-20261012.md), local V1–V5 green ([log](../agile/sprint-04/log/2026-10-12.md)); `dev` health `UP` after merge |
| A2 | ADR 0008; ArchUnit guardrails; `RoleCode` to a shared package | #455 | @l0sgAi | 10-13 | Merged in #470: [backend ADR 0008](../../backend/docs/adr/0008-dual-deployment-topology.md) (Accepted in #471), nine `ArchitectureTest` rules, `InternalPathsContractTest`, `RoleCode` in `com.blocbuy.common.model` |
| A3 | Three service modules; `MyBatisConfig` for the monolith only; thin starter | #455 | @l0sgAi | 10-14 | Merged in #471 (`45324227`): [service-assemblies plan](../agile/sprint-04/service-assemblies-plan-20261012.md); `blocbuy-platform` and `services/*`; only `identity-svc` boots on its own until B1 and B3 |
| A4 | Per-service roles and grants (service profiles only) | #455 | @l0sgAi | 10-14 | Merged in #471: `afterMigrate` grants callback, `ServiceGrantsIT`; C2 sets `SPRING_PROFILES_ACTIVE=ec2,<service>,aws` and keeps `classpath:db/swe5001` in the one `SPRING_FLYWAY_LOCATIONS` it sets (commerce; asserted by `terraform test`) |
| B1 | Session roles outside identity | #467 | @PPsyrius | 10-15 | Not started |
| B2 | Nickname port; attention query without `sys_user` | #469 | @Yorick-Yu | 10-15 | Not started |
| B3 | Remote identity check, fail closed | #468 | @EricWang1358 | 10-16 | Not started; go/no-go 10-15 |
| C1 | Image matrix for three services; per-service digests | #456 | @l0sgAi | 10-15 | In review: [service-runtime plan](../agile/sprint-04/service-runtime-plan-20261012.md); four images (monolith kept as fallback) from one `Dockerfile.ci` with `ARG JAR`; the image workflow also runs on PRs into `dev` that change its inputs |
| C2 | `distributed` topology in `runtime`, ALB rules, Service Connect, scripts | #456 | @l0sgAi | 10-16 | In review, same PR as C1: `distributed` map (priorities 10/20/30), `terraform test` run, `lib.sh` and both workflows accept it (promotion default `distributed`, fallback via `vars.SWE5001_TOPOLOGY=monolith`); identity scaling left to C4. The session needs B1 and B3 |
| C3 | Demo data per database; admin grant in identity | #456 | @l0sgAi | 10-16 | In review, same PR as C1: seed only in `commerce`; `grant-admin.sh` runs `grant-dev-admin.sql` as the `db-init` task; listings made startable with `seed-dev-demo-listings.sh` ([runbook](runbook-environment-session.md)); smoke gets a notification check once #215 adds an endpoint |
| C4 | Commerce scaling (min 2), three-row dashboard; identity 1/1/3 on CPU 50% (moved from #456) | #457 | @PPsyrius | 10-16 | Not started |
| D | Outbox (switch default off), relay, consumer, SNS/SQS | #443–#448 | several | 10-16 | ADR 0007 merged as Proposed (#466); rest not started |

Operator-provided CloudShell evidence on 2026-10-05 confirms foundation exists in account `402178232802`: four ECR repositories, three GitHub roles and their policies, lifecycle policies and workload boundary; no budget was created. The state bucket defaults to the enabled AWS-managed S3 KMS key. The agent created and read back both GitHub environments (plan unrestricted, demo only branch `prod`, no reviewers), three role ARN variables and repository variables `SWE5001_ECR_REPOSITORY_PREFIX=swe5001`, `ENABLE_SWE5001_DEPLOY=false`, `ENABLE_SWE5001_NIGHTLY_DOWN=true`. `SWE5001_TF_STATE_BUCKET` and edge variables remain unset; `prod` and its ruleset remain pending. AWS verification and mutations were performed by the operator, not the agent.

2026-10-06 completion of #397 configuration: the operator set the state-bucket variable and reran [infra-swe5001 run 37328651064](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37328651064); all static/script jobs and the foundation plan passed, with [No changes](https://github.com/PPsyrius/iss-groupbuy-team8/pull/402#issuecomment-6007800394). The operator created `prod` at `605c8e27ae5d68ab54c7770dbe9231964b8053ec` and the active ruleset; agent readback confirmed merge-only PRs, one CODEOWNERS approval, resolved threads, required `promotion-source`/`service-images`/`infra-plan`, deletion/force-push protection and no bypass actors. The operator seeded the origin secret and applied edge (9 creates, detailed-exitcode plan 0); agent configured and read back `SWE5001_FRONTEND_URL=https://d3lvq02o2e7tcn.cloudfront.net`, `SWE5001_SPA_BUCKET=blocbuy-swe5001-spa-402178232802`, `SWE5001_CLOUDFRONT_DIST_ID=E3N39GHVFV6CT3`. All requested GitHub settings are present; deployment remains disabled. The SPA bucket is initially empty and the API origin is the absent-runtime placeholder; this is bootstrap completion, not a running application.

## Next actions

1. Merge the C1–C3 PR after review; check `dev` health after merge (it builds nothing new for `dev`).
2. Track B (split plan §2): B1 and B2 by 10-15; B3 by 10-16 with the identity go/no-go at the 10-15 mid-sprint check. B1 and B3 remove the `RoleRepository` and `UserAppService` mocks from the commerce and notification ITs.
3. C4 by 10-16. Before the promotion, `prod` has no distributed images: the operator pushes the three images by hand, writes `/blocbuy/swe5001/candidate/<service>` ([README](../../infra/swe5001/README.md), "Environment sessions") and runs `SWE5001_TOPOLOGY=distributed env-up.sh --candidate`, then `env-down.sh`. Record timings and exit codes.
4. Sprint 4 promotion on Mon 2026-10-19 (`dev` → `prod`, merge commit), then the evidence session (V2–V5, V7, identity outage) and the fallback recording; rehearsal 10-20; presentation 10-21 16:15 SGT (`env-up.sh` before 12:00, `env-down.sh` and `env-status.sh` after).
5. Phase A leftovers, operator with admin credentials (unchanged from 2026-10-07, still open):
   1. Skip billing for the demo. If opting in later, ask the organisation management/payer account to activate `Course` (up to 24 hours), seed budget recipients, and set `enable_budget = true` in `envs/demo.tfvars`.
   2. Complete remaining IAM checks: origin-secret denial and apply-role protection of existing SWE5006 resources. The organization-aware simulation denied foundation access, but the actual GitHub OIDC plan succeeded; do not treat the simulation result as proof of an effective foundation-access denial.
   3. The `force_failure` promotion run and the `promotion-source` negative check were dropped on 2026-10-11 (Sprint 4 plan §4 item 8); describe them in the report instead.

## Decisions already made

- 2026-10-05 (#397): budget creation is opt-in (`enable_budget = false` by default), because the operator's linked account cannot activate cost-allocation tags. Foundation bootstrap skips both AWS Budgets and the budget-recipient SSM read; deployment does not depend on billing access. No budget alerts are provided while disabled; ephemeral runtime teardown remains the operational cost control. AWS commands are executed by the human operator only.
- ECS runs only from `prod`, once per two-week sprint; every run creates `runtime`, validates it and destroys it (`if: always()`).
- Secrets: operator-seeded SSM values are never Terraform inputs. Database passwords are ephemeral and written through write-only arguments, so they never reach state. `ORIGIN_SECRET` and the Valkey AUTH token do reach the `runtime` state (no write-only path), accepted because they exist only while `runtime` does.
- Apply role guardrails: managed policies per service; IAM only under `/swe5001/` with the mandatory workload permissions boundary; tag-conditioned denies for non-SWE5001 EC2, ELB, CloudFront, RDS and ElastiCache resources (including snapshots and retained automated backups); name-based denies for log groups outside `/blocbuy/swe5001/*` and SNS topics or SQS queues not named `blocbuy-swe5001-*`.
- `runtime` selects its services by `topology` (`runtime/services.tf`): `none`, or `monolith` (strangler step 0: the SWE5006 image as one service on `/api/*`, own database `monolith`, profile `ec2`, demo seed), kept as the fallback; or `distributed` (#456): `identity-svc`, `commerce-svc`, `notification-svc`, each with its own database, profiles `ec2,<service>,aws`, the demo seed in `commerce` only.
- `edge` is created once by an operator with a placeholder origin and no runtime state read; `env-up.sh` explicitly passes the new ALB DNS name when the `prod`-only apply role updates the distribution tagged `Course=swe5001`. CloudFront origin access controls and response headers policies cannot be scoped by name or tag.
- A promotion run is `images` → `publish` → `up` (`env-up.sh --candidate`, which is the deployment, because `runtime` starts from zero) → `release` (the SPA, then the release digests, only after a green smoke suite) → `down` and `evidence` (both always). No separate rolling deploy; the V6 rollback drill runs in a manual session. The required `prod` checks are fixed-name jobs in `swe5001-release-gate.yml` that summarise the called workflows.
- `env-up.sh` never prints the `edge` plan: its origin diff carries `ORIGIN_SECRET` in clear, and workflow logs and artifacts are readable by every collaborator.
- The PR-assumable plan role can read the foundation state only, not the secret-bearing runtime state; explicit S3 and SSM denies retain that boundary if future grants are added. Runtime plans must be made by an operator or a reviewed `prod`-only workflow, so there is no pre-merge account-backed runtime plan today. The `edge` state also holds `ORIGIN_SECRET`, so the plan role cannot read it either.
- 2026-10-11 (Sprint 4 plan §4): the distributed shape is the proposal's three services (`identity-svc` reinstated as a hard requirement after a same-day deferral); the split ranks above the notification chain; `dev` (single EC2 host) serves SWE5006 and `prod` (ECS) serves SWE5001, so no EC2 production is built; notifications serve SWE5001 only (no broker or outbox writer on `dev`).
- 2026-10-11, grade-driven cuts: no V6 broken-image drill and no forced-failure promotion run; no `X-Internal-Token` (network isolation only for `/internal/**`); dashboard reduced to Edge, Services and Messaging with only the DLQ alarm; `CloudWatchFullAccessV2` is not fenced; RDS Multi-AZ, Valkey replicas and a second NAT are reported as cost-driven limitations; `commerce-svc` runs at least two tasks across both AZs in demo sessions.
- 2026-10-12: `blocbuy-bootstrap` is repackaged with the `exec` classifier so the plain JAR can be a dependency of the service modules (WP-B2, split plan step A1).

## Known gaps and risks

| Gap | When it closes |
| --- | --- |
| No distributed image, runtime map or session exists yet; `prod` is at #418 | Split plan steps A3, C1–C3; first manual distributed session by 10-16; promotion 10-19 |
| Payment webhooks call `identity-svc` synchronously; an identity outage fails checkout closed | By design (#468): 500 and Stripe redelivery; demonstrated by the identity outage drill (#459); reported as an architectural limitation |
| `/internal/**` relies on network isolation only (no service token) | Accepted 2026-10-11; report as a limitation |
| RDS single-AZ, single-node Valkey and one fck-nat instance | Accepted for cost on 2026-10-11; report under availability |
| Foundation OIDC/state/KMS access verified by a successful account-backed plan; origin-secret denial and apply-role guardrails still lack dedicated live evidence | Operator-owned IAM checks and first runtime up/down proof |
| The plan role's KMS decrypt is limited to calls via S3 but currently accepts any key in this account; its S3 GetObject deny limits readable data to foundation state | Check the state bucket's encryption key without printing state and scope the key ARN before enabling account-backed PR plans, then test the foundation plan |
| The apply role now has `kms:Decrypt` and `kms:GenerateDataKey` through S3 for course state keys, but no live KMS authorization has been checked | Simulate access to the actual state key and perform the first private runtime apply/destroy; tighten the key ARN after reading bucket encryption metadata |
| The apply role can update only the tagged distribution; whether that covers every call an `edge` re-apply makes is unproven | Closed: `env-up.sh` re-applied `edge` with the new ALB origin in [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) |
| CloudFront placeholder origin `runtime-absent.blocbuy-swe5001.invalid` accepted during initial edge creation | Verified by operator edge apply and zero-drift plan on 2026-10-06; API remains unavailable until runtime exists |
| `env-status.sh` may list ARNs of deleted resources from the tagging API after a clean destroy. Seen twice on 2026-10-06: EC2 resources, the `INACTIVE` cluster, its service and stopped tasks, still listed by the tagging API, and a deleted VPC endpoint still returned by `ec2 describe-tags`. Each EC2 and ECS ARN is now checked with its type's own describe call; lookup errors other than not-found still fail closed | First green CI teardown with this check; a type it does not know always counts, so add its describe call if one stays listed |
| The apply role must strip the rules of the VPC default security group; this relies on the provider tagging it before revoking | Closed: the first `runtime` apply in [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) succeeded |
| `db-init`, the monolith on ECS (Flyway as the owner role, Valkey TLS) and the scripts have only been checked offline | Closed for the monolith: [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) ran `db-init`, started the service and passed the smoke suite. The three-service shape is unproven until split plan step C2 |
| `CloudWatchFullAccessV2` can still change alarms and dashboards outside SWE5001 | Accepted on 2026-10-11 (#457 rescoped): only the DLQ alarm and one dashboard are added; report as a limitation |
| Foundation infra workflow passed all static/script checks and the OIDC plan; release gate and runtime/promotion workflows remain unexercised; workflow fixes on `dev` need promotion to affect `prod` runs | First operator-initiated `swe5001-env.yml` run on `prod`, then the Sprint 3 promotion |
| The ARM64 image is built under QEMU without a Maven cache (no cache poisoning of a published image); build time is unmeasured | First release gate run; an arm64 runner is the fallback if it exceeds the 30-minute job timeout |
| `queue: max` retains pending `swe5001-tfstate` runs up to the queue limit, so a queued `down` can execute later instead of being replaced | Check for and cancel stale queued `down` runs before starting a new manual session; wait until no promotion is running |
| NFR-51 (AWS isolation) is partially verified: selected S3 identity-policy checks and actual foundation OIDC plan passed; SSM denial and apply-role guardrails remain unverified | Remaining operator IAM checks and runtime proof |

## Verification commands

Offline, no AWS credentials:

```bash
terraform fmt -check -recursive infra/swe5001
terraform -chdir=infra/swe5001/runtime init -backend=false
terraform -chdir=infra/swe5001/runtime test
terraform -chdir=infra/swe5001/edge init -backend=false
terraform -chdir=infra/swe5001/edge test
shellcheck -x infra/swe5001/scripts/*.sh perf/smoke/smoke.sh
bash infra/swe5001/scripts/test-env-status.sh
checkov -d infra/swe5001 --framework terraform --quiet --compact
trivy config --severity HIGH,CRITICAL --exit-code 1 infra/swe5001
```

Prior local results (2026-10-04): all three stacks validated; edge tests 2 passed and runtime tests 4 passed before the edge/KMS fix. The teardown status fixture now passes seven mocked AWS cases (three API failures, an empty result, only a task definition, a terminated instance and a running instance); Git Bash syntax and `git diff --check` passed. The current changed head still needs fresh Terraform tests, static scans and a live IAM simulation before any AWS apply.

## Delivery checkpoint — 2026-10-12

| Deliverable | Date |
| --- | --- |
| SWE5001 code freeze (split and scaling merged into `dev`) | Fri 2026-10-16 |
| Sprint 4 promotion and evidence session | Mon 2026-10-19 |
| SWE5001 rehearsal | Tue 2026-10-20 |
| SWE5001 presentation | Wed 2026-10-21, 16:15–16:55 SGT (confirmed 2026-10-11) |
| SWE5001 written examination | Tue 2026-11-03, 18:30 |
| SWE5001 final report and final `terraform destroy` | Mon 2026-11-16 |

State on `dev` at `3a0f6908`: Phase A complete as recorded above; Phase B not started apart from ADR 0007 (Proposed) and the A1 plan. The SWE5006 dates in the 2026-10-05 table below are superseded: the SWE5006 presentation is on 2026-10-26 and its report on 2026-11-10 ([Sprint 4 plan §1](../agile/sprint-04/plan.md#1-calendar)).

## Delivery checkpoint — 2026-10-05 (#393)

The Team 8 scheduling notification moves the SWE5001 presentation to **21 October 2026, 16:15–16:55 SGT (UTC+08)**, two days earlier. The submitted proposal is retained as historical evidence; its 23 October presentation and 22 October rehearsal are superseded by the living [timeline](microservice-refactoring-plan.md#4-effort-estimates-and-timeline) and [presentation checklist](impl-verification-observability.md). Confirm a rehearsal slot before 21 October. The existing 20 October promotion target now allows one day for recovery and needs a tested fallback recording.

| Deliverable | Current date | Calendar days from 2026-10-05 | Basis |
| --- | --- | --- | --- |
| SWE5001 presentation | 2026-10-21, 16:15–16:55 SGT | 16 | Team 8 notification supplied on 2026-10-05 |
| SWE5006 presentation | 2026-11-03 / 2026-11-04 | 29 / 30 | [Sprint 3 calendar](../agile/sprint-03/plan.md#1-calendar) |
| SWE5006 final report | 2026-11-10 | 36 | Sprint 3 calendar |
| SWE5001 final report | 2026-11-16 | 42 | [Submitted proposal](project-proposal.typ), milestone table |

Readiness is assessed from `dev` at `a55afa5` and explicit GitHub reads on 2026-10-05. GitHub now lists `swe5001-plan` and `swe5001-demo`; their protection and secrets settings were not verified, and [#397](https://github.com/PPsyrius/iss-groupbuy-team8/issues/397) remains open. `branches/prod` still returns 404; only `dev-require-review` and `main-no-forcepush` rulesets exist; `swe5001-deploy.yml` has no runs. AWS state was not independently checked.

| Area | Implemented or evidenced | Remaining delivery work |
| --- | --- | --- |
| SWE5006 buyer chain | Quote-aware Checkout, orders, team resolution, local refund/recovery tests, invites and admin APIs are on `dev` | [#223](https://github.com/PPsyrius/iss-groupbuy-team8/issues/223) shared-dev expiry and real Sandbox refund; [#224](https://github.com/PPsyrius/iss-groupbuy-team8/issues/224) deployed crash/replay proof; [#229](https://github.com/PPsyrius/iss-groupbuy-team8/issues/229) authenticated buyer acceptance |
| SWE5006 release and security | CI/contract/security gates and governed deployment code; local 100-VU baseline | [#226](https://github.com/PPsyrius/iss-groupbuy-team8/issues/226) production apply/release/rollback; [#227](https://github.com/PPsyrius/iss-groupbuy-team8/issues/227) origin TLS decision; deployed performance remains unmeasured |
| SWE5001 foundation | Terraform foundation/runtime/edge, monolith ECS topology, smoke suite and release/up/down workflows | Operator bootstrap, release configuration, first successful create/validate/destroy, and a failed-promotion teardown proof ([#376](https://github.com/PPsyrius/iss-groupbuy-team8/issues/376)); a planned promotion date is not completion evidence |
| SWE5001 distributed runtime | Plans describe identity, commerce and notification services | Three service assemblies/platform starter, remote identity/RBAC, outbox/relay, SNS/SQS/DLQ, idempotent consumer and per-service data grants are absent from the audited tree; runtime supports only `none`/`monolith` |
| SWE5001 demonstration | `perf/smoke/smoke.sh` exists | Queue/HTTP scaling, dashboard/alarms, k6 and resilience drill tools, live results and fallback recording; no `perf/k6/`, `perf/drills/` or SWE5001 evidence directory exists in the audited tree |
| Architecture discipline | Existing COLA/DDD rules and backend ADR index govern the monolith | Approve the distributed exceptions and synchronize constraints before implementation; the backend plan's proposed ADR 0005/0006 numbers are already occupied by payment records and must not be reused |

The immediate sequence is operator bootstrap and monolith ECS up/down proof (7 October checkpoint), then the 9 October promotion, service/message/scaling implementation and acceptance, and a full rehearsal before 21 October. The 9 October milestone proves strangler step 0; it cannot prove three-service behavior whose identity assembly is planned for 12–18 October. Existing owners keep their assigned work; the new independent frontend issues #392 and #394 do not close this critical path. Notification functionality remains a separate, assigned scope decision under [#215](https://github.com/PPsyrius/iss-groupbuy-team8/issues/215).
