#import "../../figures.typ": *

== UC9 Administer Product Listings <sec-a-uc09>

UC9 is the administrator's use case. An administrator prepares a listing, publishes it, corrects it, withdraws it, deletes a draft and sets the team terms and team price that let buyers start teams on it (UC3, @sec-a-uc03). It moves no money, but it protects money: a published listing's price and stock are the basis of quotes and teams that buyers pay into, so they must not change behind those buyers' backs. Two administrators must not silently overwrite each other either. This part follows UC9 from its flows of events through the analysis model and the transition to the design classes, operations, the listing statechart and the patterns. Every class, method and error code named here exists in the code at the freeze commit (report §1.1). UC9 is designed and built; its status on AWS is given by capability in the status table of report §2.2.

=== Use Case and Flows of Events <sec-a-uc09-flows>

- *Actor.* Platform administrator (primary; Admin in the diagrams): a signed-in account that holds the admin role and the permission to change listings.
- *Trigger.* The admin opens the admin console and chooses to create, edit, publish, delist or delete a listing, or to set its team terms or team price.
- *Services.* commerce-svc owns every step (its catalog module, which reads the group-buy module's teams for the delete check). identity-svc answers one question: is this admin's account still active?
- *Preconditions.* The admin is signed in (UC2, @sec-a-uc02), and an operator has granted the account the admin role. For an existing listing, the admin has read its current version.
- *Postconditions on success.* Each accepted change to the listing records exactly one new version of it; a team price set alone (A5) records one new version of the price policy instead. Buyers see a listing only while it is on sale. Team terms and a team price set together are both stored, and the terms change is recorded with its author.
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
  [*A1*], [B5], [The admin edits a published listing (on sale or off sale): text, images and category may change; price, currency and stock may not, because that would change the terms buyers accepted.],
  [*A2*], [B6], [The admin delists an on-sale listing. It becomes _off sale_: hidden from browsing and from new quotes, while its teams, orders and history stay. Publishing it again returns it to on sale with the same identity.],
  [*A3*], [B7], [The listing already has running teams: those teams keep the terms they started with; only new teams use the new terms.],
  [*A4*], [B1], [The admin deletes a draft that was never published, sold nothing and is referred to by no team, quote or price revision, even an expired or deleted one. The system marks it deleted and keeps the record.],
  [*A5*], [B7], [The admin sets or revises the team price alone; the terms and the listing's version are unchanged. The price rules of B7 apply.],
  [*E1*], [B1], [The visitor is not signed in, is not an admin or lacks the permission: the action is refused and nothing is read or changed.],
  [*E2*], [B4–B7, A4, A5], [The admin's account has been disabled, or the system cannot confirm it: the change is refused and nothing is stored.],
  [*E3*], [B4, B5], [A field is missing or out of range: the system lists the field errors and stores nothing.],
  [*E4*], [B4], [The listing number given is already used by another live listing: the system refuses it and stores nothing.],
  [*E5*], [B5–B7, A4], [Another admin, or a purchase, changed the listing after this admin read it: nothing changes, and the console shows the current version with a reload action.],
  [*E6*], [A1], [The edit of a published listing changes its price, currency or stock: the system refuses it.],
  [*E7*], [B6, A2], [The move is not allowed now: publishing a listing that is on sale or has no stock or price, delisting a draft or an off-sale listing, or returning a listing to draft. The state does not change.],
  [*E8*], [B7, A5], [The terms are out of range; or the team price is not below the individual price, its window is too short or already over, the listing is not on sale, or someone saved a newer price revision first. The system lists the reasons and keeps neither terms nor price.],
  [*E9*], [A4], [The listing was published, sold units or is referred to by history: the system refuses and deletes nothing.],
  [*E10*], [B5–B7, A4, A5], [The listing does not exist or is deleted: the system answers "not found".],
)

=== Analysis Class Diagram <sec-a-uc09-ac>

@fig-a-uc09-ac is the analysis model: what UC9 needs, with no platform. The Admin works through `ListingConsoleUI`; `AdministerListingControl` realises UC9 and is the only object that talks to the boundary and to the entities. `Product` owns its lifecycle rules (`publish`, `delist`, `revise`, `checkDeletable`) and carries a `version`, so a command made on an old copy is refused (`checkVersion`). `checkDeletable` covers the listing's state, its sales and its links: a listing with a price revision or a team keeps its history (a quote always comes from a revision). `TeamPricePolicy.latestFor` is a class-scope query. The listing statechart (@sec-a-uc09-states) defines `ProductStatus`.

The entity names are those of the system view (@sec-a-reading). Against the SWE5006 model, the control is renamed after the use case (was `ManageListingControl`), `Listing` becomes `Product`, `AdminAccount` becomes `Account` with a separate `Role`, and the team terms become attributes of `Product`.

#afig("a-uc09-ac", float: true, [UC9 analysis class diagram (UML class notation, BCE stereotypes). The Admin uses `ListingConsoleUI`; `AdministerListingControl` is the only object that talks to both the boundary and the entities, and `Product` owns the lifecycle and version rules.])

=== Analysis Sequence Diagrams <sec-a-uc09-as>

@fig-a-uc09-as-basic is the basic flow B1–B6, with delisting (A2) as an operand of the B6 fragment and E1, E5 and E7 as `break` fragments; B2 only reads, so it is a single return. @fig-a-uc09-as-terms draws B7 with E8, and A4 with E9. A1 and E6 have the shape of B5, and A5 (`setTeamPrice`) that of B7's price part; the design draws both. A3 needs no message: a running team holds its own copy of the terms (UC3). E2, E3, E4 and E10 need design concepts (a remote call, field validation, a unique index, a missing row) and appear only in the design.

#afig("a-uc09-as-basic", [UC9 analysis sequence diagram, basic flow B1–B6 with A2, E1, E5 and E7. The control checks the admin's role through `Account` and the version before every change; `Product` itself decides whether a revision or a move of state is allowed.])

#afig("a-uc09-as-terms", [UC9 analysis sequence diagram, B7 with E8, and A4 with E9. Terms and price succeed or fail together, and every accepted terms change creates a `TeamTermsChange`; a draft is deleted only when no price revision or team refers to it.])

=== Transition: Strategies and Mapping <sec-a-uc09-transition>

The transition strategies of @sec-a-strategies turn the analysis model into the design model. Six of the nine change UC9; S5, S7 and S9 do not:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `AdministerListingControl`, `Product` → resultant `AdminListingAppServiceImpl` and `TeamTermsAppServiceImpl` behind interfaces, each command a `@Transactional(timeout = 10)` unit; an immutable `Product` whose every change returns a new revision; records for the values (`ListingDetails`, `ProductTeamTerms`, `TeamTermsChange`, `TeamPricePolicy`); dynamics @fig-a-uc09-ds-change.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `ListingConsoleUI` → resultant `ListingsPage`, `NewListingPage`, `EditListingPage` with `LifecycleDialog` (in `LifecycleDialogs.tsx`), `TeamTermsPanel` and `ConflictBanner`, through the hooks in `admin-api.ts` (frontend ADR 0015); not drawn.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `Account`, `Team` → resultant port `UserAppService`, which in commerce-svc is `RemoteUserAppService`, an HTTP client of identity-svc (500 ms connect, 1,500 ms read, no retry) that fails closed; the teams stay in commerce-svc and are read in-process through the group-buy module interface `GroupDealQueryService`; dynamics @fig-a-uc09-ds-create.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Product`, `TeamPricePolicy`, `TeamTermsChange`, `PriceQuote`, `Team` → resultant aggregate `Product` with `ProductStatus` and `ProductTeamTerms`, port `ProductRepository`, adapter `ProductRepositoryImpl`, mapper `BizProductMapper`, tables `biz_product` and `log_product_team_terms`; aggregate root `TeamPricePolicy` with its entity `TeamPriceQuote`, both behind `TeamPriceRepository` over `biz_team_price_policy` and `biz_team_price_quote`; soft deletion (`deleted = true`). Aggregates refer to each other by id, so the links of `checkDeletable` become two existence queries (`hasProductReferences`); @fig-a-uc09-dc-product, @fig-a-uc09-ds-delete.
- *S5 External interface (backend ADRs 0001, 0004, 0005; NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66):* not applied; UC9 calls no external provider.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted the role check of `Account` and `Role` → resultant `AdminListingController.administrator()`: Sa-Token `checkLogin`, `checkRole(ADMIN)` and `checkPermission("catalog:product:write")`, with roles and permissions read by `SessionStpInterface` from the login snapshot in Valkey (@sec-a-strategies); the actor id comes from the session; `requireActive` rechecks the account in every transaction; dynamics @fig-a-uc09-ds-create. `TeamPriceController` (A5) checks only the permission, not the role: a known gap, harmless while only the admin role holds that permission (migration V6; @fig-a-uc09-ds-price). The snapshot is written at sign-in, so a role revoked mid-session lasts until the session ends; disabling the account stops writes at once through `requireActive`.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* not applied; no UC9 step is triggered by time.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30; also NFR-29 for the terms):* impacted the `version` of `Product` and the revision of `TeamPricePolicy` → resultant row lock `lockById` (`SELECT … FOR UPDATE`, the only row lock UC9 takes; `create` also takes a transaction-scoped advisory lock for the listing number, which no other path takes, so it stays outside the numbered order), `Product.requireVersion`, version-guarded `UPDATE`s, and a separate policy version checked by `TeamPriceAppServiceImpl`; dynamics @fig-a-uc09-ds-change, @fig-a-uc09-ds-race.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* not applied; UC9 publishes no event.

B7 writes two aggregates in one transaction: the `Product` revision with its terms, optionally a new `TeamPricePolicy` revision, and the audit row. This follows the A.2 S8 justification (@sec-a-strategies): "terms and price succeed or fail together" needs atomicity, both aggregates live in one database, the only lock is the product row, and 10 s bounds the transaction. A5 writes only the policy, in its own transaction under the same lock.

*The account check under the product row lock.* `requireActive` is an HTTP call made while the product row is locked, bounded to about 2 s by its timeouts (AD-06); B7 with a price makes it twice, once in each application service (`TeamTermsAppServiceImpl`, `TeamPriceAppServiceImpl`). The lock comes first to match the quotation writer (`issueQuote` also runs `lockProduct`, then `requireActive`), so every product writer has one shape: row, account, preconditions. The cost falls on the other holders of the product row lock: a settlement's `consumeOneUnit` (UC3, UC4) and a UC3 `issueQuote` on the same listing queue behind an admin write for up to about 4 s, inside their own 10 s timeout (NFR-13). An identity-svc outage makes admin writes fail closed with HTTP 500 (NFR-58). Checking the account before the lock would shorten the hold; it is recorded as future work.

#atable(
  [UC9 analysis → design mapping. Every analysis object of @fig-a-uc09-ac and every design class of @fig-a-uc09-dc-listing to @fig-a-uc09-dc-product (all commerce-svc unless stated).],
  <tbl-a-uc09-map>,
  columns: (1fr, auto, 2.4fr, auto, 0.8fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`ListingConsoleUI`], [boundary], [Controllers `AdminListingController` (`/api/v1/admin/products`) and, for A5, `TeamPriceController` (`PUT /api/v1/products/{productId}/team-price-policy`). Frontend: not drawn (S2).], [S2, S6], [@fig-a-uc09-dc-listing, @fig-a-uc09-dc-terms],
  [`AdministerListingControl`, listing commands], [control], [Entry point `AdminListingAppService` (interface), `AdminListingAppServiceImpl`, record `AdminListingAppService.ManagedListing`.], [S1, S8], [@fig-a-uc09-dc-listing],
  [`AdministerListingControl`, team terms], [control], [Entry point `TeamTermsAppService`, `TeamTermsAppServiceImpl`, records `ConfigureTerms`, `TeamTermsResult`; price part and A5 entry point `TeamPriceAppService`, `TeamPriceAppServiceImpl` (record `ConfigurePolicy` not drawn).], [S1, S8], [@fig-a-uc09-dc-terms],
  [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` (drawn in @fig-a-uc07-dc-invite) calls identity-svc, home of `User` (@sec-a-uc02).], [S3, S6], [@fig-a-uc09-dc-listing],
  [`Role`], [entity], [Session snapshot read by `SessionStpInterface`; role tables in identity-svc. Not drawn (S6).], [S6], [—],
  [`Product`], [entity], [Aggregate root `Product`; `ProductStatus` with three constant bodies (State); value objects `ListingDetails`, `ProductTeamTerms`; domain service `TeamTermsSpecification`; `ProductRepository` → `ProductRepositoryImpl` → `BizProductMapper`; `biz_product`.], [S1, S4, S8], [@fig-a-uc09-dc-product],
  [`TeamPricePolicy`], [entity], [Aggregate root and record `TeamPricePolicy`; port `TeamPriceRepository`, table `biz_team_price_policy`.], [S4, S8], [@fig-a-uc09-dc-terms],
  [`TeamTermsChange`], [entity], [Record `TeamTermsChange`, appended by `ProductRepository.appendTeamTermsLog` to `log_product_team_terms`.], [S4], [@fig-a-uc09-dc-terms],
  [`PriceQuote`], [entity], [No class here: `TeamPriceRepository.hasProductReferences` reads `biz_team_price_quote`; `TeamPriceQuote` is UC3's (@sec-a-uc03).], [S4], [@fig-a-uc09-dc-listing],
  [`Team`], [entity], [Module interface `GroupDealQueryService` (`countOpenDeals`, `hasProductReferences`) over `biz_group_deal`; `GroupDeal` and its statechart are UC5's (@sec-a-uc05-states).], [S3], [@fig-a-uc09-dc-listing],
  [(none)], [—], [By strategy: `RemoteUserAppService` (S3, not drawn here); `ProductRepositoryImpl`, `BizProductMapper` (S4); `SessionStpInterface` (S6, not drawn). Not drawn: the reads `get`, `page`, `pagePolicyRevisions` (shaped like `counts`) and the response records.], [S3, S4, S6], [@fig-a-uc09-dc-product],
)

=== Design Class Diagrams <sec-a-uc09-dc>

The design model is drawn in three slices: the listing commands in the web and application layers (@fig-a-uc09-dc-listing), the team-terms command with its optional price and the price-only controller (@fig-a-uc09-dc-terms), and the `Product` aggregate with its states and the persistence chain (@fig-a-uc09-dc-product). All three run in commerce-svc; only `RemoteUserAppService` reaches another service. A shared class shows only the members its slice uses.

#afig("a-uc09-dc-listing", orient: "landscape", [UC9 design class diagram, slice 1: listing commands (UML, commerce-svc). The controller checks the role and passes only the session's actor id; `AdminListingAppServiceImpl` reaches the account, the teams and the price history only through ports. Inside an enclosing type's box, nested types drop the prefix (`ManagedListing`).])

#afig("a-uc09-dc-terms", [UC9 design class diagram, slice 2: team terms with an optional team price, and the team price alone (UML, commerce-svc). `TeamTermsAppServiceImpl` calls `TeamPriceAppService` inside its own transaction, so a refused price undoes the terms; `TeamPriceController` reaches the same service directly (A5); `TeamPricePolicy` owns the amount and window rules (constructor checks, `requireCompatible`, `requireStartableWindow`); `TeamPriceAppServiceImpl.configure` checks the version and that `validUntil` is in the future.])

`ProductRepository` → `ProductRepositoryImpl` → `BizProductMapper` (@fig-a-uc09-dc-product) is the representative persistence chain, with the two statements @fig-a-uc09-ds-race calls. Every other repository method maps to one statement the same way (`updateTeamTerms` and `deleteDraft` are also version-guarded); `TeamPriceRepository` and the repositories behind `GroupDealQueryService` follow it.

#afig("a-uc09-dc-product", [UC9 design class diagram, slice 3: the `Product` aggregate, its states and the persistence chain (UML, commerce-svc). `Product` delegates its lifecycle rules to its current `ProductStatus`; the three unstereotyped boxes are the enum's constant bodies, which override only what their state allows.])

=== Design Sequence Diagrams <sec-a-uc09-ds>

Each design sequence retells part of @tbl-a-uc09-flows; the step tags link the two. Deletion (A4, at B1) comes first, and notes on the other five follow them. A5 has its own compact sequence, a sixth against the budget of five, because its own transaction cannot be drawn inside B7's without shrinking that figure below 8 pt. A `critical` fragment is a database transaction, read-only ones included. Revise, change status, delete and configure team terms take the product row lock, then check the account, then the product version. Create checks the account, then takes the advisory number lock; the team price alone (A5) checks the policy version.

*Deletion.* In @fig-a-uc09-ds-delete the product decides from its own state and sales; the service then reads the price history and, through the group-buy module interface, the teams, all under the product lock. The guarded `UPDATE` repeats the draft, sales and version conditions and only flags the row.

#afig("a-uc09-ds-delete", [UC9 design sequence diagram, A4 with E9 (commerce-svc). History owned by another module is read through its module interface under the product lock; deletion is a flag on a row that stays.])

#afig("a-uc09-ds-create", [UC9 design sequence diagram, B1–B4 with E1, E2 and E4 (commerce-svc; identity-svc behind `UserAppService`). The role check reads only the session; the account check is a remote call that fails the transaction closed.])

#afig("a-uc09-ds-change", [UC9 design sequence diagram, B5, B6, A1 and A2 with E5, E6, E7 and E10 (commerce-svc). Each response also carries the latest price revision (`withLatestPolicy`, not drawn). The row lock and the version check come before any rule, so a stale request is told to reload rather than that its data is wrong; the current `ProductStatus` decides each move.])

#afig("a-uc09-ds-terms", [UC9 design sequence diagram, B7 with A3 and E8 (commerce-svc). One transaction holds the product row from the terms to the audit row; a refused price rolls the terms back. The policy version is checked before any price rule, `TeamPricePolicy` checks the amount and the window, and `configure` checks that `validUntil` is in the future.])

#afig("a-uc09-ds-price", [UC9 design sequence diagram, A5 with E8: the team price alone (commerce-svc). `TeamPriceController` calls the same `configure` as B7's price part, which now opens its own transaction under the same row lock; the terms and the listing's version are untouched.])

*Creation.* @fig-a-uc09-ds-create shows the role check of every request (E1), the B2 totals in one read-only snapshot, and a new draft (B3–B4). `page` has the same shape (`pageListings` plus one batch `findLatestPolicies`, NFR-45). Field checks (E3) run first: Bean Validation, then the `ListingDetails` constructor. `resolveProductNumber` takes a transaction-scoped advisory lock so a generated and an explicit number cannot collide; the unique index settles a race (E4).

*Revise and change of state.* In @fig-a-uc09-ds-change, `lockForChange` locks the row and checks the account and the version before `Product` asks its state. `revise` keeps the status and builds the next revision itself; only `publish` and `delist` go through `transition`. `changeStatus` never sets a status: it routes the target to `publish()` or `delist()`, and a `DRAFT` target is refused by the request pattern and by the service's `switch`.

*Team terms and price.* `TeamTermsSpecification.violations` in @fig-a-uc09-ds-terms is a class-scope call (no colon). A3 needs no code: a team copies its terms when it starts (UC3). In B7, `TeamPriceAppServiceImpl.configure` joins the terms transaction and re-locks the row, which PostgreSQL grants at once; it checks the policy version, a second optimistic token. In A5 (@fig-a-uc09-ds-price), `TeamPriceController.configure` calls the same method, which then runs in its own transaction.

#afig("a-uc09-ds-race", [UC9 design sequence diagram, E5: two administrators save one listing (commerce-svc). The two saves run in parallel (`par`), each in its own transaction on the same bean; txB waits on the row lock, then fails the version check against v4 and changes nothing.])

*Two administrators.* @fig-a-uc09-ds-race shows E5 with two saves on the same version, drawn through the repository implementation and its mapper. A sale committed between the admin's read and save has the same effect, because `consumeOneUnit` also advances the version. The row lock orders the two transactions; the version check refuses the second without writing. Without the row lock, the guarded `UPDATE … WHERE version = expectedVersion` would still refuse it (`requireChanged` throws when no row changes); the lock makes the refusal happen before any rule runs and keeps the read, check and write of one request consistent.

=== Operation Specifications <sec-a-uc09-ops>

@tbl-a-uc09-ops specifies the six operations that carry UC9's rules. Signatures are those in the code.

#atable(
  [UC9 operation specifications (commerce-svc). Errors are `CatalogErrorCode` constants with the wire code in brackets on first use, unless named otherwise.],
  <tbl-a-uc09-ops>,
  columns: (1.45fr, 0.85fr, 0.75fr, 1fr, 1.5fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`AdminListingController.administrator(): UUID` (private, «static»). No transaction.], [Authorises every admin request (B1); returns the actor id from the session.], [A Sa-Token session cookie.], [No write. In commerce-svc the role and permission lists come from the session snapshot (`SessionStpInterface`), never from SQL.], [`NotLoginException` → `UNAUTHENTICATED` (`COMMON_401_001`); `NotRoleException`, `NotPermissionException` → `FORBIDDEN` (`COMMON_403_001`).],
  [`AdminListingAppServiceImpl.create(productNo: String, details: ListingDetails, actorId: UUID): ManagedListing`. `REQUIRED`, 10 s; advisory lock on the number allocation.], [Stores a new draft (B4). `productNo` may be null.], [`details` already valid; actor authorised.], [One `biz_product` row: `DRAFT`, version 0, `soldCount` 0, pricing mode `DUAL`.], [`ACCOUNT_DISABLED` (`IDENTITY_403_001`), `USER_NOT_FOUND`, `IdentityUnavailableException` (HTTP 500); `PRODUCT_INVALID` (`CATALOG_400_002`) for a bad number; `PRODUCT_NUMBER_CONFLICT` (`CATALOG_409_003`).],
  [`AdminListingAppServiceImpl.revise(id: UUID, details: ListingDetails, expectedVersion: int, actorId: UUID): ManagedListing` and `changeStatus(id: UUID, target: ProductStatus, expectedVersion: int, actorId: UUID): ManagedListing`. `REQUIRED`, 10 s; the product row lock (the only lock of this path) through `lockForChange`, then `Product.requireVersion`.], [Replaces the editable content (B5, A1), or publishes or delists (B6, A2).], [Listing live; `expectedVersion` equals the stored version; `target` is `ON_SALE` or `OFF_SALE`.], [Next revision stored, version + 1; identity, `soldCount` and pricing mode kept; status moved as in @fig-a-uc09-st-listing; quotes and teams untouched.], [`PRODUCT_NOT_FOUND` (`CATALOG_404_001`); `PRODUCT_VERSION_CONFLICT` (`CATALOG_412_002`) with `currentVersion`; `PRODUCT_STATE_CONFLICT` (`CATALOG_409_002`) for a commercial edit after publication, a refused move, a `DRAFT` target or no stock or price; account errors as above.],
  [`AdminListingAppServiceImpl.delete(id: UUID, expectedVersion: int, actorId: UUID): void`. As the row above.], [Soft-deletes a draft (A4).], [As the row above.], [`deleted = true`, version + 1; the row and its history stay; a repeat answers not found.], [`PRODUCT_STATE_CONFLICT` once published; `PRODUCT_REFERENCED` (`CATALOG_409_004`) when units were sold or any policy, quote or team refers to it; others as above.],
  [`TeamTermsAppServiceImpl.configure(productId: UUID, command: TeamTermsAppService.ConfigureTerms, actorId: UUID): TeamTermsAppService.TeamTermsResult`. `REQUIRED`, 10 s; the product row lock only.], [Sets the terms and, if `command.teamPrice` is present, a price (B7).], [Listing live; `expectedVersion` current. Allowed in every state.], [Product version + 1 with the new terms; one `log_product_team_terms` row; with a price, one new policy revision. All or nothing.], [`TEAM_TERMS_INVALID` (`CATALOG_400_003`) with field errors; every error of the next row, with the `teamPrice.` field prefix; `PRODUCT_NOT_FOUND`, `PRODUCT_VERSION_CONFLICT`; account errors.],
  [`TeamPriceAppServiceImpl.configure(productId: UUID, actorId: UUID, command: TeamPriceAppService.ConfigurePolicy): TeamPricePolicy`. `REQUIRED`, 10 s: joins `TeamTermsAppServiceImpl.configure`'s transaction when nested (B7), its own when called by `TeamPriceController` (A5); takes or re-takes the product row lock.], [Appends the next price revision (B7, A5).], [`command.expectedVersion` equals the latest policy version (0 for none).], [One `biz_team_price_policy` row with version + 1; earlier revisions and quotes unchanged.], [`TEAM_PRICE_VERSION_CONFLICT` (`CATALOG_412_001`); `TEAM_PRICE_UNAVAILABLE` (`CATALOG_409_001`) when not on sale; `TEAM_PRICE_INVALID` (`CATALOG_400_001`) for a price not below the listing price, a past or too-short window, or a wrong currency.],
)

=== State Machines <sec-a-uc09-states>

UC9 owns the listing's lifecycle, `Product.status` with the values of `ProductStatus` (@fig-a-uc09-st-listing). Nothing returns to `DRAFT`, only a draft can be deleted, and an event a state does not accept is refused with `PRODUCT_STATE_CONFLICT` and leaves the state unchanged. Every accepted event, including the internal transitions `revise` and `configureTeamTerms`, raises the version by one. Other parts read this state but never change it: UC1 shows only `ON_SALE` listings (@sec-a-uc01), and UC3 and UC4 require `ON_SALE` before a quote or a purchase (@sec-a-uc03, @sec-a-uc04). A sale's settlement takes one unit (`consumeOneUnit`, an internal transition of `ON_SALE`): it changes the stock and the version, not the state, so a purchase can also cause E5. Stock may not change after publication, so a sold-out listing cannot be restocked: it stays `ON_SALE` with nothing to buy, or `OFF_SALE` without a way back (`publish` needs stock), and, being published, it cannot be deleted. The admin creates a new listing instead; this is a known limitation.

#afig("a-uc09-st-listing", [UML state machine of a listing (`Product.status`). Publication needs stock and a price, delisting is reversible, and the only final transition is the soft deletion of a draft; edits, team terms and a sale's `consumeOneUnit` are internal transitions that keep the state and raise the version.])

=== Patterns Applied <sec-a-uc09-patterns>

Two patterns are in the UC9 code; @tbl-a-uc09-patterns maps their roles to classes. Only State is a GoF pattern. §3.5.5 of the SWE5006 report compares the alternatives; this table records how the code wires them today.

#atable(
  [Patterns in the UC9 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc09-patterns>,
  columns: (0.62fr, 1fr, 1.2fr, 1.3fr),
  header: ([Pattern (kind)], [Problem in UC9], [Roles → classes], [Consequences]),
  [State (GoF, behavioural), as an enum with constant-specific bodies], [Four operations of a listing (`publish`, `delist`, `revise`, `requireDeletable`) behave differently, or are refused, depending on its state. Written directly, each needs the same `switch` on the status, and a new state means editing every one.], [Context: `Product` (holds `status`; `publish()`, `delist()`, `revise()`, `requireDeletable()` delegate). State: `ProductStatus`, whose default methods refuse. ConcreteStates: the constant bodies of `DRAFT`, `ON_SALE` and `OFF_SALE`, each overriding only what it allows: `publish` and `delist` return the successor state, and `DRAFT` also overrides `deletionError()` (returns null) and `requireCommercialEdit()`. Client: `AdminListingAppServiceImpl`. @fig-a-uc09-dc-product, @fig-a-uc09-ds-change.], [Each state's rules sit in one place, and one inherited default refuses a forbidden move. The states are shared and stateless, so all listings use the same three objects. Liability: a new state still needs a stored code and a case in the `switch` of `changeStatus`; `Product.publish` checks stock and price outside the states, and `Product` adds the sales check to `deletionError`.],
  [Optimistic Offline Lock with a pessimistic row lock (Fowler, enterprise: offline concurrency)], [An admin's edit spans two requests: read, think, write. A database lock cannot be held across them, and last-write-wins would silently lose a colleague's change to a published price or stock.], [Version: `Product.version` (and the policy version for prices). Check: `Product.requireVersion` under `ProductRepository.lockById` (`SELECT … FOR UPDATE`); guarded writes `BizProductMapper.updateListing`, `updateTeamTerms`, `deleteDraft` (`WHERE version = expectedVersion`), with `ProductRepositoryImpl.requireChanged`. Conflict: 412 (`PRODUCT_VERSION_CONFLICT`), with `currentVersion` when `Product.requireVersion` refuses, without it when `requireChanged` does; `ConflictBanner` offers a reload. @fig-a-uc09-ds-race.], [No lock is held during think time; a conflict is detected, never merged. The short row lock serialises the read, check and write inside one request and takes no second lock, so the global lock order of A.2 S8 is unaffected. Liability: the losing admin re-enters the change; a purchase also advances the version, so editing a busy listing can conflict repeatedly; and the remote account check runs while the row is locked.],
)

`TeamTermsSpecification.violations(targetSize, lifetimeSeconds)` is a Specification (DDD, Evans) in validation form only: a pure, package-private check called by `Product.configureTeamTerms` that reports every violated field in one round trip and shares its bounds with `ProductTeamTerms` (@fig-a-uc09-ds-terms). Liability: it is a static function returning a violation map, not an `isSatisfiedBy` object, so it cannot be combined with other specifications.

=== Flow-to-Design Trace <sec-a-uc09-trace>

@tbl-a-uc09-trace shows that each step of @tbl-a-uc09-flows passes from an analysis message to design operations.

#atable(
  [UC9 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc09-trace>,
  columns: (auto, 0.8fr, 2fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`openConsole`, `Account.mayChangeListings`, `Role.grants`], [`AdminListingController.administrator` (Sa-Token checks, `SessionStpInterface`)], [@fig-a-uc09-ds-create],
  [B2], [return of `openConsole`], [`AdminListingAppService.counts` → `ProductRepository.countListingStatuses`, `GroupDealQueryService.countOpenDeals`, `TeamPriceRepository.countListingsWithActivePolicy`; `page` (not drawn)], [@fig-a-uc09-ds-create],
  [B3], [`enterListing`, `create`], [`AdminListingController.create` → `AdminListingAppService.create`], [@fig-a-uc09-ds-create],
  [B4], [`Account.isActive`; «create» `draft`], [`UserAppService.requireActive` (remote); `ProductRepository.resolveProductNumber`; `Product.draft`; `ProductRepository.insertDraft`], [@fig-a-uc09-ds-create],
  [B5], [`Product.checkVersion`, `revise`], [`AdminListingAppServiceImpl.revise` → `lockForChange` (`lockById`, `requireActive`, `Product.requireVersion`); `Product.revise` → `ProductStatus.requireCommercialEdit`, next revision (`nextVersion`); `updateListing` (mapper, `requireChanged`)], [@fig-a-uc09-ds-change, @fig-a-uc09-ds-race],
  [B6], [`checkVersion`, `publish`], [`AdminListingAppServiceImpl.changeStatus` → `lockForChange`; `Product.publish` → `ProductStatus.publish`, `transition`; `updateListing`], [@fig-a-uc09-ds-change],
  [B7], [`setTeamTerms`; `TeamPricePolicy.latestFor`, «create»; «create» `TeamTermsChange`], [`TeamTermsAppServiceImpl.configure` → `lockById`, `requireActive`, `requireVersion`, `Product.configureTeamTerms` → `TeamTermsSpecification.violations`; `updateTeamTerms`; `TeamPriceAppService.configure` → `lockById`, `requireActive`, `findLatestPolicy`, «create» `TeamPricePolicy`, `requireCompatible`, `requireStartableWindow`, `appendPolicy`; «create» `TeamTermsChange`, `appendTeamTermsLog`], [@fig-a-uc09-ds-terms],
  [B8], [—], [UC3 reads `Product.teamTerms` and the latest policy (@sec-a-uc03)], [—],
  [A1, E6], [`revise`; refused], [`ProductStatus.requireCommercialEdit` (default body) passes when price, currency and stock are unchanged, else `PRODUCT_STATE_CONFLICT`], [@fig-a-uc09-ds-change],
  [A2, E7], [`delist`, `publish`; refused], [`Product.delist` → `ProductStatus.delist` (`ON_SALE` body), `transition`; a default body or `Product.publish`'s stock and price check → `PRODUCT_STATE_CONFLICT`], [@fig-a-uc09-ds-change],
  [A3], [—], [No operation: a team copies the terms when it starts (UC3)], [@fig-a-uc09-ds-terms],
  [A4], [`checkDeletable`, `markDeleted`], [`AdminListingAppServiceImpl.delete` → `lockForChange`; `Product.requireDeletable` → `ProductStatus.deletionError`; `TeamPriceRepository.hasProductReferences`, `GroupDealQueryService.hasProductReferences`; `ProductRepository.deleteDraft`], [@fig-a-uc09-ds-delete],
  [A5], [`setTeamPrice`], [`TeamPriceController.configure` (`checkPermission`) → `TeamPriceAppService.configure` in its own transaction, as in B7], [@fig-a-uc09-ds-price],
  [E1], [`showRefusal`], [`administrator` throws `NotLoginException`, `NotRoleException` or `NotPermissionException` → `UNAUTHENTICATED` or `FORBIDDEN`], [@fig-a-uc09-ds-create],
  [E2], [—], [`RemoteUserAppService.requireActive` throws `ACCOUNT_DISABLED`, `USER_NOT_FOUND` or `IdentityUnavailableException`; the transaction rolls back], [@fig-a-uc09-ds-create],
  [E3], [—], [Bean Validation → `VALIDATION_FAILED` (`COMMON_400_002`); `ListingDetails` constructor → `PRODUCT_INVALID`], [@fig-a-uc09-ds-create],
  [E4], [—], [`ProductRepositoryImpl.insertDraft` → `PRODUCT_NUMBER_CONFLICT`], [@fig-a-uc09-ds-create],
  [E5], [`checkVersion` stale; `showConflict`], [`Product.requireVersion` or `requireChanged` → `PRODUCT_VERSION_CONFLICT` (412; `currentVersion` only from `requireVersion`), after another admin's save or a sale's `consumeOneUnit`], [@fig-a-uc09-ds-change, @fig-a-uc09-ds-race],
  [E8], [`setTeamTerms` or price refused], [`TEAM_TERMS_INVALID`; `TEAM_PRICE_VERSION_CONFLICT`, `TEAM_PRICE_UNAVAILABLE`, `TEAM_PRICE_INVALID` with the `teamPrice.` prefix in B7 (rollback); unprefixed in A5], [@fig-a-uc09-ds-terms, @fig-a-uc09-ds-price],
  [E9], [`checkDeletable` refuses], [`Product.requireDeletable` → `PRODUCT_STATE_CONFLICT` or `PRODUCT_REFERENCED`; a reference → `PRODUCT_REFERENCED`], [@fig-a-uc09-ds-delete],
  [E10], [—], [`lockById` returns empty → `PRODUCT_NOT_FOUND`], [@fig-a-uc09-ds-change],
)
