# SWE5001 microservice split — execution plan

Date: 2026-10-12 · Branch: `feature/microservice-20261011` (first PR) · Issues: #455, #456, #467, #468, #469, with #457 and the notification chain #450 · Container: #465 · Coordinator: los gai · Live progress: [SWE5001 handoff](../../swe5001/HANDOFF.md)

> **Status (2026-10-12): draft for review.** This file is the dated plan; the handoff carries the live state of each step.

Design sources, not repeated here: [refactoring plan](../../swe5001/microservice-refactoring-plan.md) (decomposition, integration, physical architecture), [backend implementation plan](../../swe5001/impl-backend-architecture.md) (WP-B0–B9, findings F1–F7, ArchUnit rules), [AWS plan](../../swe5001/impl-aws-infrastructure.md), [verification plan](../../swe5001/impl-verification-observability.md), backend ADR 0007, Sprint 4 plan §4 items 0–4 and §6 items 5–6.

## 1. End state

On `prod` (Amazon ECS, released by `dev` → `prod` promotion) the same code base runs as three services from three images:

| Service | Owns | Public routes | Scaling |
| --- | --- | --- | --- |
| `identity-svc` | `sys_user*`, `sys_role`, `sys_permission`, `rel_*`; Google sign-in; `/internal/users/**` for peers | `/api/v1/auth/*`, `/api/v1/users/*` | 1–3 tasks, CPU 50% |
| `commerce-svc` | catalog, groupbuy, order, payment; scheduling; outbox | every other `/api/*` | 2–6 tasks, ALB requests per target |
| `notification-svc` | `biz_notification`, `sys_consumed_message`; SQS consumer | `/api/v1/notifications*` | 1–4 tasks, queue backlog per task |

Commerce reaches identity only through `UserAppService` → HTTP `/internal/users/**` over Service Connect (fail closed); commerce publishes `TeamSucceeded`, `TeamExpired` and `PaymentSucceeded` through the outbox → SNS → SQS. Sessions are shared through Valkey; roles travel in the session. Each service has its own database and runtime role.

`dev` (single EC2 host) keeps running the monolith with `topology=monolith` (the default), no broker and no outbox writer. Every change below is additive to that shape.

## 2. Steps and pull requests

Every PR stays below about 1,500 changed lines and is reviewed within one working day. Owners and estimates are the Sprint 4 plan's.

### Track A — packaging and assemblies (critical path, @l0sgAi)

| Step | PR content | Issue | Depends on | Due | Detail |
| --- | --- | --- | --- | --- | --- |
| A1 | `exec` classifier on the bootstrap JAR; all consumers moved to `blocbuy-backend-exec.jar`; `assert-boot-jar.sh` guard | #455 | — | 10-12 | [plan](exec-jar-classifier-plan-20261012.md) |
| A2 | Backend ADR 0008 (dual topology, three services, session roles, fail-closed identity call); ArchUnit rules of WP-B1; `RoleCode` moved to a shared package; `/internal/**` absent from the contract | #455 | A1 | 10-13 | WP-B0, WP-B1 |
| A3 | `services/identity-svc`, `commerce-svc`, `notification-svc` modules: entry class, `application-<svc>.yml` with per-service component scan and `@MapperScan`, context-load test each; `MyBatisConfig` only for `topology=monolith`; thin starter pieces (`TopologyProperties`, `application-aws.yml` with the Redis SSL switch and JSON logs, trace propagation) | #455 | A1 | 10-14 | WP-B2, WP-B3 |
| A4 | Per-service runtime roles and `R__swe5001_grants.sql`, loaded only by service profiles; ITs: commerce denied on `sys_user`, notification denied on a commerce table, monolith does not load the grants | #455 | A3 | 10-14 | WP-B8 |

### Track B — identity outside its service (parallel from day 1)

| Step | PR content | Issue | Owner | Depends on | Due |
| --- | --- | --- | --- | --- | --- |
| B1 | Roles and permissions written into the Sa-Token session at login; `SessionStpInterface` for `topology=distributed`; `/internal/**` excluded from the Sa-Token route interceptor | #467 | @PPsyrius | — | 10-15 |
| B2 | `UserAppService.publicNicknames(Set<UUID>)`; `PaymentAttentionMapper` without the `sys_user` join; masking rule moved into identity | #469 | @Yorick-Yu | — | 10-15 |
| B3 | `InternalUserController` (active, public nickname, batch nicknames) in identity; `RemoteUserAppService` in commerce (500 ms / 1,500 ms timeouts, 404/409 → identity errors, timeout/5xx → `IdentityUnavailableException` → 500 and Stripe redelivery) | #468 | @EricWang1358 | B2 port method | 10-16 |

B1–B3 are built in the existing modules and selected by `blocbuy.topology`, so they do not wait for A3; A3 only wires them into the assemblies.

### Track C — images and runtime (@l0sgAi, scaling @PPsyrius)

| Step | PR content | Issue | Depends on | Due |
| --- | --- | --- | --- | --- |
| C1 | `swe5001-service-images.yml` matrix for the three services (one Dockerfile, `ARG MODULE`), Trivy per image; release digests per service in SSM; `swe5001-deploy.yml` and the release gate loop over the matrix output | #456 | A3 | 10-15 |
| C2 | `runtime/services.tf` `distributed` map: three `ecs-service` entries, ALB rules (identity before commerce; `/internal/**` never routed), Service Connect (`identity-svc:8589`), per-service SSM scopes (Google to identity, Stripe to commerce), `IDENTITY_BASE_URL`, scheduling on for commerce only; `variables.tf` validation, `lib.sh` `services_for_topology`, `env-status.sh` accept `distributed` | #456 | C1, A4 | 10-16 |
| C3 | Demo data per database: catalog seed only in commerce; admin grant in identity through `scripts/grant-dev-admin.sql` after the first sign-in (one-off task or ECS Exec), then a second sign-in; at least one startable listing; runbook updated | #456 | C2 | 10-16 |
| C4 | Commerce target tracking (min 2 tasks across AZs), three-row dashboard (Edge, Services, Messaging) | #457 (@PPsyrius) | C2 | 10-16 |

### Track D — event chain feeding `notification-svc` (tier 2, after tracks A–C when they collide)

`#443` ADR 0007 accepted (10-12) → `#444` outbox behind `blocbuy.events.enabled` (default off; on only for ECS commerce) and `#215` persistence plus one list endpoint (10-14) → `#447` SNS topic, notification and audit queues, DLQ (10-15) → `#445` relay and `#446` consumer (10-16) → `#448` evidence. Without track D, `notification-svc` still deploys and passes health; it just has nothing to consume. Reductions if tracks A–C are behind: Sprint 4 plan §6 item 6.

## 3. Order and dependencies

```
A1 classifier ── A2 ADR 0008 + guardrails
      └───────── A3 assemblies ── A4 grants ──┐
B1 session roles ─────────────────────────────┤
B2 nickname port ── B3 remote identity ───────┼─ C1 images ── C2 runtime ── C3 demo data ── C4 scaling
D  outbox/relay/consumer + SNS/SQS ───────────┘                     │
                                                                     └─ 10-19 promotion + evidence session
```

| Date | Must be true |
| --- | --- |
| 10-12 | A1 merged; dev deploy healthy |
| 10-14 | A2–A4 merged |
| 10-15 | B1, B2, C1 merged; **identity go/no-go**: B3 tests green or identity runtime moves to 10-18 |
| 10-16 | B3, C2–C4 merged; one manual session reaches steady state for three services: before the promotion `prod` has no distributed images, so the operator pushes the three images by hand and writes `/blocbuy/swe5001/candidate/<service>` ([`infra/swe5001/README.md`](../../../infra/swe5001/README.md), "Environment sessions"), then runs `SWE5001_TOPOLOGY=distributed env-up.sh --candidate` from a `dev` checkout and `env-down.sh` after it; **SWE5001 code freeze** |
| 10-18 | Fallback deadline: if identity is still not green, present two services and record identity as a limitation |
| 10-19 | Promotion `dev` → `prod`; evidence session (V2–V5, V7, identity outage); fallback recording |

## 4. Protecting `dev` (SWE5006)

| Touch point | Risk | Guard |
| --- | --- | --- |
| A1 JAR classifier | Non-runnable image on `dev` | `.dockerignore` allow-list, manifest assertion, existing smoke; watch the first `dev` deploy |
| A3 new modules | Slower CI; Sonar new-code coverage | One lightweight context-load test per service; no duplicated configuration |
| A4 grants | Monolith loses table privileges | Grants only in service-profile locations; IT proves the monolith does not load them |
| B1 session roles | Shared login path changes | Sign in and call one admin endpoint on `dev` right after merge |
| B2 attention query | Admin payments page (BA-8) | IT pins today's nicknames and "Unknown buyer" before the change |
| D outbox writer | Unbounded `sys_outbox`, extra insert in payment transactions | `blocbuy.events.enabled` defaults to `false`; `dev` never sets it |

`topology=monolith` stays the default everywhere; `dev` workflows, Compose files and EC2 infrastructure are not edited by any step except A1's artifact path.

## 5. Done for the split

- Three services reach ECS steady state in one session; Google sign-in through `identity-svc`; an admin request accepted by `commerce-svc` on a session minted by identity; a START refused with 500 while identity is stopped and accepted after it returns.
- Smoke suite green against the distributed topology; `env-down.sh` and a clean `env-status.sh` afterwards.
- `dev` health `UP` after every merge in tracks A and B.
- ADR 0008 merged; handoff updated in the same PR as each step.
