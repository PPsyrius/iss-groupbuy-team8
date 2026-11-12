#import "lib-a.typ": *

== UC8 View My Orders and My Teams <sec-a-uc08>

UC8 is the buyer's account page, "My blocs". A signed-in buyer sees the teams they lead or joined, the latest 100 of them (`MY_BLOCS_FETCH_SIZE`), with their stored state, and one page of their orders, each with its status. The use case only reads. Its design questions are ownership (a buyer must never see another buyer's rows), a consistent snapshot (a teams page and its total must agree; the orders list accepts a count from a separate snapshot), and a read that crosses from the group-buy module into the catalog module without a dependency cycle. Every class, method and error code named here exists in the code at the freeze commit (report §1.1). UC8 is designed and built; its status on AWS is given by capability in the status table of report §2.2.

=== Use Case and Flows of Events <sec-a-uc08-flows>

- *Actor.* Buyer (primary, signed in).
- *Trigger.* The buyer opens "My blocs", or returns to it after a payment.
- *Services.* commerce-svc owns every step (its group-buy, catalog and order modules). The buyer's identity comes from the shared sign-in session; identity-svc is not called.
- *Preconditions.* The buyer is signed in (UC2, @sec-a-uc02).
- *Postconditions.* The buyer has seen only their own teams and orders. Each team's state agrees with its orders: a succeeded team has confirmed orders, an expired team cancelled ones. Nothing has changed.

#atable(
  [UC8 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off.],
  <tbl-a-uc08-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The buyer opens My blocs. The system identifies the buyer from their sign-in, never from anything the page sends.],
  [*B2*], [], [The system reads one page of the buyer's teams, newest membership first: for each, the team's stored state, seats taken out of the target, deadline and price, the buyer's role (leader or member), and the order behind the membership. It then reads the current title and image of the page's listings in one batch.],
  [*B3*], [], [The system reads one page of the buyer's orders, newest first: for each, the product title and image as they were when the order was created, the amount and the order's status.],
  [*B4*], [], [The page shows the teams under the chips Active, Completed and Refunded, each row labelled "Filling", "Closing soon", "Ended", "Filled" or "Closed", and under "My orders" the orders labelled "Waiting for the bloc", "Confirmed" or "Cancelled".],
  [*B5*], [], [The buyer may switch the chip (nothing is read again), page through the orders, open a team's listing or share an open team (UC7, @sec-a-uc07).],
  [*A1*], [B1], [The visitor is not signed in: the request is refused, and the page sends the visitor to sign in and back (UC2).],
  [*A2*], [B2, B3], [The buyer has no teams and no orders: one card explains how a team works and links to the catalog. With teams but no orders, a "No orders yet" card appears under the teams.],
  [*A3*], [B2], [A listing behind a team has been deleted: the team row stays, without title or image. A delisted (`OFF_SALE`) listing keeps its title and image.],
  [*A4*], [B4], [A team's deadline has passed but the expiry run has not reached it: its stored state is still open, so the row reads "Ended", not "Closed" (UC5, @sec-a-uc05).],
  [*A5*], [B3], [The requested order page is past the last page: the page moves to the last page.],
  [*A6*], [B3], [The buyer opens one order (from a payment receipt): the system returns it only if it is the buyer's own. Another buyer's order gets the same "not found" as an order that does not exist.],
  [*E1*], [B2, B3], [Either list cannot be read: the page shows an error with "Try again".],
)

=== Analysis Class Diagram <sec-a-uc08-ac>

@fig-a-uc08-ac is the analysis model. The Buyer works through `MyBlocsUI`. Each list has its own control, and both are named after the use case: `ViewMyTeamsControl` and `ViewMyOrdersControl`. Both lists navigate from the buyer's `Account`, so ownership is part of the model rather than a filter added later; one order (A6) is checked with `Order.belongsTo`. `ViewMyTeamsControl` reads `Product` directly, in one batch per page, because only the display fields are needed. The entity names are those of part 6 (@sec-a-uc04) and of the system view (@sec-a-reading). Two associations are new to the system view because UC8 navigates them: `Account` to `Order` (role _buyer_) and `Membership` to `Order` (_backed by_).

#afig("a-uc08-ac", [UC8 analysis class diagram (UML class notation, BCE stereotypes). The Buyer uses `MyBlocsUI`; each control reaches the lists through the buyer's `Account`, one order is checked with `belongsTo`, and `Product` is read for display fields only.])

=== Analysis Sequence Diagrams <sec-a-uc08-as>

UC8 has two major flows, one per list. They read different entities and page on their own; the page starts both when it opens. @fig-a-uc08-as-teams is B1–B4 with A3; @fig-a-uc08-as-orders is B3–B4 with A6. A1 needs the design concept of a session and is drawn in the design sequences only. B5's chip switch, A2, A4 and E1 happen in the page alone (S2): they choose a card or a label from rows already read, or show an error, and involve no entity, so no sequence draws them. B5's paging and A5 read the orders again, as in @fig-a-uc08-as-orders; B5's sharing is UC7.

#afig("a-uc08-as-teams", [UC8 analysis sequence diagram, view my teams (B1–B4, A3). The control reads one page of memberships, then all listing titles of that page in one batch.])

#afig("a-uc08-as-orders", [UC8 analysis sequence diagram, view my orders (B3–B4, A6). Each row shows the product as it was when the order was created; one order is shown only to its buyer.], float: true)

=== Transition: Strategies and Mapping <sec-a-uc08-transition>

The strategies of @sec-a-strategies turn the model into the design. Six of the nine change UC8:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted both controls → resultant Spring services `GroupDealAppServiceImpl`, `OrderAppServiceImpl` with read-only transactions, and a record for every row (`TeamMembership`, `MyTeamView`, `ListingDisplayGateway.ListingDisplay`, `ProductDisplay`, `OrderSummaryView`); dynamics @fig-a-uc08-ds-teams, @fig-a-uc08-ds-orders.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `MyBlocsUI` → resultant route `/_authenticated/account` (`AccountPage`, `MyBlocs`, `OrderHistory`), which computes chips and labels itself; B5, A2, A4, A5 and E1 live here. Not drawn.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `MyBlocsUI`, `Product`, `Account` → resultant `GET /api/v1/group-deals/me`, `GET /api/v1/orders/me` and `GET /api/v1/orders/{orderId}` in the `Result` envelope, and the port `ListingDisplayGateway`, owned by group-buy and implemented by catalog. All of it runs in commerce-svc; the buyer comes from the shared session, so UC8 never calls identity-svc (AD-05), unlike the one remote call of AD-06.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Membership`, `Team`, `Product`, `Order` → resultant projections `TeamMembership` (`biz_group_member` joined to `biz_group_deal`) and `ProductDisplay` (`biz_product`), and the `Order` aggregate with its frozen product snapshot (`biz_order`). Offset pages, newest first; at most three queries per teams page, two when it is empty (NFR-34).
- *S5 External interface:* not applied; no external system takes part.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `Account` and both controls → resultant `StpUtil.checkLogin` and the session's login id in each controller (never a user id from the request), the owner filter in the SQL for both lists, and for one order a check of `Order.userId()` after the read; 404 rather than 403 for another buyer's order, and `Cache-Control: no-store` on every response (NFR-21, NFR-34).
- *S7 Scheduling:* not applied. A4 shows the lag of UC5's expiry sweep (@sec-a-uc05), because state is read as stored, never computed from the clock.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30):* impacted `ViewMyTeamsControl` → resultant one read-only `REPEATABLE_READ` transaction of 5 s: rows, count and product read share one snapshot, so the total matches the rows. No locks. The orders list runs at the default `READ COMMITTED`, so its count is a separate snapshot (accepted: a buyer's orders change only through their own payments and team resolution).
- *S9 Domain and integration events:* not applied. Orders are confirmed or cancelled before commit in the team's own transaction (@sec-a-uc05), so the rows UC8 reads always agree.

#atable(
  [UC8 analysis → design mapping. Every analysis object of @fig-a-uc08-ac, and every design class of @fig-a-uc08-dc-teams and @fig-a-uc08-dc-orders.],
  <tbl-a-uc08-map>,
  columns: (0.85fr, auto, 2.3fr, auto, 0.75fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`MyBlocsUI`], [boundary], [`GroupDealController.myTeams`, `OrderController.myOrders`, `myOrder`. Frontend `AccountPage`, `MyBlocs`, `OrderHistory`: not drawn (S2).], [S2, S3, S6], [@fig-a-uc08-dc-teams, @fig-a-uc08-dc-orders],
  [`ViewMyTeamsControl`], [control], [Entry point `GroupDealAppService.pageMyTeams` (`GroupDealAppServiceImpl`, group-buy).], [S1, S8], [@fig-a-uc08-dc-teams],
  [`ViewMyOrdersControl` (list)], [control], [Entry point `OrderAppService.pageMyOrders` (`OrderAppServiceImpl`, order).], [S1], [@fig-a-uc08-dc-orders],
  [`ViewMyOrdersControl` (one order)], [control], [Entry point `OrderAppService.findMine` (same class).], [S1, S6], [@fig-a-uc08-dc-orders],
  [`Account`], [entity], [No class in commerce-svc: the session login id is the buyer, and each query filters on `user_id`. `User` lives in identity-svc (@sec-a-uc02).], [S3, S6], [not drawn],
  [`Membership`], [entity], [Value object `TeamMembership` (read-only projection) through `GroupDealRepository` → `GroupDealRepositoryImpl` → `BizGroupDealMapper`; `biz_group_member`.], [S4], [@fig-a-uc08-dc-teams],
  [`Team`], [entity], [`biz_group_deal` columns inside `TeamMembership`; `GroupDeal` is not loaded.], [S4], [@fig-a-uc08-dc-teams],
  [`Product`], [entity], [Port `ListingDisplayGateway` and its record `ListingDisplay` (group-buy); adapter `GroupbuyListingDisplayAdapter` (catalog) over `ProductRepository`, returning `ProductDisplay`; `biz_product`.], [S3, S4], [@fig-a-uc08-dc-teams],
  [`Order`], [entity], [Aggregate root `Order`, port `OrderRepository`; `biz_order`.], [S4], [@fig-a-uc08-dc-orders],
  [(none)], [—], [Records `OrderSummaryView`, `MyTeamView` (not drawn) (S1, S3); `GroupDealAppService`, `OrderAppService` (S1); `ORDER_NOT_FOUND` (S6, not drawn).], [S1, S3, S6], [@fig-a-uc08-dc-teams, @fig-a-uc08-dc-orders],
)

=== Design Class Diagrams <sec-a-uc08-dc>

Two slices in commerce-svc (the response records are nested in their controllers): orders (@fig-a-uc08-dc-orders) and teams across group-buy and catalog (@fig-a-uc08-dc-teams). The second shows the representative persistence chain `GroupDealRepository` → `GroupDealRepositoryImpl` → `BizGroupDealMapper`; `OrderRepository` and `ProductRepository` follow it, so only their ports are drawn.

#afig("a-uc08-dc-orders", orient: "portrait", float: true, [UC8 design class diagram, slice 1: my orders (UML, commerce-svc). `OrderAppServiceImpl` turns each `Order` into an `OrderSummaryView` without the payment reference before it leaves the application layer.])

#afig("a-uc08-dc-teams", float: true, [UC8 design class diagram, slice 2: my teams (UML, commerce-svc). Group-buy declares `ListingDisplayGateway` and catalog implements it, so only a title and a cover image cross the module boundary and the dependency still points from catalog to group-buy.])

=== Design Sequence Diagrams <sec-a-uc08-ds>

@fig-a-uc08-ds-teams retells B1, B2 and B4 with A1–A3. The transaction opens on `pageMyTeams`; `GroupbuyListingDisplayAdapter.findByProductIds` is itself read-only and joins it, so the product read sees the same snapshot. @fig-a-uc08-ds-orders retells B3 and A6. In both, `SaInterceptor` refuses A1 before the controller runs; the controller checks the session again and takes the buyer from it, so A1 never reaches the application layer.

#afig("a-uc08-ds-teams", [UC8 design sequence diagram, B1, B2 and B4 with A1–A3 (commerce-svc). One `REPEATABLE_READ` snapshot covers the rows, the count and the single batch product read, so the total always matches the rows.])

#afig("a-uc08-ds-orders", [UC8 design sequence diagram, B3 and A6 (commerce-svc). Ownership is a filter, not a separate failure: a missing order and another buyer's order give the same `ORDER_NOT_FOUND`.])

=== Operation Specifications <sec-a-uc08-ops>

@tbl-a-uc08-ops specifies the four operations that carry UC8's rules. Signatures are those in the code.

#atable(
  [UC8 operation specifications (commerce-svc). Errors are `BizException` error codes unless named otherwise.],
  <tbl-a-uc08-ops>,
  columns: (1.35fr, 1fr, 0.75fr, 1fr, 1.1fr),
  header: ([Operation; transaction], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`GroupDealAppServiceImpl.pageMyTeams(page: PageQuery, userId: UUID): PageResult<MyTeamView>`. `REQUIRED`, read-only, `REPEATABLE_READ`, 5 s.], [B2: the buyer's teams, newest membership first, with listing display fields. Called by `GroupDealController.myTeams` (`page` ≥ 1, `size` 1–100).], [`userId` from the session.], [No write. At most three queries; `total` agrees with the rows; a deleted listing gives a null title and image.], [None of its own (E1). `SaInterceptor` refuses first with `UNAUTHENTICATED` (`COMMON_401_001`); the controller with `VALIDATION_FAILED` (`COMMON_400_002`).],
  [`GroupbuyListingDisplayAdapter.findByProductIds(productIds: Collection<UUID>): Map<UUID, ListingDisplayGateway.ListingDisplay>`. Read-only; joins the caller's transaction.], [B2, A3: display fields of the given products in one batch.], [None; empty input returns an empty map without a query.], [Unknown or deleted products are absent from the map.], [—],
  [`OrderAppServiceImpl.pageMyOrders(page: PageQuery, userId: UUID): PageResult<OrderSummaryView>`. `REQUIRED`, read-only, `READ COMMITTED`, no timeout.], [B3: the buyer's orders, newest first, with the frozen product snapshot.], [`userId` from the session.], [No write; rows and count read as two statements.], [None of its own (E1).],
  [`OrderAppServiceImpl.findMine(orderId: UUID, userId: UUID): Optional<OrderSummaryView>`. `REQUIRED`, read-only.], [A6: one of the buyer's own orders.], [`userId` from the session.], [Empty when the order does not exist or `Order.userId()` differs.], [None; `OrderController.myOrder` turns empty into `ORDER_NOT_FOUND` (`ORDER_404_001`).],
)

=== State Machines <sec-a-uc08-states>

UC8 changes no state, so it has no statechart. It displays two lifecycles drawn elsewhere: the team's `DealStatus` and the order's `OrderStatus`, both in @sec-a-uc05.

=== Patterns Applied <sec-a-uc08-patterns>

#atable(
  [Patterns in the UC8 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc08-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC8], [Roles → classes], [Consequences]),
  [Adapter (GoF, structural; object adapter), behind a Separated Interface (Fowler; a port of Ports and Adapters, architectural)], [Group-buy needs a title and a cover per product, but catalog's `ProductRepository` answers with `ProductDisplay` in catalog's own types. Group-buy must not import catalog: catalog already depends on group-buy, and the reverse would form a cycle.], [Target: `ListingDisplayGateway`. Adaptee: `ProductRepository` (`findDisplaysByIds`). Adapter: `GroupbuyListingDisplayAdapter`, which holds the adaptee and converts each `ProductDisplay` into a `ListingDisplayGateway.ListingDisplay` keyed by product id. Client: `GroupDealAppServiceImpl`. The target interface sits in group-buy's domain layer, the adapter in catalog's application layer. @fig-a-uc08-dc-teams.], [Group-buy depends only on an interface it owns (dependency inversion), and the direction is kept by convention: no architecture test forbids group-buy → catalog, and both share Maven modules (NFR-05). Catalog can change its storage without touching group-buy. Liability: one more class and mapping for two fields, and the port's contract (products not deleted, one batch) is held by tests, not by the type.],
)

=== Flow-to-Design Trace <sec-a-uc08-trace>

#atable(
  [UC8 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc08-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`openMyBlocs`], [`GroupDealController.myTeams`, `OrderController.myOrders`; buyer from `StpUtil.getLoginIdAsString()`], [@fig-a-uc08-ds-teams, @fig-a-uc08-ds-orders],
  [B2], [`listMyTeams`, `Account.memberships`, `Membership.describe`, `Team.summary`, `Product.displayFieldsOf`], [`GroupDealAppServiceImpl.pageMyTeams` → `GroupDealRepositoryImpl.pageMembershipsByUserId` → `BizGroupDealMapper.selectMembershipPageByUserId`, `countMembershipsByUserId`; `GroupbuyListingDisplayAdapter.findByProductIds` → `ProductRepository.findDisplaysByIds`, `toDisplay`; `toView`], [@fig-a-uc08-ds-teams],
  [B3], [`listMyOrders`, `Account.orders`, `Order.summary`], [`OrderAppServiceImpl.pageMyOrders` → `OrderRepository.pageByUserId`; `toView`], [@fig-a-uc08-ds-orders],
  [B4], [`showTeams`, `showOrders`], [`Result` of `MyTeamResponse` and `OrderSummaryResponse` pages; labels by the account page (S2)], [@fig-a-uc08-ds-teams, @fig-a-uc08-ds-orders],
  [B5], [— (page; sharing in UC7)], [Switching the chip: account page only (S2), no backend call. Paging the orders: `OrderController.myOrders` again, as in B3. Sharing a team: `RowShare` mints an invitation when its panel first opens (UC7)], [@fig-a-uc08-ds-orders, @sec-a-uc07],
  [A1], [—], [`SaInterceptor.preHandle` → `StpUtil.checkLogin` throws `NotLoginException`; `UNAUTHENTICATED` (`COMMON_401_001`). The controller's `StpUtil.checkLogin` is a second check], [@fig-a-uc08-ds-teams, @fig-a-uc08-ds-orders],
  [A2], [— (page only)], [`pageMyTeams` returns an empty page without the product read; the card is chosen by the account page (S2)], [@fig-a-uc08-ds-teams],
  [A3], [`displayFieldsOf` without the product], [`findDisplaysByIds` omits deleted products; `toView` sets a null title and image], [@fig-a-uc08-ds-teams],
  [A4], [—], [Status read as stored (`TeamMembership.status`); label by the account page (S2)], [—],
  [A5], [`listMyOrders` (last page)], [Account page requests `OrderController.myOrders` again for the last page, as in B3], [@fig-a-uc08-ds-orders],
  [A6], [`findMyOrder`, `Order.belongsTo`, `showNotFound`], [`OrderController.myOrder` → `OrderAppServiceImpl.findMine` → `OrderRepository.findById`, `Order.userId`; empty → `ORDER_NOT_FOUND` (`ORDER_404_001`)], [@fig-a-uc08-ds-orders],
  [E1], [—], [Any exception propagates as an error `Result`; the account page shows "Try again" (S2)], [—],
)
