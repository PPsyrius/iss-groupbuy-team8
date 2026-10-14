# 0008. One codebase, two deployment topologies: the monolith on dev and three services on ECS

Date: 2026-10-12

## Status

Accepted (2026-10-12; proposed in #470, merged into `dev` at `c8f529ac`; #455, Sprint 4 plan §4 items 0–3 and 8). Complements ADR 0007, which covers the events between the services.

Amended 2026-10-12 (#455 step A4): the grants are a Flyway `afterMigrate` callback instead of the repeatable migration first planned, because `db-init`'s default privileges grant every table a later migration adds to all runtime roles and only a callback re-checks them on every start.

Amended 2026-10-12 (#467): RBAC auto-configuration selects the database implementation when the assembly supplies `RoleRepository` (monolith and identity), and the session implementation only for distributed assemblies without it. Login reads roles and permissions in one read-only REPEATABLE READ transaction, then writes one `blocbuy.rbac` map containing both mandatory lists; either list missing or malformed denies both capabilities outside identity, without SQL or remote fallback. Identity and the monolith continue to observe database role changes immediately; the next-login rule applies only outside identity.

Review fix 2026-10-12 (#467): authorization lives in a dedicated per-account **Sa-Token authorization session**, `${tokenName}:rbac:${loginType}:{${userId}}`, rather than Sa-Token's terminal-metadata session. Every account token reads this same snapshot. The identity adapter starts a random refresh fence (120 s TTL) before the SQL read, creates a fresh token without publishing it, and atomically checks the fence and writes the authorization session through Redis Lua using Sa-Token's configured serializer. Newer refresh attempts supersede older ones; a delayed or expired callback cannot overwrite newer authorization, even when framework terminal metadata changes concurrently. The snapshot inherits the account session's remaining TTL (non-expiring only if explicitly configured); both Lua keys share the account hash tag for Redis Cluster compatibility. Only after persistence succeeds does Sa-Token publish the cookie/header. Failures redirect through the existing OAuth failure URL, revoke only the newly created token (mapping-deletion fallback if cleanup fails), and never expose that token; existing logins are retained. If Redis is completely unavailable, cleanup may remain incomplete, but the unknown token was never sent to the browser and expires under the configured token TTL. Existing distributed sessions without the dedicated snapshot must log in again; a successful refresh updates every existing token of the account.

Amended 2026-10-13 (#468): the internal identity endpoints answer through the standard `Result` envelope and the global handler, so "active" is 200, a missing user 404 `IDENTITY_404_001` and a disabled account 403 `IDENTITY_403_001` (the code the monolith already returns), not the 204/409 first planned. The remote adapter maps a refusal only when the status and the identity code match, so a 404 from a missing route or a proxy error is an outage, never "user not found" and a refunded buyer. The adapter (`RemoteUserAppService`, `RemoteIdentityConfiguration`) lives in the `commerce-svc` assembly and is imported by its entry class; the monolith and `identity-svc` never have it on their classpath.

Amended 2026-10-14 (#469): the `PaymentAttentionMapper.xml` coupling is removed. The attention query selects user IDs only, and buyer nicknames come from one `UserAppService.publicNicknames` batch per page, which `identity-svc` serves as `POST /internal/users/public-nicknames` (1–100 IDs, otherwise 400; showable nicknames only). The commerce adapter splits larger sets into calls of at most 100 and treats any answer other than 200 as `IdentityUnavailableException`. SQL ownership, which ArchUnit cannot see, is enforced by `IdentityTablesStayInIdentityMappersTest`: no mapper XML outside `mapper/identity/` may name an identity table.

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

**Data.** Each service has its own database on the shared RDS instance and its own runtime role, granted only on the tables it owns. Every database applies the full Flyway stream while SWE5006 still adds migrations; the grants (`blocbuy-platform`, `db/swe5001/afterMigrate__service_grants.sql`) load only from service-profile locations and are not on the monolith's classpath.

**Roles travel in the session.** Login in identity writes roles and permissions into the Sa-Token session, which all services share through Valkey. Outside identity, `StpInterface` reads the session and never calls identity per request. A role change takes effect at the user's next login. `RoleCode` moves from `com.blocbuy.identity.model` to `com.blocbuy.common.model`, so no commerce class imports identity's model.

**Commerce reaches identity only through `UserAppService`.** In `distributed`, an HTTP adapter in `commerce-svc` calls `identity-svc` over ECS Service Connect at `/internal/users/**` (connect timeout 500 ms, read timeout 1500 ms, no retry, no cache). 404 `IDENTITY_404_001` and 403 `IDENTITY_403_001` map to the same identity errors as the local implementation; any other answer is an outage. Timeouts and 5xx raise an unchecked exception that is not a `BizException`, so the request fails closed with 500, the Stripe webhook transaction rolls back and Stripe redelivers. `currentUser` has no remote implementation.

**Internal endpoints.** `/internal/**` relies on network isolation only: the ALB never routes it and the service security groups admit only peer services; there is no service token. Internal controllers live in `..web.internal..`, are `@Hidden` from the contract and are `@ConditionalOnProperty(name = "blocbuy.topology", havingValue = "distributed")` without `matchIfMissing`, so the `dev` monolith never serves them. `contracts/openapi.json` stays one public contract for all services.

**Guardrails.** `ArchitectureTest` fails the build when commerce depends on identity beyond `UserAppService`, anything outside identity calls `currentUser`, anything depends on notification or notification on another service, one service's persistence uses another's mappers, or an `/internal` path, as Spring MVC registers it, is mapped outside a guarded internal controller. `InternalPathsContractTest` fails when the contract exports an `/internal` path.

Authorization-read consistency (#467, 2026-10-12): each distributed HTTP request memoizes one validated, immutable snapshot per account for both role and permission checks; a refresh cannot combine capabilities from two versions within that request. Subsequent requests see refreshed roles. Callback failure logs retain exception types only, because SDK/Redis exception details can contain token keys.

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
