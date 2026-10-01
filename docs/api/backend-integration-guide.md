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
- A response field the backend can serialise as `null` is marked `@Schema(nullable = true)`, so the contract says `type: [T, "null"]` (object fields: `anyOf: [$ref, null]` via `NullableRefCustomizer`) and the generated type reads `T | null`; catalog `groupPrice`, `subtitle`, `description`, `coverImage`, `category`, `deal` and `MeResponse.nickname`/`avatarUrl` are nullable (#267).
- Do not accumulate money with floating-point arithmetic. Currency is currently lowercase `"sgd"`.
- Instants are ISO-8601 UTC strings; parse them at the display boundary.

## Checkout flow

```text
1. GET  /payments/capabilities
2. POST /payments/intents
3. POST /payments/checkout-sessions
4. Navigate to the returned Stripe Checkout URL
5. Stripe redirects to the server-owned return URL with paymentId
6. GET  /payments/{paymentId} while payment is CREATED or a refund is pending (at most 60 s)
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

`GET /group-deals/me` lists the signed-in buyer's blocs with `page` and `size` (maximum 100); it carries committed team status but no payment or refund state. The `/account` "Refunded" filter groups closed blocs, not confirmed refunds; read the owner-scoped receipt for actual refund status.

Reaching the team target changes pending orders to `confirmed`; resolving an expired team changes them to `cancelled` and records refund obligations. Refetch orders after a receipt reaches a terminal outcome because no push channel exists.

Automatic expiry and refund execution are deployment-opt-in under `BLOCBUY_SCHEDULING_ENABLED` (#29, [release boundary](../../backend/docs/payment/stripe-release-boundary.md)). UI correctness must tolerate delayed expiry while scheduling is off, an item is in retry backoff, or a deployment is unavailable.

## Remaining capability limits

| Capability | Limit |
| --- | --- |
| Refund history | Only on individual payment receipts |
| Order-to-payment navigation | Orders omit `paymentId` |
| Catalog team history | Only the active team; buyer history is under `/group-deals/me` |
| Admin group terms | No write endpoint for size or lifetime |

A product without group terms cannot start a team: `POST /payments/intents` returns `CATALOG_409_006`. Joining an existing configured team may still work.

## Errors requiring specific handling

### Catalog field errors (2026-10-01, #251)

Field-specific `CATALOG_400_001` (invalid team-price policy) and `CATALOG_400_002` (invalid listing) failures carry `errors` as an array of `{field, message}`, matching request-body Bean Validation's `COMMON_400_002`. Use the request JSON path to attach the safe message to an input; do not infer a field from the error code alone. Non-field failures may omit `errors`.

```json
{
  "code": "CATALOG_400_001",
  "message": "Invalid team price policy",
  "errors": [{ "field": "amount", "message": "Must be strictly below the solo price" }]
}
```

- Standalone `PUT /products/{productId}/team-price-policy`: `amount`, `currency`, `validFrom` or `validUntil`; an end time not after the start names `validUntil`, as does an already-expired end time.
- Admin listing content: `listing.title`, `listing.subtitle`, `listing.description`, `listing.coverImage`, `listing.images`, `listing.category`, `listing.originalPrice`, `listing.currency` or `listing.stock`. Invalid product numbers name the top-level `productNo`.
- Admin team-terms writes: policy fields are prefixed with `teamPrice.`, for example `teamPrice.amount` or `teamPrice.validUntil`.

The business exception carries an explicit immutable field-error list separately from its existing map details; the global handler serializes that list directly. This preserves map-shaped version-precondition details such as `errors: {"currentVersion": 2}` and keeps the domain independent of web DTOs. Existing domain rejection rules, HTTP statuses and error codes are retained.

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
