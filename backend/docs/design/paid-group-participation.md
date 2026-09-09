# UC3 / UC4: groupbuy-side paid participation

## Scope and ownership

This slice implements groupbuy behavior behind an internal application collaborator. It does not
implement checkout, call Stripe, change the payment webhook, create orders, reserve inventory,
execute refunds, or expose a public payment-confirmation endpoint. Existing catalog Strategy
pricing remains the authority for accepted prices. No frontend or OpenAPI surface changes here.

`PaidParticipation` validates the shape of already-verified payment facts; constructing it does
not prove payment. Only a trusted application collaborator may call `PaidGroupDealService` after
binding the payment to the buyer and intended operation and verifying the amount/currency against
persisted checkout terms. Browser redirects, request bodies and raw provider events are not this
collaborator. The current payment module is not wired to these methods.

The caller owns active-user/product validation, an authoritative accepted catalog price snapshot,
and the order/inventory transaction. Target size and deadline are explicit accepted terms, not
invented default business policy. `TeamPriceAppService.getQuote` is a historical read, not a
checkout-consumption or stock-reservation API. Do not wire it directly to this write path.

## Domain behavior

- UC3 uses the `PaidGroupDealCreator` initiation workflow and its overridden factory method in
  `StandardPaidGroupDealCreator`. It creates an OPEN deal with exactly one paid leader. The static
  reconstruction method is only persistence reconstruction, not the Factory Method evidence.
- UC4 joins the existing immutable aggregate; it never uses the creation factory or creates a
  replacement deal. It validates frozen amount/currency, duplicate membership, OPEN status,
  exclusive deadline and capacity. The last paid member changes status to SUCCEEDED.
- Transitions return a new snapshot; there is no public status/count setter. This is guarded
  domain behavior, not a claim that the full UC5 GoF State implementation is complete.
- SGD amounts must be positive, exactly representable in cents and fit numeric(18,2). No floating
  point, rounding or inferred discounts. Currency restriction matches the current quote scope.

## Persistence and replay

The application uses a short local database transaction. A receipt keyed by local payment ID
serializes duplicate applications and stores the immutable request fingerprint and outcome.
Identical retries return the stored outcome even after the deal changes; changed content with the
same payment ID is rejected. Provider event-ID deduplication still belongs to the payment module.

For a join, lock the receipt then the deal, read `clock_timestamp()` after acquiring the deal lock,
check active membership, and persist the member/count/status and receipt together. Existing active
deal/user uniqueness and new member/payment uniqueness backstop concurrent requests. Creation
persists the deal and leader together. Technical failures roll back; they are not translated into
successful processing. Enclosing caller transactions must use compatible lock order: acquire any
catalog/payment locks before entering this receipt -> deal protocol, never in the reverse order.
No network call belongs inside these transactions.

New transactions have a 10-second deadline, matching catalog write services. Receipt/deal lock
timeouts propagate as technical failures and roll back; they never produce compensation or success.
When joining an existing caller transaction, Spring REQUIRED inherits that transaction's deadline:
the caller must set an equally bounded timeout and retain the documented lock ordering.

A paid business rejection records `compensation_required` and a stable reason, not `refunded`.
The receipt is a durable handoff fact, not a working refund processor. Payment/refund collaborators
must consume it idempotently and track actual provider completion. A receipt alone also does not
implement notification/attribution Observer dispatch; those integrations remain separately owned.

V7 is additive. Existing memberships have no historical payment ID backfill. Do not rewrite applied
V5; its paid-count semantics remain authoritative. Receipt retention must cover provider replay
and financial reconciliation, not a short HTTP cache TTL. Production admission stays unwired until
the payment, catalog/order and recovery contracts are integrated and tested together.

## Verification and release boundary

Domain tests cover first member, exact cents, invalid identity/terms, middle/last member, terminal
states, capacity, duplicate membership and deadline equality. Application tests cover replay and
ordering. PostgreSQL integration tests exercise constraints, rollback and lock behavior; bootstrap
tests exercise the real Spring transaction proxy and concurrent last-seat race. These are local
groupbuy-side tests, not Stripe test-mode end-to-end evidence.

The real database tests also race two payments from one buyer with spare capacity and verify
database cancellation (SQLSTATE 57014), rollback and successful retry for stalled receipt/deal locks.

Before exposing a buyer API, require tests for verified payment -> inventory/order -> group/member
atomicity, quote consumption, failed/late payment recovery, durable refund processing and actual
Stripe test-mode replay. Generate OpenAPI and TypeScript only when that API is implemented.

## Post-Deploy Monitoring & Validation

No payment or buyer route is enabled by this slice. On a test environment, the integrator should
inspect receipt outcome counts, pending compensation age, duplicate-key errors and deal/member
count mismatches after each controlled admission batch and before enabling traffic. Healthy signal:
one terminal receipt per applied payment and paid member count equal to `current_size`. Any
orphaned receipt, duplicate application or mismatch blocks rollout; disable the new caller and
preserve receipt/member rows for reconciliation. Roll back application code without dropping
receipt history or member payment IDs. Deployment and enabling a caller require separate approval.
For an already-populated deployment, assess the existing member table size and bound migration
lock/statement waits before running V7. Its transactional ALTER and ordinary unique-index build
are not an online, zero-downtime migration; a live high-volume rollout needs a separately tested
expand/index sequence. Empty-container migration success does not verify that release scenario.
