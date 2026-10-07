# fix(groupbuy): share quote and paid-admission domain guards

## Background

Pre-payment quoting duplicates `GroupDeal.joinPaid` admission rules and currently accepts reconstructed deals with zero or negative paid members. Paid admission later rejects these snapshots, unnecessarily accepting payment before compensation. The aggregate should own reusable checks while each caller retains its established rejection priority.

## Scope

1. Expose domain guards for the lifecycle/time window, available seat and new membership.
2. Reuse them from quote validation and paid admission under existing locks.
3. Reject legacy counts below one before quoting; preserve the quote's window/capacity/membership order and paid admission's price/membership/window/capacity order.

## Non-goals

- Reserving seats during quoting, changing lock order or error codes, repairing persisted legacy rows, or creating a second lifecycle policy.

## Acceptance Criteria

- [x] Quote validation rejects counts zero and negative with `DEAL_FULL` before accepting payment.
- [x] Window, capacity and duplicate-membership rejection priorities remain unchanged for valid snapshots.
- [x] Database time is read after locking; leader checks avoid an extra membership lookup.
- [x] Existing last-seat races, receipt replay and rollback tests pass with real PostgreSQL.

## Traceability

Related use case: UC4 (#27), incremental to the completed admission refactor #186. Owner: Wang Ziang (Eric), @EricWang1358. Reuses the aggregate and `DealStatus.canTransitionTo`; these guards are domain behavior, not a new GoF pattern. Evidence: NFR-56 and [October 7 log](../log/2026-10-07.md). Local implementation only; publication and merge are pending.
