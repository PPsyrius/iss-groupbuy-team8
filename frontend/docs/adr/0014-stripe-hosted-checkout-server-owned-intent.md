# 0014. Payments through Stripe Hosted Checkout with a server-owned intent

Date: 2026-09-23 (records the decision shipped in PR #126 on 2026-09-21)

## Status

Accepted

## Context

UC3 (start a bloc) and UC4 (join a bloc) are pay-first: a seat is held only once a payment is
confirmed. The backend owns pricing, quotes and Stripe (`backend/docs/design/paid-group-participation.md`,
`stripe-*`), and the frontend never sees a card number or a secret. The slice was built by the
payment team outside the Sprint 2 plan (§12 note 49); this ADR records what landed so the plan,
the review issues (#128, #131, #135, #137) and the T3 rows have one reference.

## Decision

Two generated calls, both same-origin (ADR 0002, 0003, 0009): `POST /api/v1/payments/intents`
freezes a server-owned intent for `{ operation, businessId }`; `POST /api/v1/payments/checkout-sessions`
turns that intent into a Stripe Hosted Checkout session and answers `{ paymentId, status, checkoutUrl }`.
`use-checkout.ts` keeps only references in `sessionStorage` under `blocbuy.payment.attempt.<user>.<op>.<businessId>`
(intentId, an idempotency key, the paymentId once known) so a retry after a timeout reuses the same
intent and key instead of creating a second charge; a `PAYMENT_409_001` answer frees both keys (#135).
The browser is sent to `checkout.stripe.com` only (host and scheme checked); Stripe returns to
`/payments/return?paymentId=`, which redirects to the receipt `/payments/$paymentId`. The receipt
reads `GET /api/v1/payments/{id}` (owner-scoped on the server), polls every 2 s for up to 60 s while
the status is `CREATED` or a refund is pending, clears the attempt keys once terminal, and labels
the refund state (`refund-status.ts`). Money actions are gated on `GET /api/v1/payments/capabilities`
(`use-payment-availability.ts`) so an older backend never shows a pay button.

Alternatives rejected: Stripe Elements in the SPA (card data in our origin, PCI scope, no benefit for a
course demo); client-side quote and price maths (the server must revalidate anyway); polling the
webhook state from the frontend (the receipt endpoint already reflects it).

## Consequences

"Join this bloc" calls `begin('JOIN')` directly; "Start a bloc" first opens the quote sheet
(plan T3-01, addendum below) and calls `begin('START')` from its "Continue to payment". No refund can be
started from the frontend; the receipt only reports it (UC6, backend #29). The receipt and return
pages have no artboard (`design-screens.md` §4.6, T3-06). `/account` lists the buyer's orders from
`GET /orders/me` (T3-02) with no refund label: the order schema has no refund field, so refunds
stay on the receipt (option B on #128). `payments.return` reads its `paymentId` through a valibot
search schema (#131, AGENTS.md rule 2).

Addendum (T3-01, 2026-09-24): the first-team quote is a preview, not a price lock. `QuoteSheet.tsx` and `use-quote.ts` issue `POST /api/v1/team-price-quotes` and re-read a stored quote id with `GET /api/v1/team-price-quotes/{quoteId}`; they show the amount and a countdown to `expiresAt`, and an expired quote disables payment and offers a new price. A 409 (`CATALOG_409_001`) reads as "no bloc price set yet". `getPolicy` is not called, because it requires `catalog:product:write` on purpose (`backend/docs/design/team-price-quotes.md`, "explicitly protected"), which buyers never hold. `IntentRequest` carries no quote id: START re-prices from the latest policy (`PurchaseIntentAppService` → `lockStartQuote`), so an admin edit between the quote and `begin()` changes the charged amount. Stripe's page shows the amount actually charged, and the sheet says so. Whether START should honour the quote id is asked on #50.

Addendum (T3-04, 2026-09-24): UC4 has no join endpoint of its own; joining is the JOIN purchase intent. The server's `lockJoinTerms` refuses a closed (`GROUPBUY_409_002`), already-joined (`_003`), full (`_004`) or missing (`GROUPBUY_404_001`) bloc when the intent is created and again when Checkout is prepared (`StripeCheckoutService.prepare` runs `lockQuote` before `findRequest` looks up an earlier Checkout request by idempotency key), so `use-checkout.ts` shows the reason instead of the generic retry copy, and the listing refetches. The copy says only that this attempt was not charged, plus the Errors artboard's line that any bloc already paid into is safe under My blocs: full and closed are checked before membership, so a buyer who paid for a seat elsewhere can get `_004` or `_002`. A refusal keeps the stored intent and idempotency key, because a fresh intent would bypass the one blocking attempt per intent (CodeRabbit, PR #200). A seat can still be lost after payment (`joinPaid` stays the authority); that path ends in a refund the receipt already reports.
