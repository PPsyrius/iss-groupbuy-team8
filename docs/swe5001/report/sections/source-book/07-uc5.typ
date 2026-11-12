#import "../../figures.typ": *

== UC5 Resolve a Team <sec-a-uc05>

An open team ends in exactly one of two ways. It *succeeds* when a paid join fills its last seat, or it *expires* when its deadline passes first. Either way BlocBuy settles all the team's orders together with the outcome, and an expiry also records the refunds owed to its paid members (UC6 extends UC5). A resolved team never changes state again. UC5 is where time, a background job running on several servers, and two writers racing for one team meet. This part owns the two lifecycles that UC5 decides: the team (`DealStatus`) and the order (`OrderStatus`), in @sec-a-uc05-states. Every class, method and error code named here exists in the code on this branch. UC5 is designed and built; its status on AWS is given by capability in the status table of report §2.2.

=== Use Case and Flows of Events <sec-a-uc05-flows>

- *Actors.* Scheduler (primary, a time actor), the only actor of UC5. The success outcome has no actor of its own: UC4's admission of the buyer who fills the last seat triggers it (@sec-a-uc04).
- *Trigger.* Success: UC4 admits a buyer into the last free seat (UC4 B7). Expiry: the Scheduler starts its regular expiry run.
- *Services.* commerce-svc owns every step (its group-buy, order and payment modules and its scheduled jobs). notification-svc later tells the members (@sec-a-notifications); identity-svc takes no part.
- *Preconditions.* An open team exists. For expiry, scheduled expiry is switched on for the deployment; it is off by default.
- *Postconditions.* Each resolved team has exactly one final status and keeps it. A succeeded team has all its orders confirmed. An expired team has all its orders cancelled and exactly one refund obligation per paid member. A replayed resolution changes nothing.

@tbl-a-uc05-flows numbers the two basic flows and their branches. The diagrams repeat these numbers, and @tbl-a-uc05-trace maps each step to its design operations.

#atable(
  [UC5 flows of events. Basic flow 1 (B1–B4) is the success; basic flow 2 (B5–B10) is the expiry. A = alternate flow, E = exception flow; "At" is the step where it branches off.],
  <tbl-a-uc05-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  table.cell(colspan: 3)[_Basic flow 1: the last seat fills the team (inside UC4)_],
  [*B1*], [], [UC4 admits a paid buyer into the team, and the new member count equals the team's target size.],
  [*B2*], [], [The team marks itself succeeded and records its success time. From now on it admits nobody.],
  [*B3*], [], [Together with the success, the system confirms every pending order of the team, including the order of the buyer who filled it.],
  [*B4*], [], [The system records the success so that the members can be told (Receive notifications, @sec-a-notifications), and the listing stops offering the team.],
  table.cell(colspan: 3)[_Basic flow 2: the deadline passes_],
  [*B5*], [], [The Scheduler starts an expiry run one minute after the previous run ended. Only one run works at a time, whichever server it starts on.],
  [*B6*], [], [The system selects up to 100 open teams whose deadline has passed, oldest deadline first, leaving out teams that wait for a retry (E1).],
  [*B7*], [], [For each selected team in turn, the system checks again that the team is still open and its deadline has passed, and marks it expired.],
  [*B8*], [], [Together with the expiry, the system cancels every pending order of the team, with the team's resolution time as the cancel time.],
  [*B9*], [], [Together with the expiry, «extend» UC6 at extension point _resolved_ {team expired}: the system records one refund obligation for each paid member (@sec-a-uc06).],
  [*B10*], [], [The system records the expiry so that the members can be told, and the listing stops offering the team. The run ends after the last selected team.],
  [*A1*], [B5], [Another server is already running the expiry: this run does nothing, and the next run tries again.],
  [*A2*], [B7], [The team was resolved after B6: a paid join filled it, or another run expired it. The team is skipped and nothing changes. A join and an expiry of the same team are decided one after the other, and only the first takes effect.],
  [*A3*], [B7], [The run loses its right to continue (another server may now run the expiry): it takes no further team, and the next run carries on.],
  [*A4*], [B1], [A paid join reaches a team that has already succeeded: the admission is refused (`DEAL_CLOSED`), the team keeps its status, and the payment is owed a refund (UC4 A6). A payment for a team that has expired, or whose deadline has passed, is refused at settlement (`BUSINESS_EXPIRED`) before the team is read, and is owed a refund (UC4 A5).],
  [*E1*], [B7–B10], [A technical failure while resolving one team: none of its expiry, order cancellations, refund obligations or notice records is kept. The team cannot be selected again for 5 minutes, and the run goes on with the next team.],
  [*E2*], [B3], [A technical failure while confirming the orders: the whole settlement of UC4 is undone, the admission included, and Stripe's redelivery retries it (UC4 E1).],
  [*E3*], [B5], [Scheduled expiry is switched off, or the server running the expiry stops part way: overdue teams stay open in the records but refuse every join: new join terms are refused by `requireJoinWindow` (UC4 B3), and a payment that settles after the deadline ends as `BUSINESS_EXPIRED` (UC4 A5). They expire at the first run after expiry is switched on, or once the stopped run's claim lapses (at most 2 minutes).],
)

=== Analysis Class Diagram <sec-a-uc05-ac>

@fig-a-uc05-ac is the analysis model. The time actor Scheduler reaches the system only through the boundary `SchedulerTrigger`, as the Scheduler of SAD 03c's ATM example reaches "Monitor Balance". The success has no boundary of its own: it enters from UC4's `JoinTeamControl` (@fig-a-uc04-ac), drawn here only with the operation UC5 needs. `ResolveTeamControl` realises UC5 and applies the outcome to the orders. `Team` alone owns the transition rule `canMoveTo`, so no control decides by itself whether a team may change state. `TeamStatus` and `OrderStatus` are enumerations whose values the statecharts define (@sec-a-uc05-states).

The entity names are those of A.6: `Team`, `Membership` and `Order` keep their meaning. Two objects are new: the boundary `SchedulerTrigger` and the control `ResolveTeamControl`. Two attributes are new because the flows need them: `Team.successTime` (B2) and `Order.cancelTime` (B8). The refund obligation of B9 belongs to UC6 and is drawn in @sec-a-uc06; UC5 reaches it only through the «extend» relationship, so it is not repeated here.

#afig("a-uc05-ac", [UC5 analysis class diagram (UML class notation, BCE stereotypes). The Scheduler uses `SchedulerTrigger`; the success enters from UC4's `JoinTeamControl`. `ResolveTeamControl` settles the orders, and `Team` alone decides whether it may move.])

=== Analysis Sequence Diagrams <sec-a-uc05-as>

Each basic flow has its own analysis sequence. @fig-a-uc05-as-success starts where UC4 asks the team to admit a buyer. Only the admission that reaches the target size resolves the team, and only then are its orders confirmed; the other two branches show that UC5 does not start while seats remain, and A4 (a resolved team refuses). @fig-a-uc05-as-expiry is the Scheduler's run, with A2 as its `alt` branch. A1, A3, E1 and E3 are design-only: they concern servers, a claim on the run and transactions, which the analysis model does not have. E2 is UC4's own exception. B4 and B10 publish the result for another use case and are drawn in the design (@fig-a-uc04-ds-events, @fig-a-uc05-ds-expire-events).

#afig("a-uc05-as-success", [UC5 analysis sequence diagram, basic flow 1 (B1–B4) with A4. `Team` reports that it is full; only then does `ResolveTeamControl` confirm each pending order.])

#afig("a-uc05-as-expiry", [UC5 analysis sequence diagram, basic flow 2 (B5–B10) with A2. Each due team is asked to expire itself; a team already resolved refuses and is skipped, and UC6 extends the flow at B9.])

=== Transition: Strategies and Mapping <sec-a-uc05-transition>

The strategies of @sec-a-strategies turn this analysis model into the design model. Five of the nine change UC5:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26)*: impacted `ResolveTeamControl`, `Team.canMoveTo` → resultant three application services (`GroupDealExpirySweeper` selects, `GroupDealExpiryService` resolves one team, `PaidGroupDealService` is shared with UC4), records (`GroupDealExpirySweeper.Result`, `SchedulingProperties.ExpirySweep`, `TeamSucceeded`, `TeamExpired`) and the enumeration `DealStatus` with its transition rule `canTransitionTo`; dynamics @fig-a-uc05-ds-sweep, @fig-a-uc05-ds-expire.
- *S2 Presentation.* Not applied: UC5 has no screen. The account page's "Filled" and "Closed" labels are UC8's (@sec-a-uc08).
- *S3 Distribution.* Not applied: UC5 makes no remote call. The members learn the result only through the outbox of S9 (AD-04).
- *S4 Persistence (AD-03, AD-11; NFR-05, 13)*: impacted `Team`, `Membership`, `Order` → resultant aggregate root `GroupDeal` behind `PaidGroupDealRepository` → `PaidGroupDealRepositoryImpl` → `PaidGroupDealMapper` (table `biz_group_deal`), `OrderRepository` (table `biz_order`), and two technical tables: `sys_scheduler_lock` (the run's lease) and `sys_scheduled_item_retry` (the E1 back-off); dynamics @fig-a-uc05-ds-expire.
- *S5 External interface.* Not applied: UC5 calls no provider. Stripe appears only in UC4's settlement and UC6's refunds.
- *S6 Security.* Not applied: no user request reaches UC5; the job runs inside commerce-svc with no session.
- *S7 Scheduling (AD-16; NFR-15, 32, 61)*: impacted `SchedulerTrigger`, `ResolveTeamControl.expireDue` and `selectDue` → resultant `TeamExpirySweepJob` (`@Scheduled`, fixed delay PT1M, so a slow run delays the next instead of overlapping it), `LeasedJobRunner` with `SchedulerLeaseRepository` (lease PT2M, renewed every third of it), a batch of at most 100 teams, and `deferExpiryRetry` (5 minutes) so a failing team cannot block the batch; dynamics @fig-a-uc05-ds-sweep.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30)*: impacted `Team.status` and `currentSize` → resultant the team row lock `lockDeal` (lock 1, `SELECT … FOR UPDATE`), the clock read after the lock (`currentTime`), the status-guarded updates `expireDeal`, `confirmPendingByDealId` and `cancelPendingByDealId`, and one 10 s transaction per team, never one per batch. The expiry transaction writes the team, its orders, its refund obligations and an outbox row. The justification for one transaction over several aggregates (atomicity, one commerce database, the fixed lock order with the team first, the timeout) is in @sec-a-strategies, S8. Expiry takes the team lock first and the payment locks (lock 3) last: `@Order(10)` before `@Order(20)` keeps the order writes before UC6's payment locks; dynamics @fig-a-uc05-ds-expire, @fig-a-uc05-ds-race.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63)*, backend ADR 0002: impacted the links from `ResolveTeamControl` to `Order` and to UC6 → resultant the domain events `TeamSucceeded` and `TeamExpired`, published through `ApplicationEventPublisher` inside the resolving transaction; the `BEFORE_COMMIT` subscribers `OrderLifecycleSubscriber` (`@Order(10)`), `TeamExpiryRefundSubscriber` (`@Order(20)`, UC6) and `IntegrationOutboxWriter` (lowest precedence); and the `AFTER_COMMIT` subscriber `OpenDealOfferCacheSubscriber`; dynamics @fig-a-uc04-ds-events, @fig-a-uc05-ds-expire-events.

On the success path `ResolveTeamControl` has no class of its own: it dissolves into the Observer. `teamFilled` becomes the event `TeamSucceeded`, published by `PaidGroupDealService`, and the order settlement becomes `OrderLifecycleSubscriber`. On the expiry path it is `GroupDealExpirySweeper` (selection) and `GroupDealExpiryService` (one team). @tbl-a-uc05-map shows where each analysis object lands; every class on the three design class diagrams appears in it.

#atable(
  [UC5 analysis → design mapping. Every analysis object of @fig-a-uc05-ac, and every design class of @fig-a-uc05-dc-sweep to @fig-a-uc05-dc-events. The control row is split by phase; the first class named in each is the phase's entry point.],
  <tbl-a-uc05-map>,
  columns: (1.15fr, 0.75fr, 2.6fr, 0.65fr, 0.95fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`SchedulerTrigger`], [boundary], [Inbound adapter for time (bootstrap scheduling): `TeamExpirySweepJob.run()`, configured by `SchedulingProperties.ExpirySweep`. Spring's scheduler thread plays the Scheduler.], [S7], [@fig-a-uc05-dc-sweep],
  [`JoinTeamControl`], [control (UC4)], [Its after-payment phase (A.6): `OrderFulfilmentAdapter` calls `PaidGroupDealService.joinConfirmed`.], [S1, S3], [@fig-a-uc05-dc-resolve],
  [`ResolveTeamControl` (success)], [control], [Entry point `PaidGroupDealService.joinConfirmed`, which publishes `TeamSucceeded`; the orders are confirmed by `OrderLifecycleSubscriber.onTeamSucceeded`.], [S1, S9], [@fig-a-uc05-dc-resolve, @fig-a-uc05-dc-events],
  [`ResolveTeamControl` (expiry: selection)], [control], [Entry point `TeamExpirySweepJob.run` → `LeasedJobRunner.run` → `GroupDealExpirySweeper.sweep` (no transaction).], [S1, S7], [@fig-a-uc05-dc-sweep],
  [`ResolveTeamControl` (expiry: one team)], [control], [Entry point `GroupDealExpiryService.expireOne` (one transaction); consequences `OrderLifecycleSubscriber.onTeamExpired` and, for UC6, `TeamExpiryRefundSubscriber.onTeamExpired`.], [S1, S8, S9], [@fig-a-uc05-dc-resolve, @fig-a-uc05-dc-events],
  [`Team`], [entity], [Aggregate root `GroupDeal` with `DealStatus`: `admit` → `joinPaid` (returns `PaidAdmission`), `expire` → `GroupDeal.expire`, `canMoveTo` → `DealStatus.canTransitionTo`. Port `PaidGroupDealRepository`, adapter `PaidGroupDealRepositoryImpl`, mapper `PaidGroupDealMapper`, table `biz_group_deal`.], [S1, S4, S8], [@fig-a-uc05-dc-resolve],
  [`Membership`], [entity], [`biz_group_member` rows of the `GroupDeal` aggregate, whose domain record is `PaidMember` (A.6); read by the outbox writer to address the notices (@sec-a-notifications).], [S4, S9], [—],
  [`Order`], [entity], [Aggregate root `Order` with `OrderStatus` (class not drawn here; @fig-a-uc04-dc-fulfil). `confirm` and `cancel` → the status-guarded bulk updates `OrderRepository.confirmByDealId` and `cancelByDealId`; table `biz_order`.], [S4, S9], [@fig-a-uc05-dc-events],
  [(none)], [—], [Introduced by a strategy, with no analysis counterpart: `LeasedJobRunner`, `LeasedJobRunner.LeaseAwareWork`, `SchedulerLeaseRepository`, `GroupDealExpirySweeper.Result` (S7); `ApplicationEventPublisher`, `TeamSucceeded`, `TeamExpired`, `IntegrationOutboxWriter`, drawn as one subscriber (its payloads `TeamSucceededV1` and `TeamExpiredV1`, mapper and recipient lookup: @fig-a-ntf-dc-produce), `OpenDealOfferCacheSubscriber` (S9). Its port `OpenDealOfferCache` (adapter `RedisOpenDealOfferCache`) is not drawn here; it is UC1's (@sec-a-uc01).], [S7, S9], [@fig-a-uc05-dc-sweep, @fig-a-uc05-dc-events],
)

=== Design Class Diagrams <sec-a-uc05-dc>

The design model has 24 classes and interfaces, drawn in three slices along the flow: the expiry sweep (@fig-a-uc05-dc-sweep), resolving one team in the group-buy module (@fig-a-uc05-dc-resolve) and the subscribers of the result (@fig-a-uc05-dc-events). All of them run in commerce-svc. A class shared by two slices shows only the members that slice uses. `TeamExpirySweepJob` and `LeasedJobRunner` live in the bootstrap module's scheduling package. The job is the inbound adapter for time, as a controller is for HTTP; `LeasedJobRunner` is the «utility» that gives every scheduled job its lease. `ApplicationEventPublisher` is Spring's interface, the one framework type drawn, because the event sources depend on it (@sec-a-uc05-patterns). An association means a field reference; a dependency means a parameter, a return value or a created object.

The representative persistence chain is `PaidGroupDealRepository` → `PaidGroupDealRepositoryImpl` → `PaidGroupDealMapper` (@fig-a-uc05-dc-resolve). Every other repository here follows the same port → adapter → mapper shape: `SchedulerLeaseRepository` is implemented by `SchedulerLeaseRepositoryImpl` over `SysSchedulerLockMapper`, and `OrderRepository` by `OrderRepositoryImpl` over `BizOrderMapper` (not drawn). The mapper's `selectDueForExpiry` and `deferExpiryRetry` back the port operations `dueForExpiry` and `deferExpiryRetry` of slice 1.

#afig("a-uc05-dc-sweep", [UC5 design class diagram, slice 1: the expiry sweep (UML, commerce-svc). The job hands its work to `LeasedJobRunner` as a `LeasedJobRunner.LeaseAwareWork` callback; the sweeper has no transaction and calls `expireOne` once per team.])

#afig("a-uc05-dc-resolve", [UC5 design class diagram, slice 2: resolving one team in the group-buy module (UML, commerce-svc). Both entry points reach one aggregate, `GroupDeal`, whose `status` attribute (of type `DealStatus`, as in A.6) decides every move; each service only publishes the outcome as an event.])

#afig("a-uc05-dc-events", [UC5 design class diagram, slice 3: the subscribers of a team's resolution (UML, commerce-svc). No arrow leads from the event sources to the order, payment, outbox or cache code; each consequence is one subscriber. The outbox writer's own collaborators are in @fig-a-ntf-dc-produce.])

=== Design Sequence Diagrams <sec-a-uc05-ds>

Each design sequence retells part of @tbl-a-uc05-flows; the step tags in the notes link the two. A `critical` fragment is a database transaction. Listener calls follow the convention of A.6: they come from the transaction owner (`:TransactionTemplate` in settlement, the `@Transactional` proxy of `GroupDealExpiryService` in expiry), synchronously on the committing thread, through Spring's transaction synchronisation. The `BEFORE_COMMIT` subscribers run in `@Order` sequence inside the transaction, so an exception in any of them rolls the resolution back; `AFTER_COMMIT` subscribers cannot undo it.

*Success.* UC5 draws no success sequence of its own, because UC4 already shows it. The filling join is `GroupDeal.joinPaid` returning `teamCompleted: true`, after which `PaidGroupDealService` publishes `TeamSucceeded` (@fig-a-uc04-ds-admit). B3 confirms the orders at `BEFORE_COMMIT` and not at once, because the joiner's own order is saved by UC4 B8 after the admission; B4 writes the outbox row and evicts the cache (@fig-a-uc04-ds-events).

#afig("a-uc05-ds-sweep", [UC5 design sequence diagram, B5–B7 with A1, A2, A3 and E1: the expiry run under its lease (commerce-svc). No transaction spans the batch; a failed team is deferred for 5 minutes and the loop goes on.])

#afig("a-uc05-ds-expire", [UC5 design sequence diagram, B7 with A2 and E1: `GroupDealExpiryService.expireOne` resolves one team (commerce-svc). The aggregate refuses a terminal team, and the status-guarded `expireDeal` settles a race that slipped past it; both mean "already resolved".])

#afig("a-uc05-ds-expire-events", [UC5 design sequence diagram, B8–B10: the subscribers of `TeamExpired`, called by the transaction owner at commit (commerce-svc). Order cancellation, refund obligations and the outbox row commit with the expiry, in `@Order`; the cache is evicted only after commit. The outbox writer's own messages are drawn once, in @fig-a-ntf-ds-produce.])

*Expiry.* @fig-a-uc05-ds-sweep above is the run. The lease makes the run single-flight across servers (A1); its renewal runs in parallel and, if it fails, the loop stops taking teams (A3). The sweeper is deliberately not transactional: one outer transaction would join every `expireOne` and let one failing team mark the whole batch for rollback. @fig-a-uc05-ds-expire resolves one team under lock 1, with the clock read after the lock. Two failures are kept apart: a team already resolved (`DEAL_CLOSED` (`GROUPBUY_409_002`)) is an ordinary replay and returns `false`; a team not yet due (`IllegalStateException`) is a bug in the due-list query and fails loudly (E1). @fig-a-uc05-ds-expire-events shows the subscribers that run inside that transaction before it commits.

*The race.* @fig-a-uc05-ds-race shows A2 for one team with two writers: a paid join for the last seat and the expiry. On the join side the deciding lock is the first one of the settlement, `OrderFulfilmentAdapter.lockForSettlement` → `GroupDealSettlementSupport.lockTeam` (lock 1), taken before the intent (lock 2) and the payment (lock 3); `joinConfirmed` only re-takes it. The expiry's first lock is the same row, so the database orders the two transactions. Because the join waits before it locks its payment, it cannot deadlock with `TeamExpiryRefundSubscriber`, which locks payments at `@Order(20)`. Whichever commits first decides the team; the other meets a terminal status. The join's clock is read after its locks. A join that settles after the deadline finds its quote expired (`CheckoutQuote.expiredAt(now)`; a join quote's deadline is the team's `expireTime`), so `StripeCheckoutService` records `BUSINESS_EXPIRED` and a refund obligation without calling `fulfil`, whichever transaction locked first; `requireJoinWindow` in `joinPaid` is only a defensive check behind it.

#afig("a-uc05-ds-race", [UC5 design sequence diagram, A2: a last-seat join and the expiry race for one team (commerce-svc). txJ and txE are two concurrent transactions, each in its own `critical` fragment (in full: @fig-a-uc04-ds-fulfil, @fig-a-uc05-ds-expire); the join's deciding lock is lock 1 in `lockForSettlement`, if the join wins, the expiry sees `DEAL_CLOSED`; otherwise the join ends as `BUSINESS_EXPIRED`.])

=== Operation Specifications <sec-a-uc05-ops>

@tbl-a-uc05-ops specifies the eight operations that carry UC5's rules. Signatures are those in the code. `IntegrationOutboxWriter.onTeamExpired` is specified in @sec-a-notifications.

#atable(
  [UC5 operation specifications (commerce-svc). Errors are `BizException` error codes unless named as another exception.],
  <tbl-a-uc05-ops>,
  columns: (1.25fr, 1fr, 1fr, 1.2fr, 1fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`LeasedJobRunner.run(job: String, lease: Duration, work: LeasedJobRunner.LeaseAwareWork): void`. No transaction; lease row in `sys_scheduler_lock`.], [Runs one job single-flight across servers (B5, A1, A3). `job` is the lease key; `work` receives `leaseValid`.], [None; every scheduled job calls it.], [Lease taken or the run skipped; renewed every `lease / 3`; released at the end. One trace id and one run counter (`completed`, `skipped`, `failed`).], [Never throws: a failing `tryAcquire` or `work` is logged and counted as `failed`.],
  [`GroupDealExpirySweeper.sweep(limit: int, leaseValid: BooleanSupplier): GroupDealExpirySweeper.Result`. Deliberately not transactional.], [Selects due teams once and resolves each (B6–B7, E1). Returns the four counts.], [`limit` ≥ 1 (100 from configuration).], [Each due team expired, counted as already resolved, or deferred for 5 minutes in `sys_scheduled_item_retry`; stops early when `leaseValid` is false.], [`IllegalArgumentException` for `limit` < 1. `IllegalStateException` when the retry cannot be recorded.],
  [`GroupDealExpiryService.expireOne(dealId: UUID): boolean`. `REQUIRED`, default isolation, timeout 10 s; lock 1 (team).], [Resolves one overdue team (B7–B10). Returns `true` if this call expired it.], [`dealId` not null.], [Team `EXPIRED`; `TeamExpired` published, so the subscribers' writes commit with it. `false` when the team is gone or already resolved (A2).], [`INVALID_PARTICIPATION` (`GROUPBUY_400_001`) for a null id. `IllegalStateException` (not due) and any technical exception roll back (E1).],
  [`GroupDeal.expire(now: Instant): GroupDeal`. Pure; under the caller's lock 1.], [Returns the expired snapshot of this team.], [`now` read after the team lock.], [A new `GroupDeal` with status `EXPIRED`; this snapshot is unchanged.], [`DEAL_CLOSED` when `canTransitionTo(EXPIRED)` is false; `IllegalStateException` when `now` is before `expireTime`; `IllegalArgumentException` for a null `now`.],
  [`DealStatus.canTransitionTo(target: DealStatus): boolean`. Pure.], [The team's whole transition table.], [—], [`true` only when this is `OPEN` and `target` is another non-null status.], [—],
  [`PaidGroupDealRepository.expire(before: GroupDeal, after: GroupDeal, now: Instant): void`. Caller's transaction, under lock 1.], [Persists a validated expiry.], [`after` came from `before.expire(now)`.], [`UPDATE biz_group_deal … WHERE status = 'open'` changes exactly one row; `version` + 1.], [`DEAL_CLOSED` when no open row matched (another writer won).],
  [`OrderLifecycleSubscriber.onTeamSucceeded(event: TeamSucceeded): void`. `BEFORE_COMMIT`, `@Order(10)`, synchronous, inside UC4's settlement transaction.], [Confirms the team's orders (B3; `teamFilled`).], [The joiner's own order already saved (UC4 B8).], [All `pending` orders of the team become `confirmed`; a replay changes no rows.], [Any exception rolls back the whole settlement (E2).],
  [`OrderLifecycleSubscriber.onTeamExpired(event: TeamExpired): void`. `BEFORE_COMMIT`, `@Order(10)`, inside the expiry transaction.], [Cancels the team's pending orders (B8).], [The team is `EXPIRED` in this transaction.], [Every `pending` order of the team becomes `cancelled` with `cancel_time = event.at`; a replay changes no rows.], [Any exception rolls the expiry back (E1).],
)

=== State Machines <sec-a-uc05-states>

Two objects change state in UC5, and this part draws both lifecycles once. Each affects several use cases, which is why it earns a statechart. Other parts name the transitions they cause and point here.

*The team* (@fig-a-uc05-st-deal). UC3 creates it `OPEN` with the paying leader as its only member. UC4's paid joins either leave it `OPEN` with one more member (an internal transition, drawn inside the state) or fill it (`OPEN` → `SUCCEEDED`, UC5 B1–B2). The sweep moves an overdue team to `EXPIRED` (UC5 B7), which UC6 extends with refunds. _admissible_ abbreviates the checks of `joinPaid`: the amount equals the price, the buyer is new, `startTime` ≤ now < `expireTime`, and a seat is free, all read under the team lock. _not admissible_ means one of the in-window checks fails (a full team, a repeat buyer or a wrong amount); the team stays `OPEN` and the payment is owed a refund (UC4 A6). A payment that settles after the deadline never reaches `joinPaid`, swept or not (E3): settlement refuses it as `BUSINESS_EXPIRED` (UC4 A5). `FAILED` and `CANCELED` are declared in `DealStatus` and allowed by `canTransitionTo`, but no code writes them, so they are drawn with no incoming transition (the kit draws no notes in a statechart). No final state is drawn: a succeeded team still receives late paid joins and refuses each with `DEAL_CLOSED` (A4); a payment for an expired team stops at settlement.

#afig("a-uc05-st-deal", [UML statechart of the team (`GroupDeal`, `DealStatus`). Only `OPEN` has outgoing transitions; the internal transitions of `OPEN` and `SUCCEEDED` turn refused paid joins into refunds without changing the status. `FAILED` and `CANCELED` are declared but never entered.])

*The order* (@fig-a-uc05-st-order). UC3 and UC4 create it `PENDING` in the settlement transaction. It moves once, with its team: `CONFIRMED` on `TeamSucceeded`, `CANCELLED` on `TeamExpired`. Both moves are set-based SQL updates guarded by `status = 'pending'`, so a replayed event changes nothing; the chart therefore needs no `[pending]` guard of its own. The domain encodes the same rule in `OrderStatus.canTransitionTo`, used by `Order.confirm()` and `Order.cancel(canceledAt)`, but no production path calls those two methods; on this path the rule is the SQL guard.

#afig("a-uc05-st-order", [UML statechart of the order (`Order`, `OrderStatus`). The order follows its team once; the SQL guard `status = 'pending'` makes a replayed event change nothing.])

The team's statechart turns into code directly, as SAD 03c suggests: the whole table is the one method `DealStatus.canTransitionTo` (@tbl-a-uc05-ops), which `GroupDeal.expire` and `GroupDeal.requireJoinWindow` call before any move.

=== Patterns Applied <sec-a-uc05-patterns>

Four patterns are in the UC5 code; @tbl-a-uc05-patterns maps their roles to classes. Only Observer is a GoF pattern. Two candidates were rejected. `DealStatus` is not the State pattern: it has one transition rule and no behaviour that differs by state, so a table is enough (State is used where behaviour differs, for `ProductStatus` in @sec-a-uc09). `LeasedJobRunner` is not Template Method: no job subclasses it, and the varying step is passed in as an object.

#atable(
  [Patterns in the UC5 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc05-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC5], [Roles → classes], [Consequences]),
  [Observer (GoF, behavioural), realised as in-process publish–subscribe], [A team's resolution must cancel or confirm orders, owe refunds, write an outbox row and evict a cache. Calling each from `expireOne` and `joinConfirmed` would tie group-buy to the order, payment, outbox and cache code.], [Subject (holds and notifies the observers): Spring's multicaster behind `ApplicationEventPublisher`, with transaction synchronisation delivering each call at its phase. Event sources (publish only): `GroupDealExpiryService`, `PaidGroupDealService`. Notifications (events): `TeamSucceeded`, `TeamExpired`. ConcreteObservers (subscribers): `OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber`, `IntegrationOutboxWriter`, `OpenDealOfferCacheSubscriber`; update() = their `on…` methods. Collaboration: @fig-a-uc05-dc-events, @fig-a-uc05-ds-expire-events.], [A new consequence is a new subscriber; the sources do not change (open–closed). Liability: hidden ordering coupling. Reading `expireOne` does not show that orders are cancelled or refunds owed, and correctness rests on annotations: the phase (`BEFORE_COMMIT`) and `@Order(10)` before `@Order(20)`, which keeps the lock order order → payment.],
  [Execute Around Method (idiom, not GoF)], [Every scheduled job needs the same lease, renewal, release, trace id and metrics around a different piece of work.], [Fixed frame: `LeasedJobRunner.run`. Varying step: `LeasedJobRunner.LeaseAwareWork.run(leaseValid)`, passed as `TeamExpirySweepJob::sweepOnce`. Collaboration: @fig-a-uc05-dc-sweep, @fig-a-uc05-ds-sweep.], [All leased jobs share one tested frame. Liability: the work must check `leaseValid` between items and stay idempotent, because a lost lease cannot stop an item already started.],
  [Lease (distributed-systems pattern)], [commerce-svc may run several tasks; two simultaneous runs would double the database load and contend on the same team rows.], [Lease store: `SchedulerLeaseRepository` (`sys_scheduler_lock`, database clock). Holder: `LeasedJobRunner`, whose owner is host plus a UUID.], [At most one run at a time; a crashed holder blocks the others for at most one lease (PT2M, E3). Liability: correctness still rests on the status guards, not on the lease.],
  [Transactional Outbox (architectural)], [notification-svc must hear that a team succeeded or expired, but a broker publish inside the transaction could be lost or sent for a rolled-back resolution.], [Writer: `IntegrationOutboxWriter` (`BEFORE_COMMIT`) through `OutboxMapper` into `sys_outbox`; relay and consumer in @sec-a-notifications.], [The notice exists if and only if the resolution committed. Liability: delivery is at least once and later than the commit.],
)

=== Flow-to-Design Trace <sec-a-uc05-trace>

@tbl-a-uc05-trace shows that each step of @tbl-a-uc05-flows passes from an analysis message to design operations.

#atable(
  [UC5 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc05-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`Team.admit`], [`OrderFulfilmentAdapter` → `PaidGroupDealService.joinConfirmed` → `GroupDeal.joinPaid` returns `PaidAdmission` with `teamCompleted`], [@fig-a-uc04-ds-admit],
  [B2], [`Team.canMoveTo(SUCCEEDED)`], [`GroupDeal.requireJoinWindow` → `DealStatus.canTransitionTo`; new snapshot `SUCCEEDED` with `successTime`; persisted by `insertJoinedMember` (A.6)], [@fig-a-uc04-ds-admit],
  [B3], [`teamFilled`, `Order.confirm` (loop)], [«create» `TeamSucceeded`; `ApplicationEventPublisher.publishEvent`; at `BEFORE_COMMIT` `OrderLifecycleSubscriber.onTeamSucceeded` → `OrderRepository.confirmByDealId`], [@fig-a-uc04-ds-admit, @fig-a-uc04-ds-events],
  [B4], [— (design only)], [`IntegrationOutboxWriter.onTeamSucceeded` (its rows: @sec-a-notifications); at `AFTER_COMMIT` `OpenDealOfferCacheSubscriber.onTeamSucceeded` (evicts the cached offer)], [@fig-a-uc04-ds-events],
  [B5], [`tick`, `expireDue`], [`TeamExpirySweepJob.run` → `LeasedJobRunner.run` → `SchedulerLeaseRepository.tryAcquire`; `LeasedJobRunner.LeaseAwareWork.run` = `TeamExpirySweepJob.sweepOnce`], [@fig-a-uc05-ds-sweep],
  [B6], [`selectDue`], [`GroupDealExpirySweeper.sweep` → `PaidGroupDealRepository.currentTime`, `dueForExpiry`], [@fig-a-uc05-ds-sweep],
  [B7], [`Team.expire`, `canMoveTo(EXPIRED)`], [`GroupDealExpiryService.expireOne` → `lockDeal`, `currentTime`; `GroupDeal.expire` → `DealStatus.canTransitionTo`; `PaidGroupDealRepository.expire` → `PaidGroupDealMapper.expireDeal`], [@fig-a-uc05-ds-expire],
  [B8], [`Order.cancel` (loop)], [«create» `TeamExpired`; `publishEvent`; `OrderLifecycleSubscriber.onTeamExpired` → `OrderRepository.cancelByDealId`], [@fig-a-uc05-ds-expire, @fig-a-uc05-ds-expire-events],
  [B9], [«extend» UC6], [`TeamExpiryRefundSubscriber.onTeamExpired` (`@Order(20)`) → `TeamExpiryRefundService.recordExpiredTeam` (@sec-a-uc06)], [@fig-a-uc05-ds-expire-events, @fig-a-ntf-ds-produce],
  [B10], [— (design only)], [`IntegrationOutboxWriter.onTeamExpired` («create» `TeamExpiredV1`, its row: @sec-a-notifications); at `AFTER_COMMIT` `OpenDealOfferCacheSubscriber.onTeamExpired` (evicts the cached offer)], [@fig-a-uc05-ds-expire-events, @fig-a-ntf-ds-produce],
  [A1], [— (design only)], [`SchedulerLeaseRepository.tryAcquire` returns false; the run is counted `skipped`], [@fig-a-uc05-ds-sweep],
  [A2], [`expire` refused], [`GroupDeal.expire` throws `DEAL_CLOSED`, or `expireDeal` returns 0 and the repository throws `DEAL_CLOSED`; `expireOne` returns false; the sweeper counts `alreadyResolved`], [@fig-a-uc05-ds-expire, @fig-a-uc05-ds-race],
  [A3], [— (design only)], [`SchedulerLeaseRepository.renew` returns false; `leaseValid` false ends the loop; the run is counted `failed`], [@fig-a-uc05-ds-sweep],
  [A4], [`admit` refused], [`OrderFulfilmentAdapter.lockForSettlement` → `GroupDealSettlementSupport.lockTeam` (lock 1); a `SUCCEEDED` team: `GroupDeal.requireJoinWindow` throws `DEAL_CLOSED` → `compensation_required` (UC4 A6); an `EXPIRED` team: `StripeCheckoutService` returns `BUSINESS_EXPIRED` before `fulfil` (UC4 A5)], [@fig-a-uc05-ds-race, @fig-a-uc04-ds-admit, @fig-a-uc04-ds-fulfil],
  [E1], [— (design only)], [A `RuntimeException` (`IllegalStateException` included) leaves `expireOne`; its transaction rolls back; `GroupDealExpirySweeper` counts `failed` and calls `deferExpiryRetry`], [@fig-a-uc05-ds-sweep, @fig-a-uc05-ds-expire],
  [E2], [—], [An exception in `onTeamSucceeded` rolls back UC4's settlement (UC4 E1)], [@fig-a-uc04-ds-events],
  [E3], [— (design only)], [`blocbuy.scheduling.enabled` false registers no job; a stopped holder's lease lapses after PT2M; overdue teams refuse new join terms (`requireJoinWindow`) and late payments (`BUSINESS_EXPIRED`) meanwhile], [@fig-a-uc05-ds-sweep],
)
