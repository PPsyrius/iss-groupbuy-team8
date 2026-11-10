#import "lib-a.typ": *

== UC6 Refund Members of an Expired Team <sec-a-uc06>

UC6 returns the money of every buyer who paid into a team that expired before it filled. It has two halves that run at different times. The first half runs inside UC5's expiry transaction: the team's closure and one refund obligation per paid member are recorded together, or not at all. The second half runs later, on the system's own schedule: a refund worker takes one obligation at a time, asks Stripe to refund the original payment, and records the answer. Stripe is never called while a database transaction is open. The same worker also pays back a payment that UC3 or UC4 refused after the money was taken (@sec-a-uc04); only the way the obligation is created differs. UC6 is designed and built; its status on AWS is given by capability in @tbl-status. The one exception is the operator's reconcile (A11), which is designed and unit-tested but not offered to operators.

=== Use Case and Flows of Events <sec-a-uc06-flows>

- *Actors.* Stripe (secondary; executes the refund). The Scheduler reaches UC6 through UC5, which UC6 «extend»s at UC5's extension point _resolved_ {team expired} (@sec-a-uc05). The buyer benefits but does not act. An operator takes part only in the alternate flow A11.
- *Trigger.* UC5 marks an open team past its deadline as expired.
- *Services.* commerce-svc owns every step (its group-buy, order and payment modules). UC6 asks identity-svc nothing, so an identity outage cannot stop a refund.
- *Preconditions.* The team was open and its deadline has passed. Scheduled processing is switched on. Checkout need not be enabled: obligations are recorded and paid even while new checkouts are switched off.
- *Postconditions on success.* The team is expired, its orders are cancelled, and each succeeded payment that bought a seat in it has exactly one refund obligation. Each obligation ends with exactly one Stripe refund, a matching local status and an audit record of every attempt.
- *Postconditions on an uncertain case.* The obligation is parked for an operator with the reason recorded, and no second refund is ever asked for.

@tbl-a-uc06-flows numbers every step. The diagrams below repeat these numbers, and @tbl-a-uc06-trace maps each step to the design operations that realise it.

#atable(
  [UC6 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where an alternate or exception flow branches off.],
  <tbl-a-uc06-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The deadline of an open team passes. UC5 expires the team and cancels its pending orders (@sec-a-uc05). UC6 starts inside that same unit of work.],
  [*B2*], [], [The system confirms that the team is still expired and holds it, so that no payment for this team can settle while the refunds are recorded.],
  [*B3*], [], [The system finds every succeeded payment that bought a seat in the team, the leader's included, and holds them.],
  [*B4*], [], [The system records one refund obligation per payment, for the full amount, due at once. The expiry and every obligation exist together or not at all.],
  [*B5*], [], [Every 30 seconds the system picks up the oldest due obligation and reserves it for two minutes, so that no other attempt takes it meanwhile.],
  [*B6*], [], [The system checks that the payment succeeded and that the obligation's amount and currency (Singapore dollars) match it.],
  [*B7*], [], [The system asks Stripe to refund the original payment for the full amount, identified by the obligation, so that Stripe treats any repeat of the request as the same refund.],
  [*B8*], [], [The system checks that Stripe's answer names this payment, this obligation, the amount and the currency, then records Stripe's refund reference and its status: succeeded. Every attempt is kept in an audit trail.],
  [*B9*], [], [The buyer's receipt page shows "Refund sent to your original payment method" (@sec-a-pay-upfront).],
  [*A1*], [B1], [The team filled, or another run expired it, first: UC5 stops and UC6 never starts, so nothing is owed twice.],
  [*A2*], [B5], [Nothing is due: the system waits for the next run.],
  [*A3*], [B6], [The payment and the obligation disagree: the obligation is parked for an operator. No refund is asked for.],
  [*A4*], [B7], [A Stripe refund reference is already stored, because an earlier answer said the refund was still pending: the system reads that refund from Stripe instead of asking again, and continues at B8.],
  [*A5*], [B7], [23 hours have passed since the first attempt and no reference is stored: the system no longer asks for a refund. It searches Stripe for the refund marked with this obligation. Exactly one continues at B8; none or several park the obligation for an operator.],
  [*A6*], [B7], [Stripe is unreachable, limits the rate or fails for a while: the obligation stays due and is tried again 30 seconds later with the same identity.],
  [*A7*], [B7], [Stripe refuses for good (the payment is not refundable, or the request is rejected): the obligation is parked for an operator.],
  [*A8*], [B8], [Stripe's answer does not match the payment or the obligation: the obligation is parked for an operator.],
  [*A9*], [B8], [Stripe reports the refund as pending: the obligation stays due and A4 reads it again later. Failed or canceled: the obligation is marked failed and waits for an operator. Any other status: it is parked for an operator.],
  [*A10*], [B7–B8], [The system stops after Stripe accepted the refund but before B8 is recorded: the reservation lapses after two minutes, the next attempt sends the same request, and Stripe returns the same refund.],
  [*A11*], [A3–A9], [An operator reconciles a parked or failed obligation against a Stripe refund they name. If the refund matches, its status is recorded; otherwise the obligation stays parked. It can never ask for a new refund. Designed and unit-tested; no screen or command offers it yet.],
  [*E1*], [B2–B4], [Recording fails: the team is no longer expired, the number of obligations differs from the payments held, or the database fails. The whole expiry rolls back, UC5's changes included, and the next sweep tries the team again.],
  [*E2*], [B8], [An attempt finishes after its reservation lapsed and another attempt took the obligation over: its result is discarded and the newer attempt's result stands.],
)

=== Analysis Class Diagram <sec-a-uc06-ac>

@fig-a-uc06-ac is the analysis model of UC6. `RefundMembersControl` realises the use case and is the only object that talks to the boundaries and to the refund entities. It has three entry points, one per major flow: `recordRefundsOwed` (B2–B4), `payNextRefund` (B5–B8) and `reconcile` (A11), plus the read `refundStatus` for the receipt (B9). The Scheduler reaches it through `SchedulerTrigger`, the boundary UC5 also uses; Stripe through the gateway boundary `PaymentGateway`; the operator through `ReviewUI`; the buyer through `ReceiptUI`. `ResolveTeamControl` is UC5's control (@sec-a-uc05) and appears only because it starts the first flow: an «extend» is realised as a call from the extended use case's control to the extending one. The Scheduler, Stripe, the operator and the buyer are actors and are drawn only in the sequence diagrams.

`Team`, `PurchaseIntent`, `Payment` and `Refund` are the names of the system-level model (@sec-a-reading-system); a payment reaches its team through the purchase intent it pays. `RefundAttempt` is the audit record of each try, a part of its refund (composition). A payment owes at most one refund. The refund is associated with its payment, not composed into it: it is its own aggregate, with its own lifecycle (@sec-a-uc06-states) and its own transactions, and it refers to its payment by identity. `claimNextDue` and `claimForReview` are class-scope queries («static»): they find a refund, rather than ask one. `Refund.providerReference` is Stripe's identifier, an external key, not a reference to another entity. `RefundStatus` is the enumeration whose values the statechart defines (@sec-a-uc06-states).

#afig("a-uc06-ac", [UC6 analysis class diagram (UML class notation, BCE stereotypes). `RefundMembersControl` alone talks to `PaymentGateway`; each `Payment` owes at most one `Refund`, an aggregate of its own that records each of its attempts.])

=== Analysis Sequence Diagrams <sec-a-uc06-as>

UC6 has three major flows, each drawn once, in flow order. @fig-a-uc06-as-record records the refunds owed, inside UC5's expiry. @fig-a-uc06-as-pay pays one refund; the provider's possible answers (A4–A9) are `alt` operands because they use the same lifelines and differ only in what Stripe says. @fig-a-uc06-as-reconcile is the operator's reconcile (A11): a different actor starts it and it can never create a refund, so it is a flow of its own. E1 and E2 need design concepts (a rolled-back transaction, a stale reservation) and appear only in the design sequences.

#afig("a-uc06-as-record", [UC6 analysis sequence diagram, B1–B4. UC5's control hands the expired team to `RefundMembersControl`, which asks each succeeded payment for a refund; the payment, not the control, creates its `Refund`.])

#afig("a-uc06-as-pay", float: true, [UC6 analysis sequence diagram, B5–B8 with A2–A10. The payment is checked before money is asked for, and the answer is checked before it is believed; a temporary failure keeps the refund due, and anything unsafe parks it.])

#afig("a-uc06-as-reconcile", float: true, [UC6 analysis sequence diagram, alternate flow A11. The operator can only confirm a refund that already exists at Stripe; any doubt leaves the `Refund` parked.])

=== Transition: Strategies and Mapping <sec-a-uc06-transition>

The strategies of @sec-a-strategies turn the analysis model into the design model. All nine apply; S2 only by reference:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `Refund`, `Payment` → resultant records `RefundWork`, `RefundGateway.Snapshot`, `TeamExpired`, and the final classes `RefundService`, `TeamExpiryRefundService`; `RefundStatus` becomes six `String` constants of `RefundService` (@sec-a-uc06-states).
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `ReceiptUI` → the receipt's refund label (@sec-a-pay-upfront); `ReviewUI` → no design element (A11).
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `ResolveTeamControl`, `Team` → port `PaymentBusinessPort.lockExpiredTeam` (implemented by `OrderFulfilmentAdapter` over `GroupDealSettlementSupport`) and the event `TeamExpired` between modules (backend ADR 0001); no call leaves commerce-svc; dynamics @fig-a-uc06-ds-record.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Refund`, `RefundAttempt` → `biz_refund` behind `RefundRepository` → `RefundRepositoryImpl` → `RefundMapper`, and `log_refund_attempt` (`RefundMapper.audit`). The refund is its own aggregate, so its lifecycle has its own repository. Only its creation goes through `CheckoutPaymentRepository.requireRefundsForExpiredTeam`, because one SQL statement locks the payments (lock 4) and inserts their obligations, inside the expiry transaction; dynamics @fig-a-uc06-ds-record, @fig-a-uc06-ds-claim.
- *S5 External interface (backend ADRs 0001, 0004, 0005; NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66):* impacted `PaymentGateway` → port `RefundGateway`, adapter `StripeRefundGateway`, idempotency key `RefundWork.providerKey()`, a 23-hour cutoff after which only `findRefund` is used, and retryable versus permanent failures; dynamics @fig-a-uc06-ds-provider. The common Stripe call handling is in @sec-a-pay-upfront.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `PaymentGateway`, `ReviewUI` → money only returns to the original PaymentIntent; live-mode payments are refused; the audit trail stores result codes only; `reconcile` has no endpoint.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* impacted `SchedulerTrigger` → `RefundWorkerJob` under `LeasedJobRunner` and the job lease of `SchedulerLeaseRepository`, one attempt per tick; dynamics @fig-a-uc06-ds-claim.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30):* impacted `Team`, `Payment`, `Refund` → lock 1 (team), lock 4 (payments in id order), `UNIQUE (payment_id)` with `ON CONFLICT` and a count check; on the worker side `FOR UPDATE SKIP LOCKED`, a two-minute row lease and the `version` fencing token, with no transaction open during a provider call; dynamics @fig-a-uc06-ds-record to @fig-a-uc06-ds-provider. One expiry transaction writes the team, its orders and the refund rows, as justified in A.2 S8 (@sec-a-strategies): one fact (backend ADR 0002), one database, the global lock order, a 10 s timeout.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* impacted `ResolveTeamControl`, `RefundMembersControl` → `GroupDealExpiryService` publishes `TeamExpired`; `TeamExpiryRefundSubscriber` reacts at `BEFORE_COMMIT` with `@Order(20)`, after the order subscriber's `@Order(10)` (backend ADR 0002). The outbox row for notification-svc is drawn in @sec-a-notifications. A finished refund publishes nothing.

@tbl-a-uc06-map shows where each analysis object lands. Every class on the three design class diagrams appears in it.

#atable(
  [UC6 analysis → design mapping. Every analysis object of @fig-a-uc06-ac, and every design class of @fig-a-uc06-dc-record to @fig-a-uc06-dc-provider. All design classes are in commerce-svc.],
  <tbl-a-uc06-map>,
  columns: (0.95fr, auto, 2.9fr, auto, 0.75fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`SchedulerTrigger`], [boundary], [Bootstrap: `RefundWorkerJob`, `LeasedJobRunner` (callback `LeasedJobRunner.LeaseAwareWork` not drawn); port `SchedulerLeaseRepository` (`sys_scheduler_lock`). The expiry tick is UC5's.], [S7, S8], [@fig-a-uc06-dc-worker],
  [`PaymentGateway`], [boundary], [Domain port `RefundGateway` with `RefundGateway.Snapshot`; infrastructure adapter `StripeRefundGateway`; failure kinds in `PaymentErrorCode` (not drawn).], [S5, S6], [@fig-a-uc06-dc-provider],
  [`ReviewUI`], [boundary], [Not built: no controller, command or job calls `RefundService.reconcile`. Not drawn.], [S6], [—],
  [`ReceiptUI`], [boundary], [`PaymentBrowserService.receipt` reads `RefundRepository.status` (@sec-a-pay-upfront; not drawn).], [S2], [—],
  [`ResolveTeamControl`], [control (UC5)], [`GroupDealExpiryService`, which publishes `TeamExpired` through `ApplicationEventPublisher` (@sec-a-uc05).], [S9], [@fig-a-uc06-dc-record],
  [`RefundMembersControl`, record phase], [control], [Entry point `TeamExpiryRefundSubscriber.onTeamExpired`; then `TeamExpiryRefundService.recordExpiredTeam` (application layer, payment module).], [S3, S8, S9], [@fig-a-uc06-dc-record],
  [`RefundMembersControl`, pay phase], [control], [Entry point `RefundService.processNext` (application layer, payment module).], [S5, S7, S8], [@fig-a-uc06-dc-worker, @fig-a-uc06-dc-provider],
  [`RefundMembersControl`, reconcile phase], [control], [Entry point `RefundService.reconcile`, with no caller (A11).], [S5, S6], [@fig-a-uc06-dc-provider],
  [`Team`], [entity], [Aggregate root `GroupDeal`, locked through `PaymentBusinessPort` → `OrderFulfilmentAdapter` → `GroupDealSettlementSupport` (`GroupDealSettlementSupportImpl`) → `PaidGroupDealRepository.lockDeal`.], [S3, S8], [@fig-a-uc06-dc-record],
  [`PurchaseIntent`], [entity], [Not navigated: a payment records the team it fulfilled (`biz_payment.fulfilled_deal_id`). Not drawn.], [S4], [—],
  [`Payment`], [entity], [Aggregate root `CheckoutPayment` (@sec-a-pay-upfront), table `biz_payment`; port `CheckoutPaymentRepository`, adapter `CheckoutPaymentRepositoryImpl`, mapper `PaymentMapper`. `agreesWith` becomes `RefundWork.valid()`.], [S4, S8], [@fig-a-uc06-dc-record, @fig-a-uc06-dc-worker],
  [`Refund`], [entity], [Its own aggregate: one `biz_refund` row, without a domain class (@sec-a-uc06-patterns). Created by `CheckoutPaymentRepository.requireRefundsForExpiredTeam` (`PaymentMapper.refundExpiredTeam`, S4); its lifecycle behind `RefundRepository`, `RefundRepositoryImpl` and `RefundMapper`. A claim returns the claim snapshot `RefundWork` (a data object).], [S1, S4, S8], [@fig-a-uc06-dc-record, @fig-a-uc06-dc-worker],
  [`RefundAttempt`], [entity], [One `log_refund_attempt` row, inserted by `RefundMapper.audit`; never read by the application.], [S4], [@fig-a-uc06-dc-worker],
  [(none)], [—], [Introduced by a strategy, with no analysis counterpart: `PaymentBusinessPort`, `GroupDealSettlementSupport` (S3); `ApplicationEventPublisher`, `TeamExpired` (S9).], [S3, S9], [@fig-a-uc06-dc-record],
)

=== Design Class Diagrams <sec-a-uc06-dc>

The design model has 25 classes and interfaces in three slices: the obligations recorded inside the expiry (@fig-a-uc06-dc-record), the worker and its claim (@fig-a-uc06-dc-worker), and the provider call (@fig-a-uc06-dc-provider). Static members are marked «static». The chain port → adapter → mapper is drawn once, for `RefundRepository`; the other repositories follow it. Spring's multicaster calls the subscriber at `BEFORE_COMMIT`; the Observer roles are in @tbl-a-uc06-patterns, so no publisher-to-subscriber dependency is drawn.

#afig("a-uc06-dc-record", [UC6 design class diagram, slice 1: refund obligations inside the expiry (UML, commerce-svc). Group-buy only publishes `TeamExpired`; the payment module's subscriber reaches the team back only through `PaymentBusinessPort`, and writes the obligations through its own repository.])

#afig("a-uc06-dc-worker", [UC6 design class diagram, slice 2: the refund worker and its claim (UML, commerce-svc). The six `String` constants of `RefundService` are the refund states; the representative persistence chain is `RefundRepository` → `RefundRepositoryImpl` → `RefundMapper`.])

#afig("a-uc06-dc-provider", [UC6 design class diagram, slice 3: the provider call (UML, commerce-svc). `RefundService` depends only on the port `RefundGateway`; `RefundWork` carries the facts that `valid()` checks and the stable key `providerKey()`.])

=== Design Sequence Diagrams <sec-a-uc06-ds>

Each design sequence retells part of @tbl-a-uc06-flows, linked by the step tags in its notes. A `critical` fragment is one database transaction, labelled with its propagation, isolation and timeout. Locks follow the global order of A.2 S8: here the team is lock 1 and the payments lock 4; the order rows (lock 3) that UC5 cancels in between are locked by its guarded bulk update.

*Recording the obligations* (@fig-a-uc06-ds-record). Spring calls the subscriber synchronously on the committing thread, so an exception rolls the expiry back (E1). The `TransactionTemplate` joins the expiry transaction (`REQUIRED`). The team lock is re-taken, then the payments are locked in id order and the obligations inserted in one statement; an identical existing row counts as written (`ON CONFLICT (payment_id)`), so a replayed expiry adds nothing.

#afig("a-uc06-ds-record", [UC6 design sequence diagram, B2–B4 with E1 (commerce-svc). The subscriber runs before commit on the expiry's own transaction; the payment rows are locked after the team, and a count mismatch rolls back the expiry.])

*Claiming one obligation* (@fig-a-uc06-ds-claim). `processNext` runs with no transaction open and claims one row in a short transaction of its own, with `FOR UPDATE SKIP LOCKED`, so a second worker passes over a claimed row instead of waiting.

#afig("a-uc06-ds-claim", [UC6 design sequence diagram, B5–B6 with A2 and A3 (commerce-svc). The claim commits before Stripe is called, so the row lock lasts milliseconds; the row lease, not the lock, keeps other attempts away while Stripe answers.])

*Asking Stripe and finishing* (@fig-a-uc06-ds-provider). A stored refund id is read back (A4), after 23 hours only a search is allowed (A5), otherwise `createRefund` sends the idempotency key (B7). `apply` checks the answer with `matches` and calls `finish`, which runs in its own transaction, fenced by `version` (E2).

#afig("a-uc06-ds-provider", [UC6 design sequence diagram, B7–B8 with A4–A10 and E2 (commerce-svc). No transaction is open during a provider call; `matches` and the closing `finish` run inside `apply`, and every outcome ends in one fenced `finish`.])

*Operator reconcile* (@fig-a-uc06-ds-reconcile). It claims only a `review` or `failed` row and applies the worker's checks. A provider error keeps the row in `review`; only a matched refund that Stripe still reports as pending returns it to the worker (`processing`). No boundary calls it, so the diagram starts at the operator.

#afig("a-uc06-ds-reconcile", [UC6 design sequence diagram, A11 (commerce-svc). Reconcile can only retrieve an existing refund and map it with the worker's checks; it has no path that creates a refund. No boundary calls it yet.])

=== Operation Specifications <sec-a-uc06-ops>

@tbl-a-uc06-ops specifies the eight operations that carry UC6's rules. Signatures are those in the code. Errors are named enum constant first, with the wire code in brackets: `PAYMENT_STATE_INVALID` (`PAYMENT_409_001`), `PAYMENT_PROVIDER_ERROR` (`PAYMENT_502_001`) and `PAYMENT_PROVIDER_REJECTED` (`PAYMENT_502_002`), all of `PaymentErrorCode`.

#atable(
  [UC6 operation specifications (commerce-svc). Every transaction uses PostgreSQL's default `READ COMMITTED`.],
  <tbl-a-uc06-ops>,
  columns: (1.25fr, 1fr, 1fr, 1.2fr, 1fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`TeamExpiryRefundSubscriber.onTeamExpired(event: TeamExpired): void`. `BEFORE_COMMIT`, `@Order(20)`, inside the expiry transaction.], [Entry for B2–B4; passes `event.dealId()` on.], [`OrderLifecycleSubscriber.onTeamExpired` (`@Order(10)`) has cancelled the orders.], [Obligations recorded, or the expiry rolls back.], [Any exception from below rolls the expiry back (E1).],
  [`TeamExpiryRefundService.recordExpiredTeam(dealId: UUID): void`. `TransactionTemplate`, `REQUIRED` (joins); lock 1 through `PaymentBusinessPort.lockExpiredTeam`.], [Holds the expired team and records what it owes (B2–B4).], [Called inside the expiry transaction; `OrderFulfilmentAdapter.lockExpiredTeam` is `MANDATORY`.], [Team row still locked; one obligation per succeeded payment, reason `TEAM_EXPIRED`, source event `team-expired-<dealId>`.], [`IllegalStateException` "Expired team no longer exists" or "Team is not expired" from `GroupDealSettlementSupportImpl.lockExpiredTeam`.],
  [`CheckoutPaymentRepositoryImpl.requireRefundsForExpiredTeam(dealId: UUID, reason: String, eventId: String): void`. Caller's transaction; lock 4 (`PaymentMapper.lockRefundableMemberIds`, `FOR UPDATE`, id order).], [Writes the obligations in one statement (B3–B4).], [Team row locked by the caller.], [One `pending` `biz_refund` row per payment with `fulfilled_deal_id = dealId`, result `FULFILLED`, status `succeeded`; an identical existing row counts as written.], [`IllegalStateException` "Expired-team refund write conflict" when written ≠ locked.],
  [`RefundService.processNext(): boolean`. No transaction of its own; the claim and each finish run in separate `TransactionTemplate` transactions, `REQUIRED` (new), 5 s.], [One refund attempt (B5–B8). Returns `false` only when nothing was due.], [Called by `RefundWorkerJob` under the job lease.], [At most one obligation moved to `succeeded`, `processing`, `failed` or `review`, with an audit row.], [`BizException` from the gateway is caught and classified; any other exception ends the tick, and the row lease lapses (A10).],
  [`RefundRepositoryImpl.claimNext(): RefundWork`. Caller's claim transaction; `RefundMapper.claimNext` with `FOR UPDATE SKIP LOCKED`.], [Reserves the oldest due obligation (B5).], [None.], [`lease_until` = now + 2 min, `version` + 1, `first_attempt_at` set once; audit `CLAIMED`. Returns `null` when nothing is due.], [None expected.],
  [`RefundGateway.createRefund(work: RefundWork): RefundGateway.Snapshot`, implemented by `StripeRefundGateway`. Outside any transaction.], [Asks Stripe to refund the original PaymentIntent, idempotency key `work.providerKey()`, metadata `obligation_id` (B7).], [`work.valid()`; fewer than 23 hours since the first attempt; no stored refund id.], [Returns Stripe's refund as a snapshot; Stripe returns the same refund for a repeated key.], [`PAYMENT_STATE_INVALID` (PaymentIntent live, not succeeded, or amount or currency differs); `PAYMENT_PROVIDER_REJECTED`; `PAYMENT_PROVIDER_ERROR`.],
  [`RefundRepository.finish(work: RefundWork, providerId: String, status: String, result: String): void`. Own transaction, `REQUIRED` (new), 5 s.], [Records one outcome (B8, A3–A9).], [The claim's `version` is still current.], [Status, provider id and `last_result` stored; lease cleared; next attempt in 30 s; audit `result` when one row changed. A stale `version` changes nothing (E2).], [None expected.],
  [`RefundService.reconcile(paymentId: UUID, providerId: String): boolean`. Claim and finish as for `processNext`; `RefundMapper.claimReview`.], [Confirms a refund the operator names (A11). Returns `false` when no `review` or `failed` row was claimed.], [`providerId` is `re_` followed by 1–128 letters or digits.], [Matching refund: status recorded; otherwise stays `review`. Never creates a refund.], [`IllegalArgumentException` "Invalid provider refund reference".],
)

=== State Machines <sec-a-uc06-states>

UC6 owns the lifecycle of the refund obligation (@fig-a-uc06-st-refund), whatever created it: a team expiry here, or a refused settlement in @sec-a-uc03, @sec-a-uc04 and @sec-a-pay-upfront. Both creations are the one initial transition, `«create» / insert 'pending'`: `CheckoutPaymentRepository.requireRefundsForExpiredTeam` writes one row per paid member when a team expires, and `CheckoutPaymentRepository.requireRefund` (`PaymentMapper.refund`) writes one row when a settlement is refused after the money moved, with the rejection as its reason (for example `GROUP_REJECTED`, `INTENT_EXPIRED` or `BUSINESS_EXPIRED`). The state is the column `biz_refund.status`. Its values are not a Java enum: they are six private static final `String` constants of `RefundService` (`PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REVIEW`, `CANCELED`), holding the lowercase database codes. Five are states. `CANCELED` is never stored: it names Stripe's status `canceled`, which maps to `failed`. There is no transition table in the code. The statechart is enforced by SQL: `RefundMapper.claimNext` claims only `pending` and `processing` rows, `RefundMapper.claimReview` only `review` and `failed` rows, and `finish` writes the outcome only if `version` is unchanged.

_Due_ means that the next attempt time has passed and no live row lease holds the row. There are two choices, because `processNext` and `reconcile` treat a provider error differently (`RefundService.reconcile`). A `provider …` guard names the Stripe status of a matching answer. In the worker's choice, `retryable` is a temporary provider error (A6), and `unsafe` covers every case parked for an operator: `valid()` false (A3), a mismatching answer (A8), a permanent refusal (A7), no single match after 23 hours (A5) and an unknown status (A9). In the reconcile's choice, a mismatch, a refusal, any provider error or an unknown status keeps the row in `review`; only a matched refund still pending returns it to the worker. Every `finish` stamps the next attempt 30 seconds ahead, but only `pending` and `processing` are claimed again. `succeeded` is the only final state. The receipt shows the state in upper case (@sec-a-pay-upfront).

#afig("a-uc06-st-refund", [UML statechart of the refund obligation (`biz_refund.status`). The worker's choice and the reconcile's choice differ: a provider error retries in the worker but stays in `review` in a reconcile. «create» is `requireRefundsForExpiredTeam` (B4) or `requireRefund` (a refused settlement); every transition out of a choice has the effect `finish`.])

An obligation in `review` or `failed` needs a person. The admin attention list (backend ADR 0006, @sec-a-uc09) omits refunds owed after an expiry, so such a case shows only in the warning log "Refund needs operator review" and in `biz_refund`; reconcile (A11) has no caller yet.

The team's `OPEN` → `EXPIRED` and the orders' `PENDING` → `CANCELLED` are UC5's (@sec-a-uc05); UC6 only checks `GroupDeal.status()`.

=== Patterns Applied <sec-a-uc06-patterns>

@tbl-a-uc06-patterns gives the four patterns in the UC6 code. Only Observer is a GoF pattern. One thing is deliberately absent: a domain class for the refund aggregate. Its rules live in `RefundService` and in `RefundMapper`'s SQL; `RefundWork` is a claim snapshot (a data object, the course's DTO) with two queries, `providerKey()` and `valid()`, not an aggregate root with commands. Every state change is one fenced `UPDATE`; the liability is that no single Java class checks the lifecycle, hence the statechart's SQL references.

#atable(
  [Patterns in the UC6 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc06-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC6], [Roles → classes], [Consequences]),
  [Observer (GoF, behavioural), realised as in-process publish–subscribe], [An expired team must cancel orders and owe refunds, but group-buy must not depend on the order or payment modules.], [Notifier: `ApplicationEventPublisher` (Spring's multicaster behind it). Event source: `GroupDealExpiryService`. Event: `TeamExpired`. ConcreteObserver in this part: `TeamExpiryRefundSubscriber`, update `onTeamExpired`, phase `BEFORE_COMMIT`, `@Order(20)`. The full collaboration is in @sec-a-uc05; @fig-a-uc06-dc-record shows UC6's share.], [Payment reacts without group-buy knowing it (open–closed). Liability: the order and phase of subscribers are part of the design and are fixed with `@Order` and `BEFORE_COMMIT`; a slow subscriber lengthens the expiry transaction.],
  [Idempotency Key (enterprise integration, idempotent request)], [A refund request may be lost after Stripe accepts it, and asking again must not refund twice.], [Key: `RefundWork.providerKey()` = `blocbuy-refund-<id>`, stable for one obligation. Sender: `StripeRefundGateway.createRefund`. Receiver: Stripe. Fallback after 23 h: `findRefund`, which accepts only one refund tagged with the `obligation_id`. @fig-a-uc06-ds-provider.], [At most one Stripe refund per obligation (A10). Liability: the key is safe only inside Stripe's 24-hour window, so an obligation still open after 23 hours needs an operator.],
  [Leased work queue with a fencing token (architectural)], [Several worker instances may run, a crashed worker must not hold an obligation for ever, and a late worker must not overwrite a newer result.], [Queue: `biz_refund` with index `idx_biz_refund_due`. Claim: `RefundMapper.claimNext` (`FOR UPDATE SKIP LOCKED`, `lease_until`). Fencing token: `version`, checked by `RefundMapper.finish`. Job lease: `LeasedJobRunner` over `SchedulerLeaseRepository`. @fig-a-uc06-ds-claim.], [No database lock is held across the provider call, and a crash costs at most two minutes (E2, A10). Liability: one attempt per 30-second tick bounds throughput.],
  [Ports and Adapters with an Anti-Corruption Layer (architectural)], [The worker must not see Stripe's SDK types or failure kinds.], [Port: `RefundGateway` (domain). Adapter: `StripeRefundGateway` (infrastructure), which translates a Stripe refund into `RefundGateway.Snapshot` and a Stripe failure into a `PaymentErrorCode`. Also `PaymentBusinessPort`, implemented by `OrderFulfilmentAdapter`. @fig-a-uc06-dc-provider.], [A second provider is a new adapter; payment owns the interfaces it depends on (dependency inversion). Liability: a Stripe status that the code does not know is parked in `review`.],
)

=== Flow-to-Design Trace <sec-a-uc06-trace>

@tbl-a-uc06-trace maps each step of @tbl-a-uc06-flows to design operations. The quoted codes are the `last_result` values that `finish` stores and audits.

#atable(
  [UC6 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc06-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`tick`, `expireDue`], [UC5: `TeamExpirySweepJob` → `GroupDealExpirySweeper.sweep` → `GroupDealExpiryService.expireOne` → `ApplicationEventPublisher.publishEvent(TeamExpired)` (@sec-a-uc05)], [@fig-a-uc06-ds-record],
  [B2], [`recordRefundsOwed`, `Team.isExpired`], [`TeamExpiryRefundSubscriber.onTeamExpired` → `TeamExpiryRefundService.recordExpiredTeam` → `PaymentBusinessPort.lockExpiredTeam` → `GroupDealSettlementSupport.lockExpiredTeam` → `PaidGroupDealRepository.lockDeal`, `GroupDeal.status()`], [@fig-a-uc06-ds-record],
  [B3], [`Team.paidPayments`], [`CheckoutPaymentRepository.requireRefundsForExpiredTeam` → `PaymentMapper.lockRefundableMemberIds`], [@fig-a-uc06-ds-record],
  [B4], [`Payment.requireRefund`; «create» `Refund`], [`PaymentMapper.refundExpiredTeam` (reason `TEAM_EXPIRED`); count check], [@fig-a-uc06-ds-record],
  [B5], [`tick`, `payNextRefund`, `Refund.claimNextDue`], [`RefundWorkerJob.run` → `LeasedJobRunner.run` (`SchedulerLeaseRepository.tryAcquire`); `RefundService.processNext` → `RefundRepositoryImpl.claimNext` → `RefundMapper.claimNext`, `audit("CLAIMED")`], [@fig-a-uc06-ds-claim],
  [B6], [`Payment.agreesWith`], [`RefundWork.valid()`], [@fig-a-uc06-ds-claim],
  [B7], [`PaymentGateway.refund`], [`RefundWork.providerKey()`; `RefundGateway.createRefund` (`StripeRefundGateway`)], [@fig-a-uc06-ds-provider],
  [B8], [`Refund.record`; «create» `RefundAttempt`], [`RefundService.apply`, `matches`, `finish` → `RefundRepository.finish` → `RefundMapper.finish`, `audit("PROVIDER_SUCCEEDED")`], [@fig-a-uc06-ds-provider],
  [B9], [`refundStatus`, `showRefundStatus`], [`PaymentBrowserService.receipt` → `RefundRepository.status` (@sec-a-pay-upfront)], [—],
  [A1], [—], [UC5: `GroupDeal.expire` refuses, `expireOne` returns `false`, no `TeamExpired` is published (@sec-a-uc05)], [—],
  [A2], [`claimNextDue` returns none], [`claimNext` returns `null`; `processNext` returns `false`], [@fig-a-uc06-ds-claim],
  [A3], [`Refund.parkForReview`], [`valid()` false → `finish(…, REVIEW, "PAYMENT_MISMATCH")`], [@fig-a-uc06-ds-claim],
  [A4], [`PaymentGateway.lookUpRefund`], [`RefundGateway.retrieveRefund(providerRefundId)`], [@fig-a-uc06-ds-provider],
  [A5], [`PaymentGateway.findRefund`; `parkForReview`], [`databaseNow` → `RefundRepository.now`; `RefundGateway.findRefund`; `null` → `finish(…, REVIEW, "UNKNOWN_AFTER_RETRY_WINDOW")`], [@fig-a-uc06-ds-provider],
  [A6], [`Refund.retryLater`], [`PAYMENT_PROVIDER_ERROR` → `permanent` false → `finish(…, PROCESSING, "PROVIDER_RETRY")`], [@fig-a-uc06-ds-provider],
  [A7], [`Refund.parkForReview`], [`PAYMENT_STATE_INVALID` or `PAYMENT_PROVIDER_REJECTED` → `finish(…, REVIEW, "PROVIDER_STATE_MISMATCH" or "PROVIDER_REJECTED")`], [@fig-a-uc06-ds-provider],
  [A8], [`Refund.parkForReview`], [`matches` false → `finish(…, REVIEW, "PROVIDER_MISMATCH")`], [@fig-a-uc06-ds-provider],
  [A9], [`Refund.record`], [`apply`: `pending` → `PROCESSING` (`"PROVIDER_PROCESSING"`); `failed`, `canceled` → `FAILED` (`"PROVIDER_FAILED"`); other → `REVIEW` (`"PROVIDER_REVIEW"`)], [@fig-a-uc06-ds-provider],
  [A10], [—], [Row lease (`lease_until`) lapses; the next `claimNext` returns the row; `createRefund` repeats `providerKey()`], [@fig-a-uc06-ds-claim, @fig-a-uc06-ds-provider],
  [A11], [`reconcile`, `claimForReview`, `lookUpRefund`, `record`, `parkForReview`], [`RefundService.reconcile` → `RefundRepository.claimReview` (audit `OPERATOR_RECONCILE`), `RefundWork.valid()`, `RefundGateway.retrieveRefund`, then `apply`, or `finish` with one of the three `OPERATOR_PROVIDER_` codes (stays `REVIEW`)], [@fig-a-uc06-ds-reconcile],
  [E1], [—], [`IllegalStateException` from `lockExpiredTeam` or `requireRefundsForExpiredTeam`; the expiry transaction rolls back], [@fig-a-uc06-ds-record],
  [E2], [—], [`RefundMapper.finish` matches no row (`version` changed); no audit row is written], [@fig-a-uc06-ds-provider],
)
