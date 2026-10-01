# Architecture Decision Records (backend)

Nygard format, numbered from 0001. Numbering is independent of `frontend/docs/adr/` — an ADR number
is only unique within its own directory, so cite backend ones as "backend ADR 0001". New decisions
get the next number; superseded ADRs keep their file and point forward.

Locked stack decisions that predate this directory live in
[`../design/backend-skeleton-plan.md`](../design/backend-skeleton-plan.md) and in the
`backend-constraints-skill`; they are not restated here.

| # | Title | Status |
|---|---|---|
| 0001 | [Purchase intent lives in order, group terms live on the product](0001-payment-business-integration.md) | Accepted (2026-09-22) |
| 0002 | [A team's resolution is published, and its consequences run before the commit](0002-team-lifecycle-events.md) | Accepted (2026-09-22) |
| 0003 | [Referral credit is a post-commit subscriber](0003-share-credit-post-commit-subscriber.md) | Accepted (2026-09-25) |
| 0004 | [Retire the unused legacy PaymentIntent path](0004-retire-legacy-payment-intent-path.md) | Accepted (2026-09-28) |
| 0005 | [Gate Stripe Checkout with a deployment property](0005-stripe-checkout-env-flag.md) | Accepted (2026-09-30) |
