## Background

The imported dev ZIP had a product page and session guard, but authenticated Start/Join buttons were unwired. The local Stripe slice already handled provider confirmation and database settlement.

## Scope

1. Connect the existing Start/Join actions through the generated payment client.
2. Add an authenticated receipt with payment, order and bloc references.
3. Verify Cookie ownership, retry handling and real Sandbox settlement against isolated PostgreSQL.

## Non-goals

- Shared deployment, real Google login acceptance, buy-alone checkout and refund execution.
- GitHub changes: this draft stays local under the user's instruction.

## Acceptance Criteria

- [x] Product page → Stripe Sandbox → frontend receipt works for START and JOIN.
- [x] Each paid attempt has one local order/member and the shared bloc reaches 2/2.
- [x] Frontend `pnpm check` passes 106 tests; focused backend script passes 57 tests.
- [x] Anonymous/wrong-owner receipt requests fail; client amount/buyer overrides are rejected.
- [x] Saved evidence and remaining integration boundaries are in `backend/docs/payment/stripe-frontend-integration-20260920.md`.
