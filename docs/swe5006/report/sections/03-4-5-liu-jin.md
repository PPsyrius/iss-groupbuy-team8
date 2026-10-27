<!-- report
rubric: 3.4.5, 3.4-A..E, G4
owner: Liu Jin
plan: #678
sources: backend/docs/design/admin-listings.md
owns: R17, FG01, FG02, FG03, FG07
status: draft
-->

### 3.4.5 UC9 Admin Listing Management and UC5 Team Lifecycle Resolution by Liu Jin

This section models two use cases. In UC9 Admin listing management, an administrator prepares, publishes, revises, withdraws and deletes catalog listings and sets their team terms. In UC5 Team lifecycle resolution, an open team is resolved exactly once: it succeeds when its last seat is paid for, or it expires when its deadline passes first. Both use cases have parts A to E. Their flows of events are in 3.3.10 (UC9) and 3.3.6 (UC5).

The listing statechart belongs with the State pattern in 3.5.5. The team statechart (OPEN, SUCCEEDED, EXPIRED) is drawn in this section, after part D.

Liu Jin identified the six major flows of the two use cases and analysed them. In UC9 one control serves every administrator command, and the stale-version case became a flow of its own because the administrator must act to recover from it. In UC5 the team alone owns its transition rule, so no control decides a move. In design, team terms and a team price were placed in one command so that they succeed or fail together, and the expiry was split into a selection step and a one-team-per-transaction step whose consequences are event subscribers (code credits: chapter 5).

#### A Analysis class diagram

**UC9.** One control, `ManageListingControl`, carries every administrator command (Figure 3.98). The `Listing` entity holds a `version`, so the model can state the rule that a command made on an old copy of the listing is refused. A listing that has any team, price policy or quote in its history keeps that history. For this reason the control reads `Team` and `PriceQuote` before it deletes anything. `AdminAccount` is the administrator's own account: signing in with Google is not enough, the account must also hold the admin role and be active.

```mermaid
classDiagram
    class ListingConsoleUI {
        <<boundary>>
        +openListing(listing)
        +saveDetails(details)
        +changeStatus(target)
        +deleteDraft()
        +setTeamTerms(terms)
        +showConflict(currentVersion)
        +showRefusal(reason)
    }
    class ManageListingControl {
        <<control>>
        +revise(listing, details, version)
        +changeStatus(listing, target, version)
        +delete(listing, version)
        +setTeamTerms(listing, terms, version)
    }
    class AdminAccount {
        <<entity>>
        -role: Role
        -active: Boolean
        +checkActiveAdmin()
    }
    class Listing {
        <<entity>>
        -title: String
        -originalPrice: Money
        -stock: Integer
        -soldCount: Integer
        -status: ListingStatus
        -version: Integer
        -teamSize: Integer
        -teamLifetime: Duration
        +checkVersion(version)
        +revise(details) Listing
        +publish() Listing
        +delist() Listing
        +checkDeletable()
        +setTeamTerms(size, lifetime) Listing
    }
    class TeamPricePolicy {
        <<entity>>
        -teamPrice: Money
        -validFrom: DateTime
        -validUntil: DateTime
        -version: Integer
    }
    class PriceQuote {
        <<entity>>
        -price: Money
        -expireTime: DateTime
    }
    class TeamTermsChange {
        <<entity>>
        -oldTerms: TeamTerms
        -newTerms: TeamTerms
        -changedBy: AdminAccount
    }
    class Team {
        <<entity>>
        -teamPrice: Money
        -targetSize: Integer
        -status: TeamStatus
    }
    %% at ListingConsoleUI 1,0
    %% at ManageListingControl 1,1
    %% at AdminAccount 0,2
    %% at Listing 1,2
    %% at TeamTermsChange 2,1
    %% at TeamPricePolicy 0,3
    %% at PriceQuote 1,4
    %% at Team 2,3
    ListingConsoleUI "1" -- "1" ManageListingControl
    ManageListingControl --> AdminAccount
    ManageListingControl --> Listing
    ManageListingControl ..> TeamTermsChange : «create»
    Listing -- TeamTermsChange
    Listing "1" -- "0..*" TeamPricePolicy
    Listing "1" -- "0..*" PriceQuote
    Listing "1" -- "0..*" Team
```

Figure 3.98: Analysis class diagram of UC9 Admin listing management (UML). One control serves every command; the version on `Listing` carries the stale-copy rule, and teams, policies and quotes are the history that blocks deletion.

**UC5.** The team resolves itself on one of two triggers (Figure 3.99). The time-driven Scheduler actor reaches the system through the `SchedulerTrigger` boundary. The last seat arrives through UC4's `PaymentGateway` and `JoinTeamControl` (3.4.1), shown here only as the entry point. `ResolveTeamControl` applies the outcome: it confirms the team's orders on success; on expiry it cancels them and creates one refund obligation per paid member, which UC6 then pays out (3.4.2). The `Team` entity owns the transition rule `canMoveTo`, so no control decides alone whether a team may change state.

```mermaid
classDiagram
    class SchedulerTrigger {
        <<boundary>>
        +tick(now)
    }
    class PaymentGateway {
        <<boundary>>
        +paymentConfirmed(ref, amount)
    }
    class JoinTeamControl {
        <<control>>
        +paymentConfirmed(ref, amount)
    }
    class ResolveTeamControl {
        <<control>>
        +teamFilled(team)
        +expireDue(now)
    }
    class Team {
        <<entity>>
        -targetSize: Integer
        -currentSize: Integer
        -expireTime: DateTime
        -successTime: DateTime
        -status: TeamStatus
        +admit(buyer, amount, now) Admission
        +expire(now) Team
        +canMoveTo(status) Boolean
    }
    class Membership {
        <<entity>>
        -role: MemberRole
        -joinTime: DateTime
    }
    class Order {
        <<entity>>
        -amount: Money
        -status: OrderStatus
        +confirm()
        +cancel(at)
    }
    class Refund {
        <<entity>>
        -amount: Money
        -status: RefundStatus
    }
    %% at PaymentGateway 0,0
    %% at SchedulerTrigger 2,0
    %% at JoinTeamControl 0,1
    %% at ResolveTeamControl 2,1
    %% at Team 1,2
    %% at Order 2,2
    %% at Membership 1,3
    %% at Refund 2,3
    PaymentGateway "1" -- "1" JoinTeamControl
    SchedulerTrigger "1" -- "1" ResolveTeamControl
    JoinTeamControl --> Team
    JoinTeamControl --> ResolveTeamControl : last seat
    ResolveTeamControl --> Team
    ResolveTeamControl --> Order
    ResolveTeamControl ..> Refund : «create»
    Team "1" *-- "1..*" Membership
    Team "1" -- "1..*" Order
    Membership "1" -- "0..1" Refund
```

Figure 3.99: Analysis class diagram of UC5 Team lifecycle resolution (UML). Two boundaries feed one resolution control, and `Team` alone decides whether it may move; `PaymentGateway` and `JoinTeamControl` come from UC4.

#### B Analysis sequence diagrams

There are six major flows: four for UC9 and two for UC5. Exceptional flows are `alt` fragments inside their major flow, except the stale-version case: it gets its own flow (B2) because the administrator has to act to recover from it.

| Flow | Use case | Analysis | Design |
| --- | --- | --- | --- |
| B1/D1 Publish a listing | UC9 | Figure 3.100 | Figure 3.109 |
| B2/D2 Revise with a stale version, reload and retry | UC9 | Figure 3.101 | Figure 3.110 |
| B3/D3 Delete a draft | UC9 | Figure 3.102 | Figure 3.111 |
| B4/D4 Set team terms and an optional team price | UC9 | Figure 3.103 | Figure 3.112 |
| B5/D5 The last seat fills the team: OPEN to SUCCEEDED | UC5 | Figure 3.104 | Figure 3.113 |
| B6/D6 The deadline passes: OPEN to EXPIRED | UC5 | Figure 3.105 | Figure 3.114 |

**B1 Publish a listing.** Publishing a draft or an off-sale listing puts it on sale. The listing itself refuses the move when it is already on sale or has no stock or price.

```mermaid
sequenceDiagram
    actor Admin
    participant UI as ListingConsoleUI (boundary)
    participant C as ManageListingControl (control)
    participant A as AdminAccount (entity)
    participant L as Listing (entity)
    Admin->>UI: changeStatus(on sale)
    UI->>C: changeStatus(listing, on sale, version)
    C->>A: checkActiveAdmin()
    C->>L: checkVersion(version)
    C->>L: publish()
    alt draft or off sale, stock and price positive
        L-->>C: on-sale revision, version + 1
        C-->>UI: saved revision
        UI-->>Admin: listing is on sale
    else already on sale, or no stock or price
        L-->>C: refused
        C-->>UI: state conflict
        UI-->>Admin: showRefusal(reason)
    end
```

Figure 3.100: Analysis sequence diagram of UC9 flow B1, publish a listing (UML). The listing decides whether it may go on sale; the control only checks the account and the version first.

**B2 Revise with a stale version, then reload and retry.** Two administrators can open the same listing. The one who saves second holds an old version, and the listing refuses the save without changing anything. Nothing merges or retries on its own: the administrator reloads the listing, which drops the unsaved edits, and saves again at the new version.

```mermaid
sequenceDiagram
    actor Admin
    participant UI as ListingConsoleUI (boundary)
    participant C as ManageListingControl (control)
    participant L as Listing (entity)
    Note over UI,L: Admin loaded version v, then a colleague saved v + 1
    Admin->>UI: saveDetails(details)
    UI->>C: revise(listing, details, v)
    C->>L: checkVersion(v)
    L-->>C: stale, current is v + 1
    C-->>UI: version conflict, current v + 1
    UI-->>Admin: showConflict(v + 1)
    Admin->>UI: openListing(listing)
    UI->>C: read(listing)
    C-->>UI: listing at v + 1
    Admin->>UI: saveDetails(details)
    UI->>C: revise(listing, details, v + 1)
    C->>L: checkVersion(v + 1)
    C->>L: revise(details)
    alt published and price, currency or stock changed
        L-->>C: refused
        C-->>UI: state conflict
    else edit allowed in this state
        L-->>C: revision v + 2
        C-->>UI: saved revision
    end
```

Figure 3.101: Analysis sequence diagram of UC9 flow B2, revise with a stale version (UML). The stale save changes nothing; recovery is the administrator's own reload and second save. The account check of Figure 3.100 is omitted.

**B3 Delete a draft.** Only a draft that was never published and has no history may be deleted, and even then the record is only marked deleted, so the audit trail stays.

```mermaid
sequenceDiagram
    actor Admin
    participant UI as ListingConsoleUI (boundary)
    participant C as ManageListingControl (control)
    participant L as Listing (entity)
    participant P as PriceQuote (entity)
    participant T as Team (entity)
    Admin->>UI: deleteDraft()
    UI->>C: delete(listing, version)
    C->>L: checkVersion(version)
    C->>L: checkDeletable()
    alt published before, or units sold
        L-->>C: refused
        C-->>UI: state conflict
    else never-published draft
        C->>P: any for listing, including expired?
        C->>T: any for listing, including closed?
        alt any history found
            C-->>UI: listing has history
        else no history
            C->>L: markDeleted()
            C-->>UI: deleted
        end
    end
    UI-->>Admin: result
```

Figure 3.102: Analysis sequence diagram of UC9 flow B3, delete a draft (UML). The listing checks its own state, and the control checks the other entities' history; a price policy is checked the same way as a quote (omitted).

**B4 Set team terms and an optional team price.** The administrator sets how many paid members a team needs and how long it stays open, and may add a team price in the same command. A rejected price leaves the terms unchanged. Every accepted change is recorded.

```mermaid
sequenceDiagram
    actor Admin
    participant UI as ListingConsoleUI (boundary)
    participant C as ManageListingControl (control)
    participant L as Listing (entity)
    participant P as TeamPricePolicy (entity)
    participant H as TeamTermsChange (entity)
    Admin->>UI: setTeamTerms(size, lifetime, price)
    UI->>C: setTeamTerms(listing, terms, version)
    C->>L: checkVersion(version)
    C->>L: setTeamTerms(size, lifetime)
    alt size or lifetime out of bounds
        L-->>C: refused, bad fields
        C-->>UI: showRefusal
    else terms valid
        L-->>C: revision, version + 1
        opt team price given
            C->>P: create revision(price, valid from, valid until)
            alt price not below original, or listing not on sale
                P-->>C: refused
                C-->>UI: refused, terms not saved
            end
        end
        C->>H: create(old terms, new terms, admin)
        C-->>UI: saved terms
    end
```

Figure 3.103: Analysis sequence diagram of UC9 flow B4, set team terms (UML). Terms and price succeed or fail together, and every accepted change leaves an audit record.

**B5 The last seat fills the team.** The paid join that takes the last seat moves the team from OPEN to SUCCEEDED. The join itself is UC4 (3.4.1); UC5 begins where `Team` reports that it is full.

```mermaid
sequenceDiagram
    actor Stripe
    participant G as PaymentGateway (boundary)
    participant J as JoinTeamControl (control)
    participant T as Team (entity)
    participant R as ResolveTeamControl (control)
    participant O as Order (entity)
    Stripe->>G: paymentConfirmed(ref, amount)
    G->>J: paymentConfirmed(ref, amount)
    J->>T: admit(buyer, amount, now)
    alt admissible and currentSize + 1 = targetSize
        T->>T: canMoveTo(SUCCEEDED), successTime = now
        T-->>J: admitted, team filled
        J->>R: teamFilled(team)
        loop each pending order of the team
            R->>O: confirm()
        end
    else admissible, seats remain
        T-->>J: admitted, still open
    else closed, full or already a member
        T-->>J: rejected
        Note over J: UC4 records a refund for this payment
    end
```

Figure 3.104: Analysis sequence diagram of UC5 flow B5, the last seat fills the team (UML). Only the admission that reaches the target size resolves the team, and only then are its orders confirmed.

**B6 The deadline passes.** The Scheduler ticks every minute. Each open team whose deadline has passed is expired once: its orders are cancelled and one refund obligation is created per paid member. A team that a last-seat join resolved first is skipped.

```mermaid
sequenceDiagram
    actor Scheduler
    participant X as SchedulerTrigger (boundary)
    participant R as ResolveTeamControl (control)
    participant T as Team (entity)
    participant O as Order (entity)
    participant F as Refund (entity)
    Scheduler->>X: tick(now)
    X->>R: expireDue(now)
    loop each open team past its deadline
        R->>T: expire(now)
        alt still OPEN and expireTime reached
            T->>T: canMoveTo(EXPIRED)
            T-->>R: expired
            R->>O: cancel(at)
            R->>F: create, one per paid member
        else already resolved
            T-->>R: refused, skip this team
        end
    end
```

Figure 3.105: Analysis sequence diagram of UC5 flow B6, the deadline passes (UML). Expiry and its consequences happen together, and a team that is already resolved is skipped, not treated as an error.

#### C Design class diagram

The design classes are the real classes in the repository (Figures 3.106 to 3.108). They follow the four COLA layers of 3.1: adapter (web), application, domain and infrastructure. UC9 is split into two figures, so that each stays legible on A4. A parameter list with more than one parameter is shown as `(…)`; part D gives the full calls.

**UC9, web and application layers.** The analysis boundary becomes React components that call the REST API under `/api/v1/admin/products` through the generated client (3.2.2, 3.2.3). The control becomes `AdminListingController`, which checks the session, role and permission, and two application services: `AdminListingAppServiceImpl` for revise, status change and delete, and `TeamTermsAppServiceImpl` for team terms. Each service method is one bounded transaction. History owned by another bounded context is read only through that context's facade, `GroupDealQueryService`, never through its tables.

```mermaid
classDiagram
    class EditListingPage {
        <<route component>>
        -conflict
    }
    class LifecycleDialog {
        <<component>>
    }
    class TeamTermsPanel {
        <<component>>
    }
    class ConflictBanner {
        <<component>>
        +loadedVersion
        +serverVersion
        +onReload()
    }
    class AdminApi["admin-api.ts"] {
        <<module>>
        +useReviseListing()
        +useChangeStatus()
        +useDeleteListing()
        +useConfigureTeamTerms()
    }
    class AdminListingController {
        <<RestController>>
        +revise(…)
        +changeStatus(…)
        +delete(…)
        +configureTeamTerms(…)
        -administrator() UUID
    }
    class AdminListingAppService {
        <<interface>>
        +revise(…)
        +changeStatus(…)
        +delete(…)
    }
    class AdminListingAppServiceImpl {
        -lockForChange(…)
    }
    class TeamTermsAppService {
        <<interface>>
        +configure(…)
    }
    class TeamTermsAppServiceImpl
    class TeamPriceAppService {
        <<interface>>
        +configure(…)
    }
    class UserAppService {
        <<interface>>
        +requireActive(userId)
    }
    class GroupDealQueryService {
        <<interface>>
        +hasProductReferences(productId) boolean
    }
    %% at EditListingPage 0,0
    %% at ConflictBanner 0,1
    %% at LifecycleDialog 1,1
    %% at TeamTermsPanel 2,1
    %% at AdminApi 1,2
    %% at AdminListingController 1,3
    %% at AdminListingAppService 0,4
    %% at TeamTermsAppService 2,4
    %% at AdminListingAppServiceImpl 0,5
    %% at TeamTermsAppServiceImpl 2,5
    %% at GroupDealQueryService 0,6
    %% at UserAppService 1,6
    %% at TeamPriceAppService 2,6
    EditListingPage *-- ConflictBanner
    EditListingPage *-- LifecycleDialog
    EditListingPage *-- TeamTermsPanel
    EditListingPage ..> AdminApi
    LifecycleDialog ..> AdminApi
    TeamTermsPanel ..> AdminApi
    AdminApi ..> AdminListingController
    AdminListingController --> AdminListingAppService
    AdminListingController --> TeamTermsAppService
    AdminListingAppService <|.. AdminListingAppServiceImpl
    TeamTermsAppService <|.. TeamTermsAppServiceImpl
    AdminListingAppServiceImpl --> GroupDealQueryService
    AdminListingAppServiceImpl --> UserAppService
    TeamTermsAppServiceImpl --> UserAppService
    TeamTermsAppServiceImpl --> TeamPriceAppService
```

Figure 3.106: Design class diagram of UC9, web and application layers (UML). The analysis control is split into a controller that checks the role and two transactional services; another context's history is reached only through its facade, `GroupDealQueryService`.

**UC9, domain and infrastructure layers.** The `Listing` entity becomes the `Product` aggregate. Its lifecycle rules sit in the per-state behaviour of the `ProductStatus` enum, which is the State pattern of 3.5.5. `Product` has no public status setter: every change returns a new revision with `version + 1`. `ProductRepository` is the domain port, implemented by `ProductRepositoryImpl` over `BizProductMapper`; a save made on an old version is refused (Optimistic Offline Lock, section 3.5.5).

```mermaid
classDiagram
    class Product {
        -status: ProductStatus
        -stock: int
        -soldCount: int
        -version: int
        -teamTerms: ProductTeamTerms
        +revise(details) Product
        +publish() Product
        +delist() Product
        +requireDeletable()
        +configureTeamTerms(…)
        +requireVersion(expected)
    }
    class ProductStatus {
        <<enumeration>>
        DRAFT
        ON_SALE
        OFF_SALE
    }
    class ProductTeamTerms {
        <<record>>
        +targetSize: Integer
        +lifetimeSeconds: Integer
    }
    class TeamTermsChange {
        <<record>>
        +previous: ProductTeamTerms
        +current: ProductTeamTerms
        +actorId: UUID
    }
    class ProductRepository {
        <<interface>>
        +lockById(id) Optional~Product~
        +updateListing(…)
        +updateTeamTerms(…)
        +appendTeamTermsLog(change)
        +deleteDraft(…)
    }
    class ProductRepositoryImpl {
        -requireChanged(count)
    }
    class BizProductMapper {
        <<MyBatis mapper>>
        +selectActiveByIdForUpdate(id)
        +updateListing(…)
        +updateTeamTerms(…)
        +deleteDraft(…)
    }
    class TeamPriceRepository {
        <<interface>>
        +hasProductReferences(productId) boolean
        +appendPolicy(…)
    }
    class CatalogErrorCode {
        <<enumeration>>
        PRODUCT_STATE_CONFLICT
        PRODUCT_REFERENCED
        PRODUCT_VERSION_CONFLICT
    }
    %% at Product 1,0
    %% at ProductStatus 0,1
    %% at ProductTeamTerms 1,1
    %% at TeamTermsChange 2,1
    %% at CatalogErrorCode 0,2
    %% at ProductRepository 1,2
    %% at TeamPriceRepository 2,2
    %% at ProductRepositoryImpl 1,3
    %% at BizProductMapper 1,4
    Product "0..*" --> "1" ProductStatus : current state
    Product *-- ProductTeamTerms
    TeamTermsChange ..> ProductTeamTerms
    ProductStatus ..> CatalogErrorCode
    ProductRepository ..> Product
    ProductRepository ..> TeamTermsChange
    ProductRepository <|.. ProductRepositoryImpl
    ProductRepositoryImpl --> BizProductMapper
```

Figure 3.107: Design class diagram of UC9, domain and infrastructure layers (UML). `Product` delegates its lifecycle rules to its current `ProductStatus`, drawn here as one box, and every write goes through `ProductRepository`; the State classes of a listing are Figure 3.138.

**UC5.** The two analysis triggers become two entry points, which meet at the same row of `biz_group_deal` (Figure 3.108). `SchedulerTrigger` becomes `TeamExpirySweepJob`, a Spring `@Scheduled` job run under `LeasedJobRunner` (sweep design in section 3.2.7). `ResolveTeamControl` is split into three classes. `GroupDealExpirySweeper` selects the due teams in one bounded query. `GroupDealExpiryService.expireOne` resolves one team in its own short transaction. `PaidGroupDealService.joinConfirmed`, shared with UC4, publishes `TeamSucceeded` when an admission completes the team. The consequences are domain-event subscribers that commit with the move (listener phases, section 3.2.9): `OrderLifecycleSubscriber` confirms or cancels the orders, and `TeamExpiryRefundSubscriber` records the refunds.

The analysis rule `canMoveTo` becomes `DealStatus.canTransitionTo`: only OPEN may move, and never to itself. `GroupDeal.joinPaid` and `GroupDeal.expire` check it on every move. `DealStatus` is a transition table, not State (section 3.5.5). FAILED and CANCELED are declared but never written (Figure 3.115).

```mermaid
classDiagram
    class TeamExpirySweepJob {
        <<Scheduled job>>
        +run()
    }
    class LeasedJobRunner {
        +run(…)
    }
    class GroupDealExpirySweeper {
        +sweep(…) Result
    }
    class GroupDealExpiryService {
        +expireOne(dealId) boolean
    }
    class PaidGroupDealService {
        +joinConfirmed(…)
    }
    class PaidGroupDealRepository {
        <<interface>>
        +lockDeal(dealId) Optional~GroupDeal~
        +currentTime() Instant
        +dueForExpiry(…) List~UUID~
        +deferExpiryRetry(…)
        +expire(…)
        +insertJoinedMember(…)
    }
    class GroupDeal {
        -targetSize: int
        -currentSize: int
        -status: DealStatus
        -expireTime: Instant
        -successTime: Instant
        +joinPaid(…)
        +expire(now) GroupDeal
        +requireJoinWindow(now)
        +requireAvailableSeat()
    }
    class DealStatus {
        <<enumeration>>
        OPEN
        SUCCEEDED
        EXPIRED
        FAILED
        CANCELED
        +canTransitionTo(target) boolean
    }
    class TeamSucceeded {
        <<domain event>>
    }
    class TeamExpired {
        <<domain event>>
    }
    class OrderLifecycleSubscriber {
        +onTeamSucceeded(event)
        +onTeamExpired(event)
    }
    class TeamExpiryRefundSubscriber {
        +onTeamExpired(event)
    }
    %% at TeamExpirySweepJob 0,0
    %% at LeasedJobRunner 1,0
    %% at GroupDealExpirySweeper 0,1
    %% at GroupDealExpiryService 0,2
    %% at PaidGroupDealService 2,2
    %% at PaidGroupDealRepository 1,3
    %% at GroupDeal 1,4
    %% at DealStatus 1,5
    %% at TeamExpired 0,6
    %% at TeamSucceeded 2,6
    %% at TeamExpiryRefundSubscriber 0,7
    %% at OrderLifecycleSubscriber 1,7
    TeamExpirySweepJob --> LeasedJobRunner
    TeamExpirySweepJob --> GroupDealExpirySweeper
    GroupDealExpirySweeper --> GroupDealExpiryService
    GroupDealExpirySweeper --> PaidGroupDealRepository
    GroupDealExpiryService --> PaidGroupDealRepository
    PaidGroupDealService --> PaidGroupDealRepository
    GroupDealExpiryService ..> GroupDeal : expire
    PaidGroupDealService ..> GroupDeal : joinPaid
    GroupDealExpiryService ..> TeamExpired : publishes
    PaidGroupDealService ..> TeamSucceeded : publishes
    GroupDeal --> DealStatus
    TeamExpiryRefundSubscriber ..> TeamExpired : listens
    OrderLifecycleSubscriber ..> TeamExpired : listens
    OrderLifecycleSubscriber ..> TeamSucceeded : listens
```

Figure 3.108: Design class diagram of UC5 Team lifecycle resolution (UML). Two entry points (the sweep and the paid join) reach one aggregate whose `DealStatus` table decides every move; the consequences are event subscribers, not calls.

#### D Design sequence diagrams

Each design flow matches the analysis flow with the same number. Every UC9 write locks the product row, then checks the account and the version, in one transaction (Optimistic Offline Lock, section 3.5.5).

**D1 Publish a listing.**

```mermaid
sequenceDiagram
    actor Admin
    participant P as :LifecycleDialog
    participant W as :AdminListingController
    participant S as :AdminListingAppServiceImpl
    participant R as :ProductRepository
    participant D as :Product
    participant T as :ProductStatus
    Admin->>P: Publish
    P->>W: PUT /admin/products/{id}/status (on_sale, expectedVersion)
    W->>W: administrator() checks login, role, permission
    W->>S: changeStatus(id, ON_SALE, expectedVersion, actorId)
    critical transaction, timeout 10 s
        S->>R: lockById(id)
        R-->>S: product, row locked FOR UPDATE
        Note over S,R: UserAppService checks the account is active
        S->>D: requireVersion(expectedVersion)
        S->>D: publish()
        break no stock or no price
            D-->>S: BizException CATALOG_409_002
            S-->>W: rollback
            W-->>P: 409 via GlobalExceptionHandler
        end
        D->>T: publish()
        alt DRAFT or OFF_SALE
            T-->>D: ON_SALE
            D-->>S: next revision, version + 1
            S->>R: updateListing(changed, expectedVersion, actorId)
            S-->>W: ManagedListing
            W-->>P: 200 Result, new version
        else ON_SALE
            T-->>D: BizException CATALOG_409_002
            D-->>S: BizException CATALOG_409_002
            S-->>W: rollback
            W-->>P: 409 via GlobalExceptionHandler
        end
    end
```

Figure 3.109: Design sequence diagram of UC9 flow D1, publish a listing (UML). `Product` refuses a listing without stock or price; for the rest, the current `ProductStatus` decides the move inside one locked transaction; a refused move rolls back and returns 409. The state dispatch alone is Figure 3.140.

**D2 Revise with a stale version, then reload and retry.** The 412 response carries the server's current version, which the conflict banner shows. While the banner is up, every write on the page is disabled.

```mermaid
sequenceDiagram
    actor Admin
    participant P as :EditListingPage
    participant B as :ConflictBanner
    participant W as :AdminListingController
    Admin->>P: Save, form loaded at v
    P->>W: PUT /admin/products/{id} (expectedVersion v)
    W-->>P: 412 with currentVersion v + 1
    P->>B: show(loadedVersion v, serverVersion v + 1)
    Admin->>B: Reload
    B->>P: onReload()
    P->>W: GET /admin/products/{id}
    W-->>P: listing at v + 1, form remounted
    Admin->>P: Save again
    P->>W: PUT /admin/products/{id} (expectedVersion v + 1)
    W-->>P: 200, version v + 2
```

Figure 3.110: Design sequence diagram of UC9 flow D2, stale revise and retry, seen from the page (UML). The banner shows both versions and the retry is the administrator's Reload, never automatic; the version check behind the 412 is Figure 3.141.

**D3 Delete a draft.** The flow starts when the administrator confirms Delete in the lifecycle dialog.

```mermaid
sequenceDiagram
    participant P as :LifecycleDialog
    participant W as :AdminListingController
    participant S as :AdminListingAppServiceImpl
    participant R as :ProductRepository
    participant D as :Product
    participant Q as :TeamPriceRepository
    participant G as :GroupDealQueryService
    P->>W: DELETE /admin/products/{id}?expectedVersion=v
    W->>S: delete(id, v, actorId)
    critical transaction, timeout 10 s, product row locked first
        S->>R: lockById(id)
        S->>D: requireVersion(v)
        S->>D: requireDeletable()
        alt published state, or soldCount above 0
            D-->>S: CATALOG_409_002 or CATALOG_409_004
        else never-published draft
            S->>Q: hasProductReferences(id)
            S->>G: hasProductReferences(id)
            alt any policy, quote or team, even deleted ones
                S-->>W: CATALOG_409_004, rollback
            else no history
                S->>R: deleteDraft(id, v, actorId)
                Note over R,D: soft delete, deleted = true, row kept for audit
                S-->>W: done
            end
        end
    end
    W-->>P: 200, or 409 with the reason
```

Figure 3.111: Design sequence diagram of UC9 flow D3, delete a draft (UML). Policies and quotes are checked in the catalog's own repository and teams through the group-buy facade, all under the product lock; deletion is soft. The account check of Figure 3.109 is omitted.

**D4 Set team terms and an optional team price.** The product version and the policy version are two separate concurrency tokens (Optimistic Offline Lock, section 3.5.5).

```mermaid
sequenceDiagram
    actor Admin
    participant P as :TeamTermsPanel
    participant W as :AdminListingController
    participant S as :TeamTermsAppServiceImpl
    participant R as :ProductRepository
    participant D as :Product
    participant X as :TeamPriceAppService
    Admin->>P: Save terms
    P->>W: PUT /admin/products/{id}/team-terms
    W->>S: configure(id, ConfigureTerms, actorId)
    critical one transaction, timeout 10 s
        S->>R: lockById(id)
        S->>D: requireVersion(expectedVersion)
        S->>D: configureTeamTerms(targetSize, lifetimeSeconds)
        D-->>S: next revision, or CATALOG_400_003
        S->>R: updateTeamTerms(revised, expectedVersion, actorId)
        opt teamPrice sent
            S->>X: configure(id, actorId, policy)
            alt bad price, stale policy version, or not on sale
                X-->>S: CATALOG_400_001, 412_001 or 409_001
                S-->>W: rollback, terms not saved
            else accepted
                X-->>S: new TeamPricePolicy revision
            end
        end
        S->>R: appendTeamTermsLog(TeamTermsChange)
    end
    S-->>W: TeamTermsResult
    W-->>P: 200, new version and terms
```

Figure 3.112: Design sequence diagram of UC9 flow D4, set team terms (UML). The price joins the terms' transaction, so a rejected price rolls the terms back; each accepted write appends a `log_product_team_terms` row.

**D5 The last seat fills the team.** This flow runs inside the settlement transaction of UC4, which locks in the order of Table 3.12 (section 3.2.8). The admission that takes the last seat and publishes `TeamSucceeded` is Figure 3.48; UC5 adds only what follows the event. Its listener runs before the same transaction commits (listener phases, section 3.2.9), so it is drawn as an ordinary call.

```mermaid
sequenceDiagram
    participant S as :PaidGroupDealService
    participant O as :OrderLifecycleSubscriber
    participant Y as :OrderRepository
    critical settlement transaction of UC4
        Note over S: joinPaid admitted the last seat (Figure 3.48)
        S->>O: TeamSucceeded, BEFORE_COMMIT listener
        O->>Y: confirmByDealId(dealId)
        Y-->>O: pending orders of the team confirmed
    end
```

Figure 3.113: Design sequence diagram of UC5 flow D5, from `TeamSucceeded` onward (UML). The team's pending orders, including the last buyer's, are confirmed before the settlement transaction commits; the admission of the last seat is Figure 3.48.

**D6 The deadline passes.** Each due team is resolved in its own transaction (sweep design in section 3.2.7).

```mermaid
sequenceDiagram
    actor Scheduler
    participant J as :TeamExpirySweepJob
    participant L as :LeasedJobRunner
    participant W as :GroupDealExpirySweeper
    participant X as :GroupDealExpiryService
    participant R as :PaidGroupDealRepository
    participant G as :GroupDeal
    Scheduler->>J: run(), fixed delay PT1M
    J->>L: run(team-expiry-sweep, lease, sweepOnce)
    alt lease held by another instance
        L-->>J: skipped
    else lease acquired
        L->>W: sweep(batchSize, leaseValid)
        W->>R: dueForExpiry(now, limit)
        loop each due team, while the lease is valid
            W->>X: expireOne(dealId)
            critical transaction per team, timeout 10 s
                X->>R: lockDeal(dealId)
                X->>R: currentTime()
                X->>G: expire(now)
                alt OPEN and expireTime reached
                    G-->>X: EXPIRED snapshot
                    X->>R: expire(before, after, now)
                    Note over R,G: expireDeal WHERE status = open
                    X->>X: publishEvent(TeamExpired), BEFORE_COMMIT listeners
                    Note over X,R: orders cancelled (order 10), then refunds recorded (order 20)
                    X-->>W: true
                else resolved first by a join or another sweep
                    G-->>X: BizException DEAL_CLOSED
                    X-->>W: false, already resolved
                end
            end
        end
        W-->>L: Result(due, expired, alreadyResolved, failed)
    end
```

Figure 3.114: Design sequence diagram of UC5 flow D6, the deadline passes (UML). Each team gets its own locked transaction, the status-guarded update settles a race with a last-seat join, and the `TeamExpired` subscribers commit with the expiry.

#### Statechart of the team

The team has significant, dynamic states, so it has a statechart, in the notation of section 3.4.1.

The team (`GroupDeal`) is created OPEN by a confirmed paid start (UC3) and filled by confirmed paid joins (UC4). It resolves once (UC5): it succeeds when the last seat is taken, or expires when its window closes first, which owes refunds (UC6).

![Figure 3.115: UML statechart of the team (`GroupDeal`). The `paidJoinConfirmed` events have disjoint guards; those that leave the status unchanged are internal transitions, listed inside the state box. No final state is drawn because a resolved team still absorbs late paid joins as refunds.](../figures/team-statechart.svg)

*Admissible* means that the paid amount equals the team price, the buyer is neither the leader nor a member, `startTime ≤ now < expireTime`, and a seat is free. The chart matches `DealStatus.canTransitionTo`: only OPEN may move, and never to itself. The events that keep the status are a join that leaves seats, a refused join and a late join after resolution. `DealStatus` also declares FAILED and CANCELED, and the transition table allows them, but no production code writes either, so a team ends only as SUCCEEDED or EXPIRED and the figure draws FAILED and CANCELED dashed and unconnected. `GroupDeal.joinPaid` enforces the OPEN transitions; `TeamExpirySweepJob` reaches `GroupDealExpiryService`, which calls `GroupDeal.expire` and writes through a status-guarded update (guarded update, section 3.2.8).

#### E Compliance with the transition strategies

UC9 applies six of the nine strategies of 3.2, and UC5 applies five. Table 3.20 maps each strategy to the analysis object it transformed and the design element that resulted.

Table 3.20: Transition strategies applied to UC9 and UC5.

| Strategy | Analysis object | Design element |
| --- | --- | --- |
| 3.2.1 Language and Framework | «control» `ManageListingControl` (UC9) | Spring `@Service` classes `AdminListingAppServiceImpl` and `TeamTermsAppServiceImpl` behind the interfaces `AdminListingAppService` and `TeamTermsAppService`, each method a `@Transactional(timeout = 10)` unit |
| 3.2.1 Language and Framework | «control» `ResolveTeamControl` (UC5) | split into `GroupDealExpirySweeper` (selection), `GroupDealExpiryService` (one team, one transaction) and `PaidGroupDealService` (last seat, shared with UC4) |
| 3.2.2 Presentation | «boundary» `ListingConsoleUI` (UC9) | route component `EditListingPage` with `LifecycleDialog`, `TeamTermsPanel` and `ConflictBanner`; data hooks in `admin-api.ts` (frontend ADR 0015) |
| 3.2.3 Distribution | «boundary» `ListingConsoleUI` and «entity» `Team`, `PriceQuote` as read by the control (UC9) | REST endpoints under `/api/v1/admin/products` called through the client generated from the OpenAPI contract; cross-context history through the facade `GroupDealQueryService` |
| 3.2.4 Persistence | «entity» `Listing`, `TeamPricePolicy`, `TeamTermsChange` (UC9) | aggregate `Product` with `ProductStatus` and `ProductTeamTerms`; port `ProductRepository`, adapter `ProductRepositoryImpl`, `BizProductMapper`; tables `biz_product`, `log_product_team_terms`; soft delete via `deleted` |
| 3.2.4 Persistence | «entity» `Team`, `Membership` (UC5) | aggregate `GroupDeal` with `DealStatus`; port `PaidGroupDealRepository`, adapter `PaidGroupDealRepositoryImpl`, `PaidGroupDealMapper`; tables `biz_group_deal`, `biz_group_member` |
| 3.2.6 Security | «entity» `AdminAccount` (UC9) | `administrator()` in `AdminListingController`: Sa-Token `checkLogin`, `checkRole` for the admin role and `checkPermission` for `catalog:product:write`; actor taken from the session; `requireActive` in `UserAppService` rechecks the account inside the transaction |
| 3.2.7 Scheduling | «boundary» `SchedulerTrigger` (UC5) | `TeamExpirySweepJob` (`@Scheduled`), `LeasedJobRunner`, retry deferral in `sys_scheduled_item_retry` (sweep design in section 3.2.7) |
| 3.2.8 Concurrency | `version` of «entity» `Listing` (UC9) | `Product.requireVersion` under the product row lock and the version-guarded mapper updates (Optimistic Offline Lock, section 3.5.5) |
| 3.2.8 Concurrency | `status` and `currentSize` of «entity» `Team` (UC5) | `lockDeal` row lock and the guarded updates `updateDeal` and `expireDeal` (guarded update, section 3.2.8) |
| 3.2.9 Domain Events | links from «control» `ResolveTeamControl` to «entity» `Order` and `Refund` (UC5) | events `TeamSucceeded` and `TeamExpired`; subscribers `OrderLifecycleSubscriber` and `TeamExpiryRefundSubscriber` (listener phases, section 3.2.9) |

UC9 does not use 3.2.5 External Interface, 3.2.7 Scheduling or 3.2.9 Domain Events: it calls no provider, runs on no timer and publishes no event. UC5 does not use 3.2.2 Presentation, 3.2.3 Distribution or 3.2.6 Security: it has no screen of its own, and its only actor is the Scheduler inside the server. 3.2.5 External Interface reaches UC5 only through UC4's payment boundary (3.4.1).

**How the design was checked.** Most of the design was checked by automated tests; parts of it were also run on dev.

- UC9 rules are covered by domain unit tests (`ProductStatusTest`, `ProductLifecycleTest`), application unit tests (`AdminListingAppServiceImplTest`, `TeamTermsAppServiceImplTest`) and PostgreSQL integration tests (`AdminListingFlowIT`, `TeamTermsFlowIT`). These run locally and in CI on Testcontainers, not on dev. `AdminListingFlowIT` covers stale writes returning the stored version without a change, the history vetoes on deletion, and the role-only and permission-only denials.
- On 8 October on dev, the demo-listing seed script made 12 writes through this API in 4.0 seconds: it created, published and set team terms for four listings. The stale-version and deletion flows were not exercised on dev.
- UC5 rules are covered by `DealStatusTest`, `PaidGroupDealTest`, `GroupDealExpiryServiceTest` (including a concurrent resolution treated as a replay), `GroupDealExpirySweeperTest` (one failing team does not stop the others), `TeamExpirySweepJobTest` and `V4ExpiryPipelineIT`, which expires 2,000 seeded teams with exactly one effect each. All run locally and in CI, not on dev.
- The expiry and its refund were shown on dev on 23 October (Table E.3), and the last-seat flow was shown on dev on 24 October in a recording (Appendix E).
- How late an expiry may run is the expiry design bound (section 1.4.3).
