# CR-11 open-bloc progress on the catalog summary — implementation plan

Date: 2026-10-04 · Branch: `feature/open-bloc-process-20261004` · Issue: #245 (frontend ask CR-11, not in the Sprint 3 backlog #232) · Owner: los gai · Related: [best open-deal cache design](../../../backend/docs/design/best-open-deal-cache.md), [backend ADR 0002](../../../backend/docs/adr/0002-team-lifecycle-events.md), [frontend design screens §4.1](../../../frontend/docs/design-screens.md), frontend backlog FB-16, #208 (hot-path caching, open), #366 (best-offer cache, merged 2026-10-03)

> **Status (2026-10-04): approved (D3 mirror the detail page; D5 cache design amendment, no ADR) and implemented on this branch.** V1–V4 passed locally: full `verify` 1,598 tests, 0 failures, 1 pre-existing skip; JaCoCo aggregate shows no missed line or branch on any touched main file; the contract diff is exactly `ProductSummaryResponse.deal`; `pnpm gen:api && pnpm check` green (685 tests). CI `contract-check` and SonarCloud wait for the PR.

## 1. Goal and acceptance criteria

Each card in the catalog grid (`GET /api/v1/products`, browse and keyword search) carries the open bloc's progress, so the frontend can draw "3 of 5 joined · 2 seats left" and the progress bar without one detail request per card.

| # | Criterion (issue #245) | Delivered by |
| --- | --- | --- |
| AC1 | For a listing with an open bloc, the list's `deal.targetSize` / `deal.currentSize` equal what `GET /api/v1/products/{id}` returns (IT) | D2, §4 B1–B4, T5 |
| AC2 | `deal` is `null` for a listing with no open bloc | D2, T1, T4, T5 |
| AC3 | Keyword search results carry the same field | D2 (shared `toPageResult`), T2, T5 |
| AC4 | SQL statements per page do not grow with page size (IT / statement count) | D2, T6 |
| AC5 | If listing pages are cached (#208), a join that changes `currentSize` does not leave a stale count longer than the documented TTL | D5, T7, §4 B6 |
| AC6 | `contracts/openapi.json` regenerated and committed; `./mvnw -B verify` green | B5, V2, V3 |

Non-goals (issue): no change to `GET /api/v1/products/{id}`; no member list or buyer names; no change to search ranking or ordering. Also out of scope: the frontend `ProductCard` progress row (FB-16, follow-up once this is on `dev`).

## 2. Baseline (read on 2026-10-04 from `317ce30`)

| Piece | State | Consequence |
| --- | --- | --- |
| `ProductController` (adapter, catalog) | `pageListings` routes blank keyword → `productAppService.pageListings`, else → `search`; both go through `toPageResult` → `toSummary(ListingView)`. `toDetail` already maps `ActiveDealView` → `ActiveDealResponse` inline. | One mapper (`toSummary`) serves both paths; the deal mapping can be shared with `toDetail`. |
| `ProductSummaryResponse` | `id, title, subtitle, coverImage, category, individualPrice, groupPrice, currency` | Gains `deal`. |
| `ActiveDealResponse` | `dealId, targetSize, currentSize, expireTime`; contract schema exists; `ProductDetailResponse.deal` renders as `anyOf [$ref, null]` via `NullableRefCustomizer` | Reuse as-is; the summary's new property renders the same way. |
| `ListingView` (application query) | Same eight fields as the summary, no deal | Gains `ActiveDealView deal`. Only built in `ProductAppServiceImpl.toListingView`; read in `ProductController` and two test files. |
| `ProductAppServiceImpl.toPageResult` | Shared by browse and search: **one** `bestDealsFor(products)` batch → `groupDealQueryService.findBestOpenDealsByProductIds(ids)`, then `toListingView(product, bestDeals.get(id))`, which already uses that offer for `groupPrice` | The required data is already loaded per page, once. The change is plumbing: pass the same `OpenDealOffer` into the view. **No new SQL, no join, no new query.** |
| `getListingDetail` | Calls the same `findBestOpenDealsByProductIds(List.of(id))` and builds `ActiveDealView` from the offer **whenever an offer exists** (independent of whether `groupPrice` resolved) | Same source as the list → AC1 holds by construction if the list uses the same rule (D3). |
| `GroupDealQueryServiceImpl.findBestOpenDealsByProductIds` | One Redis `MGET` (best-offer cache, #366), at most one PostgreSQL batch for misses, one Lua population. Cache unavailable → PostgreSQL only, no populate | Statement count per page is constant: 0 or 1 `biz_group_deal` statement regardless of page size (≤ 100 IDs). |
| `RedisOpenDealOfferCache` / `OpenDealOfferCacheSubscriber` | Per-product key; positive TTL 15–25 s, absence 5–10 s; `MemberJoined`, `TeamSucceeded`, `TeamExpired` evict at `AFTER_COMMIT`; documented in `best-open-deal-cache.md` | Listing **pages** are not cached (#208 is still open); the only cache in the path is this per-product offer, already bounded and evicted on join. AC5 is met by existing behaviour; needs a test and a doc line naming the new consumer. |
| `currentSize` semantics | Incremented by `PaidGroupDealService` on paid join, which publishes `MemberJoined` (and `TeamSucceeded` at target) | "N of M joined" counts paid members, matching the issue. |
| `DualPricingStrategy.resolve` | `groupPrice = deal.dealPrice` only when deal currency equals product currency; otherwise `null` | The only case where an open offer exists but `groupPrice` is null. Detail still returns `deal` there. See D3. |
| Tests | `ProductAppServiceImplTest` (browse/search/detail, Mockito); `ProductControllerTest` (MockMvc, mocked app service); `BestOpenDealCacheIT` (real PG + Redis, eviction); `AdminListingFlowIT` (real PG, Redis on port 1, `SqlStatementCapture` page-size statement assertion) | No PostgreSQL IT covers the public `GET /api/v1/products` today. `AdminListingFlowIT.policyEnrichmentKeepsSqlCountIndependentOfPageSize` is the pattern for AC4. |
| Frontend client | `frontend/src/api/generated` is not committed; `prepare` runs `gen:api`; `vProductSummaryResponse` is `v.object` (non-strict) | Additive property cannot break the current frontend; FB-16 consumes it later. |

## 3. Decisions

| ID | Decision | Recommendation | Alternatives rejected |
| --- | --- | --- | --- |
| D1 | Contract shape | **Nested nullable `deal: ActiveDealResponse`**, appended as the last `ProductSummaryResponse` component with `@Schema(nullable = true)`. Same schema as the detail page, so the frontend can reuse one progress component and one type; `dealId` and `expireTime` come for free. | Flat `dealTargetSize` / `dealCurrentSize` ints (a second representation of the same fact; loses `dealId`/`expireTime`; the issue allows it only as a fallback). A new `DealProgressResponse` schema (duplicates `ActiveDealResponse`). |
| D2 | Data source and query count | **Reuse the existing per-page batch in `ProductAppServiceImpl.toPageResult`**: the same `OpenDealOffer` that prices `groupPrice` also fills `deal`. Browse and search share `toPageResult`, so AC3 needs no extra code. | (a) SQL join from `ProductMapper` to `biz_group_deal` — catalog infrastructure reading groupbuy tables breaks the module boundary (hard gate) and bypasses the best-offer cache. (b) Calling `getListingDetail` per card — N+1 (hard gate). (c) A new groupbuy query port for progress only — the existing `GroupDealQueryService` port already returns it. |
| D3 | Offer present but `groupPrice` null (deal currency ≠ product currency) | **Mirror the detail endpoint: `deal` is non-null whenever an open offer exists.** AC1 then holds for every listing, and one shared helper builds `ActiveDealView` for both paths. The mismatch is a data-integrity case (team prices are admin-set in the product's currency; START rejects unsupported currencies), not a normal state. **Confirmed 2026-10-04.** | Hide `deal` when `groupPrice` is null — keeps the card "price-only" in the mismatch case but makes list and detail disagree (violates AC1 literally) and needs a second rule on the detail page or a divergence. |
| D4 | Mapping placement | Application: one private static `toActiveDeal(OpenDealOffer)` in `ProductAppServiceImpl`, used by `toListingView` and `getListingDetail` (removes today's inline copy). Adapter: one private static `toDealResponse(ActiveDealView)` in `ProductController`, used by `toSummary` and `toDetail`. Hand-written, no MapStruct. | Separate copies per path (drift risk between list and detail). |
| D5 | Staleness bound and decision record | **No new cache, no page cache.** The bound is the existing best-offer cache's: committed joins evict at `AFTER_COMMIT`; a failed eviction or in-flight repopulation is bounded by the 25 s positive TTL (absence ≤ 10 s). **Amend `backend/docs/design/best-open-deal-cache.md`** with one paragraph naming the catalog summary progress as a display consumer under the same bound. No new ADR: no new architectural decision (ADR 0002 already records eviction). **Confirmed 2026-10-04.** | A page-level cache now (that is #208's scope); an ADR addendum (duplicates the cache design doc). |
| D6 | Search ranking | Untouched: `deal` is filled after `rerank`, from the same batch, by product id. Ordering and `total` unchanged. | — |
| D7 | Evidence location | Agile log entry `docs/agile/sprint-03/log/2026-10-04.md`; this plan's status line; FB-16 note in `frontend/docs/backlog.md` and `design-screens.md` §7 line 419 updated only after merge to `dev` (frontend follow-up owns the card). | — |

## 4. Change list

Backend — bounded context `catalog` (consumer) reading `groupbuy` through the existing `GroupDealQueryService` port. No schema change, no migration, no new mapper, no new bean.

| ID | Layer | File | Change |
| --- | --- | --- | --- |
| B1 | application (catalog query) | `blocbuy-application/.../catalog/query/ListingView.java` | Append `ActiveDealView deal` (nullable); Javadoc: "`deal` null when no open deal". |
| B2 | application (catalog service) | `blocbuy-application/.../catalog/service/ProductAppServiceImpl.java` | `toListingView(product, deal)` passes `toActiveDeal(deal)`; new `private static ActiveDealView toActiveDeal(OpenDealOffer)` (null-safe); `getListingDetail` uses it instead of the inline construction. Class Javadoc unchanged (still one page query + one batch deal query). |
| B3 | application (catalog service) | `blocbuy-application/.../catalog/service/ProductAppService.java` | Javadoc of `pageListings`/`search`: each view also carries the open deal's progress. No signature change. |
| B4 | adapter (catalog web) | `blocbuy-adapter/.../catalog/web/ProductController.java` | `ProductSummaryResponse` appends `@Schema(nullable = true) ActiveDealResponse deal`; record Javadoc "`groupPrice` and `deal` null when no open deal"; `toSummary` and `toDetail` share `toDealResponse(ActiveDealView)`. No new endpoint, parameter or annotation on the operation. |
| B5 | contract | `contracts/openapi.json` | Regenerate with `-Popenapi-export` (never hand-edit). Expected diff: one new `deal` property (`anyOf [$ref ActiveDealResponse, null]`) on `ProductSummaryResponse`, nothing else. |
| B6 | docs | `backend/docs/design/best-open-deal-cache.md` | One paragraph (D5): catalog summary progress is a display consumer; same eviction and 25 s bound; never used for admission. |

Frontend: none in this change (FB-16 follow-up). `pnpm gen:api && pnpm check` is run once as a compatibility check only (V4).

## 5. Tests

| ID | Level | File | Asserts |
| --- | --- | --- | --- |
| T1 | unit | `ProductAppServiceImplTest.pageEnrichesDualPricesViaSingleBatchDealLookup` (extend) + new case | A product with an offer gets `deal` with the offer's `dealId/targetSize/currentSize/expireTime`; a product without an offer gets `deal == null`; still exactly one `findBestOpenDealsByProductIds` call per page. |
| T2 | unit | `ProductAppServiceImplTest.searchCombinesKeywordAndCategoryWithOneBatchDealQuery`, `aiSearchReranksPageBeforeBatchEnrichment` (extend) | Search views carry `deal` keyed by product id after rerank (order preserved, deal attached to the right product). |
| T3 | unit | `ProductAppServiceImplTest` new case | Currency-mismatched offer: `groupPrice == null`, `deal` non-null (D3); detail path returns the same `ActiveDealView`. Covers the `toActiveDeal` null arm and non-null arm on both callers. |
| T4 | web slice | `ProductControllerTest` | Browse and keyword responses serialise `$.data.items[0].deal.targetSize/currentSize/dealId/expireTime`; a view without a deal has no `deal` value (`doesNotExist` also accepts JSON `null`; T5 asserts the explicit `null`). |
| T5 | IT (PostgreSQL) | new `bootstrap/.../CatalogListingProgressIT.java` (Redis on port 1 → cache unavailable, deterministic source path; `TeamPriceControllerTest.SessionStore`; Flyway migrations) | Seed on-sale products, one with an open `biz_group_deal` (target 5, current 3), one with none. AC1: list item `deal` equals `GET /products/{id}` `deal` field by field. AC2: no-bloc product `deal` null and `groupPrice` null. AC3: keyword search returns the same `deal`. |
| T6 | IT (statement count) | `CatalogListingProgressIT` with `SqlStatementCapture.Configuration` | Page size 1 vs 20 (browse and search): same statement count; exactly one statement touching `biz_group_deal`. Mirrors `AdminListingFlowIT.policyEnrichmentKeepsSqlCountIndependentOfPageSize`. |
| T7 | IT (PostgreSQL + Redis) | `BestOpenDealCacheIT` new case | Through `ProductAppService.pageListings`: first read populates Redis with `currentSize = 1`; a committed transaction raising `current_size` to 2 and publishing `MemberJoined` evicts; the next page read shows `deal.currentSize == 2` (AC5). The existing TTL assertions (≤ 25 s) remain the documented-bound evidence. |

Coverage target: 100% of new/changed lines and branches on touched main files — the `toActiveDeal` null/non-null arms (T1, T3) and the adapter `toDealResponse` null/non-null arms (T4).

## 6. Verification

| ID | Check | Proves |
| --- | --- | --- |
| V1 | `cd backend && ./mvnw -B -pl blocbuy-bootstrap -am test -Dtest='ProductAppServiceImplTest,ProductControllerTest' -Dsurefire.failIfNoSpecifiedTests=false` | Focused unit/slice tests. |
| V2 | `cd backend && ./mvnw -B verify` on JDK 21, then the JaCoCo aggregate missed-line script from the backend skill | Spotless, Checkstyle, ArchUnit (no new cross-module dependency), SpotBugs, T5–T7 via Testcontainers, coverage on touched files. |
| V3 | `./mvnw -B verify -Popenapi-export -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true`, commit, then `git diff --exit-code -- contracts/` from repo root | AC6 drift gate; CI `contract-check` openapi-diff confirms additive-only. |
| V4 | `cd frontend && pnpm gen:api && pnpm check` | Additive property leaves the current client and tests green. |

## 7. Risks and consistency

- **Module boundaries.** Catalog keeps reading groupbuy only through `GroupDealQueryService`; no Mapper, DO or table crosses. ArchUnit (V2) is the guard.
- **Consistency.** Read-only; no lock, no write, no funds or admission path touched. PostgreSQL stays authoritative; the displayed count can lag a committed join only by a failed/raced eviction, bounded by the 25 s positive TTL already accepted in ADR 0002 and the cache design. A cached count never feeds `lockJoinTerms`, membership insertion or seat admission.
- **Performance.** Zero extra queries and zero extra Redis calls: the offer is already loaded for `groupPrice`. Response grows by one small object per card with an open bloc (≤ 100 per page).
- **Contract.** One additive nullable response property; old clients ignore it; openapi-diff checks it in CI.
- **Seats left.** Derived on the client as `targetSize − currentSize`; `BizGroupDealMapper.selectBestOpenByProductIds` keeps only `status = 'open'` and unexpired rows (a team reaching target leaves `open` in the same transaction that publishes `TeamSucceeded`), and `RedisOpenDealOfferCache` treats a cached offer with `currentSize >= targetSize` or a past `expireTime` as a miss, so the card does not show a full or expired bloc as open.
- **Review outcome.** D3 and D5 confirmed by the owner on 2026-10-04.
