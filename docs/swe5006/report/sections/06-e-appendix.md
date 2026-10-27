<!-- report
rubric: E
owner: Panpakorn Siripanich (Pan), Yu Qianshuo (Yorik)
plan: #681
sources: deck B9, B11, B12; docs/quality/nfr-register.md
status: draft
-->

# Appendix E: Known Limits and Evidence {.appendix}

This appendix states the known limits of the SWE5006 system and its pipeline, and gives the evidence behind the main runtime claims: the Stripe Sandbox refund, the paid flow on dev, the performance runs and the system demonstration. Each item names its environment, because a local result, a dev result and a SWE5001 ECS result are different evidence. Statuses are as read on 26 October 2026 unless dated otherwise; each is re-checked at the freeze.

#### Known limits

Table E.1: Known limits of the dev system and its pipeline.

| Limit | Effect | Status and evidence |
|---|---|---|
| CloudFront to load balancer over HTTP (NFR-37) | API payloads and the origin-secret header cross the AWS network unencrypted | Accepted risk on dev until 16 Nov 2026 (#227); the fix needs an origin hostname and certificate |
| SSH port 22 on the dev host | Open to 0.0.0.0/0 on 21 and 24 Oct although SSM makes it unnecessary | The team decided on 24 Oct to close it; closed: the read-only check of 27 Oct (05:19 UTC, `aws ec2 describe-security-groups`) found no rule admitting port 22 (section 4.3) |
| Dev EBS volume not encrypted | Database and host data unencrypted at rest | Read-only AWS check, 19 Oct; no issue raised |
| Rollback never exercised | The recovery path is untested on the real host | `rollback-backend.yml` 0 runs; no automatic rollback recorded; rollback drill #623 open |
| No dev alarms or dashboards | Failures are noticed by the post-deploy smoke or by people | All CloudWatch alarms belong to SWE5001; DSOMM Monitoring level 0 |
| Dev is not zero-downtime | A deploy restarts the only API container; the database shares the host | HTTP 502 during the 3–4 Oct deploys; accepted for a low-cost dev |
| Secrets in a host file | Created and rotated by hand (`/etc/blocbuy/backend.env`, root 0600) | Accepted for dev; the unapplied production design uses Parameter Store |
| Delivery image rebuilt | Dev delivery rebuilds the JAR and image without repeating the Trivy image scan, so the deployed image is not the one the pull request scanned | Provenance limit; ECR scans on push but does not gate; frontend ADR 0004 and the operator guide to be reconciled |
| Weekly full-history secret scan red | Four historical SonarCloud badge-token findings remain | NFR-02 partially met; scheduled run 40287812848 failed on 26 Oct |
| Passive DAST only | ZAP scans anonymous pages passively; no login, payment or active testing; findings advisory | 2 Medium and 4 Low header findings open (#610, Table 4.10) |
| SBOM is a source inventory | Lists Maven and pnpm source dependencies, not the exact deployed JAR, image or bundle; licence findings advisory | NFR-72; no signing or attestation |
| CodeQL unavailable | No GitHub Code Scanning SAST; SonarCloud and SpotBugs remain | Code Scanning API returns 403; `ENABLE_CODEQL=false` |
| One IAM user access key | A long-lived key exists in the project account | Disclosed and accepted; limited to SSM port forwarding and the organisation's guardrails (section 4.1) |
| GitHub two-factor sign-in | Not enforceable on a personal-account repository | Not recorded per member (section 4.1) |
| Performance on dev | Never measured on SWE5006 dev | NFR-15 "measured locally; deployed performance remains unverified" |
| Expiry bound | The design bound is one sweep interval (60 s, fixed delay) plus the sweep time, not a one-minute guarantee | Local worst observation lag 58.18 s over 30 teams; one dev bloc expired 18.9 s and was refunded 40.6 s after its deadline (Table E.3) |
| Separate SWE5006 production stack | Designed (`backend-app-stack`, 21 resources) but never applied | Closed as not planned (#226); production is the shared SWE5001 release |

#### Stripe Sandbox refund, tested locally (20 September 2026)

Tested locally, not on dev: a local backend and PostgreSQL made real calls to the team's Stripe Sandbox account (test mode), without Google sign-in or a deployed environment (`backend/docs/payment/stripe-sandbox-acceptance-20260920.md`).

Table E.2: Local Sandbox acceptance, 20 September 2026.

| Scenario | Result |
|---|---|
| START and JOIN | Two SGD 10 payments created one member and one order each; the bloc succeeded with 2 of 2 members |
| Webhook replay | Replayed valid events changed nothing; a modified payload with the old signature was rejected with 400 |
| Expiry | An under-filled bloc expired and produced one SGD 10 refund obligation; its unpaid Checkout session expired |
| Refund | One full Sandbox refund succeeded; a recheck created no duplicate |

Screenshots of the open bloc, the START and JOIN receipts, the refunded order, the refund receipt and the Sandbox transaction list are kept with the record in `backend/docs/payment/evidence/20260920/`.

#### Paid flow and expiry refund on dev (23–24 October 2026)

On 23 October 2026 on dev, a buyer started a bloc on listing `US34-04` and paid S$22 through Stripe Sandbox Checkout; nobody joined, and the bloc expired and was refunded with no manual step (`backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md`). Before this, the dev webhook endpoint had used an older Stripe API version than the deployed SDK expected, so no payment had been confirmed on dev; the operator replaced the endpoint and its signing secret, turned scheduling on, and compensated three earlier stuck payments with one refund each. The backend image was unchanged.

Table E.3: The paid START and natural expiry on dev, 23 October 2026 (times UTC).

| Step | Time | Result |
|---|---|---|
| Payment confirmed by webhook | 09:46:50 | HTTP 200; settlement `succeeded` and `FULFILLED` |
| Bloc deadline | 10:24:15.36 | 40-minute lifetime, no deadline edit or manual job run |
| Bloc expired, order cancelled | 10:24:34.27 | 18.9 s after the deadline |
| Refund succeeded in Stripe | 10:24:55.94 | 40.6 s after the deadline; exactly one refund for the PaymentIntent |
| Final audit | 10:25:40 | One succeeded refund per payment; no pending Checkout, overdue team, unfinished refund or open payment case |

These times describe one run, not a latency bound. A second buyer's paid JOIN was shown on dev on 24 October in the system-demonstration backup recording; it is a recorded user-interface observation, not a log audit. Crash recovery of refunds under replay is tested in CI (`StripeRolloutAcceptanceIT`) but was not shown on dev.

Screenshots of the payment, the active and expired bloc, the webhook delivery and the "Refund sent" receipt are kept with the record in `backend/docs/payment/evidence/20261023-dev-recovery/`.

#### Performance runs

Neither run was on SWE5006 dev. The k6 test and its gates are in section 4.2; the proposal's target is in section 1.4.3.

Table E.4: Performance runs by environment.

| Run | Environment | Load | Result |
|---|---|---|---|
| Sprint 3 baseline, 30 Sep 2026 | Local stack, test-only sessions in real Redis | Node closed-loop workload, 100 virtual users per scenario, 200 ms think time, 60 s measured | p95 browse 24.18 ms, JOIN intent 56.56 ms, My Orders 18.38 ms; 0 errors. Expiry under load: 30 teams, worst observation lag 58.18 s |
| V2 baseline, repeat run `20261016-v2-monolith-repeat`, 16 Oct 2026 | SWE5001 ECS, one monolith task, image from `dev` commit `6c7a99ab` | k6 1.6.1, 10 to 200 requests a second in ten one-minute plateaus, anonymous browse | p95 per plateau 53, 39, 27, 28, 23, 23, 42, 23, 23, 23 ms; 57,600 workload requests, 0 errors, 0 dropped iterations; every gate passed; saturation not reached, so 200 requests a second is a tested lower bound |

The V2 run issued 57,600 workload requests plus one catalogue setup request, which is why the presentation quoted 57,601. It is an anonymous browse result, not a scaling result.

#### System demonstration evidence

No payment was made during the 26 October presentation. The system demonstration on dev showed: the 24 October Sandbox purchase on dev and its receipt (START); the 24 October recording of the second buyer's join; a bloc that expired before the talk; one HTTP 403 on an admin API for a non-admin account; and the saved CI run of the last-seat race test. The backup recording `swe5006-system.mp4` (24 October, 8 min 27 s) and the pipeline recording `swe5006-ci-cd.mp4` (24 October, 4 min 18 s) were shared in the team chat; they are not in the repository.
