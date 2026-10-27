<!-- report
rubric: 3.3.7
owner: Yu Qianshuo (Yorik)
plan: #677
sources: ../report-drafts/yorick-yu-contribution.md (YY:7–37); UC6 issue (#29); TeamExpiryRefundSubscriber, TeamExpiryRefundService, RefundService, RefundWorkerJob, AdminPaymentController; backend ADR 0006; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
owns: none
status: draft
-->

### 3.3.7 UC6 Refund on Team Expiry

When a team expires before it fills, BlocBuy returns every paid member's money to the original payment method: the expiry records one refund obligation per paid member, and the system then sends each refund to Stripe, retries safely and records every attempt for audit. The same refund steps also return the money that UC3 and UC4 owe when they refuse a payment that has already succeeded.

**Actors:** Stripe (secondary; executes the refund), the only actor associated with UC6 in Figure 3.36. UC6 «extend»s UC5, so the Scheduler reaches it through UC5's expiry flow. Buyer (beneficiary; does not act). An operator who reviews uncertain cases takes part only in the exceptional flows and is not drawn in Figure 3.36.

**Preconditions:** UC5's expiry flow is expiring an open team whose deadline has passed (UC5, expiry flow, step 3); at least one paid member's payment has succeeded; scheduled processing is switched on.

**Trigger:** UC5 marks the team *expired*. UC6 extends that step: its first part happens together with UC5's expiry, and the rest happens later, on the system's own schedule.

**Normal flow:**

1. UC6 starts as part of UC5's expiry, after UC5 has confirmed that the team is still open and past its deadline, marked it *expired* and cancelled its pending orders.
2. The system finds the succeeded payment of each paid member of the team.
3. Together with the expiry, the system records one refund obligation for each of those payments.
4. From then on, the team's closure and the money owed for it exist together or not at all.
5. Shortly afterwards, the system picks up one due refund obligation.
6. The system checks that the payment and the obligation agree on the original Stripe payment, the amount, the currency and the obligation's identity.
7. The system asks Stripe to refund the original payment, marked so that Stripe treats any repeat of this request as the same refund.
8. The system records Stripe's refund reference, the reported status and an audit record of the attempt.
9. The buyer's receipt page shows "Refund sent" with the refunded amount.

**Exceptional flows:**

- 1a. An admission or another expiry run has already filled or expired the team: UC5 stops, so UC6 never starts and no obligation is recorded twice (UC5, expiry 3a).
- 6a. The payment facts do not match the obligation: the system stops automatic processing and parks the obligation for operator review. No refund is sent.
- 7a. Stripe is temporarily unavailable or answers with a retryable error: the obligation stays recorded and is tried again later as the same request, so a retry can never create a second refund.
- 7b. Stripe permanently refuses the refund: the obligation is parked for operator review.
- 7c. The refund is still not settled close to a day after its first attempt: the system no longer asks Stripe for a refund. It looks at Stripe for the refund it asked for earlier; if it does not find exactly one, it parks the obligation for review instead of asking again (retry rules in section 3.2.5).
- 8a. The system stops after Stripe accepted the refund but before it recorded the result: a later attempt sends the same request, and Stripe returns the same refund.
- R1. For a parked case, an operator lists the payments needing attention on an admin-only, read-only view. A check of a parked case against a refund that already exists at Stripe is built but not offered to the operator (not exposed; section 3.4.2).

**Postconditions:** Each paid member of an expired team has exactly one refund obligation. Each known Stripe result is tied to one Stripe refund and a matching local status, and every attempt is recorded. Unresolved or uncertain cases remain visible to operators. A simulated refund is never reported as a real Stripe refund.

**Analysis objects:** `SchedulerTrigger` (the Scheduler's expiry and refund ticks) and the payment provider (Stripe) «boundary»; `ResolveTeamControl` (records the refunds owed) and `RefundControl` (pays them) «control»; team, paid membership, payment, refund obligation and refund attempt «entity».
