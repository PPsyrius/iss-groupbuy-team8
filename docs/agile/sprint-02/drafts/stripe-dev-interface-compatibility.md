## Background
The Checkout change expanded PaymentGateway and changed PaymentRepository.findById from String to UUID. The user requires all original dev interfaces and shared configuration to remain unchanged.

## Scope
Restore the original payment interfaces, model, implementations and controllers from dev 211cfb7. Put Checkout capabilities in separate classes and select the new webhook implementation only under the stripe-sprint2 profile. Preserve the existing HTTP envelopes, Cookie authentication and frontend operation names.

## Non-goals
GitHub writes, merging, shared deployment, or replacing the team's unfinished intent/order/expiry work with local fixtures.

## Acceptance Criteria
- [x] Protected source hashes match the imported dev baseline.
- [x] Original PaymentGateway, PaymentRepository and Payment.of(String) callers still compile and pass their original tests.
- [x] The unchanged webhook controller resolves the correct application service with the feature profile on and off.
- [x] Checkout, refunds, database and HTTP regression tests pass after isolation.

No GitHub issue was created. This is a local implementation record.

Evidence: `backend/docs/payment/stripe-dev-compatibility-20260920.md`. Shared intent/order/expiry integration remains outstanding and is not included in the compatibility completion claim.
