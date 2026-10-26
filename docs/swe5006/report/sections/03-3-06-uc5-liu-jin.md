<!-- report
rubric: 3.3.6
owner: Liu Jin
plan: #677
sources: UC5 issue (#28); backend/docs/design/backend-skeleton-plan.md §16; backend ADR 0002; GroupDeal.joinPaid, GroupDeal.expire, DealStatus, GroupDealExpirySweeper, GroupDealExpiryService, TeamExpirySweepJob, OrderLifecycleSubscriber, TeamExpiryRefundSubscriber; docs/perf/sprint3-baseline.md; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
status: draft
-->

### 3.3.6 UC5 Team Lifecycle Resolution

An open team ends in exactly one of two ways: it *succeeds* when an admission fills its last seat, or it *expires* when its deadline passes first. Either way BlocBuy settles the team's orders in the same transaction, and an expiry also records the refunds owed to its paid members (UC6 extends this use case). A resolved team never changes state again.

**Actors:** Scheduler (primary for expiry; time). Buyer (primary for success; the joining buyer of UC4 whose admission fills the last seat).

**Preconditions:** An open team exists. For expiry, scheduled processing is enabled in the environment; it is off by default and an operator enables it per deployment after checking the backlog (on dev since 23 October).

**Trigger:** For success, UC4 admits a buyer into the last free seat (UC4, step 7). For expiry, the expiry sweep runs; by default it starts every minute.

**Normal flow, success:**

1. Within UC4's admission transaction, the system finds that the new member count equals the target size.
2. The system marks the team *succeeded* and records its success time.
3. Before the transaction commits, the system confirms every pending order of the team, including the order of the buyer who filled it.
4. After the commit, the listing no longer shows the team as open, and every member's account page shows it as "Filled".

**Normal flow, expiry:**

1. The scheduler starts the expiry sweep. Only one running instance holds the sweep's lease at a time, so two instances never sweep together.
2. The system selects up to 100 open teams whose deadline has passed, oldest deadline first.
3. For each team, in its own short transaction, the system locks the team, reads the database clock, checks again that the team is still open and its deadline has passed, and marks it *expired*.
4. Before that transaction commits, the system cancels all of the team's pending orders, with the team's resolution time as the cancel time.
5. In the same transaction, «extend» UC6 at the extension point *resolved* {team expired}: the system records one refund obligation for each paid member (Section 3.3.7, steps 1 to 4).
6. The transaction commits. Every member's account page shows the team as "Closed" and the order as "Cancelled".

**Exceptional flows:**

- Success 1a. A join payment arrives after the team has already succeeded or expired: the admission is refused as closed (`DEAL_CLOSED`) and the payment is refunded (UC4, 6a). A late payment never reopens a team.
- Expiry 1a. Scheduled processing is disabled in the environment: teams past their deadline stay *open* in storage. They accept no new member, because admission compares the deadline with the database clock (UC4, step 6), and the account page labels them "Ended". They expire at the first sweep after processing is enabled.
- Expiry 1b. The instance holding the lease stops: another instance takes the lease when it lapses, after at most two minutes.
- Expiry 3a. Between steps 2 and 3, an admission filled the team or another sweep expired it: the status check fails, the team is skipped as already resolved, and nothing is written. Joining at the deadline cannot win with a clock reading taken before a long lock wait, because the clock is read after the lock.
- Expiry 3b. The expiry of one team fails for a technical reason: its transaction rolls back the expiry, the order cancellations and the refund obligations together; the failure is logged, that team is retried after five minutes, and the sweep continues with the other teams.
- A1. Any attempt to resolve a resolved team again, or to move it back to *open*, is rejected: only an open team may change state, and the terminal states *succeeded* and *expired* never change.

**Postconditions:** Each resolved team has exactly one terminal state. A succeeded team has all its orders confirmed; an expired team has all its orders cancelled and exactly one refund obligation per paid member. The states *failed* and *canceled* exist in the data model but no flow reaches them (Section 1.4).

The expiry bound is one sweep interval plus the sweep's own time, not a one-minute guarantee. Tested locally, not on dev: over 30 teams the worst observed lag between deadline and expiry was 58.18 s. On 23 October on dev, one team expired 18.9 s after its deadline and its member's payment was refunded 40.6 s after it; these figures describe that one run, not a general bound.

**Analysis objects:** expiry timer (the Scheduler reaching the system through a «boundary»); team resolution control «control»; team, membership, order and refund obligation «entity».
