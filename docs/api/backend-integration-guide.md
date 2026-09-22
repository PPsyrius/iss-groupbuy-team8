# Backend integration guide for the frontend

[`contracts/openapi.json`](../../contracts/openapi.json) is the authoritative HTTP contract. Run `pnpm gen:api` after it changes and use the generated client.

This guide records frontend-visible behavior that the current contract does not describe, including status values, required headers, call ordering, and unavailable capabilities. If it conflicts with the contract, treat this guide as stale and correct it in the same PR.

## Transport and errors

- The API base path is `/api/v1`; requests remain relative to the page origin.
- JSON endpoints return a `Result` envelope:

  ```json
  {
    "code": "COMMON_200_000",
    "data": {},
    "message": "Success"
  }
  ```

- `errors` and `traceId` appear only when applicable. Include `traceId` in error reports.
- Successful void operations omit `data`.
- Failures use their real HTTP status, not a 200 response containing an error.
- The frontend client converts non-2xx responses into `ApiError`. Branch on `error.code`, never `error.message`; messages may be localized, while published codes remain stable.
- Google OAuth endpoints are protocol redirects and do not use the JSON envelope.

## Authentication

- Authentication uses the `HttpOnly` `blocbuy-token` cookie. The frontend must not read it.
- Login is a full-page navigation to `/api/v1/auth/oauth2/google`, not an XHR request.
- The backend owns the OAuth callback and final frontend redirect.
- `GET /auth/me` returns `401 COMMON_401_001` when anonymous; it does not return an empty success.
- `POST /auth/logout` returns a successful response without `data`.
- A 403 may mean either insufficient permission or a missing payment-request header.

## Payment headers

Paths in this section are relative to `/api/v1`.

| Header | Required by | Value |
|---|---|---|
| `X-BlocBuy-Request` | `POST /payments/intents`, `POST /payments/checkout-sessions` | `checkout` |
| `Idempotency-Key` | `POST /payments/checkout-sessions` | `[A-Za-z0-9._-]{1,128}` |

Omitting or changing `X-BlocBuy-Request` produces `403 COMMON_403_001`.

An idempotency key identifies one user-initiated checkout attempt. Reuse it when retrying after a network or provider error. Generate a new key only when the user deliberately starts a new attempt. Starting another attempt while one remains open may produce `PAYMENT_409_001`.

## Published values

OpenAPI supports enums, but the current contract exposes these fields as strings. Compare against the exact values below.

### Order status

| Value | Meaning |
|---|---|
| `pending` | Payment succeeded, but the team remains open |
| `confirmed` | The team reached its target |
| `cancelled` | The team expired and a refund is owed |

### Payment status

| Value | Meaning |
|---|---|
| `CREATED` | Checkout exists without a provider outcome |
| `SUCCEEDED` | Provider-verified payment |
| `FAILED` | Verified terminal unpaid failure |
| `CANCELED` | Checkout expired without payment |

### Receipt outcome

| Value | Meaning |
|---|---|
| `PENDING` | Business settlement has not completed |
| `FULFILLED` | Membership and order were created |
| `REFUND_REQUIRED` | Payment succeeded, but fulfilment was rejected and a refund is owed |

`refundStatus` is one of `NONE`, `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REVIEW`, or
`CANCELED`. `refundPending` is true only for `PENDING` and `PROCESSING`.

Order `cancelled` and payment/refund `CANCELED` are separate published values; the spelling difference is intentional.

## Money and time

- `Receipt.amount` is a decimal string such as `"12.00"`.
- Order and catalog amounts are JSON numbers.
- Do not accumulate monetary values using floating-point arithmetic.
- Currency is currently the lowercase code `"sgd"`.
- Instants are ISO-8601 UTC strings. Parse them at the display boundary rather than assuming local time.

## Checkout flow

```text
1. GET  /payments/capabilities
2. POST /payments/intents
3. POST /payments/checkout-sessions
4. Navigate to the returned Stripe Checkout URL
5. Stripe redirects to the server-owned return URL with paymentId
6. GET  /payments/{paymentId} until outcome is no longer PENDING
```

Additional rules:

- Gate payment UI on `capabilities.enabled`.
- For `START`, `businessId` is the product ID.
- For `JOIN`, `businessId` is the deal ID.
- The browser never sends a price; the backend determines and freezes it.
- `checkoutUrl` may be null on a successful response when the attempt is no longer usable. Do not navigate to it; read the payment receipt instead.
- Do not construct the Stripe return URL. It is configured by the backend.
- Returning from Stripe does not prove business fulfilment. The webhook may still be settling the membership and order.
- Polling must be bounded. The current receipt page polls every two seconds for at most 60 seconds and stops after an error.
- `REFUND_REQUIRED` is a terminal business outcome, not a failed request to retry. Show the refund state from the receipt.

## Catalog and orders

`ProductDetailResponse.deal` contains only an open, unexpired team. A null value means there is nothing currently joinable; it does not prove that a previous team was marked expired.

`GET /orders/{orderId}` is owner-scoped. An order belonging to another buyer returns `404 ORDER_404_001`, the same as an unknown order. Do not present this as a permission error.

Order responses do not expose `paymentId` or recipient PII.

When a team reaches its target, its pending orders become `confirmed`. When a team is resolved as expired, its pending orders become `cancelled` and refund obligations are recorded. Refetch orders after a receipt reaches a terminal outcome because there is no push channel.

Automatic expiry is not scheduled yet. An overdue team can disappear from the catalog while its orders remain `pending` and no refund has been recorded. Do not make UI correctness depend on expiry running within a fixed period; this remains tracked by issue #29.

## Unavailable capabilities

| Capability | Current state |
|---|---|
| My teams | No `GET /group-deals/me` endpoint |
| Refund history | Refund status is available only through an individual payment receipt |
| Order-to-payment navigation | Order responses do not expose `paymentId` |
| Resolved team history | Catalog responses expose only the currently active team |
| Automatic team expiry | Resolution logic exists, but no scheduler invokes it |
| Admin group terms | No write endpoint for team size or lifetime |

A product without group terms cannot start a team. `POST /payments/intents` returns `CATALOG_409_006`; joining an existing configured team may still work.

## Errors requiring specific handling

| Code | Meaning | Frontend behavior |
|---|---|---|
| `COMMON_401_001` | No authenticated session | Treat the user as anonymous |
| `COMMON_400_002` | Request validation failed | Display safe field details from `errors` |
| `COMMON_404_001` | Unknown resource or disabled payment route | Use payment capabilities to distinguish the disabled state |
| `ORDER_404_001` | Order missing or owned by another user | Show "Order not found" |
| `PAYMENT_404_001` | Receipt missing or owned by another user | Show "Payment not found" |
| `PAYMENT_409_001` | Checkout attempt conflict | Re-read the receipt before offering another attempt |
| `PAYMENT_502_001` | Payment provider unavailable | Retry with the same idempotency key |
| `CATALOG_409_005` | Product cannot currently be purchased | Disable the purchase action and refetch |
| `CATALOG_409_006` | Product has no group terms | Disable START; JOIN may remain available |
| `GROUPBUY_409_002` | Team is closed | Refetch the product |
| `GROUPBUY_409_003` | User already belongs to the team | Direct the user to their existing order |
| `GROUPBUY_409_004` | Team is full | Refetch and offer another available action |
