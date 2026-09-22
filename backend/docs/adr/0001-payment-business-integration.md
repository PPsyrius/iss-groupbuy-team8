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

Open: no product has group terms yet, so the START path is unavailable until an operator supplies
them; there is no admin surface for that in this change (NFR-26 records it as the unmet part).
A START reserves its team id before the team exists, so an intent that is never paid leaves a
dangling `deal_id` — harmless, since the column has no foreign key, but a cleanup job for expired
unfulfilled intents is owed in Sprint 3.
