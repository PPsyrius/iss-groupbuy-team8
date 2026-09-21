# Stripe Sandbox acceptance

Owner: Yorick. Run: 20 September 2026, Team8 Stripe Sandbox (`livemode=false`).

This is historical acceptance evidence. Its private local routes and scripts were removed before publication; the current boundary is [stripe-release-cleanup-20260920.md](stripe-release-cleanup-20260920.md).

## Results

| Scenario | Result |
| --- | --- |
| Backend START and JOIN | Two SGD 10 payments produced one member and order each; the bloc reached `succeeded`, size 2/2 |
| Browser START and JOIN | The teammate product page opened Hosted Checkout and returned to buyer-owned receipts backed by PostgreSQL |
| Idempotency | Repeating the buyer, intent and request key reused the payment and Checkout link |
| Webhook handling | Replayed valid events stayed idempotent; a modified payload with the old signature returned 400 |
| Missed webhook recovery | Provider-paid/local-`CREATED` payment recovered from Stripe facts once, without duplicate fulfilment |
| Expiry | An expired underfilled bloc created one SGD 10 refund obligation and expired an unpaid Session |
| Refund | The application sent one full Sandbox refund; rechecking created no duplicate |

Real provider calls covered payment, event recovery, Session expiry and refund. Concurrency, rollback, ownership and failure paths used PostgreSQL with mocked Stripe responses; they are not represented as additional real transactions.

## Traceable identifiers

| Purpose | Local payment ID | Provider reference |
| --- | --- | --- |
| Backend START | `01a0bce3-9b2a-734e-9451-b03ef2194f17` | `pi_3UHbaN5iEVLN2uQJ1Ki4Ao37` |
| Backend JOIN | `01a0bce6-8ba6-764f-a16c-223fdbc8b9ac` | `pi_3UHbch5iEVLN2uQJ1yDvwhHW` |
| Recovery and refund | `01a0bce8-1615-7f87-a736-788fbe34a1f3` | `pi_3UHbeV5iEVLN2uQJ1nASa65p`; refund `re_3UHbeV5iEVLN2uQJ1V71krwQ` |
| Expired unpaid Session | `01a0bcea-dec3-71e9-9e43-a981f1bb3b12` | No PaymentIntent or charge |
| Browser START | `01a0bd24-1858-7019-81ed-ef6415c3a237` | Order `eac32257-0c2e-4181-95cd-9a50a73343b8` |
| Browser JOIN | `01a0bd25-529b-7603-b564-3518c8b2cb10` | Order `9e18bf54-9550-4baa-b1d4-d80e4008df8a` |

All successful payments were SGD 10 (`amount_total=1000`). Browser START/JOIN completed bloc `d00e90c3-dff9-4404-b1b8-46445ea9086f`. The refund was SGD 10, provider/local state `succeeded`/`SUCCEEDED`; the original payment remained `SUCCEEDED` because refund state is separate.

## Evidence

- [Machine-checked backend acceptance](evidence/20260920/sandbox-acceptance.json)
- [Machine-checked browser ownership and order results](evidence/20260920/browser-acceptance.json)
- [Machine-checked refund result](evidence/20260920/refund-acceptance.json)
- [Stripe Sandbox transactions](evidence/20260920/stripe-sandbox-transactions.png)
- [START receipt](evidence/20260920/frontend-start-receipt.png), [open bloc](evidence/20260920/frontend-open-bloc.png), and [JOIN receipt](evidence/20260920/frontend-join-receipt.png)
- [Refunded order](evidence/20260920/frontend-refunded-order.png) and [refund receipt](evidence/20260920/frontend-refund-receipt.png)

The run did not verify real Google OAuth, shared deployment, public TLS, production collaborators or live-mode payments. Test cards came from the [official Stripe test documentation](https://docs.stripe.com/testing); no card number or CVC was stored in BlocBuy tables.
