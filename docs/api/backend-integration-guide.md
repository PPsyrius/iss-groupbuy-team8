# Backend integration guide for the frontend

Date: 2026-09-22. Covers the HTTP surface as of the order/payment lifecycle change on this date.

`contracts/openapi.json` is the contract: every path, request body and response field is generated
from the controllers, and `pnpm gen:api` turns it into the typed client the frontend must use. This
document is for everything the schema **cannot** say — which string values a `status` field actually
takes, which header a POST needs, what order two calls must happen in, and which capabilities exist
in the backend but have no endpoint yet.

When this document and `contracts/openapi.json` disagree, the contract is right and this file is
stale; say so in the PR that notices it.

## 1. Transport

- Base path is `/api/v1`. Every call is relative to the page origin (`baseUrl: '/'` in
  `frontend/src/api/hey-api.ts`); the `servers[0].url` in the contract is the export harness and is
  deliberately ignored.
- Every JSON endpoint returns the same envelope:

  ```json
  { "code": "COMMON_200_000", "data": { }, "message": "Success" }
  ```

  `errors` and `traceId` appear only on failures (`traceId` correlates with backend logs — put it in
  any error report). `data` is absent on failures and on successful void operations.
- **The HTTP status is never 200 for a failure.** It comes from the same `code`: `COMMON_404_001` is
  404, `ORDER_409_001` is 409, and so on. `frontend/src/api/client.ts` already turns every non-2xx
  into an `ApiError` carrying `code`, `message` and `traceId`; `unwrapResult` handles the 2xx case.
  Branch on `error.code`, never on `error.message` — messages are English defaults and will be
  localized later, codes never change once published.
- Two endpoints are envelope-exempt because they are protocol handshakes, not JSON resources:
  `GET /auth/oauth2/google` and its callback both answer 302 with `Location` and `Set-Cookie`.

## 2. Authentication

- Sa-Token, cookie `blocbuy-token`, `HttpOnly` + `SameSite=Lax` (and `Secure` outside the plain-HTTP
  dev profiles). The frontend cannot read it and does not need to: `credentials: 'same-origin'` is
  already set on the generated client.
- **Login is a full-page navigation to `/api/v1/auth/oauth2/google`.** It is not an XHR — the
  redirect chain ends on a frontend landing URL configured server-side. A provider-side failure also
  lands on the frontend, with the error code in the query string, rather than leaving the user on a
  raw JSON error.
- `GET /auth/me` answers 401 `COMMON_401_001` when anonymous; it never returns an empty success.
  The existing interceptor settles the `me` query as anonymous on 401 instead of looping.
- `POST /auth/logout` invalidates the token; it returns `data`-less success.
- Every endpoint below requires a session unless stated otherwise. `403 COMMON_403_001` means the
  session is real but not allowed (admin paths, or a missing CSRF header — see §3).

## 3. Two headers the schema does not enforce for you

| Header | Where | Value | If you omit it |
|---|---|---|---|
| `X-BlocBuy-Request` | `POST /payments/intents`, `POST /payments/checkout-sessions` | exactly `checkout` | 403 `COMMON_403_001` |
| `Idempotency-Key` | `POST /payments/checkout-sessions` | `[A-Za-z0-9._-]{1,128}` | 400 `COMMON_400_001` |

`X-BlocBuy-Request` is the CSRF control: a JSON body plus a non-simple header forces a preflight
cross-origin, and no CORS is configured, so a cross-site form cannot reach these endpoints.

`Idempotency-Key` is a real idempotency key, not a nonce. **Retrying a failed checkout with the same
key returns the same payment; retrying with a new key while an attempt is still open is rejected**
with `PAYMENT_409_001`. Generate one key per user-initiated checkout attempt, keep it for the
lifetime of that attempt (including retries after a network error), and only mint a new one when the
user deliberately starts over.

## 4. The vocabularies

These are the string values the API actually emits. None of them is expressible in the OpenAPI
schema, because they are Java enum names and database codes rather than declared enums.

**Order `status`** — lowercase, two `l`s in `cancelled`:

| Value | Meaning |
|---|---|
| `pending` | Paid, order created, team still open |
| `confirmed` | The team reached its target size |
| `cancelled` | The team expired; `cancelTime` is set and a refund is owed |

**Payment `status`** (receipt) — uppercase, **one** `l` in `CANCELED`:

| Value | Meaning |
|---|---|
| `CREATED` | Checkout session issued, no provider outcome yet |
| `SUCCEEDED` | Provider-verified payment |
| `FAILED` | Verified terminal unpaid failure |
| `CANCELED` | Session expired without a payment intent |

**Receipt `outcome`** — what happened to the money's business side:

| Value | Meaning |
|---|---|
| `PENDING` | Not settled yet (the backend has recorded no outcome) |
| `FULFILLED` | Membership and order exist |
| `REFUND_REQUIRED` | Paid, but the business refused (team full, expired, product gone) — a refund obligation exists |

**Receipt `refundStatus`** — uppercase mirror of the refund row, `NONE` when there is no obligation:
`NONE`, `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REVIEW`, `CANCELED`. `refundPending` is a
convenience boolean, true for `PENDING` and `PROCESSING` only.

> The spelling difference is not a typo to fix: `cancelled` (order) and `CANCELED` (payment, refund)
> are separate published vocabularies on separate aggregates. Compare against the exact constants.

## 5. Money and time

- **Amounts are not consistently typed, and this will bite you.** `Receipt.amount` is a JSON
  **string** (`"12.00"`, scale preserved). `OrderSummaryResponse.unitPrice` / `totalAmount`, and the
  catalog's `individualPrice` / `groupPrice`, are JSON **numbers**. Format the string as it arrives;
  for the numbers, format for display and never accumulate them in floating point.
- Currency is a lowercase ISO code (`"sgd"`). Only SGD exists today.
- All instants are ISO-8601 UTC (`2026-09-22T10:00:00Z`). The generated types give you `string`;
  parse at the edge, never assume local time.

## 6. Endpoint catalogue

### Catalog (public browse)

| Endpoint | Notes |
|---|---|
| `GET /products` | Paged browse/search, dual pricing |
| `GET /products/{id}` | Detail plus `deal`: the **active** team only |
| `GET /products/{id}/team-price-policy`, `PUT …` | Admin-scoped (`catalog:product:write`) despite the public-looking path |
| `POST /team-price-quotes`, `GET /team-price-quotes/{quoteId}` | Quotation, not checkout |

`ProductDetailResponse.deal` is `null` unless a team is **open and not past its deadline** — the
query filters on `status = 'open' AND expire_time > CURRENT_TIMESTAMP`. An overdue team therefore
disappears from the product page immediately, before anything marks it `expired` in the database.
Do not treat "`deal` is null" as "the team expired"; it only means there is nothing joinable.
`ActiveDealResponse` carries `dealId`, `targetSize`, `currentSize`, `expireTime` — there is no
status field, by design.

### Payments

| Endpoint | Notes |
|---|---|
| `GET /payments/capabilities` | `{ enabled }`. `Cache-Control: no-store`. Requires login |
| `POST /payments/intents` | `{ operation: "START" \| "JOIN", businessId }` → `{ intentId }` |
| `POST /payments/checkout-sessions` | `{ intentId }` → `{ paymentId, checkoutUrl, status }` |
| `GET /payments/{id}` | Owner-scoped receipt. `Cache-Control: no-store` |

`businessId` is the **product id for START** and the **deal id for JOIN**. The browser never sends a
price: the server prices the request under a row lock and freezes it into the intent.

When the Stripe rollout is off, the three non-capability endpoints answer **404 `COMMON_404_001`**,
not a payment-specific code. Gate the UI on `capabilities.enabled` rather than on that 404.

### Orders

| Endpoint | Notes |
|---|---|
| `GET /orders/me` | Paged, newest first, owner-scoped by session |
| `GET /orders/{orderId}` | **New (2026-09-22).** One order, owner-scoped |

`GET /orders/{orderId}` answers **404 `ORDER_404_001` for another buyer's order** — deliberately
indistinguishable from an id that does not exist, so it cannot be used to probe which orders are
real. Do not write UI that treats 403 as "not yours"; that status never arrives here.

Neither response carries `paymentId` or any recipient PII. To get from an order back to its payment
you need the receipt you already hold from the checkout flow.

### Admin

`GET|POST /admin/products`, `GET|PUT|DELETE /admin/products/{productId}`,
`PUT /admin/products/{productId}/status`. Admin role plus `catalog:product:write`. All mutations are
compare-and-swap: send `expectedVersion`, handle `CATALOG_412_001` / `CATALOG_412_002` by refetching
and asking the user to retry. There is no admin route in the frontend today.

### Webhook

`POST /webhooks/stripe` is provider-facing, signature-authenticated, and not for the browser.

## 7. The checkout flow, end to end

```
1. GET  /payments/capabilities        → enabled?  (else: no payment UI at all)
2. POST /payments/intents             → intentId          [X-BlocBuy-Request]
3. POST /payments/checkout-sessions   → checkoutUrl       [X-BlocBuy-Request, Idempotency-Key]
4. window.location = checkoutUrl      → Stripe Hosted Checkout
5. Stripe redirects back to …?paymentId=<uuid>
6. GET  /payments/{paymentId}         → poll until outcome != "PENDING"
```

Four things about step 3 and step 6:

- **`checkoutUrl` can be `null` on a 2xx response.** That is not an error: it means the attempt is no
  longer usable (a webhook or a business deadline won the race), and `status` tells you which state
  the payment is in. Never navigate to a null URL; re-read the receipt and show the user where they
  actually stand.
- The return URL is server-owned and already carries `?paymentId=`. Do not construct it yourself.
- The receipt is the only place the browser learns the business outcome. Step 5 landing on the
  success URL means Stripe accepted the card, **not** that the buyer is in the team: the webhook
  settles the business side asynchronously, so `outcome` may still be `PENDING` for a moment.
- Polling is bounded by the existing receipt page at 2s × 30. Keep a deadline — the backend does not
  push, and an unbounded poll on a stuck payment is a hot loop.

A `REFUND_REQUIRED` outcome is a complete, successful response, not a failure to retry. It means the
money arrived and the business refused; the refund is owed and its progress shows in `refundStatus`.

## 8. The order lifecycle (new on 2026-09-22)

Before this change orders were born `pending` and stayed there forever. They now move:

- **Team fills up** → every `pending` order of that team becomes `confirmed`, in the same
  transaction as the join that completed it. Both buyers' orders, including the one written moments
  earlier in that transaction.
- **Team expires** → every `pending` order becomes `cancelled` with `cancelTime` set, and each paid
  member gets a refund obligation, again in one transaction. The order and the money owed cannot
  disagree.

What this means for the UI:

- `GET /orders/me` and `GET /orders/{id}` now return all three statuses, so any order list has to
  render `confirmed` and `cancelled` rather than assuming `pending`.
- A `cancelled` order implies a refund is owed. The refund's progress is **not** on the order — it is
  on the payment receipt, and the order does not carry `paymentId`. Linking those two in the UI needs
  either a new backend field or the receipt the buyer already visited; neither exists as a flow yet
  (§9).
- Refetch orders after a receipt reaches a terminal `outcome`; there is no push channel.

**One thing that does not happen yet: nothing expires teams on its own.** The resolution logic is
production code and tested, but the scheduler that sweeps overdue teams is not in this change (it is
PR-G of `docs/agile/sprint-02/lifecycle-completion-plan-20260922.md`). Until it lands, a team past
its deadline vanishes from the product page by the `expire_time` filter, its orders stay `pending`,
and no refund obligation is recorded. Do not build UI whose correctness depends on expiry firing
within a given time.

## 9. What has no endpoint yet

Be explicit about these when planning screens — the backend closing the loop is not the same as the
product being demonstrable.

| Capability | State |
|---|---|
| My teams (`GET /group-deals/me`) | No endpoint. `GroupDealController` is an empty shell; issue #31 |
| Refund progress as its own view | Only the payment receipt exposes `refundStatus`; there is no list of refunds |
| Order → payment link | Deliberately absent from the order response (no `paymentId`, no PII) |
| Team lifecycle state on the catalog | `ActiveDealResponse` has no status; a resolved team is simply absent |
| Automatic team expiry | Not scheduled yet (§8) |
| Admin group terms (team size / lifetime) | No write surface, so on a fresh database every START is refused with `CATALOG_409_006` |

That last row is the one most likely to waste an afternoon: **a product with no group terms cannot
start a team at all**, and the failure surfaces at `POST /payments/intents`, not at the product page.

## 10. Error codes worth handling by name

The full list is each domain's `*ErrorCode` enum. These are the ones a browser can actually trigger:

| Code | HTTP | When | Suggested UI |
|---|---|---|---|
| `COMMON_401_001` | 401 | No session | Already handled by the interceptor; show the login link |
| `COMMON_403_001` | 403 | Missing/incorrect `X-BlocBuy-Request`, or no permission | A bug in the caller — do not show a retry |
| `COMMON_400_002` | 400 | Bean validation failed (e.g. `page=0`, `size=101`) | Fix the request; `errors` carries field detail |
| `COMMON_404_001` | 404 | Unknown resource, **or payments switched off** | Distinguish via `capabilities` |
| `COMMON_409_001` | 409 | Generic state conflict | Refetch and re-render |
| `ORDER_404_001` | 404 | Order missing **or not yours** | "Order not found" — never "forbidden" |
| `PAYMENT_404_001` | 404 | Receipt missing or not yours | Same |
| `PAYMENT_409_001` | 409 | Checkout attempt conflicts (stale quote, another open attempt, replayed key with different terms) | Re-read the receipt before offering a retry |
| `PAYMENT_502_001` | 502 | Provider unavailable | Transient — retry with the **same** idempotency key |
| `CATALOG_409_005` | 409 | Product not purchasable | Disable the buy path |
| `CATALOG_409_006` | 409 | Product has no group terms → cannot START | Hide START, JOIN may still work |
| `GROUPBUY_409_002` | 409 | Team closed (full, expired, resolved) | Refetch the product; the team is gone |
| `GROUPBUY_409_003` | 409 | Already a member | Point at the existing order |
| `GROUPBUY_409_004` | 409 | Team full | Refetch; offer starting a new one |

## 11. Local development

- `pnpm dev` serves the SPA on :5173 and proxies `/api` to `http://localhost:8589`; start the backend
  with `docker compose up` at the repo root. No `VITE_*` variables, no absolute API URLs — CI greps
  for `import.meta.env` and fails.
- Payments need the `stripe-sprint2` profile plus Stripe sandbox keys; without them
  `capabilities.enabled` is false and the payment endpoints answer 404. That is the expected
  default, not a broken environment.
- The generated client is **not** in git (`.gitignore`) and is rebuilt by the `prepare` hook from
  `contracts/openapi.json`, so a backend contract change reaches the frontend automatically. There
  is no "forgot to regenerate" drift risk, but `contracts/**` is in `ci-frontend.yml`'s `paths`, so
  a backend contract PR does run the whole frontend gate.
- When the contract changes, run `pnpm check` before pushing: `gen:api → typecheck → lint → fmt →
  test → knip`. Newly generated SDK functions that nothing imports are not flagged as dead code
  (`knip.json` excludes `src/api/generated/**`).

## 12. Contract evolution

Backend contract changes are additive by rule and enforced by CI: `contract-check` regenerates the
spec from the code and fails on drift, then `openapi-diff` rejects backward-incompatible changes
against the base branch. A breaking change needs explicit approval and an API version bump. In
practice this means the frontend will see new optional fields and new paths, never a removed or
retyped one, so pinning to a regenerated client is safe.
