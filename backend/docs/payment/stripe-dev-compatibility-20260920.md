# Stripe compatibility with dev 211cfb7

> Superseded runtime instructions: local endpoints and launch scripts were removed before publication.
> See [release cleanup](stripe-release-cleanup-20260920.md). Test fixtures no longer ship in the application.

The Checkout implementation no longer changes the original dev interfaces. This is a local change; nothing was pushed or merged.

## Connections

| Existing dev contract | Stripe implementation |
| --- | --- |
| `PaymentGateway` and `StripePaymentGateway` | Restored unchanged. `StripeCheckoutGateway` implements the separate `CheckoutGateway`; callers of the original PaymentIntent methods need no changes. |
| `PaymentRepository.findById(String)` and `Payment.of(String)` | Restored unchanged. `DevPaymentRepositoryAdapter` converts the ID and reads the Checkout record, returning the original ID-only model. Invalid or unknown IDs return empty; database errors still propagate. |
| `PaymentAppService.handleStripeWebhook(String, String)` and `StripeWebhookController` | Unchanged. With `stripe-sprint2` enabled, Spring selects `StripePaymentAppService`, which delegates to transactional Checkout processing. Without that profile, the original implementation remains selected. |
| `PaymentController`, `OrderController`, `GlobalExceptionHandler` | Restored unchanged. New routes live in `StripeCheckoutController` and `StripeOrderHistoryController`. Missing-header handling is scoped to Stripe; local fixture handling requires the local profile. |
| `PaidGroupDealService.startConfirmed` / `joinConfirmed` | Unchanged. The local integration adapter still calls these actual teammate methods with their existing DTOs. |

The Checkout aggregate and persistence port are named `CheckoutPayment` and `CheckoutPaymentRepository`. Checkout states and UUID fields do not change the public shape of the original `Payment` model. Original HTTP paths and schemas, Cookie authentication and response envelopes are preserved; the frontend still uses the generated client.

## Verification

- `python3 scripts/check-stripe-dev-contracts.py`: compares 192 original Java/configuration/DDL files, 13 original HTTP paths and 23 schemas with the imported dev snapshot. New files/endpoints are allowed. Refresh this baseline only after comparing an intentional dev update; it does not prove compatibility with unseen newer commits.
- Backend `./mvnw -B -ntp verify -Popenapi-export -DskipITs`: passed, 620 tests reported, 39 skipped without the isolated database environment; no failures or errors.
- `bash scripts/test-stripe-sprint2.sh`: 102 passed, none skipped, using a separate PostgreSQL 18 instance. Covers real persistence, teammate group participation, rollback, concurrency, refunds, HTTP security and profile/contract compatibility. The Stripe network is mocked, not a new Sandbox transaction.
- Frontend `pnpm check`: 114 tests passed; `pnpm build` passed. Runtime-configuration tests: 2 passed.
- The aggregate JaCoCo report shows no missed lines or branches in the separated Checkout gateway, aggregate, repository implementation or String-ID repository adapter. No remote CI/Sonar result is claimed.

## Still required before shared deployment

`PaymentBusinessPort` and `PaymentIntentPort` are our explicit integration contracts. The only complete bindings in this snapshot still belong to the local test adapter: purchase intent/quote persistence, order creation and expiry coordination. They are not the team's finished production APIs. Enabling `stripe-sprint2` without those bindings fails rather than acknowledging an unprocessed payment.

Replace those local bindings when the corresponding teammate implementations are ready. Do not enable `stripe-local-adapter`, fixture login or local refund workers in shared deployments. The original server configuration, database documentation, migrations and GitHub repository were not changed. New Stripe migrations remain opt-in.

## Default-off rollout addendum — 20 September

The existing `stripe-sprint2` Spring profile is now the common rollout switch. Without it, `StripeFeatureSwitch` rejects the new payment-intent, Checkout, receipt and order-history HTTP routes with the existing `COMMON_404_001` envelope before calling application services or starting their database transactions. Authentication and input validation still apply. The original webhook interface/implementation is unchanged; Checkout processing remains profile-controlled.

The additive authenticated `GET /api/v1/payments/capabilities` returns `data.enabled` with `Cache-Control: no-store`, without accessing the database. The generated frontend client reads this endpoint. A signed-in buyer's Start/Join buttons, order query and receipt polling require an explicit boolean `true`. Missing endpoints on older backends, network failures and malformed responses leave them off. The existing anonymous sign-in prompt remains available, but cannot start payment. Disabled history does not claim there are no orders; disabled receipts warn against paying again to check the result.

Local testing: `scripts/stripe-local-runtime.py` already opts into `local,stripe-sprint2,stripe-local-adapter` and the isolated migrations. Rebuild and restart that private runtime to use this change; it does not require editing a committed application YAML. Do not copy these local adapters/profile settings into shared deployment. Omitting `stripe-sprint2` keeps the new browser features off. This is a startup/deployment switch, not a live administration toggle or a database readiness probe.

Verification after this change:

- `./mvnw -B -ntp verify -Popenapi-export -DskipITs`: passed; 625 reported, 39 skipped without the isolated DB environment, zero failures/errors.
- `bash scripts/test-stripe-sprint2.sh`: 107 passed, none skipped, with isolated PostgreSQL and mocked Stripe network. Includes disabled HTTP tests against an unavailable database and the enabled local payment/refund flow.
- `pnpm check`: 126 tests passed, including 12 disabled/missing/offline/malformed capability scenarios; types, lint, formatting and unused-code checks passed. `pnpm build` passed.
- `python3 scripts/check-stripe-dev-contracts.py`: all 192 protected original files, 13 original HTTP paths and 23 schemas unchanged. The new capability endpoint is additive.

No shared-environment verification, remote CI, upload or merge was performed. Formal intent/order/expiry bindings remain necessary before enabling Stripe in a shared environment; they are not required to retain the feature in its disabled state.

Do not use this rollout switch to cancel an in-flight payment. Before disabling a previously enabled deployment, drain or reconcile outstanding Checkout sessions and refund work; disabling the profile does not cancel a Stripe Session.
