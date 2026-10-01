# 0001. Purchase intent lives in order, group terms live on the product

Date: 2026-09-22

## Status

Accepted

## Context

The Stripe checkout slice (PR #126) shipped two ports it does not implement — `PaymentIntentPort`
and `PaymentBusinessPort` — with the note "Integration contract owned jointly with groupbuy/order.
No production stub is supplied." Until this change the only implementation was a test fixture, so
`StripeCheckoutConfiguration` carried `@Profile("stripe-sprint2")` with the comment "startup fails
if the team has not supplied the real business collaborator", and the payment path was not
connected to order or groupbuy in any profile.

Two things had no owner. First, the **purchase intent**: the server's own frozen answer to "what
would this checkout cost, and on what terms", which must exist before any provider call so that no
request can choose its own price. The fixture stored it in a test-only `stripe_local_intent` table.
Second, the **group terms** a START needs — how many buyers a team wants and how long it stays
open. The fixture invented a `stripe_local_product_terms` table for them; production had no source
at all.

The COLA hard gate (`architecture-and-ddd.md` L51) forbids cross-domain access to another module's
mappers, DOs, repository implementations or internal aggregates. The fixture ignored this — it read
`biz_product`, `biz_group_deal`, `biz_group_member`, `sys_user` and wrote `biz_order` through one
`JdbcTemplate` — which is exactly why it could never become production code.

## Decision

**The purchase intent is an order-module aggregate.** `biz_purchase_intent` (V15) with
`PurchaseIntent` as its root. Order owns it because an intent is the thing that becomes an order:
its product snapshot, amount and currency are the order's snapshot, amount and currency, and the
same partial unique index (`uq_biz_purchase_intent_fulfilled_payment`) that makes one payment
consume one intent is what makes one payment produce one order. Payment owns the money and the
provider; groupbuy owns the team; neither owns the quote.

**Group terms are two nullable columns on `biz_product`** (V14: `team_target_size`,
`team_lifetime_seconds`), not a separate table and not a payment-side configuration. They are a
property of the product being sold — the same product row that carries stock, price and status —
and a team is created from them, not the other way round. They are nullable because every existing
product predates them; a product without them simply cannot start a team, which
`ProductTeamTerms.startable()` reports and `CatalogPurchaseSupportImpl` refuses on.

**Cross-domain collaboration goes through application facades, one per direction of need.**
Catalog exposes `CatalogPurchaseSupport` (price a START, validate a purchase, take one unit of
stock). Groupbuy exposes `GroupDealPurchaseSupport` for the unpaid side (lock a team and judge
whether this buyer may still pay into it) and `GroupDealSettlementSupport` for the paid side (lock
without judging, backfill the member's order, attest a membership, assert an expiry). Identity
already exposed `UserAppService.requireActive`. Order implements both payment ports on top of
those four facades and touches no other domain's tables.

Alternatives rejected: a narrow order-side mapper reading `biz_product` directly (violates the
gate, and the gate exists precisely so catalog can change its schema without breaking checkout);
keeping the terms in a payment-side table (a group's size is not a payment concern and would drift
from the product); putting the intent in the payment module (payment would then own pricing, which
is the thing the intent exists to keep server-side and business-owned).

## Consequences

The checkout path is production code end to end. `PaymentBusinessFixture` is deleted, and
`StripeLocalIntegrationTest` — thirteen cases including a twenty-buyer race for the last seat and
an eight-thread webhook replay — now exercises the real implementations against PostgreSQL.
`PaymentIntegrationConfiguration` registers both adapters with **no profile**: the business half of
a checkout is always correct to have. `StripeCheckoutConfiguration` keeps `@Profile`, but its
meaning has changed from "the collaborator is missing" to "this is the rollout switch" — it is the
same profile `StripeFeatureSwitch` reads, and it also gates beans whose `success-url` /
`cancel-url` properties exist in no `application*.yml`.

Lock order is fixed and global: **group deal → purchase intent → payment**. It is recorded in
`PurchaseIntentRepository`'s Javadoc, in `OrderFulfilmentAdapter`'s, and in
`V15__purchase_intent.sql`. `PaidGroupDealService` internally locks its receipt before the deal, so
the combined path is a cycle on paper; it cannot deadlock because a receipt is keyed by payment id
and no two transactions contend for the same row.

A rejection and a failure are not interchangeable. An `Outcome` carrying a rejection is *committed*
by the payment slice, which then raises a refund obligation — so every rejecting check in `fulfil`
runs before the first write, and everything after the first write throws instead. Losing the last
unit at `consumeOneUnit` therefore rolls the whole settlement back rather than committing a
membership and an order next to a refund.

Resolved 2026-09-29 (US25, #221): an administrator now writes the group terms (see the addendum below). Products that predate V14 stay unconfigured, and START keeps refusing them with `CATALOG_409_006` until an admin configures them.
A START reserves its team id before the team exists, so an intent that is never paid leaves a dangling `deal_id` — harmless, since the column has no foreign key. The Sprint 3 cleanup obligation is addressed by the US30 addendum below (#225).

## Durable details (folded from the implementation plan, 2026-09-22)

The implementation plan `order-payment-integration-plan-20260922.md` was retired once its four
PRs landed; these are the decisions in it that the sections above do not already carry:

1. **Deadlines are database-owned.** `PurchaseIntentRepository.currentTime()` reads
   `clock_timestamp()` — the same clock `PaidGroupDealRepository` and `TeamPriceRepository`
   already use. The application server's clock is never read: both deadlines frozen into an
   intent are later compared against `timestamptz` values in the database, so they must come
   from that same clock.
2. **Fulfilment evidence is status-independent.** `fulfilmentEvidence` derives replay-safe
   evidence from rows keyed by payment id — `OrderRepository.findByPaymentId` plus the team
   membership by payment id: neither present → `NONE`; both present and agreeing on user, deal,
   amount, currency and status → `COMPLETE`; anything else → `INCONSISTENT`. No mutable status
   field drives the verdict, so a replay after any intermediate state answers the same.
3. **Identifiers are the contract; error numbers are business identifiers.** The ports take
   identifiers only (`businessId` is a productId for START, a dealId for JOIN); price and terms
   never cross the wire. `PurchaseOperation.parse` (returning `Optional`) replaces `fromCode`
   for HTTP input, because an unknown value is a user mistake, not a programming error.
   `ORDER_409_004` (`TEAM_NOT_JOINABLE`) is therefore permanently vacant — joinability is
   groupbuy's rule, expressed by its own error codes — and a vacated number is retired, never
   reused.

## Addendum (2026-09-28): START honours a team-price quote (#183)

Plan: `docs/agile/sprint-03/quote-honouring-plan-20260928.md`. Until now a START re-priced from the latest policy when the intent was created. An admin revision between the quote sheet and "Continue to payment" therefore charged an amount the buyer had not been shown.

**Shape.**
- `IntentRequest` carries an optional `quoteId`. `PaymentIntentPort` passes it through as an opaque value, like `inviteRef`, and payment never interprets it.
- `PurchaseIntentAppService` sends a quoted START to `CatalogPurchaseSupport.lockQuotedStart` instead of `lockStartQuote`.
- Catalog stays the only reader of `biz_team_price_quote`, so the cross-domain rule above holds.

**Rules.**
1. **Lock first, then clock, then quote.** The product row lock is taken first, with the same sellable and team-terms checks as an unquoted START. The database clock is read *after* the lock, and the quote is read last. A buyer who waited on the lock cannot slip a quote that lapsed meanwhile through on the earlier clock. Quote rows are immutable and need no lock. Lock order is unchanged.
2. **The latest policy is not read.** A revision, or a withdrawn policy, does not move a quote's price. Exposure is bounded by the quote itself: at most 15 minutes, and never past the `valid_until` of the policy it was issued under. The admin's kill switch is still the product status. The quoted amount must still be below the product's current original price (`CATALOG_409_001` otherwise).
3. **Missing, foreign and other-product quotes are the same 404** (`CATALOG_404_002`), so a quote id is not an existence oracle. An expired quote is `CATALOG_409_007`. That leaks nothing, because only the owner can get past the 404.
4. **A quote on JOIN is refused** (`ORDER_400_001`) before anything is read. A JOIN pays the team's frozen price. Unlike an invite reference on START, a money term sent to the wrong operation is not silently ignored.
5. **Reusable until expiry.** Each use issues a new intent, and each intent is still consumed by at most one payment. A retry that issues a replacement intent after a stale intent or a cancelled Checkout keeps the shown price only while the quote remains unexpired; a replacement intent created at or after `expiresAt` is refused with `CATALOG_409_007`. An intent that accepted the quote before expiry keeps its frozen amount for its own unchanged pay window (40 minutes): the quote's expiry is the deadline to accept the price, not to pay it.
6. **Unquoted START is unchanged.** `quoteId` is optional, and the contract change is additive. Making it required would be a breaking API change and needs approval. The quote id is not stored on the intent; the frozen amount is the audit fact.

## Addendum (2026-09-29): an administrator writes the team terms (#221)

Plan: `docs/agile/sprint-03/team-terms-plan-20260929.md`. V14 gave a product its group terms but nothing wrote them, so START was unavailable outside tests.

**Shape.**
- One command, `PUT /api/v1/admin/products/{productId}/team-terms`, sets `targetSize` and `lifetimeSeconds` and optionally appends a first-team price policy revision, in one local transaction. `TeamTermsAppServiceImpl` takes the product row lock first, the only lock START, quotation and listing edits also take, so a START sees the old terms or the new terms, never a mix, and the global lock order is unchanged.
- The standalone `PUT /products/{id}/team-price-policy` remains for price-only changes. The price part of this command delegates to `TeamPriceAppService.configure`, so a rejected price rolls the terms back with it and the policy rules are not copied. Every price rule, including "team price below the original price", lives only in `TeamPricePolicy`, so a bad price returns the same `CATALOG_400_001` (no field errors) here as on the standalone endpoint, and `CATALOG_409_001` when the listing is not on sale.
- `Product` carries the terms (`ProductTeamTerms`, "unset" when both columns are null). `Product.configureTeamTerms` returns the next revision; `TeamTermsSpecification` checks size and lifetime as one rule set and names each bad term field (`CATALOG_400_003`, a map keyed by `targetSize` / `lifetimeSeconds`). Over HTTP the request repeats those bounds as Bean Validation, so clients get 400 `COMMON_400_002` with a field list first; `CATALOG_400_003` is the domain backstop for other callers. Term errors are reported before the price is checked, so a request with both kinds of error takes two round trips.
- `ProductTeamTerms.configured()` (and `teamTerms.configured` on the admin responses) means only "both terms set and in bounds". START also needs an on-sale listing with stock and a price policy in force for at least the next 2400 s; it can still fail with `CATALOG_409_001` or `CATALOG_409_006` on a configured listing.

**Rules.**
1. **One optimistic token per listing.** `expectedVersion` is the product version, checked in the domain and again in SQL (`WHERE version = ?`, 0 rows → `CATALOG_412_002`). A successful write bumps it once, so a terms write and a concurrent listing edit conflict. `teamPrice.expectedVersion` is the latest policy version, checked separately (`CATALOG_412_001`), because the standalone policy endpoint does not bump the product version.
2. **Terms are editable in every lifecycle state**; the price part still needs an on-sale listing (`CATALOG_409_001`), the existing `TeamPricePolicy.requireCompatible` rule. Seeding order: create draft → set terms → publish → set terms again with `teamPrice`.
3. **A change never moves a team already started.** START freezes the size and lifetime into the intent and the team snapshots them, so an edit only reaches the next START. It is not snapshotted into quotes (#183 D12).
4. **Bounds are code constants** (`ProductTeamTerms`: size ≥ 2, lifetime 2400–86400 s), not a property: an environment must not be able to configure a lifetime shorter than the 2400 s quote window.
5. **No clearing in v1.** Both terms are required on every write. Delisting is the kill switch for START.
6. **Audited by history, not just a stamp.** Each write also appends one immutable row to `log_product_team_terms` (V20: product, resulting version, terms before and after, policy version, actor) in the same transaction, because the columns are overwritten in place.

## Addendum (2026-09-30): expire unpaid intents (#225, US30)

**State and scope.** Order owns nullable `expired_at` (V22). An overdue, unfulfilled, unpaid intent transitions once; the frozen terms, attribution, reserved team ID and row remain auditable. The quote deadline is no later than the business deadline, so selecting `quote_valid_until <= database_now` covers either expired deadline. Fulfilment rejects the marker before business writes; it still permits ordinary fulfilled replays. No public contract or provider cancellation changes.

**Concurrency.** One bounded stable-order candidate query precedes independent ten-second transactions, the per-intent pattern explicitly required by #225. The transaction reads the reserved team ID, takes the groupbuy facade's team lock, then attempts the intent lock with `FOR UPDATE SKIP LOCKED`. START may have no team row; its intent lock still serializes checkout binding and settlement. JOIN shares the existing team-first order. Under the intent lock, the job re-reads `clock_timestamp()`, checks the aggregate and asks payment's read-only facade whether any attempt succeeded. Payment writers take this intent lock before their payment lock. Thus a settling intent is skipped or re-read after settlement; a committed paid/fulfilled intent is never expired. Expiry's UPDATE guards active row, version, null fulfilment/expiry and deadline. A lost guard is a technical failure, not a success.

**Money and replay.** Cleanup neither creates orders nor changes payments. Late provider facts continue through the existing settlement engine: expired quote/intent rejection commits one refund obligation, keyed by payment, before any order write. Fulfilled replay still returns its existing outcome. Webhook event deduplication, order/payment uniqueness and the intent claim guard remain the backstop. A paid but refused intent stays unexpired for audit; its succeeded payment is found with an indexed existence query even if an older review case shadows the blocking-attempt query.

**Bounds and recovery.** `purchase-intent-expiry` has its own renewable `LeasedJobRunner` lease and inherits the existing disabled master switch. Default fixed delay/initial delay/lease/batch are 60 s/30 s/120 s/100; batch validation limits selection to 1..500. The outer sweeper has no transaction: a failed intent rolls back alone. Temporary skip or failure receives five-minute durable backoff in V17 retry state after the item transaction ends. A confirmed terminal succeeded payment is permanently excluded with `retry_after='infinity'`, so even a large backlog of refused paid intents cannot monopolize later batches; the candidate query needs no cross-domain payment join. Exclusion failure receives finite backoff for recovery. Both recovery writes have a three-second statement timeout independent of the ten-second item transaction. A failed backoff write aborts the run and retains both exceptions. Lease loss stops starting further items; locks, version guards and terminal markers make re-entry safe.

**Operations.** V23 creates candidate and succeeded-payment partial indexes concurrently outside a migration transaction. Flyway uses session-level advisory locks (`spring.flyway.postgresql.transactional-lock=false`), as required by [Flyway PostgreSQL guidance](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-postgresql-namespace/flyway-postgresql-transactional-lock-setting); a transaction-level history lock otherwise blocks its own concurrent index build. Apply schema migrations with scheduling disabled; if a concurrent index build fails, inspect and drop only its invalid index before the normal Flyway repair/retry procedure. Count overdue unfulfilled intents before enabling the master switch, including paid refusal cases; monitor expiry/skip/failure counters and shared lease-run duration/errors. Each non-empty run writes one INFO summary with due/expired/skipped/failed/tookMs and the runner's `job-<uuidv7>` trace ID; idle runs log DEBUG. No provider call runs in cleanup. Pausing the master switch retains rows/retry state; no data rollback is needed.

**Evidence.** `PurchaseIntentExpiryIT` exercises bounded batches, replay, paid/live exclusion, one-item rollback and retry bypass, held intent/team locks, recovery-row timeout with lease release, foreign leases and START/JOIN late webhook/cleanup races against real PostgreSQL; only provider network responses are mocked. Timer, binding, service and repository tests cover the opt-in wiring and state guards. Full verification and aggregate coverage evidence are recorded in the sprint day log and NFR-32.
