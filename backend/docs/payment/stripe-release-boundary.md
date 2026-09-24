# Stripe release boundary

Owner: Yorick. Updated: 23 September 2026. PRs #126 and #148.

This is the current deployment boundary for the Stripe work. Historical Sandbox evidence is in [stripe-sandbox-acceptance-20260920.md](stripe-sandbox-acceptance-20260920.md), and quality evidence is in [stripe-sonar-fixes-20260920.md](stripe-sonar-fixes-20260920.md).

## Current boundary

- `stripe-sprint2` is the rollout switch. Without it, `StripeFeatureSwitch` rejects payment-intent, Checkout and receipt routes before business or database access. The authenticated capabilities endpoint returns `enabled=false` with `Cache-Control: no-store`; missing, invalid or unavailable capability responses also keep frontend payment actions off. When enabled, a pending receipt is polled every two seconds for at most 60 seconds and automatic polling stops immediately after a read error.
- Browser requests contain operation and business identifiers, not buyer or amount. Sa-Token owns identity, server-side ports own pricing, and Checkout retries reuse the intent and idempotency key.
- `CheckoutPayment`, its repository and `CheckoutGateway` are separate from the original payment contracts. The original webhook contract remains, with `StripePaymentAppService` selected only under the Stripe profile.
- `PaymentIntegrationConfiguration` provides the production `PaymentBusinessPort` and `PaymentIntentPort` bindings with no profile, and the test-only `PaymentBusinessFixture` is retired (backend ADR 0001). `DevPaymentRepositoryAdapter` still awaits its production replacement.
- `RefundService` and `StripeRefundGateway` retain provider verification, idempotency, leasing and uncertain-outcome safeguards. The profile-independent `TeamExpiryRefundService` and unconditional `TeamExpiryRefundSubscriber` create durable refund obligations in the lifecycle transaction. The team-expiry scheduler is wired behind `BLOCBUY_SCHEDULING_ENABLED`; no refund worker or recovery endpoint is wired.

## Removed before publication

The local login/operator routes, `/local/stripe/*`, local schedulers, runtime and operator scripts, Vite override, and deployable intent/order/expiry substitutes were removed. Test substitutes and SQL remain under bootstrap test sources and are explicitly imported by tests; they are not packaged in the application JAR or included in deployment Flyway locations.

The payment-branch `GET /api/v1/orders` and its `/account` consumer were also removed in favour of the order team's `GET /api/v1/orders/me`. Rebuild account history only after that endpoint reaches the committed OpenAPI contract.

## Migration and compatibility status

PR review moved the payment schema into the standard Flyway path as V9-V11. Duplicate V2 indexes and the review-case backfill were removed, lifecycle status values follow the existing lowercase convention, and `intent_id` remains distinct from the provider PaymentIntent ID.

The historical compatibility guard compared 192 original files, 13 HTTP paths and 23 schemas with the supplied `211cfb7` snapshot. The branch later merged `origin/dev` at `92e5ca9`; the guard is historical evidence, not a substitute for current CI and contract review.

## Pre-live checklist

1. ~~Replace `PaymentBusinessPort` and `PaymentIntentPort` with team production implementations~~ — done 2026-09-22 (PR #147, backend ADR 0001). Remaining: replace `DevPaymentRepositoryAdapter` and verify shared lock and transaction ordering.
2. Enable and monitor the expiry scheduler after its backlog check; wire the refund worker and define production recovery ownership without restoring private local HTTP controls. The in-transaction expiry notification path and durable obligation creation are already wired.
3. Keep test fixtures and `db/stripe-fixture` outside every deployment Flyway location.
4. Rebuild account history against `/api/v1/orders/me` after its contract is available.
5. Remove the gateways' `sk_test_` restriction only with live-key rotation, a live webhook endpoint, reconciliation controls and renewed payment/refund acceptance.
6. Drain or reconcile in-flight Checkout and refund work before disabling an enabled deployment.

Local and Sandbox evidence does not establish Google OAuth, shared AWS deployment, public TLS or live Stripe readiness.
