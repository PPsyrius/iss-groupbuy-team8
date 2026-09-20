## Background
The local checkout journey passes Sandbox acceptance. Expired purchases still have pending refund obligations, and the account page has no order list.

## Scope
1. Execute Sandbox refunds from saved obligations, with durable idempotency and retry recovery.
2. Show the signed-in buyer's paginated orders and accurate refund status.
3. Test PostgreSQL persistence, authorization, retries and the real Sandbox refund.
4. Check Google login and deployed endpoints without changing shared infrastructure.

## Non-goals
- Live payments, GitHub writes, shared deployment or OAuth configuration changes.

## Acceptance Criteria
- [x] One obligation produces at most one provider refund despite repeated processing.
- [x] Unknown outcomes remain recoverable; they never appear as a successful refund.
- [x] Buyers cannot read another buyer's orders or payment receipts.
- [x] Backend and frontend checks pass; real Sandbox evidence is recorded separately.
- [x] Remaining external prerequisites are reported without claiming local code is deployed.

Evidence: `backend/docs/payment/stripe-refunds-orders-20260920.md`. No GitHub issue was created.
