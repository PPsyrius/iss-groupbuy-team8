# US15b Invite names the sharer — backend implementation plan

Date: 2026-09-30 · Branch: `feature/invite-sharer-resolve-20260930` · Issue: #201 (US15b API, UC7 follow-up) · Owner: los gai (backend) · Reviewer: @limjiechao · Frontend consumer: #230 (@limjiechao, FB-19) · Related: [Sprint 3 plan](plan.md), [share attribution plan D5](../sprint-02/share-attribution-plan-20260924.md), [backend ADR 0003](../../../backend/docs/adr/0003-share-credit-post-commit-subscriber.md), #208 / #274 (rate limiting)

Budget: about 5 h of the 6 h US15b line (the rest is the #230 UI). Due **2026-09-30**, so #230 can regenerate the client and ship by 2026-10-02.

> **Status (2026-09-30): implemented on this branch, not yet committed or merged.** D1, D2 and D3 were accepted in chat on 2026-09-30 and recorded on #201. Departures from the text below:
>
> - **No window index in the Redis key (§4.5).** The key is `blocbuy:{env}:common:rate-limit:{scope}:{digest}`; the first request of a window creates it with a one-window TTL in the same Lua call, and the window ends when the key expires. No clock is involved, so nothing needs to be injected or mocked.
> - **Subject digest.** `UUID.nameUUIDFromBytes` (name-based UUID) instead of truncated SHA-256: same pseudonymisation, no checked `NoSuchAlgorithmException` branch.
> - **No ArchUnit change (§4.2 item 4 dropped).** Infrastructure implementations share their port's package (`identity.gateway.RedisOAuthStateStore`), so a framework-free rule on `common.gateway..` would flag `RedisRateLimitGateway`. The port stays JDK-only by review, like the other gateway ports.
> - **WEB-7.** A POST on the invite path never reaches the use case, but it returns 500, not 401: Spring rejects the method before the Sa-Token interceptor runs, and `GlobalExceptionHandler` has no 405 mapping (pre-existing, app-wide; split out). The test instead proves a deeper path (`/invites/{ref}/credits`) still needs a session (401), so only single-segment GET is anonymous.
> - **NFR row is NFR-46**, not NFR-43: the draft misread the register, where NFR-43 and NFR-44 already existed; NFR-45 was then taken by #249 on `dev`.

## 1. Goal and acceptance criteria

A buyer who opens an invite link (`/products/{productId}?deal={dealId}&ref={ref}`) before signing in sees who shared it: "Sarah started this bloc", "Sarah's bloc is full". Today the landing page has the opaque `ref` but no way to turn it into a name, so the banner shows generic copy.

| # | Criterion (issue #201) | Delivered by |
| --- | --- | --- |
| AC1 | Option (a) or (b) chosen and recorded on the issue | §3 D0; issue comment (§7) |
| AC2 | Anonymous `GET /api/v1/invites/{ref}` for an active invite returns `dealId`, `productId`, `sharerNickname`, `expiresAt`, and no email, user id or other personal data (IT asserts the body) | §4.1, §4.3; WEB-1, WEB-2 |
| AC3 | Unknown `ref` → 404; expired or closed bloc behaviour documented in the contract | §3 D1; §4.1; APP-3..7, WEB-4 |
| AC4 | Rate-limited per client IP so refs cannot be enumerated; `Cache-Control: no-store` | §3 D2, D4; §4.4, §4.5; RL-*, WEB-3, WEB-5 |
| AC5 | `contracts/openapi.json` regenerated and committed; `./mvnw -B verify` green | §6 step 7; §8 |

Out of scope: any change to attribution or crediting (`resolveSharer` on JOIN stays as in #198); the frontend banner (#230); rate limits on the write endpoints (#274 reuses the limiter built here); a trusted client-IP resolver for the whole app (separate task, see §9 R1).

## 2. Baseline (read from `dev` at `9faf721`)

| Piece | State | Consequence |
| --- | --- | --- |
| `ShareInvite(id, ref, dealId, sharerUserId)` | domain record; `ref` is 22 base64url chars (128 random bits); private `REF_FORMAT` pattern | expose the pattern once as a public constant and reuse it in the adapter (§4.2) |
| `ShareInviteRepository.findByRef` | `WHERE invite_ref = ? AND deleted = false`, unique index on `invite_ref` | one indexed read; no new SQL |
| `GroupDealRepository.findById` | returns the deal with `productId`, `status`, `expireTime` | one indexed read; no new SQL |
| `ShareInviteAppService.mint` | minting only; D5 of the share-attribution plan deliberately left out a resolve endpoint | add `resolve` next to `mint` |
| Nickname | `sys_user.nickname`, nullable. `User.registerViaOAuth` sets it to the Google display name, or the **email local part** when Google gives none | public exposure needs D3 |
| Cross-domain read | `order` already calls `identity.service.UserAppService` directly (`PurchaseIntentAppService`, `OrderFulfilmentAdapter`); `identity` never imports `groupbuy` | groupbuy may call `UserAppService` the same way; no cycle, no new port |
| `SaTokenConfig` | `PUBLIC_PATHS` (all methods) and `ANONYMOUS_GET_PATHS` (`/products/**`, GET only) | add `/invites/*` to `ANONYMOUS_GET_PATHS`, so only GET is anonymous |
| Rate limiting | none in production code; `CommonErrorCode.RATE_LIMITED` (`COMMON_429_001`, 429) exists; `blocbuy-domain/.../common/gateway/` exists and is empty | build the first limiter (§4.2, §4.5) |
| Redis test support | no Redis Testcontainers test exists; `RedisOAuthStateStoreTest` mocks the template. `testcontainers-junit-jupiter` is already a test dependency of `blocbuy-infrastructure` | the limiter IT uses `GenericContainer("redis:8.10")`; no new dependency |
| Client IP on dev | `SERVER_FORWARD_HEADERS_STRATEGY=framework` makes `getRemoteAddr()` the **leftmost** `X-Forwarded-For` value, which the caller controls. CloudFront `/api/*` uses the managed `AllViewerExceptHostHeader` origin request policy (`infra/terraform/frontend-stack/cloudfront.tf`); the ALB only accepts the CloudFront prefix list and the origin secret header; 8589 is closed to the public (`docs/deploy/dev-cloudfront-deployment.md`) | key the limiter on `CloudFront-Viewer-Address` (D2) |
| `Result` envelope | `@JsonInclude(NON_NULL)` on `Result` only; nested DTO nulls serialise as `null` | `sharerNickname: null` is sent explicitly |
| Latest error code in groupbuy | `GROUPBUY_404_001` (deal not found) | new `GROUPBUY_404_002` |
| NFR register | latest row NFR-45 | new NFR-46 (§7) |

## 3. Decisions

✅ = accepted or recommended; the plan assumes it. ⚠️ = reviewer decides before step 1.

| # | Decision | Choice | Why / alternative |
| --- | --- | --- | --- |
| D0 ✅ | Option (a) resolve endpoint or (b) nickname in the URL | **(a)** `GET /api/v1/invites/{ref}` | (b) puts an editable name in the link, so anyone can forge "Sarah invited you". (a) keeps the name server-owned and the link unchanged |
| D1 ✅ (chat, 2026-09-30) | Response for a full, succeeded, expired or failed bloc | **200 for every existing invite, whatever the bloc status.** 404 only for an unknown, deleted or malformed ref (and for an invite whose deal row is gone). No 410 | #230 needs the name for the full and ended banners ("Sarah's bloc is full"). The landing already loads the bloc status from `productId` and `deal`, so returning the name reveals nothing new. This departs from the issue's "404 or 410 for an expired or closed bloc"; the issue comment records it |
| D2 ✅ (chat, 2026-09-30) | Client address for the per-IP limit | **`CloudFront-Viewer-Address`** when the `blocbuy.web.client-address-header` property names it (set on the `ec2` profile only); otherwise `getRemoteAddr()` (local and docker profiles). The port suffix is stripped | `getRemoteAddr()` on dev is the leftmost `X-Forwarded-For` value, so a caller could reset the limit on every request by sending a new header. CloudFront sets `CloudFront-Viewer-Address` itself. Whether the managed policy forwards it, and whether CloudFront overwrites a viewer-supplied copy, is checked on dev before the limit is relied on (§8 DEV-3, DEV-4). If it is not forwarded, a custom origin request policy in `cloudfront.tf` is a separate infra PR (deployment-operations skill) |
| D3 ✅ (chat, 2026-09-30) | What name leaves the server | **The stored nickname unchanged, with two exceptions that return `null`:** (1) the account is not `ACTIVE`; (2) the nickname equals the local part of the user's email (case-insensitive). Null or blank also → `null`. "Sarah Tan" → "Sarah Tan"; "sarah.tan93" (from `sarah.tan93@gmail.com`) → `null`; a disabled account → `null` | The issue asks for the nickname, the same field as `MeResponse.nickname`, and it is what the sharer's contacts see elsewhere in the app. Rejected: first word only, which turns "Lim Jie Chao" into the family name "Lim". (1) stops a banned user's name appearing on a landing page. (2) covers `User.registerViaOAuth` falling back to the email local part when Google gives no display name, which would reveal most of the sharer's email address to anyone holding the link. No server-side length cut; the banner truncates long names with CSS (#230) |
| D4 ✅ | Rate limit shape | **Fixed window, 60 requests per minute per client address**, scope `invite-resolve`. Redis `INCR` + `PEXPIRE` in one Lua script, so no key is ever left without a TTL. **Fail-open** on Redis errors (WARN log) | Refs are 128 random bits, so the limit is defence in depth, not the enumeration barrier; a Redis outage must not take the invite landing down. 60/min covers a class demo behind one NAT. A token bucket or sliding window is more exact but unnecessary here; #274 can revisit |
| D5 ✅ | Where the limit is enforced | **Application layer**: `ShareInviteAppService.resolve` calls the `RateLimitGateway` port first, before any database read | Controllers stay protocol-only (backend skill); the port sits in `blocbuy-domain` `common.gateway` because infrastructure cannot depend on application. #274 calls the same port from its three use cases. Alternative: a `HandlerInterceptor` (keeps the use case clean, but splits one use-case rule across layers) |
| D6 ✅ | Transaction | **No `@Transactional` on `resolve`.** It does two independent indexed reads plus one identity read that has its own read-only transaction | The result is display text; a deal that closes between the two reads changes nothing that matters. Not opening a transaction also avoids holding a DB connection during the Redis call |
| D7 ✅ | Cross-domain nickname lookup | **New method on identity's public `UserAppService`**: `Optional<String> publicNickname(UUID userId)` | Same pattern as `order` → `identity`. A consumer-owned port (like `ListingDisplayGateway`) is only needed when the dependency would form a cycle, and it would not here |
| D8 ✅ | Malformed ref | **400 `COMMON_400_002`** through Bean Validation (`@Pattern` on the path variable, using the domain's pattern constant) | "All external input goes through Bean Validation." The format is public, so a 400 reveals nothing. The use case also treats a malformed ref as not found, for callers other than the controller |
| D9 ✅ | Caching | **`Cache-Control: no-store`** on every response, set before the use case runs so 404 and 429 carry it too. No Redis cache for the lookup | CloudFront `/api/*` already uses `CachingDisabled`; `no-store` also stops browsers keeping it. Caching the lookup is #276 |

## 4. Design

### 4.1 HTTP contract (additive)

```
GET /api/v1/invites/{ref}          anonymous (no session needed); tag "invites"
  path  ref   string, pattern ^[A-Za-z0-9_-]{22}$

200  Result<InviteResponse>        Cache-Control: no-store
     { "dealId": uuid, "productId": uuid, "sharerNickname": string | null, "expiresAt": date-time }
400  COMMON_400_002                malformed ref
404  GROUPBUY_404_002              unknown or deleted ref, or its bloc no longer exists
429  COMMON_429_001                more than 60 requests in the current minute from this client address
```

- `sharerNickname`: `@Schema(nullable = true, description = "The sharer's nickname for display; null when there is none to show. Unverified display text, never an identifier")`. It is a scalar, so swagger-core renders `type: [string, "null"]`, and hey-api generates `string | null` (the #267 `$ref` problem does not apply).
- `expiresAt`: the bloc's own deadline, the same value `POST /group-deals/{dealId}/invites` returns.
- The bloc status is **not** in the response (non-goal of #201; the landing already reads it from the deal).
- `@Operation` description states D1: any existing invite resolves, whatever the bloc status.
- `openapi-diff` sees one new path, one new schema: backward compatible, no `contract-change-approved` label.

### 4.2 Domain (`blocbuy-domain`)

1. `groupbuy.model.ShareInvite`
   - `public static final String REF_PATTERN = "[A-Za-z0-9_-]{22}"`; the private `Pattern` compiles from it.
   - `public static boolean isWellFormedRef(String ref)`: null-safe match.
2. `groupbuy.exception.GroupbuyErrorCode.INVITE_NOT_FOUND("GROUPBUY_404_002", 404, "Invite not found", "error.groupbuy.inviteNotFound")`.
3. `common.gateway.RateLimitGateway` (new, framework-free):

   ```java
   public interface RateLimitGateway {
       /** true when this request fits in the current window; false when over the limit. */
       boolean tryAcquire(RateLimit limit, String subject);

       /** scope: lowercase kebab-case; maxRequests >= 1; window >= 1 s, whole seconds. */
       record RateLimit(String scope, int maxRequests, Duration window) { /* validating ctor */ }
   }
   ```
4. `ArchitectureTest`: add `com.blocbuy.common.gateway..` to the `domain_is_framework_free` package list, so the new port stays JDK-only. One-line change, driven by the new package.

### 4.3 Application (`blocbuy-application`)

1. `identity.service.UserAppService.publicNickname(UUID userId)` → `Optional<String>`; `UserAppServiceImpl` (`@Transactional(readOnly = true)`):
   - `users.findById(userId)`; empty when absent.
   - The rule is a domain method `User.publicNickname()` returning `Optional<String>`, so it is unit-tested without Spring; the service only loads and delegates.
   - `User.publicNickname()`: empty when `status != ACTIVE`, when the nickname is null or blank, or when it equals the local part of the user's email, ignoring case (D3). Otherwise the stored nickname, unchanged.
2. `groupbuy.query.ResolvedInviteView(UUID dealId, UUID productId, String sharerNickname, Instant expiresAt)`.
3. `groupbuy.service.ShareInviteAppService.resolve(String ref, String clientAddress)`:

   ```
   if (!rateLimits.tryAcquire(RESOLVE_LIMIT, clientAddress)) throw BizException(RATE_LIMITED)
   if (!ShareInvite.isWellFormedRef(ref))                    throw BizException(INVITE_NOT_FOUND)
   invite = invites.findByRef(ref)          orElseThrow INVITE_NOT_FOUND
   deal   = deals.findById(invite.dealId()) orElseThrow INVITE_NOT_FOUND
   name   = users.publicNickname(invite.sharerUserId()).orElse(null)
   return ResolvedInviteView(deal.id(), deal.productId(), name, deal.expireTime())
   ```

   - `RESOLVE_LIMIT = new RateLimit("invite-resolve", 60, Duration.ofMinutes(1))`, a constant (D4). A property is not needed until #274 wants tuning.
   - Three reads, each a single indexed lookup; no loop, no N+1.
   - No `@Transactional` (D6). New constructor dependencies: `RateLimitGateway`, `UserAppService`.

### 4.4 Adapter (`blocbuy-adapter`)

1. `groupbuy.web.InviteController` (new): `@RestController`, `@Validated`, `@RequestMapping(ApiConstants.API_V1 + "/invites")`, `@Tag(name = "invites")`.

   ```java
   @GetMapping("/{ref}")
   public Result<InviteResponse> resolve(
           @PathVariable @Pattern(regexp = ShareInvite.REF_PATTERN) String ref,
           HttpServletRequest request, HttpServletResponse response) {
       response.setHeader("Cache-Control", "no-store");
       ResolvedInviteView view = invites.resolve(ref, clientAddresses.resolve(request));
       return Result.ok(new InviteResponse(...));
   }
   ```

   `InviteResponse` is a nested record in the controller, like `ShareInviteResponse`.
2. `common.web.ClientAddressResolver` (new `@Component`): reads the header named by `blocbuy.web.client-address-header`.
   - When the property is set and the header is present, take its value and strip the trailing `:port` (after the last `:`; this covers both IPv4 `198.51.100.10:46532` and IPv6 `2001:db8::1:46532`).
   - Otherwise use `request.getRemoteAddr()`.
   - Never logs the address.
3. `common.config.SaTokenConfig` (infrastructure module, but web wiring): add `ApiConstants.API_V1 + "/invites/*"` to `ANONYMOUS_GET_PATHS`. Any other method on `/invites/**` still requires login.

### 4.5 Infrastructure (`blocbuy-infrastructure`)

`common.gateway.RedisRateLimitGateway implements RateLimitGateway` (`@Component`, `@Slf4j`):

- **Key:** `RedisKeys.key(env, "common", "rate-limit", scope + ":" + digest(subject) + ":" + windowIndex)`, where `windowIndex = epochSecond / windowSeconds` and `digest` is the first 16 hex chars of SHA-256. Env comes from `spring.profiles.active`, as in `RedisOAuthStateStore`. The digest keeps raw client addresses out of key names (`SCAN`/`MONITOR` output). It is pseudonymisation, not anonymisation: the IPv4 space is small enough to reverse.
- **Script** (atomic, never leaves a key without a TTL):

  ```lua
  local n = redis.call('INCR', KEYS[1])
  if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
  return n
  ```

  Run through `DefaultRedisScript<Long>`; allowed when `n <= maxRequests`.
- **Owner / TTL / cardinality:** owner `common` (rate limiting); TTL = one window (60 s); at most one key per distinct client address per window, gone one window later.
- **Failure policy:** catch `DataAccessException`, log WARN with the scope only (no subject), return `true` (fail-open, D4).

Configuration:

- `application-ec2.yml`: `blocbuy.web.client-address-header: CloudFront-Viewer-Address`.
- `local` and `docker` profiles: unset, so `getRemoteAddr()` is used.

No migration and no SQL change.

## 5. Tests

| ID | Layer / class | Cases |
| --- | --- | --- |
| DOM-1 | `ShareInviteTest` | `isWellFormedRef`: minted ref true; 21 and 23 chars, `+`, `/`, `=`, blank, null false |
| DOM-2 | `RateLimitTest` (domain) | valid record; blank or upper-case scope, `maxRequests < 1`, null, sub-second or fractional window rejected |
| DOM-3 | `UserTest` | `publicNickname`: "Sarah Tan" → "Sarah Tan" (unchanged); equals the email local part → empty, also with different case; contains the local part but is not equal ("sarah.tan93 fan") → unchanged; null or blank → empty; `DISABLED` → empty |
| APP-1 | `UserAppServiceImplTest` | delegates to the aggregate; unknown user → empty (no exception) |
| APP-2 | `ShareInviteAppServiceTest` | over the limit → `RATE_LIMITED` and no repository or identity call |
| APP-3 | same | malformed ref → `INVITE_NOT_FOUND`, no repository call |
| APP-4 | same | unknown ref → `INVITE_NOT_FOUND` |
| APP-5 | same | invite whose deal is missing → `INVITE_NOT_FOUND` |
| APP-6 | same | open deal → view with `dealId`, `productId`, nickname, `expireTime` |
| APP-7 | same | `SUCCEEDED`, `FAILED`, `EXPIRED` and past-deadline `OPEN` deals still resolve (D1) |
| APP-8 | same | identity returns empty → `sharerNickname` null |
| RL-1 | `RedisRateLimitGatewayIT` (Testcontainers `redis:8.10`) | 60 acquisitions true, the 61st false |
| RL-2 | same | key TTL is > 0 and ≤ the window after the first hit (no key without a TTL) |
| RL-3 | same | two subjects count independently; key name does not contain the raw subject |
| RL-4 | same | a 1 s window resets once its key expires (poll the key TTL, bounded at 3 s; no fixed sleep) |
| RL-5 | `RedisRateLimitGatewayTest` (mocked template) | `RedisConnectionFailureException` → true (fail-open), WARN logged without the subject |
| WEB-1 | `InviteControllerTest` (`@SpringBootTest` + MockMvc, use case mocked, like `ShareInviteControllerTest`) | anonymous GET without a cookie → 200 |
| WEB-2 | same | `data` has exactly the keys `dealId`, `productId`, `sharerNickname`, `expiresAt`; null nickname serialised as `null` |
| WEB-3 | same | `Cache-Control: no-store` on 200, 404 and 429 |
| WEB-4 | same | use case throws `INVITE_NOT_FOUND` → 404 `GROUPBUY_404_002` envelope |
| WEB-5 | same | use case throws `RATE_LIMITED` → 429 `COMMON_429_001` envelope |
| WEB-6 | same | malformed ref → 400 `COMMON_400_002`; use case not called |
| WEB-7 | same | `POST /api/v1/invites/{ref}` without a session → 401 (only GET is anonymous) |
| WEB-8 | same, with `blocbuy.web.client-address-header=CloudFront-Viewer-Address` | the use case receives `198.51.100.10` from `198.51.100.10:46532`; without the header it receives `remoteAddr` |
| WEB-9 | `ClientAddressResolverTest` | IPv4 with port; IPv6 with port; property unset; header blank → `remoteAddr` |
| ARCH | `ArchitectureTest` | green, including the extended framework-free rule |

Coverage: after `./mvnw -B verify`, the aggregate report shows no missed line or branch in any touched file (backend skill pre-commit script).

## 6. Steps (≈ 5 h)

| # | Step | h |
| --- | --- | --- |
| 1 | Post D0–D9 on #201 | 0.1 |
| 2 | Domain: `REF_PATTERN`, `isWellFormedRef`, `INVITE_NOT_FOUND`, `RateLimitGateway` + `RateLimit`, `User.publicNickname`, ArchUnit line; DOM-1..3 | 0.6 |
| 3 | Identity: `UserAppService.publicNickname`; APP-1 | 0.2 |
| 4 | Infrastructure: `RedisRateLimitGateway` + script; RL-1..5 | 1.2 |
| 5 | Application: `ResolvedInviteView`, `ShareInviteAppService.resolve`; APP-2..8 | 0.6 |
| 6 | Adapter: `InviteController`, `ClientAddressResolver`, `SaTokenConfig`, `application-ec2.yml`; WEB-1..9 | 1.0 |
| 7 | Contract: regenerate with `-Popenapi-export`; `git diff --exit-code -- contracts/` after a second run; `./mvnw -B verify` on JDK 21; coverage script | 0.5 |
| 8 | Docs and evidence (§7) | 0.4 |
| 9 | After merge and deploy: dev checks DEV-1..4; hand over to #230 | 0.4 |

## 7. PR, documentation and evidence

- **One PR**, about 20 files (well under the 50-file limit): `feat(groupbuy): resolve an invite ref to the sharer's nickname (#201)`, body `Closes #201`, reviewer @limjiechao. The contract, code, tests and docs ship together.
- `docs/agile/sprint-02/share-attribution-plan-20260924.md` D5: mark it built by #201 and link this plan.
- `docs/quality/nfr-register.md`: new **NFR-46**, security and privacy: "An anonymous invite resolve returns only the sharer's nickname (none for inactive accounts or email-derived nicknames) and three identifiers, at most 60 per minute per viewer address, never cached (`no-store`)". Evidence: WEB-2, WEB-3, RL-1 and the DEV-3 output.
- This plan: status banner updated to "implemented", with departures listed, as in the US25 plan.
- `docs/agile/sprint-03/log/2026-09-30.md`: day-log entry (agile-evidence-capture skill).
- After merge: comment on #230 that the client can be regenerated; tick US15b API in the Sprint 3 plan.
- No ADR: no new architectural pattern beyond a port and its Redis adapter. The decisions live here and on #201. If #274 changes the limiter's shape, it amends this plan or opens an ADR then.

## 8. Verification checklist

Before the PR:

- [ ] `./mvnw -B verify` green on JDK 21 (Spotless, Checkstyle, SpotBugs, ArchUnit, unit tests and ITs)
- [ ] Aggregate JaCoCo: no missed lines or branches in touched files
- [ ] `contracts/openapi.json` regenerated; `git diff --exit-code -- contracts/` clean after a second export
- [ ] `openapi-diff` reports backward compatible
- [ ] `git grep` shows no email, user id or raw IP in any new log call or response

On dev, after deploy (record the output in the day log):

- [ ] **DEV-1** `curl -sS https://d1b3np88z657cp.cloudfront.net/api/v1/invites/<real ref>` → 200 with the sharer's nickname; `-D -` shows `cache-control: no-store`
- [ ] **DEV-2** random well-formed ref → 404 `GROUPBUY_404_002`; malformed → 400
- [ ] **DEV-3** 61 requests within a minute from one machine → the 61st is 429 `COMMON_429_001`; from a second network → 200. This proves `CloudFront-Viewer-Address` reaches the backend (if it did not, the key would fall back to `getRemoteAddr()` and the result is not trustworthy; check the next item)
- [ ] **DEV-4** repeat DEV-3 sending a different `X-Forwarded-For` and a forged `CloudFront-Viewer-Address: 203.0.113.<n>:1` on every request → still 429 after 60. If this passes the limit, the viewer header is not trusted; open the custom origin request policy PR (R1)

## 9. Risks

| # | Risk | Mitigation |
| --- | --- | --- |
| R1 | The managed `AllViewerExceptHostHeader` policy may not forward `CloudFront-Viewer-Address`, or may pass a viewer-forged copy; the limit is then per spoofable address | DEV-3 and DEV-4 detect it. The fix is a custom origin request policy in `infra/terraform/frontend-stack/cloudfront.tf` (separate PR, deployment-operations skill). The wider problem (every `getRemoteAddr()` on dev is spoofable via `X-Forwarded-For`) is a separate task |
| R2 | Many users behind one NAT (a classroom demo) share one address | 60/min per address, one request per landing load; the constant is easy to raise |
| R3 | Redis outage turns the limit off (fail-open) | Enumeration still faces 2^128 refs; WARN log makes the outage visible |
| R4 | The nickname is often a full real name, visible to anyone holding the link | The sharer chose to share the link, usually with people who already know them; refs are unguessable and the endpoint is rate-limited. Disabled accounts and email-derived nicknames show nothing (D3). If a first-name-only display is wanted later, it is a change to `User.publicNickname` alone |
| R7 | A long nickname overflows the banner | No server-side cut (D3); #230 truncates with CSS |
| R5 | `ShareInviteAppService` gains two dependencies and a second use case | Both use cases concern the same invite; split into `InviteQueryService` if #276 adds caching |
| R6 | Frontend treats `sharerNickname` as trusted | Contract description says display text only; React escapes text by default |

## 10. Resolved questions

1. D3 (2026-09-30): the stored nickname unchanged, `null` for inactive accounts and email-derived nicknames. First-word-only was rejected because it breaks names written family name first.
