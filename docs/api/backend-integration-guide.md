# Backend integration guide for the frontend

[`contracts/openapi.json`](../../contracts/openapi.json) is authoritative. Run `pnpm gen:api` after it changes and use the generated client. This guide adds behavior OpenAPI does not express; correct it in the same PR if it conflicts with the contract.

## Transport and authentication

- The API base path is `/api/v1`; requests remain relative to the page origin.
- JSON endpoints return `Result<T>` with `code`, optional `data`, and `message`; `errors` and `traceId` appear when applicable. Successful void operations omit `data`.
- Failures use their real HTTP status. The client converts non-2xx responses to `ApiError`; branch on stable `error.code`, not localizable `error.message`, and include `traceId` in reports.
- Google OAuth endpoints redirect without the JSON envelope. Login uses full-page navigation to `/api/v1/auth/oauth2/google`; the backend owns the callback and final frontend redirect.
- Authentication uses the unreadable `HttpOnly` `blocbuy-token` cookie. `GET /auth/me` returns `401 COMMON_401_001` when anonymous; `POST /auth/logout` succeeds without `data`.
- A 403 can mean insufficient permission or a missing payment-request header.

## Payment requests

Paths below are relative to `/api/v1`.

| Header | Required by | Value |
| --- | --- | --- |
| `X-BlocBuy-Request` | `POST /payments/intents`, `POST /payments/checkout-sessions` | `checkout` |
| `Idempotency-Key` | `POST /payments/checkout-sessions` | `[A-Za-z0-9._-]{1,128}` |

Missing or changing `X-BlocBuy-Request` returns `403 COMMON_403_001`. Reuse an idempotency key when retrying the same user-initiated checkout after a network or provider error; create a new key only for a deliberate new attempt. A second attempt while one remains open may return `PAYMENT_409_001`.

## Published values

The contract currently exposes these values as strings.

| Field           | Value             | Meaning                                       |
| --------------- | ----------------- | --------------------------------------------- |
| Order status    | `pending`         | Payment succeeded; team remains open          |
| Order status    | `confirmed`       | Team reached its target                       |
| Order status    | `cancelled`       | Team expired; refund is owed                  |
| Payment status  | `CREATED`         | Checkout has no provider outcome              |
| Payment status  | `SUCCEEDED`       | Provider-verified payment                     |
| Payment status  | `FAILED`          | Verified terminal unpaid failure              |
| Payment status  | `CANCELED`        | Checkout expired without payment              |
| Receipt outcome | `PENDING`         | Business settlement is incomplete             |
| Receipt outcome | `FULFILLED`       | Membership and order were created             |
| Receipt outcome | `REFUND_REQUIRED` | Payment succeeded but fulfilment was rejected |

`refundStatus` is `NONE`, `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REVIEW`, `CANCELED`; `refundPending` is true only for `PENDING` and `PROCESSING`. Order `cancelled` and payment/refund `CANCELED` are intentionally different published values.

## Money and time

- `Receipt.amount` and order amounts are decimal strings such as `"12.00"`; catalog amounts are JSON numbers.
- Optional order fields `productImage` and `cancelTime` are nullable in the OpenAPI contract.
- Do not accumulate money with floating-point arithmetic. Currency is currently lowercase `"sgd"`.
- Instants are ISO-8601 UTC strings; parse them at the display boundary.

## Checkout flow

```text
1. GET  /payments/capabilities
2. POST /payments/intents
3. POST /payments/checkout-sessions
4. Navigate to the returned Stripe Checkout URL
5. Stripe redirects to the server-owned return URL with paymentId
6. GET  /payments/{paymentId} until outcome is no longer PENDING
```

- Gate payment UI on `capabilities.enabled`.
- `businessId` is the product ID for `START` and the deal ID for `JOIN`.
- The browser never sends a price; the backend determines and freezes it.
- A successful response can contain a null `checkoutUrl` when the attempt is no longer usable. Read the receipt instead of navigating.
- Never construct the Stripe return URL; the backend configures it.
- A Stripe return does not prove fulfilment because the webhook may still be settling the membership and order.
- Poll every two seconds for at most 60 seconds, stopping on an error.
- `REFUND_REQUIRED` is terminal; show receipt refund state rather than retrying the request.

## Catalog and orders

`ProductDetailResponse.deal` contains only an open, unexpired team. Null means no team is currently joinable, not necessarily that a previous team was marked expired.

`GET /orders/{orderId}` is owner-scoped. Foreign and unknown orders both return `404 ORDER_404_001`; do not present this as a permission error. Successful and error responses include `Cache-Control: no-store`, so clients must not retain the order detail. Order responses expose neither `paymentId` nor recipient PII.

Reaching the team target changes pending orders to `confirmed`; resolving an expired team changes them to `cancelled` and records refund obligations. Refetch orders after a receipt reaches a terminal outcome because no push channel exists.

Automatic expiry is not scheduled. An overdue team can disappear from the catalog while orders remain `pending` and no refund is recorded; UI correctness must not depend on prompt expiry. Issue #29 tracks this gap.

## Unavailable capabilities

| Capability                  | Current state                                        |
| --------------------------- | ---------------------------------------------------- |
| My teams                    | No `GET /group-deals/me` endpoint                    |
| Refund history              | Available only through an individual payment receipt |
| Order-to-payment navigation | Orders do not expose `paymentId`                     |
| Resolved team history       | Catalog exposes only the active team                 |
| Automatic team expiry       | Resolution exists, but no scheduler invokes it       |
| Admin group terms           | No write endpoint for team size or lifetime          |

A product without group terms cannot start a team: `POST /payments/intents` returns `CATALOG_409_006`. Joining an existing configured team may still work.

## Errors requiring specific handling

| Code | Meaning | Frontend behavior |
| --- | --- | --- |
| `COMMON_401_001` | No authenticated session | Treat user as anonymous |
| `COMMON_400_002` | Validation failed | Display safe field details from `errors` |
| `COMMON_404_001` | Unknown resource or disabled payment route | Use payment capabilities to distinguish disabled state |
| `ORDER_404_001` | Order missing or foreign | Show "Order not found" |
| `PAYMENT_404_001` | Receipt missing or foreign | Show "Payment not found" |
| `PAYMENT_409_001` | Checkout conflict | Re-read receipt before offering another attempt |
| `PAYMENT_502_001` | Provider unavailable | Retry with the same idempotency key |
| `CATALOG_409_005` | Product cannot be purchased | Disable purchase and refetch |
| `CATALOG_409_006` | Product lacks group terms | Disable START; JOIN may remain available |
| `GROUPBUY_409_002` | Team closed | Refetch product |
| `GROUPBUY_409_003` | User already joined | Direct user to the existing order |
| `GROUPBUY_409_004` | Team full | Refetch and offer another action |
