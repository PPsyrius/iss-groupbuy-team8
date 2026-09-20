# Refund execution and order history — local acceptance

> Historical Sandbox evidence. The local endpoints and commands below have been retired;
> see [release cleanup](stripe-release-cleanup-20260920.md) for the current boundary.

Date: 20 September 2026. Personal branch: `feature/yorick-stripe-sprint2`.

The user requested the remaining refund and order-list work after local checkout acceptance. Refund execution extends the earlier Sprint 2 obligation-only scope. Nothing was pushed to GitHub or deployed to shared AWS.

## Verified result

The existing expired Sandbox purchase was refunded through the application worker:

| Item | Result |
| --- | --- |
| Payment | `01a0bce8-1615-7f87-a736-788fbe34a1f3` |
| Stripe refund | `re_3UHbeV5iEVLN2uQJ1V71krwQ` |
| Amount | SGD 10.00, refunded to the original test payment method |
| Provider / local refund state | `succeeded` / `SUCCEEDED` |
| Original payment state | Remains `SUCCEEDED`; refund is a separate fact |
| Duplicate check | One provider refund after the worker processed and rechecked the obligation |
| Order display | `refunded`, bloc `expired`; receipt and order IDs match PostgreSQL |

Evidence: [machine-checked results](evidence/20260920/refund-acceptance.json), [order list](evidence/20260920/frontend-refunded-order.png), [refund receipt](evidence/20260920/frontend-refund-receipt.png). The earlier browser-created START/JOIN purchases still pass their acceptance check.

## Implementation

- `RefundService` claims one due obligation per invocation, calls Stripe outside the database transaction, then records the verified result in another short transaction. The local scheduler checks every five seconds when explicitly enabled.
- `biz_refund` retains one obligation per payment. The provider idempotency key is `blocbuy-refund-<obligation UUID>`. A two-minute lease and version check prevent stale workers overwriting newer results. `log_refund_attempt` records claims and outcomes without card data.
- The worker matches the original PaymentIntent, full amount, currency and obligation metadata. Only test keys are accepted. It never accepts a destination or amount from the browser.
- `PENDING` means an obligation exists; `PROCESSING` means the provider outcome is pending or uncertain; `SUCCEEDED` requires provider confirmation. `FAILED` and `REVIEW` stop automatic creation and emit an operator warning. Neither is shown as a successful refund.
- Unknown creation responses retry with the same key for less than 23 hours. After that, the worker only searches existing refunds by metadata. It does not create another refund after Stripe's idempotency-retention window. Known refund IDs are retrieved directly. Successful refunds are checked hourly for 31 days to detect later provider failure.
- `GET /api/v1/orders?page=1&size=20` uses the signed-in buyer, a stable sort, and a 50-row page-size cap. The order-owned read model joins payment/refund/bloc facts in one bounded query; no per-order queries. Refund status is projected into the order response, preserving the original paid order record for audit.
- `/account` now lists real orders with pagination and receipt links. Receipts distinguish a pending refund, a sent refund and a refund needing review. Query caches are separated by the signed-in buyer.

The final team intent/order/expiry implementations are still replaced by the explicitly requested minimal local adapters. This is not evidence that those modules or the new migration have been merged on `dev`.

## Checks

| Command | Result |
| --- | --- |
| `cd backend && ./mvnw -B -ntp verify -Popenapi-export -DskipITs` | Passed; includes formatting, static checks and OpenAPI export |
| `bash scripts/test-stripe-sprint2.sh` | 79 tests passed, including isolated PostgreSQL flow, recovery, ownership and refunds; Stripe network mocked |
| `cd frontend && pnpm check` | 114 tests passed; generation, typecheck, lint, format and unused-code checks passed |
| `cd frontend && pnpm build` | Passed |
| `python3 scripts/test-stripe-runtime.py` | 2 offline configuration-boundary tests passed |
| `python3 scripts/verify-stripe-refund.py --payment 01a0bce8-1615-7f87-a736-788fbe34a1f3 --buyer 3 --recheck-due` | Real Sandbox refund, PostgreSQL, receipt and order API passed |

Anonymous order/receipt reads return 401. Another buyer gets 404 for the receipt and cannot see the order in their list. Oversized pages return 400. Mock and native-database tests are not presented as real Google or shared-deployment acceptance.

## Recovery ownership

Yorick owns review of this personal local runtime. During development, check `python3 scripts/stripe-refund-ops.py` at the start and end of a test session and after any worker warning. It lists `REVIEW`, `FAILED`, and processing jobs older than ten minutes, up to 100 per run. This is a manual local runbook, not a configured production alert service.

For a case with an existing verified provider refund, run:

```sh
python3 scripts/stripe-refund-ops.py --reconcile PAYMENT_UUID --provider-refund re_EXISTING_REFUND
```

The private loopback endpoint checks the operator token and retrieves that refund from Stripe. It validates payment/amount/currency/metadata before saving anything; it cannot create a second refund. Resolution requires matching provider and local final states, an audit entry and the correct buyer-facing status. An actually failed refund with no replacement remains open for team review; do not invent a new key or mark it resolved manually.

Stripe references: [refund creation](https://docs.stripe.com/api/refunds/create), [pending and failed refunds](https://docs.stripe.com/refunds), [idempotency retention](https://docs.stripe.com/api/idempotent_requests).

## Remaining environment work

Real Google login is not verified. The isolated runtime intentionally does not inherit shared OAuth secrets. The shared authorize endpoint redirects to Google with the CloudFront callback, not a local callback. A private `--google-env` option is ready; use [the placeholder file](google-local.env.example) and have the OAuth owner register `http://127.0.0.1:5173/api/v1/auth/oauth2/google/callback` without removing the existing shared callback. Use a current development secret, not the credential previously pasted into chat.

Shared deployment is not verified. Read-only checks returned 200 for `/api/actuator/health`, 403 for `/`, and 401 for anonymous `/api/v1/orders`. Browser navigation was blocked by the client. These checks do not prove that our local payment/refund code is online. Publishing it requires the team's review and deployment authorization.

Original `application*.yml`, V1–V8 migrations and `frontend/vite.config.ts` still match the supplied ZIP. New migrations remain in opt-in `db/stripe` and `db/stripe-local` locations; the local order-history index is not a shared-database migration. PostgreSQL 18 is the source of truth; Redis stores sessions. Local HTTP is loopback-only; Stripe calls use HTTPS. Shared production enablement still requires TLS and the agreed deployment controls.
