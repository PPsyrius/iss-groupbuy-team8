# Refund replay and crash recovery: local rehearsal, 2026-10-03 (#224)

Status: local rehearsal passed; shared-dev and real Stripe Sandbox acceptance remain open. Owner: @Yorick-Yu. No provider credentials or real Sandbox identifiers are recorded here.

## Environment and result

- Source: `codex/issue223-224-dev-acceptance` based on `origin/dev` at `23180f2`; the changes and this record were uncommitted during the run.
- Command: `bash scripts/payment/test-rollout-local.sh` on Java 21 and an isolated loopback PostgreSQL 18 cluster; Maven `verify` passed across seven modules, including 8 `StripeRolloutAcceptanceIT` and 4 `TeamExpirySchedulingIT` integration tests with zero failures.
- Command: full `./mvnw -B -ntp verify` from `backend/` passed across all seven modules with the full Testcontainers integration suite, Spotless, Checkstyle, SpotBugs and aggregate JaCoCo generation. Testcontainers was pointed at the workstation's Colima socket; that local Docker setting is not application configuration.
- Boundary: production controllers, services, repositories, jobs and Flyway migrations ran against PostgreSQL; OAuth/session and Stripe network calls used test-only substitutes. No shared-dev host, real Stripe API, browser or AWS operation was exercised.

## Observed local cases

| Case | Local evidence | Limit |
|---|---|---|
| Paid unfilled team expires | One sweep produced cancelled orders and one durable refund obligation per paid member; repeated sweep and worker calls left two succeeded refunds for two members | Provider snapshots were substitutes, not real Sandbox refunds |
| Provider succeeds, worker loses result before DB finish | Test substitute stored one provider refund keyed by the durable `RefundWork.providerKey()`, then threw before finish; the DB row remained pending with no provider ID. After the lease was made due, a second claim used the same key, received the same provider refund and finished one `biz_refund` row as succeeded | Simulates a crash boundary; it does not kill a deployed worker or prove Stripe's live idempotency retention |
| Duplicate signed refund notification | The same signed `refund.updated` event was delivered twice after a succeeded refund; payment/refund/audit counts did not change | Current Checkout webhook ignores `refund.*`; it is not the refund settlement source |
| Permanent provider-state refusal | `PAYMENT_STATE_INVALID` parked the obligation as `review`/`PROVIDER_STATE_MISMATCH`; the next worker tick made no second provider call | The refusal was a test substitute, not a real Stripe response |

## Shared-dev acceptance still required

| Check | Required evidence | Status |
|---|---|---|
| Dev target and pre-switch backlog | Approved operator, account/Region/instance, immutable image, UTC counts for overdue teams and due/review refunds | Not checked |
| Natural expiry and real Sandbox refund | Dedicated team/payment/order/refund IDs, one Stripe refund ID with succeeded status, receipt and My blocs screenshots, next-sweep no-duplicate check | Not checked |
| Replay | Before/after database and provider facts for an approved replay; no new obligation, refund or settlement | Not checked |
| Crash window | Reviewed deterministic stop after provider acceptance but before DB finish, followed by restart/lease expiry and same-key recovery with one provider refund | Not checked; no dev fault-injection mechanism approved |
| Permanent failure and operator review | Controlled refusal with review state, named owner and no retry on the next tick | Not checked |

The acceptance criterion's phrase “refund webhook” needs care: current enabled Checkout handles signed Checkout session events, while signed `refund.*` events are acknowledged without changing refund state. The worker verifies the provider outcome. If the team intends refund webhooks to drive state transitions, that is a separate design/API change and cannot be inferred from this local no-op replay test.
