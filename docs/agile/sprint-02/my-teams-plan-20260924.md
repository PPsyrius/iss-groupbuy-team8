# My Teams (UC8 / US12) — implementation plan

Date: 2026-09-24 · Branch: `feature/view-my-teams-20260924` · Issue: #179 (parent #31) · Sprint 2 Plan §2.7.1 US12, §3.2.4

This plan was written before implementation and approved on 2026-09-24. Section 7 records what was delivered and verified against it.

## 1. Scope

### 1.1 In scope

1. `GET /api/v1/group-deals/me?page=&size=` returning `Result<PageResult<MyTeamResponse>>`, following the `GET /api/v1/orders/me` conventions.
2. A groupbuy-owned read path: repository port method, one-domain SQL join, application query, `MyTeamView` projection.
3. Product titles and images resolved in one batch per page through a groupbuy-owned port implemented by catalog.
4. Tests (unit, Testcontainers, MockMvc, lifecycle integration), regenerated OpenAPI contract, design-doc update, NFR row and sprint-log entry.

### 1.2 Out of scope

| Item | Where it goes |
| --- | --- |
| Team detail, member list, invite landing (`GET /group-deals/{dealId}`) | separate issue |
| Frontend My Teams page | US24 / #160 T3-03 (needs `api-client-regen` after merge) |
| Refund status per team row | #29 (refund worker), #128 |
| Keyset pagination | deferred; see §3.4 |
| Database migration or new index | none needed at current volume; see §3.4 |

## 2. Baseline (measured on `dev` bc987c3)

| Piece | Location | Note |
| --- | --- | --- |
| `GroupDealController` | `blocbuy-adapter/.../groupbuy/web/GroupDealController.java` | empty class, no endpoints |
| `GroupDealAppService` | `blocbuy-application/.../groupbuy/service/GroupDealAppService.java` | empty interface with a "one public method per use case" TODO; no implementation |
| Membership data | `V5__groupbuy.sql` `biz_group_member` | `user_id`, `member_role` (`leader`/`member`), `join_time`, nullable `order_id`; index `idx_biz_group_member_user_id` |
| Who writes members | `PaidGroupDealRepositoryImpl` | the leader also gets a member row (`leader`), joiners get `member`; member `status` stays `paid` after expiry, so team state lives only in `biz_group_deal.status` |
| Paging precedent | `OrderController.myOrders`, `BizOrderMapper.selectPageByUserId` | offset `PageQuery` (max 100), `ORDER BY create_time DESC, id DESC`, `Cache-Control: no-store` |
| Catalog product lookup | `ProductRepository` | only single-row `findById`; no batch read |
| Cross-domain direction | catalog → `groupbuy.service`; order → catalog, groupbuy; payment → `groupbuy.event` | groupbuy imports no other domain |

## 3. Design

### 3.1 Layer placement

| Layer | Change |
| --- | --- |
| domain | `groupbuy.model.MemberRole` enum (`fromCode` fails fast); `groupbuy.model.TeamMembership` read projection (not an aggregate); `GroupDealRepository.pageMembershipsByUserId(PageQuery, UUID)`; `groupbuy.gateway.ListingDisplayGateway` port; `ProductRepository.findByIds(Collection<UUID>)` |
| infrastructure | `BizGroupDealMapper.selectMembershipPageByUserId` / `countMembershipsByUserId` sharing one FROM/WHERE fragment; `TeamMembershipDO`; `GroupDealConverter.toMembership`; `BizProductMapper.selectActiveByIds` with the empty-input guard in `ProductRepositoryImpl` |
| application | `GroupDealAppService.pageMyTeams` + new `GroupDealAppServiceImpl` (`@Transactional(readOnly = true)`); `groupbuy.query.MyTeamView`; `catalog.service.GroupbuyListingDisplayAdapter implements ListingDisplayGateway` |
| adapter | `GroupDealController.myTeams` (`GET /me`) and nested `MyTeamResponse` |

The issue text names a `MyTeamQueryService`; the empty `GroupDealAppService` already is the groupbuy use-case slot, so the query lands there instead of adding a second facade.

### 3.2 Product titles without a dependency cycle

Catalog already depends on groupbuy (`GroupDealQueryService` for listing enrichment and reference checks). If groupbuy called a catalog service for titles, the two domains would depend on each other. ArchUnit has no cycle rule, so CI would not catch it; it is still a boundary violation.

Decision: groupbuy declares the consumer-owned port `ListingDisplayGateway` (domain module, provider-free record `ListingDisplay(productId, title, coverImage)`), and catalog implements it in its application layer. Precedent: `payment.gateway.PaymentBusinessPort` implemented by `order.service.OrderFulfilmentAdapter`. Only title and cover image cross the boundary; no `Product` or DO leaks.

The title is the **current catalog title** (team decision 2026-09-24), not the frozen order snapshot. Titles stay editable after publish (`Product.requireCommercialEdit` freezes only price, currency and stock), so My Teams may show a newer title than My Orders for the same deal.

### 3.3 Query

```sql
FROM biz_group_member m
JOIN biz_group_deal d ON d.id = m.deal_id AND d.deleted = false
WHERE m.user_id = #{userId} AND m.deleted = false
ORDER BY m.join_time DESC, m.id DESC
LIMIT #{limit} OFFSET #{offset}
```

The join stays inside groupbuy. Page and count share the FROM/WHERE fragment so the total always matches the rows. A request costs a fixed three queries (rows, count, one batch product read), independent of page size. An empty page skips the product read. A product that is no longer active keeps its row with null display fields, so page and total never disagree.

### 3.4 Paging and index

Offset paging with a unique tie-breaker (`m.id`), matching `/orders/me`, so the frontend handles both lists the same way. The Sprint 2 plan suggested keyset; the backend skill prefers keyset for *deep* pagination, and one buyer's team count is small. Keyset is deferred; `PageResult.nextCursor` stays null. The existing `idx_biz_group_member_user_id` serves the filter; a composite `(user_id, join_time DESC, id DESC)` index is a follow-up only if the US23 baseline shows the sort is costly.

### 3.5 Status and consistency

Status is the committed `biz_group_deal.status`, never derived from the clock on read. A team past `expire_time` stays `open` until the sweeper resolves it (up to one sweep interval), and so does its order (`pending`). Deriving `expired` on read would make My Teams disagree with My Orders during that window. `expireTime` lets the UI show "ended" meanwhile. `failed` and `canceled` are passed through as codes; nothing writes them today.

### 3.6 Security and errors

The buyer id comes from `StpUtil.getLoginIdAsString()` only; a `userId` query parameter is ignored. Anonymous requests get 401 `COMMON_401_001` from Sa-Token; out-of-range paging gets 400 `COMMON_400_002` from Bean Validation. No new `ErrorCode` is needed. Responses carry `Cache-Control: no-store`.

### 3.7 Contract

Additive only: a new path and new schemas. The contract is regenerated with the `openapi-export` profile; `openapi-diff` must pass without the `contract-change-approved` label.

## 4. Tests mapped to #179 acceptance criteria

| Criterion | Test |
| --- | --- |
| Only the caller's teams, led and joined, paged; client user id ignored | `TeamMembershipRepositoryIT` (other user, deleted member, deleted deal excluded; tie-breaker; offset vs count); `GroupDealControllerTest.pageIsScopedToTheSessionBuyerNotTheWire` |
| Row shows status, sizes, expiry, role | `GroupDealControllerTest.defaultsApplyAndEveryFieldRendersWhenPaginationParamsAreAbsent`; `GroupDealAppServiceImplTest` |
| Team status agrees with order status | `StripeLocalIntegrationTest`: filled team → `succeeded`/`confirmed` for leader and joiner; overdue-but-unswept → `open`/`pending`; swept → `expired`/`cancelled`; the row links the same `orderId` and resolves the catalog title |
| Titles in one batch per page | `GroupDealAppServiceImplTest` (`verify(gateway, times(1))` with a de-duplicated id set; empty page never calls it); `GroupbuyListingDisplayAdapterTest`; `ProductSearchRepositoryIT.findByIdsReturnsLiveProductsOfAnyStatusInOneQuery` |
| Anonymous → `COMMON_401_001` | `GroupDealControllerTest.anonymousMyTeamsIsRejectedBeforeBusinessCode` |
| Contract regenerated | `contracts/openapi.json` in the same commit |
| Unit + Testcontainers tests, verify green, coverage ≥ 80% | JaCoCo aggregate: zero missed lines/branches on every touched main file |

`ArchitectureTest` stays unchanged and green.

## 5. Execution order

1. Domain types, ports and `MemberRoleTest`.
2. Infrastructure mapper, DO, converter, repositories, unit tests and ITs.
3. Application service, view, catalog adapter and unit tests.
4. Controller and `GroupDealControllerTest`.
5. Consistency assertions in `StripeLocalIntegrationTest`.
6. JDK 21 `./mvnw -B verify`, JaCoCo aggregate check, contract regeneration.
7. `backend-skeleton-plan.md` §19, NFR register row, sprint-log entry, PR linking #179 and #31.

## 6. Risks

| Risk | Handling |
| --- | --- |
| Sprint 2 code freeze is 2026-09-24 EOD | merge needs a team freeze exception, otherwise it carries into Sprint 3 |
| Title differs from the order snapshot after an edit | accepted; current catalog title chosen deliberately (§3.2) |
| Sweeper lag shows `open` past the deadline | accepted; agrees with the order and `expireTime` is exposed (§3.5) |
| Paid-but-rejected participations have no member row | correct per spec; refund visibility belongs to #128 / #29 |
| Legacy member rows without `order_id` | `orderId` is nullable in the contract |

## 7. Delivered (2026-09-24)

Implemented as designed; no deviations beyond §3.1's note on `GroupDealAppService`. JDK 21 `./mvnw -B verify` passed all modules, including `ArchitectureTest` 8, `TeamMembershipRepositoryIT` 4, `ProductSearchRepositoryIT` 7, `GroupDealControllerTest` 7, `StripeLocalIntegrationTest` 17, `GroupDealAppServiceImplTest` 3, `GroupbuyListingDisplayAdapterTest` 2, `GroupDealRepositoryImplTest` 7, `ProductRepositoryImplTest` 18 and `MemberRoleTest` 4. The JaCoCo aggregate reports zero missed lines and branches on all eleven touched main files. The contract regeneration adds only the new path and schemas. SonarCloud and the CI contract gate run on the PR.
