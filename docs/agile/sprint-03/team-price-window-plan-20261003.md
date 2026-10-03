# Reject team price windows START cannot use — implementation plan

Date: 2026-10-03 · Branch: `feature/team-price-rejection-20261003` · Issue: #264 (follow-up from the #242 review of US25, #221) · Owner: los gai · Related: [Sprint 3 plan US25](plan.md), [team terms plan](team-terms-plan-20260929.md), [backend ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md), [admin listings design](../../../backend/docs/design/admin-listings.md), [team price quotes design](../../../backend/docs/design/team-price-quotes.md)

> **Status (2026-10-03): approved (D6 behaviour change confirmed by the owner; D9 frontend mirror deferred to a follow-up issue) and implemented on this branch.** V1–V4 passed locally: full `verify` 1,546 tests, 0 failures, 1 pre-existing skip; JaCoCo aggregate shows no missed line or branch on any touched main file; the contract diff is exactly the two operation descriptions; `pnpm gen:api` leaves the generated client unchanged. CI `contract-check` and SonarCloud wait for the PR.

## 1. Goal and acceptance criteria

An administrator can no longer save a team price policy that a quote-less START can never use. Today `TeamPriceAppServiceImpl.configure` only refuses a `validUntil` that is not after database time, while START (`CatalogPurchaseSupportImpl.requireCoversQuoteWindow`) needs `validFrom <= now` and `now + 2400 s < validUntil`. A policy that ends within 40 minutes of taking effect is therefore accepted and then makes every quote-less START fail with `CATALOG_409_001`.

| # | Criterion (issue #264) | Delivered by |
| --- | --- | --- |
| AC1 | A policy whose usable window is not longer than the quote window, `validUntil - max(now, validFrom) <= 2400 s`, is refused with 400 `CATALOG_400_001` and nothing is written | §4 B1–B3, T1–T4 |
| AC2 | A future `validFrom` (a scheduled price) stays allowed when its own window is longer than 2400 s | D1, T1, T2, T3 |
| AC3 | Both write paths apply the rule: `PUT /api/v1/products/{id}/team-price-policy` and `PUT /api/v1/admin/products/{id}/team-terms` (`teamPrice`) | D2 (one rule, one call site), T3, T4 |
| AC4 | The 2400 s value is declared once and shared by `CatalogPurchaseSupportImpl`, `PurchaseIntentAppService` and `ProductTeamTerms.MIN_LIFETIME_SECONDS` | D4, B4–B7 |
| AC5 | Backend ADR 0001 and `admin-listings.md` describe the rule | D7, B10–B12 |
| AC6 | Unit tests plus an IT on each endpoint | §5 |

Non-goals: no new error code, no schema or migration, no change to quote issuance or to quoted START (D5), no clean-up of policies already stored, no frontend change in this PR (D9).

## 2. Baseline (read on 2026-10-03 from `3f18c87`)

| Piece | State | Consequence |
| --- | --- | --- |
| `TeamPriceAppServiceImpl.configure` (application, catalog) | Product lock → active actor → version check → `prices.currentTime()` → `new TeamPricePolicy(...)` → `requireCompatible(product)` → `!now.isBefore(validUntil)` → `forField(TEAM_PRICE_INVALID, "validUntil", "Must be in the future")` → `appendPolicy` | The single write path for policies. The new check goes right after the "in the future" check, under the same lock and clock. |
| `TeamTermsAppServiceImpl.configure` | Delegates the price to `TeamPriceAppService.configure` inside its own transaction and rethrows with `withFieldPrefix("teamPrice.")`; a rejected price rolls the terms back | Gets the rule for free; its error field becomes `teamPrice.validUntil`. |
| `TeamPricePolicy` (domain, `catalog.pricing`) | Record. Owns shape rules (`validFrom < validUntil`, SGD, exact cents), `requireCompatible(product)` and `requireQuotable(product, now)`; truncates instants to microseconds | Natural home for a time-window predicate that both START and configure can use. |
| `CatalogPurchaseSupportImpl.requireCoversQuoteWindow` | Private: `now.isBefore(validFrom) \|\| !now.plusSeconds(2400).isBefore(validUntil)` → `TEAM_PRICE_UNAVAILABLE` (`CATALOG_409_001`). Called only from `lockStartQuote` (quote-less START) | The START predicate lives in application code today; configure would have to copy it. |
| `lockQuotedStart` (START with a quote id) | Does not read the latest policy; checks only the quote's own expiry (ADR 0001, addendum 2026-09-28, rule 2) | A buyer holding a quote can still START against a short policy. The bug hits quote-less START only — the frontend sends `quoteId` only when one is in `sessionStorage`. |
| 2400 s declarations | `CatalogPurchaseSupportImpl.QUOTE_WINDOW_SECONDS` (private long), `PurchaseIntentAppService.QUOTE_WINDOW_SECONDS` (private long, Javadoc says "matches" the other), `ProductTeamTerms.MIN_LIFETIME_SECONDS` (public int, used by `TeamTermsSpecification` and by `@Min` in `AdminListingController`) | Three copies of one business fact across two bounded contexts (catalog, order). |
| `StripeCheckoutService` `1860` | Stripe Checkout session expiry (Stripe's 30-minute minimum + 1 minute), must fit inside the 2400 s window | A different fact; stays separate. |
| Clock | `TeamPriceRepository.currentTime()` and the order side both read PostgreSQL `clock_timestamp()` | Configure and START compare against the same clock. |
| Error rendering | `BizException.forField` produces `errors[0].field` / `message`; ITs already assert `validUntil` / `teamPrice.validUntil` field errors | ADR 0001 (addendum 2026-09-29) and `admin-listings.md` §237 still say "no field errors" — stale, fixed in passing (B10, B11). |
| Existing tests | Every policy fixture in `TeamPriceFlowIT`, `TeamTermsFlowIT`, `StripeLocalIntegrationTest` and the application unit tests uses a window of ≥ 3540 s or a mocked service; `TeamPriceControllerTest` mocks `TeamPriceAppService` | No existing test should change behaviour. |
| Seeds | `R__demo_catalog_seed.sql` inserts 2026-01-01 → 2030-01-01 by SQL; `scripts/seed-dev-demo-listings.sh` writes windows measured in days through the API | Unaffected. |
| Frontend `policy-form.schema.ts` | Mirrors "end in the future" and "end after start"; server field errors are placed on the matching field by `PolicyForm` | A rejected window already shows the server's message on `validUntil`; the client mirror would lag (D9). |

## 3. Decisions

| ID | Decision | Recommendation | Alternatives rejected |
| --- | --- | --- | --- |
| D1 | The rule | **Accept a policy only if START could succeed at the instant it takes effect:** with `t0 = max(now, validFrom)`, require `t0 + 2400 s < validUntil` (strict, identical to START). Equal to 2400 s is refused, because START at `t0` would fail. A future `validFrom` is judged on its own window, independent of now. | (a) A safety margin above 2400 s (e.g. +5 min) so START still works a little after saving — arbitrary, and real windows are days long. (b) Only check `now + 2400 s < validUntil` — wrongly accepts a scheduled policy of e.g. 10 minutes. |
| D2 | Where the rule lives | **Domain, on `TeamPricePolicy`:** `boolean startableAt(Instant at)` (the START predicate) and `void requireStartableWindow(Instant now)` (configure, calls `startableAt(max(now, validFrom))`). `CatalogPurchaseSupportImpl.requireCoversQuoteWindow` becomes `if (!policy.startableAt(now)) throw TEAM_PRICE_UNAVAILABLE`. The application keeps supplying database time after the lock, as today. One predicate, so configure and START cannot drift again. | Copy the predicate into `TeamPriceAppServiceImpl` (two copies of one rule — the cause of this bug). |
| D3 | Error shape | **400 `CATALOG_400_001`, one field error on `validUntil`** (`teamPrice.validUntil` on the team-terms endpoint), message **"Must end more than 40 minutes after the rule takes effect"**. The existing "Must be in the future" check stays first, so an end already past keeps its current message and tests. No new `ErrorCode` or i18n key. | A new code such as `CATALOG_400_00x` (issue asks for `CATALOG_400_001`; clients already handle it); blaming `validFrom` (the end is what the admin moves to fix it). |
| D4 | Home of the shared 2400 s | **New shared-kernel constant class in the domain module:** `com.blocbuy.common.model.PurchaseQuoteWindow` (`final`, private constructor, `public static final int SECONDS = 2400`, Javadoc stating the business meaning: the time a buyer has to pay an issued intent, so a START policy and a team's life must cover it). Used by `TeamPricePolicy`, `ProductTeamTerms.MIN_LIFETIME_SECONDS = PurchaseQuoteWindow.SECONDS` (still a compile-time constant for `@Min`), and `PurchaseIntentAppService`. Both private copies are deleted. The window is a contract between order (which enforces it on the intent) and catalog (which must cover it); neither should import the other's domain internals. | (a) `TeamPricePolicy.QUOTE_WINDOW_SECONDS` imported by order — order reaching into catalog's domain model, against the module-boundary hard gate. (b) Re-export on the `CatalogPurchaseSupport` facade — still two declarations (domain cannot see the facade), plus a constants-on-interface smell. (c) A `Duration` — `@Min` needs an integer compile-time constant. |
| D5 | Scope of enforcement | **Write time only.** Not applied to: policies already stored (no migration; they behave as today and lapse on their own), `issueQuote` (a quote may still be issued in a policy's last 40 minutes; quoted START honours it per ADR 0001), seed SQL. The last 40 minutes of every policy remain un-STARTable without a quote — inherent and documented, not a defect. | Backfill or soft-delete short stored policies (data change for a validation fix); also refusing quote issuance in the tail (behaviour change outside the issue). |
| D6 | Behaviour change | **Owner confirms before implementation** (issue note). Both endpoints tighten: a request accepted today can now be 400. Response and request schemas do not change. | — |
| D7 | Decision record | **Dated addendum to backend ADR 0001** ("a price window must cover the quote window", #264), not a new ADR; it owns START, the quote window and the team-terms command. Same commit fixes the stale "no field errors" sentence in the 2026-09-29 addendum. | New ADR (duplicates 0001's subject). |
| D8 | OpenAPI | **Update the `@Operation` descriptions** of `configureTeamPricePolicy` and the team-terms PUT to name the rule, then regenerate `contracts/openapi.json` with `-Popenapi-export` (description-only diff; openapi-diff stays compatible). | Leave the descriptions — the contract would hide a validation rule clients hit. |
| D9 | Frontend mirror | **Follow-up issue**, not this PR: extend `policyFormSchema` with "end more than 40 minutes after max(now, start)" and a test. Until then the server message lands on the `validUntil` field through `PolicyForm`, so nothing breaks; the schema's "mirrors the backend" comment is temporarily incomplete. **Reviewer to confirm** (alternative: include it here as F1–F2). | Silently leaving the mirror stale. |

## 4. Change list

Backend (bounded context `catalog`, plus the shared kernel and one `order` constant; no schema change, no migration):

| ID | Layer | File | Change |
| --- | --- | --- | --- |
| B1 | domain (common) | `blocbuy-domain/.../common/model/PurchaseQuoteWindow.java` (new) | Constant class per D4. JDK only. |
| B2 | domain (catalog) | `blocbuy-domain/.../catalog/pricing/TeamPricePolicy.java` | Add `startableAt(Instant at)`: `!at.isBefore(validFrom) && at.plusSeconds(PurchaseQuoteWindow.SECONDS).isBefore(validUntil)`. Add `requireStartableWindow(Instant now)`: `t0 = now.isAfter(validFrom) ? now : validFrom`; if `!startableAt(t0)` throw `forField(TEAM_PRICE_INVALID, "validUntil", <D3 message>)`. Null `now` → `IllegalArgumentException` (programming error, same as `lockStartQuote`). |
| B3 | application (catalog) | `blocbuy-application/.../catalog/service/TeamPriceAppServiceImpl.java` | After the "in the future" check: `policy.requireStartableWindow(now)`. Nothing else moves; still before `appendPolicy`, so a refusal writes nothing. |
| B4 | application (catalog) | `blocbuy-application/.../catalog/service/CatalogPurchaseSupportImpl.java` | `requireCoversQuoteWindow` delegates to `policy.startableAt(now)`; delete `QUOTE_WINDOW_SECONDS` and move its Javadoc rationale to B1. |
| B5 | application (order) | `blocbuy-application/.../order/service/PurchaseIntentAppService.java` | Delete the private `QUOTE_WINDOW_SECONDS`; use `PurchaseQuoteWindow.SECONDS` in `start` and `join`; replace the "Matches ..." Javadoc with a link to B1. |
| B6 | domain (catalog) | `blocbuy-domain/.../catalog/model/ProductTeamTerms.java` | `MIN_LIFETIME_SECONDS = PurchaseQuoteWindow.SECONDS`; Javadoc links B1. `TeamTermsSpecification` and `@Min` in `AdminListingController` keep using `MIN_LIFETIME_SECONDS` unchanged. |
| B7 | — | `AdminListingController` description strings ("2400-86400", "2400 seconds") | Left as literals: human-readable contract text, regenerated into the spec. Not a second source of the rule. |
| B8 | adapter | `blocbuy-adapter/.../catalog/web/TeamPriceController.java` | `configureTeamPricePolicy` description: add "validUntil must be more than 2400 seconds after the later of validFrom and database time, otherwise 400 CATALOG_400_001 on validUntil." |
| B9 | adapter | `blocbuy-adapter/.../catalog/web/AdminListingController.java` | Team-terms PUT description: add the same reason to the `CATALOG_400_001` list. |
| B10 | contract | `contracts/openapi.json` | Regenerate (never hand-edit) and commit. |
| B11 | docs | `backend/docs/adr/0001-payment-business-integration.md` | Addendum (2026-10-03, #264): rule (D1), one predicate (D2), error (D3), shared constant (D4), scope and the 40-minute tail (D5). Correct "(no field errors)" in the 2026-09-29 addendum to "with `teamPrice.<field>` field errors". |
| B12 | docs | `backend/docs/design/admin-listings.md` §team-terms errors | Add the window reason to the `CATALOG_400_001` list; replace "with no field errors" by "with `teamPrice.<field>` field errors". |
| B13 | docs | `backend/docs/design/team-price-quotes.md` §PUT policy | One sentence: the window must exceed 2400 s from the later of `validFrom` and now (owning design doc of the standalone endpoint; the issue did not list it). |

Evidence: `docs/agile/sprint-03/log/2026-10-03.md` entry (or the day the PR lands); this plan's status line; follow-up issue link for D9.

## 5. Tests

Literal `2400` stays in existing tests on purpose: a test that reads the constant cannot catch an accidental change to it.

| ID | Level | File | Asserts |
| --- | --- | --- | --- |
| T1 | unit (domain) | `TeamPricePolicyTest` | `startableAt`: false before `validFrom`; true at `validFrom` when `validUntil = validFrom + 2401 s`; false when `validUntil = at + 2400 s` exactly. `requireStartableWindow`: in-force policy with `now + 2400 s == validUntil` → `TEAM_PRICE_INVALID`, field `validUntil`, D3 message; `+2401 s` passes; scheduled policy (`validFrom = now + 1 d`) with a 2400 s window → refused, 2401 s → passes even though `now + 2400 s` is irrelevant; null `now` → `IllegalArgumentException`. |
| T2 | unit (application) | `TeamPriceAppServiceImplTest` | `configureRejectsWindowShorterThanQuoteWindowWithoutWriting` (`validUntil = NOW + 2400 s`); `configureAcceptsScheduledPolicyCoveringQuoteWindow` (future `validFrom`, 2401 s) → `appendPolicy` once; existing `configureRejectsExpiredWindowWithoutWriting` additionally asserts the "Must be in the future" message, proving check order. |
| T2b | unit (application) | `CatalogPurchaseSupportImplTest` | Existing `aRevisionThatDoesNotCoverTheWholeQuoteWindowIsRefused` and the not-yet-in-force case stay green after B4 (add the future-`validFrom` case if absent). |
| T3 | IT (PostgreSQL, MockMvc) | `TeamPriceFlowIT` | Standalone PUT: in-force window ending at `now + 1800 s` → 400 `CATALOG_400_001`, `errors[0].field = validUntil`, D3 message, zero `biz_team_price_policy` rows. Scheduled boundary (deterministic, independent of clock drift): `validFrom = now + 1 h`, `validUntil = validFrom + 2400 s` → 400; `validFrom + 2401 s` → 200 revision 1. |
| T4 | IT (PostgreSQL, MockMvc) | `TeamTermsFlowIT` | Team-terms PUT with `teamPrice` ending at `now + 1800 s` → 400 `CATALOG_400_001`, `errors[0].field = teamPrice.validUntil`, D3 message; `assertNothingWritten` (terms rolled back, no policy, no `log_product_team_terms` row). |
| T5 | unit | `PurchaseIntentAppServiceTest`, `ProductTeamTermsTest`, `TeamTermsSpecificationTest` | Unchanged and green — proves B5/B6 kept the value. |

Coverage target: 100% of new/changed lines and branches on touched files. Likely misses: the `now` vs `validFrom` arm of `t0` (T1 covers both), the null-`now` guard (T1). `PurchaseQuoteWindow`'s private constructor is filtered by JaCoCo.

## 6. Verification

JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

| ID | Check | Proves |
| --- | --- | --- |
| V1 | `cd backend && ./mvnw -B -pl blocbuy-bootstrap -am test -Dtest='TeamPricePolicyTest,TeamPriceAppServiceImplTest,CatalogPurchaseSupportImplTest,PurchaseIntentAppServiceTest,ProductTeamTermsTest,TeamTermsSpecificationTest' -Dsurefire.failIfNoSpecifiedTests=false` | Focused unit tests. |
| V2 | `cd backend && ./mvnw -B verify`, then the JaCoCo aggregate missed-line script from the backend skill on touched files | Spotless, Checkstyle, ArchUnit (`common.model` stays framework-free; domain has no outward dependency), SpotBugs, T3/T4 via Testcontainers, coverage. |
| V3 | `./mvnw -B verify -Popenapi-export -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true`, commit, then `git diff --exit-code -- contracts/` from the repo root | Contract drift gate; CI `contract-check` openapi-diff confirms description-only change. |
| V4 | `cd frontend && pnpm gen:api && pnpm check` | Regenerated client compiles (description-only, so no type change expected). |

## 7. Risks and consistency

- **Funds and inventory.** None touched. The change only refuses writes; START, quotes, intents and payments behave as before.
- **Concurrency.** No new lock or query. The check runs inside the existing 10 s configure transaction, after the product row lock and on `clock_timestamp()`, the same clock START uses. Lock order unchanged.
- **Residual race.** A policy saved with a window just over 2400 s can still fail a quote-less START a few seconds later; D1 only guarantees START is possible at the moment the policy takes effect. Accepted (D1 alternative a).
- **Behaviour change.** Admin requests that used to succeed can now be 400 on both endpoints (D6). No known caller relies on short windows: seeds and fixtures use hours to years.
- **Module boundaries.** `common.model` gains a constant class used by catalog and order; no aggregate, Mapper or DO crosses a context. ArchUnit in V2 guards the domain rules.
- **Contract.** Description text only; schemas and response shapes unchanged.
- **Frontend lag (D9).** Until the follow-up lands, the admin form lets the user submit and shows the server's field message instead of refusing locally.
- **Open questions for review.** D6 confirmation of the behaviour change; D3 message wording; D4 constant name and home; D9 follow-up vs same PR.
