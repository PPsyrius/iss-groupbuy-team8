# 0008. One codebase, two deployment topologies: the monolith on dev and three services on ECS

Date: 2026-10-12

## Status

Proposed (#455; Sprint 4 plan §4 items 0–3 and 8). Becomes Accepted when the PR that adds it is reviewed and merged. Complements ADR 0007, which covers the events between the services.

## Context

The same backend serves two courses. SWE5006 grades the agile process and a complete runnable system: the `dev` environment (one EC2 host, Docker Compose, the modular monolith) must keep working unchanged until its presentation on 2026-10-26. SWE5001 grades a scalable microservice platform: the `prod` environment (Amazon ECS, released by `dev` → `prod` promotion) must run the proposal's three services, `identity-svc`, `commerce-svc` and `notification-svc`, by 2026-10-21. A draft that deferred `identity-svc` was reversed on 2026-10-11; three services are a hard requirement.

The code is one Maven reactor of COLA modules (`blocbuy-client` … `blocbuy-bootstrap`) whose bounded contexts already sit in separate packages. Coupling that blocks a split, found in code on 2026-10-11: `StpInterfaceImpl` reads roles from the identity database on every request; 14 `requireActive` and one `publicNickname` call cross from commerce into identity; `PaymentAttentionMapper.xml` joins `sys_user`; two commerce controllers imported identity's `RoleCode`; `MyBatisConfig` scans every mapper; and `blocbuy-bootstrap` was a repackaged JAR that nothing could depend on.

## Decision

**Two topologies from one codebase.** `blocbuy.topology` is `monolith` (default everywhere, `matchIfMissing = true`) or `distributed`. `dev` never sets it, runs no broker and leaves the ADR 0007 outbox writer off. Only ECS sets `distributed`.

**Split by assembly, not by relocation.** Business code stays in the shared modules. `blocbuy-bootstrap` publishes a plain library JAR next to the executable `blocbuy-backend-exec.jar` (exec classifier). Three thin modules under `services/` each add an entry class, `application-<service>.yml`, their own component scan and `@MapperScan`; `MyBatisConfig` applies only to the monolith. Each service is built into its own image.

| Service | Contexts | Owned tables |
| --- | --- | --- |
| `identity-svc` | identity | `sys_user*`, `sys_role`, `sys_permission`, `rel_*` |
| `commerce-svc` | catalog, groupbuy, order, payment | everything else except `biz_notification` |
| `notification-svc` | notification | `biz_notification`, `sys_consumed_message` |

**Data.** Each service has its own database on the shared RDS instance and its own runtime role, granted only on the tables it owns. Every database applies the full Flyway stream while SWE5006 still adds migrations; the grants (`R__swe5001_grants.sql`) load only from service-profile locations, never in the monolith.

**Roles travel in the session.** Login in identity writes roles and permissions into the Sa-Token session, which all services share through Valkey. Outside identity, `StpInterface` reads the session and never calls identity per request. A role change takes effect at the user's next login. `RoleCode` moves from `com.blocbuy.identity.model` to `com.blocbuy.common.model`, so no commerce class imports identity's model.

**Commerce reaches identity only through `UserAppService`.** In `distributed`, an HTTP adapter calls `identity-svc` over ECS Service Connect at `/internal/users/**`. 404 and 409 map to the same identity errors as the local implementation. Timeouts and 5xx raise an unchecked exception that is not a `BizException`, so the request fails closed with 500, the Stripe webhook transaction rolls back and Stripe redelivers. `currentUser` has no remote implementation.

**Internal endpoints.** `/internal/**` relies on network isolation only: the ALB never routes it and the service security groups admit only peer services; there is no service token. Internal controllers live in `..web.internal..`, are `@Hidden` from the contract and are `@ConditionalOnProperty(name = "blocbuy.topology", havingValue = "distributed")`, so the `dev` monolith never serves them. `contracts/openapi.json` stays one public contract for all services.

**Guardrails.** `ArchitectureTest` fails the build when commerce depends on identity beyond `UserAppService`, anything outside identity calls `currentUser`, anything depends on notification or notification on another service, one service's persistence uses another's mappers, or an `/internal` path is mapped outside a guarded internal controller. `InternalPathsContractTest` fails when the contract exports an `/internal` path.

## Consequences

`dev` behaviour does not change: the default topology, the monolith image and the deploy path stay as they are, and every split step must keep `dev` health `UP` after merge. The guardrails hold the service boundaries in the monolith too, so a violation fails on the first PR rather than at the ECS session.

`commerce-svc` is still the transactional core of four contexts; the boundary follows transactional consistency, and extracting payment (outbox plus saga) is roadmap work. Payment webhooks depend on `identity-svc` at run time: an identity outage fails checkout closed until it returns, which is demonstrated by the identity outage drill and reported as a limitation, as are `/internal/**` without a token and the single RDS instance. Foreign tables exist but stay empty and unreachable in each service database until each service gets its own migration baseline after SWE5006 feature work ends.

Synchronisation obligations: a new table needs a grant for its owning service; a new cross-context call from commerce into identity must go through `UserAppService` and its remote adapter.

## Alternatives rejected

- **One image for all services, role chosen by a property.** No build change, but deviates from the proposal's split by assembly and weakens the platform-reuse story; kept only as the fallback if the build path breaks `dev`.
- **Moving business code into per-service source trees now.** Same end state with a far larger diff in the SWE5006 code path two weeks before its presentation.
- **Two services (identity kept in commerce).** Rejected on 2026-10-11: three services are a hard requirement.
- **Roles fetched from identity on every request.** Adds a synchronous call to every authorised request and couples availability of all services to identity.
- **An `X-Internal-Token` on `/internal/**`.** Defence in depth, cut on 2026-10-11 as not rewarded by either course; network isolation is the control.
