#import "lib-a.typ": *

== UC9 Administer Product Listings <sec-a-uc09>

UC9 is the administrator's use case. An administrator prepares a listing, publishes it, corrects it, withdraws it, deletes a draft and sets the team terms and team price that let buyers start teams on it (UC3, @sec-a-uc03). It moves no money, but it protects money: a published listing's price and stock are the basis of quotes and teams that buyers pay into, so they must not change behind those buyers' backs. Two administrators must not silently overwrite each other either. This part follows UC9 from its flows of events through the analysis model and the transition to the design classes, operations, the listing statechart and the patterns. Every class, method and error code named here exists in the code on this branch. UC9 is designed and built; the evidence for each flow is listed in section 2.2.

=== Use Case and Flows of Events <sec-a-uc09-flows>

- *Actor.* Admin (primary): a signed-in account that holds the admin role and the permission to change listings.
- *Trigger.* The admin opens the admin console and chooses to create, edit, publish, delist or delete a listing, or to set its team terms.
- *Services.* commerce-svc owns every step (its catalog module, which reads the group-buy module's teams for the delete check). identity-svc answers one question: is this admin's account still active?
- *Preconditions.* The admin is signed in (UC2, @sec-a-uc02), and an operator has granted the account the admin role. For an existing listing, the admin has read its current version.
- *Postconditions on success.* Each accepted change records exactly one new version of the listing. Buyers see a listing only while it is on sale. Team terms and a team price set together are both stored, and the terms change is recorded with its author.
- *Postconditions on refusal.* The listing, its terms and its price history are unchanged. No published listing, and no listing that history refers to, is ever deleted.

@tbl-a-uc09-flows numbers every step. The diagrams below repeat these numbers, and @tbl-a-uc09-trace maps each step to the design operations that realise it.

#atable(
  [UC9 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where an alternate or exception flow branches off.],
  <tbl-a-uc09-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The admin opens the console. On every action the system checks that the admin is signed in, holds the admin role and may change listings.],
  [*B2*], [], [The console shows how many listings are in each state, how many teams are open and how many listings have a team price in force, and a page of listings filtered by state.],
  [*B3*], [], [The admin enters a new listing: title, subtitle, description, images, category, individual price in Singapore dollars and stock, with an optional listing number.],
  [*B4*], [], [The system checks that the admin's account is active, validates the input and stores the listing as a _draft_ at version 0, with a generated number when none was given. Drafts are invisible to buyers.],
  [*B5*], [], [The admin edits the draft and saves it with the version they read. The system checks that the account is active and the version is still current, applies the change and records the next version.],
  [*B6*], [], [The admin publishes the listing. A draft with stock and a price becomes _on sale_ and appears in UC1 (@sec-a-uc01).],
  [*B7*], [], [The admin sets the team terms (a target size of at least two and a team lifetime between 40 minutes and 24 hours) and, optionally, a team price. The system saves terms and price together or not at all. The terms change is recorded with its author, and the price becomes the listing's next price revision.],
  [*B8*], [], [The listing can now start teams (UC3, @sec-a-uc03).],
  [*A1*], [B5], [The admin edits a published listing (on sale or off sale): text, images and category may change; price, currency and stock may not, because that would reprice or restock accepted purchases.],
  [*A2*], [B6], [The admin delists an on-sale listing. It becomes _off sale_: hidden from browsing and from new quotes, while its teams, orders and history stay. Publishing it again returns it to on sale with the same identity.],
  [*A3*], [B7], [The listing already has running teams: those teams keep the terms they started with; only new teams use the new terms.],
  [*A4*], [B1], [The admin deletes a draft that was never published, sold nothing and is referred to by no team, quote or price revision, even an expired or deleted one. The system marks it deleted and keeps the record.],
  [*E1*], [B1], [The visitor is not signed in, is not an admin or lacks the permission: the action is refused and nothing is read or changed.],
  [*E2*], [B4–B7, A4], [The admin's account has been disabled, or the system cannot confirm it: the change is refused and nothing is stored.],
  [*E3*], [B4, B5], [A field is missing or out of range: the system lists the field errors and stores nothing.],
  [*E4*], [B4], [The listing number given is already used by another live listing: the system refuses it and stores nothing.],
  [*E5*], [B5–B7, A4], [Another admin saved the listing after this admin read it: nothing changes, and the console shows the current version with a reload action.],
  [*E6*], [A1], [The edit of a published listing changes its price, currency or stock: the system refuses it.],
  [*E7*], [B6, A2], [The move is not allowed now: publishing a listing that is on sale or has no stock or price, delisting a draft or an off-sale listing, or returning a listing to draft. The state does not change.],
  [*E8*], [B7], [The terms are out of range; or the team price is not below the individual price, its window is too short or already over, the listing is not on sale, or someone saved a newer price revision first. The system lists the reasons and keeps neither terms nor price.],
  [*E9*], [A4], [The listing was published, sold units or is referred to by history: the system refuses and deletes nothing.],
  [*E10*], [B5–B7, A4], [The listing does not exist or is deleted: the system answers "not found".],
)

=== Analysis Class Diagram <sec-a-uc09-ac>

@fig-a-uc09-ac is the analysis model: what UC9 needs, with no platform. The Admin works through `ListingConsoleUI`. `AdministerListingControl` realises UC9 and is the only object that talks to the boundary and to the entities. `Product` owns its lifecycle rules (`publish`, `delist`, `revise`, `checkDeletable`) and carries a `version`, so the model can state that a command made on an old copy is refused (`checkVersion`). `ProductStatus`, `AccountStatus` and `TeamStatus` are enumerations; the listing statechart (@sec-a-uc09-states) defines `ProductStatus`. `checkDeletable` covers the listing's own state and sales and its links: a listing with a linked price revision or team keeps its history. A quote is always issued by a price revision, so a listing with a quote also has a revision. `TeamPricePolicy.latestFor` is a class-scope query: the latest revision of a product.

The entity names are those of the system-level analysis model (@sec-a-reading): `Account`, `Role`, `Product`, `TeamPricePolicy`, `TeamTermsChange`, `PriceQuote` and `Team`. Compared with the SWE5006 analysis model of UC9, the control is renamed after the use case (it was `ManageListingControl`), `Listing` becomes `Product`, `AdminAccount` becomes `Account` with a separate `Role`, and the team terms are attributes of `Product` rather than an entity of their own.

#afig("a-uc09-ac", float: true, [UC9 analysis class diagram (UML class notation, BCE stereotypes). The Admin uses `ListingConsoleUI`; `AdministerListingControl` is the only object that talks to both the boundary and the entities, and `Product` owns the lifecycle and version rules.])

=== Analysis Sequence Diagrams <sec-a-uc09-as>

@fig-a-uc09-as-basic is the basic flow from opening the console to publishing (B1–B6), with delisting (A2) as an operand of the B6 fragment and the refusals E1, E5 and E7 as `break` fragments. B2 only reads, so it is a single return here and is drawn in full in the design. After B4 the account check is not repeated in the drawing; the design repeats it on every write. @fig-a-uc09-as-terms draws B7 with its refusals (E8) and the deletion of a draft (A4, E9). A1 and E6 have the shape of B5 (the listing refuses a commercial edit after publication) and appear in the design sequence. A3 needs no message: a running team holds its own copy of the terms (UC3). E2, E3, E4 and E10 need design concepts (a remote account check, field validation, a unique index, a missing row) and appear only in the design sequences.

#afig("a-uc09-as-basic", float: true, [UC9 analysis sequence diagram, basic flow B1–B6 with A2, E1, E5 and E7. The control checks the admin's role through `Account` and the version before every change; `Product` itself decides whether a revision or a move of state is allowed.])

#afig("a-uc09-as-terms", float: true, [UC9 analysis sequence diagram, B7 with E8, and A4 with E9. Terms and price succeed or fail together, and every accepted terms change creates a `TeamTermsChange`; a draft is deleted only when no price revision or team refers to it.])

=== Transition: Strategies and Mapping <sec-a-uc09-transition>

The transition strategies of @sec-a-strategies turn the analysis model into the design model. Five of the nine change UC9:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `AdministerListingControl`, `Product` → resultant `AdminListingAppServiceImpl` and `TeamTermsAppServiceImpl` behind interfaces, each command a `@Transactional(timeout = 10)` unit; an immutable `Product` whose every change returns a new revision; records for the values (`ListingDetails`, `ProductTeamTerms`, `TeamTermsChange`, `TeamPricePolicy`); dynamics @fig-a-uc09-ds-change.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `ListingConsoleUI` → resultant route components `ListingsPage`, `NewListingPage` and `EditListingPage` with `LifecycleDialogs`, `TeamTermsPanel` and `ConflictBanner`, calling the REST API under `/api/v1/admin/products` through the hooks in `admin-api.ts` (frontend ADR 0015); not drawn.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `Account`, `Team` → resultant port `UserAppService`, which in commerce-svc is `RemoteUserAppService`, an HTTP client of identity-svc (500 ms connect, 1,500 ms read, no retry) that fails closed; the teams stay in commerce-svc and are read in-process through the group-buy facade `GroupDealQueryService`; dynamics @fig-a-uc09-ds-create.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Product`, `TeamPricePolicy`, `TeamTermsChange`, `PriceQuote`, `Team` → resultant aggregate `Product` with `ProductStatus` and `ProductTeamTerms`, port `ProductRepository`, adapter `ProductRepositoryImpl`, mapper `BizProductMapper`, tables `biz_product` and `log_product_team_terms`; `TeamPriceRepository` over `biz_team_price_policy` and `biz_team_price_quote`; soft deletion (`deleted = true`). Aggregates refer to each other by id, so the links that `checkDeletable` follows in analysis become two existence queries made by the application service (`hasProductReferences`); @fig-a-uc09-dc-product, @fig-a-uc09-ds-delete.
- *S5 External interface (AD-06; NFR-21, 22, 23, 31, 50, 66):* not applied; UC9 calls no external provider.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted the role check of `Account` and `Role` → resultant `AdminListingController.administrator()`: Sa-Token `checkLogin`, `checkRole(ADMIN)` and `checkPermission("catalog:product:write")`, with the roles and permissions read by `SessionStpInterface` from the login snapshot in Valkey instead of the identity database (S6 diagram in @sec-a-strategies); the actor id comes from the session, never from the request; `requireActive` rechecks the account inside every transaction; dynamics @fig-a-uc09-ds-create.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* not applied; no UC9 step is triggered by time.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30; also NFR-29 for the terms):* impacted the `version` of `Product` and the revision of `TeamPricePolicy` → resultant row lock `lockById` (`SELECT … FOR UPDATE`, the only lock UC9 takes), `Product.requireVersion`, version-guarded `UPDATE`s, and a separate policy version checked by `TeamPriceAppServiceImpl`; dynamics @fig-a-uc09-ds-change, @fig-a-uc09-ds-race.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* not applied; UC9 publishes no event.

B7 writes two aggregates in one transaction: the `Product` revision with its terms and, optionally, a new `TeamPricePolicy` revision, plus the audit row. This follows the A.2 S8 justification (@sec-a-strategies): the rule "terms and price succeed or fail together" needs atomicity, both aggregates live in commerce-svc's one database, the only lock taken is the product row (so the global lock order is unchanged), and the 10 s timeout bounds the transaction. In the distributed topology, `requireActive` is an HTTP call made while the product row is locked; its timeouts bound that hold to about 2 s per call (AD-06). B7 with a price makes the call twice, once in each service.

@tbl-a-uc09-map shows where each analysis object lands. Every class on the three design class diagrams appears in it.

#atable(
  [UC9 analysis → design mapping. Every analysis object of @fig-a-uc09-ac and every design class of @fig-a-uc09-dc-listing to @fig-a-uc09-dc-product (all commerce-svc unless stated).],
  <tbl-a-uc09-map>,
  columns: (1fr, auto, 2.4fr, auto, 0.8fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`ListingConsoleUI`], [boundary], [Adapter: `AdminListingController` (`/api/v1/admin/products`) with its nested request and response records. Frontend admin console: not drawn (S2).], [S2, S6], [@fig-a-uc09-dc-listing],
  [`AdministerListingControl`, listing commands], [control], [Entry point `AdminListingAppService` (interface), `AdminListingAppServiceImpl`, record `AdminListingAppService.ManagedListing`.], [S1, S8], [@fig-a-uc09-dc-listing],
  [`AdministerListingControl`, team terms], [control], [Entry point `TeamTermsAppService` (interface), `TeamTermsAppServiceImpl`, records `TeamTermsAppService.ConfigureTerms` and `TeamTermsAppService.TeamTermsResult`; price part `TeamPriceAppService`, `TeamPriceAppServiceImpl`, `TeamPriceAppService.ConfigurePolicy` (not drawn: a plain record of the request fields).], [S1, S8], [@fig-a-uc09-dc-terms],
  [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` (commerce-svc) calls identity-svc. The `User` aggregate lives in identity-svc (@sec-a-uc02).], [S3, S6], [@fig-a-uc09-dc-listing],
  [`Role`], [entity], [Roles and permissions in the session snapshot, read by `SessionStpInterface`; the role tables live in identity-svc. Not drawn here (S6 diagram in @sec-a-strategies).], [S6], [—],
  [`Product`], [entity], [Aggregate root `Product`; `ProductStatus` with three constant bodies (State, @sec-a-uc09-patterns); value objects `ListingDetails`, `ProductTeamTerms`; utility `TeamTermsSpecification`. Port `ProductRepository`, adapter `ProductRepositoryImpl`, mapper `BizProductMapper`, table `biz_product`.], [S1, S4, S8], [@fig-a-uc09-dc-product],
  [`TeamPricePolicy`], [entity], [Aggregate root and record `TeamPricePolicy`; port `TeamPriceRepository`, table `biz_team_price_policy`.], [S4, S8], [@fig-a-uc09-dc-terms],
  [`TeamTermsChange`], [entity], [Record `TeamTermsChange`, appended by `ProductRepository.appendTeamTermsLog` to `log_product_team_terms`.], [S4], [@fig-a-uc09-dc-terms],
  [`PriceQuote`], [entity], [No class in UC9: `TeamPriceRepository.hasProductReferences` reads `biz_team_price_quote`; the `TeamPriceQuote` record belongs to UC3 (@sec-a-uc03).], [S4], [@fig-a-uc09-dc-listing],
  [`Team`], [entity], [Facade `GroupDealQueryService` (`countOpenDeals`, `hasProductReferences`) over `biz_group_deal`; the `GroupDeal` aggregate belongs to UC4 and UC5 (@sec-a-uc05).], [S3], [@fig-a-uc09-dc-listing],
  [(none)], [—], [Introduced by a strategy, with no analysis counterpart: `RemoteUserAppService` (S3); `ProductRepositoryImpl`, `BizProductMapper` (S4); `SessionStpInterface` (S6, not drawn). Not drawn: the reads `get`, `page` and `pagePolicyRevisions` (same shape as `counts`), and the response records.], [S3, S4, S6], [@fig-a-uc09-dc-product],
)

=== Design Class Diagrams <sec-a-uc09-dc>

The design model is drawn in three slices: the listing commands in the web and application layers (@fig-a-uc09-dc-listing), the team-terms command with its optional price (@fig-a-uc09-dc-terms), and the `Product` aggregate with its states and the persistence chain (@fig-a-uc09-dc-product). All three run in commerce-svc; the only class that reaches another service is `RemoteUserAppService`. A class shared by two slices shows only the members that slice uses. An association means a field reference; a dependency means a parameter, return value or created object. Inside the box of an enclosing type, its nested types are written without the prefix (`ManagedListing` in `AdminListingAppService`, `CreateListingRequest` in `AdminListingController`). The kit cannot underline, so class-scope (static) members end in «static».

#afig("a-uc09-dc-listing", orient: "landscape", [UC9 design class diagram, slice 1: listing commands (UML, commerce-svc). The controller checks the role and passes only the session's actor id; `AdminListingAppServiceImpl` reaches the account, the teams and the price history only through ports.])

#afig("a-uc09-dc-terms", float: true, [UC9 design class diagram, slice 2: team terms with an optional team price (UML, commerce-svc). `TeamTermsAppServiceImpl` calls `TeamPriceAppService` inside its own transaction, so a refused price undoes the terms; `TeamPricePolicy` owns every price rule.])

`ProductRepository` → `ProductRepositoryImpl` → `BizProductMapper` in @fig-a-uc09-dc-product is the representative persistence chain, shown with the two statements that @fig-a-uc09-ds-race calls. Every other repository method maps to one mapper statement in the same way (`updateTeamTerms` and `deleteDraft` are also guarded by the version), and `TeamPriceRepository` and the group-buy repositories behind `GroupDealQueryService` follow the same shape.

#afig("a-uc09-dc-product", float: true, [UC9 design class diagram, slice 3: the `Product` aggregate, its states and the persistence chain (UML, commerce-svc). `Product` delegates its lifecycle rules to its current `ProductStatus`; the three unstereotyped boxes are the enum's constant bodies, which override only what their state allows.])

=== Design Sequence Diagrams <sec-a-uc09-ds>

Each design sequence retells part of @tbl-a-uc09-flows with the classes above; the step tags link the two. A `critical` fragment is a database transaction, labelled with its propagation, isolation and timeout; the read-only reads of B2 are labelled in a note instead. Every write takes the product row lock first, then checks the account and the version, in that order.

*Authorisation, console and creation.* @fig-a-uc09-ds-create shows the role check of every request (E1), the console totals of B2 in one read-only `REPEATABLE READ` snapshot, and the creation of a draft (B3–B4). The page of listings (`page`) has the same shape: one `pageListings` query and one batch `findLatestPolicies` query (NFR-45). Field checks (E3) run before the method: Bean Validation on the request records, then the `ListingDetails` constructor. `resolveProductNumber` takes an allocation lock so that a generated number and an explicit one cannot collide; the unique index on live listing numbers settles a race (E4).

#afig("a-uc09-ds-create", float: true, [UC9 design sequence diagram, B1–B4 with E1, E2 and E4 (commerce-svc; identity-svc behind `UserAppService`). The role check reads only the session; the account check is a remote call that fails the transaction closed.])

*Revise and change of state.* @fig-a-uc09-ds-change shows that `revise` and `changeStatus` share one shape: `lockForChange` locks the row, checks the account and the version, and only then asks `Product`, which asks its state. `changeStatus` never sets a status: it routes the target to `publish()` or `delist()`. A target of `DRAFT` is refused twice, by the request pattern (`on_sale` or `off_sale` only) and by the service's `switch`.

#afig("a-uc09-ds-change", float: true, [UC9 design sequence diagram, B5, B6, A1 and A2 with E5, E6, E7 and E10 (commerce-svc). Each response also carries the latest price revision (`withLatestPolicy`, not drawn). The row lock and the version check come before any rule, so a stale request is told to reload rather than that its data is wrong; the current `ProductStatus` decides each move.])

*Deletion.* @fig-a-uc09-ds-delete shows A4. The product decides from its own state and sales; the application service then reads the price history in catalog and the teams through the group-buy facade, all under the product lock. The guarded `UPDATE` repeats the draft, sales and version conditions, and it only flags the row.

#afig("a-uc09-ds-delete", float: true, [UC9 design sequence diagram, A4 with E9 (commerce-svc). History owned by another module is read through its facade under the product lock; deletion is a flag on a row that stays.])

*Team terms and price.* @fig-a-uc09-ds-terms shows B7. `TeamTermsSpecification.violations` is a class-scope call, so its lifeline has no colon. A3 needs no code: a team copies its target size and deadline when it starts (UC3), and nothing here touches `biz_group_deal`. @fig-a-uc09-ds-price is the optional price inside the same transaction. `TeamPriceAppServiceImpl.configure` locks the product row again, which PostgreSQL grants at once because the transaction already holds it, and checks the policy version, a second optimistic token independent of the product's.

#afig("a-uc09-ds-terms", float: true, [UC9 design sequence diagram, B7 with A3 and E8 (commerce-svc). One transaction holds the product row from the terms to the audit row; a refused price is rethrown with a field prefix and rolls the terms back.])

#afig("a-uc09-ds-price", float: true, [UC9 design sequence diagram, B7 with E8: the team price revision (commerce-svc). The policy version is checked before any price rule, and `TeamPricePolicy` owns every rule on the amount and the window.])

*Two administrators.* @fig-a-uc09-ds-race shows E5 with two saves on the same version, drawn through the repository implementation and its mapper. The row lock orders the two transactions; the version check refuses the second without writing. Without the row lock, the guarded `UPDATE … WHERE version = expectedVersion` would still refuse it (`requireChanged` throws when no row changes); the lock makes the refusal happen before any rule runs and keeps the read, check and write of one request consistent.

#afig("a-uc09-ds-race", float: true, [UC9 design sequence diagram, E5: two administrators save one listing (commerce-svc). txA and txB are two concurrent invocations of the same bean; the second waits on the row lock, then fails the version check and changes nothing.])

=== Operation Specifications <sec-a-uc09-ops>

@tbl-a-uc09-ops specifies the six operations that carry UC9's rules. Signatures are those in the code.

#atable(
  [UC9 operation specifications (commerce-svc). Errors are `CatalogErrorCode` constants with the wire code in brackets on first use, unless named otherwise.],
  <tbl-a-uc09-ops>,
  columns: (1.5fr, 1fr, 1fr, 1.15fr, 1.15fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`AdminListingController.administrator(): UUID` (private, «static»). No transaction.], [Authorises every admin request (B1); returns the actor id from the session.], [A Sa-Token session cookie.], [No write. In commerce-svc the role and permission lists come from the session snapshot (`SessionStpInterface`), never from SQL.], [`NotLoginException` → `UNAUTHENTICATED` (`COMMON_401_001`); `NotRoleException`, `NotPermissionException` → `FORBIDDEN` (`COMMON_403_001`).],
  [`AdminListingAppServiceImpl.create(productNo: String, details: ListingDetails, actorId: UUID): ManagedListing`. `REQUIRED`, 10 s; number-allocation lock.], [Stores a new draft (B4). `productNo` may be null.], [`details` already valid; actor authorised.], [One `biz_product` row: `DRAFT`, version 0, `soldCount` 0, pricing mode `DUAL`.], [`ACCOUNT_DISABLED` (`IDENTITY_403_001`), `USER_NOT_FOUND`, `IdentityUnavailableException` (HTTP 500); `PRODUCT_INVALID` (`CATALOG_400_002`) for a bad number; `PRODUCT_NUMBER_CONFLICT` (`CATALOG_409_003`).],
  [`AdminListingAppServiceImpl.revise(id: UUID, details: ListingDetails, expectedVersion: int, actorId: UUID): ManagedListing` and `changeStatus(id: UUID, target: ProductStatus, expectedVersion: int, actorId: UUID): ManagedListing`. `REQUIRED`, 10 s; lock 1 (product row) through `lockForChange`, then `Product.requireVersion`.], [Replaces the editable content (B5, A1), or publishes or delists (B6, A2).], [Listing live; `expectedVersion` equals the stored version; `target` is `ON_SALE` or `OFF_SALE`.], [Next revision stored, version + 1; identity, `soldCount` and pricing mode kept; status moved as in @fig-a-uc09-st-listing; quotes and teams untouched.], [`PRODUCT_NOT_FOUND` (`CATALOG_404_001`); `PRODUCT_VERSION_CONFLICT` (`CATALOG_412_002`) with `currentVersion`; `PRODUCT_STATE_CONFLICT` (`CATALOG_409_002`) for a commercial edit after publication, a refused move, a `DRAFT` target or no stock or price; account errors as above.],
  [`AdminListingAppServiceImpl.delete(id: UUID, expectedVersion: int, actorId: UUID): void`. As the row above.], [Soft-deletes a draft (A4).], [As the row above.], [`deleted = true`, version + 1; the row and its history stay; a repeat answers not found.], [`PRODUCT_STATE_CONFLICT` once published; `PRODUCT_REFERENCED` (`CATALOG_409_004`) when units were sold or any policy, quote or team refers to it; others as above.],
  [`TeamTermsAppServiceImpl.configure(productId: UUID, command: TeamTermsAppService.ConfigureTerms, actorId: UUID): TeamTermsAppService.TeamTermsResult`. `REQUIRED`, 10 s; lock 1 only.], [Sets the terms and, if `command.teamPrice` is present, a price (B7).], [Listing live; `expectedVersion` current. Allowed in every state.], [Product version + 1 with the new terms; one `log_product_team_terms` row; with a price, one new policy revision. All or nothing.], [`TEAM_TERMS_INVALID` (`CATALOG_400_003`) with field errors; every error of the next row, with the `teamPrice.` field prefix; `PRODUCT_NOT_FOUND`, `PRODUCT_VERSION_CONFLICT`; account errors.],
  [`TeamPriceAppServiceImpl.configure(productId: UUID, actorId: UUID, command: TeamPriceAppService.ConfigurePolicy): TeamPricePolicy`. Joins the caller's transaction (10 s); re-takes lock 1.], [Appends the next price revision (B7).], [`command.expectedVersion` equals the latest policy version (0 for none).], [One `biz_team_price_policy` row with version + 1; earlier revisions and quotes unchanged.], [`TEAM_PRICE_VERSION_CONFLICT` (`CATALOG_412_001`); `TEAM_PRICE_UNAVAILABLE` (`CATALOG_409_001`) when not on sale; `TEAM_PRICE_INVALID` (`CATALOG_400_001`) for a price not below the listing price, a past or too-short window, or a wrong currency.],
)

=== State Machines <sec-a-uc09-states>

UC9 owns the listing's lifecycle, `Product.status` with the values of `ProductStatus` (@fig-a-uc09-st-listing). Nothing returns to `DRAFT`, only a draft can be deleted, and an event a state does not accept is refused with `PRODUCT_STATE_CONFLICT` and leaves the state unchanged. Every accepted event, including the internal transitions `revise` and `configureTeamTerms`, raises the version by one. Other parts read this state but never change it: UC1 shows only `ON_SALE` listings (@sec-a-uc01), and UC3 and UC4 require `ON_SALE` before a quote or a purchase (@sec-a-uc03, @sec-a-uc04). Taking one unit of stock on a purchase (`consumeOneUnit`, UC3 and UC4) changes the stock, not the state.

#afig("a-uc09-st-listing", float: true, [UML state machine of a listing (`Product.status`). Publication needs stock and a price, delisting is reversible, and the only final transition is the soft deletion of a draft; edits and team terms are internal transitions that keep the state and raise the version.])

=== Patterns Applied <sec-a-uc09-patterns>

Three patterns are in the UC9 code; @tbl-a-uc09-patterns maps their roles to classes. Only State is a GoF pattern. The SWE5006 report (section 3.5.5) compares the alternatives; this table records how the code wires them today.

#atable(
  [Patterns in the UC9 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc09-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC9], [Roles → classes], [Consequences]),
  [State (GoF, behavioural), as an enum with constant-specific bodies], [Four operations of a listing (`publish`, `delist`, `revise`, `requireDeletable`) behave differently, or are refused, depending on its state. Written directly, each needs the same `switch` on the status, and a new state means editing every one.], [Context: `Product` (holds `status`; `publish()`, `delist()`, `revise()`, `requireDeletable()` delegate). State: `ProductStatus`, whose default methods refuse. ConcreteStates: the constant bodies of `DRAFT`, `ON_SALE` and `OFF_SALE`, each overriding only what it allows and returning its successor. Client: `AdminListingAppServiceImpl`. @fig-a-uc09-dc-product, @fig-a-uc09-ds-change.], [Each state's rules sit in one place, and one inherited default refuses a forbidden move. The states are shared and stateless, so all listings use the same three objects. Liability: a new state still needs a stored code and a case in the `switch` of `changeStatus`; `Product.publish` checks stock and price outside the states, and `Product` adds the sales check to `deletionError`.],
  [Optimistic Offline Lock with a pessimistic row lock (Fowler, enterprise: offline concurrency)], [An admin's edit spans two requests: read, think, write. A database lock cannot be held across them, and last-write-wins would silently lose a colleague's change to a published price or stock.], [Version: `Product.version` (and the policy version for prices). Check: `Product.requireVersion` under `ProductRepository.lockById` (`SELECT … FOR UPDATE`); guarded writes `BizProductMapper.updateListing`, `updateTeamTerms`, `deleteDraft` (`WHERE version = expectedVersion`), with `ProductRepositoryImpl.requireChanged`. Conflict: 412 with `currentVersion`; `ConflictBanner` offers a reload. @fig-a-uc09-ds-race.], [No lock is held during think time; a conflict is detected, never merged. The short row lock serialises the read, check and write inside one request and takes no second lock, so the global lock order of A.2 S8 is unaffected. Liability: the losing admin re-enters the change, and the remote account check runs while the row is locked.],
  [Specification (DDD, Evans), validation form only], [The team-terms bounds must be checked in one place and report every violated field in one round trip, while the price rules stay in `TeamPricePolicy`.], [Specification: `TeamTermsSpecification.violations(targetSize, lifetimeSeconds)`, package-private and pure; candidate: the proposed terms; caller: `Product.configureTeamTerms`. @fig-a-uc09-ds-terms.], [The bounds have one home, shared with `ProductTeamTerms.configured()` through its constants. Liability: it is a static function returning a violation map, not an `isSatisfiedBy` object, so it cannot be combined with other specifications.],
)

=== Flow-to-Design Trace <sec-a-uc09-trace>

@tbl-a-uc09-trace shows that each step of @tbl-a-uc09-flows passes from an analysis message to design operations.

#atable(
  [UC9 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc09-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`openConsole`, `Account.mayChangeListings`, `Role.grants`], [`AdminListingController.administrator` (Sa-Token checks, `SessionStpInterface`)], [@fig-a-uc09-ds-create],
  [B2], [return of `openConsole`], [`AdminListingAppService.counts` → `ProductRepository.countListingStatuses`, `GroupDealQueryService.countOpenDeals`, `TeamPriceRepository.countListingsWithActivePolicy`; `page` → `pageListings`, `findLatestPolicies` (not drawn)], [@fig-a-uc09-ds-create],
  [B3], [`enterListing`, `create`], [`AdminListingController.create` → `AdminListingAppService.create`], [@fig-a-uc09-ds-create],
  [B4], [`Account.isActive`; «create» `draft`], [`UserAppService.requireActive` (remote); `ProductRepository.resolveProductNumber`; `Product.draft`; `ProductRepository.insertDraft`], [@fig-a-uc09-ds-create],
  [B5], [`Product.checkVersion`, `revise`], [`AdminListingAppServiceImpl.revise` → `lockForChange` (`lockById`, `requireActive`, `Product.requireVersion`); `Product.revise` → `ProductStatus.requireCommercialEdit`, `transition`; `ProductRepository.updateListing` (`BizProductMapper.updateListing`, `requireChanged`)], [@fig-a-uc09-ds-change, @fig-a-uc09-ds-race],
  [B6], [`checkVersion`, `publish`], [`AdminListingAppServiceImpl.changeStatus` → `lockForChange`; `Product.publish` → `ProductStatus.publish`; `updateListing`], [@fig-a-uc09-ds-change],
  [B7], [`setTeamTerms`; `TeamPricePolicy.latestFor`, «create»; «create» `TeamTermsChange`], [`TeamTermsAppServiceImpl.configure` → `lockById`, `requireActive`, `requireVersion`, `Product.configureTeamTerms` → `TeamTermsSpecification.violations`; `updateTeamTerms`; `TeamPriceAppService.configure` → `findLatestPolicy`, `currentTime`, «create» `TeamPricePolicy`, `requireCompatible`, `requireStartableWindow`, `appendPolicy`; «create» `TeamTermsChange`, `appendTeamTermsLog`], [@fig-a-uc09-ds-terms, @fig-a-uc09-ds-price],
  [B8], [—], [UC3 reads `Product.teamTerms` and the latest policy (@sec-a-uc03)], [—],
  [A1], [`revise`], [`ProductStatus.requireCommercialEdit` (default body, used by `ON_SALE` and `OFF_SALE`) passes when price, currency and stock are unchanged], [@fig-a-uc09-ds-change],
  [A2], [`delist`], [`Product.delist` → `ProductStatus.delist` (`ON_SALE` body)], [@fig-a-uc09-ds-change],
  [A3], [—], [No operation: a team copies the terms when it starts (UC3)], [@fig-a-uc09-ds-terms],
  [A4], [`checkDeletable`, `markDeleted`], [`AdminListingAppServiceImpl.delete` → `lockForChange`; `Product.requireDeletable` → `ProductStatus.deletionError`; `TeamPriceRepository.hasProductReferences`, `GroupDealQueryService.hasProductReferences`; `ProductRepository.deleteDraft`], [@fig-a-uc09-ds-delete],
  [E1], [`showRefusal`], [`administrator` throws `NotLoginException`, `NotRoleException` or `NotPermissionException` → `UNAUTHENTICATED` or `FORBIDDEN`], [@fig-a-uc09-ds-create],
  [E2], [—], [`RemoteUserAppService.requireActive` throws `ACCOUNT_DISABLED`, `USER_NOT_FOUND` or `IdentityUnavailableException`; the transaction rolls back], [@fig-a-uc09-ds-create],
  [E3], [—], [Bean Validation → `VALIDATION_FAILED` (`COMMON_400_002`); `ListingDetails` constructor → `PRODUCT_INVALID`], [@fig-a-uc09-ds-create],
  [E4], [—], [`ProductRepositoryImpl.insertDraft` → `PRODUCT_NUMBER_CONFLICT`], [@fig-a-uc09-ds-create],
  [E5], [`checkVersion` stale; `showConflict`], [`Product.requireVersion` or `requireChanged` → `PRODUCT_VERSION_CONFLICT` (412, `currentVersion`)], [@fig-a-uc09-ds-change, @fig-a-uc09-ds-race],
  [E6], [`revise` refused], [`ProductStatus.requireCommercialEdit` → `PRODUCT_STATE_CONFLICT`], [@fig-a-uc09-ds-change],
  [E7], [`publish` or `delist` refused], [`ProductStatus.publish` or `delist` default, or the stock and price check in `Product.publish` → `PRODUCT_STATE_CONFLICT`], [@fig-a-uc09-ds-change],
  [E8], [`setTeamTerms` or price refused], [`TEAM_TERMS_INVALID`; `TEAM_PRICE_VERSION_CONFLICT`, `TEAM_PRICE_UNAVAILABLE`, `TEAM_PRICE_INVALID` with the `teamPrice.` prefix; rollback], [@fig-a-uc09-ds-terms, @fig-a-uc09-ds-price],
  [E9], [`checkDeletable` refuses], [`Product.requireDeletable` → `PRODUCT_STATE_CONFLICT` or `PRODUCT_REFERENCED`; a reference found → `PRODUCT_REFERENCED`], [@fig-a-uc09-ds-delete],
  [E10], [—], [`lockById` returns empty → `PRODUCT_NOT_FOUND`], [@fig-a-uc09-ds-change],
)
