## Background

Yorick requested removal of all locally added test interfaces before the Stripe change is merged.
Original dev interfaces and configuration must remain unchanged.

## Scope

Remove the local login/operator endpoints and their runtime scripts. Retain test-only business
fixtures outside the deployable JAR and keep formal payment contracts/default-off behavior.

## Acceptance Criteria

- [x] No `/local/stripe` handlers registered in default or enabled test contexts.
- [x] No local adapters, fixture classes or fixture migrations in the application JAR.
- [x] Original source/config/DDL and HTTP contract guard passes.
- [x] Payment/refund regression suite and frontend gates pass.
- [ ] Changes uploaded and merged through the repository's required checks.

Contribution: Yorick; Sprint 2. Current evidence: `../log.md`, entry 20 September.
