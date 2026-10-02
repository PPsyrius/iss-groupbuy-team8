# Stripe release boundary

Owner: Yorick. Updated: 30 September 2026. PRs #126 and #148; #216 legacy-path cleanup; issue #292 Checkout flag.

This is the current deployment boundary for the Stripe work. Historical Sandbox evidence is in [stripe-sandbox-acceptance-20260920.md](stripe-sandbox-acceptance-20260920.md), and quality evidence is in [stripe-sonar-fixes-20260920.md](stripe-sonar-fixes-20260920.md).

## Current boundary

- `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true` enables the buyer-facing intent, Checkout and receipt routes. It defaults to `false` in every profile. Without it, `StripeFeatureSwitch` rejects payment commands before business/database access; authenticated capabilities return `enabled=false` with `Cache-Control: no-store`. The frontend also keeps payment actions off when capabilities are missing or unavailable ([integration guide](../../../docs/api/backend-integration-guide.md)). `stripe-sprint2` is a one-release local compatibility alias, not the dev deployment switch (backend ADR 0005).
- Browser requests contain operation and business identifiers, not buyer or amount. Sa-Token owns identity, server-side ports own pricing, and Checkout retries reuse the intent and idempotency key.
- Checkout currently requests `card` only, in Stripe Sandbox. Future eligible payment methods can be evaluated as separate Checkout work, using the existing settlement boundary rather than reviving the retired PaymentIntent pipeline.
- The webhook controller keeps one `PaymentAppService` per flag state: Checkout settles signed session events durably when enabled; when disabled, signed unrelated events are acknowledged but supported Checkout events and `payment_intent.*`, `charge.*`, `refund.*` return non-2xx without database access. The old PaymentIntent gateway, String-ID repository, stub and dev adapter are retired (backend ADR 0004).
- `PaymentIntegrationConfiguration` always binds the production `PaymentBusinessPort` and `PaymentIntentPort` (backend ADR 0001). The latter issues an order-owned purchase intent, not a provider PaymentIntent.
- `TeamExpiryRefundSubscriber` records refund obligations in the lifecycle transaction. `RefundService` and `StripeRefundGateway` process them with provider checks, idempotency and leasing; both scheduled jobs require `BLOCBUY_SCHEDULING_ENABLED`, independently of the Checkout flag even under `ec2`. Scheduling is off by default and the refund gateway still requires a sandbox key (backend ADR 0002).

## Dev Sandbox enablement for #292

1. Configure startable dev products through `PUT /api/v1/admin/products/{productId}/team-terms` with a valid team price policy. The `R__demo_catalog_seed.sql` migration is local-only and is not deployed under `ec2`; existing `UC9-SMOKE-*` rows must be configured or left off-sale.
2. In the dev host's root-only `/etc/blocbuy/backend.env`, set `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true`, a `sk_test_` key, the signing secret for this endpoint, and `BLOCBUY_PAYMENT_CHECKOUT_SUCCESS_URL` / `BLOCBUY_PAYMENT_CHECKOUT_CANCEL_URL` to the dev frontend's `/payments/return` route. Do not commit or send the values in a PR. The backend refuses startup if any required value is blank or the API key is not a test key.
3. Subscribe the test-mode webhook at `https://<dev-host>/api/v1/webhooks/stripe` to `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`.
4. Use an approved deployment that force-recreates the backend container. A plain `docker restart` does not reload the env file. Check authenticated capabilities, quote, Checkout and a test card payment; confirm the new team appears under `GET /api/v1/group-deals/me` as leader. Record the operator, time, payment ID and outcome without card or secret data.
5. Keep production's flag `false`. Before disabling an enabled dev deployment, reconcile or drain open Checkouts because disabled money webhooks return non-2xx and automatic retries are finite.

## Removed before publication

The local login/operator routes, `/local/stripe/*`, local schedulers, runtime and operator scripts, Vite override, and deployable intent/order/expiry substitutes were removed. Test substitutes and SQL remain under bootstrap test sources and are explicitly imported by tests; they are not packaged in the application JAR or included in deployment Flyway locations.

The payment-branch `GET /api/v1/orders` was removed in favour of the order team's `GET /api/v1/orders/me`, now in the contract and used on `/account`.

## Migration and compatibility status

The payment schema uses the standard Flyway path (V9–V11); local `intent_id` is distinct from Stripe's PaymentIntent ID. See backend ADR 0001 for the order/payment boundary.

The `211cfb7` source/contract snapshot guard is historical evidence, not a current CI or contract gate ([quality record](stripe-sonar-fixes-20260920.md)).

## Pre-live checklist

1. ~~Supply production business and purchase-intent ports~~ — PR #147, backend ADR 0001. The legacy gateway/repository retirement is implemented locally in #216 (backend ADR 0004); merge it and verify Checkout lock/transaction ordering with PostgreSQL integration tests before enabling Checkout on dev (#223).
2. Refund worker and expiry notifications are wired (#29). Before enabling `BLOCBUY_SCHEDULING_ENABLED` on dev (#223), count overdue teams and due refunds, name a recovery owner and monitor both jobs; the switch can issue sandbox refunds without enabling Checkout. The checked-in EC2 deployment leaves scheduling off.
3. Keep test fixtures and `db/stripe-fixture` outside every deployment Flyway location.
4. ~~Rebuild account history against `/api/v1/orders/me`~~ — shipped in the contract and `/account` (#128).
5. Revisit the `StripeProperties.sandbox()` gate in `StripeClientProvider` only with live-key rotation, a live webhook endpoint, reconciliation controls and renewed payment/refund acceptance. Keep provider-reported live-mode checks and session-hint validation aligned with that rollout.
6. Drain or reconcile in-flight Checkout and refund work before disabling an enabled deployment. Feature-off rejects signed Checkout settlement, PaymentIntent, charge and refund events; Stripe retries are finite and cannot replace operator-owned reconciliation. Keep the endpoint subscribed to all four supported Checkout session event types.

Local and Sandbox evidence does not establish Google OAuth, shared AWS deployment, public TLS or live Stripe readiness.

## Checkout expiry isolation — 2026-10-02 (#130, #134)

The shared Stripe client now rejects non-test or missing keys at access time; Checkout and refund gateways cannot bypass it. Provider-reported live-mode facts are still rejected independently of the key. The explicit Checkout expiry hook uses a dedicated bounded `dueForExpiry` read (created, bound, quote-expired, not in open review), leaving the broader `pendingBefore` queue for operator scans. We chose a separate query rather than reprioritizing the shared scan: review-only or succeeded rows must not consume expiry slots, while failures need a durable operator case so the next run can reach younger payments. Each candidate's provider I/O remains outside transactions; settlement and failed-row review writes each run in their own short transaction. A failure is logged by payment ID and never aborts later candidates; if the review write also fails, the row may be retried next run and requires operator attention from logs.
