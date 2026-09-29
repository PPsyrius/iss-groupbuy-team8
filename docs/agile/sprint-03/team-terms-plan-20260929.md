# US25 Admin sets team terms and the team price policy — backend implementation plan

Date: 2026-09-29 · Branch: `feature/admin-team-terms-20260929` · Issue: #221 (US25, UC9-ext) · Owner: los gai (backend) · Reviewer: @EricWang1358 · Frontend consumer: @limjiechao (admin console, later) · Related: [`liu-jin-task-plan.md` §3.1](liu-jin-task-plan.md), [Sprint 3 plan](plan.md), [backend ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md) ("Open"), NFR-29, [`admin-listings.md`](../../../backend/docs/design/admin-listings.md)

Budget: 12 h (Sprint 3 plan). Target: merged to `dev` with the regenerated contract by **2026-10-01**, so US34 can seed dev through the API and US13, US23 and US32 have startable listings.

> **Status (2026-09-29): decisions accepted, implemented on this branch.** D1 (one command with an optional `teamPrice`), D3 (no clearing in v1), D5 (terms editable on an on-sale listing), D7 (`log_product_team_terms`) and D11 (code constants) were confirmed by the owner. Departures from the text below:
>
> - **Specification scope (amended in review of #242).** `TeamTermsSpecification` checks size and lifetime only. Every price rule, including "team price below the original price" and the currency match, stays in `TeamPricePolicy`, reached through the delegated policy write (D8). A bad price therefore returns `CATALOG_400_001` without field errors, the same as the standalone endpoint, instead of a `teamPrice.amount` key in `CATALOG_400_003`. This supersedes the price part of D6 and §4.2.
> - **`ProductTeamTerms.configured` factory** was dropped: `Product.configureTeamTerms` is the only entry point, so a second factory would be unused code.
> - **`teamTerms` on `AdminListingResponse` is always present** (members null and `configured` false when never configured) instead of a nullable object, so clients need no null branch.
> - **`startable` renamed to `configured` (amended in review of #242)**, both `ProductTeamTerms.configured()` and the `teamTerms.configured` response field: it only means both terms are set and in bounds. START also needs an on-sale listing with stock and a price policy in force for the next 2400 s. `startable` in D13 and §4 below reads as `configured`.
> - **Response nulls (review of #242).** `teamTerms.targetSize` / `lifetimeSeconds` are marked nullable in the schema; `teamPrice` is omitted rather than null when no price was set, because the generator cannot express a nullable `$ref`.
> - **One PR, not two.** D9's PR A / PR B split did not happen; the plan, contract, code and docs shipped together in #242 (see §7).
> - **Extra test-schema change.** Two repository ITs that load a hand-picked migration list (`TeamPriceRepositoryIT`, `ProductSearchRepositoryIT`) now also load V14, because the shared product mapper selects the terms columns.
> - **Test placement.** The controller checks (UT-A*) live in `TeamTermsFlowIT` (real Bean Validation and RBAC over MockMvc) rather than a separate mocked controller test.

## 1. Goal and acceptance criteria

An administrator can make a listing startable: set how many paid members a team needs, how long it stays open, and (optionally, in the same call) the first-team price. After that, `ProductTeamTerms.startable()` is true and a START intent succeeds.

| # | Criterion (issue #221) | Delivered by |
| --- | --- | --- |
| AC1 | Admin-only Sa-Token RBAC API sets `team_target_size`, `team_lifetime_seconds` and the team price policy; non-admin 403, anonymous 401 | §4.1, §4.4; UT-W*, AUTH-1..4 |
| AC2 | Validation: size ≥ 2; lifetime in range; team price below individual price; invalid input 400 with field errors | §3 D5, D6; §4.2; UT-D*, IT-4 |
| AC3 | Stale `expectedVersion` → 412; writes audited | §3 D4, D7; §4.3; IT-2, IT-6 |
| AC4 | `startable()` true for configured listings; a START intent succeeds against one (integration test) | IT-8 |
| AC5 | `contracts/openapi.json` changes additively; the contract entry is merged or approved before the controller PR | §3 D9; §7 PR split; `contract-check` + `openapi-diff` green |
| AC6 | Unit tests, PostgreSQL IT, authorization tests; ArchUnit and `./mvnw -B verify` green; NFR-29 row updated | §5, §6, §8 |

Out of scope: an admin console UI; seeding dev data (US34, #222, runs after this merges); clearing terms (D3); snapshotting terms into quotes (#183 D12); stock or price editing (owned by `revise`).

## 2. Baseline (read from `dev` 071fa05)

| Piece | State | Consequence |
| --- | --- | --- |
| `biz_product.team_target_size`, `team_lifetime_seconds` (V14) | nullable, null on every product, no writer | add the writer; **no schema change** for the columns |
| `ProductTeamTerms(productId, targetSize, lifetimeSeconds)` | record with `startable()` and the bounds (`MIN_TARGET_SIZE = 2`, lifetime 2400–86400 s); no validating factory | reuse the bounds as the single source; add a rejecting factory |
| `ProductRepository.findTeamTerms` → `BizProductMapper.selectTeamTerms` | read path used by `CatalogPurchaseSupportImpl.lockStartable` under `lockById` | unchanged; START keeps reading terms live under the product lock |
| `Product` aggregate | 15 fields, no terms; `revise`/`transition` copy fields and bump `version` via `nextVersion()` | carry terms on the aggregate (D2) |
| `AdminListingAppServiceImpl.lockForChange` | `lockById` → `users.requireActive` → `requireVersion` | same order for the new command |
| `TeamPriceAppServiceImpl.configure` | locks product, checks `expectedVersion` against the **latest policy version** (its own stream), `requireCompatible` (product on sale, currency, amount < original price), `appendPolicy` | reuse, do not duplicate (D8) |
| `PUT /products/{id}/team-price-policy` | checks `catalog:product:write` only, no role check | stays as is; the new endpoint checks role **and** permission like `AdminListingController` |
| `AdminListingResponse` | no terms | gains a nullable `teamTerms` (additive) |
| `GlobalExceptionHandler` | `BizException.details()` becomes `errors`; Bean Validation becomes a list of `{field, message}` | use both; see D6 |
| Audit today | `update_by`, `update_time`, `version` on `biz_product`; policy revisions append-only with `create_by` | terms overwrite in place, so old values are lost (D7) |
| Flyway | latest V19 | one new migration, V20 (D7) |
| Open PRs | only #140 (frontend docs) | no conflict expected; re-check before merge |

## 3. Decisions

✅ = recommended, plan assumes it. ⚠️ = reviewer decides before step 1.

| # | Decision | Choice | Why / alternative |
| --- | --- | --- | --- |
| D1 ⚠️ | Endpoint shape | **One command:** `PUT /api/v1/admin/products/{productId}/team-terms` sets size and lifetime and optionally appends a team price policy, in one transaction. The existing policy endpoint stays for price-only changes | The issue says the API sets all three and calls them "one rule set". Options: **A** terms-only endpoint, policy stays on its own endpoint (smallest PR, the task plan's earlier lean; but the admin makes two calls and "team price below individual price" is never validated with the terms); **B** put terms on `ListingContent` in `revise` (BA-7's wording; widest blast radius: `ListingDetails`, create, revise, every listing test). C (chosen) meets the AC literally and leaves `revise` untouched |
| D2 ✅ | Where terms live in the model | `Product` carries a `ProductTeamTerms teamTerms` field (an "unset" value when both columns are null). `Product.configureTeamTerms(...)` returns a new revision with `version + 1`, the same immutable-revision style as `revise` and `publish`. Older `reconstitute` overloads default to unset, so existing tests and callers compile unchanged | Terms and listing edits then share one optimistic token, which is what "versioned against concurrent admin edits" needs. Alternative: keep terms outside `Product` and write them through a bare repository method (no domain revision; the admin read would need a second query, and a page would need a batch query). `updateListing` does not write the terms columns, so it cannot clobber them |
| D3 ⚠️ | Clearing terms | **Not supported in v1.** `targetSize` and `lifetimeSeconds` are both required on every write. The kill switch for START is delisting (status), as in #183 D6 | The issue asks only for setting. Clearing needs a second meaning ("null both = unstartable") and half-set handling. The task plan leaned "allow, audited". Say so if you want it; it adds one factory arm and one IT |
| D4 ✅ | Concurrency token | `expectedVersion` = **product** version, checked under the product row lock; a stale value → **412 `CATALOG_412_002`** (`PRODUCT_VERSION_CONFLICT`, existing). A successful write bumps the product version once. Optimistic Offline Lock, checked twice: in the domain (`requireVersion`) and in SQL (`WHERE version = ?`, 0 rows → 412) | Same protocol as `revise` and `changeStatus`. The two-layer check mirrors them |
| D5 ✅ | Which lifecycle states accept terms (State) | **Terms: draft, on-sale and off-sale.** Deleted rows are invisible (404). **Team price policy part: on-sale only**, because `TeamPricePolicy.requireCompatible` already says so and this plan does not change the standalone endpoint's behaviour. A `teamPrice` sent for a draft or off-sale listing → 409 `CATALOG_409_001` | Terms are read only at START and frozen into the intent and the team, so a live edit cannot move a bloc already running (§4.5, IT-9). A frozen-after-publish rule would force delisting to fix a typo. Seeding order on dev: create draft → PUT terms → publish → PUT terms again with `teamPrice`. ⚠️ If you would rather allow the policy on a draft too, `requireCompatible` splits into an on-sale check and a price check; say so and add UT for it |
| D6 ✅ | Validation and field errors | Two layers. **Bean Validation** on the request (`@NotNull`, `@Min(MIN_TARGET_SIZE)`, `@Min/@Max` on lifetime from the domain constants, money pattern, `validFrom < validUntil` is a domain rule) → 400 `COMMON_400_002` with `errors: [{field, message}]`. **Domain rule set** `TeamTermsSpecification` re-checks everything that is an invariant, plus the cross-field rules Bean Validation cannot see (team price < original price, currency match) and reports **all** violations at once → new **`CATALOG_400_003` `TEAM_TERMS_INVALID`** with `errors` as a map `{"targetSize": "...", "teamPrice.amount": "..."}` | The AC wants 400 with field errors. Domain checks are authoritative (the aggregate is used by more than the controller). Known wart: the two error shapes differ (list vs map), because `GlobalExceptionHandler` already renders them that way. Not changed here; noted for the frontend hand-off |
| D7 ⚠️ | Audit of writes | **Append-only `log_product_team_terms`** (V20): id, product_id, product_version (the new revision), old and new size and lifetime (nullable for the first write), team_price_version (nullable), actor id, create_time. Written in the same transaction. Plus the usual `update_by` / `update_time` on `biz_product` | The columns are overwritten in place, so without a log the previous terms are unrecoverable and "who changed the bloc size on 10-03" has no answer. Policy revisions already keep their own history. Cheaper alternative: `update_by`/`update_time`/`version` only (no migration, no port); it audits "last writer", not history. Recommend the log: terms are money-adjacent and this is the only new table. No FK, no CHECK, `log_` prefix, per `pgsql-ddl` |
| D8 ✅ | Reusing policy logic | `TeamTermsAppServiceImpl` calls `TeamPriceAppService.configure(...)` inside its own transaction (propagation `REQUIRED` joins it), after the terms are written. If the policy is rejected, the whole transaction rolls back, terms included | No copy of the policy version check, `requireCompatible` or `appendPolicy`. Cost: one extra `SELECT ... FOR UPDATE` on a row this transaction already holds, and a second `requireActive`. Both are cheap. Alternative: extract a package-private policy writer used by both services (cleaner, but changes `TeamPriceAppServiceImpl`'s constructor and its tests) |
| D9 ✅ | Contract first | **PR A (docs + contract entry)**: this plan, the HTTP contract section in `admin-listings.md` (path, request and response JSON, status codes), reviewed and approved by @EricWang1358 and shared with @limjiechao. **PR B (implementation)**: code, tests, the generated `contracts/openapi.json`, ADR addendum, NFR row, log entry. `openapi.json` is generated, never hand-edited (hard gate), so the "contract entry" is the documented, approved shape, and PR B's diff must match it | AC5. If review of PR A is not back by 09-30 noon, start PR B on the assumption that the shape is accepted, and reconcile before merge |
| D10 ✅ | Policy version token in the request | `teamPrice.expectedVersion` = the **latest policy** version (0 = first revision), exactly as on the standalone endpoint | The standalone endpoint does not bump the product version, so without this a price revision made between the admin's read and this write would be silently overwritten. Both tokens are checked under the same lock. Mismatch → 412 `CATALOG_412_001` |
| D11 ✅ | Bounds | Size ≥ 2, lifetime 2400–86400 s, the constants already on `ProductTeamTerms` | The issue says "a configured range". These are the documented, tested bounds and V14's comment ties 2400 to the quote window. A per-environment property would let one environment invent a lifetime shorter than the quote window (#183 D11) and START would then reject it. Keep code constants; say so if a property is wanted |
| D12 ✅ | Permission | Role `admin` **and** permission `catalog:product:write`, via the same `administrator()` check as `AdminListingController` (extract to a shared package-private helper only if it stays a one-line move; otherwise copy) | V6 grants the permission to admins only; the double check is the UC9 convention (role-only and permission-only denial tests exist). Anonymous → 401, others → 403 |
| D13 ✅ | Response shape | `PUT` returns `Result<TeamTermsResponse>`: `{ productId, version, teamTerms: {targetSize, lifetimeSeconds, startable}, teamPrice: {amount, currency, validFrom, validUntil, version} \| null }`. `AdminListingResponse` gains `teamTerms` (nullable, same object) on `GET /{id}` and `GET /` | The admin needs the new version to make the next write. Reading `teamTerms` on the listing meets BA-7's display need. The price policy is still read from `GET /products/{id}/team-price-policy`; putting it on the page would need a batch policy query, which is not worth it now |

## 4. Design

### 4.1 HTTP contract (the "contract entry" for PR A)

`PUT /api/v1/admin/products/{productId}/team-terms`, tag `admin-listings`, security scheme `blocbuyToken` (already on the controller class).

Request (unknown fields are rejected by the existing global Jackson policy):

```json
{
  "expectedVersion": 3,
  "targetSize": 5,
  "lifetimeSeconds": 7200,
  "teamPrice": {
    "amount": "89.00",
    "currency": "sgd",
    "validFrom": "2026-10-01T00:00:00Z",
    "validUntil": "2026-11-01T00:00:00Z",
    "expectedVersion": 0
  }
}
```

- `expectedVersion` (product version): required, ≥ 0.
- `targetSize`: required integer ≥ 2. `lifetimeSeconds`: required integer 2400–86400.
- `teamPrice`: optional. Omitted → the price policy is untouched. Present → every field required; `amount` is a decimal **string** (same pattern as the standalone endpoint), `currency` is `sgd`.
- PUT is a full replacement of the terms, not a patch. It is not replay-idempotent: a replay carries a stale `expectedVersion` and returns 412, like every other admin PUT.

Response 200, `Result<TeamTermsResponse>`:

```json
{
  "code": "COMMON_200_000",
  "data": {
    "productId": "0199...",
    "version": 4,
    "teamTerms": { "targetSize": 5, "lifetimeSeconds": 7200, "configured": true },
    "teamPrice": { "amount": "89.00", "currency": "sgd", "validFrom": "...", "validUntil": "...", "version": 1 }
  },
  "message": "Success"
}
```

| Status | Code | When |
| --- | --- | --- |
| 200 | `COMMON_200_000` | written; `version` is the new product version |
| 400 | `COMMON_400_002` | Bean Validation failed, with `errors: [{field, message}]` |
| 400 | `COMMON_400_001` | malformed JSON or path id |
| 400 | `CATALOG_400_003` (new) | term rules failed, with `errors: {field: message}` naming each bad term field (`targetSize`, `lifetimeSeconds`). Not reached over HTTP: Bean Validation repeats the bounds and answers `COMMON_400_002` first; it is the domain backstop for other callers |
| 400 | `CATALOG_400_001` | bad `teamPrice`, no field errors: price not below the original price, `validFrom` not before `validUntil`, or `validUntil` already past (as on the standalone endpoint) |
| 401 / 403 | `COMMON_401_001` / `COMMON_403_001` | no session / missing role or permission / disabled account |
| 404 | `CATALOG_404_001` | product absent or deleted |
| 409 | `CATALOG_409_001` | `teamPrice` sent for a product that is not on sale (D5) |
| 412 | `CATALOG_412_002` / `CATALOG_412_001` | stale product version / stale policy version (D4, D10) |

Additive changes to existing operations: `AdminListingResponse.teamTerms` (nullable object) on the two admin GETs, and the `PUT`'s returned object. No path, parameter or required field of an existing operation changes, so `openapi-diff` should classify it as compatible.

### 4.2 Domain (`blocbuy-domain`, catalog)

- `CatalogErrorCode`: add `TEAM_TERMS_INVALID("CATALOG_400_003", 400, "Invalid team terms", "error.catalog.teamTermsInvalid")`. Numbers are never reused.
- `ProductTeamTerms`:
  - keep the record and `startable()`;
  - add `static ProductTeamTerms unset(UUID productId)` and `boolean isSet()`;
  - add `static ProductTeamTerms configured(UUID productId, int targetSize, int lifetimeSeconds)` that delegates to the specification and throws `BizException(TEAM_TERMS_INVALID, details)`.
- `TeamTermsSpecification` (**Specification**, one small final class in `catalog.model`): `Map<String,String> violations(int size, int lifetime, BigDecimal teamPriceOrNull, Product product)`; empty = satisfied. It collects every violation, so the admin fixes the form in one round trip. It is a pure function of its inputs: no I/O, no clock.
- `Product`:
  - new field `ProductTeamTerms teamTerms`; a reconstitute overload takes it, the old overloads pass `unset(id)`;
  - `revise`, `transition` and any other copy method carry it through;
  - `configureTeamTerms(int targetSize, int lifetimeSeconds, BigDecimal teamPriceOrNull)`: runs the specification, returns a new revision with the terms set and `nextVersion()`. Version and lifecycle are the caller's responsibility (`requireVersion` first), as with `revise`.
  - accessor `teamTerms()`.
- `ProductRepository`: add `void updateTeamTerms(Product product, int expectedVersion, UUID actorId)` (writes only the two terms columns and the version) and `void appendTeamTermsLog(TeamTermsChange change)` (D7). `TeamTermsChange` is a record `(productId, productVersion, oldTerms, newTerms, teamPriceVersion, actorId)`. Ports carry no DO or SDK types.

### 4.3 Application (`blocbuy-application`, catalog)

- `TeamTermsAppService.configure(UUID productId, ConfigureTerms command, UUID actorId)` returns `TeamTermsResult(Product product, TeamPricePolicy policyOrNull)`. `ConfigureTerms(int expectedVersion, int targetSize, int lifetimeSeconds, ConfigurePolicy teamPriceOrNull)`; the policy record is the existing `TeamPriceAppService.ConfigurePolicy`.
- `TeamTermsAppServiceImpl`, `@Transactional(timeout = 10)`, `@RequiredArgsConstructor`, constructor-injected `ProductRepository`, `UserAppService`, `TeamPriceAppService`. It is a new class, so `AdminListingAppServiceImpl` keeps its four collaborators and tests.

Flow, inside one bounded local transaction (no remote call, no retry loop):

```
PUT /admin/products/{id}/team-terms          adapter: login, role, permission, @Valid, actor from session
 └ TeamTermsAppServiceImpl.configure
    1. products.lockById(id)                   -- row lock; absent → 404
    2. users.requireActive(actor)
    3. product.requireVersion(expectedVersion) -- stale → 412 CATALOG_412_002
    4. revised = product.configureTeamTerms(size, lifetime, teamPrice?.amount)   -- Specification; → 400 CATALOG_400_003
    5. products.updateTeamTerms(revised, expectedVersion, actor)                 -- WHERE id AND version AND deleted=false; 0 rows → 412
    6. if teamPrice != null: policy = prices.configure(id, actor, teamPrice)     -- joins this tx; 412 CATALOG_412_001 / 409 CATALOG_409_001 / 400 CATALOG_400_001
    7. products.appendTeamTermsLog(change)
    return (revised, policy)
```

Notes:

- **Lock order.** The only lock is the product row, taken first, exactly like `lockStartable`, `issueQuote` and `configure`. So START and a terms write serialize on it: START sees either the old terms or the new terms, never a mix. No second lock is taken, so this adds no cycle to the global order (group deal → purchase intent → payment).
- **Rollback.** Any failure after step 5 (policy rejected, log insert failed) rolls back the terms too. A stale or invalid request changes nothing (AC3).
- **Order of validation.** Version before the specification, so a stale client gets 412, not a 400 about data it may already have fixed. Specification before the SQL write.
- **Redis** is not involved. PostgreSQL is the source of truth; nothing is cached.

### 4.4 Adapter (`blocbuy-adapter`, `AdminListingController`)

- New `@PutMapping("/{productId}/team-terms")` with `@Operation` summary and description (idempotency, 412, bounds), returning `Result<TeamTermsResponse>`. The controller only checks authorization, validates, converts and calls the service (protocol adaptation only; the status comes from `ErrorCode` in the global handler).
- Request DTO `TeamTermsRequest(@NotNull @Min(0) Integer expectedVersion, @NotNull @Min(ProductTeamTerms.MIN_TARGET_SIZE) Integer targetSize, @NotNull @Min(MIN_LIFETIME_SECONDS) @Max(MAX_LIFETIME_SECONDS) Integer lifetimeSeconds, @Valid TeamPriceRequest teamPrice)`. `TeamPriceRequest` reuses the standalone policy's money pattern and `DecimalStringDeserializer`, with `@Pattern(regexp = "^sgd" + INPUT_END)` for the currency.
- `AdminListingResponse` gains `teamTerms`; `from(Product)` maps it. No aggregate or DO crosses the HTTP boundary.
- The class already carries the six `@ApiResponse` codes; add the 412 description for the policy version.

### 4.5 Infrastructure (`blocbuy-infrastructure`, catalog)

- `BizProductMapper.xml`: add `team_target_size, team_lifetime_seconds` to `<sql id="columns">` and to `bizProductMap`, so `selectActiveById`, `selectActiveByIdForUpdate` and the pages load terms with no extra query (the page stays two queries, never N+1). Add:

```xml
<update id="updateTeamTerms">
    UPDATE biz_product SET team_target_size = #{row.teamTargetSize},
        team_lifetime_seconds = #{row.teamLifetimeSeconds}, version = version + 1,
        update_by = #{actorId,jdbcType=OTHER}, update_time = clock_timestamp()
    WHERE id = #{row.id,jdbcType=OTHER} AND deleted = false AND version = #{expectedVersion}
</update>
```

- `BizProductDO` and `ProductConverter`: two new fields, mapped both ways. `ProductRepositoryImpl.updateTeamTerms` uses the existing `requireChanged` (0 rows → `PRODUCT_VERSION_CONFLICT`).
- V20 `V20__product_team_terms_log.sql` (D7): table `log_product_team_terms`.

```sql
CREATE TABLE log_product_team_terms (
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    product_id         uuid        NOT NULL,
    product_version    integer     NOT NULL,
    old_target_size    integer,
    old_lifetime_secs  integer,
    new_target_size    integer     NOT NULL,
    new_lifetime_secs  integer     NOT NULL,
    team_price_version integer,
    create_by          uuid        NOT NULL,
    create_time        timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_product_team_terms_product ON log_product_team_terms (product_id, create_time);
-- COMMENT ON TABLE / every column; no FK, no CHECK. Application obligations listed in the header.
```

  `log_` tables are immutable audit rows (like `log_refund_attempt`, V11), so they carry no `update_*` or `deleted` columns. Confirm the exact column set against `.claude/skills/pgsql-ddl/references/conventions.md` while writing V20. A new mapper `LogProductTeamTermsMapper` (or an `insert` in `BizProductMapper`, whichever the package convention prefers) does the insert; it is bound with `#{}` only.
- **Existing rows.** Nothing is backfilled: V14 rows keep null, and START keeps refusing (`CATALOG_409_006`) until an admin configures them. The migration adds a table only, so an application rollback can leave it applied.
- **Open teams are unaffected by a terms change.** START freezes size and lifetime into the intent (`StartQuote` → `PurchaseIntent`) and the team snapshots `target_size` and `expire_time` at creation (`biz_group_deal`, V14 comment). IT-9 asserts it.

## 5. Tests

Unit tests use JUnit 5, plus Mockito for the application layer. Target: 100 % line and branch coverage on every touched main file (see §8).

| ID | Class | Case |
| --- | --- | --- |
| UT-D1 | `TeamTermsSpecificationTest` | size 1, 0, negative → violation on `targetSize`; size 2 accepted |
| UT-D2 | ″ | lifetime 2399 and 86401 → violation on `lifetimeSeconds`; 2400 and 86400 accepted (boundaries) |
| UT-D3 | ″ | team price = original and > original → violation on `teamPrice.amount`; original − 0.01 accepted |
| UT-D4 | ″ | several failures at once → all fields reported, not the first |
| UT-D5 | `ProductTest` / `ProductTeamTermsTest` | `configureTeamTerms` returns a new revision, version + 1, other fields unchanged, original unmodified; `unset` is not `startable()`; configured values are `startable()` |
| UT-D6 | `ProductLifecycleTest` | `revise`, `publish`, `delist` keep the terms; terms allowed in draft, on-sale and off-sale |
| UT-D7 | `ProductTest` | `Integer.MAX_VALUE` version → `PRODUCT_VERSION_CONFLICT` (no wrap) |
| UT-W1 | `TeamTermsAppServiceImplTest` | happy path, no policy: `InOrder` lockById → requireActive → updateTeamTerms → log; policy service never called |
| UT-W2 | ″ | happy path with policy: `configure` called with the command, after `updateTeamTerms` |
| UT-W3 | ″ | stale version → 412 before any write; product missing → 404 |
| UT-W4 | ″ | invalid terms → `CATALOG_400_003` with the expected field map; no write |
| UT-W5 | ″ | policy rejection propagates (no swallow) and the log is not written |
| UT-W6 | ″ | disabled actor is rejected after the lock and before the version check, like `lockForChange` |
| UT-P1 | `ProductRepositoryImplTest` | `updateTeamTerms` 1 row OK, 0 rows → `PRODUCT_VERSION_CONFLICT`; log insert delegated |
| UT-P2 | `ProductConverterTest`, `ProductTeamTermsPersistenceTest` | terms map both ways, including nulls |
| UT-A1 | `AdminListingControllerTest` (MockMvc) | request binds; response has `version`, `teamTerms`, `teamPrice` or null; the service gets the session actor, never a body actor |
| UT-A2 | ″ | Bean Validation errors: size 1, lifetime out of range, malformed money, missing `expectedVersion` → 400 with the field names |
| IT-1 | `TeamTermsFlowIT` (MockMvc + real Sa-Token RBAC + Flyway + PostgreSQL 18, the `AdminListingFlowIT` pattern) | draft → PUT terms → 200; row shows the values, `version` 1, `update_by` = admin; admin GET shows `teamTerms`; buyer GET of the draft is still 404 |
| IT-2 | ″ | stale `expectedVersion` → 412; row unchanged; no log row |
| IT-3 | ″ | two competing writes with the same version (two threads): exactly one 200, one 412; final row equals the winner |
| IT-4 | ″ | invalid input matrix → 400 with `errors`; `teamPrice.amount` ≥ original price → `CATALOG_400_003` with the `teamPrice.amount` key; nothing written |
| IT-5 | ″ | on-sale product: terms plus `teamPrice` → 200; `biz_team_price_policy` has revision 1 and the product version moved once; then a stale `teamPrice.expectedVersion` → 412 `CATALOG_412_001` and **no terms change** (atomic rollback) |
| IT-6 | ″ | every write leaves one `log_product_team_terms` row with old and new values, the actor and the new version; a rejected write leaves none |
| IT-7 | ″ | `teamPrice` on a draft → 409 `CATALOG_409_001`, terms not written; deleted product → 404 |
| IT-8 | `StripeLocalIntegrationTest` (existing container and fixtures) | replace the raw `UPDATE biz_product SET team_target_size…` seed with the admin service, then `startable()` is true and a START intent succeeds (AC4); an unconfigured product still returns `CATALOG_409_006` |
| IT-9 | ″ | terms change between START intent and settlement, and after a team exists: the intent's frozen size and lifetime, and the running team's `target_size` and `expire_time`, do not change; the next START uses the new terms |
| AUTH-1 | `TeamTermsFlowIT` | anonymous → 401 |
| AUTH-2 | ″ | buyer (no role) → 403 |
| AUTH-3 | ″ | admin role without `catalog:product:write` → 403; permission without admin role → 403 (both denials, as in `AdminListingFlowIT`) |
| AUTH-4 | ″ | disabled administrator with an old session → 403 |
| CT-1 | `TeamTermsFlowIT` | the generated `/v3/api-docs` documents the operation, the `blocbuyToken` scheme, the constraints on the request fields, and the nullable `teamTerms` on `AdminListingResponse` |
| ARCH | `ArchitectureTest` | existing rules stay green: domain has no Spring, MyBatis or Redis; catalog reaches no other domain's mapper, DO or repository implementation; no `@RequiredConstructorArgs` |

The existing `AdminListingFlowIT`, `TeamPriceFlowIT`, `CatalogPurchaseSupportImplTest` and `StripeLocalIntegrationTest` cases are regression gates and must pass unchanged, apart from the IT-8 seeding swap.

## 6. Steps (≈ 12 h)

| Step | Work | Est. | Done when |
| --- | --- | --- | --- |
| 0 | **PR A**: this plan reviewed; HTTP contract section added to `admin-listings.md`; contract shared with @limjiechao on #221 | 1 h | approved by the reviewer; decisions D1, D3, D7 answered |
| 1 | Domain: error code, `TeamTermsSpecification`, `ProductTeamTerms` factory, `Product.configureTeamTerms` and the carried field, ports; UT-D* | 2 h | `./mvnw -pl blocbuy-domain test` green |
| 2 | Infrastructure: mapper columns, `updateTeamTerms`, DO and converter, V20 and log insert; UT-P* | 1.5 h | `./mvnw -pl blocbuy-infrastructure -am test` green; V20 applies on an empty and on a populated database |
| 3 | Application: `TeamTermsAppService(Impl)`; UT-W* | 1.5 h | unit tests green; `InOrder` asserts the lock order |
| 4 | Adapter: endpoint, DTOs, `teamTerms` on `AdminListingResponse`; UT-A*; regenerate the contract | 1.5 h | `git diff --exit-code -- contracts/` clean after `git add`; the diff matches §4.1 |
| 5 | Integration: `TeamTermsFlowIT` (IT-1..7, AUTH-*, CT-1), IT-8 and IT-9 in `StripeLocalIntegrationTest` | 2.5 h | `./mvnw -B verify` green with Docker |
| 6 | Docs (§7) and the day log | 0.5 h | links resolve |
| 7 | Coverage self-check (aggregate `jacoco.xml` script from the skill), PR B against `dev` with the repo template, hand-off note on #221 | 1 h | no missed lines or branches on new code; PR open |

Calendar (from the task plan): 09-29 steps 0–2; 09-30 steps 3–5 and open PR B; 10-01 review fixes and merge; US34 seeding starts the same day.

## 7. PRs, documentation and evidence

As shipped: **one PR, #242**. D9's PR A / PR B split was not used; the plan, the generated contract, the code and the docs were reviewed together. AC5 is met by `contract-check` and `openapi-diff` on that PR rather than by a separate contract PR. The sprint-log entry is `docs/agile/sprint-03/log/2026-09-29.md`.

Documentation changed in #242:

- **Backend ADR 0001**: "Addendum (2026-09-29): an administrator writes the team terms (#221)" resolves the "Open" paragraph. It records D1, D2, D4, D5, D7, D8, D10, the START/terms lock rule, that price rules live only in `TeamPricePolicy` (`CATALOG_400_001` vs `CATALOG_400_003`), and what `configured` does and does not mean. It amends the owning ADR, not a new one.
- **`admin-listings.md`**: the route in the HTTP contract table; the two independent version tokens (product version vs policy version); the error codes of this endpoint; the standalone team-price-policy endpoint remains.
- **`docs/quality/nfr-register.md` NFR-29**: the writer, the IT that proves START succeeds after configuration, and V20 as the audit trail. Row status stays `implemented`.
- **`ProductTeamTerms` Javadoc**: points at `Product.configureTeamTerms` and says configured terms are necessary but not sufficient for START.
- **`V14__product_team_terms.sql`**: unchanged (applied migrations are never rewritten).
- **Frontend hand-off**: in the #242 description (Follow-ups) for @limjiechao: use the returned `version` for the next write; the error shapes (a field list on `COMMON_400_002` for bad terms, none on `CATALOG_400_001` for a bad price); `teamPrice` omitted when no price was set; the seeding order for a new listing.

## 8. Verification checklist (before PR B)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend
./mvnw -B verify        # spotless, checkstyle, unit + ArchUnit, ITs, SpotBugs, JaCoCo aggregate
./mvnw -B verify -Popenapi-export -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true
git add ../contracts/openapi.json && git diff --exit-code -- ../contracts/
```

Then run the aggregate-report script from `.claude/skills/backend-constraints-skill/SKILL.md` over `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml` and clear every missed line or branch on touched files: the `TeamTermsSpecification` failure arms, `Product.configureTeamTerms`, the repository's 0-row arm, the "no `teamPrice`" arm and the "policy rejected" arm are the likely misses.

Hard-gate self-check: no field injection; `@RequiredArgsConstructor` (never `@RequiredConstructorArgs`); `BizException` only from `CatalogErrorCode`; no Mapper call in a loop; `Result<T>` from the controller and no `ResponseEntity`; Sa-Token role plus permission, actor from the session; `#{}` binding only; no secrets; no FK or CHECK in V20.

## 9. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Terms edited while a START intent is in flight | A buyer pays into a bloc sized differently from what the console now shows | The intent freezes size and lifetime, so the buyer gets what the intent said; IT-9 asserts it. The quote sheet does not show terms (#183 D12). Raise if the sheet starts showing them |
| Admin bumps the product version by editing terms, so a concurrent listing edit gets 412 | An admin re-reads and retries; no data is lost | Intended (D4). Documented for the console |
| Two version tokens (product, policy) confuse the client | Wrong `expectedVersion` sent, 412 loop | Response returns both new values; the hand-off note states which is which |
| `Product` gains a field; a missed copy method drops the terms on `revise` or `publish` | Terms silently disappear from the aggregate view (the columns themselves are untouched, because `updateListing` never writes them) | UT-D6 covers `revise`, `publish` and `delist`; IT-1 re-reads after a `revise` |
| V20 collides with another migration number on merge | Flyway fails at startup | Re-check the highest version on `dev` right before merge; renumber if needed |
| Merge-queue contract drift if another PR regenerates `openapi.json` first | `contract-check` red | Rebase and regenerate just before queueing |
| Team price no longer undercuts the original price after a later edit | A discount that is not one | Not reachable through this API: D5 allows `teamPrice` only on-sale, and `revise` freezes the price after first publication. `lockStartQuote` and `issueQuote` re-check the undercut anyway |
| Extra `SELECT ... FOR UPDATE` in the nested `configure` | Negligible; the row is already held | Accepted (D8); revisit only if the writer is extracted |

## 10. Resolved questions

The owner answered every open question before implementation; review of #242 added two amendments.

1. **D1**: one command with an optional `teamPrice`. Shipped.
2. **D3**: no clearing in v1; delisting stays the only way to stop START. Shipped.
3. **D5**: terms are editable on an on-sale listing (and in every other state). Shipped.
4. **D7**: the `log_product_team_terms` audit table (V20). Shipped.
5. **D11**: the lifetime range stays as code constants on `ProductTeamTerms`. Shipped.
6. **Review of #242**: the price rule left `TeamTermsSpecification` (price errors are `CATALOG_400_001` only), and `startable` became `configured`. Follow-ups filed as issues: read the terms from the locked `Product` in `lockStartable` instead of a second query, and reject price windows that START cannot use.
