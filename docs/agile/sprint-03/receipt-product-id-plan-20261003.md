# US27 `Receipt.productId` — implementation plan

Date: 2026-10-03 · Branch: `feature/receipt-productId-20261003` · Issue: #284 (Sprint 3 container #232, story US27) · Owner: los gai · Related: [Sprint 3 plan §4/§5 US27](plan.md), [backend ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md), [backend ADR 0006](../../../backend/docs/adr/0006-payment-attention-read-model.md), T3-06 receipt restyle #209

> **Status (2026-10-03): approved (D7 keeps "See the listing", D2 keeps the owner check) and implemented on this branch; V1–V5 passed locally (full `verify`: 1,525 tests, zero failures; `pnpm check`: 685 tests). CI `contract-check` and SonarCloud wait for the PR.**

## 1. Goal and acceptance criteria

Every receipt (`GET /api/v1/payments/{paymentId}`) carries the purchased product's id, so the receipt page can link back to the listing in every state, including `CREATED`, `FAILED` and `CANCELED` receipts that have no order.

| # | Criterion (issue #284) | Delivered by |
| --- | --- | --- |
| AC1 | `contracts/openapi.json` `Receipt` has `productId`; openapi-diff reports the change as backward compatible | §4 B1–B6, V3, CI `contract-check` |
| AC2 | A `CREATED` receipt with no order shows the listing link to `/products/{productId}` | §4 F1–F3, V4, V5 |
| AC3 | `./mvnw -B verify` and `cd frontend && pnpm check` are green; the US27 `Receipt.productId` part can be ticked | V2, V4 |
| T | Backend test asserts `productId` on a paid and a pending receipt; frontend route test asserts the link for a receipt without an order | §5 |

Non-goals (issue): no receipt copy, layout or state change; no product name or image on the receipt.

## 2. Baseline (read on 2026-10-03 from `8d463ba`)

| Piece | State | Consequence |
| --- | --- | --- |
| `StripeCheckoutController.receipt` (adapter) | Returns `Result<PaymentBrowserService.Receipt>`; no adapter DTO of its own | The contract change is one record component in the application layer; the controller is untouched. |
| `PaymentBrowserService.Receipt` (application) | `paymentId, status, outcome, amount, currency, dealId, orderId, refundPending, refundStatus` | Gains `productId`. Built in `receipt(user, id)` inside a `readOnly`, `REPEATABLE_READ`, 5 s transaction. |
| `CheckoutPaymentRepository.ReceiptSnapshot` (payment domain port) | `userId, status, amount, currency, outcome, dealId, orderId`, read by `PaymentMapper.receipt` from `biz_payment` only | Has no intent id today, so nothing can locate the product. |
| `biz_payment.business_id` | `OrderFulfilmentAdapter.quoteOf` passes `intent.dealId()` as `CheckoutQuote.businessId` for START and JOIN alike | Always a deal id, never a product id. ADR 0001 §3 "`businessId` is a productId for START" describes the HTTP `createIntent` input, not the stored payment column. |
| `biz_group_deal.product_id` | Exists only once the team exists | A START that is `CREATED`, `FAILED`, `CANCELED` or rejected has no deal row, so a deal lookup cannot satisfy AC2. |
| `biz_purchase_intent.product_id` (order domain, V15) | `NOT NULL` for START and JOIN; immutable once issued; intents are never deleted (V22 expiry sets `expired_at`); `biz_payment.intent_id` is non-null for every receipt-eligible row (`PaymentMapper.receipt` filters `intent_id IS NOT NULL`) | The only source that covers every receipt state. |
| ADR 0006 | "does not authorize cross-domain writes or joins in ordinary aggregate repositories" | `CheckoutPaymentRepositoryImpl.receipt` must not join `biz_purchase_intent`. |
| ADR 0001 | Cross-domain collaboration goes through ports/facades; order implements `PaymentIntentPort` (`PurchaseIntentAppService`), registered with no profile in `PaymentIntegrationConfiguration` | A read-back method on that port is the established route from payment to intent facts. |
| `PaymentIntentPort` implementors | `PurchaseIntentAppService` only; tests use Mockito mocks, no lambdas | Adding an abstract method breaks no test stub. |
| Frontend `payments.$paymentId.tsx` | `productId = order.data?.productId` from a second read `GET /api/v1/orders/{orderId}` (`useReceiptOrder`); link label is "See the listing" | No link for any receipt without a readable order. The order read is still needed for `paidTime`. |
| Frontend tests | Receipt route tests live in `src/features/payment/payment.test.tsx` (L383 "links a saved order's receipt…", L395 "offers no listing link while the receipt has no order…") | L395 asserts today's gap and must be inverted. |
| Dev mock `src/test/browser-msw.ts` | `settledReceipts` and `readCheckout` build `Receipt` objects without `productId` | Must gain `productId` so `pnpm dev` with mocks shows the new behaviour. |
| Existing ITs | `StripeLocalIntegrationTest` reads a paid receipt (L147) and a `CREATED` START receipt with no deal or order (L664) against PostgreSQL | Both are the natural home for the backend half of T. |

## 3. Decisions

| ID | Decision | Recommendation | Alternatives rejected |
| --- | --- | --- | --- |
| D1 | Source of `productId` | **The purchase intent, read through `PaymentIntentPort`** (payment-domain port, order implementation). Covers every status, keeps payment out of order tables, follows ADR 0001. | (a) Join `biz_purchase_intent` in `PaymentMapper.receipt` — forbidden by ADR 0006 for aggregate repositories. (b) New `biz_payment.product_id` column (V25 + backfill) frozen at checkout — touches the money write path, `CheckoutQuote`, the payment insert and the settlement tamper check for a display field. (c) `biz_group_deal.product_id` via `business_id` — empty for unfulfilled START (§2), fails AC2. (d) A dedicated receipt read model like ADR 0006 — a second projection plus a new ADR for one column. |
| D2 | Port shape | **Add `Optional<UUID> purchasedProduct(UUID user, UUID intentId)` to `PaymentIntentPort`.** Same implementor and same payment→order direction; no new bean or wiring. Takes the buyer so order re-checks ownership (empty on mismatch) — defence in depth, because receipt ownership is already enforced by payment. | A separate `PurchaseProductPort` (one more interface and bean for one method); an un-scoped `productOf(intentId)` (no owner check). |
| D3 | Missing intent or owner mismatch | **`productId` is `null`; the receipt still returns 200.** The receipt is the buyer's money truth; a display link must never turn it into an error. Not expected in practice (§2: intent never deleted, `NOT NULL` product). No log line: the receipt is polled for up to a minute and would repeat it. | Throwing `PAYMENT_NOT_FOUND`/500 (hides a real payment). |
| D4 | Contract shape | **`productId` UUID, optional (not in `required`), appended as the last `Receipt` property.** Matches `dealId`/`orderId`, which are also absent for some states. Additive response property → openapi-diff compatible. | Marking it required (would be a false promise under D3). |
| D5 | Ownership of the transaction | `PurchaseIntentAppService.purchasedProduct` is `@Transactional(propagation = MANDATORY, readOnly = true)` and joins the receipt's `REPEATABLE_READ` transaction; one primary-key read via the existing `PurchaseIntentRepository.findById`. | Its own transaction (would read outside the receipt snapshot). |
| D6 | Frontend link source | **`receipt.productId` only.** The order read stays, solely for `paidTime`. No fallback to `order.productId`: an order always comes from the same intent, so it can never add a product the receipt lacks. | `receipt.productId ?? order.data?.productId` (dead branch, untestable). |
| D7 | Link label | Keep **"See the listing"** (non-goal: no copy change). The issue text says "Back to the listing"; that wording belongs to `/login`. **Reviewer to confirm.** | Renaming the link (copy change outside scope). |
| D8 | Decision record | **Dated addendum to backend ADR 0001** ("`PaymentIntentPort` gains a read-back for the receipt") rather than a new ADR; ADR 0006 unchanged. | New ADR (duplicates 0001's subject). |

## 4. Change list

Backend (bounded contexts: `payment` consumer, `order` provider; no schema change, no migration):

| ID | Layer | File | Change |
| --- | --- | --- | --- |
| B1 | domain (payment) | `blocbuy-domain/.../payment/gateway/PaymentIntentPort.java` | Add `Optional<UUID> purchasedProduct(UUID user, UUID intentId)` with Javadoc (D2, D3); class Javadoc becomes "preparation and read-back". No Spring, no SDK types. |
| B2 | domain (payment) | `blocbuy-domain/.../payment/repository/CheckoutPaymentRepository.java` | `ReceiptSnapshot` gains `UUID intentId` (own-table column). |
| B3 | infrastructure | `blocbuy-infrastructure/src/main/resources/mapper/payment/PaymentMapper.xml` | `receipt` select adds `intent_id` (still `biz_payment` only, by primary key). |
| B4 | infrastructure | `blocbuy-infrastructure/.../payment/persistence/CheckoutPaymentRepositoryImpl.java` | Map `row.getIntentId()` into the snapshot. |
| B5 | application (order) | `blocbuy-application/.../order/service/PurchaseIntentAppService.java` | Implement `purchasedProduct`: `intents.findById(intentId).filter(owner).map(PurchaseIntent::productId)`; `@Transactional(propagation = MANDATORY, readOnly = true)` (D5). |
| B6 | application (payment) | `blocbuy-application/.../payment/service/PaymentBrowserService.java` | `Receipt` appends `UUID productId`; `receipt()` resolves it via `intents.getIfAvailable()` → `purchasedProduct(user, snapshot.intentId())`, `null` when the port is absent or returns empty (D3). One call per request, never in a loop. |
| B7 | contract | `contracts/openapi.json` | Regenerate with `-Popenapi-export` (never hand-edit) and commit. |
| B8 | docs | `backend/docs/adr/0001-payment-business-integration.md` | Dated addendum (D8). |

Frontend:

| ID | File | Change |
| --- | --- | --- |
| F1 | `frontend/src/api/generated/*` | `pnpm gen:api` after B7 (`api-client-regen` skill); `Receipt.productId?: string`. |
| F2 | `frontend/src/routes/_authenticated/payments.$paymentId.tsx` | `const productId = receipt?.productId;` (D6); `useReceiptOrder` kept for `paidTime`; comment updated. |
| F3 | `frontend/src/test/browser-msw.ts` | `receiptFor` and the `CREATED` branch of `readCheckout` carry the product id of the mocked listing. |
| F4 | `frontend/src/features/payment/receipt-view.ts` | Header comment only if it mentions the order as the product source; no logic change. |

Evidence: `docs/agile/sprint-03/log/2026-10-03.md` entry; `docs/agile/sprint-03/plan.md` US27 — note that the `Receipt.productId` half is done (the row also covers #200 and T3-06, so the whole row is ticked only when those are done too); this plan's status line.

## 5. Tests

| ID | Level | File | Asserts |
| --- | --- | --- | --- |
| T1 | unit | `PaymentBrowserServiceTest` | `productId` comes from the port for the snapshot's intent and the caller's user id; `null` when the port returns empty; `null` when no `PaymentIntentPort` bean is available; wrong-owner receipt still `PAYMENT_NOT_FOUND` before the port is called. |
| T2 | unit | `PurchaseIntentAppServiceTest` | `purchasedProduct` returns the intent's product for its owner; empty for another user; empty for an unknown intent. |
| T3 | unit | `CheckoutPaymentRepositoryImplTest.receiptReadsThePaymentRowOnce` | Snapshot carries `intentId`. |
| T4 | web slice | `PaymentBrowserControllerTest` | `$.data.productId` serialised on the receipt response. |
| T5 | IT (PostgreSQL) | `StripeLocalIntegrationTest` L147 (paid) and L664 (`CREATED` START, no deal, no order) | `receipt.productId()` equals the purchased `product` in both — the issue's "paid and pending receipt" requirement, against the real mapper, port and intent row. |
| T6 | route (Vitest + MSW) | `payment.test.tsx` L383 | The paid receipt links to `/products/{productId}` from `receipt.productId`; the order read still supplies the paid line. |
| T7 | route (Vitest + MSW) | `payment.test.tsx` L395 (rewritten) | A `CREATED` receipt with no `orderId` shows "See the listing" → `/products/{productId}` and makes no order request (AC2); a receipt without `productId` shows no listing link. |

Coverage target: 100% of new/changed lines and branches on touched backend files (the `null` arms in B6 and the owner filter in B5 are the likely misses — T1/T2 cover them).

## 6. Verification

| ID | Check | Proves |
| --- | --- | --- |
| V1 | `cd backend && ./mvnw -B -pl blocbuy-bootstrap -am test -Dtest='PaymentBrowserServiceTest,PurchaseIntentAppServiceTest,CheckoutPaymentRepositoryImplTest,PaymentBrowserControllerTest' -Dsurefire.failIfNoSpecifiedTests=false` | Focused unit/slice tests. |
| V2 | `cd backend && ./mvnw -B verify` on JDK 21, then the JaCoCo aggregate missed-line script from the backend skill | Spotless, Checkstyle, ArchUnit (port in payment domain, implementation in order application — existing pattern), SpotBugs, T5 via Testcontainers, coverage on touched files. |
| V3 | `./mvnw -B verify -Popenapi-export -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true`, commit, then `git diff --exit-code -- contracts/` from repo root | AC1 drift gate; CI `contract-check` openapi-diff confirms backward compatibility. |
| V4 | `cd frontend && pnpm gen:api && pnpm check` | Types, lint, Vitest (T6, T7). |
| V5 | `pnpm dev` with mocks: open a fresh mock checkout receipt while `CREATED`, then after it settles; screenshot both for the PR | AC2 visually; PR template UI evidence. |

## 7. Risks and consistency

- **Cross-domain coupling.** Payment gains one more read dependency on order through an existing port; no table, Mapper or DO crosses the boundary (ADR 0001/0006 respected). ArchUnit in V2 is the guard.
- **Consistency.** Read-only; no lock, no write, no funds path touched. `product_id` on the intent is immutable, so the extra read cannot disagree with the payment it belongs to. It joins the receipt's `REPEATABLE_READ` snapshot (D5).
- **Performance.** One extra primary-key read per receipt request; the page polls for at most a minute. No N+1: the receipt is a single-item read.
- **Contract.** Additive optional response property; old clients ignore it. Breaking-change risk is nil and openapi-diff checks it in CI.
- **Null product.** Possible only with inconsistent historical data (D3); the UI then shows no listing link, which is today's behaviour.
- **Open question for review.** D7 link label; D2 owner re-check in order (kept unless the reviewer prefers the simpler un-scoped read).
