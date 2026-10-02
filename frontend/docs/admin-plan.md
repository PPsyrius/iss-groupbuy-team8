# BlocBuy Admin Console Implementation Plan

Buyer-side Sprint 2 tasks and recorded Tier 3 follow-ups live in [`plan.md`](plan.md); newer buyer tasks are indexed in [`backlog.md`](backlog.md) and tracked in GitHub issues. This admin plan remains a deferred scope proposal.

**Goal:** Build the UC9 admin console under `/admin`: dashboard, listings table, listing editor with lifecycle actions and version-conflict recovery, and the bloc price rule panel. Build it against the contract as it stands today. Record every artboard need the API cannot meet as a backend ask, not as frontend invention.

**Architecture:** The console is a second shell in the same SPA. It is a route subtree `_authenticated/admin*` with its own dark-rail layout. The storefront `Header` is hidden through route `staticData`. Admin access is decided by a probe: the admin layout's `beforeLoad` calls `GET /api/v1/admin/products?page=1&size=1` and turns a 403 into the Access denied screen. `me` had no roles when A1 was built (CR-9); #282 added them, and A3-01 replaces the probe with a `roles` check. All server state goes through the generated hey-api client with `select: unwrapResult`. Forms use TanStack Form with valibot. Every write is pessimistic and sends the loaded `version`. A 412 always ends in the conflict banner and a Reload, never a merge.

**Tech Stack:** Use the current exact pins in `frontend/package.json` (HeroUI OSS, TanStack Router/Query/Form, valibot, MSW and Playwright). The proposed A2-01 date picker would add an exact-pinned direct dependency on `@internationalized/date`; it is not yet in `package.json`.

**Spec:** the Claude Design export in `~/Downloads/BlocBuy Prototype/`, files `BlocBuy Admin Console.dc.html` (batch 4a) and `BlocBuy Admin Editor.dc.html` (batch 4b), plus the admin parts of `BlocBuy Flow Maps.dc.html` (Flow 2), `BlocBuy Buyer Errors.dc.html` (403 card), `BlocBuy Design System.dc.html` and `BlocBuy HeroUI Kit.dc.html`. Where the artboards differ from the brief, the artboards win (the user's rule from plan §12 note 39). The API is `contracts/openapi.json` at `origin/dev` `c30ed5a` (rechecked 2026-09-26: since `ab0c530` the contract only gained `POST /group-deals/{dealId}/invites` and `IntentRequest.ref` from #198; every admin and policy operation and schema is unchanged; rechecked 2026-10-01 at `b8c2a2a`: the admin answers from #282, #286, #288, #290, #293 and #300 are recorded in §2.2 and §2.3; rechecked again at `82f800c`: #323 only documents field paths on catalog domain 400s, BA-6), with the backend rules in `backend/docs/design/admin-listings.md` and `team-price-quotes.md`. `frontend/docs/design-screens.md` skipped the admin files on purpose; A2-04 adds its §4.7.

**Contract amendment (2026-10-01, #253):** the Spec paragraph above records the original planning baseline. Current generated names in §2.2 follow the locally regenerated `contracts/openapi.json`, with only ten operation IDs changed by #253; they do not claim that #253 has merged or deployed. The [integration guide](../../docs/api/backend-integration-guide.md#generated-operation-names-2026-10-01-253) lists the full migration map.

**Planning date:** 2026-09-25 (end of Sprint 2). Q1 was answered on 2026-09-26: only A0-00 is in Sprint 3; every A1 and A2 row is deferred (§6 Q1).

## Global Constraints

- Every buyer-plan constraint still applies: AGENTS.md rules 1–11, `pnpm check` as the one gate, no `import.meta.env`, generated code never edited, valibot only in `*-search.ts` and `*.schema.ts`.
- Desktop only, per the artboards (1440 wide): density `text-sm`, 44px table rows, 240px rail on `background-inverse`, `p-6` full width. Tokens are the storefront's; the console gets no second theme.
- The UI never shows raw codes (`draft`, `on_sale`, `off_sale`, `sgd`). Labels are Draft, On sale, Off sale and SGD, mapped in one module.
- Money on the wire is a decimal string (`originalPrice`, policy `amount`). The UI never parses it to a float. Comparisons use integer cents (`BigInt`).
- Times are shown in SGT (`Asia/Singapore`) and sent as ISO instants.
- No accent-filled button ships (CR-8). The primary action is `variant="secondary"` with the accent-soft-foreground label, as in the storefront.
- Image URLs are text fields, not uploads. Only `https://` URLs are accepted, because the CSP has `img-src 'self' https:` and `upgrade-insecure-requests`. Every image has the striped fallback tile.

---

## 1. Scope and goals

| Use case / story | Admin-facing part | Source |
|---|---|---|
| **UC9** Admin listing management (#32, closed; backend by Eric, `AdminListingController`) | Create draft, page, read, revise, publish / take off sale / put back on sale, delete an unreferenced draft | Artboards 4a.1–4b.4; flow map Flow 2 stages 1–6 |
| **UC3 first-team pricing** (#50, closed; `TeamPriceController`) | Configure and read the latest bloc price rule (team price policy) per listing | Artboard 4b.5; flow map stage 5 |
| **UC6** refunds (Tier 3, design only) | Read-only "Payments needing attention" queue | Artboard 4b.6; flow map stage 7 |
| NFR-29 group terms | A listing can only start a bloc once its group terms are set, and no admin surface writes them yet | `docs/quality/nfr-register.md`; lifecycle-completion plan PR-H (`docs/agile/sprint-02/lifecycle-completion-plan-20260922.md`) |

No Sprint 2 user story covers the admin GUI. The Sprint 2 plan says UC9 was "completed in Sprint 1" as API-only, and #32 and #49 both say "API-only CRUD, no GUI". The only story this work can sit under is the umbrella **US24 Web application**. Q1 keeps the console out of Sprint 3 (2026-09-26).

**Goals.** (1) An admin can take a listing from draft to on sale to off sale and back, with the frozen fields and the one-way rule visible before the action. (2) Two admins editing the same listing never lose a write silently: the 412 path is designed, tested and demonstrable. (3) An admin can set a bloc price rule without guessing its validity window. (4) A non-admin who types `/admin` gets the designed 403 page, not a blank screen.

**Out of scope:** manual refunds (the artboard is read-only by design), sorting, search, file upload, merge UI, dark mode, and any admin link in the storefront header (the flow-map rationale says admin is reached by URL only).

## 2. Screens, endpoints and gaps

### 2.1 Screen inventory (artboard › state)

| # | Screen | Route | States drawn | States needed but not drawn (§11) | Tier |
|---|---|---|---|---|---|
| S1 | Access denied | `/admin*` when the probe answers 403 | `Errors › Access denied` (code chip 403, "You don’t have access to the admin console", money line, "Browse listings" / "My blocs", hint "Should you have access?") | — | 1 |
| S2 | Dashboard | `/admin` | 4a.1 populated: 4 neutral stat cards (Drafts, On sale, Off sale, Open blocs), Recent listings (5 rows), "New listing", date line "Wednesday 16 September 2026 · all times SGT" | loading, empty (named in the flow map, not drawn) | 1 |
| S3 | Listings table | `/admin/listings?tab=&page=` | 4a.2 All (header "62 listings · page size 20", tabs with counts, 9 columns, row menu, pager "Showing 1–8 of 62 · Page 1 of 4"); 4a.3 Draft empty ("No drafts" + lifecycle lesson, header CTA drops to outline); 4a.4 loading (8 skeleton rows, tab counts withheld) | error; empty for All / On sale / Off sale | 1 |
| S4 | Editor · new draft | `/admin/listings/new` | 4b.1: Title, Subtitle, Category, Solo price, Stock, Cover image URL, Image URLs (up to 20, "Add another URL"); sidebar Status Draft v0; Publish disabled with its precondition; rule panel "Add a rule" | validation errors on create; duplicate product number | 1 |
| S5 | Editor · draft | `/admin/listings/$productId` | 4b.2: header "BB-100472 · draft · v0", Save draft + Publish; lifecycle trail; Danger zone "Delete draft"; publish `AlertDialog size="sm"` listing the three fields that freeze | delete confirmation; delete blocked (409_004) | 1 |
| S6 | Editor · published | same | 4b.3: "on sale · v4 · 142 sold"; Solo price, Currency and Stock frozen with a chip and reason; Sold read-only; "Take off sale"; Delete replaced by its blocking reason with counts | take-off-sale confirmation; off-sale editor with "Put back on sale" | 1 |
| S7 | Validation and conflict | same | 4b.4: field errors in danger next to the field; 412 page-level warning banner "This listing was updated by someone else / You loaded v4; the server now has v5…" + Reload; summary "Two problems to clear" | 409 lifecycle rejection (not drawn) | 1 |
| S8 | Bloc price rule panel | on the editor page | 4b.5: none / active v3 / expired v2 / conflict v3 → v4 ("Not saved", Reload) | scheduled (validFrom in the future); unavailable on draft and off sale (backend rule, §12 note 7); revision form open | 1 |
| S9 | Payments needing attention | `/admin/payments` | 4b.6: read-only table with Payment, Buyer, Bloc, Reason, Amount, Refund, When; "Read-only by design" card | empty | 3 (design only) |
| — | Rail navigation | shell | Dashboard, Listings (62), Bloc price rules (18), Payments (2), user block "EC Eric Chua · Admin" | the "Bloc price rules" destination (no artboard) | 1 |

### 2.2 Endpoint-to-screen mapping

Generated names come from `@/api/generated/@tanstack/react-query.gen` with the current operationIds. They are wrapped under admin names in `features/admin/admin-api.ts` (§12 note 3). The local #253 implementation gives the six original listing operations and four team-price operations explicit resource-specific IDs; merge and pre-merge coordination remain pending. Since #242 (`eed59da`) every admin read and write of a listing carries `teamTerms: TeamTermsView {targetSize, lifetimeSeconds, configured}` (all optional; a never-configured listing reads `null`, `null`, `false`). There are now eight admin operations including `configureTeamTerms` and #290's `counts`; their IDs are unchanged by #253. Since #293 every admin read and write also carries `latestPolicy: LatestPolicyResponse {amount, currency, validFrom, validUntil, version} | null`: the highest non-deleted revision, even when expired or future-dated, and `null` when none exists (create always returns `null`). Since #300 the single read alone adds `references` and `deletable` (see its row).

| Endpoint (operationId → generated) | Request | Response `data` | Validation (contract + backend) | Errors | Screens |
|---|---|---|---|---|---|
| `GET /api/v1/admin/products` (`pageAdminListings` → `pageAdminListingsOptions`, `pageAdminListingsQueryKey`) | query `page` ≥1 (default 1), `size` 1..100 (default 20), `status` optional, `^(draft\|on_sale\|off_sale)$` | `PageResultAdminListingResponse {items, total, page, size, nextCursor}`; newest first, UUID tie-breaker; no sort, no search | Bean validation on params → 400 `COMMON_400_001` with `errors: ["page must be…"]` | 401 `COMMON_401_*`, 403 `COMMON_403_*` (Sa-Token `NotRole`/`NotPermission`) | S1 probe, S2 counts + recent, S3 table + tab counts |
| `GET /api/v1/admin/products/counts` (`counts` → `countsOptions`) | — | `ListingCountsResponse {draft, onSale, offSale, openDeals, withActivePolicy}` (all int64, none marked required): one database snapshot. `openDeals` counts open, unfilled, unexpired blocs across all listings (#290). `withActivePolicy` counts non-deleted listings whose latest revision is valid now, whatever the listing status (#293). | — | 401, 403 as above | S3 tab counts (A3-02), S2 stat cards (A2-02) |
| `POST /api/v1/admin/products` (`createAdminListing` → `createAdminListingMutation`) | `CreateListingRequest {productNo?, listing: ListingContent}` | `AdminListingResponse` (status `draft`, version 0, `pricingMode` `dual`, `soldCount` 0) | `productNo` `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`, optional since #288 (omitted or `null` → server-generated `BB-` number; a supplied number is kept and must be unique among live rows; A3-08 drops the field from the form). `ListingContent`: `title` required, not blank, ≤256; `subtitle` ≤256; `description` ≤10000; `coverImage` ≤1024; `images` ≤20, each not blank and ≤1024; `category` ≤64; `originalPrice` required, positive decimal string with at most 16.2 digits (**must be positive even on a draft**); `currency` `sgd`; `stock` int ≥0. Unknown JSON fields are rejected. Non-idempotent. | 400 `COMMON_400_001` + `errors: [{field, message}]` (for example `listing.originalPrice`); 400 `CATALOG_400_002` (domain; since #323 field-specific cases carry `errors: [{field, message}]` with `listing.<field>` or `productNo`, for example `listing.originalPrice`); 409 `CATALOG_409_003` duplicate product number | S4 |
| `GET /api/v1/admin/products/{productId}` (`getAdminListing` → `getAdminListingOptions`, `getAdminListingQueryKey`) | path uuid | `AdminListingResponse {id, productNo, listing, status, pricingMode, soldCount, version, teamTerms, latestPolicy, references, deletable}`. Only this read carries `references: ListingReferencesResponse {deals, quotes, policies}` (int64; every retained row, including expired, closed and soft-deleted history) and `deletable` (boolean; a snapshot, not a reservation: DELETE can still answer 409 or 412) (#300) | non-uuid → 400 | 404 `CATALOG_404_001` absent or deleted | S5–S7 |
| `PUT /api/v1/admin/products/{productId}` (`reviseAdminListing` → `reviseAdminListingMutation`) | `UpdateListingRequest {listing, expectedVersion}` | new `AdminListingResponse` (version + 1) | **Full replacement**: an omitted optional field is cleared. After first publication `originalPrice`, `currency` and `stock` must equal the stored values. `productNo`, `pricingMode` and `soldCount` are not writable. | 412 `CATALOG_412_002` stale; 409 `CATALOG_409_002` frozen field changed; 400 as above; 404 | S5–S7 |
| `PUT /api/v1/admin/products/{productId}/status` (`changeAdminListingStatus` → `changeAdminListingStatusMutation`) | `ListingStatusRequest {status: "on_sale"\|"off_sale", expectedVersion}` | new `AdminListingResponse` | draft → on_sale ↔ off_sale; no route back to draft; publishing needs positive stock and price | 409 `CATALOG_409_002` (illegal transition, or the stock/price precondition, which "maps to 409 before state dispatch"); 412; 404 | S5, S6, S3 row menu |
| `DELETE /api/v1/admin/products/{productId}?expectedVersion=N` (`deleteAdminListing` → `deleteAdminListingMutation`) | query `expectedVersion` ≥0, required | `ResultVoid` | Only a never-published draft with no policy, quote, deal or sold history. Soft delete. A repeat returns 404. | 409 `CATALOG_409_004` referenced; 409 `CATALOG_409_002` not a draft; 412; 404; 400 `COMMON_400_001` without a version | S5, S3 row menu |
| `PUT /api/v1/admin/products/{productId}/team-terms` (`configureTeamTerms` → `configureTeamTermsMutation`) | `TeamTermsRequest {expectedVersion, targetSize, lifetimeSeconds, teamPrice?: TeamPriceRequest}` | `TeamTermsResponse {productId, version, teamTerms, teamPrice?}` (`teamPrice` omitted when none was sent) | Both terms required: `targetSize` ≥2 with no maximum, `lifetimeSeconds` 2400..86400. Allowed in every lifecycle state. **Increments the product `version`**, so a concurrent terms edit makes a later save or lifecycle call a 412. Revise and status changes keep the terms. Optional `teamPrice` appends a policy revision through the same service as `/team-price-policy` and needs an on-sale listing. | 400 `COMMON_400_002` + field errors for out-of-range terms (`CATALOG_400_003` exists but is not reachable over HTTP); 412 `CATALOG_412_002` stale product version; with `teamPrice`: 400 `CATALOG_400_001`, 409 `CATALOG_409_001`, 412 `CATALOG_412_001`; 404 | A3-04 terms panel (not built in A1) |
| `GET /api/v1/products/{productId}/team-price-policy` (`getTeamPricePolicy` → `getTeamPricePolicyOptions`) | path uuid | `PolicyResponse {productId, amount, currency, validFrom, validUntil, version}`: **latest revision only** | needs `catalog:product:write` (admin only) | **409 `CATALOG_409_001` when no rule exists** (not 404); 404 `CATALOG_404_001` unknown product; 403 | S8 |
| `GET /api/v1/admin/products/{productId}/team-price-policy/revisions` (`pageAdminTeamPricePolicyRevisions` → `pageAdminTeamPricePolicyRevisionsOptions`; #254 local implementation, merge pending) | path uuid; page 1-based, size 1..100 (default 20) | `PageResult<PolicyResponse>`: non-deleted revisions, version descending, expired/future policies included; no policy → empty page | admin role, `catalog:product:write`, active account; one read-only snapshot | 400 invalid pagination; 401 anonymous; 403 non-admin/missing capability/disabled; 404 missing/deleted listing | Optional S8 history; no edit/delete of revisions |
| `PUT /api/v1/products/{productId}/team-price-policy` (`configureTeamPricePolicy` → `configureTeamPricePolicyMutation`) | `PolicyRequest {amount, currency: "sgd", validFrom, validUntil, expectedVersion}` | new `PolicyResponse` (version + 1) | `expectedVersion` 0 creates revision 1, later ones must match the latest; `amount` > 0 and **strictly below `originalPrice`**; `validFrom < validUntil`; `validUntil > now`; validFrom inclusive and validUntil exclusive; **the product must be on sale**. The latest revision always replaces earlier ones, including a future or expired one: no fallback. | 400 `CATALOG_400_001` (domain; since #323 field-specific cases carry `errors: [{field, message}]` with `amount`, `currency`, `validFrom` or `validUntil`); 409 `CATALOG_409_001` product not on sale; 412 `CATALOG_412_001` stale; 404 | S8 |
| `GET /api/v1/auth/me` (`me` → `meOptions`) | — | `MeResponse {userId, nickname, avatarUrl, roles}`: `roles: string[]` since #282 (A3-01 reads it) | — | 401 | rail user block; guard (anonymous → `/login`) |

**Admin authorisation today.** Sa-Token RBAC. `AdminListingController.administrator()` runs `StpUtil.checkLogin()`, then `checkRole("admin")` (`RoleCode.ADMIN`), then `checkPermission("catalog:product:write")` on every admin call, reads included. `TeamPriceController` checks only the permission. Roles and permissions load from `sys_role`, `rel_user_role` and `rel_role_permission` through `StpInterfaceImpl`. V3 seeds the roles `buyer` (granted at first Google sign-in) and `admin` ("assigned manually"), and V6 grants `catalog:product:write` to `admin` only. Sign-in is the same Google OAuth for everyone: an admin is simply a buyer account whose DB row has the admin role. `GlobalExceptionHandler` maps `NotRoleException` and `NotPermissionException` to 403 `CommonErrorCode.FORBIDDEN` and `NotLoginException` to 401. A disabled account also returns 403 (application-layer `requireActive`). The client cannot tell a 403 for "not admin" from a 403 for "disabled"; both land on S1.

### 2.3 Gaps: what the artboards need that the API lacks (ready-to-file text in §10)

| Ask | Artboard need | Contract today | Frontend fallback until it lands |
|---|---|---|---|
| **BA-1 (CR-9)** `GET /auth/me` returns `roles: string[]` | Admin guard without a probe; the rail's "Admin" label | **Answered by #282**: `MeResponse.roles` (`string[]`) | A1-03 probes `GET /admin/products?size=1` in `beforeLoad` (403 → S1); A3-01 switches to `roles` |
| **BA-2** Server-assigned product number | 4b.1 has no product number field: "the server assigns it on save" | **Answered by #288**: `productNo` is optional on create; omitted or `null` → the server generates a `BB-` number; a supplied number is kept and must be unique (duplicate → 409) | A1-05 shows a create-only field; A3-08 removes it |
| **BA-3** Admin counts: `{draft, onSale, offSale}` plus an **open blocs** count | Dashboard's 4 stats, tab badges, rail counts | **Answered by #290**: `GET /api/v1/admin/products/counts` (operationId `counts`) → `{draft, onSale, offSale, openDeals}`, one database snapshot; `openDeals` counts open, unfilled, unexpired blocs across all listings | A1-04 read `total` from four `page?size=1` calls (All plus one per status); A3-02 switches the tabs to `counts` (All = the three statuses summed); the Open blocs card arrives with A2-02 |
| **BA-4** Latest policy summary on `AdminListingResponse` (`latestPolicy {amount, validFrom, validUntil, version}`) and a policy count | Table subline "Bloc price S$71.00 · valid to 30 Sep"; rail "Bloc price rules 18" | **Answered by #293**: `latestPolicy {amount, currency, validFrom, validUntil, version}` or `null` on every admin read, page items included (one batched lookup per page). The count is `counts.withActivePolicy`: listings whose latest rule is valid now, not a total of rules. | A1-04 omits the subline; A3-03 adds it from `latestPolicy`. The rail item stays hidden: it has a count but no destination page (Q3) |
| **BA-5** Reference counts on the admin read (`references {deals, quotes, policies}`) or a `deletable` flag | 4b.3 "has 26 bloc records, 61 quotes and 3 price rules" | **Answered by #300**: both. The single admin read carries `references {deals, quotes, policies}` and `deletable`; page and mutation responses omit them | A1-07 shows the reason without counts ("Not available. This listing has been published; take it off sale instead."); A3-05 adds the counts and reads `deletable` |
| **BA-6 (CR-12)** Field path on domain 400s (`CATALOG_400_001` / `400_002`) | 4b.4 attaches "Must be strictly below the solo price" and "Must be after the start" to their fields | **Answered by #323**: field-specific `CATALOG_400_001` and `CATALOG_400_002` carry `errors: [{field, message}]` like `COMMON_400_002`. Paths: `amount`, `currency`, `validFrom`, `validUntil` on the standalone policy write (an end not after the start, or already past, names `validUntil`); `listing.<field>` and top-level `productNo` on listing writes; `teamPrice.<field>` on team-terms writes. Non-field failures may still omit `errors` | Client valibot still catches both rules before submit. A3-07 attaches server domain 400s to fields through `fieldViolations`, which strips `listing.` only; a `teamPrice.` prefix (A3-04) needs its own strip. A 400 without `errors` stays a form-level alert |
| **BA-7 (NFR-29, lifecycle plan PR-H)** Group terms (seats per bloc, bloc lifetime) | Buyer detail's "5 seats per bloc" needs it; without it every START is refused (`CATALOG_409_006`) | **Answered by #242**, as a separate endpoint rather than fields on `ListingContent`: `PUT /admin/products/{id}/team-terms` (`configureTeamTerms`), both terms required on the request (`targetSize` ≥2, no maximum; `lifetimeSeconds` 2400..86400), editable in every lifecycle state; an optional `teamPrice` needs an on-sale listing. Reads carry `teamTerms`. | Not drawn on any artboard. A3-04 builds a separate terms panel once design gap D6 is answered. |
| **BA-8** `GET /admin/payments?attention=true` (payment id, buyer nickname, bloc number, reason, amount, refund status, time) | 4b.6 | No endpoint | Screen not built (Tier 3) |
| **BA-9** Distinct operationIds for the six original admin listing operations | — | **Answered by #324** (issue #253): `pageAdminListings`, `getAdminListing`, `createAdminListing`, `reviseAdminListing`, `changeAdminListingStatus`, `deleteAdminListing`; the four team-price IDs also become resource-specific | Admin wrappers/cache keys, buyer quotation hooks, browser MSW overrides and interceptor tests migrated together; regenerate with `pnpm gen:api` and notify @limjiechao before merge |
| **BA-10** Policy revision history | 4b.5 "an expired rule is shown, never deleted" | **Implemented locally for #254; merge pending**: `pageAdminTeamPricePolicyRevisions`, paged `PolicyResponse` items newest version first; expired/future revisions retained | The panel shows the latest revision only until the optional history UI is wired |
| **BA-11** Policy on drafts: allow it, or confirm it is intentional | 4b.1 "Add a rule after the listing has a price" | `configureTeamPricePolicy` requires on sale (`requireCompatible`) | Copy "Add a rule after you publish"; disabled on draft and off sale |
| **BA-12** 412 body carries `currentVersion` | 4b.4 "the server now has v5" | **Answered by #286**: 412 `errors: {currentVersion}`, the listing version for `CATALOG_412_002` and the policy version (0 if none) for `CATALOG_412_001` | A3-09 reads it from the error; background `getAdminListing` / `getTeamPricePolicy` refetch remains the fallback when the error has no version |
| **CR-10** Category dictionary | "One of five fixed categories" | Free string ≤64 | Reuse `features/catalog/categories.ts`; an unknown stored value shows as an extra option |

---

## 3. Task table

Columns match `plan.md` §3. `FE` = `frontend/`, `A` = `FE/src/features/admin/`. Commands run from the repository root unless prefixed `cd frontend &&`. Each row is one PR. The `issue` cell carries the size with unit and E2E tests (S/M/L, ideal days) and the Q1 status (2026-09-26). Every file under `A/`, every new route and test file, `FE/src/components/ImageFallback.tsx` (moved out of `FE/src/features/catalog/ProductCard.tsx`), `FE/e2e/admin.spec.ts`, `FE/docs/adr/0015-*.md` and `docs/agile/sprint-03/` are to be created by the task that lists them; all other paths exist on `origin/dev`.

### Tier A1: foundation, listings and editor (contract as it stands; artboard Tier 1)

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| A0-00 | A1 | Commit this plan; file BA-1..BA-12 and the §11 design gaps as issues | `FE/docs/admin-plan.md` | `gh issue list --search "[BA-" --json number --jq length` ≥ 12 | — | new: "Admin console (UC9 GUI)" · S 0.25d · Sprint 3 first half |
| A1-01 | A1 | Admin API layer: named wrappers with `unwrapResult`; `ApiError.errors` plus `fieldViolations()`; decimal-cents money helpers; status labels | `FE/src/api/envelope.ts`, `FE/src/api/client.ts`, `FE/src/api/envelope.test.ts`, `FE/src/api/client.test.ts`, `A/admin-api.ts`, `A/money.ts`, `A/money.test.ts`, `A/listing-status.ts`, `A/listing-status.test.ts` | `cd frontend && pnpm vitest run src/api src/features/admin && pnpm check` | A0-00 | new · S 0.5d · merged in #294 |
| A1-02 | A1 | Stateful admin MSW (store with versions, 412, 409 and 403 rules as in the backend), wired into Vitest helpers and `?msw=1&me=1&admin=1` | `FE/src/test/admin-msw.ts`, `FE/src/test/admin-msw.test.ts`, `FE/src/test/browser-msw.ts`, `FE/src/test/render-route.tsx` (`serveAdmin()`, `serveNonAdmin()`) | `cd frontend && pnpm vitest run src/test/admin-msw.test.ts` | A1-01 | new · M 1.5d · merged in #299 |
| A1-03 | A1 | Route subtree, admin guard (probe), dark-rail `AdminShell`, Access denied card, storefront header hidden through `staticData` | `FE/src/routes/_authenticated/admin.tsx`, `FE/src/routes/_authenticated/admin/index.tsx` (placeholder), `A/admin-guard.ts`, `A/admin-guard.test.ts`, `A/AdminShell.tsx`, `FE/src/components/ErrorCard.tsx` (+`AccessDeniedCard`), `FE/src/components/ErrorCard.test.tsx`, `FE/src/routes/__root.tsx`, `FE/src/router.tsx` (`StaticDataRouteOption`), `FE/src/routes/admin.test.tsx`, `FE/src/routeTree.gen.ts` | `cd frontend && pnpm vitest run src/routes/admin.test.tsx src/components && pnpm check` | A1-02 | new · M 1d · merged in #305 |
| A1-04 | A1 | Listings table: tabs All / Draft / On sale / Off sale with counts, server pagination, loading / empty / error, row menu | `FE/src/routes/_authenticated/admin/listings.index.tsx`, `A/listings-search.ts`, `A/listings-search.test.ts`, `A/ListingsTable.tsx`, `A/ListingsTable.test.tsx`, `A/StatusTabs.tsx`, `A/use-status-counts.ts`, `FE/src/routes/admin-listings.test.tsx` | `cd frontend && pnpm vitest run src/features/admin src/routes/admin-listings.test.tsx` | A1-03 | new · M 1.5d · merged in #306 |
| A1-05 | A1 | Shared listing form (schema, DTO mapping, fields, image URL list) and the new-draft screen with create | `A/listing-form.schema.ts`, `A/listing-form.schema.test.ts`, `A/listing-form.ts` (to and from DTO), `A/listing-form.test.ts`, `A/ListingForm.tsx`, `A/ImageUrlsField.tsx`, `A/ImageUrlsField.test.tsx`, `FE/src/components/ImageFallback.tsx` (moved out of `ProductCard`), `FE/src/routes/_authenticated/admin/listings.new.tsx`, `FE/src/routes/admin-listing-new.test.tsx` | `cd frontend && pnpm vitest run src/features/admin src/routes/admin-listing-new.test.tsx` | A1-04 | new · L 2d · merged in #307 |
| A1-06 | A1 | Editor for an existing listing: fresh loader, sidebar (status, version, lifecycle trail), frozen fields, Save (`revise`), unsaved-changes blocker, 404 | `FE/src/routes/_authenticated/admin/listings.$productId.tsx`, `A/EditorSidebar.tsx`, `A/FrozenField.tsx`, `A/use-listing-mutations.ts`, `A/UnsavedChangesDialog.tsx`, `FE/src/routes/admin-listing-edit.test.tsx` | `cd frontend && pnpm vitest run src/routes/admin-listing-edit.test.tsx` | A1-05 | new · M 1.5d · merged in #309 |
| A1-07 | A1 | Lifecycle: Publish (freeze-list dialog), Take off sale, Put back on sale, Delete draft (confirm), delete blocked; row menu reuses the dialogs | `A/LifecycleDialogs.tsx`, `A/LifecycleDialogs.test.tsx`, `A/lifecycle.ts` (allowed actions per status; pure), `A/lifecycle.test.ts`, `A/RowMenu.tsx`, `A/EditorSidebar.tsx`, `A/ListingsTable.tsx` | `cd frontend && pnpm vitest run src/features/admin` | A1-06 | new · M 1.5d · merged in #311 |
| A1-08 | A1 | Version conflict (412) banner on save, lifecycle and delete; 409 code mapping; "Two problems to clear" summary | `A/ConflictBanner.tsx`, `A/ConflictBanner.test.tsx`, `A/listing-errors.ts` (code → copy), `A/listing-errors.test.ts`, `A/use-listing-mutations.ts`, `FE/src/routes/admin-listing-edit.test.tsx` | `cd frontend && pnpm vitest run src/features/admin src/routes/admin-listing-edit.test.tsx` | A1-07 | new · M 1d · merged in #312 |

### Tier A2: price rule, dashboard, journeys, docs (contract as it stands; artboard Tier 1)

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| A2-01 | A2 | Bloc price rule panel: none, scheduled, active, expired, conflict, unavailable (draft or off sale); revision form with SGT date pickers; replace-now warning | `A/policy.ts` (state derivation), `A/policy.test.ts`, `A/sgt.ts`, `A/sgt.test.ts`, `A/policy-form.schema.ts`, `A/policy-form.schema.test.ts`, `A/PricePolicyPanel.tsx`, `A/PricePolicyPanel.test.tsx`, `FE/src/routes/_authenticated/admin/listings.$productId.tsx`, `FE/package.json` (`@internationalized/date` 3.12.4) | `cd frontend && pnpm vitest run src/features/admin && pnpm knip` | A1-08 | new · L 2.5d · deferred (Q1) |
| A2-02 | A2 | Dashboard: SGT date line, 4 stat cards (Open blocs reads `openDeals` from `counts`, #290), Recent listings (5), New listing, loading / empty / error | `FE/src/routes/_authenticated/admin/index.tsx`, `A/StatCards.tsx`, `A/StatCards.test.tsx`, `A/RecentListings.tsx`, `A/ListingsTable.tsx` (`variant="recent"`), `A/sgt.ts` (`formatSgtDate`), `A/sgt.test.ts`, `A/use-minute-clock.ts`, `FE/src/routes/admin-dashboard.test.tsx` | `cd frontend && pnpm vitest run src/routes/admin-dashboard.test.tsx src/features/admin` | A1-04 | new · S 0.75d · stack 2 (2026-10-01) |
| A2-03 | A2 | Playwright: anonymous → `/login`; non-admin → 403 card; stubbed admin create → publish → conflict; `@admin` real run on dev behind a storage state | `FE/e2e/admin.spec.ts`, `FE/e2e/README.md` | `cd frontend && pnpm build && pnpm e2e --grep admin`; `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @admin` | A1-08, A2-01 | new · S 0.5d · deferred (Q1) |
| A2-04 | A2 | Docs: `design-screens.md` §4.7 (admin, from the artboards), ADR 0015 (admin shell, probe guard, pessimistic writes), AGENTS.md layout line, sprint log | `FE/docs/design-screens.md`, `FE/docs/adr/0015-admin-console-shell-and-guard.md`, `FE/docs/adr/README.md`, `FE/AGENTS.md`, `docs/agile/sprint-03/log/…` | `grep -q '4.7' frontend/docs/design-screens.md && test -f frontend/docs/adr/0015-*.md` | A1-03 | new · S 0.5d · deferred (Q1) |

### Tier A3: blocked on backend asks (rows only)

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| A3-01 | A3 | Guard reads `session.roles` and drops the probe; rail shows the role | `A/admin-guard.ts`, `FE/src/features/session/session.ts`, tests | `cd frontend && pnpm gen:api && pnpm vitest run src/features/admin src/features/session` | A1-03, #282 (BA-1, merged) | BA-1 · merged in #318 |
| A3-02 | A3 | Counts endpoint: the tab counts come from one `counts` read instead of four `size=1` pages; writes refresh it; the dashboard's stat cards (Open blocs included) stay with A2-02 | `A/use-status-counts.ts`, `A/admin-api.ts`, `FE/src/test/admin-msw.ts`, tests | `cd frontend && pnpm vitest run src/features/admin src/test/admin-msw.test.ts src/routes/admin-listings.test.tsx` | #290 (merged) | BA-3 · merged in #321 |
| A3-03 | A3 | Table bloc-rule subline from `latestPolicy` ("Bloc price S$71.00 · valid to 30 Sep"); the "Bloc price rules" rail item stays hidden until Q3 gives it a page (its count would be `counts.withActivePolicy`) | `A/bloc-rule.ts` (`blocRuleLine`; pure), `A/bloc-rule.test.ts`, `A/ListingsTable.tsx`, `FE/src/test/admin-msw.ts` (`latestPolicy` on reads and writes), tests | `cd frontend && pnpm vitest run src/features/admin src/test/admin-msw.test.ts src/routes/admin-listings.test.tsx` | A1-04, #293 (BA-4, merged); rail: Q3 | BA-4 |
| A3-04 | A3 | Group terms panel (seats per bloc, bloc lifetime) on the editor page, with its own write (`configureTeamTerms`), not `ListingForm` fields; the backend blocker (BA-7) is cleared by #242, design is not | `A/admin-api.ts` (`useConfigureTeamTerms`), `A/team-terms.schema.ts`, `A/TeamTermsPanel.tsx`, `FE/src/routes/_authenticated/admin/listings.$productId.tsx`, `FE/src/test/admin-msw.ts` (terms endpoint), tests | as above | design (§11 D6) | BA-7 |
| A3-05 | A3 | Delete-blocked reason with counts from `references`; Delete draft offered only when `deletable` | `A/EditorSidebar.tsx`, `A/lifecycle.ts` (`deleteBlockedReason`), `FE/src/routes/_authenticated/admin/listings.$productId.tsx`, `FE/src/test/admin-msw.ts` (`references` and `deletable` on the single read), tests | `cd frontend && pnpm vitest run src/features/admin src/test/admin-msw.test.ts src/routes/admin-listing-edit.test.tsx` | A1-07, #300 (BA-5, merged) | BA-5 |
| A3-06 | A3 | Payments needing attention (read-only table) | `FE/src/routes/_authenticated/admin/payments.tsx`, `A/AttentionQueue.tsx`, tests | as above | BA-8 | BA-8 |
| A3-07 | A3 | Attach domain 400s to fields through `fieldViolations` | `A/listing-errors.ts`, `A/PricePolicyPanel.tsx`, tests | as above | #323 (BA-6, merged); A2-01 for the `PricePolicyPanel.tsx` part | BA-6 · listing form part A3-07a PR pending (2026-10-01); panel part in A2-01 step 6 |
| A3-08 | A3 | Remove the product number field from create | `A/listing-form.schema.ts`, `A/ListingForm.tsx`, tests | as above | A1-05, #288 (BA-2, merged) | BA-2 · merged in #319 |
| A3-09 | A3 | 412 banner reads `currentVersion` from the error | `FE/src/api/envelope.ts` (typed reader beside `fieldViolations`), `FE/src/api/envelope.test.ts`, `A/use-listing-mutations.ts`, `A/ConflictBanner.tsx`, `FE/src/test/admin-msw.ts` (412 bodies carry `currentVersion`), `FE/src/test/admin-msw.test.ts`, `FE/src/routes/admin-listing-edit.test.tsx` | `cd frontend && pnpm vitest run src/features/admin src/api src/test/admin-msw.test.ts src/routes/admin-listing-edit.test.tsx` | A1-08, #286 (BA-12, merged) | BA-12 · merged in #320 |

### Blocker assumptions per task

| Blocker | Tasks gated | Assumption planned under | If it resolves the other way |
|---|---|---|---|
| An admin account on dev | A2-03 `@admin`, the demo | Someone with DB access grants `admin` to one Google account on the dev DB (#68: grants do not travel with git) | `@admin` stays skipped; all other admin tests run on MSW and `page.route` stubs |
| CR-9 / BA-1 | A1-03 | Probe guard | Resolved by #282: A3-01 swaps in one function; S1 unchanged |
| BA-2 | A1-05 | Product number field on create | Resolved by #288: A3-08 deletes the field |
| Q1 scope | all | Sprint 3 includes the console | Resolved the other way on 2026-09-26: rows stay as backlog; A0-00 still files the asks |

---

## 4. Task details

Each task ends with `pnpm check` green, a screenshot or Playwright trace on the PR, and a commit `feat(frontend): …` citing the issue and ADR 0015.

### A0-00: Commit the plan, file the asks

- [x] Step 1: add `frontend/docs/admin-plan.md`. (on `dev`)
- [ ] Step 2: file BA-1..BA-12 from §10 (label `backend`; body cites the artboard and the contract line) and D1..D8 from §11 (label `design`); link them from the "Admin console (UC9 GUI)" issue. Status 2026-10-01: BA-1..BA-6 and BA-8..BA-12 are filed as #246–#256 (BA-7 was answered by #242 before filing); D1..D8 and the umbrella issue are not filed yet.
- [ ] Step 3: `git commit -m "docs(frontend): admin console plan"`.

### A1-01: Admin API layer

**Interfaces produced:** `adminListingsOptions(q: {page; size; status?})`, `adminListingOptions(id)`, `adminPolicyOptions(id)`, `useCreateListing()`, `useReviseListing()`, `useChangeStatus()`, `useDeleteListing()`, `useConfigurePolicy()`; `ApiError.errors: unknown`; `fieldViolations(e): Record<string, string>`; `toCents(s): bigint | null`, `formatSgd(s): string` ("S$46.80"); `statusLabel(code)`.

- [x] Step 1: failing tests. `envelope.test.ts`: `ApiError` keeps `errors`. `client.test.ts`: a 400 body `{errors:[{field:"listing.originalPrice",message:"…"}]}` reaches `ApiError.errors`. `money.test.ts`: `toCents("12.8") === 1280n`, `"0.00"` → 0n, `"1.234"` → null; `formatSgd("89")` → `S$89.00`. `listing-status.test.ts`: the three codes plus an unknown code → "Unknown". (2026-09-29)
- [x] Step 2: extend `ApiError` with `errors` and have the interceptor copy `candidate.errors` as-is (still `unknown`). `fieldViolations` accepts only `{field: string, message: string}[]` and strips the `listing.` prefix. (2026-09-29)
- [x] Step 3: `admin-api.ts` wraps the generated names in `queryOptions({...pageOptions({query}), select: (r) => unwrapResult(r, {allowVoid:false})})` and similar; mutations run `unwrapResult` on success. This is the one file that knows the generic operationIds (BA-9). (2026-09-29)
- [x] Step 4: ADR 0009 addendum line: `ApiError.errors` carries the safe `FieldViolation[]`. (2026-09-29)

### A1-02: Admin MSW

- [x] Step 1: `admin-msw.ts` exports `createAdminStore(seed)` and `adminHandlers(store, {role: 'admin' | 'buyer'})`. It mirrors the backend rules: version check → 412 `CATALOG_412_002`; publish needs positive stock → 409 `CATALOG_409_002`; frozen-field change after publish → 409; duplicate `productNo` → 409 `CATALOG_409_003`; delete of a non-draft → 409; policy absent → 409 `CATALOG_409_001`; policy on a non-on-sale listing → 409; `amount` not below the price → 400 `CATALOG_400_001`; buyer role → 403 `COMMON_403_*` on every admin path. (2026-09-29)
- [x] Step 2: seed from the 4a.2 rows (BB-100481 … BB-100472, versions 4, 7, 2, 1, 9, 3, 5, 0) with the artboard copy; 62 total, generated up to that count. (2026-09-29)
- [x] Step 3: `browser-msw.ts` mounts it for `?msw=1&me=1&admin=1` (`admin=0` → 403); `&conflict=1` bumps the server version on the next write so 4b.4 can be walked. (2026-09-29)
- [x] Step 4: `render-route.tsx` gains `serveAdmin()` and `serveNonAdmin()`. (2026-09-29)

### A1-03: Route tree, guard, shell

- [x] Step 1: failing `routes/admin.test.tsx`: anonymous `/admin` → `/login?returnTo=%2Fadmin`; signed-in buyer (probe 403) → "You don’t have access to the admin console" with no storefront header; admin → rail with "Dashboard" and no storefront "Sign in"/avatar header; probe 500 → Global error card. (2026-09-29)
- [x] Step 2: `admin-guard.ts` (2026-09-29):

```ts
export class AdminAccessDenied extends Error {}
export const adminProbeOptions = () => ({ ...adminListingsOptions({ page: 1, size: 1 }), staleTime: 30_000 })
export async function requireAdmin(qc: QueryClient) {
  try { await qc.query(adminProbeOptions()) }
  catch (e) { if (e instanceof ApiError && e.status === 403) throw new AdminAccessDenied(); throw e }
}
```

- [x] Step 3: `routes/_authenticated/admin.tsx`: `staticData: { shell: 'admin' }`, `beforeLoad: ({context}) => requireAdmin(context.queryClient)`, `component: AdminShell`, `errorComponent` renders `AccessDeniedCard` for `AdminAccessDenied` or an `ApiError` 403 (a role revoked mid-session), else `RouteErrorCard`. The parent `_authenticated` handles anonymous visitors. (2026-09-29)
- [x] Step 4: `router.tsx` declares `interface StaticDataRouteOption { shell?: 'admin' }`; `__root.tsx` skips `<Header/>` when `useMatches()` has a match with `staticData.shell === 'admin'`. (2026-09-29)
- [x] Step 5: `AdminShell`: 240px rail on `bg-foreground` (`background-inverse`), dark isologo (PNG from the artboard until CR-7; `alt="BlocBuy"`), nav as router `Link`s (Dashboard, Listings); Payments and Bloc price rules hidden (Q3, A3-06); the user block shows initials and `nickname ?? userId` from `useSession()` with the label "Admin". Content `min-w-[1024px] p-6 text-sm` (Q12). (2026-09-29)
- [x] Step 6: `AccessDeniedCard` in `ErrorCard.tsx` with the Errors-artboard strings verbatim, primary "Browse listings" → `/`, secondary "My blocs" → `/account`, and a wordmark-only bar. (2026-09-29)

### A1-04: Listings table

- [x] Step 1: `listings-search.ts` (valibot): `tab` picklist `all | draft | on-sale | off-sale` (default `all`, stripped from the URL), `page` ≥1 default 1, `size` 1..100 default 20. `tabToStatus()` maps to contract codes in one place. (2026-09-29)
- [x] Step 2: failing tests: 9 column headers (PRODUCT NO., TITLE, CATEGORY, PRICE, STOCK, SOLD, STATUS, VER, menu), right-aligned tabular numbers, "On sale" chip success-soft and others neutral, "v4" cells; tab counts hidden while loading; Draft empty shows "No drafts" + the lifecycle copy + a secondary "New listing", with the header button switching to `outline`; `?page=99` redirects to the last page (buyer note 40); the pager reads "Showing 1–20 of 62 · Page 1 of 4". (2026-09-29)
- [x] Step 3: HeroUI `Table` (no react-table; §12 note 4), `Tabs` as router links, `Skeleton` × 8 rows in the column grid; title cell links to the editor; error is `Alert status="danger"` "We couldn’t load the listings" + "Try again". No sort indicators, no search. (2026-09-29)
- [x] Step 4: `use-status-counts.ts` runs `useQueries` over `size=1` for all, draft, on_sale and off_sale, reading `total` (shared with the probe and the dashboard). (2026-09-29)

### A1-05: Listing form and new draft

**Form values:** `{ productNo; title; subtitle; description; category; originalPrice: string; stock: number; coverImage; images: string[] }`.

- [x] Step 1: `listing-form.schema.ts` mirrors the contract: `productNo` pattern (create only); `title` trimmed, 1..256; `subtitle` ≤256; `description` ≤10000; `category` in `CATEGORIES` or the loaded value; `originalPrice` `^\d{1,16}(\.\d{1,2})?$` and `toCents > 0` ("Enter a price above S$0.00"); `stock` integer ≥0; `coverImage` empty or `https://` URL ≤1024; `images` ≤20, each `https://` ≤1024, blanks dropped. (2026-09-29)
- [x] Step 2: `listing-form.ts`: `toListingContent(values)` omits empty optionals (PUT replaces), sets `currency: 'sgd'`, drops blank image rows; `fromListing(dto)` is the inverse and keeps `originalPrice` **as the server string** (no reformatting, so the frozen-field equality check can never fail on formatting). (2026-09-29)
- [x] Step 3: `ListingForm.tsx`, one column: `TextField` (Title, Subtitle, Product number on create), `Select` (Category), `TextField inputMode="decimal"` with an `S$` prefix (Solo price; not `NumberField`, which is float-based), `NumberField` (Stock, integer), read-only "SGD" (Currency), `TextField` (Cover image URL), `ImageUrlsField` ("Image URLs · up to 20", "Add another URL", remove per row, thumbnail with `ImageFallback`), `TextArea` (Description; §11 D7). Helper text from 4b.1 verbatim ("Shown on cards and on the listing page.", "Decimal string, SGD only. Freezes on publish.", …). Field errors use `FieldError`. (2026-09-29)
- [x] Step 4: `listings.new.tsx`: header crumb "Listings / New listing", "Saved as a draft at version 0. Nothing is visible on the storefront until you publish."; Cancel and "Save draft" (disabled while pending: POST is not idempotent). The sidebar shows Draft v0, Publish disabled with "Publishing needs a positive price and positive stock. Fill those in and save first.", and the rule panel as unavailable. On success, `setQueryData(adminListing)` and `navigate({ to: '/admin/listings/$productId', replace: true })`; invalidate the page queries. A 409 `CATALOG_409_003` goes to the Product number field ("That product number is already in use."); a 400 with `errors` maps through `fieldViolations`; other 400s show a form-level alert. (2026-09-29)

### A1-06: Editor for an existing listing

- [x] Step 1: failing tests: draft (4b.2 header "Hokkaido milk, 12 cartons · BB-100472 · draft · v0", all fields editable); on sale (4b.3: Solo price, Currency and Stock read-only with the "Frozen" chip and their 4b.3 reasons; Sold read-only "Read-only, maintained by the server."); save sends `expectedVersion` = loaded version and unchanged frozen values; after save the header shows v5 and the form is clean; unknown id → an in-shell not-found ("This listing no longer exists", link to Listings). (2026-09-29)
- [x] Step 2: loader `queryClient.query({ ...adminListingOptions(id) })` **without** `staleTime: 'static'` (§12 note 5); `useSuspenseQuery` in the component; `ListingForm` keyed by `version`, so a Reload or a save remounts it from server data. (2026-09-29)
- [x] Step 3: `EditorSidebar`: Status chip + `v{n}` chip; the lifecycle trail from 4b.2 / 4b.3 ("Draft — you are here", "On sale — after publishing", "Off sale — reversible later"; published variant); helper "Draft → on sale → off sale → on sale. There is no way back to draft." (2026-09-29)
- [x] Step 4: `useBlocker({ shouldBlockFn: () => isDirty && !isSubmitting, withResolver: true })` with `UnsavedChangesDialog` ("Discard your changes?", Keep editing / Discard); §11 D5. (2026-09-29)

### A1-07: Lifecycle actions

- [x] Step 1: `lifecycle.ts` (pure): `actionsFor(status, {dirty, stock, price})` → `publish` (draft, enabled only when not dirty, stock > 0 and price > 0), `takeOffSale` (on sale), `putBackOnSale` (off sale), `delete` (draft) or `deleteBlocked` (other). Table tests. (2026-09-29)
- [x] Step 2: Publish `AlertDialog size="sm"`: "Publish this listing?" / "It becomes visible on the storefront immediately and buyers can start blocs on it." / box "These three fields freeze for good" listing Solo price, Currency and Stock with **current saved values** / "Title, subtitle, description, images and category stay editable. Price, currency and stock do not — and there is no route back to draft." / Cancel, "Publish listing". (2026-09-29)
- [x] Step 3: Take off sale, Put back on sale and Delete dialogs use the §11 D2 proposed copy. On delete success: toast "Draft deleted", then navigate to `/admin/listings?tab=draft`. (2026-09-29)
- [x] Step 4: the delete-blocked text replaces the button (no greyed button): "Not available. This listing has been published; take it off sale instead." A 409 `CATALOG_409_004` on a draft reads "Not available. This draft already has price or bloc history." (2026-09-29)
- [x] Step 5: `RowMenu` (HeroUI `Dropdown`; §12 note 10) on table rows: "Edit", plus the one lifecycle action `actionsFor` allows, opening the same dialog (Q14). (2026-09-29)
- [x] Step 6: every mutation success does `setQueryData(adminListingOptions(id).queryKey, envelopeOf(dto))` and invalidates the `pageQueryKey` family. Nothing is optimistic. (2026-09-29)

### A1-08: Conflict and error mapping

- [x] Step 1: `listing-errors.ts` maps codes to copy: `CATALOG_412_002` → conflict; `CATALOG_409_002` → "This listing changed state. Reload to see what you can do now."; `CATALOG_409_003`, `409_004` as above; `COMMON_403_*` → rethrow to the route boundary (S1); 401 → "Your session ended. Sign in again to save." with a link to `/login?returnTo=` (the form stays in memory). (2026-09-29)
- [x] Step 2: `ConflictBanner`: `Alert status="warning"` pinned at the top of the editor: "This listing was updated by someone else" / "You loaded v{loaded}; the server now has v{server}. Reload to see the latest version — your unsaved changes will be lost." + "Reload". `server` comes from a background `fetchQuery` of the admin read that does **not** touch the form (BA-12); until it answers, the line reads "…the server has a newer version." (2026-09-29)
- [x] Step 3: while the banner shows, Save and lifecycle actions are disabled; Reload writes the fresh read to the cache and remounts the form. (2026-09-29)
- [x] Step 4: with a field error and a conflict together, the side card "Two problems to clear" lists both (4b.4). (2026-09-29)
- [x] Step 5: tests for 412 on save, publish and delete; 409 state; and the pair. (2026-09-29)

Note (#242): a team-terms write also increments the product `version`, so a terms edit made elsewhere surfaces here as the ordinary 412 `CATALOG_412_002`; no extra mapping is needed.

### A2-01: Bloc price rule panel

- [ ] Step 1: `policy.ts`: `policyState(policy | undefined, listingStatus, now)` → `unavailable-draft | unavailable-off-sale | none | scheduled | active | expired` (validFrom inclusive, validUntil exclusive). `getTeamPricePolicy` 409 `CATALOG_409_001` → `none`.
- [ ] Step 2: `sgt.ts`: `toZoned(iso)` and `toInstant(zdt)` over `@internationalized/date` in `Asia/Singapore`; `formatWindow()` → "Valid 1 Sep 2026, 12.00 am → 30 Sep 2026, 11.59 pm SGT" (display: end minus 1 minute, because the end is exclusive; §6 Q13).
- [ ] Step 3: panel per 4b.5. Bloc price in accent-soft-foreground over the struck solo price; chips "No rule yet" / "Active" / "Expired 14 Sep" / "Starts 1 Oct" (scheduled; §11 D3) / "Not saved"; body copy verbatim; actions "Add a rule" / "New revision" / "Reload". On a draft: "Add a rule after you publish. The bloc price must be strictly below the solo price." On off sale: "Put the listing back on sale to add a revision."
- [ ] Step 4: the revision form (inline in the panel): Bloc price (decimal string), Valid from and Valid until (`DatePicker granularity="minute"`, SGT). Schema: amount pattern, >0, `toCents(amount) < toCents(originalPrice)` → "Must be strictly below the solo price of S$12.80."; until > from → "Must be after the start of the rule."; until > now. Sends `expectedVersion` = loaded policy version, or 0.
- [ ] Step 5: **replace-now warning** when a rule is active and the new validFrom is in the future: "Saving replaces the active rule now. Buyers see no bloc price until {validFrom}." (backend: the latest revision replaces, no fallback). Footnote from 4b.3: "Saving appends a revision. Prices already locked into a quote never change."
- [ ] Step 6: 412 `CATALOG_412_001` → panel conflict state ("You loaded v3; the server now has v4" after a background refetch; Reload); 409 → unavailable state; domain 400 → the field named in `errors` (BA-6, #323), else a form alert.
- [ ] Step 7: add the `@internationalized/date` 3.12.4 exact pin (AGENTS.md rule 11; PR says why) and confirm `pnpm knip` is clean.

### A2-02: Dashboard

- [x] Step 1: heading "Dashboard" + "{Weekday d Month yyyy} · all times SGT" (`sgt.ts`, on the console's one-minute clock) + a secondary "New listing". (2026-10-01)
- [x] Step 2: `StatCards`: Drafts ("private until published"), On sale ("visible on the storefront"), Off sale ("delisted, no new blocs"); neutral surface panels, no accent, no trend. Open blocs reads `openDeals` from `counts` (#290); the artboard's "filling across N listings" detail has no source and is left out. Counts are withheld (skeleton) while loading; a failed counts read shows "—". `withActivePolicy` has no card on 4a.1 and is not shown. (2026-10-01)
- [x] Step 3: `RecentListings`: the table component with 7 columns (no SOLD, no rule line, no row menu), `page=1&size=5`, "View all" → `/admin/listings`. The loader waits for the five rows only; the counts fill in after. (2026-10-01)
- [x] Step 4: empty (total 0): "No listings yet" + "New listing" (§11 D4), with the header's button dropped to outline. Error: danger alert + Try again. (2026-10-01)

### A2-03: Playwright

- [ ] Step 1: `admin.spec.ts` "anonymous" (every target): `/admin` → `/login?returnTo=%2Fadmin`.
- [ ] Step 2: "non-admin" (every target, `page.route` stubs `me` 200 and admin 403): the 403 card is shown and the storefront header is absent.
- [ ] Step 3: "admin, stubbed" (every target, `page.route` with an in-spec store): create draft → publish dialog shows the three values → status On sale v1 (publish from v0) → a forced 412 shows the banner → Reload.
- [ ] Step 4: `@admin` (dev only; skipped unless `e2e/.auth/admin.json` exists): the list loads and one draft is created, then deleted. `e2e/README.md` gets the seeding command and the "admin role must be granted in the dev DB" note.

### A2-04: Docs

- [ ] Step 1: `design-screens.md` §4.7 Admin console, with the same citation style (`file › artboard`), including the §11 gaps.
- [ ] Step 2: ADR 0015 "Admin console: second shell, probe guard, pessimistic versioned writes" (Nygard, under 60 lines); README row.
- [ ] Step 3: AGENTS.md Layout gains `src/features/admin/` and `routes/_authenticated/admin*`.

### A3-01: Role guard (BA-1, #282)

- [x] Step 1: failing tests. `admin-guard.test.ts`: a session with `roles: ["admin"]` passes without a network call; `["buyer"]` throws `AdminAccessDenied`. `routes/admin.test.tsx` keeps its four cases; the All-tab count test in `routes/admin-listings.test.tsx` still shows 62. (2026-09-30)
- [x] Step 2: the session read carries `roles` (default `[]`); the mock `me` answers `roles: ["admin"]` under `admin=1` and `["buyer"]` otherwise. (2026-09-30)
- [x] Step 3: `requireAdmin` reads the session's roles and drops the probe. The All-tab count loses the probe's cached result, so `use-status-counts.ts` asks for its own `size=1` page. (2026-09-30)
- [x] Step 4: keep the 403 boundary: a role revoked mid-session still reaches Access denied from any admin read. The rail shows the role from `roles`. (2026-09-30)

### A3-08: Product number from the server (BA-2, #288)

- [x] Step 1: failing tests. `listing-form.schema.test.ts`: create mode has no `productNo`; `routes/admin-listing-new.test.tsx`: the create request omits `productNo` and the new editor shows the server's `BB-` number. (2026-09-30)
- [x] Step 2: remove the create field (artboard 4b.1: "the server assigns it on save") from `listing-form.schema.ts`, `listing-form.ts` and `ListingForm.tsx`; the editor keeps the read-only number. (2026-09-30)
- [x] Step 3: the admin mock generates the next `BB-` number when `productNo` is omitted or `null`, and still answers 409 `CATALOG_409_003` for a supplied duplicate. (2026-09-30)

### A3-09: Server version from the 412 (BA-12, #286)

- [x] Step 1: failing tests. `envelope.test.ts`: `conflictVersion(e)` returns `currentVersion` from a 412 `ApiError` whose `errors` is `{currentVersion: number}`, else `null`. `admin-msw.test.ts`: every 412 carries it. `admin-listing-edit.test.tsx`: the banner shows "the server now has v{n}" from the error, before any refetch. (2026-09-30)
- [x] Step 2: add `conflictVersion` beside `fieldViolations`; the admin mock's 412 bodies (listing and policy) carry `errors: {currentVersion}`. (2026-09-30)
- [x] Step 3: `use-listing-mutations.ts` passes the version to `ConflictBanner`; the background refetch stays as the fallback when the error has none. (2026-09-30)

### A3-02: Counts endpoint (BA-3, #290)

- [x] Step 1: failing tests. `admin-msw.test.ts`: `GET /admin/products/counts` answers the store's totals per status plus the seeded `openDeals`, and 403 for a buyer. `routes/admin-listings.test.tsx`: the tabs still read All 62, Draft 14, On sale 34, Off sale 14, from one `counts` request and no `size=1` page; after a publish the counts move. (2026-09-30)
- [x] Step 2: `admin-api.ts` gains `adminCountsOptions()` (`countsOptions`, `unwrapResult`, `adminRetry`); `forgetListingPages` also refreshes the counts. The admin mock answers `counts` ahead of `/:productId`. (2026-09-30)
- [x] Step 3: `use-status-counts.ts` reads `counts`; All is `draft + onSale + offSale` (the backend counts the same non-deleted rows the page filters use). A missing field reads as 0; the tabs stay hidden until the read arrives. (2026-09-30)

Next stack (2026-10-01): A3-05, then A3-03 (subline only), then A2-02. Each is one PR on `dev`. A3-07 is the next candidate after A2-02 now that #323 has merged: its listing-form part can go first, and its `PricePolicyPanel.tsx` part waits for A2-01, which creates that panel.

Follow-up FU-01 (2026-10-01, not an A-row; PR pending): the listings route (A1-04) now rethrows a 403 from a refetch of the page or the tab counts, and the editor (A1-06) one from a refetch of the listing or the conflict banner's version read, as the dashboard does since #328, so a role revoked after the page loaded reaches Access denied instead of leaving stale data on screen. Tests: `routes/admin-listings.test.tsx`, `routes/admin-listing-edit.test.tsx`.

A3-07a, the listing-form part of A3-07 (2026-10-01; PR pending): a field-specific `CATALOG_400_002` (#323) lands on the field its `listing.<field>` path names, through the same `fieldViolations` path as bean validation; a path with no form field (top-level `productNo` since A3-08) reads in the form's alert, and a 400 without `errors` keeps the plain form-level message. The client already routed both through `refusalOf` since A1-08, so the change is tests and comments, plus the admin mock now answers bean failures on a listing body with `COMMON_400_002` as `GlobalExceptionHandler` does. The `PricePolicyPanel.tsx` part waits for A2-01 (its step 6).

### A3-05: Delete-blocked reason with counts (BA-5, #300)

- [x] Step 1: failing tests. `admin-msw.test.ts`: the single read carries `references` and `deletable`; page items do not. `admin-listing-edit.test.tsx`: a published listing reads 4b.3's "Not available. This listing has been published and has 26 bloc records, 61 quotes and 3 price rules." and, with no history, keeps "…has been published; take it off sale instead."; a draft with history shows the reason instead of Delete draft ("Not available. This draft has 1 quote."); a clean draft keeps Delete draft. (2026-10-01)
- [x] Step 2: the admin mock tracks deals, quotes and policies per listing and answers `references` and `deletable` on `GET /admin/products/{id}` only. (2026-10-01)
- [x] Step 3: `EditorSidebar` reads `references` for the count line and `deletable` to choose between Delete draft and the reason. A missing field falls back to today's reason without counts. DELETE still maps 409 `CATALOG_409_004` and 412 as before, because `deletable` is a snapshot. (2026-10-01)

### A3-03: Bloc price subline (BA-4, #293)

- [x] Step 1: failing tests. `bloc-rule.test.ts`, `ListingsTable.test.tsx` and `admin-listings.test.tsx`: an active rule reads "Bloc price S$71.00 · valid to 30 Sep" under the title; an expired one "Bloc price S$32.00 · expired 14 Sep" and a future one "… · starts 1 Oct"; a published listing with no rule reads "No bloc price rule yet" and a draft "Draft · not published" (both 4a.2 rows); the start is inclusive, the end exclusive, and the day is the SGT day of the last valid minute (§6 Q13). (2026-10-01)
- [x] Step 2: the admin mock puts `latestPolicy` (the latest revision without `productId`, or `null`) on page items, the single read and the create, revise and status responses; a new revision shows on the next page read. (2026-10-01)
- [x] Step 3: `ListingsTable` renders `blocRuleLine` under the title with `formatSgd` and the SGT day from `deal-time.ts`, on a one-minute clock kept out of render; the rail item stays hidden (Q3). (2026-10-01)

### A3-04, A3-06, A3-07

- [ ] Each row starts only when its ask is merged into `contracts/openapi.json`. Run `pnpm gen:api` with the `api-client-regen` skill, then make the change in the files listed and add tests for the new field.

---

## 5. Test strategy

| Layer | What | Where |
|---|---|---|
| Unit (pure, no DOM) | valibot schemas (listing, policy, search); `toCents` / `formatSgd`; `statusLabel`; `actionsFor`; `policyState` at the boundaries (now = validFrom and now = validUntil); `sgt` round-trip including 11.59 pm and midnight; `toListingContent` / `fromListing` (optional omission, price string kept); `fieldViolations`; `listing-errors` code map | `src/features/admin/*.test.ts`, `src/api/*.test.ts` |
| Component + MSW (Vitest, happy-dom) | Table states, tabs, pager; form field errors; dialogs; conflict banner; policy panel states. Network comes from `adminHandlers(store)` via `server.use` (the generated handlers answer 501 unless overridden). | `src/features/admin/*.test.tsx` |
| Route (real route tree, `renderRoute`) | Guard matrix (anonymous / buyer 403 / admin / 500); page-past-last redirect; create → navigate; editor save → version bump; 412 → banner → Reload; 404; unsaved-changes blocker | `src/routes/admin*.test.tsx` |
| E2E (Playwright) | Anonymous redirect and 403 card on every target; stubbed admin happy path with a forced 412; `@admin` real-dev smoke behind a storage state | `e2e/admin.spec.ts` |
| Manual | `?msw=1&me=1&admin=1` walkthrough of 4a.1–4b.5 and `&conflict=1`; screenshots per artboard on each PR | PR evidence |

The MSW store implements the backend rules (§2.2), so the rules are tested at the UI boundary. It does not replace backend tests. Its codes are copied from `CatalogErrorCode`, and a comment in the file names the source.

## 6. Open questions for the user

1. **Scope.** #32, #49 and the Sprint 2 plan define UC9 as "API-only, no GUI", and no user story covers the console. Is the admin GUI in Sprint 3, and where does it rank against the buyer T3 rows?
   - **Answered 2026-09-26: no. The admin console is not in Sprint 3.** The user's rule: "If it can be finished with full unit + E2E test suites in the first half of Sprint 3, then yes."
   - Window: Sprint 3 is taken as Mon 2026-09-28 to Fri 2026-10-09, the Sprint 2 shape, because `docs/agile/README.md` has no Sprint 3 row yet. The first half ends Fri 2026-10-02: 5 working days for one frontend developer (@limjiechao with agent help).
   - Size: A1 plus A2, with Vitest unit and route tests and Playwright `page.route` specs against `vite preview`, is 10.5 + 4.25 = 14.75 ideal days (§3 `issue` cells). That is about three times the window, and A1 alone is twice it.
   - Queue: P1 buyer work comes first, at about 5.5 days. It covers review follow-ups on six coded PRs from #200 (T3-04) on, T3-05, and FB-02 to FB-05. So even the smallest usable slice (A1-01 to A1-04, a read-only listings console, 4.5 days) fits only if that work is postponed.
   - Review: PRs go up one at a time. Sprint 2 merges took 7 to 13 hours from opening (#164, #181, #189), so at most two merge a day, and the 12 PRs already queued fill the window.
   - Blocked regardless: the `@admin` run against dev cannot pass until the role is granted there (Q11). The D2 dialog copy (Q7) is not approved.
   - In Sprint 3: A0-00 only (this plan, and filing BA-1 to BA-12 and D1 to D8). A1-01 to A1-08 and A2-01 to A2-04 are deferred to a later sprint's planning with the sizes in §3; A3 stays gated on the backend.
2. **Product number.** The contract requires it from the client at create; the artboard says the server assigns it. Show a create-only field (planned), or push BA-2 first?
3. **"Bloc price rules" rail item.** No artboard for its page. Hide it (planned), point it at the listings table, or ask design for a page (needs BA-4)?
   - Still open on 2026-10-01: #293 gives the count (`counts.withActivePolicy`) but there is still no page, so the item stays hidden.
4. **Table bloc-rule subline.** Omit until BA-4 (planned), or accept 20 `getTeamPricePolicy` calls per page?
   - Resolved by #293: page items carry `latestPolicy`, so A3-03 adds the subline with no extra calls.
5. **Open blocs stat.** Omit the card (planned) or show "—" with a caption?
   - Resolved by #290: the card reads `counts.openDeals` (A2-02); its "filling across N listings" detail has no source and is left out.
6. **Policy on drafts.** The backend refuses rules unless the listing is on sale; the artboard implies drafts can have one. Accept the copy change (planned) or raise BA-11?
7. **Undrawn dialogs.** Take off sale, put back on sale, delete, unsaved changes: approve the §11 D2 copy?
8. **Description.** In the contract and brief, missing from the 4b.1 field list. Include it (planned)?
9. **Group terms** (seats per bloc, lifetime): a listing cannot start a bloc without them (NFR-29). Who designs the fields, and is PR-H (BA-7) scheduled?
10. **HeroUI Pro.** The brief puts Pro blocks first (`app-layout`, `sidebar`, `kpi`, `data-grid` exist). The package is not installed. Is there a licence and a registry token for CI? The plan assumes OSS only.
11. **Admin account on dev.** Who grants the `admin` role on the dev DB, and for which Google account (demo and `@admin` E2E)? Still open on 2026-09-26: #68 was closed on 2026-09-24 without recording a grant on the dev DB.
12. **Below 1024px.** Horizontal scroll (planned) or a "use a wider screen" notice?
13. **Rule end time.** The backend's `validUntil` is exclusive. Should the picker ask for "last minute valid" (UI adds 1 minute) or the exact end instant (planned: exact instant, displayed minus 1 minute)?
14. **Row menu contents.** Not specified. Plan: Edit plus the one allowed lifecycle action.
15. **#183 interaction.** START re-prices from the latest policy, so a new revision changes what buyers mid-checkout pay. Is the replace-now warning enough, or should the admin form block revisions while quotes are live?

## 7. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| No admin account on dev or local (#68: grants do not travel with git) | Cannot demo; `@admin` E2E stays skipped | Q11; MSW seed and `page.route` stubs cover every path; manual acceptance checklist from `admin-listings.md` |
| The probe guard conflates "not admin" and "disabled account" | A disabled admin sees "ask the team" copy | Acceptable (both are 403 by contract); BA-1 removes the probe |
| A future-dated revision instantly removes the active bloc price (latest replaces, no fallback) | Storefront loses its bloc price unexpectedly | Replace-now warning (A2-01 step 5); design note D3 |
| PUT is full replacement | An omitted optional field silently clears data | `toListingContent` always builds the full object from the loaded form; round-trip test |
| Frozen fields compared by the backend after publish | Reformatting "89.00" to "89" could trigger 409 | The price string is kept verbatim from the read (§12 note 8) |
| 412 message needs the server version, which the body lacks | Banner shows no number or needs an extra call | Background refetch; BA-12 |
| Session expiry during a long edit | Loss of typed content | 401 shows an inline sign-in prompt and the form is kept; `returnTo` brings the admin back (unsaved edits are still lost across the Google redirect; noted in the copy) |
| Generated-name changes during contract regeneration | Stale imports, query keys or MSW override names | #253 supplies resource-specific IDs and migrates consumers together; run `pnpm gen:api`, typecheck and tests; coordinate with @limjiechao before merge (BA-9) |
| External image URLs (http, hotlink blocks, dead hosts) | Broken previews on the storefront | `https://` only; fallback tile everywhere; thumbnails in the editor |
| Time zone mistakes in rule windows | Rules start or end at the wrong hour | All conversion in `sgt.ts` with boundary tests; the display always says SGT |
| Scope creep into Tier 3 (payments queue) | Time lost on an endpoint that does not exist | A3 rows only start on a merged contract |

## 8. PR map

| PR | Scope | Tasks | ADRs cited | Reviewer |
|---|---|---|---|---|
| PR-AA `docs(frontend): admin console plan` | plan + issues | A0-00 | — | @limjiechao |
| PR-AB `feat(frontend): admin API layer and MSW store` | API + seed | A1-01, A1-02 | 0003, 0009, 0011 | @limjiechao |
| PR-AC `feat(frontend): admin shell, guard and access denied` | shell | A1-03 | 0002, 0015 | @limjiechao |
| PR-AD `feat(frontend): admin listings table` | S3 | A1-04 | 0001 | @limjiechao |
| PR-AE `feat(frontend): listing form and new draft` | S4 | A1-05 | 0001, 0015 | @limjiechao; backend owner for BA-2 |
| PR-AF `feat(frontend): listing editor with frozen fields` | S5, S6 | A1-06 | 0015 | @limjiechao |
| PR-AG `feat(frontend): listing lifecycle dialogs` | S5, S6 | A1-07 | 0015 | @limjiechao |
| PR-AH `feat(frontend): version conflict banner` | S7 | A1-08 | 0009, 0015 | @limjiechao |
| PR-AI `feat(frontend): bloc price rule panel` | S8 | A2-01 | 0015 | @limjiechao; @EricWang1358 (policy rules) |
| PR-AJ `feat(frontend): admin dashboard` | S2 | A2-02 | 0001 | @limjiechao |
| PR-AK `test(frontend): admin journeys` | e2e | A2-03 | 0011 | @limjiechao |
| PR-AL `docs(frontend): admin screens spec and ADR 0015` | docs | A2-04 | 0008, 0015 | @limjiechao |

## 9. ADRs

- **New: 0015 Admin console: second shell, probe guard, pessimistic versioned writes.** Context: artboard density; `me` has no roles; 412 semantics. Decision: §12 notes 1–7. Consequences: one extra request per 30s of admin navigation; the probe goes away with BA-1.
- **Addenda:** 0009 (`ApiError.errors`), 0001 (admin uses HeroUI `Table`, still no react-table), 0002 (admin guard is UX only; the backend enforces RBAC on every call).

## 10. Backend asks, ready to file

Each body is **Ask / Why (artboard + contract line) / Acceptance / Frontend fallback**, as in plan §10.

- **[BA-1] `/auth/me` returns `roles`.** Answered by #282. Ask: add `roles: string[]` (Sa-Token `getRoleList`) to `MeResponse`. Why: CR-9; the admin guard currently probes an admin endpoint. Acceptance: an admin session → `["buyer","admin"]` or `["admin"]`; the contract is additive. Fallback: probe.
- **[BA-2] Server-assigned product number.** Answered by #288 (optional on create; a supplied number is kept). Ask: drop `productNo` from `CreateListingRequest` and generate one (for example `BB-` + sequence), or confirm the client-supplied number is intended. Why: 4b.1 note "the server assigns it on save". Fallback: create-only field.
- **[BA-3] Admin counts.** Answered by #290 (`counts`, `{draft, onSale, offSale, openDeals}`; none marked required). Ask: `GET /api/v1/admin/products/counts` → `{draft, onSale, offSale, openDeals}`. Why: 4a.1 stat cards, tab badges, rail. Fallback: three `size=1` calls; no Open blocs.
- **[BA-4] Latest policy on the admin listing.** Answered by #293 (`latestPolicy` or `null` on every admin read; the count is `counts.withActivePolicy`, listings with a rule valid now). Ask: `AdminListingResponse.latestPolicy` (nullable) + count. Why: 4a.2 subline, rail count. Fallback: none shown. The standalone policy operation is `getTeamPricePolicy` after #253.
- **[BA-5] Reference counts.** Answered by #300 (both `references {deals, quotes, policies}` and `deletable`, on the single admin read only). Ask: `references {deals, quotes, policies}` or `deletable` on the admin read. Why: 4b.3 delete-blocked copy. Fallback: reason without counts.
- **[BA-6] Field paths on domain 400s (CR-12).** Answered by #323 (`errors: [{field, message}]` on field-specific `CATALOG_400_001` and `CATALOG_400_002`; paths `amount`, `validUntil`, `listing.<field>`, `productNo`, `teamPrice.<field>`). Ask: `CATALOG_400_001` / `400_002` carry `errors: [{field, message}]` (`amount`, `validUntil`, `listing.originalPrice`). Fallback: client pre-validation plus a form alert.
- **[BA-7] Group terms on `ListingContent` (NFR-29 PR-H).** Answered by #242 (`eed59da`) as a separate endpoint, not `ListingContent` fields: `PUT /api/v1/admin/products/{productId}/team-terms` (`configureTeamTerms`). Both terms are required on the request: `targetSize` ≥2 with no maximum, `lifetimeSeconds` 2400..86400. Terms can be edited in every lifecycle state; the optional `teamPrice` needs an on-sale listing. No longer to be filed.
- **[BA-8] Payments attention queue.** Ask: an admin-only read of participations in compensation-required state with the 4b.6 columns. Tier 3.
- **[BA-9] Distinct operationIds.** Answered by #324 (issue #253). The six original listing operations use the IDs in §2.2, and the four team-price operations use `getTeamPricePolicy`, `configureTeamPricePolicy`, `issueTeamPriceQuote` and `getTeamPriceQuote`. HTTP behavior is unchanged. Regenerate the client and migrate consumers together; notify @limjiechao before merge. `configureTeamTerms` and `counts` retain their existing IDs.
- **[BA-10] Policy revision history** read (optional). Implemented locally for #254 (merge pending): `GET /api/v1/admin/products/{productId}/team-price-policy/revisions` (`pageAdminTeamPricePolicyRevisions`) returns paged `PolicyResponse` items in descending version order, including expired and future policies but excluding soft-deleted revisions. Existing listings without policies return an empty page; missing/deleted listings return 404. Uses the admin role, catalog capability and active-account checks. [History semantics](../../backend/docs/design/team-price-quotes.md#admin-revision-history--2026-10-02-254). Frontend history remains a follow-up.
- **[BA-11] Policy on drafts.** Either allow it (the price rule prepared before publish) or confirm the on-sale requirement is intentional so design copy changes.
- **[BA-12] Current version in 412 `errors`** (`{currentVersion}`) for both listing and policy. Answered by #286.

## 11. Design gaps for the design owner

- **D1** Rail counts (Bloc price rules 18, Payments 2). Payments has no data source until BA-8. Since #293 Bloc price rules could show `counts.withActivePolicy` (listings with a rule valid now), but its page is not drawn (Q3), so the item stays hidden. The Open blocs stat has a source since #290.
- **D2** Undrawn dialogs, proposed copy. *Take off sale:* "Take this listing off sale?" / "Buyers can no longer start or join blocs on it. Blocs already filled keep their orders. You can put it back on sale later." / "Take off sale". *Put back on sale:* "Put this listing back on sale?" / "It becomes visible on the storefront again. Price, currency and stock are unchanged." *Delete draft:* "Delete this draft?" / "It disappears from the console. This cannot be undone." (danger). *Unsaved:* "Discard your changes?"
- **D3** Scheduled rule state (validFrom in the future) and the replace-now warning.
- **D4** Dashboard loading and empty; table error; All / On sale / Off sale empty.
- **D5** Unsaved-changes prompt; toast after save or delete.
- **D6** Group terms panel on the editor page (BA-7, answered by #242): a separate panel with its own save (A3-04), not `ListingForm` fields.
- **D7** The Description field is missing from 4b.1.
- **D8** The copy change "Add a rule after you publish" (BA-11). The product number field on create (BA-2 fallback) is no longer needed: #288 answered BA-2, and A3-08 removes the field.

## 12. Decision notes

1. **Route tree.** `src/routes/_authenticated/admin.tsx` (layout), `admin/index.tsx` (`/admin`), `admin/listings.index.tsx`, `admin/listings.new.tsx`, `admin/listings.$productId.tsx`; Tier 3 `admin/payments.tsx`. Nesting under `_authenticated` reuses the login redirect and `returnTo`. The storefront header is suppressed through `staticData.shell`, not by moving every storefront route under a new pathless layout (that would be a churn-only rename of eight routes).
2. **Role guard = probe.** When A1 was built `me` had no roles (CR-9); #282 added them and A3-01 replaces the probe. The flow map says "the console probes and handles 403". The probe is the "All" count query (`size=1`, `staleTime` 30s), so the dashboard and tabs reuse its result. A 403 anywhere later (role revoked) goes to the same boundary. Only `beforeLoad` redirects (AGENTS.md rule 6). Access denied is rendered in place at `/admin`, not as a redirect, as the flow map's terminal node shows.
3. **Admin generated operations are wrapped once** in `admin-api.ts`; other admin feature files use its named wrappers. The local #253 implementation uses resource-specific generated names in §2.2 and the `pageAdminListingsQueryKey` family for page invalidation. Buyer quotations use `issueTeamPriceQuoteMutation` and `getTeamPriceQuoteOptions` in `use-quote.ts`; browser MSW overrides use the corresponding operation IDs. The dated A1-01/A1-07 steps retain the names used when those tasks were completed.
4. **Table, not list.** Admin data is 8–9 comparable columns, paged server-side, with no sorting (the contract has none; the artboard rationale rejects client sorting of one page). HeroUI `Table` fits and ADR 0001's addendum already chose plain HeroUI `Table` over `@tanstack/react-table`. No new dependency.
5. **Editor reads are fresh.** Loaders elsewhere use `staleTime: 'static'` (rule 7). The editor loader omits it, because the version is the save precondition and a stale cached version guarantees a 412. The form is keyed by `version`.
6. **Pessimistic updates only.** Every write can fail on version (412), state (409) or freeze rules, and publish is irreversible, so no optimistic cache writes. After success the response (new version) is written to the read cache and the page queries are invalidated. Buttons are disabled while pending; POST is not idempotent.
7. **Conflict = warning, Reload only.** As in the 4b.4 rationale: no merge, no auto-retry (the backend doc says clients must re-read, not overwrite). The server version shown in the banner comes from a background refetch that does not touch the form.
8. **Money as strings.** `originalPrice` and `amount` stay strings end to end; `toCents` is used only for comparisons. The price string from the read is sent back unchanged after publish, so the backend's "must remain unchanged" check never trips on formatting. The solo price input is `TextField inputMode="decimal"`, not `NumberField`.
9. **Dates.** HeroUI `DatePicker` with `ZonedDateTime` in `Asia/Singapore` needs `@internationalized/date`. It is already resolved at 3.12.4 through `react-aria-components` 1.21.1 and is added as an exact direct pin, not a new library.
10. **Row menu = HeroUI `Dropdown`.** The artboard's Popover + ListBox choice worked around a Figma kit rendering bug ("Dropdown cannot render standalone"), not a runtime problem. HeroUI 3's `Dropdown` gives menu semantics and keyboard support. Rendered to the artboard's look.
11. **Forms = TanStack Form + valibot** (settled stack; formisch and react-hook-form are not used). Schemas live in `*.schema.ts` (rule 2). Server field errors map through `fieldViolations` into `form.setFieldMeta` errors.
12. **No image upload.** The contract and design both take URLs only. `https://` is enforced client-side to match the CSP.
13. **Tiering.** A1 and A2 are all artboard Tier 1 screens that the current contract supports. A3 collects every row gated on a backend ask, including artboard Tier 3 (payments).

## 13. Self-review

- Every artboard (4a.1–4a.4, 4b.1–4b.6, Errors 403, Flow 2) maps to S1–S9 and to a task. Every admin endpoint and the policy pair are in §2.2 with request, response, pagination, validation and codes.
- Every artboard need without API support is a BA row with a fallback. Every undrawn state is a D row.
- Names are consistent across tasks: `adminListingsOptions`, `adminListingOptions`, `adminPolicyOptions`, `requireAdmin`, `AdminAccessDenied`, `AccessDeniedCard`, `AdminShell`, `toListingContent`, `fromListing`, `actionsFor`, `policyState`, `toCents`, `fieldViolations`, `createAdminStore`, `adminHandlers`.
