## Background
Checkout is opt-in on the backend, but the product page and account history can still call its routes when the profile is off.

## Scope
Use the existing stripe-sprint2 profile as the switch. Expose its state through an authenticated additive capability endpoint. Gate Stripe browser routes before service/DB access, and keep frontend actions and queries disabled until the server explicitly enables them.

## Non-goals
No dev interface/configuration changes, live transactions, deployment, GitHub writes or merge.

## Acceptance Criteria
- [x] Disabled browser routes never reach payment or order services.
- [x] Missing, failed or disabled capability responses leave payment actions and dependent queries off.
- [x] Explicit local enablement retains the payment/refund test flow.
- [x] Original dev contracts remain unchanged; generated clients and regression tests pass.

NFR-23: availability/interoperability. Yorick, Sprint 2. No remote issue was created.

Evidence: `backend/docs/payment/stripe-dev-compatibility-20260920.md`, default-off addendum.
