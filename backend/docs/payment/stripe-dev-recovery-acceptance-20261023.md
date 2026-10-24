# Dev Checkout recovery acceptance — 23 Oct 2026 (#661)

Operator and issue owner: @Yorick-Yu. This is dev Sandbox evidence for #661, #517 and #223, not production or two-buyer/crash acceptance. Times below are UTC; Singapore/China time is UTC+8.

## Target and cause

- Site: `https://d1b3np88z657cp.cloudfront.net`; EC2 `i-0852b6c3475b19f05`, account `402178232802`, Region `ap-southeast-1`.
- Backend commit: `45bbd2b5157d6655b4851e6ba9307f6dc7a4f4ad`. Backend source diff against audited dev `da0ab556d84dace03b376662aebace0dab6b9e1f` was empty.
- Image unchanged: `402178232802.dkr.ecr.ap-southeast-1.amazonaws.com/dev/blocbuy@sha256:a6165b4c2fc75b5445d636faadb130628b6db1f01fbe6b66d3918e24c27e4ebf`.
- Stripe Sandbox: `acct_1UALPt5iEVLN2uQJ`. Old dev endpoint `we_1ULHuv5iEVLN2uQJx3cabUJX` used `2026-08-26.dahlia`; deployed SDK 34.0.0 expects `2026-09-30.endive`.
- Real failed event `evt_1UTdFQ5iEVLN2uQJlml1ci9R` returned HTTP 502 / `PAYMENT_502_001` at 07:50:59, trace `1cb727a4-b58e-44c4-82b3-176adb43ffb5`. A correctly signed dahlia payload reproduces the typed-deserialization rejection locally. This establishes the 23 Oct cause; it does not reconstruct every 16 Oct delivery in #517.
- Scheduling was off. Before the change, there were three created Checkout payments past their deadlines, zero overdue open teams, zero unfinished refunds and zero unresolved review cases. Six seeded open teams expire on 31 Dec 2027; they were left unchanged.

## Controlled configuration change

At 09:41:39 the backlog was rechecked. The reviewed deployment script and compose file matched the local source hashes. A private root-owned `0600` env backup was kept on the dev host. New endpoint `we_1UTeyn5iEVLN2uQJAC0iW2sd` uses the same dev URL, Your account, `2026-09-30.endive`, and the four documented Checkout events. Its signing secret was installed without printing it. `BLOCBUY_SCHEDULING_ENABLED` changed to `true`; Checkout stayed enabled.

The backend was force-recreated at 09:41:45 and healthy by 09:42:06 on the same digest. PostgreSQL and Redis retained their 17 Sep start timestamps. After a real successful payment delivery, the old dev endpoint was disabled, not deleted. Both prod endpoint configurations were unchanged. The existing signature checks, safe deserializer and business transaction code were not weakened.

## Old paid sessions

Each payment was first inspected with the deployed `stripe-ops` runner. All three returned `localStatus=CREATED`, `sessionStatus=complete`, `paymentStatus=paid`, and `VERIFY_FULFILMENT_OR_RECORD_COMPENSATION`. Recovery then used `--operator=Yorick-Yu --confirm-recovery=true`. The temporary operator containers used the same image, loopback-only random HTTP ports, migrations and scheduling disabled, and bounded CPU/memory; each exited and was removed. No SQL updates were used.

| Payment reference | Amount | Outcome | Stripe refund |
|---|---|---|---|
| `01a14345-9b3e-75ae-a8cc-2fe9f3899ecd` | S$49 | `REFUND_REQUIRED`; refund succeeded | `re_3UR45C5iEVLN2uQJ1KEqGm2O` |
| `01a14353-5315-712d-bf67-2d6350881222` | S$29 | `REFUND_REQUIRED`; refund succeeded | `re_3UR4JW5iEVLN2uQJ0xKleC3O` |
| `01a167bb-3fb2-7797-b937-fda2f6ae39a1` | S$22 | `REFUND_REQUIRED`; refund succeeded | `re_3UTdFO5iEVLN2uQJ0EEjAxzE` |

At 09:51:20 each PaymentIntent's Stripe refund list contained exactly one `succeeded` refund, the correct amount/currency and matching obligation/payment metadata, with `has_more=false`. Pending Checkout payments, unfinished refunds and unresolved cases were all zero. These expired payments were compensated, not admitted to new teams. The signed-in buyer's previously stuck S$22 receipt now shows [Refund sent](evidence/20261023-dev-recovery/recovered-refund.png).

## New paid START and natural expiry

The existing `US34-04` product was used without changing its terms: two seats, S$22 team price, 40-minute lifetime. Google login used the existing buyer session; payment used a Stripe Sandbox test card and synthetic billing details.

| Evidence | Identifier / result |
|---|---|
| Payment | `01a16825-93ac-778e-b4c0-547fb8a67e74` |
| Checkout Session | `cs_test_a1cJciR4kz3khNWZlILiQN7NuzjUZFUiC1PYpukCCAdZEBb3jc4uIbLTsO` |
| PaymentIntent | `pi_3UTf3i5iEVLN2uQJ1lZK2zAg` |
| Bloc | `01a16825-9317-7ed2-8ab7-b8f5c2a60e9f` |
| Order | `01a16827-ef19-756a-9db7-aa93a3e47330` |
| Webhook | `evt_1UTf3j5iEVLN2uQJrNRU6XYQ`, endive, HTTP 200 / `COMMON_200_000`, delivered 09:46:50 |
| Settlement | `succeeded` / `FULFILLED`, paid time 09:46:49.940322; webhook row recorded 09:46:49.904423 |
| Browser | Payment received; order and bloc references visible; My blocs Active 1, one of two joined; My orders waiting for the bloc |
| Natural deadline | 10:24:15.355419; no deadline edits or manual job invocation |
| Automatic expiry | Bloc `expired` at 10:24:34.271813, 18.916 seconds after its deadline; order `cancelled` at 10:24:34.283475 |
| Automatic refund | Obligation `01a1684a-7bf4-70b2-a689-aee0421c7778`; `succeeded` at 10:24:55.940855, 40.585 seconds after the deadline |
| Stripe confirmation | `re_3UTf3i5iEVLN2uQJ1xbGulcM`, succeeded, S$22; one refund for the PaymentIntent, matching metadata, `has_more=false` |
| Buyer confirmation | [Refund sent](evidence/20261023-dev-recovery/natural-refund.png); [My blocs closed and order cancelled](evidence/20261023-dev-recovery/expired-bloc.png) |

The read-only observer saw the transition from open/pending to expired/cancelled/refund-pending, then refund-succeeded; that final state persisted through a further 90 seconds of observation, ending at 10:26:43.632608. The [observer output](evidence/20261023-dev-recovery/expiry-observation.json) records those snapshots. The timer delay is one minute for team expiry and 30 seconds for the refund worker; the measured 18.916/40.585 seconds describe this run, not a general latency bound. Confirm the receipt and Stripe refund, not just the My blocs "Refunded" filter, which still says "refund due".

The [final read-only audit](evidence/20261023-dev-recovery/dev-final-audit.json), started at 10:25:40, found exactly one succeeded Stripe refund for each of the four payments, zero pending Checkout payments, zero overdue open teams, zero unfinished/review/failed refunds, and zero unresolved payment cases. The six seeded teams still have their 2027 deadlines. The backend remained healthy on the same digest, PostgreSQL/Redis retained their start times, and the endpoint inventory confirmed the dev cutover with prod unchanged.

## Verification boundaries

Frontend `pnpm check` with Node 24.19.0 / pnpm 12.4.2 passed 77 files and 815 tests, plus generation, types, lint, formatting and knip. The local copy change is not yet deployed. The PostgreSQL 18 focused backend run passed 508 tests (483 unit/architecture/HTTP tests and 25 integration tests), with zero failures, errors or skips; Maven `verify` completed in 55.713 seconds. Static/format gates ran too. The modified backlog query ran successfully against the isolated full-migration database, returning zero pending Checkout and refund work. The temporary database was stopped afterward. This is focused verification, not the full Docker-backed repository suite.

The backend selection was `Stripe*Test,PaymentFactsTest,CheckoutPaymentTest,CheckoutQuoteTest,PaidGroupDealTest,PaidGroupDealServiceTest,OrderFulfilmentAdapterTest,RefundServiceTest,TeamExpiryRefund*Test,PaymentBrowserServiceTest,PurchaseIntentExpiry*Test,GroupDealExpiry*Test,TeamExpiry*Test,RefundWorker*Test,ArchitectureTest`, plus `StripeRolloutAcceptanceIT,TeamExpirySchedulingIT,PurchaseIntentExpiryIT` with the guarded local PostgreSQL fixture. `StripeCheckoutGatewayTest` now covers the observed dahlia mismatch and asserts `PAYMENT_PROVIDER_ERROR`; it still accepts the SDK version and rejects bad signatures.

Screenshots: [paid receipt](evidence/20261023-dev-recovery/payment-success.png), [real HTTP 200 delivery](evidence/20261023-dev-recovery/webhook-200.png), [active bloc/order](evidence/20261023-dev-recovery/active-bloc.png). The [local UI screenshot](evidence/20261023-dev-recovery/local-copy.png) uses MSW fixture data to verify wording only, not deployed business behavior.

The independent second-buyer flow (#590), deterministic refund crash recovery (#224), and production rollout are not established by these checks. This change does not close those issues.
