// D4a: the EventStorming process-level timeline for UC3–UC6, tabulated.
// Columns are the acts in time order; rows are the sticky kinds. Each row is filled with a pale
// tint of its EventStorming sticky colour (actor yellow, command blue, event orange, policy lilac,
// hot spot pink); command and hot spot reuse the report palette (style.typ, STYLE.md).
// Source of the content: docs/swe5001/architecture-options-study/06-eventstorming-ddd.md §3,
// checked against the code on 11 Nov 2026. Only the four events in `code` exist as Java records;
// every other event is a workshop sticky and is marked "(concept)".
#import "style.typ": platform-fill, business-fill
#import "../lib.typ": hdr

#let sticky = (
  actor: rgb("#FFF3B0"),
  command: platform-fill,
  event: rgb("#FFDDB8"),
  policy: rgb("#EADFF5"),
  hotspot: business-fill,
)

#let d4a() = {
  let rows = ("actor", "command", "event", "policy", "hotspot")
  set text(8.5pt)
  set par(justify: false, leading: 0.45em)
  // Code prints at 8 pt and long names wrap after "_ . / ( ," and at camelCase joins (as atable).
  show raw: set text(size: 8pt)
  show raw.where(block: false): it => {
    show regex("[_./(,]"): m => m.text + sym.zws
    show regex("[a-z][A-Z]"): m => m.text.at(0) + sym.zws + m.text.at(1)
    it
  }
  table(
    columns: (auto, 1fr, 1.15fr, 1.1fr, 1fr),
    align: left + top,
    fill: (x, y) => if y == 0 { hdr } else { sticky.at(rows.at(y - 1)) },
    table.header(
      [Sticky],
      [Act 1: quote and pay (UC3, UC4)],
      [Act 2: settle the payment (UC3, UC4)],
      [Act 3: resolve the team (UC5)],
      [Act 4: refund the members (UC6)],
    ),
    // Actor or trigger
    [*Actor*],
    [Buyer (leader or joiner), deciding on the listing detail],
    [Stripe, through the signed webhook],
    [The settlement that fills the last seat; time: `TeamExpirySweepJob` every minute, under a lease],
    [Time: `RefundWorkerJob` every 30 s, under a lease; the operator],
    // Command
    [*Command*],
    [Create purchase intent, `POST /payments/intents` (`START` or `JOIN`); create checkout session, `POST /payments/checkout-sessions`],
    [Handle Stripe event, `POST /webhooks/stripe` (`StripeCheckoutService.handleWebhook`)],
    [`GroupDeal.joinPaid` fills the team; expire team, `GroupDealExpiryService.expireOne`],
    [Process next refund, `RefundService.processNext`; reconcile a refund in review, `RefundService.reconcile`],
    // Domain event
    [*Domain event*],
    [Purchase Intent Issued _(concept)_; Checkout Session Created _(concept)_],
    [`PaymentSucceeded`; Team Started _(concept)_ or `MemberJoined`; Order Placed _(concept)_; Stock Unit Consumed _(concept)_; on a refused fulfilment, Refund Obligation Recorded _(concept)_],
    [`TeamSucceeded` or `TeamExpired`; Orders Confirmed _(concept)_ or Orders Cancelled _(concept)_; Refund Obligation Recorded _(concept)_],
    [Refund Succeeded _(concept)_; Refund Attempt Failed _(concept)_; Refund Sent to Review _(concept)_],
    // Policy
    [*Policy*],
    [The server freezes price, team size and deadlines in the intent; no request chooses its own price],
    [Claim the event id once. Whenever a payment succeeds, fulfil its intent (`PaymentBusinessPort.fulfil`) in one transaction that locks team → intent → payment. Whenever fulfilment is refused, owe a refund],
    [`BEFORE_COMMIT` in the transition transaction: confirm or cancel the orders (`OrderLifecycleSubscriber`, `@Order(10)`); on expiry, owe each paid member a refund (`TeamExpiryRefundSubscriber`, `@Order(20)`)],
    [Call the Stripe refund with an idempotency key, outside any database transaction. Retry a failed attempt after a fixed 30 s backoff. After 23 h create no new refund: search for one, else send the payment to review],
    // Hot spot
    [*Hot spot*],
    [HS-10: an unpaid `START` intent leaves its reserved team id unused],
    [HS-1: the last-seat race; HS-2: duplicate or replayed webhooks; HS-3: a payment that arrives after the team deadline; HS-5: the last stock unit taken during settlement],
    [HS-4: the sweeper and a webhook race on one team; HS-8: consequences run `BEFORE_COMMIT` in one transaction],
    [HS-6: a refund fails, and Stripe may drop idempotency keys after 24 h],
  )
}
