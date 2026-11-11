// Detailed Design: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "../diagrams/d5-physical-deployment.typ": d5
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d9-data-models.typ": d9-identity, d9-notification, d9-commerce-team, d9-commerce-payment

// AD and AISS numbers come from ../registers.md. Replace each short description with
// context, options, choice, trade-off and consequence (AGENTS.md writing rule 3).
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  [Identifier], [Description],
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

=== Detailed Design

The two use cases below protect the same buyer at different points. When a buyer pays for the last seat, commerce must either admit that buyer and create an order, or record a refund. When the deadline passes with a seat still empty, commerce must close the team and record what it owes the buyers who paid. Both decisions use the team's database row to settle competing actions. The diagrams trace the implementation on the report baseline (5 November 2026, commit `174af508`).

*UC4 — joining by paying.* D11 shows the objects behind the webhook. The inbound adapter passes the raw request and signature to the payment service. Before opening a settlement transaction, the service verifies the signature and retrieves the session from Stripe. In the transaction it claims the event ID, locks the existing team for JOIN, locks the saved purchase intent, and then locks the payment. The business port lets payment request fulfilment while the order module owns the membership and order writes. The group aggregate checks its deadline, remaining seats and existing members. It returns the updated group snapshot to the application service. The repository implements its lock with PostgreSQL through MyBatis. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/StripeCheckoutService.java")[Checkout and settlement] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/order/service/OrderFulfilmentAdapter.java")[Business port implementation] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/persistence/PaidGroupDealRepositoryImpl.java")[Group persistence].

#figure(
  image("../diagrams/d11-paid-participation-classes.svg", width: 95%, height: 14.0cm, fit: "contain"),
  caption: [D11. UML class diagram, paid participation. The payment service depends on the business port. A locked group decides whether to admit a paid buyer.],
) <d11-paid>

*How a paid refusal differs from a retry.* In D12 the business port returns a rejection for a full team. The payment service commits the obligation to refund the losing buyer without creating an order. A technical error instead rolls back the event claim and business writes, so a webhook redelivery can settle the payment. After a successful commit, a repeated event ID does no further work. The fulfilment adapter saves the order, links it to the membership, marks the intent fulfilled and consumes stock in the same transaction. On a full team the order listener runs before commit, after the last buyer's order exists, so both pending orders become confirmed. The concurrency test runs two payments against the remaining capacity. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/PaidGroupDealServiceIT.java")[Concurrency and replay tests] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0001-payment-business-integration.md")[Settlement decision].

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
  *UC3 START and UC4 JOIN — two buyers, one team* \
  The leader pays for a two-seat listing and shares the invitation. Opening it leaves one paid member. The joiner's successful payment fills the second seat. Each buyer sees their own order. The group status is shared. The 24 October 2026 recording on deployed dev showed the second buyer's receipt and the same group marked “2 of 2 joined” and “Filled”. #link("https://github.com/PPsyrius/iss-groupbuy-team8/issues/590")[Recorded walkthrough].

  #figure(
    image("../diagrams/d12-two-buyers-sequence.svg", width: 100%, height: 14.6cm, fit: "contain"),
    caption: [D12. UML sequence diagram, buyer and settlement viewpoints. The lower alternative shows a competing payment taking the final seat before the joiner obtains the team lock.],
  ) <d12-paid>
]

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
*Team expiry and refunds.* D13 shows why expiry and refund execution are separate. Under the scheduler lease, the sweeper calls `expireOne` for each candidate team. The expiry service locks the team and checks the database clock again. If a concurrent join has already filled the team, expiry returns without publishing an event. If expiry wins, it writes the expired state and publishes `TeamExpired` in the transaction. Spring then invokes the order subscriber, the refund subscriber and, when integration events are enabled, the outbox writer. A failure in one listener rolls back that team's expiry, cancellations and refund obligations together. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/groupbuy/service/GroupDealExpiryService.java")[Expiry transaction] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0002-team-lifecycle-events.md")[Subscriber order] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/TeamExpirySchedulingIT.java")[Rollback, retry and refund tests].

#figure(
  image("../diagrams/d13-expiry-classes.svg", width: 100%),
  caption: [D13. UML class diagram, expiry responsibilities. The expiry service publishes one event. Subscribers own order cancellation, the refund obligation and the integration record.],
) <d13-expiry>

*Design choices.* The Factory Method in `PaidGroupDealCreator` constructs a new paid team on START. JOIN always acts on the existing team. Observer lets a new listener handle `TeamExpired` without adding another call to the expiry service, but the listeners must finish before commit to preserve consistency. The Transactional Outbox stores the integration event with the business writes. Its relay can publish a duplicate if it succeeds at the broker and then loses its database update. The notification handler implements an Idempotent Receiver. It commits its consumed-message marker with the notification before acknowledging the queue message. The refund worker uses its own provider idempotency key for the separate risk of issuing a refund twice. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-domain/src/main/java/com/blocbuy/groupbuy/model/PaidGroupDealCreator.java")[Factory Method] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/integration/outbox/IntegrationOutboxWriter.java")[Outbox writer] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/notification/service/NotificationEventAppService.java")[Consumer transaction].
]

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
  *After expiry — paying the buyer back* \
  The worker claims a refund, releases the database transaction, calls Stripe and records the result in another transaction. It reuses the refund ID as the basis of the provider key. If it knows the provider refund ID, it retrieves that refund. Beyond the safe creation window it looks up the refund and sends an uncertain outcome for review. Notification delivery can lag or retry independently of repayment. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/RefundService.java")[Refund worker] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-domain/src/main/java/com/blocbuy/payment/model/RefundWork.java")[Provider key] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/common/service/OutboxRelayService.java")[Relay].

  #figure(
    image("../diagrams/d14-expiry-refund-sequence.svg", width: 100%, height: 14.4cm, fit: "contain"),
    caption: [D14. UML sequence diagram, transaction and recovery viewpoints. Synchronous listeners complete before commit. Stripe refund execution and event delivery proceed afterwards.],
  ) <d14-expiry>
]
