<!-- report
rubric: 3.3.11
owner: Wang Ziang (Eric)
plan: #677
sources: use-case.svg «include» of UC3, UC4; Yorik reviews; ../report-drafts/eric-wang-contribution.md (ER:7–50); backend ADR 0005; StripeCheckoutController, StripeCheckoutService (prepare, handleWebhook), StripeWebhookController, PurchaseIntentExpiryJob; backend/docs/design/write-rate-limits.md; frontend/src/features/payment/; backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md
owns: none
status: draft
-->

### 3.3.11 Pay Upfront via Stripe Checkout

This included use case takes the buyer's money for a frozen purchase intent through Stripe's hosted payment page and turns Stripe's report of the payment into one verified, recorded payment result. UC3 and UC4 «include» it at their payment step; it never creates a team, membership or order itself, but hands the verified payment back to the base use case as part of recording it. Every payment uses a test card in the Stripe Sandbox.

**Actors:** Buyer (primary, through the base use case). Stripe (secondary; hosts the payment page, reports results).

**Preconditions:** The base use case has frozen a purchase intent for this buyer (UC3, step 3, or UC4, step 3), and online payment is switched on.

**Trigger:** The base use case reaches its payment step.

**Normal flow:**

1. The buyer chooses to pay, and the page asks the system to prepare a payment for the intent. A repeat of the same attempt is recognised as the same attempt.
2. The system checks that the intent belongs to the signed-in buyer and has not lapsed, and that no other payment attempt for the intent is open. It records a new payment as *created*, with a payment page that closes after a fixed time and never later than the end of the intent or the team's deadline.
3. The system asks Stripe to create a hosted payment page for the frozen amount, links the page to the payment, and sends the buyer only to a genuine Stripe payment page.
4. The browser goes to Stripe's page, and the buyer pays with a test card.
5. Stripe sends the browser back to BlocBuy's payment return page, which opens the receipt page. Returning to BlocBuy only names the payment; it is never taken as proof of payment.
6. Stripe notifies the system of the payment result. The system checks that the notification comes from BlocBuy's own Stripe test account, ignores any notification about real money, and confirms the result with Stripe instead of trusting the notification's content.
7. The system makes sure each notification is applied only once, checks that the amount, currency and payment page match what was frozen, and marks the payment *succeeded*.
8. Together with that, the system hands the succeeded payment to the base use case (UC3, step 5, or UC4, step 5) and records its outcome, *fulfilled* or *refund required*, exactly once.
9. The receipt page, which checks the payment again until it is settled, shows "Payment received" with the order and team.

**Exceptional flows:**

- 1a. Online payment is switched off: the page disables its payment actions, and the system would refuse the request anyway.
- 1b. The buyer prepares payments too often in a short time: the system refuses and asks the buyer to try again later.
- 2a. The intent belongs to someone else, has lapsed, or already has another open payment attempt: the system refuses and opens no payment. The buyer must start again from the base use case.
- 2b. The same attempt is sent again, for example after a lost response: the system reuses the payment it already created instead of creating a second one.
- 3a. Stripe cannot be reached, or it stays unknown whether the page was created: the payment is set aside for operator review, the request is refused, and no second payment page is created.
- 4a. The buyer abandons the page, or it closes: Stripe reports this, the payment is closed as *canceled* and the receipt shows "Checkout closed without payment"; nothing was charged and no team, membership or order exists. The unpaid intent is later marked as lapsed.
- 5a. The buyer reaches the receipt before Stripe's notification: the receipt shows "Waiting for payment confirmation" and checks again until the result arrives.
- 6a. The notification cannot be shown to come from Stripe: the system rejects it and applies nothing; Stripe sends it again.
- 6b. Stripe's notifications do not arrive, or the system cannot read them: the payment stays *created* although the buyer has paid. An operator checks the payment with Stripe and applies steps 7 and 8, with the operator's name recorded.
- 6c. The notification names a payment BlocBuy has never held, for example one from another environment that shares the same Stripe Sandbox: the system records it as unknown, acknowledges it and changes nothing.
- 7a. Stripe sends the same notification again: it is already recorded, so nothing is applied twice. A second notification for the same payment returns the outcome already recorded.
- 7b. The amount, currency or payment page that Stripe reports differs from the frozen payment: the system does not settle it and opens a review case for an operator.
- 7c. Stripe reports a payment as paid after BlocBuy had already closed it as canceled or failed: the system does not settle it automatically and opens a review case for the operator check of 6b.
- 8a. The intent lapsed before the payment was recorded, or the base use case refuses the payment: the outcome is *refund required*, a refund obligation is recorded, and the money is returned as in UC6 (section 3.3.7). The receipt shows "Refund pending" until the refund is sent.
- 8b. A technical failure happens during steps 7 to 8: nothing of these steps is kept, the notification is not marked as applied, and Stripe sends it again.

**Postconditions:** Each succeeded payment has exactly one recorded outcome, and no payment is settled twice. A payment that Stripe reports inconsistently is held for review, not settled. A buyer is never asked to pay twice for one intent.

**Analysis objects:** payment return and receipt pages and the payment provider (`PaymentGateway`) «boundary»; checkout control and settlement control «control»; purchase intent, payment, payment event and review case «entity». Section 3.4.1 models the payment steps inside the UC3 and UC4 sequences.
