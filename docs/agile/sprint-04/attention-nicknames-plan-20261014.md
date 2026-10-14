# #469 Admin attention buyer nicknames through the identity port (step B2) — implementation plan

Date: 2026-10-14 · Branch: `refactor/469-attention-nicknames-identity-port` from `dev` at `439edcfe` (#488 merged, #468 merged as #479) · Issue: #469 (Sprint 4 container #465) · Owners: @Yorick-Yu (scope 1–3) and @l0sgAi (batch endpoint and remote side), pairing on one branch · Reviewer: @PPsyrius · Related: step B2 of the [microservice split plan](microservice-split-plan-20261012.md), [remote identity plan](remote-identity-plan-20261013.md) §3 and §7 Q3, [backend ADR 0006](../../../backend/docs/adr/0006-payment-attention-read-model.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md), NFR-47

> **Status (2026-10-14): reviewed (all §10 defaults accepted) and implemented on `refactor/469-attention-nicknames-identity-port`; T10 needed no edit. Results: [day log](log/2026-10-14.md).**

## 1. Goal and acceptance criteria

The admin "payments needing attention" page (BA-8, #252) must work unchanged on the `dev` monolith and on `commerce-svc`, through one code path. Today `PaymentAttentionMapper.xml` joins `sys_user` to build `buyer_nickname`. `commerce-svc`'s runtime role has no grant on `sys_user` (#455, `afterMigrate__service_grants.sql`), so the page fails in the distributed shape. The 10-16 ECS session needs the page working on `commerce-svc`.

The issue comment of 2026-10-14 split the work into two PRs, one per owner. Because @Yorick-Yu and @l0sgAi are pairing, this plan delivers both parts in **one PR** on one branch, with every commit co-authored (Q5). That removes the "whichever merges second rebases" step and the compile hazard between the two branches.

| # | Criterion (issue text, then plan detail) | Delivered by |
| --- | --- | --- |
| AC1 | PostgreSQL IT: the attention list returns the same nicknames and "Unknown buyer" cases as before the change (fixture covering email-like and email-local-part nicknames) | T1. The new cases land first, green against today's SQL, then stay green after the refactor. One deliberate difference: a disabled buyer (Q1) |
| AC2 | One batch lookup per page, proven by a test | T2 (unit, Mockito `times(1)`) and T1 (IT, exactly one `sys_user` batch statement for page size 1 and 20) |
| AC3 | No remaining SQL in non-identity mappers references `sys_user`, `sys_role` or `rel_*` (`grep` result recorded) | V3 grep in the day log, plus a durable guard test T9 (Q4) |
| AC4 | `./mvnw verify` green; `contracts/openapi.json` unchanged | V1, V2 |
| AC5 (comment) | `POST /internal/users/public-nicknames` answers 400 when `userIds` is null, empty or larger than 100; wire records `PublicNicknamesQuery` / `PublicNicknamesView` | T6 |
| AC6 (comment) | `RemoteUserAppService.publicNicknames`: an empty set returns at once without a call; larger sets are split into calls of at most 100; anything other than 200 → `IdentityUnavailableException`; WireMock cases; the batch case in `UserAppServiceContractTest` | T7, T8 |

Non-goals (issue): changing the admin payments UI or its contract. Also out of scope: caching nicknames, retries or circuit breakers (remote identity plan §8), moving the identity call out of the read-only transaction (§8 here).

## 2. Baseline (read from `439edcfe`)

| Area | Today | Gap |
| --- | --- | --- |
| Attention SQL | `PaymentAttentionMapper.pageAttention` pages the population in a CTE, then `LEFT JOIN sys_user buyer ON buyer.id = attention.user_id AND NOT buyer.deleted` and computes `buyer_nickname` in a `CASE` (rule below). `countAttention` does not touch `sys_user` | Drop the join and the `CASE`; select `payment.user_id AS buyer_id` |
| Projection | `PaymentAttention` (domain, `com.blocbuy.payment.model`) carries `buyerNickname`; its Javadoc says no buyer identifier is selected. `AdminPaymentController.PaymentAttentionResponse.from(PaymentAttention)` copies it | The query must carry the buyer id to the application, and the HTTP response must still not carry it |
| Application | `AdminPaymentAppService.pageAttention`: `@Transactional(readOnly, REPEATABLE_READ)`, `users.requireActive(actor)`, `attention.page(page)`. No unit test | Enrich the page with one `publicNicknames` call; add a unit test |
| Port | `UserAppService`: `requireActive`, `currentUser`, `publicNickname(UUID)`. No batch method | Add `Map<UUID, String> publicNicknames(Set<UUID> userIds)` (signature agreed on #469) |
| Domain rule | `User.publicNickname()`: empty when not `ACTIVE`, nickname null or blank, or `nickname.equalsIgnoreCase(emailLocalPart(email))` | Missing two rules the SQL has (contains `@`; compare after trimming) |
| Repository | `UserRepository.findById` only; `SysUserMapper.selectActiveById` | No batch read: add `findAllById(Set<UUID>)` → one `IN (...)` select |
| Internal endpoint | `InternalUserController` (`@Hidden`, distributed only): `GET /{id}/active`, `GET /{id}/public-nickname`, wire record `PublicNicknameView` | Add `POST /public-nicknames` and two records |
| Remote adapter | `RemoteUserAppService` (commerce-svc): GET-only private `exchange(operation, path, userId, type)`; non-200 or unreadable → `IdentityUnavailableException` | Add a POST path and chunking |
| Contract tests | `UserAppServiceContractTest` runs one case set against local and remote (MockMvc transport into the real controller); `RemoteUserAppServiceTest` (WireMock); `InternalUserControllerTest` | Batch cases in all three |
| IT | `AdminPaymentFlowIT` (Testcontainers PostgreSQL 18, real migrations, `SqlStatementCapture`): nickname shown; `""`, `" "`, `"private"`, `"PRIVATE"`, `"private@example.com"` → Unknown buyer (buyer email `private@example.com`); deleted buyer → Unknown buyer; fixed statement count for size 1 vs 20 | Add the cases the rule move could break (§4.1) and the batch statement assertion |
| Other test using the type | `fixtures/rbac/DistributedRbacTest` mocks `payments.pageAttention(...)` returning `PageResult<PaymentAttention>` | Follows the new return type |
| Other `publicNickname` caller | `ShareInviteAppService.resolve` (public invite landing page) | Inherits the stricter rule (Q2); its tests mock the port and do not change |
| Mapper XML touching identity tables | `grep -rlE "sys_user\|sys_role\|rel_" backend/*/src/main/resources/mapper`: `identity/SysUserMapper.xml`, `identity/SysUserOAuthMapper.xml`, `identity/RbacMapper.xml`, **`payment/PaymentAttentionMapper.xml`** | Only the identity three may remain |
| Records citing the join | ADR 0006 ("two database reads", "the query selects only the buyer's nickname"), ADR 0008 Context (coupling list), NFR-47 ("snapshot uses 2 queries"), `docs/swe5001/impl-backend-architecture.md` line 189 ("#469 records a `grep`"), HANDOFF B2 row "Not started" | Amend in the same PR (§6, D-1…D-7) |

### 2.1 Today's masking rule, side by side

| Case | SQL (`PaymentAttentionMapper.xml`) | `User.publicNickname()` | After this PR (`User.publicNickname()`) |
| --- | --- | --- | --- |
| Row missing or soft-deleted | Unknown buyer (`LEFT JOIN ... AND NOT buyer.deleted`) | `findById` filters deleted → empty | Absent from the map → Unknown buyer |
| Nickname null, `''` or blank | Unknown buyer (`NULLIF(trim(nickname), '')`) | Empty (`isBlank`) | Empty |
| Nickname contains `@` (for example `someone@else.com`) | Unknown buyer (`strpos(nickname, '@') = 0`) | **Shown** | Empty (rule added) |
| Nickname equals the email local part, any case | Unknown buyer | Empty | Empty |
| Nickname equals the local part with surrounding spaces (`" private "`) | Unknown buyer (`lower(trim(nickname))`) | **Shown** | Empty (compare after `strip()`) |
| Email null | Nickname shown | Shown | Shown |
| Account disabled | **Nickname shown** (no status check) | Empty | Empty → Unknown buyer (**behaviour change, Q1**) |
| Showable nickname | The stored value, untrimmed | The stored value | The stored value, untrimmed |

So moving the rule into `User` makes it the strict union of the two: the two SQL-only rules join the domain, and the domain's "active only" rule now also applies to the admin page.

## 3. Wire contract (identity side, distributed only)

| Method and path | Request | Success | Errors (status, `code`) |
| --- | --- | --- | --- |
| `POST /internal/users/public-nicknames` | `PublicNicknamesQuery {"userIds": [uuid, ...]}` | 200 `Result<PublicNicknamesView>`, `{"nicknames": {"<uuid>": "<nickname>", ...}}` holding only users with a showable nickname; unknown, deleted, inactive and masked ids are absent | 400 `COMMON_400_*` when the body is missing or unreadable, `userIds` is null, empty, larger than 100 (`PageQuery.MAX_SIZE`) or holds a null or malformed UUID |

- `PublicNicknamesQuery(@NotEmpty @Size(max = MAX_BATCH) Set<@NotNull UUID> userIds)` and `PublicNicknamesView(Map<UUID, String> nicknames)` sit next to `PublicNicknameView` in `InternalUserController`. The remote adapter in commerce-svc serialises and deserialises the same records, as it already does for `PublicNicknameView`.
- `InternalUserController.PUBLIC_NICKNAMES = "/public-nicknames"` and `InternalUserController.MAX_BATCH = PageQuery.MAX_SIZE` are public constants. The client chunks by the constant the server validates, so the two cannot drift.
- Validation is `@RequestBody @Valid`, so failures arrive as `MethodArgumentNotValidException` / `HttpMessageNotReadableException`, which `GlobalExceptionHandler` already answers as 400 in the standard envelope. The controller sets no status itself.
- POST, not GET with 100 query parameters: a URL with 100 UUIDs is about 3.7 KB, near proxy limits, and the body keeps ids out of access logs.

## 4. Design

### 4.1 Domain: one masking rule in `User` (@Yorick-Yu)

```java
public Optional<String> publicNickname() {
    if (status != UserStatus.ACTIVE || nickname == null || nickname.isBlank()) {
        return Optional.empty();
    }
    if (nickname.indexOf('@') >= 0) {                       // email-shaped
        return Optional.empty();
    }
    if (email != null && nickname.strip().equalsIgnoreCase(emailLocalPart(email))) {
        return Optional.empty();                            // OAuth fallback, padded or not
    }
    return Optional.of(nickname);
}
```

The Javadoc names the three hidden cases and the consumers that rely on them (invite landing page, admin attention page). The SQL `CASE` disappears, so this method is the only copy of the rule in the code base, used by `UserAppServiceImpl` in the monolith and in `identity-svc`, and so by `commerce-svc` over HTTP.

### 4.2 Port and local implementation (@Yorick-Yu)

- `UserAppService` gets the agreed method with the agreed Javadoc: `/** Showable public nicknames by user id; ids without one are absent. Never throws for unknown or inactive users. */ Map<UUID, String> publicNicknames(Set<UUID> userIds);`
- `UserRepository.findAllById(Set<UUID> ids)` → `List<User>`, active (non-deleted) rows only. `UserRepositoryImpl` returns `List.of()` for an empty set **without** calling the mapper (an empty `IN ()` is invalid SQL). Otherwise one call to the new `SysUserMapper.selectActiveByIds(@Param("ids") Collection<UUID> ids)`: the same column list as `selectActiveById`, `WHERE id IN <foreach ... #{id,jdbcType=OTHER}> AND deleted = false`, the pattern `BizProductMapper.xml` and `TeamPriceMapper.xml` already use.
- `UserAppServiceImpl.publicNicknames` (`@Transactional(readOnly = true)`): empty set → `Map.of()`; otherwise `users.findAllById(ids)`, keep `User::publicNickname` hits, collect into an unmodifiable map. One SQL statement per call, any size; the only caller sends at most 100 ids.

### 4.3 Payment: query returns ids, application enriches (@Yorick-Yu)

- **Mapper.** `pageAttention` selects `attention.user_id AS buyer_id` instead of the `CASE`, and the `sys_user` join goes. The `biz_group_deal` join stays (commerce owns it). `countAttention` is unchanged.
- **Query projection.** The domain record `PaymentAttention` replaces `String buyerNickname` with `UUID buyerId`. Javadoc: payment-owned row; the buyer id is for the identity lookup and is never returned over HTTP.
- **Application view.** The app service returns a new application record, `AdminPaymentAppService.AttentionItem(paymentId, buyerNickname, dealId, dealNo, reasonCode, amount, currency, refundStatus, occurredAt)`. That is the HTTP field set exactly, with no buyer id. This follows the `UserAppService.CurrentUser` pattern of nested view records (Q3).
- **Enrichment.**

```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public PageResult<AttentionItem> pageAttention(PageQuery page, UUID actorId) {
    users.requireActive(actorId);
    PageResult<PaymentAttention> rows = attention.page(page);
    Set<UUID> buyers = rows.items().stream().map(PaymentAttention::buyerId).collect(toSet());
    Map<UUID, String> nicknames = users.publicNicknames(buyers);          // exactly one call per page
    List<AttentionItem> items = rows.items().stream()
            .map(row -> AttentionItem.of(row, nicknames.getOrDefault(row.buyerId(), UNKNOWN_BUYER)))
            .toList();
    return new PageResult<>(items, rows.total(), rows.page(), rows.size(), rows.nextCursor());
}
```

- `UNKNOWN_BUYER = "Unknown buyer"` becomes a constant in the app service; the SQL literal goes.
- `PageResult` has no `map` method; the rebuild follows the controller's existing pattern, and `PageResult` itself does not change.
- The port is always called exactly once, even for an empty page (Q6). The local and remote implementations both short-circuit an empty set without I/O, so "one batch lookup per page" holds without a branch.
- **Controller.** `PaymentAttentionResponse.from(AttentionItem)`; the field list, the annotations and the `@Operation` text are unchanged, so `contracts/openapi.json` is byte-identical (V2).
- **Snapshot.** In the monolith the batch read joins the same REPEATABLE READ snapshot as count and page, as the class Javadoc promises. In `commerce-svc` the nicknames come from identity's database at call time. A nickname edited between the page read and the call can show its newer value, which is harmless for a display-only column. The Javadoc says so.

### 4.4 Identity endpoint (@l0sgAi)

`InternalUserController` gets the method below, plus the two records and two constants of §3:

```java
@PostMapping(PUBLIC_NICKNAMES)
public Result<PublicNicknamesView> publicNicknames(@RequestBody @Valid PublicNicknamesQuery query) {
    return Result.ok(new PublicNicknamesView(users.publicNicknames(query.userIds())));
}
```

The controller Javadoc lists the third endpoint and says it is always 200 for a valid request. No change to `SaTokenConfig` (`/internal/**` already public), the ALB rules or the ArchUnit internal-controller rules, which the controller already satisfies.

### 4.5 Remote adapter (@l0sgAi)

- `RemoteUserAppService.publicNicknames(Set<UUID> ids)`:
  - an empty set returns `Map.of()` with no call;
  - otherwise copy the ids into a list, take `MAX_BATCH`-sized slices, and POST each slice;
  - each reply must be 200 + `SUCCESS` + non-null `data` + non-null `nicknames`, or the result is `unexpected("publicNicknames", reply)` → `IdentityUnavailableException`;
  - merge the slices into one unmodifiable map;
  - a failed slice fails the whole call, so partial results are never returned.
- The private `exchange` is generalised so that GET and POST share the timing, `answersOnPurpose`, the body read and the transport-failure mapping. Each caller supplies a `RestClient.RequestHeadersSpec<?>` (GET with URI variables, or POST with a JSON body); the rest stays as it is. The two GET operations keep exactly their current behaviour, and the existing tests pin it.
- The class Javadoc adds the batch rule: no retry, no cache, and a failed slice is an outage for the whole lookup.

### 4.6 Failure behaviour on the attention page

| What happens | Monolith / identity-svc | commerce-svc |
| --- | --- | --- |
| Identity answers | Page as today | Page as today |
| Identity unavailable, slow (> 1.5 s read timeout) or answers non-200 | n/a (local call) | `requireActive` fails first, so 500 `INTERNAL_ERROR` before the batch. If identity fails between the two calls, the batch raises `IdentityUnavailableException` → 500. Fail closed, consistent with ADR 0008; no degraded "Unknown buyer" page that could hide an outage |
| Buyer deleted, disabled or masked | Unknown buyer, financial row kept | Same |

### 4.7 What does not change

`contracts/openapi.json` (V2), the frontend, `AdminPaymentController`'s route, guards and `@Operation` text, `countAttention`, `PaymentAttentionQueryImpl`'s two reads, `ShareInviteAppService` code, `GlobalExceptionHandler`, `SaTokenConfig`, `afterMigrate__service_grants.sql`, Terraform, migrations (no DDL: `sys_user` is read by primary key, `biz_payment.user_id` is already selected).

## 5. Tests

| Id | Test (module) | Proves | Owner |
| --- | --- | --- | --- |
| T1 | `AdminPaymentFlowIT` (bootstrap, PostgreSQL). **Step 1, before any production change, green on today's SQL:** extend `blankAndEmailDerivedNicknamesNeverLeakContactInformation` with `"someone@else.com"` (email-shaped, not the buyer's), `" private "` and `"Private "` (padded local part); add `twoBuyersOnOnePageEachKeepTheirOwnNickname` (a second buyer with a showable nickname, rows interleaved). **Step 2, with the refactor:** add `disabledBuyerUsesFallbackWithoutDroppingFinancialRow` (Q1); extend `pageHasFixedQueryCountAndDoesNotWriteFinancialState` to assert, for page size 1 and 20 with two distinct buyers, exactly one captured statement selecting `sys_user` by an id list, and that no statement touching `biz_group_participation_receipt` mentions `sys_user` | AC1, AC2 (local topology, real SQL); the existing cases (`Tea buyer`, blank, local part any case, deleted buyer, no email/user id in the body) carry over unchanged | @Yorick-Yu |
| T2 | `AdminPaymentAppServiceTest` (new, application, Mockito): `publicNicknames` called exactly once with the distinct buyer set (3 rows, 2 buyers); an absent id → `Unknown buyer`; empty page → one call with an empty set, empty items; `requireActive` refusal → no query and no lookup; order, totals and paging fields preserved | AC2 at unit level; coverage of the new code | @Yorick-Yu |
| T3 | `UserTest` (domain): `@` in the nickname hidden (`"sarah@example.com"`, `"a@b"`, `"@sarah"`); padded local part hidden (`" sarah.tan93 "`, `"SARAH.TAN93\t"`); a showable nickname is returned **untrimmed**; the existing cases unchanged | §4.1 rule, branch coverage | @Yorick-Yu |
| T4 | `UserAppServiceImplTest` (application): `publicNicknames` with active, disabled, masked and unknown ids → only the active showable one; empty set → `Map.of()` and no repository call; one `findAllById` call for many ids | §4.2 | @Yorick-Yu |
| T5 | `UserRepositoryImplTest` (infrastructure, mocked mapper as today): `findAllById` converts every row; empty set → no mapper call. The SQL itself runs in T1 against PostgreSQL | §4.2 | @Yorick-Yu |
| T6 | `InternalUserControllerTest` (bootstrap, standalone MockMvc + `GlobalExceptionHandler`): 200 with the map (absent ids absent); exactly 100 ids → 200; 0 ids, 101 ids, `{"userIds": null}`, `{}`, no body, `[null]` and `["not-a-uuid"]` → 400 `COMMON_400_*` and the port never called | AC5 | @l0sgAi |
| T7 | `RemoteUserAppServiceTest` (commerce-svc, WireMock): the request is a POST to `/internal/users/public-nicknames` with JSON `userIds`; the response map returned; empty set → zero requests (`IDENTITY.verify(0, ...)`); 150 ids → two requests of 100 and 50 whose union is the input, merged map; second slice 503 → `IdentityUnavailableException`; 400 / 500 / 503 → unavailable with `answered <status>`; 200 without `data` and 200 with `data.nicknames` null → unavailable; unreadable body → unavailable; `X-Trace-Id` travels on the POST. Existing GET cases unchanged | AC6 | @l0sgAi |
| T8 | `UserAppServiceContractTest` (commerce-svc): new contract cases run against local and remote. `{ACTIVE, DISABLED, EMAIL_PREFIX, UNKNOWN, EMAIL_SHAPED}` → exactly `{ACTIVE: "Tester"}`; empty set → empty map; plus a single `publicNickname` case for the new `EMAIL_SHAPED` user, so both methods agree | AC6; local = remote for the batch | @l0sgAi |
| T9 | `IdentityTablesStayInIdentityMappersTest` (new, infrastructure, plain JUnit): scans `classpath*:mapper/**/*.xml`; any file outside `mapper/identity/` whose SQL matches `\b(sys_user\|sys_user_oauth\|sys_role\|sys_permission\|rel_[a-z_]+)\b` fails, naming the file. The table list matches `identity_tables` in the grants callback | AC3 as a regression guard, closing the gap `impl-backend-architecture.md` line 189 notes (ArchUnit cannot see SQL) (Q4) | @Yorick-Yu |
| T10 | `DistributedRbacTest`: the mocked `pageAttention` returns `PageResult<AttentionItem>`; assertions unchanged. **As built:** no edit needed, the empty `PageResult` is inferred | Compile follow-up | @Yorick-Yu |
| T11 | Existing `ArchitectureTest` (`commerce_reaches_identity_only_through_user_port`, `internal_controllers_are_hidden_and_distributed_only`, persistence ownership), `InternalPathsContractTest`, `CommerceServiceApplicationIT`, `IdentityServiceApplicationIT` | No rule change needed; all stay green | both |

Coverage: T2–T6 and T9 count in the bootstrap JaCoCo aggregate; T7 and T8 in commerce-svc's report. New production code is about 120 lines, every branch exercised.

## 6. Changes (one PR, about 25 files, est. 700 lines including tests)

| Id | File | Change | Owner |
| --- | --- | --- | --- |
| P-1 | `blocbuy-domain/.../identity/model/User.java` | §4.1 rule and Javadoc | @Yorick-Yu |
| P-2 | `blocbuy-domain/.../identity/repository/UserRepository.java` | `findAllById(Set<UUID>)` | @Yorick-Yu |
| P-3 | `blocbuy-infrastructure/.../identity/persistence/SysUserMapper.java`, `mapper/identity/SysUserMapper.xml`, `UserRepositoryImpl.java` | `selectActiveByIds`; empty short-circuit | @Yorick-Yu |
| P-4 | `blocbuy-application/.../identity/service/UserAppService.java`, `UserAppServiceImpl.java` | Port method, local implementation | @Yorick-Yu |
| P-5 | `blocbuy-infrastructure/src/main/resources/mapper/payment/PaymentAttentionMapper.xml` | Drop the `sys_user` join and the `CASE`; `buyer_id` | @Yorick-Yu |
| P-6 | `blocbuy-domain/.../payment/model/PaymentAttention.java` | `buyerId` replaces `buyerNickname`; Javadoc | @Yorick-Yu |
| P-7 | `blocbuy-application/.../payment/service/AdminPaymentAppService.java` | §4.3 enrichment, `AttentionItem`, `UNKNOWN_BUYER` | @Yorick-Yu |
| P-8 | `blocbuy-adapter/.../payment/web/AdminPaymentController.java` | `from(AttentionItem)`; nothing visible changes | @Yorick-Yu |
| P-9 | `blocbuy-infrastructure/.../payment/persistence/PaymentAttentionQueryImpl.java`, `PaymentAttentionMapper.java` | Javadoc only ("no per-row enrichment" → enrichment is the app service's single batch) | @Yorick-Yu |
| I-1 | `blocbuy-adapter/.../identity/web/internal/InternalUserController.java` | §3, §4.4 | @l0sgAi |
| C-1 | `services/commerce-svc/.../identity/RemoteUserAppService.java` | §4.5 | @l0sgAi |
| T-1…T-10 | Tests of §5 (3 new classes, 7 extended) | | as §5 |
| D-1 | `backend/docs/adr/0006-payment-attention-read-model.md` | Dated amendment: nickname through `UserAppService.publicNicknames`, one batch per page, the rule lives in `User.publicNickname`, disabled → Unknown buyer, "two database reads" becomes two payment reads plus one identity lookup | @Yorick-Yu |
| D-2 | `backend/docs/adr/0008-dual-deployment-topology.md` | Dated amendment: the `PaymentAttentionMapper` coupling is removed; batch endpoint added; T9 guards SQL ownership | @l0sgAi |
| D-3 | `docs/quality/nfr-register.md` NFR-47 | "2 queries" → "2 payment queries + 1 batched identity lookup"; evidence link to this PR and the day log | @Yorick-Yu |
| D-4 | `docs/swe5001/impl-backend-architecture.md` (line 189 row, WP-B5 note) | The grep is recorded and T9 now enforces it; batch endpoint delivered | @l0sgAi |
| D-5 | `docs/swe5001/HANDOFF.md` B2 and B3 rows; `docs/agile/sprint-04/microservice-split-plan-20261012.md` B2 row; `remote-identity-plan-20261013.md` status line | Delivered by this PR | @l0sgAi |
| D-6 | this plan | Status line | @l0sgAi |
| D-7 | `docs/agile/sprint-04/log/2026-10-1x.md` (+ `log.md` index if a new day file) | **Two entries**, one per author (one entry per PR per author, `docs/agile/README.md`) | both |

### 6.1 Commit sequence (each commit builds and passes its own tests)

Every commit ends with `Co-authored-by: Yorick-Yu <158500056+Yorick-Yu@users.noreply.github.com>` (Yorick's GitHub noreply address, which GitHub always links to the account) and the Claude co-author line.

| # | Commit | Content |
| --- | --- | --- |
| 1 | `docs(agile): add #469 attention nickname plan` | This file |
| 2 | `test(payment): pin attention nicknames before the port refactor` | T1 step 1 only; green against today's SQL (proves AC1's "as before") |
| 3 | `feat(identity): hide email-shaped and padded nicknames in User` | P-1, T3 |
| 4 | `feat(identity): add batch public nickname lookup across topologies` | P-2…P-4, I-1, C-1, T4–T8. The port method, local and remote implementations, the endpoint and the contract test go in one commit, because commerce-svc does not compile while the interface has a method `RemoteUserAppService` lacks |
| 5 | `refactor(payment): resolve attention nicknames through identity port` | P-5…P-9, T1 step 2, T2, T9, T10 |
| 6 | `docs: record #469 nickname port in ADRs, NFR-47 and logs` | D-1…D-7, with V1–V3 results |

## 7. Order and dates

| When | Step |
| --- | --- |
| 10-14 | This plan reviewed (§10); commits 1–3 |
| 10-14 / 10-15 morning | Commits 4–5; local V1–V3 |
| 10-15 | PR opened (`Closes #469`), CI green (V4), review by @PPsyrius, merge into `dev` before the mid-sprint check; `dev` health `UP` (V5) |
| 10-16 | Admin attention page on `commerce-svc` in the ECS session (V6) |

No external dependency remains: #468 is merged (#479), and the batch part it deferred is in this PR.

## 8. Risks and limitations

| Risk | Mitigation or record |
| --- | --- |
| A disabled buyer's nickname disappears from the admin page (Q1) | Deliberate: one rule for every public display. The financial row stays, with the amount, bloc and refund status; ADR 0006 amendment and T1 record it. If the review rejects it, the fallback is a second, status-agnostic port method, which widens the remote contract |
| The invite landing page now hides `@`-containing and padded-local-part nicknames (Q2) | Privacy improvement; the page already renders a missing nickname (remote identity plan Q4). Noted in the day log |
| In `commerce-svc` the HTTP call runs inside the read-only REPEATABLE READ transaction and holds a pooled connection for up to about 2 s | Same accepted trade-off as `requireActive`, which already runs there (remote identity plan §8). Admin traffic is low. Moving both calls before the transaction is roadmap work |
| Nicknames in `commerce-svc` are not in the payment snapshot | Display-only; documented in the Javadoc and ADR 0006 amendment (§4.3) |
| `rel_[a-z_]+` in T9 also matches a future non-identity `rel_*` table | No such table exists; if commerce ever adds one, T9's list moves to the explicit identity table names. Accepted as a cheap, strict default |
| JSON map keys of type `UUID` | Jackson serialises and deserialises `Map<UUID, String>` keys natively; T7 and T8 exercise the real conversion |
| Merge conflict with other Sprint 4 work on `UserAppService` or `RemoteUserAppService` | Only #469 touches them now (#468 merged). Rebase on `dev` before opening the PR |

Rollback: revert the PR. There is no migration or data change. `dev` returns to the SQL join, and `commerce-svc` returns to a failing attention page, as before.

## 9. Verification and evidence

| # | Check | Where |
| --- | --- | --- |
| V1 | `cd backend && ./mvnw spotless:apply && ./mvnw -B verify` with Docker (JDK 21): all tests, `ArchitectureTest`, Checkstyle, SpotBugs, JaCoCo; test counts recorded | Local |
| V2 | `./mvnw -B verify -Popenapi-export` then `git diff --exit-code contracts/openapi.json` | Local |
| V3 | `grep -rnE '\b(sys_user\|sys_role\|sys_permission\|rel_[a-z_]+)\b' backend/*/src/main/resources/mapper \| grep -v '/mapper/identity/'` returns nothing; output and command recorded in the day log | Local |
| V4 | PR CI: `build-quality` (verify, Sonar new-code gate ≥ 80 %), `contract-check`, plus `swe5001-service-images` if triggered | GitHub |
| V5 | `dev` health `UP` after merge; the admin attention page loads in the `dev` UI for the seeded admin | @l0sgAi |
| V6 | Optional before merge, required on 10-16: three images locally or on ECS. The admin attention request through `commerce-svc` answers 200 with nicknames, and `identity-svc` logs one `POST /internal/users/public-nicknames` per page with the same trace id | Operator |

Evidence: two day-log entries (V1–V4 results, V3 grep output, the Q1 behaviour change); ADR 0006 and 0008 amendments; NFR-47 row; HANDOFF, split-plan and remote-identity-plan status rows.

## 10. Questions for the reviewer

| # | Question | Default |
| --- | --- | --- |
| Q1 | A **disabled** buyer shows "Unknown buyer" on the attention page (today: their nickname), because the admin page now uses identity's single `publicNickname` rule, which hides inactive accounts, as the agreed port Javadoc says ("inactive users" absent)? | Yes; amend ADR 0006 and pin it in T1 |
| Q2 | The two SQL-only rules (`@` in the nickname; compare after trimming) move into `User.publicNickname`, so the public invite page also hides such nicknames? | Yes |
| Q3 | Shape: the domain `PaymentAttention` carries `buyerId` (no nickname), and the app service returns a nested `AdminPaymentAppService.AttentionItem` with the HTTP field set (no buyer id), instead of one record holding both fields with a null nickname until enrichment? | Separate records |
| Q4 | Besides the grep the issue asks for (V3), add T9 as a permanent guard that no non-identity mapper XML names an identity table? | Yes |
| Q5 | One PR with both parts, all commits co-authored by @Yorick-Yu, instead of the two PRs in the 2026-10-14 issue comment. A short comment on #469 records the change | Yes |
| Q6 | `publicNicknames` is called exactly once per page, even for an empty page (both implementations skip I/O for an empty set), instead of skipping the call for an empty page? | Always once |
