<!-- report
rubric: 3.3.7
owner: Yu Qianshuo (Yorik)
plan: #677
sources: ../report-drafts/yorick-yu-contribution.md (YY:7–37); UC6 issue (#29); TeamExpiryRefundSubscriber, TeamExpiryRefundService, RefundService, RefundWorkerJob, AdminPaymentController; backend ADR 0006; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
status: draft
-->

### 3.3.7 UC6 Refund on Team Expiry

When a team expires before it fills, BlocBuy returns every paid member's money to the original payment method: the expiry records one durable refund obligation per paid member, and a background refund worker sends each refund to Stripe, retries safely and records every attempt for audit. The same worker also sends the refunds that UC3 and UC4 owe when they refuse a payment that has already succeeded.

**Actors:** Stripe (secondary; executes the refund), the only actor associated with UC6 in Figure 3.36. UC6 «extend»s UC5, so the Scheduler reaches it through UC5's expiry flow. Buyer (beneficiary; does not act). An operator who reviews uncertain cases takes part only in the exceptional flows and is not drawn in Figure 3.36.

**Preconditions:** UC5's expiry flow is expiring an open team whose deadline has passed (UC5, expiry flow, step 3); at least one paid member's payment has succeeded; scheduled processing is enabled in the environment.

**Trigger:** UC5 marks the team *expired*. UC6 extends that step: its first part runs inside UC5's expiry transaction, and the rest runs later in the refund worker.

**Normal flow:**

1. UC6 starts inside UC5's expiry transaction, after UC5 has confirmed that the team is still open and past its deadline, marked it *expired* and cancelled its pending orders.
2. The system finds the succeeded payment of each paid member of the team.
3. For each of those payments, the system records one refund obligation in the same transaction.
4. The transaction commits; from now on the team's closure and the money owed for it exist together or not at all.
5. The refund worker starts (by default every 30 seconds) and claims one due obligation under a renewable lease.
6. The worker checks that the payment and the obligation agree on the original Stripe payment, the amount, the currency and the obligation's identity.
7. The worker asks Stripe to refund the original payment, with an idempotency key derived from the obligation.
8. The system records Stripe's refund reference, the reported status and an audit record of the attempt.
9. The buyer's receipt page shows "Refund sent" with the refunded amount.

**Exceptional flows:**

- 1a. Another transaction has already filled or expired the team: UC5's status check stops the expiry, so UC6 never starts and no obligation is recorded twice (UC5, expiry 3a).
- 6a. The payment facts do not match the obligation: the worker stops automatic processing and parks the obligation for operator review. No refund is sent.
- 7a. Stripe is temporarily unavailable or answers with a retryable error: the obligation stays recorded and is retried after a delay with the same idempotency key, so a retry can never create a second refund.
- 7b. Stripe permanently refuses the refund: the obligation is parked for operator review.
- 7c. More than 23 hours have passed since the first attempt (Stripe may forget idempotency keys after 24 hours): the worker no longer creates a refund. It searches Stripe for an existing refund carrying this obligation's metadata; if it finds none, or not exactly one, it parks the obligation for review instead of creating a new refund.
- 8a. The worker stops after Stripe accepted the refund but before the database recorded it: the lease lapses, a later run retries with the same key, and Stripe returns the same refund.
- R1. For a parked case, an operator lists the payments needing attention through an admin-only, read-only API. The backend also has a reconciliation operation that checks a case against a refund that already exists at Stripe; it verifies an existing refund and can never send another one. No screen, endpoint or command line exposes reconciliation: an operator must invoke it as a separately reviewed step.

**Postconditions:** Each paid member of an expired team has exactly one refund obligation. Each known Stripe result is tied to one Stripe refund and a matching local status, and every attempt is recorded. Unresolved or uncertain cases remain visible to operators. A local test double is never reported as a real Stripe refund.

On 23 October on dev, with scheduling enabled, a team started on a test listing expired 18.9 s after its deadline, and its member's S$22 Sandbox payment was refunded automatically 40.6 s after the deadline; Stripe showed exactly one succeeded refund for that payment. On the same day the operator recovery path settled three earlier paid sessions that a webhook misconfiguration had left unsettled: each was recorded as *refund required* and refunded once.

**Analysis objects:** refund worker timer and the payment provider (Stripe) «boundary»; expiry refund control and refund execution control «control»; team, paid membership, payment, refund obligation and refund attempt «entity».
