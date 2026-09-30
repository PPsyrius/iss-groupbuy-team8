# 0005. Gate Stripe Checkout with a deployment property

Date: 2026-09-30

## Status

Accepted

## Context

The `stripe-sprint2` profile previously selected both the Checkout webhook facade and the buyer-facing capability gate. The EC2 Compose deployment fixes `SPRING_PROFILES_ACTIVE=ec2`; its `environment` entry overrides `env_file`, so an operator could not enable Sandbox Checkout through `/etc/blocbuy/backend.env`. A profile change required a source edit and deployment. Local demo products also had no startable team terms or price policy.

## Decision

`BLOCBUY_STRIPE_CHECKOUT_ENABLED` binds to `blocbuy.payment.stripe.checkout-enabled`, defaults to `false` in all profiles, and selects exactly one `PaymentAppService` at the bootstrap assembly boundary. The bound value also feeds `StripeFeatureSwitch`. The Boot-specific bean conditions stay out of the application module. The `stripe-sprint2` profile is a one-release compatibility alias that sets the same property to `true`; a follow-up issue will remove it. This amends the profile-gate wording of backend ADRs 0001 and 0004 without changing their business, lock or settlement decisions.

When enabled, startup requires a nonblank `sk_test_` API key, webhook signing secret, and success/cancel URLs. Disabled deployments require none of them. The `ec2` Compose profile remains unchanged. Checkout and the independently gated expiry/refund scheduler remain separate switches. Neither this decision nor the test-mode key permits live Stripe keys or production Checkout.

The repeatable demo seed gives only local on-sale products valid team terms and a first team-price policy. It does not run under `ec2`; dev operators must use the existing audited admin team-terms command for real dev rows rather than applying demo data to a shared database.

## Consequences

An operator can enable Sandbox Checkout on dev by placing the flag and test-mode configuration in the host env file and force-recreating the backend container after the release checklist. A container restart alone retains its old environment. Production remains disabled; its optional SSM flag defaults to `false` when absent for a compatible rollout. Turning the flag off while payments are outstanding rejects signed money events, so operators must reconcile or drain them first. The public API and OpenAPI contract do not change.
