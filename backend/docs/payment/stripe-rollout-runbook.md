# Checkout and expiry-refund rollout (#223)

Owner: Yorick (@Yorick-Yu). Reviewer: Liu Jin (@l0sgAi), per Sprint 3 container #232. The team must name the authorized dev runtime operator before changing switches. This runbook prepares #223; it does not authorize a shared-dev or production change.

## Requirements and local development boundary

The current backend already freezes purchase intents, creates Checkout sessions, settles verified payments, expires teams, cancels their orders and records refund obligations. #223 connects these existing capabilities through runtime switches and records their acceptance evidence; #224 verifies refund replay and crash recovery. Keep the production payment, catalog, group-deal and order interfaces unchanged. Keep Flyway migrations and shared database/server addresses unchanged; local tests use an isolated PostgreSQL 18 cluster and test-only provider/session substitutes. The Checkout switch is `BLOCBUY_STRIPE_CHECKOUT_ENABLED`, introduced by #292; `stripe-sprint2` is a temporary local compatibility alias and is not the dev rollout switch (backend ADR 0005).

| Requirement | Local verification | Shared-dev acceptance still required |
|---|---|---|
| Count backlog before enabling scheduling | Read-only snapshot SQL, with due, leased and review work separated | Record dev counts, UTC time and deployed image before the switch |
| Enable Checkout independently of scheduling | Existing default-off and scheduling-wiring tests; authenticated capabilities and intent/Checkout HTTP tests | Confirm runtime profiles, scheduling flag, health and buyer smoke |
| Expire a paid, unfilled team | Production sweep, subscribers, transactions and PostgreSQL | Observe the automatic sweep within the configured interval |
| Refund each paid member once | Production worker with a local provider substitute; repeated jobs and provider retry | Real Stripe Sandbox refund id and succeeded status |
| Show buyer-visible results | Existing receipt, My blocs and My orders HTTP contracts | Browser screenshots against the deployed frontend |
| Recover and disable safely | Recovery ownership and procedure below | Operator executes and records the procedure on the actual target |

Local setup and repeatable verification:

```bash
# JDK 21 and PostgreSQL 18 are required; no Docker or shared credentials are used.
bash scripts/payment/test-rollout-local.sh
```

The runner owns its private loopback-only cluster, creates only dedicated test databases and stops the cluster on exit. Test-only Google/session and Stripe network substitutes are never packaged in the application. The tests use the team's real `/api/v1` controllers, business ports, repositories and Flyway migrations. They do not prove Google OAuth, AWS deployment, actual Stripe money movement or a browser session. No new public test/operator endpoint is introduced.

## Preflight before any dev switch

1. Obtain operator approval; verify account, Region, instance, immutable image digest and deployed commit using the [EC2 operator guide](../../../docs/deploy/backend-ec2-pipeline-operator-guide.md). #216, #221 and #222 are closed, but confirm the relevant commits are deployed and the #222 startable listings exist before the switch. #222's paid-bloc phase depends on #223, so closure alone cannot prove a paid START works on dev.
2. Run [rollout-backlog.sql](../../../scripts/payment/rollout-backlog.sql) against the authorized dev database using a read-only connection and approved private credential handling. It uses one read-only snapshot and a statement timeout; it outputs counts, database name and UTC timestamp only. Never paste an environment file or connection password. Keep the snapshot in the day log before enabling either switch.
3. Inspect unresolved payments through the existing `stripe-ops` `scan`, `cases` and `inspect` commands. The backlog query also counts created Checkout payments and those past their business deadline: zero refund rows can conceal a payment already paid in Stripe but not yet settled locally. Check scheduled leases/retries separately. Assign every open review case; a zero due count can conceal future retries, active leases or manual review work.
4. Verify the Stripe **test** key, endpoint signing secret and public webhook URL privately. Return/cancel URLs must be the deployed frontend's `/payments/return`; database, Redis and OAuth settings stay owned by the dev operator. Production remains off and live keys are excluded.

## Runtime settings: dev is not rendered from SSM

### Webhook version and signing-secret cutover

The webhook API version must be compatible with the deployed Java SDK's supported release train. `stripe-java 34.1.0` defaults to `2026-10-28.endive` for outbound requests; its safe deserializer also accepts the previously recorded endpoint version `2026-09-30.endive` because both share the Endive release train ([maintenance compatibility record](stripe-release-boundary.md#stripe-java-341-maintenance-update--2026-11-10)). Recheck the deployed dependency, `Stripe.API_VERSION` and the SDK's version-comparison behavior on future upgrades; do not assume every date difference requires an endpoint cutover or every older version is compatible. A valid signature does not prove a payload can be deserialized. In #661, a real dev event using `2026-08-26.dahlia` passed signature verification but returned HTTP 502 / `PAYMENT_502_001`. Keep the safe deserializer and signature check; do not bypass either to acknowledge incompatible money events.

Before cutover, verify the runtime test key belongs to the same Sandbox account as the endpoint, inventory pending payments, and preserve a root-owned `0600` backup of the host env file. Register a replacement endpoint for the same dev `/api/v1/webhooks/stripe` URL, Your account (not Connect), the SDK-compatible version, and exactly `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`. Disable the replacement while installing its signing secret privately; never put it in logs, issue comments or Git. See [Stripe webhook versioning](https://docs.stripe.com/webhooks/versioning).

Force-recreate only the backend through the reviewed deployment script on the same image digest. If health fails, restore the saved env and recreate the backend with the previous configuration; an image-only rollback cannot undo a bad env change. Enable the new endpoint after health passes. Verify a real paid Sandbox Checkout delivery is 2xx, the local payment is fulfilled and the receipt shows its order/bloc. Then disable, but retain, the old dev endpoint. The application accepts one signing secret, so leaving both enabled produces signature failures from the old endpoint. Leave prod endpoints unchanged. A synthetic "Send test event" can refer to a nonexistent Checkout session and is not end-to-end acceptance.

An existing event retains the API version with which it was created. Resending an old incompatible event does not upgrade its payload. For stranded paid sessions, inspect each payment and use the existing confirmed recovery command; the normal fulfilment path compensates a payment whose business deadline passed. Do not force it into an expired bloc or mark it paid with SQL.

### Host environment

At the checked-in baseline, `deployment/backend/compose.yml` sets `SPRING_PROFILES_ACTIVE=ec2` and reads `/etc/blocbuy/backend.env`; the dev workflow does not render SSM parameters into that file. Editing SSM alone therefore does not satisfy #223's written SSM step. Production's SSM renderer is a different path. Resolve this issue/runbook mismatch with the dev operator and issue owner rather than changing the shared compose file or assuming a switch took effect.

For an approved dev-only enablement, set `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true` and `BLOCBUY_SCHEDULING_ENABLED=true` in the dev host environment, keeping `SPRING_PROFILES_ACTIVE=ec2` and other settings unchanged. Confirm the deployed image includes #292 and the application reports Checkout enabled before attempting money movement. Ensure checkout success/cancel URLs are present. Keep the env file root-owned and mode `0600`; do not display it. Force-recreate through the reviewed deployment path with the same approved immutable image, then check only the two non-secret settings, health and authenticated capabilities (`enabled=true`, `Cache-Control: no-store`). A container restart alone retains its old environment.

| Host environment key | Approved dev-only value |
|---|---|
| `BLOCBUY_STRIPE_CHECKOUT_ENABLED` | `true` only for the approved dev Sandbox rollout |
| `BLOCBUY_SCHEDULING_ENABLED` | `true` after the backlog check |
| `BLOCBUY_PAYMENT_CHECKOUT_SUCCESS_URL` | Approved frontend origin plus `/payments/return` |
| `BLOCBUY_PAYMENT_CHECKOUT_CANCEL_URL` | Approved frontend origin plus `/payments/return` |

`StripeEc2RolloutWiringTest` verifies the explicit Checkout flag and scheduler wiring alongside `ec2` locally. No environment file or shared configuration is changed by that test.

Use the buyer's normal Google session for dev HTTP smoke: `POST /api/v1/payments/intents` with `X-BlocBuy-Request: checkout`, then `POST /api/v1/payments/checkout-sessions` with the same marker and an `Idempotency-Key`. Keep client amounts and buyer ids out of the request. Use the owned seeded product/quote; do not modify arbitrary team deadlines on shared dev. The approved seed must expire naturally so the timer, not a manual invocation, supplies acceptance evidence.

## Buyer evidence and frontend contract

Follow one Sandbox Checkout payment through verified webhook settlement, an open unfilled team, expiry, cancelled orders, one refund obligation per paid member and a provider-verified succeeded refund. Repeat the observation after another worker/sweep interval and confirm no extra obligation or provider refund. Record payment, team, order, Checkout and refund ids plus timestamp and deployed image; no cookies, credentials or card data.

The receipt `GET /api/v1/payments/{id}` is the source of `refundStatus` (`PENDING`, `PROCESSING`, `SUCCEEDED`, `REVIEW` or `FAILED`). `GET /api/v1/group-deals/me` exposes the team lifecycle but **no refund state**; the existing My blocs frontend labels expired rows as closed/refund due. `GET /api/v1/orders/me` exposes cancelled orders, not refund success. Do not label either response as evidence that money was refunded. Attach both the receipt and My blocs screenshots. If #223 requires My blocs to display confirmed refund success itself, agree that separate additive frontend/API work with its owner; do not silently change the teammates' contract.

## Recovery responsibility

Yorick owns payment/refund investigation for #223; the authorized dev runtime operator must be named in the rollout log before applying approved changes. Until a different cadence is agreed, this runbook's rollout procedure requires the operator to check counts and review cases before enabling, immediately after the controlled expiry demo, and daily while the dev switch remains on. Record these checks in the sprint log. This is an operator procedure, not an automated alert that already exists. Escalate nonzero review/failed work or missing demo progress to the issue owner and runtime operator immediately.

For a missing payment callback, use `stripe-ops` `inspect` before `recover` with the payment id, named operator and explicit confirmation. Recovery re-reads Stripe and uses the normal idempotent settlement path; never set a payment to succeeded with SQL. For a refund in review/failed, inspect its payment amount, currency, provider intent and obligation metadata. `RefundService.reconcile(paymentId, providerRefundId)` verifies an **existing** provider refund; it cannot send a second refund. The current operator runner does not expose this refund method as a command: do not invent a CLI or public endpoint. If it is needed on dev, arrange a separately reviewed operator invocation before enabling that scenario.

Read the refund's `last_result` before acting on it (#539):

| `last_result` | Meaning | First check |
|---|---|---|
| `PROVIDER_STATE_MISMATCH` | The PaymentIntent is not refundable as recorded: not succeeded, wrong amount or currency, or live mode | Payment facts in Stripe and in `biz_payment` |
| `PROVIDER_REJECTED` | Stripe refused the call in a way that a retry cannot change: 400/404 invalid request, 402, or an idempotency key reused with different parameters | The backend WARN line `Stripe call failed: operation=..., type=..., status=..., code=..., requestId=..., disposition=review`; look up the request id in the Stripe Dashboard request log |
| `PROVIDER_RETRY` (state `processing`), with that WARN line | Rate limit, connection failure, 5xx, a 409 concurrent same-key request, or a 401/403 credential error; retried every 30 s under the same idempotency key | The WARN line with `disposition=retry`; for 401/403 fix the configured key (Stripe rejects these before executing, so nothing is saved under the key and the next tick creates exactly one refund); escalate if it persists |
| `PROVIDER_RETRY` (state `processing`), with **no** `Stripe call failed` line | The sandbox gate refused the client before any Stripe call: the key is missing or does not start with `sk_test_` (#548) | That the configured key is present and starts with `sk_test_`, without printing it |

The WARN line deliberately carries no provider message or response body; the request id is the link to the details in Stripe. Credential retries are bounded for creation by the 23 h cutoff, after which the worker only searches by metadata; a row that already has a `re_...` id keeps re-reading it until the key is fixed.

Stranded review rows. The worker never re-claims a `review` row, and `reconcile` needs an existing `re_...` id. When a row was parked before the database recorded any provider refund id, recover it in this order, with the payment owner:

1. List the refunds on the obligation's PaymentIntent and keep those whose `metadata.obligation_id` equals the obligation id. This is what `findRefund` does. If exactly one matches, `reconcile` with its `re_...` id. Do not create anything. If more than one matches, stop and escalate.
2. Only if none matches, the payment owner may decide on a reviewed manual refund in Stripe carrying the obligation's `obligation_id` and `payment_id` metadata, then `reconcile` with its id. A separately reviewed requeue is the alternative. There is no requeue command yet.

`charge_already_refunded` (400) means the charge was refunded outside the worker, for example from the Dashboard. Do not create another refund. Attach the obligation's `obligation_id` and `payment_id` metadata to that existing refund, then `reconcile` with its id. `reconcile` still checks the payment intent, amount and currency, so a partial or different refund stays in review as `PROVIDER_MISMATCH`.

A 404 `resource_missing` on a read (`retrieveRefund`, or the PaymentIntent lookup inside creation) parks the row too. One cause is a key from a different Stripe account than the one that took the payment. Check the configured key's account first. After fixing it, follow step 1, or `reconcile` with the stored `re_...` id.

A case is resolved only when database and provider facts agree, the receipt agrees with them, durable audit exists and the next due/review snapshot contains no unexplained work. Stripe retries do not replace this ownership. Pending provider work, failed recovery or uncertainty about an existing refund stays open with an owner and next check time.

## Refund replay and crash acceptance (#224)

The [dated local rehearsal](stripe-refund-recovery-acceptance-20261003.md) exercises the real PostgreSQL claim/lease/finish path with a test-only idempotent provider substitute: after the substitute records a successful refund, the worker loses the response before the database finish, then the recovered claim uses the same durable key and observes one provider-side refund. It also checks a permanent refusal moves the obligation to review and a repeated signed `refund.updated` notification creates no new work. The enabled Checkout webhook deliberately ignores `refund.*`; provider-verified refund state comes from the worker, not such a notification. Do not describe the ignored notification as a refund-settlement handler.

For shared-dev acceptance, first record the approved operator, target image, Sandbox endpoint and the dedicated test payment/obligation IDs without credentials. Run one paid, unfilled team through natural expiry and a successful Sandbox refund; inspect the refund in both Stripe and the database using only non-secret identifiers. Replay the same signed notification and compare payment, refund and audit facts before and after. For the crash case, arrange a reviewed, deterministic fault-injection window that stops only the dedicated test worker after the provider has accepted the refund and before the database finish; do not randomly kill the shared backend or inject an unreviewed production switch. After restart and lease expiry, verify the same idempotency key, one Stripe refund ID, one `biz_refund` row and a succeeded receipt. A permanent provider-state refusal or provider rejection must leave the obligation in `review` with an operator owner and must not be retried on the next tick. If a deterministic dev fault point is unavailable, leave that acceptance item open rather than claiming a manual restart proves it.

## Disable and drain

1. Pause new payment traffic at an approved ingress/maintenance control; keep the enabled Checkout profile and signed webhooks available. The current feature flag also hides receipts and rejects money callbacks, so disabling it first is not a safe intake-only switch.
2. Inventory open Checkout attempts. Expire/reconcile them through the existing operator commands, preserving paid results and compensation obligations. Wait for in-flight callbacks or explicitly assign their recovery; use bounded batches and inspect remaining review work.
3. Keep scheduling on while due teams are resolved and refund obligations drain. Count **all** pending/processing refunds, active leases and review/failed rows, including future retries; a zero runnable count is insufficient. Confirm provider outcomes. Do not stop a worker in the middle of a claimed obligation just because the latest runnable count is zero.
4. If money may still be captured, leave verified webhook processing available or assign every attempt to an operator recovery plan before changing the profile. For an emergency stop, record the undrained inventory and recovery owner; do not claim a successful drain.
5. Only after the above checks, the authorized operator can set `BLOCBUY_STRIPE_CHECKOUT_ENABLED=false` and scheduling false, preserving unrelated settings, then force-recreate and verify capabilities false, runtime scheduling false and health. Stop maintenance after confirming payment actions fail closed. A disabled Checkout switch rejects signed money callbacks; unresolved notifications require recovery after re-enablement.

Do not tick #223's deployed acceptance criteria or pre-live checklist items 2 and 6 from local tests. The day log must say which target actually passed.
