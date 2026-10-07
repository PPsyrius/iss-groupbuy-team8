# refactor(groupbuy): inject the existing paid-deal Factory Method

## Background

UC3 already uses `PaidGroupDealCreator` and `StandardPaidGroupDealCreator`, but `PaidGroupDealService` instantiates the concrete creator itself. The service must change to select a different implementation, and tests cannot independently supply a creator. This task improves the existing Factory Method's composition boundary.

## Scope

1. Constructor-inject the existing `PaidGroupDealCreator` into `PaidGroupDealService`.
2. Register `StandardPaidGroupDealCreator` in application configuration, keeping the domain free of Spring.
3. Verify service delegation and Spring wiring through the existing paid-participation tests.

## Non-goals

- Additional creator registries, pattern abstractions, payment verification or public API changes.

## Acceptance Criteria

- [x] A configured creator determines initiation behavior without editing the service.
- [x] Standard initiation still persists the paid leader and matching receipt atomically.
- [x] Full backend verification includes real Spring transaction and PostgreSQL tests.

## Traceability

Related use case: UC3 (#26). Owner: Wang Ziang (Eric), @EricWang1358. Design: [paid participation](../../../../backend/docs/design/paid-group-participation.md#reuse-and-receipt-compatibility-amendment-2026-10-07). Local implementation only; publication and merge are pending.
