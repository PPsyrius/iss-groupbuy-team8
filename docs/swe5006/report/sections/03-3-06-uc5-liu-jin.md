<!-- report
rubric: 3.3.6
owner: Liu Jin
plan: #677
sources: UC5 issue (#28); backend/docs/design/backend-skeleton-plan.md §16; backend ADR 0002; GroupDeal.joinPaid, GroupDeal.expire, DealStatus, GroupDealExpirySweeper, GroupDealExpiryService, TeamExpirySweepJob, OrderLifecycleSubscriber, TeamExpiryRefundSubscriber; docs/perf/sprint3-baseline.md; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
owns: none
status: draft
-->

### 3.3.6 UC5 Team Lifecycle Resolution

An open team ends in exactly one of two ways: it *succeeds* when an admission fills its last seat, or it *expires* when its deadline passes first. Either way BlocBuy settles the team's orders together with the outcome, and an expiry also records the refunds owed to its paid members (UC6 extends this use case). A resolved team never changes state again.

**Actors:** Scheduler (primary; time), the only actor associated with UC5 in Figure 3.34. The success outcome has no actor of its own: UC4's admission of the buyer who fills the last seat triggers it as part of UC4.

**Preconditions:** An open team exists. For expiry, scheduled expiry is switched on; it is off by default, and an operator switches it on for each deployment.

**Trigger:** For success, UC4 admits a buyer into the last free seat (UC4, step 7). For expiry, the Scheduler starts its regular expiry run (sweep design in section 3.2.7).

**Normal flow, success:**

1. As part of UC4's admission, the system finds that the new member count equals the target size.
2. The system marks the team *succeeded* and records its success time.
3. Together with the success, the system confirms every pending order of the team, including the order of the buyer who filled it.
4. From then on, the listing no longer shows the team as open, and every member's account page shows it as "Filled".

**Normal flow, expiry:**

1. The Scheduler starts the expiry run. Only one run works at a time.
2. The system finds the open teams whose deadline has passed, oldest deadline first.
3. For each team in turn, the system checks again that the team is still open and its deadline has passed, and marks it *expired*.
4. Together with the expiry, the system cancels all of the team's pending orders, with the team's resolution time as the cancel time.
5. Together with the expiry, «extend» UC6 at the extension point *resolved* {team expired}: the system records one refund obligation for each paid member (section 3.3.7).
6. Every member's account page shows the team as "Closed" and the order as "Cancelled".

**Exceptional flows:**

- Success 1a. A join payment arrives after the team has already succeeded or expired: the admission is refused as closed and the payment is refunded (UC4, 6a). A late payment never reopens a team.
- Expiry 1a. Scheduled expiry is switched off: teams past their deadline stay *open* in the records. They accept no new member, because admission checks the deadline (UC4, step 6), and the account page labels them "Ended". They expire at the first run after expiry is switched on.
- Expiry 1b. The instance holding the lease stops: another instance takes the lease when it lapses, after at most two minutes.
- Expiry 3a. Between steps 2 and 3, an admission filled the team or another run expired it: the team is skipped as already resolved, and nothing changes. A join and an expiry of the same team are decided one after the other, never both.
- Expiry 3b. The expiry of one team fails for a technical reason: none of its expiry, order cancellations and refund obligations is kept; that team is tried again later, and the run continues with the other teams.
- A1. Any attempt to resolve a resolved team again, or to move it back to *open*, is rejected: only an open team may change state, and the terminal states *succeeded* and *expired* never change.

**Postconditions:** Each resolved team has exactly one terminal state. A succeeded team has all its orders confirmed; an expired team has all its orders cancelled and exactly one refund obligation per paid member.

**Analysis objects:** `SchedulerTrigger` (the Scheduler reaching the system through a «boundary»); `ResolveTeamControl` (team resolution) «control»; team, membership, order and refund obligation «entity».
