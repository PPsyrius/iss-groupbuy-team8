<!-- report
rubric: E
owner: Panpakorn Siripanich (Pan), Yu Qianshuo (Yorik)
plan: #681
sources: deck B9, B11, B12; docs/quality/nfr-register.md
owns: R01, R02, R06, R16, R20
status: draft
-->

# Appendix E: Known Limits and Evidence {.appendix}

This appendix states the known limits of the SWE5006 system and its pipeline, and gives the evidence behind the main runtime claims: the Stripe Sandbox refund, the paid flow on dev, the performance runs and the system demonstration. Each item names its environment, because a local result, a dev result and a SWE5001 ECS result are different evidence. Statuses are as read on 26 October 2026 unless dated otherwise.

#### Known limits

Table E.1: Known limits of the dev system and its pipeline.

| Limit | Effect | Status and evidence |
|---|---|---|
| CloudFront to load balancer over HTTP (NFR-37) | API payloads and the origin-secret header cross the AWS network unencrypted | Accepted risk on dev (open issues in Table 2.6); the fix needs an origin hostname and certificate |
| SSH port 22 on the dev host | Open to any address although SSM makes it unnecessary | Closed (open issues in Table 2.6) |
| Dev EBS volume not encrypted | Database and host data unencrypted at rest | Open (open issues in Table 2.6) |
| Rollback never exercised | The recovery path is untested on the real host | Never run on dev (section 4.3; open issues in Table 2.6) |
| No dev alarms or dashboards | Failures are noticed by the post-deploy smoke or by people | All CloudWatch alarms belong to SWE5001; DSOMM Monitoring level 0 |
| Dev is not zero-downtime | A deploy restarts the only API container; the database shares the host | The post-deploy smoke's API health check got HTTP 502 through CloudFront in four frontend delivery runs, each while the same commit's `deploy-existing-ec2` was recreating the container: 36215889478 (26 Sep), 37102432667 and 37122348459 (3 Oct) and 37182996807 (4 Oct); accepted for a low-cost dev |
| Secrets in a host file | Created and rotated by hand (`/etc/blocbuy/backend.env`, root 0600) | Accepted for dev; the unapplied production design uses Parameter Store |
| Delivery image rebuilt | Dev delivery rebuilds the JAR and image without repeating the Trivy image scan, so the deployed image is not the one the pull request scanned | Provenance limit; ECR scans on push but does not gate; frontend ADR 0004 and the operator guide to be reconciled |
| Weekly full-history secret scan red | Four historical SonarCloud badge-token findings remain | NFR-02 partially met; scheduled run 40287812848 failed on 26 Oct |
| Passive DAST only | ZAP scans anonymous pages passively; no login, payment or active testing; findings advisory | Header findings fixed; Informational findings under review (section 4.2, Table 4.10) |
| SBOM is a source inventory | Lists Maven and pnpm source dependencies, not the exact deployed JAR, image or bundle; licence findings advisory | NFR-72; no signing or attestation |
| CodeQL unavailable | No GitHub Code Scanning SAST; SonarCloud and SpotBugs remain | Code Scanning API returns 403; `ENABLE_CODEQL=false` |
| One IAM user access key | A long-lived key exists in the project account | Disclosed and accepted (CI authentication, section 4.1; open issues in Table 2.6) |
| GitHub two-factor sign-in | Not enforceable on a personal-account repository | Not recorded per member (section 4.1) |
| Performance on dev | Deployed performance on SWE5006 dev is unverified | Performance status, section 1.4.3; runs in Table E.4 |
| Expiry bound | Expiry within one minute is not guaranteed | The expiry design bound (section 1.4.3); the one dev run is in Table E.3 |
| Separate SWE5006 production stack | Not built; production is the shared SWE5001 release | No SWE5006 production stack, section 1.4 (#226) |

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

On 23 October 2026 on dev, a buyer started a bloc on listing `US34-04` (two seats, S$22 team price, 40-minute lifetime) and paid S$22 through Stripe Sandbox Checkout; nobody joined, and the bloc expired and was refunded with no manual step (`backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md`; PR #664).

Before this, no payment had been confirmed on dev. The dev webhook endpoint used Stripe API version `2026-08-26.dahlia`, while the deployed SDK 34.0.0 expected `2026-09-30.endive`, so the backend rejected the events: a real event at 07:50:59 UTC got HTTP 502. Three paid Checkout sessions were stranded in the created state, for S$49, S$29 and S$22. At about 09:41 UTC the operator replaced the endpoint and its signing secret and turned scheduling on; the backend image was unchanged. The operator then ran the deployed recovery command on each stranded payment, with no SQL update. Because each purchase had expired, each was recorded as refund required and compensated, not admitted to a team, and by 09:51:20 UTC Stripe listed exactly one succeeded refund for each.

Table E.3: The paid START and natural expiry on dev, 23 October 2026 (times UTC).

| Step | Time | Result |
|---|---|---|
| Payment confirmed by webhook | 09:46:50 | HTTP 200; settlement `succeeded` and `FULFILLED` |
| Bloc deadline | 10:24:15.36 | 40-minute lifetime, no deadline edit or manual job run |
| Bloc expired, order cancelled | 10:24:34.27 | 18.9 s after the deadline; the order was cancelled 12 ms after the bloc |
| Refund succeeded in Stripe | 10:24:55.94 | 40.6 s after the deadline; exactly one refund for the PaymentIntent |
| Final audit | 10:25:40 | One succeeded refund per payment; no pending Checkout, overdue team, unfinished refund or open payment case |

These times describe one run, not a latency bound. A second buyer's paid JOIN was shown on dev on 24 October in the system-demonstration backup recording (#657): a second account opened a member's invite link, paid S$22.00 in Stripe Checkout, and My blocs then read "2 of 2 joined · Filled 24 Oct, 7:17 pm" (sprint day log of 24 October). It is a recorded user-interface observation, not a database read or log audit. Crash recovery of refunds under replay was not part of these runs (not shown on dev; Table 1.3).

Screenshots of the payment, the active and expired bloc, the webhook delivery and the "Refund sent" receipt are kept with the record in `backend/docs/payment/evidence/20261023-dev-recovery/`.

#### Performance runs

Neither run was on SWE5006 dev. The k6 test and its gates are in section 4.2; the proposal's target is in section 1.4.3.

Table E.4: Performance runs by environment. Records: `docs/perf/sprint3-baseline.md` (local) and `docs/swe5001/evidence/20261016-http-baseline-dashboard/v2-repeat-summary.json` (SWE5001 ECS).

| Run | Environment | Load | Result |
|---|---|---|---|
| Sprint 3 baseline, 30 Sep 2026 | Local stack, test-only sessions in real Redis | Node closed-loop workload, 100 virtual users per scenario, 200 ms think time, 60 s measured | p95 browse 24.18 ms, JOIN intent 56.56 ms, My Orders 18.38 ms; 0 errors. Expiry under load: 30 teams (the expiry design bound, section 1.4.3) |
| V2 baseline, repeat run `20261016-v2-monolith-repeat`, 16 Oct 2026 | SWE5001 ECS, one monolith task, image from `dev` commit `6c7a99ab` | k6 1.6.1, 10 to 200 requests a second in ten one-minute plateaus, anonymous browse | p95 per plateau 53, 39, 27, 28, 23, 23, 42, 23, 23, 23 ms; 57,600 workload requests, 0 errors, 0 dropped iterations; every gate passed; saturation not reached, so 200 requests a second is a tested lower bound |

The V2 run issued 57,600 workload requests plus one catalogue setup request, which is why the presentation quoted 57,601. It is an anonymous browse result, not a scaling result.

#### System demonstration evidence

No payment was made during the 26 October presentation. The system demonstration on dev showed: the 24 October Sandbox purchase on dev and its receipt (START); the 24 October recording of the second buyer's join; a bloc that expired before the talk; one HTTP 403 on an admin API for a non-admin account; and the saved CI run of the last-seat race test. The backup recording `swe5006-system.mp4` (24 October, 8 min 27 s) and the pipeline recording `swe5006-ci-cd.mp4` (24 October, 4 min 18 s) were shared in the team chat; they are not in the repository.

#### Evidence references

Each row gives the record behind a claim stated without identifiers in the body: commits, CI runs, issues and pull requests, AWS resources and source files.

<!-- table: split -->
Table E.5: Evidence references by section.

| Section | Claim | Record |
| --- | --- | --- |
| 1.4.1 (Table 1.3) | UC1 issue, closed 19 Sep | #24 |
| 1.4.1 (Table 1.3) | UC2 issue, closed 23 Sep | #25 |
| 1.4.1 (Table 1.3) | UC3 issue, closed 24 Sep | #26 |
| 1.4.1 (Table 1.3) | UC4 issue, closed 24 Sep | #27 |
| 1.4.1 (Table 1.3) | UC5 issue, closed 24 Sep | #28 |
| 1.4.1 (Table 1.3) | UC6 issue, closed 25 Sep | #29 |
| 1.4.1 (Table 1.3) | UC7 issue, closed 28 Sep | #30 |
| 1.4.1 (Table 1.3) | UC8 issue, closed 27 Sep | #31 |
| 1.4.1 (Table 1.3) | UC9 issue, closed 19 Sep; admin console extension | #32; #221 |
| 1.4.1 (Table 1.3) | Pay upfront, part of UC3 and UC4 issues | #26, #27 |
| 1.4.1 (Table 1.3) | Receive notifications issue, closed 13 Oct | #215 |
| 1.4.2 (Table 1.4) | Separate SWE5006 production release dropped | #226 |
| 1.4.2 (Table 1.4) | Signed-in end-to-end journey on dev dropped | #229 |
| 1.4.2 (Table 1.4) | Notification bell dropped | #449 |
| 1.4.2 (Table 1.4) | Invite credits view dropped | #452 |
| 1.4.2 (Table 1.4) | Deployed performance tuning dropped | #453 |
| 1.4.2 (Table 1.4) | Admin console polish and design gaps dropped | #346–#349, #351–#353, #371, #382 |
| 2.1 (Table 2.1, Table 2.4) | Sprint container issues, Sprints 1–4 (Sprint 2 plan in #93) | #49, #93, #232, #465 |
| 2.2 (Table 2.5) | Crash recovery of refunds, open | #224 |
| 2.2 (Table 2.5) | Sign-in session decided before the first sprint, 30 Aug | backend design decisions D6, D7 (Table B.3) |
| 2.2 (Table 2.5) | MyBatis decided before the first sprint, 30 Aug | Skeleton plan §1 and §3 |
| 2.2 (Table 2.5) | Separate SWE5006 production stack dropped, closed 11 Oct | #226 |
| 2.2 (Table 2.6) | Crash recovery shown only locally | #224 |
| 2.2 (Table 2.6) | Lock order of ADR 0001 and ADR 0002 not reconciled | #663 |
| 2.2 (Table 2.6) | CloudFront to dev load balancer over HTTP | #227 |
| 2.2 (Table 2.6) | SSH port 22 closed, read-only check 27 Oct 05:19 UTC | `aws ec2 describe-security-groups` |
| 2.2 (Table 2.6) | Dev EBS volume unencrypted | `vol-08df685248a8b1aea` |
| 2.2 (Table 2.6) | IAM access key disclosure and its limiting policy; no MFA device (27 Oct) | `docs/deploy/aws-account-governance/README.md`; `BlocBuySsmPortForwarding`; `aws iam list-mfa-devices` |
| 2.2 (Table 2.6) | Backend rollback never run | #623 |
| 2.2 (Table 2.6) | OWASP ZAP findings and header fixes | #610, #636–#647; fixes #700, #703 |
| 2.2 (Table 2.6) | Actuator, springdoc and error path exposure undecided | #549 |
| 2.2 (Table 2.6) | Shared subnets in SWE5001 infrastructure code | #589 |
| 2.2 (Table 2.6) | Sprint 3 retrospective reconstructed, review draft | #451 |
| 2.2 (Table 2.6) | Sprint 4 review, retrospective and burndown missing | #619 |
| 2.2 | Presentation remarks recorded under the report tracking issue | #463 |
| 2.2 (Table 2.7) | Repository link remark | #693 |
| 2.2 (Table 2.7) | ZAP before/after remark; before runs 22 and 26 Oct; after run 27 Oct | #694; runs 39733450177, 40298983391, 40459212995 |
| 2.2 (Table 2.7) | Submission deadline remark, closed when the first draft merged | #695; #698; #463 |
| 2.3 (Table 2.8) | Web application live on dev, 22 Sep | #93 |
| 2.3 (Table 2.8) | Admin console merged, 30 Sep | #305–#307 |
| 2.3 (Table 2.8) | First production deployment on ECS, 6 Oct | #418; run 37470479078 |
| 2.3 (Table 2.8) | First three-service promotion, 17 Oct | #534; run 39008208244 |
| 2.3 (Table 2.8) | Second three-service promotion, 19 Oct | #564; run 39259845179 |
| 2.3 (Table 2.8) | Last promotion before the presentations, 20 Oct | #574; run 39435280952 |
| 2.3 (Table 2.8) | Paid start and expiry refund on dev, 23 Oct | #664 |
| 2.3 (Table 2.8) | Second buyer's paid join recording, 24 Oct | #657 |
| 2.3 (Table 2.8) | SWE5006 presentation, 26 Oct | #464 |
| 3.1 | COLA layering locked on 30 Aug | backend design decision D1 (Table B.3) |
| 3.1 | `Result<T>` envelope superseded the bare DTO and ProblemDetail choice, 11 Sep | backend design decision D10 and its historical note (Table B.3) |
| 3.1 | Dev request path and security group rules | `docs/deploy/dev-cloudfront-deployment.md`; EC2 Name tag `BlocBuy-dev` |
| 3.1 (Table 3.3) | Platform versions read on 26 Oct, re-read unchanged on 27 Oct | `46faacd7`; `6d14e449` |
| 3.1 (Table 3.4) | Decisions behind the architecture | backend design decisions D1, D4, D5, D6, D7, D12, D19, plan sections 4 and 8, and the historical note superseding D10 (Table B.3) |
| 3.2 | COLA module layout and the Spring Boot 3.5 starting point | backend design decision D1 (Table B.3); `backend/pom.xml` |
| 3.2 (Table 3.5) | Strategies without an ADR rest on earlier decisions | backend design decisions D1, D9; D1–D20 (Table B.3) |
| 3.2.4 | Hand-written converters decided before the first sprint | backend design decision D9 (Table B.3) |
| 3.2.6 | Server session design | backend design decisions D6, D7 (Table B.3) |
| 3.2.8 (Table 3.12) | Combined lock order not yet in one record, open backend task | #663 |
| 3.2.8 | Transaction settings and listener phases read in the code | `46faacd7` |
| 3.4.1 | Transactional outbox writer | #473 |
| 3.4.1 | Outbox relay merged to dev, Template Method base class | #485; `44fb9bed` |
| 3.4.1 | Idempotent Receiver of the notification consumer | #505, #513 |
| 3.4.1 | 23 Oct paid start settled on dev | `backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md` |
| 3.4.1 | 24 Oct recorded join | sprint log, 24 Oct |
| 3.4.2 | 23 Oct paid START expired and refunded on dev | `backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md` |
| 3.4.3 | Class, method and table names checked in code, 26 Oct | `46faacd7` |
| 3.4.4 | Design diagrams show the code, 26 Oct; credits written but not shown | `46faacd7` |
| 3.4.5 | UC9 first version, versioned drafts and 412 rule, 11 Sep | #72; #32 |
| 3.4.5 | UC9 team terms and first-team price in one command, 29 Sep | #242; #221 |
| 3.4.5 | Deletion eligibility and history counts on the admin read, 1 Oct | #300 |
| 3.4.5 | Admin console screens from 30 Sep | #294–#307 |
| 3.4.5 | UC5 expiry, `GroupDealExpiryService` and team events, 23 Sep | #148; #28 |
| 3.4.5 | UC5 expiry sweep and scheduler job, 24 Sep | #162, #173 |
| 3.4.5 | 8 Oct demo-listing seed on dev, 12 admin writes in 4.0 s | sprint log, 8 Oct; `scripts/seed-dev-demo-listings.sh`; #222 |
| 3.5.1 | Factory Method refactoring merged, 13 Oct; before-design captions and candidates table | `081047f5` (before: `081047f5^`); #424 |
| 3.5.1 (Table 3.20) | Issue that scoped the change excluded creator registries | #421 |
| 3.5.1 (Table 3.21) | Refactoring evidence change | #421, #422, #423, #424 |
| 3.5.1 | Local full build of 7 Oct: 1,615 cases, 194 lines, 92 branches | sprint log, 7 Oct |
| 3.5.2 | First expiry commit published TeamExpired, 23 Sep | `fbb71a9e` (#148) |
| 3.5.2 | Inline call rejected when the change was planned | `docs/agile/sprint-02/lifecycle-completion-plan-20260922.md` |
| 3.5.2 | Pull request adding a dispatch-order test, open | #612 |
| 3.5.2 | 23 Oct dev expiry and refund | `backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md` |
| 3.5.3 | Relay before the pattern, state of the relay pull request on 13 Oct | `94407979` |
| 3.5.3 | Review faults reproduced on 13 Oct | sprint log, 13 Oct |
| 3.5.3 | Pull request head that failed the SonarCloud duplication condition (3.1%) | `8f063ee6` |
| 3.5.3 | Relay and original transports | `c74f8a97` |
| 3.5.3 | Base class and review fix by Pan, 13 Oct | `44fb9bed`; squash `4234d5d0` (#485) |
| 3.5.4 | Strategy in the first pricing commit, 8 Sep; no switch on the mode in history | `2c6ea4f3`; `git log -S "case DUAL"` |
| 3.5.4 | Pricing code by Liu Jin and first-team quote by Eric, 9 Sep | `2c6ea4f3`; `cb02ad55` |
| 3.5.5 | Names and tests read in the code, 26 Oct | `6d14e449` |
| 3.5.5 | First listing State by Eric, 11 Sep; deletion eligibility by Pan, 1 Oct | #72; #300 |
| 4 | Facts read on 26 October at this dev commit | `61486f0e` |
| 4.1 | Commit count at the final commit | `git rev-list --count 6d14e449` |
| 4.1 (Table 4.3) | Sprint tag commits, sprints 1–4 | `61c8806c`; `c30ed5a1`; `414ee79a`; `fba3cfbd` |
| 4.1 | Account governance record of the IAM user exception | `docs/deploy/aws-account-governance/README.md` |
| 4.1 (Table 4.6) | Terraform state bucket | `blocbuy-tfstate-402178232802` |
| 4.2 | First ZAP dispatch on dev with verified coverage, 24 Oct | run 40107924516 |
| 4.2 (Table 4.10) | ZAP before: first baseline 22 Oct; scheduled run 26 Oct | runs 39733450177, 40298983391 |
| 4.2 (Table 4.10) | ZAP after: dispatch 27 Oct on the fixed commit | run 40459212995 on `2f23d923` |
| 4.2 (Table 4.10) | ZAP finding issues: Medium and Low fixed; Informational pending | #636–#641 fixed; #642–#647 pending |
| 4.2 (Table 4.10) | Header fixes: img-src and Permissions-Policy; style-src and COEP/COOP/CORP | #700 (#636, #641); #703 (#637–#640) |
| 4.2 | ZAP reports and run metadata before the fixes | `docs/quality/evidence/zap/` |
| 4.2 (Table 4.11) | Static test counts read at this dev commit | `61486f0e` |
| 4.2 | Executed counts: runs on the final code (backend push; frontend push; frontend pull request with the `happy-dom` bump #691) | runs 40200028697 (`ad2a73e6`), 40294726775 (`9135ad8b`), 40206547524 |
| 4.2 (Table 4.12) | Last-seat race test in CI | run 40200028697, `SonarCloud Backend` job |
| 4.2 | SonarCloud analyses behind the gate result | backend `ad2a73e6`; frontend `9135ad8b` |
| 4.2 | k6 run on SWE5001 ECS, 16 Oct, image built from this dev commit | `6c7a99ab` |
| 4.2 (Table 4.14) | SpotBugs and BCEL mismatch, fix | run 39463373272; #580 (`0baf2052`) |
| 4.2 (Table 4.14) | Weekly full-history secret scan failures; digest fix | runs 37287243039, 38228025996, 39217910471, 40287812848; #580 |
| 4.2 (Table 4.14) | SWE5001 environment run stopped by spending budget | run 39442654274 |
| 4.3 (Table 4.16) | Latest backend and frontend dev deliveries | run 40200028697 (`ad2a73e6`); run 40294726775 (`9135ad8b`) |
| 4.3 | Smoke failures; all four were 502s (26 Sep, 3–4 Oct) | runs 36215889478, 37102432667, 37122348459, 37182996807; `docs/ci/dora-metrics-20261022.md` |
| 4.3 | Rollback configured, 4 Sep | `ab640878` |
| 4.3 | Rollback drill on dev, open | #623 |
| 4.3 | Dev CloudFront distribution | `E3U5W7UWDL8LRV` |
| 4.3 | SSH port 22 closed; EBS unencrypted | `aws ec2 describe-security-groups`; `vol-08df685248a8b1aea` |
| 4.3 | Product image bucket | `blocbuy-media-dev-402178232802` |
| 4.3 | First OIDC subject fix, 5 Sep; placeholder instance ID | `121a0733`; `i-0123456789abcdef0` |
