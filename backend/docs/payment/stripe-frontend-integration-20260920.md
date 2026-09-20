# Stripe frontend integration — local acceptance

> Historical Sandbox evidence. The local endpoints and commands below have been retired;
> see [release cleanup](stripe-release-cleanup-20260920.md) for the current boundary.

Update: refund execution and the order list are now verified locally. See [refund/order acceptance](stripe-refunds-orders-20260920.md). The earlier scope and test counts below describe the checkout-only stage.

The teammate's product detail page now opens Stripe Hosted Checkout for **Start a bloc** and **Join this bloc**. Both paths were paid with official test cards in Team8 Sandbox on 20 September 2026. After the webhook, the browser returned to a receipt showing the saved PostgreSQL order and bloc IDs.

## Evidence

| Browser action | Payment ID | Saved order |
| --- | --- | --- |
| Buyer 1 starts a bloc and pays SGD 10 | `01a0bd24-1858-7019-81ed-ef6415c3a237` | `eac32257-0c2e-4181-95cd-9a50a73343b8` |
| Buyer 2 joins that bloc and pays SGD 10 | `01a0bd25-529b-7603-b564-3518c8b2cb10` | `9e18bf54-9550-4baa-b1d4-d80e4008df8a` |

Both payments are `SUCCEEDED/FULFILLED`, with one order and member each. Bloc `d00e90c3-dff9-4404-b1b8-46445ea9086f` reached `succeeded`, size `2/2`. Stripe reported `livemode=false` for both. No real money was charged.

- [Start payment receipt](evidence/20260920/frontend-start-receipt.png)
- [Teammate product page with an open bloc](evidence/20260920/frontend-open-bloc.png)
- [Join payment receipt](evidence/20260920/frontend-join-receipt.png)
- [Machine-checked payment, order and ownership results](evidence/20260920/browser-acceptance.json)

## API connection

| Endpoint | Input and behavior |
| --- | --- |
| `POST /api/v1/payments/intents` | `{operation: START or JOIN, businessId}`; product ID for START, bloc ID for JOIN. Server reads price and validates eligibility. |
| `POST /api/v1/payments/checkout-sessions` | `{intentId}` and `Idempotency-Key`; creates or reuses the Checkout attempt. |
| `GET /api/v1/payments/{id}` | Returns only the signed-in buyer's payment, order, bloc and pending-refund status. |

The two POSTs require `X-BlocBuy-Request: checkout` and JSON. Sa-Token derives the buyer from the HttpOnly Cookie; requests cannot override buyer or amount. The generated OpenAPI client uses relative `/api` URLs and the existing session handling. No Stripe secret, database password or operator token enters the frontend bundle.

Checkout retries keep the same intent and request key. A synchronous guard also prevents double clicks. Redirects are restricted to HTTPS `checkout.stripe.com`. Each newly created Checkout return URL includes its payment reference; the backend still checks ownership. Older already-issued local links use a same-tab fallback. The receipt polls the database-confirmed state, stops automatic polling after 30 successful reads, and offers manual refresh. It never treats a return URL or a pending refund as proof of payment or refund completion.

## Run locally

Use the repository's pinned Node and pnpm versions. Build the backend first:

```bash
cd backend
./mvnw -B -ntp verify -Popenapi-export -DskipITs
cd ..
python3 scripts/stripe-local-runtime.py start --stripe-env /Users/spiral/Documents/BlocBuy-Stripe-Local/stripe.env
cd frontend
pnpm install --frozen-lockfile
pnpm dev:stripe-local
```

If this private backend is already running, stop it with `python3 scripts/stripe-local-runtime.py stop` before restarting. Data is retained. The frontend listens on `127.0.0.1:5173` and its opt-in proxy targets `127.0.0.1:18589`. PostgreSQL is the dedicated local database on port `55440`; Redis is local on `56379`. The team's default Vite proxy and server/database profiles were not changed.

Open `http://127.0.0.1:18589/local/stripe/login`. The fixture login accepts only buyers 1–3 and the locally generated operator token in the private `runtime.json`. Keep that token out of screenshots and shared files. This login opens a real Sa-Token Cookie session backed by local Redis; it does not test Google OAuth. The route exists only with the isolated adapter profile and an explicit local-browser flag.

The login redirects to the fixture product. Use **Start a bloc**, complete a Sandbox payment, then sign in as buyer 2 and use **Join this bloc**. The receipt's order reference comes from the database. Use the same browser hostname (`127.0.0.1`) throughout local testing.

## Repeat verification

```bash
# Repository root: real local PostgreSQL; Stripe network mocked in these tests.
bash scripts/test-stripe-sprint2.sh

# Frontend: type/lint/format/106 tests/unused-code, then production build.
cd frontend
pnpm check
pnpm build
cd ..

# Reads the already-paid Sandbox sessions and local business records; no new charges.
python3 scripts/verify-stripe-browser.py \
  --start 01a0bd24-1858-7019-81ed-ef6415c3a237 \
  --join 01a0bd25-529b-7603-b564-3518c8b2cb10
```

The focused backend suite passed 57 tests, including real database/order assertions and six Cookie/API-boundary tests. The full Maven verification and frontend production build passed. Browser-created payments were separately checked against Stripe and the running database; anonymous reads returned 401, another buyer's payment returned 404, and missing/wrong POST protection headers returned 400/403.

## Still outside this acceptance

- Real Google OAuth with the shared deployed frontend/backend. The local test login is deliberately separate.
- Shared deployment and replacement of the opt-in local intent/order/expiry adapters with the team's final module implementations.
- Buy-alone checkout, the complete account order-history page, and execution of refunds. A pending refund can be displayed, but it has not been sent.

All changes remain in the local `feature/yorick-stripe-sprint2` worktree. No commit, push, PR, GitHub setting change, or shared AWS database write was made.
