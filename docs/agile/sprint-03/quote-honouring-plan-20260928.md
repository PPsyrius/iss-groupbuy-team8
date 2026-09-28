# #183 Price shown = price charged — backend implementation plan

Date: 2026-09-28 · Branch: `refactor/price-shown-20260928` · Issue: #183 (US26) · Owner: los gai (API) · Frontend consumer: @limjiechao (US26 UI) · Design: [`team-price-quotes.md`](../../../backend/docs/design/team-price-quotes.md), [backend ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md), [frontend ADR 0014](../../../frontend/docs/adr/0014-stripe-hosted-checkout-server-owned-intent.md)

Budget: 6 h (Sprint 3 plan). Target: merged to `dev` with the regenerated contract by 2026-10-02, so that the US13 switch-on and the Review demo charge the quoted price.

> **Implementation notes (2026-09-28).** Implemented as planned; D2 and D8 remain open for Planning. Departures:
>
> - **IT placement.** The IT cases live in `StripeLocalIntegrationTest`, reusing its container and context; no separate `QuotedStartIT`. IT-1 goes past `prepare`: it also settles and asserts `biz_order.total_amount`, and IT-5's "unquoted START still takes the latest policy" is asserted inside IT-1 after the revision.
> - **UT-A2.** It asserts `COMMON_400_001`, the existing `HttpMessageNotReadableException` mapping.
> - **Null guard.** `lockQuotedStart` rejects a null quote or buyer id with `IllegalArgumentException`, mirroring `lockStartQuote`'s null-clock guard. The order caller never passes either.
> - **Generated client.** `frontend/src/api/generated/` is gitignored and rebuilt by `pnpm gen:api`, so the backend PR carries no frontend file. The field is optional, so the frontend compiles unchanged.

## 1. Goal and acceptance criteria

A START intent can carry the id of a team-price quote. When it does, the intent charges the quote's amount, not the latest policy's amount. The decision was made on #183 (comment of 2026-09-26): "START will accept the quote id".

| # | Criterion | Delivered by |
| --- | --- | --- |
| AC1 | A START with a valid `quoteId` charges `quote.amount`, even if an admin revised the policy after the quote was issued | §4.2, §4.3; IT-1 |
| AC2 | The quote is validated for owner, product and expiry, under the product lock, inside the intent transaction | §4.2; UT-C3..C7, IT-2..IT-4 |
| AC3 | A missing, foreign or other-product quote id reveals nothing; an expired one says "get a new price" | §3 D4, D5 |
| AC4 | A START without `quoteId` behaves exactly as today; the contract change is additive only | §3 D2; `contract-check` + `openapi-diff` green |
| AC5 | The Stripe Checkout amount equals the quote amount end to end | IT-1 (intent → `prepare` → `CheckoutQuote.amount`) |

## 2. Baseline (measured on `dev` b4c4d98)

| Piece | State | Consequence |
| --- | --- | --- |
| `StripeCheckoutController.IntentRequest(operation, businessId, ref)` | no quote field | add optional `quoteId` |
| `PaymentIntentPort.createIntent(user, operation, businessId, inviteRef)` (payment domain) | 4-arg abstract + 3-arg default | add 5-arg abstract; 4-arg becomes a default passing `null` |
| `PaymentBrowserService.createIntent` | 10 s `TransactionTemplate` around the port | pass `quoteId` through, untouched |
| `PurchaseIntentAppService.start` → `CatalogPurchaseSupport.lockStartQuote(productId, now)` | locks product, checks sellable + terms, reads **latest policy**, requires policy to cover the 40-min window, amount < original price | new catalog method for the quoted path |
| `TeamPriceRepository.findQuoteForOwner(quoteId, ownerId)` | exists, owner-scoped, `deleted = false` | reuse; no new mapper SQL |
| `TeamPriceQuote` | immutable, SGD only, exact cents, `expiresAt` = min(issue + 15 min, policy `validUntil`), exclusive | its `amount` becomes the charged amount |
| `TeamPriceAppService` Javadoc | "Quotes cannot yet be consumed by checkout" | update |
| `OrderFulfilmentAdapter.lockQuote` / `lockForSettlement` / `fulfil` | re-read the **frozen intent**; never re-price START | no change: once the intent holds the quoted amount, it flows to Stripe and settlement as is |
| `now` in `createIntent` | `intents.currentTime()` read **before** the product lock | quote expiry must use a clock read **after** the lock (§3 D7) |
| Flyway | latest V19 | no migration in this plan (§3 D8) |
| Open PRs | #234, #206, #207, #140 touch none of the files below | no conflict expected; re-check before merge |

## 3. Decisions

✅ = decided in this plan. ⚠️ = confirm at Planning (09-28) or with @limjiechao before step 3 in §6.

| # | Decision | Choice | Why |
| --- | --- | --- | --- |
| D1 | Who validates the quote | **Catalog**, via a new `CatalogPurchaseSupport.lockQuotedStart(productId, quoteId, buyerId)`. Order never reads `biz_team_price_quote` | Quotes are catalog aggregates. The cross-domain gate forbids order reading catalog's repository or mapper (ADR 0001, "facades one per direction of need"). ✅ |
| D2 | `quoteId` required? | **Optional.** Absent on START → today's latest-policy pricing | Additive contract only (`openapi-diff` rejects breaking changes). The unquoted path is still correct: it charges the current policy, and Stripe shows it. Making it required later is a breaking change that needs approval. ⚠️ confirm that "require it in Sprint 4" is not wanted |
| D3 | `quoteId` on JOIN | **Rejected**: 400 `ORDER_400_001` (`PURCHASE_INTENT_TERMS_INVALID`), before any read or lock | A JOIN's price is the team's frozen price. A money term sent to the wrong operation is a client bug, so it fails loudly. This differs from `ref` on START (ignored) on purpose: `ref` is attribution, not money. ✅ |
| D4 | Missing / foreign / other-product quote | Same 404 `CATALOG_404_002` (`TEAM_PRICE_QUOTE_NOT_FOUND`) for all three | Same rule as `GET /team-price-quotes/{id}`: no existence oracle across owners. A quote for product A used on product B is "not found for this product". ✅ |
| D5 | Expired quote | **New** `CATALOG_409_007` `TEAM_PRICE_QUOTE_EXPIRED`, "Team price quote has expired" | The frontend needs to tell "get a new price" apart from "no bloc price set" (`CATALOG_409_001`). Distinguishing expiry leaks nothing, because only the owner gets past D4. Codes are never reused, so this is the next free number. ✅ |
| D6 | Policy revised or withdrawn after the quote | **Honour the quote.** The quoted path does not read the latest policy at all | This is the point of #183. Exposure is bounded: at most 15 min, and never past the policy `validUntil` the quote was issued under. The admin kill switch is still the product status: `requireSellable` runs on both paths. ✅ |
| D7 | Clock for the expiry check | `teamPrices.currentTime()` (`clock_timestamp()`) read **after** `products.lockById` | Same rule as `team-price-quotes.md` ("read … after the product lock"). A buyer who waits on the lock must not slip an expired quote through with the pre-lock `now`. The intent's deadlines still use the order's `now`, as today. ✅ |
| D8 | Persist the quote id on the intent | **Not in this PR.** No V20 | Settlement compares only the frozen intent, and the amount on the intent is the audit fact. A `team_price_quote_id` column would add a 9th constructor field to `PurchaseIntent` for traceability only. ⚠️ Revisit if the Review asks "which quote was charged" |
| D9 | Single use | **Reusable until `expiresAt`.** Each reuse creates a new intent, and each intent is still consumed by at most one payment | Retries after a stale intent (`PAYMENT_409_001`) or a cancelled Stripe page must keep the shown price. V6 already documents "not single-use consumption". Abuse is bounded by 15 min and by the buyer's own session. ✅ |
| D10 | Invariants re-checked on the quoted path | Product lock → sellable (on sale, stock ≥ 1, SGD) → terms `startable()` → quote (owner, product, not expired) → `quote.amount < product.originalPrice` | The team price must still undercut the individual price. If an admin lowered the original price below the quote, refuse with `CATALOG_409_001` rather than sell a "discount" that is not one. ✅ |
| D11 | Intent windows on the quoted path | Unchanged: `quoteValidUntil = now + 2400 s`, `businessDeadline = now + lifetimeSeconds` | The quote's `expiresAt` is the deadline to **accept** the price (create the intent). The intent's window is the deadline to **pay**. Capping the pay window by the quote would leave a buyer who accepts at minute 14 one minute to pay. ✅ |
| D12 | Team terms drift | Out of scope. Target size and lifetime are still read live from the product | The quote sheet shows price and expiry only, not team terms. US25 (#221) admin terms edits take the same product lock. Log the risk (§8). ✅ |

## 4. Design

### 4.1 Flow (quoted START)

```
POST /api/v1/payments/intents { operation: START, businessId: productId, quoteId }
StripeCheckoutController.intent          -- CSRF header, feature switch, @Valid
 └ PaymentBrowserService.createIntent    -- TransactionTemplate(timeout 10 s)
    └ PurchaseIntentAppService.createIntent(user, op, businessId, ref, quoteId)   MANDATORY
       ├ JOIN && quoteId != null → 400 ORDER_400_001 (before any read)
       ├ users.requireActive(user)
       ├ now = intents.currentTime()
       └ start(user, productId, quoteId, now)
          └ CatalogPurchaseSupport.lockQuotedStart(productId, quoteId, user)
             ├ products.lockById           -- product row lock (only lock START takes)
             ├ requireSellable, terms.startable()
             ├ clock = teamPrices.currentTime()          -- after the lock (D7)
             ├ teamPrices.findQuoteForOwner(quoteId, user).filter(productId) → else 404
             ├ !clock.isBefore(quote.expiresAt) → 409 CATALOG_409_007
             ├ quote.amount >= product.originalPrice → 409 CATALOG_409_001
             └ StartQuote(amount = quote.amount, currency = quote.currency, terms...)
       └ PurchaseIntent.issue(...) → intents.save   -- amount frozen from here on
```

Lock order is unchanged: START holds only the product row, then writes the intent row. Quote rows are immutable and read without a lock. There is no remote call inside the transaction.

### 4.2 Catalog (`blocbuy-application` + `blocbuy-domain`)

- `CatalogErrorCode`: add `TEAM_PRICE_QUOTE_EXPIRED("CATALOG_409_007", 409, "Team price quote has expired", "error.catalog.teamPriceQuoteExpired")`. There is no message bundle yet, so the enum is the only change.
- `CatalogPurchaseSupport`: add `StartQuote lockQuotedStart(UUID productId, UUID quoteId, UUID buyerId)`, with a Javadoc listing its `@throws` codes in the same style as `lockStartQuote`.
- `CatalogPurchaseSupportImpl`:
  - extract `private Startable lockStartable(UUID productId)`, holding the lock, `requireSellable` and the terms check, into a private record `(Product product, ProductTeamTerms terms)`;
  - `lockStartQuote` keeps its behaviour and uses the helper;
  - `lockQuotedStart` follows §4.1;
  - no new repository method.
- `TeamPriceAppService` Javadoc: replace "Quotes cannot yet be consumed by checkout" with "A START purchase intent may accept an unexpired quote (see `CatalogPurchaseSupport.lockQuotedStart`)".

### 4.3 Order and payment

- `PaymentIntentPort` (payment domain): add the 5-arg abstract method `createIntent(user, operation, businessId, inviteRef, quoteId)`.
  - Javadoc: `quoteId` is an opaque, optional price-quote reference that payment passes through untouched.
  - The existing 4-arg method becomes a `default` that delegates with `null`. The 3-arg default is unchanged.
  - Existing callers and tests compile as they are.
- `PurchaseIntentAppService`:
  - override the 5-arg method;
  - add the D3 check next to the existing null checks;
  - `start(user, productId, quoteId, now)` branches: `quoteId == null ? products.lockStartQuote(productId, now) : products.lockQuotedStart(productId, quoteId, user)`;
  - the rest of `start` is untouched;
  - update the class Javadoc ("every number … is read here from the database under a lock"), which still holds because the quote is a database row.
- `PaymentBrowserService`:
  - add `createIntent(user, operation, businessId, inviteRef, quoteId)`;
  - keep the 4-arg overload delegating with `null`, used by `PaymentLifecycleFixture` and other tests.
- `StripeCheckoutController`:
  - `IntentRequest` gets a `UUID quoteId`, annotated `@Schema(nullable = true, description = "Optional first-team price quote (UC3). START charges its amount while it is unexpired; rejected on JOIN")`;
  - the controller passes `request.quoteId()`.
- Error mapping: all new failures are `BizException(ErrorCode)`, mapped by `GlobalExceptionHandler`. A malformed UUID in JSON is a 400 through the existing message-not-readable handling (verify in UT-A2).

### 4.4 Contract

```bash
cd backend && ./mvnw -B verify -Popenapi-export -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true
git diff --exit-code -- ../contracts/   # after `git add contracts/openapi.json`
```

The only expected diff is one optional nullable `quoteId` (uuid) property on `IntentRequest`. `openapi-diff` should classify it as compatible.

## 5. Tests

Unit tests use Mockito, in the existing test classes. The target is 100 % line and branch coverage on the touched files.

| ID | Class | Case |
| --- | --- | --- |
| UT-C1 | `CatalogPurchaseSupportImplTest` | Quoted START returns `quote.amount` while the latest policy has a different amount, and never calls `findLatestPolicy` |
| UT-C2 | ″ | Quoted START succeeds when the latest policy is withdrawn or expired (D6) |
| UT-C3 | ″ | `expiresAt == clock` → `CATALOG_409_007` (exclusive boundary); `expiresAt == clock + 1 µs` → OK |
| UT-C4 | ″ | Missing quote → `CATALOG_404_002`; quote for another product → `CATALOG_404_002` |
| UT-C5 | ″ | `quote.amount >= originalPrice` → `CATALOG_409_001` |
| UT-C6 | ″ | Not sellable / terms not startable / product missing → same codes as `lockStartQuote`, and the quote is never read |
| UT-C7 | ″ | `InOrder`: `lockById` before `currentTime` before `findQuoteForOwner` (D7) |
| UT-C8 | ″ | Existing `lockStartQuote` tests stay green unchanged (refactor safety) |
| UT-O1 | `PurchaseIntentAppServiceTest` | START + `quoteId` → `lockQuotedStart(productId, quoteId, user)`; the intent amount equals the quote amount |
| UT-O2 | ″ | START without `quoteId` → `lockStartQuote` (no behaviour change) |
| UT-O3 | ″ | JOIN + `quoteId` → `ORDER_400_001`, `verifyNoInteractions(users, intents, deals, products)` |
| UT-O4 | ″ | Catalog's `CATALOG_409_007` / `CATALOG_404_002` propagate, not swallowed |
| UT-P1 | `PaymentBrowserServiceTest` | `quoteId` reaches the port unchanged; the 4-arg overload passes `null` |
| UT-A1 | `PaymentBrowserControllerTest` (MockMvc) | `quoteId` binds and is passed through; absent `quoteId` still works |
| UT-A2 | ″ | Malformed `quoteId` → 400 envelope, no stack trace |
| IT-1 | `StripeLocalIntegrationTest` (PostgreSQL 18, Testcontainers) | Issue a quote at 60.00 → admin appends a policy at 80.00 → START with `quoteId` → intent amount 60.00 → `prepare` → `CheckoutQuote.amount` 60.00 (AC1, AC5) |
| IT-2 | ″ | Quote owned by buyer A, used by buyer B → 404; no intent row written |
| IT-3 | ″ | Expired quote (test-only JDBC sets `expires_at` in the past) → 409 `CATALOG_409_007`; no intent row |
| IT-4 | ″ | Product set off-sale after the quote → `CATALOG_409_005` (kill switch still works) |
| IT-5 | ″ | Existing unquoted START tests unchanged (AC4) |

IT-3 must write to the table with test-only JDBC, never through a production path. If `StripeLocalIntegrationTest` is too crowded, put these cases in a new `QuotedStartIT` that uses the same fixture.

## 6. Steps (≈ 6 h)

| Step | Work | Est. | Done when |
| --- | --- | --- | --- |
| 1 | Error code, `lockStartable` refactor, `lockQuotedStart`, UT-C1..C8 | 1.5 h | `./mvnw -pl blocbuy-application -am test` green |
| 2 | Port 5-arg, `PurchaseIntentAppService`, `PaymentBrowserService`, UT-O*, UT-P1 | 1 h | unit tests green, ArchUnit green |
| 3 | `IntentRequest.quoteId`, UT-A1..A2, contract regen | 0.75 h | `git diff --exit-code -- contracts/` clean after `git add` |
| 4 | IT-1..IT-5 | 1.5 h | `./mvnw -B verify` green with Docker |
| 5 | Docs (§7) + day log | 0.5 h | links resolve |
| 6 | Coverage self-check on touched files (aggregate `jacoco.xml` script), PR with the repo template, hand-off note to @limjiechao on #183 | 0.75 h | no missed lines or branches on new code; PR opened against `dev` |

## 7. Documentation changes (same PR)

- Backend ADR 0001: add "Addendum (2026-09-28): START honours a team-price quote (#183)" covering D1, D3, D6, D7 and D9. It amends the owning ADR rather than creating a new one.
- `team-price-quotes.md`:
  - replace "Future checkout must revalidate … this increment promises neither single-use quotes nor fulfilment" with the implemented rule (owner, product and expiry under the product lock, reusable until expiry, never past policy `validUntil` at issuance);
  - replace "Clients must not treat this as checkout" with "a quote id may be passed to a START intent".
- `paid-group-participation.md` L119: "Quote acceptance … remain integration work" → quote acceptance is done (#183).
- `TeamPriceAppService` Javadoc (§4.2).
- `docs/agile/sprint-03/log/2026-09-2x.md`: a day entry per the agile-evidence skill.

Frontend changes are **not** in this PR. See §9.

## 8. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| The quoted path skips the policy read, so a withdrawn policy is still honoured for ≤ 15 min | Sells at the old team price briefly | This is intended (D6). The admin kill switch is product status. Documented in ADR 0001 addendum |
| Clock read before the lock (existing `now`) used for expiry by mistake | An expired quote is accepted after a lock wait | D7, plus UT-C7 `InOrder` |
| Port signature change ripples through 19 `createIntent` callers | Compile breaks in tests | The 4-arg default is kept; only the implementor changes |
| The frontend treats any 409 on intent creation as a stale intent (`isStaleIntent` checks `status === 409`) | `CATALOG_409_007` shows the wrong copy | Hand-off note (§9); Lim maps `CATALOG_409_007` and `CATALOG_404_002` to "Get a new price" |
| Team terms (size, lifetime) edited between quote and intent (US25) | Buyer sees the same price with different team terms | Out of scope (D12). The sheet does not show terms today. Raise if US25 adds them to the sheet |
| Merge-queue contract drift if another PR regenerates `openapi.json` first | `contract-check` red | Rebase and regenerate just before queueing |

## 9. Frontend hand-off (US26 UI, @limjiechao, 2 h)

Post as a comment on #183 once the PR is up:

1. `use-checkout.ts` `intentRequest`: for START from the `QuoteSheet`, send `{ operation: "START", businessId, quoteId }`, using the quote id the sheet already stores.
2. Map `CATALOG_409_007` (expired) and `CATALOG_404_002` (not found) on intent creation to the sheet's "Get a new price" state. Check that `isStaleIntent` (`status === 409`) does not catch them first.
3. Copy: "This price is a preview and may change before you pay" → the price is held until the countdown ends.
4. Update the ADR 0014 T3-01 addendum and the `quote.ts` header comment ("The quote is a preview").
5. Browser MSW: honour `quoteId` in the intent handler.

## 10. Out of scope

- Persisting the quote id on the intent (D8).
- Making `quoteId` mandatory (D2).
- Snapshotting team terms in the quote (D12).
- Rate-limiting quote issuance (`POST /team-price-quotes` is not idempotent; each call writes a row). This is existing behaviour; track it separately if the NFR register asks.
