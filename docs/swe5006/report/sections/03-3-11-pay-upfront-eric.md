<!-- report
rubric: 3.3.11
owner: Wang Ziang (Eric)
plan: #677
sources: use-case.svg «include» of UC3, UC4; Yorik reviews; ../report-drafts/eric-wang-contribution.md (ER:7–50); backend ADR 0005; StripeCheckoutController, StripeCheckoutService (prepare, handleWebhook), StripeWebhookController, PurchaseIntentExpiryJob; backend/docs/design/write-rate-limits.md; frontend/src/features/payment/; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
status: draft
-->

### 3.3.11 Pay Upfront via Stripe Checkout

This included use case takes the buyer's money for a frozen purchase intent through Stripe's hosted payment page and turns Stripe's report of the payment into one verified, recorded payment result. UC3 and UC4 «include» it at their payment step; it never creates a team, membership or order itself, but hands the verified payment back to the base use case in the same transaction that records it. Stripe runs in test mode only (the Sandbox), so every payment uses a test card.

**Actors:** Buyer (primary, through the base use case). Stripe (secondary; hosts the payment page, reports results).

**Preconditions:** The base use case has frozen a purchase intent for this buyer (UC3, step 3, or UC4, step 3), and online payment is enabled in the environment.

**Trigger:** The base use case reaches its payment step.

**Normal flow:**

1. The browser asks the system to prepare a payment for the intent, sending a key that identifies this attempt (an idempotency key). A retry of the same attempt sends the same key.
2. In one transaction, the system checks that the intent belongs to the signed-in buyer and has not lapsed, and that no other payment attempt for the intent is open. It records a new payment as *created*, with a payment page that expires after 31 minutes and never later than the intent or the team's deadline.
3. The system asks Stripe to create a hosted payment page (a Checkout Session) for the frozen amount, binds the session to the payment, and returns the page's address. It returns an address only if it is a Stripe Checkout address over HTTPS.
4. The browser goes to Stripe's page, and the buyer pays with a test card.
5. Stripe sends the browser back to BlocBuy's payment return page, which opens the receipt page. The return address only names the payment; it is never taken as proof of payment.
6. Stripe sends a signed notification (a webhook). Outside any transaction, the system verifies the signature, rejects live-mode events and events from other Stripe accounts, and reads the session again from Stripe instead of trusting the notification's content.
7. In one transaction, the system records the event's identity so that it is applied only once, locks the team (for a join; a team being started does not exist yet), then the purchase intent, then the payment, checks that the amount, currency and session match what was frozen, and marks the payment *succeeded*.
8. In the same transaction, the system hands the succeeded payment to the base use case (UC3, step 5, or UC4, step 5) and records its outcome, *fulfilled* or *refund required*, exactly once.
9. The receipt page, which re-reads the payment until it is settled, shows "Payment received" with the order and team.

**Exceptional flows:**

- 1a. Online payment is disabled in the environment: the page disables its payment actions, and the system would refuse the request anyway.
- 1b. The buyer has prepared 60 payments within one minute: the system refuses with HTTP 429 (`RATE_LIMITED`) before reading anything.
- 2a. The intent belongs to someone else, has lapsed, or already has another open payment attempt: the system refuses with HTTP 409 (`PAYMENT_STATE_INVALID`) and opens no payment. The buyer must start again from the base use case.
- 2b. The same key is sent again for the same intent (for example after a lost response): the system reuses the payment it already created instead of creating a second one.
- 3a. Stripe cannot be reached, or whether the page was created is still unknown after the safe creation window: the payment is set aside for operator review, the request is refused with HTTP 409, and no second payment page is created.
- 4a. The buyer abandons the page, or it expires: Stripe reports the expiry, the payment is closed as *canceled* and the receipt shows "Checkout closed without payment"; nothing was charged and no team, membership or order exists. A background sweep (every minute when scheduled processing is enabled) marks the unpaid intent as expired.
- 5a. The buyer reaches the receipt before Stripe's notification: the receipt shows "Waiting for payment confirmation" and re-reads the payment until the result arrives.
- 6a. The signature is invalid: the system rejects the notification (`PAYMENT_WEBHOOK_SIGNATURE_INVALID`) and applies nothing; Stripe retries.
- 6b. Stripe's notifications do not arrive, or the system cannot read them (for example because the endpoint is configured for another Stripe API version): the payment stays *created* although the buyer has paid. An operator inspects it with the recovery command, which reads the session from Stripe and applies steps 7 and 8 with the operator's name recorded.
- 6c. The notification names a payment this database has never held, for example one from another environment that shares the same Stripe Sandbox: the system records it as unknown, acknowledges it and changes nothing.
- 7a. Stripe sends the same event again: the event is already recorded, so nothing is applied twice. A second event for the same payment returns the outcome already recorded.
- 7b. The amount, currency or session that Stripe reports differs from the frozen payment: the system does not settle it and opens a review case for an operator.
- 7c. Stripe reports a payment as paid after BlocBuy had already closed it as canceled or failed: the system does not settle it automatically and opens a review case for the recovery command of 6b.
- 8a. The intent lapsed before the payment was recorded, or the base use case refuses the payment: the outcome is *refund required*, a refund obligation is recorded, and the refund worker returns the money (Section 3.3.7). The receipt shows "Refund pending" until the refund is sent.
- 8b. A technical failure happens during steps 7 to 8: the whole transaction rolls back, the event is not marked as applied, and Stripe's redelivery repeats it.

**Postconditions:** Each succeeded payment has exactly one recorded outcome, and no payment is settled twice. A payment that Stripe reports inconsistently is held for review, not settled. A buyer is never asked to pay twice for one intent.

On 23 October on dev, the Stripe webhook endpoint still used an older Stripe API version than the deployed backend expected, so three paid sessions had been left unsettled (flow 6b). Their intents had lapsed by then, so the operator recovery command recorded each as *refund required* (8a), and each was refunded exactly once. After the endpoint was corrected, a new paid start on dev was notified and settled as *fulfilled*.

**Analysis objects:** payment return and receipt pages and the payment provider (`PaymentGateway`) «boundary»; checkout control and settlement control «control»; purchase intent, payment, payment event and review case «entity». Section 3.4.1 models the payment steps inside the UC3 and UC4 sequences.
