# 0004. Retire the unused legacy PaymentIntent path

Date: 2026-09-28

## Status

Accepted

## Context

The original `PaymentGateway` and `PaymentRepository` were skeleton contracts without a production payment-creation caller. The default webhook implementation verified and logged signed PaymentIntent events but acknowledged them without settlement; its repository always returned empty. The `stripe-sprint2` compatibility adapter only served a test. The payment lead confirmed that this code existed for compatibility, not future payment methods; Checkout remains the expansion path for PayNow. The monorepo is the complete caller set for this change (#216).

## Decision

Remove the unused PaymentIntent gateway, String-ID payment aggregate/repository and compatibility adapters rather than implementing another settlement pipeline. Keep `PaymentIntentPort`, which issues server-owned order purchase intents for Checkout and is unrelated to the legacy Stripe PaymentIntent gateway. Keep the existing `biz_payment` schema and Checkout mapper: they are the source of truth for the active payment and refund paths.

Keep the public webhook controller and its `PaymentAppService` interface, with exactly one implementation per profile. When `stripe-sprint2` is enabled, `StripePaymentAppService` delegates to `StripeCheckoutService`, which verifies the signature outside a transaction and durably claims and settles supported Checkout session events inside a local transaction. When disabled, `DisabledStripePaymentAppService` verifies the signature through the same gateway and returns non-2xx for the four supported Checkout events or any signed `payment_intent.*`, `charge.*` or `refund.*` event; unrelated signed events are acknowledged. A supported event with an undecodable payload or a missing type also fails closed. Neither the disabled facade nor verification accesses the database or constructs a Stripe API client.

The only settlement triggers when Checkout is enabled are `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`. Other signed Stripe event types, including PaymentIntent notifications, are not Checkout settlement triggers, but money-related types are deliberately rejected while Checkout is disabled rather than silently acknowledged. The configured endpoint must deliver the four session events. This decision relies on there being no callers or outstanding payments from the retired legacy path, as confirmed for this monorepo.

## Consequences

There is no always-empty production repository or separate PaymentIntent creation path. The existing event-id uniqueness, payment/intent/order locks, refund obligations and sandbox gates remain owned by Checkout and refund services. Checkout still explicitly requests `card` only; adding PayNow or other eligible payment methods would require separate implementation and acceptance of their payment and refund lifecycle. A disabled deployment will receive provider retries for relevant events until Checkout is re-enabled, so operators must drain or reconcile in-flight work before disabling an enabled deployment; the release checklist retains that gate. No controller mapping, DTO, migration or HTTP success shape changes.
