# First-team price quotation foundation — issue #50

This increment makes the first team quotable without an existing group deal. It does not implement UC9 CRUD, checkout, payment, stock reservation, team creation, joining, or refunds.

## Boundaries and Strategy

Catalog owns append-only `TeamPricePolicy` revisions and immutable `TeamPriceQuote` snapshots. The existing `PricingStrategyResolver` selects the real `PricingStrategy` by product pricing mode. `DualPricingStrategy.quoteTeamPrice` validates the latest catalog policy and returns its configured amount. It never consults existing deals. Existing browse `resolve` behavior remains unchanged: its optional displayed group price still comes from groupbuy.

No additional pattern names are claimed. A snapshot record is not a GoF Factory Method. Row locks and uniqueness enforce consistency; Strategy is only the pricing variation seam.

## Additive HTTP contract

All success responses use the established HTTP 200 `Result` envelope; failures use the global error-code handler. Regenerate `contracts/openapi.json` from the application, never by editing JSON.

- `PUT /api/v1/products/{productId}/team-price-policy`: requires `catalog:product:write`. The V6 migration grants this new permission only to the existing administrator role. Body: decimal-string `amount`, literal `currency: "sgd"`, inclusive `validFrom`, exclusive `validUntil`, and required `expectedVersion`. Version 0 creates revision 1; later requests must match the latest revision. A stale retry returns 412 and creates nothing. This is a conditional replacement, not an unconditional last-write-wins PUT.
- `GET /api/v1/products/{productId}/team-price-policy`: same permission and active-account validation, explicitly protected even though other product GET routes are anonymous.
- `POST /api/v1/team-price-quotes`: authenticated body contains only `productId`; owner is taken from the trusted session. Each call creates a new snapshot, so retries are **not idempotent**. No financial or inventory effect exists. Clients must not treat this as checkout.
- `GET /api/v1/team-price-quotes/{quoteId}`: active authenticated owner only; absent and foreign-owned IDs both return 404. Historical snapshots remain readable after expiry. Reading is not validation or consumption of a quote.

V1 quotes support SGD only, exact positive cents fitting `numeric(18,2)`, strictly below the product original price. No rounding, client-selected amounts on issuance, or guessed currency minor units. Wire amounts are decimal strings. Policy instants normalize to PostgreSQL's microsecond precision before validation, so a valid Java-only nanosecond window cannot collapse when persisted. Latest policy always replaces earlier revisions, including a future or expired latest policy: there is no fallback.

Quote lifetime is an explicit v1 rule: at most **15 minutes**, clipped to policy expiry. Expiry is exclusive. Policy edits never alter old snapshots or extend their deadlines. Future checkout must revalidate owner, quote expiry, product availability and consumption under its own transaction; this increment promises neither single-use quotes nor fulfilment.

## Persistence, authorization, and concurrency

PostgreSQL 18 `domain` schema is authoritative; no new middleware or runtime dependencies. V6 adds new tables only. IDs are native UUIDv7, with the repository's existing application generator for quotes and PostgreSQL default for revision row IDs. DOs and mapper XML remain infrastructure-only.

Each configuration/issuance command uses a short, **10-second bounded** application transaction. Lock the active `biz_product` row first; then validate current account state through identity's public application facade, read the latest policy, and read PostgreSQL `clock_timestamp()` **after the product lock**. This avoids using transaction-start time after waiting. No provider/network call and no retry loop occurs in the transaction. Infrastructure inserts check affected-row counts. A database error rolls back the whole command.

`(product_id, version)` is unique across all revisions, including deleted rows. Revisions and quotes have no update/delete repository or API operation. Administrative tools must never soft-delete a revision to restore an older policy: issue a new revision instead. Product status/price/delete writers must use the same product-row lock; this includes future UC9. Stock is not reserved by a quote.

User account state is rechecked when executing a request, rather than trusting a possibly stale session. The identity read is not a durable authorization lease: an account disabled after that read can leave a historical non-financial snapshot. Future payment acceptance must independently revalidate the account. Permission checks use the existing Sa-Token RBAC tables, not role names in catalog code. There is no tenant model in this global catalog.

No foreign-key or check constraints are added, per team DDL agreement. Catalog owns product/policy/quote reference validity. Immutable quote history must be retained if a product or account is later disabled or soft-deleted. There is no physical-purge path in this increment; a future retention/purge job must reconcile product/policy/owner references, preserve snapshots used by payment/order records, and coordinate with catalog and identity owners before deletion. The product-policy index supports those audits. Do not turn an orphan into a new price or silently delete monetary history.

## Verification

Tests cover first-team pricing without an active deal, exact cents and currencies, validity boundaries, stale policy writes, disabled accounts, server-derived ownership, and snapshots independent of later policy reads. PostgreSQL 18 integration tests execute the repository migrations and actual mapper XML, verify UUIDv7 and admin-only grants, round-trip snapshots across revision changes, enforce unique revisions, roll back failed work, and make a competing reader wait for the product lock.

CI must run both unit tests and `*IT` tests, plus formatting, static analysis, ArchUnit (including pricing packages), API regeneration/compatibility, and security gates. Docker absence is a failure, not an accepted integration-test skip. HTTP permission/validation tests and the generated contract are part of the same PR. This document describes the implemented contract; only actual green run results count as verification evidence.

`TeamPriceFlowIT` additionally exercises real HTTP, Sa-Token RBAC, application transactions, Flyway and PostgreSQL without business-service mocks. It verifies first-team issuance, historical snapshot reads after repricing, ownership rejection, unchanged inventory/payment state, disabled-account rejection and two concurrent configuration attempts committing exactly one revision. Sessions use an isolated test-only memory store; these tests do not claim Redis or Google OAuth integration coverage.
