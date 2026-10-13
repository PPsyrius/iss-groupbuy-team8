# #468 Remote identity check from `commerce-svc` to `identity-svc` (step B3, WP-B5) — implementation plan

Date: 2026-10-13 · Branch: `feature/468-remote-identity-20261013` from `dev` at `22536866` (#473 merged) · Issue: #468 (Sprint 4 container #465) · Owner: @l0sgAi (reassigned from @EricWang1358 on 2026-10-13) · Reviewer: @Yorick-Yu · Related: step B3 of the [microservice split plan](microservice-split-plan-20261012.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md), [backend implementation plan](../../swe5001/impl-backend-architecture.md) WP-B5, [refactoring plan](../../swe5001/microservice-refactoring-plan.md) §2.4 "Synchronous calls", [C1–C3 plan](service-runtime-plan-20261012.md)

> **Status (2026-10-13): draft for review.** §10 lists the decisions that need an answer before coding starts; each has a default.

## 1. Goal and acceptance criteria

`commerce-svc` must start and answer identity questions over HTTP. On `dev` at `22536866` it applies 27 migrations, then exits with `required a bean of type 'com.blocbuy.identity.service.UserAppService'`. This issue is the last blocker for the three-service ECS shape: a `dev` → `prod` promotion today would fail at `env-up`'s stable-service wait. The `dev` monolith must behave exactly as before.

| # | Criterion (issue text, then plan detail) | Delivered by |
| --- | --- | --- |
| AC1 | One contract test runs the same `requireActive` and `publicNickname` cases against both implementations | T1 (§5) |
| AC2 | WireMock tests cover timeout, 5xx, 404 and 409 (403 if Q1 is accepted), plus the cases §4.3 adds: a 404 without the identity code, a refused connection, a malformed body, trace propagation | T2 |
| AC3 | A webhook test proves that an identity timeout returns 500, writes no `BUYER_INACTIVE` outcome, and succeeds on redelivery | T4 + T5 (§5, Q5) |
| AC4 | `./mvnw verify` green with ≥ 80 % coverage on new code; monolith behaviour unchanged; `contracts/openapi.json` unchanged | V1–V3 |
| AC5 | `CommerceServiceApplicationIT` has no `@MockitoBean UserAppService` and proves that the remote adapter is wired | T6 |
| AC6 | Three images run as on ECS (ARM64, read-only root, profiles `ec2,<service>,aws`, `db-init`-prepared PostgreSQL, Redis): all three health `UP`; commerce → identity calls succeed, refuse a disabled user, and fail closed within about 2 s while identity is stopped | V4 (shared with @l0sgAi's runtime check) |

Non-goals (issue): caching identity answers; circuit breakers or client retries beyond the two timeouts (recorded as a limitation). Also out of scope: the masking rule and the admin attention query (#469); an internal service token (cut on 2026-10-11, ADR 0008); moving `requireActive` out of the webhook transaction (§8).

## 2. Baseline (read from `22536866`)

| Area | Today | Gap |
| --- | --- | --- |
| Port | `UserAppService` (`blocbuy-application`, `com.blocbuy.identity.service`): `requireActive`, `currentUser`, `publicNickname`. `publicNicknames(Set<UUID>)` does not exist yet (#469, open, @Yorick-Yu, due 10-15) | Remote implementation; the batch method once #469 adds it |
| Local implementation | `UserAppServiceImpl` (`@Service`, read-only transactions). Missing → `BizException(USER_NOT_FOUND)` = `IDENTITY_404_001`, HTTP 404; disabled → `User.ensureActive()` → `BizException(ACCOUNT_DISABLED)` = `IDENTITY_403_001`, **HTTP 403**. `publicNickname` never throws: empty for unknown, inactive or no nickname | None. Its errors are the contract the remote side must reproduce |
| Callers outside identity | `requireActive`: 14 sites (`AdminListingAppServiceImpl` 5, `TeamPriceAppServiceImpl` 5, `TeamTermsAppServiceImpl` 1, `PurchaseIntentAppService` 1, `AdminPaymentAppService` 1, `OrderFulfilmentAdapter` 1). `publicNickname`: `ShareInviteAppService.resolve` (public invite page). `currentUser`: identity's `AuthController` only, held by ArchUnit `only_identity_reads_current_user` | None. No caller changes |
| Swallowing catch blocks | Only `OrderFulfilmentAdapter.rejects` turns a `BizException` from `requireActive` into an outcome (`BUYER_INACTIVE` → refund). `TeamTermsAppServiceImpl` catches `BizException` only around `prices.configure` and rethrows. No `catch (RuntimeException)` surrounds a `requireActive` call | An unchecked non-`BizException` passes every caller unchanged |
| Webhook transaction | `StripeCheckoutService.apply` → `tx.executeWithoutResult(settle)`: `claimEvent` → `payment.succeed()` → `events.publishEvent(PaymentSucceeded)` (the #473 outbox row is written `BEFORE_COMMIT`) → `business.fulfil` → `rejects(requireActive)`, all in one transaction. `StripeRequestExceptionHandler` handles only a missing header, so any other exception reaches `GlobalExceptionHandler.handleUnknown` → 500 `INTERNAL_ERROR` | None. `StripeCheckoutFlowTest.signedHttpFailureRollsBackAndProviderRetryCanRecover` already proves 500, no event row and recovery on redelivery for a generic failure |
| Assemblies | `CommerceServiceApplication` scans catalog, groupbuy, order, payment, `integration.outbox`, `common`, `bootstrap.config`, `bootstrap.scheduling`, but **not** `com.blocbuy.identity`, so `UserAppServiceImpl` is absent. `IdentityServiceApplication` scans `com.blocbuy.identity` and `com.blocbuy.common` | Commerce must register the remote adapter explicitly |
| Topology guard | `PlatformAutoConfiguration.distributedTopologyGuard` fails any service assembly that does not run `distributed`; `TopologyCheck` rejects unknown values | None |
| Trace propagation | `PlatformAutoConfiguration` adds `TraceIdPropagationInterceptor` to every Boot-provided `RestClient.Builder`; `TraceIdFilter` on identity accepts and sanitises an inbound `X-Trace-Id` | None, as long as the adapter uses the injected builder |
| Auth on `/internal/**` | `SaTokenConfig.PUBLIC_PATHS` contains `/internal/**` (#467). No servlet context path: the path is literally `/internal/users/...` | None |
| ArchUnit | `internal_controllers_are_hidden_and_distributed_only`, `internal_paths_live_in_internal_controllers`, `commerce_reaches_identity_only_through_user_port`; `InternalPathsContractTest` | The new controller must satisfy them; nothing to change |
| Runtime | `infra/swe5001/runtime/services.tf` gives commerce only `IDENTITY_BASE_URL=http://identity-svc:8589` (no trailing slash; `terraform test` asserts it). Service Connect creates commerce after identity; the ALB blocks `/internal/*` and `/api/internal/*`; the smoke suite checks that `/internal/users/...` is not served | Bind the variable to a property |
| Context ITs | `CommerceServiceApplicationIT` has `@MockitoBean UserAppService` with a "delete when #468 lands" note | Delete it (AC5) |
| Test tooling | No WireMock in the build. Spring Boot parent 4.1.1; JaCoCo reports already exist per service module and are in `sonar.coverage.jacoco.xmlReportPaths` | Add `org.wiremock:wiremock-standalone` (test scope, commerce-svc) |

## 3. Wire contract of `/internal/users`

`identity-svc` serves it through `InternalUserController`, which delegates to `UserAppService` (in identity that is `UserAppServiceImpl`). Every response is the standard `Result<T>` envelope, and every error goes through `GlobalExceptionHandler`, as the backend constraints require.

| Method and path | Success | Errors (status, `code`) | Notes |
| --- | --- | --- | --- |
| `GET /internal/users/{id}/active` | 200 `Result.ok()` | 404 `IDENTITY_404_001` missing; 403 `IDENTITY_403_001` disabled; 400 `COMMON_*` malformed UUID | **Deviation from the issue (204 / 404 / 409), see Q1** |
| `GET /internal/users/{id}/public-nickname` | 200 `Result<PublicNicknameView>`, `nickname` null when unknown, inactive or not showable | 400 malformed UUID | Mirrors "never throws for an unknown user" |
| `POST /internal/users/public-nicknames` | 200 `Result<PublicNicknamesView>`, a map `userId → nickname` holding only users with a showable nickname | 400 when `userIds` is null, empty or larger than 100 (`PageQuery.MAX_SIZE`, the largest admin page) | Needs `UserAppService.publicNicknames` from #469 (§7, Q3) |

**Why 200/403 rather than 204/409 (Q1).** The issue's 204/409 would need the controller to set its own status (forbidden: only the global handler applies status, from the `ErrorCode`). It would also need a new 409 error code that means the same as `ACCOUNT_DISABLED`. With the envelope, the adapter keys on the error **`code`** as well as the status. That matters for money: if identity answers 404 because the route does not exist (an old identity image, a wrong base URL, `NoResourceFoundException` → `COMMON_404_*`), status-only mapping would raise `USER_NOT_FOUND`, `rejects` would record `BUYER_INACTIVE`, and the paying buyer would be refunded. Keying on the code makes that case "identity unavailable" (500, Stripe redelivers). If Q1 is accepted, ADR 0008's sentence "404 and 409 map to the same identity errors" is amended to "404 `IDENTITY_404_001` and 403 `IDENTITY_403_001`".

Wire records (`PublicNicknameView(String nickname)`, `PublicNicknamesQuery(Set<UUID> userIds)`, `PublicNicknamesView(Map<UUID, String> nicknames)`) live with the controller in `blocbuy-adapter`, `com.blocbuy.identity.web.internal`. The commerce adapter deserialises the same records (commerce-svc has `blocbuy-adapter` on its classpath through the bootstrap library JAR), so the two sides cannot drift.

## 4. Design

### 4.1 Identity side: `InternalUserController`

- `blocbuy-adapter`, `com.blocbuy.identity.web.internal.InternalUserController`: `@RestController`, `@RequestMapping("/internal/users")`, `@Hidden`, `@ConditionalOnProperty(name = Topology.PROPERTY, havingValue = Topology.DISTRIBUTED)` (no `matchIfMissing`), `@Validated` for the batch bound. It injects `UserAppService` and nothing else.
- The monolith scans `com.blocbuy` but never registers the controller (`topology` defaults to `monolith`). `identity-svc` scans `com.blocbuy.identity` and registers it. `commerce-svc` and `notification-svc` do not scan the package.
- `/internal/**` is already public in `SaTokenConfig`. The ALB block and the security groups are the controls (ADR 0008).

### 4.2 Commerce side: where the adapter lives

`services/commerce-svc`, package `com.blocbuy.service.commerce.identity`:

| Class | Role |
| --- | --- |
| `IdentityClientProperties` | `@ConfigurationProperties("blocbuy.identity")` record, `@Validated`: `baseUrl` (`@NotNull URI`), `connectTimeout` (default 500 ms), `readTimeout` (default 1500 ms) |
| `RemoteUserAppService` | `implements UserAppService` (§4.3) |
| `RemoteIdentityConfiguration` | `@Configuration`, `@EnableConfigurationProperties(IdentityClientProperties.class)`; builds the `RestClient` from the injected `RestClient.Builder` (trace propagation included) with its own request factory and the two timeouts; exposes `RemoteUserAppService` as the only `UserAppService` bean |

`CommerceServiceApplication` gains `@Import(RemoteIdentityConfiguration.class)`, the same pattern `IdentityServiceApplication` uses for `TraceIdFilter`. `application-commerce.yml` gains:

```yaml
blocbuy:
  identity:
    # Service Connect DNS of identity-svc (infra/swe5001/runtime/services.tf); no default, so a missing value fails startup
    base-url: ${IDENTITY_BASE_URL}
```

Why there (Q2):

- Only commerce calls identity. The class is on no other classpath, so the monolith keeps `UserAppServiceImpl` without a conditional, and identity-svc cannot end up with two `UserAppService` beans.
- "Selected by `blocbuy.topology=distributed`" holds because the `commerce-svc` assembly already refuses to start in any other topology (`distributedTopologyGuard`).
- commerce-svc already has its own JaCoCo report in the Sonar path.

The design document's earlier idea (the adapter in the platform starter) would make `blocbuy-platform` depend on `blocbuy-application` and `blocbuy-domain`, and would need `@ConditionalOnMissingBean` ordering to stay out of identity-svc. WP-B5 does not fix the location.

`IdentityUnavailableException` goes to `blocbuy-domain`, `com.blocbuy.identity.exception`, next to `IdentityErrorCode`. It extends `RuntimeException`, **not** `BizException`. Domain placement lets `OrderFulfilmentAdapterTest` (application module) throw the real type. No commerce class references it, so `commerce_reaches_identity_only_through_user_port` is unaffected.

### 4.3 `RemoteUserAppService` behaviour

The adapter uses `RestClient.exchange(...)` so it sees every status and body itself; nothing relies on default status handlers.

| What happens | `requireActive` | `publicNickname` / `publicNicknames` |
| --- | --- | --- |
| 200 with a valid envelope | Returns | `Optional.ofNullable(nickname)` / the map (missing ids mean "no nickname") |
| 404 with `code = IDENTITY_404_001` | `BizException(IdentityErrorCode.USER_NOT_FOUND)` | — (identity never answers it here) → unavailable |
| 403 with `code = IDENTITY_403_001` | `BizException(IdentityErrorCode.ACCOUNT_DISABLED)` | — → unavailable |
| Any other status, or the right status with another code (route 404, 400, 401, 409, 5xx, Envoy 503) | `IdentityUnavailableException` | `IdentityUnavailableException` |
| Connect timeout (500 ms), read timeout (1500 ms), refused connection, DNS failure, I/O error | `IdentityUnavailableException` | `IdentityUnavailableException` |
| Malformed or unparseable body | `IdentityUnavailableException` | `IdentityUnavailableException` |
| — | `currentUser` → `UnsupportedOperationException("currentUser is served by identity-svc only")` | |

Details:

- The two `BizException`s are built from the local `IdentityErrorCode` constants, never from remote text, so callers, the `rejects` branch and HTTP answers stay the same as in the monolith.
- `IdentityUnavailableException` carries the operation, the HTTP status or exception class, and the elapsed time. Its message never includes the response body. One WARN log line per failure (trace id from MDC); the global handler then logs its usual ERROR line with the stack trace.
- Request factory: JDK `HttpClient` (HTTP/1.1, connect timeout) with a per-request read timeout. Service Connect's Envoy speaks HTTP/1.1, and this avoids an h2c upgrade attempt. Whether `ClientHttpRequestFactoryBuilder` / `HttpClientSettings` in Boot 4.1 covers this more simply is checked while coding.
- No client retry and no cache (non-goals). Worst case per call is about 2 s (0.5 s connect + 1.5 s read).
- `publicNicknames(emptySet)` returns an empty map without a call. A set larger than 100 is split into calls of at most 100 (one call for a page, so #469's "one batch lookup per page" still holds).

### 4.4 Failure semantics per caller (unchanged code, new outcomes)

| Caller | Identity down or slow (`IdentityUnavailableException`) | Identity says missing or disabled (`BizException`) |
| --- | --- | --- |
| Stripe webhook (`settle` → `fulfil` → `rejects`) | Not caught by `rejects`. The whole settle transaction rolls back: event claim, `SUCCEEDED` status, outbox row. The global handler answers 500; Stripe redelivers with backoff, and the redelivery claims and settles normally | As in the monolith: `BUYER_INACTIVE` → refund required, 2xx |
| Manual recovery (`StripeCheckoutService.recover`) | Its `catch (RuntimeException)` records the failed attempt and rethrows → 500 to the admin; retry later | As in the monolith |
| Admin listing, pricing, terms, payment actions; purchase intent | Request transaction rolls back; 500 `INTERNAL_ERROR` (fail closed) | As in the monolith (404 / 403) |
| Public invite resolve (`ShareInviteAppService`) | 500 (fail closed, as the issue states). Q4 asks whether to show the invite without a nickname instead | — (never throws) |

The global handler needs no change: `IdentityUnavailableException` reaches `handleUnknown` → 500, which is what the issue and ADR 0008 specify.

### 4.5 What does not change

`UserAppService` (apart from #469's method), `UserAppServiceImpl`, all 15 caller sites, `OrderFulfilmentAdapter` (its `rejects` already catches only `BizException`; a comment pointing at #468 is the only possible edit), `GlobalExceptionHandler`, `SaTokenConfig`, `contracts/openapi.json` and every Terraform file. The `dev` monolith has no new bean.

## 5. Tests

| Id | Test (module) | Proves |
| --- | --- | --- |
| T1 | `UserAppServiceContractTest` (commerce-svc test), an abstract case set run twice: **local** = `UserAppServiceImpl` over an in-memory `UserRepository` fixture; **remote** = `RemoteUserAppService` → `MockMvcClientHttpRequestFactory` → real `InternalUserController` + real `GlobalExceptionHandler` → `UserAppServiceImpl` over the same fixture. Cases: active passes; unknown → `USER_NOT_FOUND`; disabled → `ACCOUNT_DISABLED`; nickname shown; unknown and disabled → empty; nickname not showable → empty; batch (after #469) | AC1. Both sides return the same answer for every case, including the error code a caller sees. The MockMvc transport exercises the real controller, envelope and handler mapping in process |
| T2 | `RemoteUserAppServiceTest` (commerce-svc test, WireMock on a random port, real JDK client and timeouts): 200; 404 + identity code; 404 with `COMMON_404_*` code → unavailable; 403 + identity code; 409 / 400 / 401 → unavailable; 500 and 503 → unavailable; fixed delay 2 s → unavailable within < 1.9 s; port closed → unavailable; non-JSON body → unavailable; `X-Trace-Id` sent from MDC; batch request body and empty-set short cut; `currentUser` → `UnsupportedOperationException` | AC2 |
| T3 | `InternalUserControllerTest` (blocbuy-bootstrap test, standalone MockMvc with `GlobalExceptionHandler`): statuses and envelopes of §3, batch bound 0 / 101 → 400 | Identity side of the contract |
| T4 | `OrderFulfilmentAdapterTest`, new case `anIdentityOutageIsNotABuyerRejection`: `requireActive` throws `IdentityUnavailableException` → `fulfil` rethrows it; no admission, order, intent or stock writes | The `rejects` branch cannot turn an outage into `BUYER_INACTIVE` |
| T5 | `StripeCheckoutFlowTest`, new case `identityOutageAnswers500WithoutOutcomeAndRedeliverySettles`, next to `signedHttpFailureRollsBackAndProviderRetryCanRecover`: the test business throws `IdentityUnavailableException` on the first `fulfil`; signed HTTP webhook → 500; no `log_payment_webhook_event` row, no outcome, no `biz_refund` row, payment still `CREATED`; the same event again → 2xx and `FULFILLED` | AC3 at the HTTP and transaction level (Q5) |
| T6 | `CommerceServiceApplicationIT`: drop `@MockitoBean UserAppService`, set `blocbuy.identity.base-url` to an unused local URL, assert exactly one `UserAppService` bean and that it is a `RemoteUserAppService`; context starts without contacting identity | AC5 |
| T7 | `IdentityServiceApplicationIT`: `InternalUserController` is registered and the `UserAppService` is `UserAppServiceImpl`. A monolith context test asserts that no `InternalUserController` bean exists (the ArchUnit rule checks the annotation; this checks the result) | Topology selection both ways |
| T8 | Existing `ArchitectureTest` (all service-boundary rules) and `InternalPathsContractTest`; `-Popenapi-export` regenerates an identical `contracts/openapi.json` | AC4, `@Hidden` works |

Coverage: T1, T2 and T6 are in commerce-svc (own JaCoCo report). T3, T4 and T5 count in the bootstrap aggregate. The new production code is about 250 lines, almost all of it on tested branches.

## 6. Changes (one PR, about 20 files, est. 900 lines including tests)

| Id | File | Change |
| --- | --- | --- |
| I-1 | `blocbuy-adapter/.../identity/web/internal/InternalUserController.java` (new) | §3, §4.1 |
| I-2 | `blocbuy-adapter/.../identity/web/internal/PublicNickname*.java` (new, 2–3 records) | Wire records |
| I-3 | `blocbuy-domain/.../identity/exception/IdentityUnavailableException.java` (new) | §4.2 |
| C-1 | `services/commerce-svc/.../identity/IdentityClientProperties.java` (new) | §4.2 |
| C-2 | `services/commerce-svc/.../identity/RemoteUserAppService.java` (new) | §4.3 |
| C-3 | `services/commerce-svc/.../identity/RemoteIdentityConfiguration.java` (new) | §4.2 |
| C-4 | `services/commerce-svc/.../CommerceServiceApplication.java` | `@Import(RemoteIdentityConfiguration.class)`; Javadoc |
| C-5 | `services/commerce-svc/src/main/resources/application-commerce.yml` | `blocbuy.identity.base-url: ${IDENTITY_BASE_URL}` |
| C-6 | `services/commerce-svc/pom.xml`, `backend/pom.xml` | `wiremock-standalone` test dependency and version property |
| T-1…T-8 | Tests of §5 (4 new classes, 4 extended) | |
| D-1 | `backend/docs/adr/0008-dual-deployment-topology.md` | Amendment: 403 instead of 409 (if Q1); adapter location (Q2) |
| D-2 | `docs/swe5001/impl-backend-architecture.md` WP-B5 | Dated note: wire contract, location, keyed on the error code |
| D-3 | `docs/swe5001/HANDOFF.md` | B3 row |
| D-4 | `docs/agile/sprint-04/microservice-split-plan-20261012.md` | B3 owner @l0sgAi |
| D-5 | this plan | Status |
| D-6 | `docs/agile/sprint-04/log/2026-10-1x.md`, `log.md` | Day log entry |
| D-7 | `.claude/skills/backend-constraints-skill/SKILL.md` | One line: internal endpoints use the envelope; remote adapters map identity errors by code |

If #469 is not merged when this PR is ready, the batch endpoint and the remote `publicNicknames` move to whichever of the two PRs merges second (§7, Q3).

## 7. Order, dates and dependencies

| Date | Step |
| --- | --- |
| 10-13 | This plan reviewed (§10 answered) |
| 10-14 | Draft PR: I-1…I-3, C-1…C-6, T1–T8 without the batch; local V1–V4. @l0sgAi runs the three-image check against the branch and posts it on #468 |
| 10-15 | **Identity go/no-go** at the mid-sprint check: T1–T6 green and V4 shows commerce `UP` |
| 10-15/16 | Batch endpoint and remote `publicNicknames`, after #469's port method (in whichever PR merges second) |
| 10-16 | Merge into `dev`; `dev` health `UP`; SWE5001 code freeze; first manual distributed ECS session (C2/C3 plan, AC6 there) |
| 10-18 | Fallback deadline: if not green, `SWE5001_TOPOLOGY=monolith` (or two services) and identity recorded as a limitation |

| Needed for | Dependency | Owner |
| --- | --- | --- |
| Batch endpoint (#469 AC "one batch lookup per page" in `distributed`) | `UserAppService.publicNicknames(Set<UUID>)`; agreed signature `Map<UUID, String> publicNicknames(Set<UUID> userIds)`, missing key = no showable nickname | @Yorick-Yu (#469), 10-15 |
| Compile safety across the two PRs | Whichever PR adds the interface method second also implements it in `RemoteUserAppService`; otherwise commerce-svc does not compile | Both |
| Review | @Yorick-Yu | 10-14/15 |
| AC6 | Operator session with B1 (#467, merged) and this PR | @l0sgAi, 10-16 |

## 8. Risks and limitations

| Risk | Mitigation or record |
| --- | --- |
| Identity outage blocks checkout settlement, admin actions and the invite page | Intended fail-closed behaviour (ADR 0008). Stripe retries for up to three days; the identity outage drill in the 10-19 evidence session shows 500s while identity is down and recovery after it returns. Recorded as a limitation |
| The webhook holds the team lock, the intent row lock and a pooled connection during the identity call, for up to about 2 s | Accepted for the demo load (one buyer per team per webhook). Removing it means checking the buyer before the locks, which changes the monolith path two weeks before SWE5006's presentation. Recorded as roadmap work |
| A route 404 or a proxy error read as "user missing" refunds a paying buyer | Mapping on the error code (§3, §4.3); T2 has a route-404 case |
| No retries or circuit breaker: one dropped connection fails one request | Non-goal (issue). Envoy and Service Connect metrics plus the client WARN line make failures visible. Recorded as a limitation |
| `/internal/**` has no token | Network isolation (ALB block at priority 5, peer-only security groups, smoke check). Already a recorded limitation (ADR 0008) |
| `wiremock-standalone` with Spring Boot 4.1 / Jetty 12 | The standalone artifact shades its Jetty and Jackson; checked first thing. Fallback: the JDK's `com.sun.net.httpserver.HttpServer` as the stub (same cases, a little more code) |
| `IDENTITY_BASE_URL` missing in a local run | Startup fails with a clear placeholder error; the C2/C3 runbook and the local three-image check set it |
| #469 and #468 conflict on `UserAppService` | Signature agreed in §7; whichever merges second implements the remote side |

Rollback: revert the PR. `dev` loads none of the new beans; the ECS fallback is `SWE5001_TOPOLOGY=monolith`.

## 9. Verification and evidence

| # | Check | Where |
| --- | --- | --- |
| V1 | `./mvnw -B verify` with Docker (JDK 21): all tests, `ArchitectureTest`, JaCoCo per module; counts recorded | Local |
| V2 | `-Popenapi-export` gives a byte-identical `contracts/openapi.json` | Local |
| V3 | Monolith (`local` profile) health `UP` and no `/internal/users/...` route (404) | Local |
| V4 | Three ARM64 images from `Dockerfile.ci`, read-only root, profiles `ec2,<service>,aws`, `db-init`-prepared PostgreSQL, Redis, one Docker network with `IDENTITY_BASE_URL=http://identity-svc:8589`: three health `UP`; a commerce admin request for a granted admin succeeds; the same after disabling the user → 403; `docker stop identity-svc` → the same request answers 500 within about 2 s with the WARN line and the same trace id in both logs | Local, repeated by @l0sgAi on #468 |
| V5 | PR CI: `ci-backend` (verify, Sonar new-code gate ≥ 80 %), `swe5001-service-images` if its paths change | GitHub |
| V6 | `dev` health `UP` after merge | @l0sgAi |
| V7 | AC6 in the 10-16 ECS session; identity outage drill on 10-19 | Operator |

Not verifiable locally: Service Connect DNS and Envoy behaviour, ECS security groups, the real Stripe redelivery schedule.

Evidence: a day log entry with V1–V5 results; ADR 0008 amendment (Q1, Q2); WP-B5 and handoff rows; the limitations of §8 copied into the SWE5001 report notes in the handoff.

## 10. Questions for the reviewer

| # | Question | Default |
| --- | --- | --- |
| Q1 | Internal `active` endpoint answers 200 `Result.ok()` / 404 `IDENTITY_404_001` / 403 `IDENTITY_403_001` through the global handler, and the adapter maps by error code, instead of the issue's 204/404/409 mapped by status (§3)? | Yes; amend ADR 0008 and the issue text |
| Q2 | `RemoteUserAppService` and its configuration in `services/commerce-svc`, imported by `CommerceServiceApplication`, rather than in `blocbuy-platform` (§4.2)? | commerce-svc |
| Q3 | The batch endpoint and remote `publicNicknames` land in whichever of #468 and #469 merges second, so this PR is not blocked by #469? | Yes |
| Q4 | Public invite resolve during an identity outage: fail closed with 500 (issue), or show the invite with a null nickname (the page already handles a missing nickname)? | Fail closed, as the issue says; revisit only if the outage drill shows the invite page |
| Q5 | AC3 proved in two layers, T4 (the real `OrderFulfilmentAdapter` does not reject on an outage) and T5 (HTTP 500, rollback, redelivery through the real `StripeCheckoutService`), rather than one commerce-svc IT with PostgreSQL, WireMock and a seeded purchase intent (about 3 h more)? | Two layers; add the end-to-end IT only if time remains after 10-15 |
| Q6 | Client timeouts as properties (`blocbuy.identity.connect-timeout`, `read-timeout`) with the 500 ms / 1500 ms defaults, not hard-coded? | Properties with defaults; no Terraform change |
