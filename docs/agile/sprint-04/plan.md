# Sprint 4 plan (2026-10-12 to 2026-10-23)

Status: draft for Sprint Planning on 2026-10-12. Numbers, owners and dates change only at that meeting; after it, this file mirrors the Sprint 4 container issue #465.

Sources read on 2026-10-11: `origin/dev` at `414ee79a`, `docs/agile/sprint-03/plan.md` and `review.md`, open issues and PRs, `docs/swe5001/HANDOFF.md`, the SWE5001 proposal, refactoring plan and implementation plans, backend ADR 0007 (#443), the SWE5001 briefing and presentation guidelines, and both course report templates.

## 1. Calendar

Sprint 4 is shared by two courses on one trunk: SWE5006 (this sprint's backlog and the final report) and SWE5001 (the distributed shape on Amazon ECS, presented first).

| Item | Date |
|---|---|
| Sprint Planning; ADR 0007 accepted at stand-up; NFR-37 decision (#227) | Mon 2026-10-12 |
| Sprint 3 review, retrospective and progress report (#451); tag `v0.1.0-sprint3` pushed at `414ee79a` on 2026-10-11 | by Tue 2026-10-13 |
| US13 Checkout, scheduling and refunds on dev (#223): critical path | by Wed 2026-10-14 |
| #455 service assemblies merged (identity runtime follows by 10-16) | Wed 2026-10-14 |
| Mid-sprint check (owner vs actual, rebalance); **identity go/no-go** (§6 item 5) | Thu 2026-10-15 |
| **SWE5001 code freeze** (everything the presentation needs merged into `dev`) | Fri 2026-10-16 EOD |
| SWE5001 Sprint 4 promotion `dev` → `prod`; evidence session (V2–V7) and fallback recording | Mon 2026-10-19 |
| SWE5001 full rehearsal; promotion fallback day | Tue 2026-10-20 |
| **SWE5001 presentation** (Team 8; #393 notice, confirmed on 2026-10-11; supersedes 23 Oct in the published schedule) | Wed 2026-10-21, 16:15–16:55 SGT |
| SWE5006 code freeze | Thu 2026-10-22 EOD |
| Sprint 4 Review, Retrospective, progress report; tag `v0.1.0-sprint4` | Fri 2026-10-23 |
| Hardening window: #453 deployed performance, #454 security and quality closure | 2026-10-26 to 2026-11-02 |
| SWE5006 report: use-case and design-problem drafts (#463) | Wed 2026-10-28 |
| SWE5006 report: integrated draft | Sat 2026-10-31 |
| SWE5006 presentation rehearsal (#464) | Mon 2026-11-02 |
| SWE5006 presentation | 2026-11-03 / 2026-11-04 |
| SWE5001 written examination (tentative, open book) | 2026-11-02 to 2026-11-06 |
| SWE5006 final report submission | Tue 2026-11-10 |
| SWE5001 final report submission (#462) and final `terraform destroy` | Mon 2026-11-16 |

The SWE5001 promotion moves from the 2026-10-20 target in #376 to 2026-10-19, so the evidence session and the fallback recording run on promoted code and 2026-10-20 stays free for recovery and the rehearsal.

## 2. Sprint goal

By 2026-10-23, on the **deployed dev environment**, a buyer can start or join a bloc on an admin-configured listing, pay the quoted price through Stripe Sandbox, be refunded automatically when the bloc expires, and see a notification for each of these events in the header bell, delivered through the transactional outbox and an idempotent SQS consumer. The first production release is cut from a tag with a rehearsed rollback. On SWE5001 ECS, the same code runs as three services, `identity-svc`, `commerce-svc` and `notification-svc`, with identity reached through a fail-closed internal HTTP call and commerce and notification connected by SNS and SQS, and HTTP scaling, queue-driven scaling, self-healing, safe deployment and a DLQ drill are captured as evidence before the 2026-10-21 presentation.

## 3. Starting position

| Area | State on `dev` at `414ee79a` | Sprint 4 obligation |
|---|---|---|
| Checkout, scheduling, refunds on dev | Merged and tested locally; off on dev (#223); US14 local evidence only (#224) | Turn on and prove on dev (US13, US14) |
| Demo data on dev | Four `US34-*` startable listings and an admin grant (#222 Phase A) | Paid START and JOIN on dev (Phase B) after #223 |
| Signed-in E2E on dev | Waits for #223 and #222 (#229) | Run on dev |
| Production | Not applied; blocked on NFR-37 (#226, #227) | Decide NFR-37, then apply, release and drill |
| Admission refactor | Draft PR #424 for #421–#423, CI green | Merge |
| Share attribution | Credits written, never read | Read API and `/account` section (#452) |
| Performance | Local 100-VU baseline only (`docs/perf/sprint3-baseline.md`) | Deployed measurement and tuning (#453) |
| Notifications | Placeholder domain (#215); ADR 0007 drafted on this branch (#443) | Outbox, relay, consumer, read API and bell (#450 epic) |
| SWE5001 | Phase A green: monolith as one ECS service, promotion and up/down workflows (#376) | Phase B: three service assemblies, remote identity, messaging, scaling, observability, drills |
| Admin console design gaps | D1–D8 open (#345–#352) | Triage; implement only what fits, defer the rest as documented gaps |
| Identity outside its service | `UserAppService` has 14 `requireActive` call sites and one `publicNickname` caller outside identity; `StpInterfaceImpl` reads roles from the identity database; `PaymentAttentionMapper.xml` joins `sys_user`; two commerce controllers import identity's `RoleCode` | Session roles and internal token (#467), remote identity check (#468), attention query rewrite (#469), guardrails (#455) |
| Sprint 3 records | Review drafted at freeze; no retro, no progress report; tag `v0.1.0-sprint3` pushed on 2026-10-11 | Close #232 through #451 |

## 4. Scope decisions for Sprint 4

1. **No new SWE5006 features beyond notifications.** Sprint 4 is closure and demo readiness, plus the notification use case.
2. **Notifications serve both courses.** The notification use case (#215, #444–#446, #449) is the SWE5006 feature and the SWE5001 event-driven core: the consumer runs inside the monolith on EC2 dev with ElasticMQ, and inside `notification-svc` on ECS with SNS (ADR 0007).
3. **The SWE5001 distributed shape is the proposal's three services: `identity-svc`, `commerce-svc` and `notification-svc`.** Amended 2026-10-11: the first draft deferred `identity-svc`; three services are a hard requirement, so the deferral was reversed the same day. The identity work adds about 45 h (#467, #468, #469 and the identity parts of #455, #456, #459, #460), recorded in ADR 0008 under #455.
4. **One k6 code base for both courses.** The scripts in #458 also measure the deployed SWE5006 journey (#453); the EC2-versus-ECS comparison feeds both reports.
5. **NFR-37 (origin TLS) becomes an accepted risk with an end date** unless a hostname appears by 2026-10-12, so #226 is unblocked (#227).
6. **Admin console design gaps D1–D8 are deferred** unless one costs under an hour; the remainder is listed as a known gap in the report (#353).
7. **Funding the identity work.** #453 (US16) and #454 (US17) move to the hardening window after the sprint (2026-10-26 to 2026-11-02); #382 (BA-13) moves to the backlog; #371 moves to @limjiechao. Capacity rises from 225 h to 256 h.
8. **Out of scope:** keyset paging, scheduled share-credit reconciliation (NFR-39), live Stripe keys, email or push notifications, FIFO ordering.

## 5. Sprint 4 backlog

Capacity is 256 h, about 50 h per member across both courses (45 h in the first draft, raised by the identity amendment in §4). Issue estimates in ideal days are converted at 6 h per day. **The planned owner is the person who writes the code.** If that changes, the board card and this table change the same day. Course: 5006, 5001, or both.

| ID | Story or task | Course | Owner | Reviewer | h | Issue | Due |
|---|---|---|---|---|---|---|---|
| US13 | Checkout, scheduling and expiry refunds on dev *(carry-over)* | 5006 | @Yorick-Yu | @l0sgAi | 7 | #223 | 10-14 |
| US14 | Refund idempotency proven on dev *(carry-over)* | 5006 | @Yorick-Yu | @PPsyrius | 4 | #224 | 10-16 |
| US34 | Paid START and JOIN on dev (Phase B) *(carry-over)* | 5006 | @l0sgAi | @Yorick-Yu | 2 | #222 | 10-15 |
| US32 | Signed-in buyer journey on dev *(carry-over)* | 5006 | @limjiechao | @EricWang1358 | 4 | #229 | 10-16 |
| US31 | First production release and rollback drill *(carry-over)* | 5006 | @PPsyrius | @l0sgAi | 14 | #226 | 10-20 |
| US28b | NFR-37 decision and NFR-33/35 closure *(carry-over)* | 5006 | @PPsyrius | @l0sgAi | 1 | #227 | 10-12 |
| RF | Admission reuse and receipt identity refactor | 5006 | @EricWang1358 | @Yorick-Yu | 2 | #421–#423, PR #424 | 10-13 |
| AD | Admin policy-form window rule | 5006 | @limjiechao | @l0sgAi | 2 | #371 | 10-16 |
| ADG | Admin console design gaps triage | 5006 | @limjiechao | @l0sgAi | 2 | #353, #345–#352 | 10-15 |
| US15c | Sharer sees invite credits on `/account` | 5006 | @l0sgAi (API), @limjiechao (UI) | each other | 4 + 4 | #452 | 10-20 |
| DEC | Backend ADR 0007: outbox, SNS fan-out, SQS + DLQ | both | @l0sgAi | @Yorick-Yu | 3 | #443 | 10-12 |
| US38 | Notification domain, persistence and read API | both | @PPsyrius | @EricWang1358 | 15 | #215 | contract 10-13, merge 10-14 |
| US39 | Transactional outbox and versioned event contracts | both | @Yorick-Yu | @l0sgAi | 12 | #444 | 10-14 |
| US40 | Outbox relay, SQS/SNS transports, ElasticMQ for local, CI and dev | both | @Yorick-Yu | @PPsyrius | 13 | #445 | 10-16 |
| US41 | Idempotent SQS consumer creating notifications | both | @EricWang1358 | @Yorick-Yu | 15 | #446 | 10-16 |
| US42 | Header bell, unread badge and notification list | 5006 | @limjiechao | @PPsyrius | 10 | #449 | 10-16 |
| NFR | Effectively-once delivery, poison isolation, backlog scaling evidence | both | @l0sgAi | @EricWang1358 | 6 | #448 | 10-20 |
| P1 | Messaging module: SNS topic, notification and audit queues, DLQ, alarms | 5001 | @PPsyrius | @l0sgAi | 8 | #447 | 10-15 |
| P2 | `identity-svc`, `commerce-svc` and `notification-svc` assemblies, ADR 0008, grants, guardrails | 5001 | @l0sgAi | @EricWang1358 | 21 | #455 | 10-14 |
| P3 | Distributed topology on ECS (three services) | 5001 | @l0sgAi | @PPsyrius | 11 | #456 | 10-16 |
| P10 | Session roles outside identity and the internal-call token (WP-B4, WP-B3) | 5001 | @PPsyrius | @l0sgAi | 9 | #467 | 10-15 |
| P11 | Remote identity check from commerce (WP-B5) | 5001 | @EricWang1358 | @Yorick-Yu | 15 | #468 | 10-16 |
| P12 | Admin attention nicknames through the identity port, no `sys_user` join | both | @Yorick-Yu | @PPsyrius | 6 | #469 | 10-15 |
| P4 | HTTP target-tracking scaling, dashboard and alarms | 5001 | @EricWang1358 | @PPsyrius | 9 | #457 | 10-16 |
| P5 | k6 scripts, load seed, V2 baseline, V3/V4 runs | 5001 | @EricWang1358 | @Yorick-Yu | 7 | #458 | V2 10-15; V3/V4 10-19 |
| P6 | Drills V5–V7, identity outage drill and rebuild timings V8 | 5001 | @Yorick-Yu | @PPsyrius | 8 | #459 | 10-20 |
| P7 | Smoke suite, cross-service sign-in and buyer E2E on ECS | 5001 | @limjiechao | @Yorick-Yu | 9 | #460 | 10-19 |
| P8 | Presentation slides, demo script, rehearsal, fallback recording (lead) | 5001 | @limjiechao | @l0sgAi | 13 | #461 | 10-21 |
| P9 | SWE5001 progress report 2 | 5001 | @l0sgAi | @limjiechao | 2 | #462 | 10-18 |
| S3C | Sprint 3 review, retrospective, progress report, tag | 5006 | @limjiechao | @l0sgAi | 4 | #451 | 10-13 |
| SM | Scrum Master duties (proposed; confirm at planning) | 5006 | @EricWang1358 | — | 4 | #465 | daily |
| EV | Evidence (2 h × 5) | both | all | — | 10 | — | daily |
| | **Total** | | | | **256** | | |

Per member:

- @l0sgAi (Liu Jin): 2 (US34) + 4 (US15c) + 3 (DEC) + 6 (NFR) + 21 (P2) + 11 (P3) + 2 (P9) + 2 (EV) = 51.
- @Yorick-Yu (Yu Qianshuo): 7 (US13) + 4 (US14) + 12 (US39) + 13 (US40) + 8 (P6) + 6 (P12) + 2 (EV) = 52.
- @EricWang1358 (Wang Ziang): 2 (RF) + 15 (US41) + 9 (P4) + 7 (P5) + 15 (P11) + 4 (SM) + 2 (EV) = 54. The highest load; Planning may move SM to another member.
- @PPsyrius (Panpakorn Siripanich): 14 (US31) + 1 (US28b) + 15 (US38) + 8 (P1) + 9 (P10) + 2 (EV) = 49.
- @limjiechao (Lim Jiechao): 4 (US32) + 2 (AD) + 2 (ADG) + 4 (US15c) + 10 (US42) + 9 (P7) + 13 (P8) + 4 (S3C) + 2 (EV) = 50.

Moved out of the sprint by the identity amendment: #453 US16 (6 h, @EricWang1358) and #454 US17 (5 h, @PPsyrius) to the hardening window; #382 BA-13 (3 h, @l0sgAi) to the backlog.

SWE5001 Phase A (#376) was carried mainly by @l0sgAi and @PPsyrius; Phase B moves the larger share to the other three members so that the contribution record stays balanced for peer assessment.

Acceptance criteria live in each issue; the sprint-level conditions are in §8.

## 6. Dependencies and integration order

```
#443 ADR ──┬─ #444 outbox ── #445 relay + ElasticMQ ──┐
           ├─ #215 notification API ──┬─ #449 bell     ├─ #446 consumer ── #448 evidence
           │                          └────────────────┘
           └─ #447 messaging infra ──────────────┐
#455 assemblies (3 services) ────────────────────┤
#467 session roles + internal token ─────────────┼─ #456 distributed runtime ── #457 scaling + dashboard
#468 remote identity check ──────────────────────┤
#469 attention query without sys_user ───────────┘
#458 V2 baseline (monolith) ── #459 drills (incl. identity outage) ── #461 presentation
#223 dev rollout ── #222 Phase B ── #229 signed-in E2E ── #460 E2E on ECS
```

1. **Day 1 (10-12):** ADR 0007 accepted; NFR-37 decided; #215 contract entry and #444 envelope shape agreed, so #446 and #449 can start against them on 10-13.
2. **#223 is the SWE5006 critical path.** Without it, #222 Phase B, #229 and the paid demo have no data on dev. Target 10-14.
3. **#446 does not wait for #445's relay:** it is built against Testcontainers ElasticMQ by sending envelopes directly; it waits only for #215 persistence.
4. **#455 by 10-14, then #456; #447 before #456.** The distributed runtime is created only once both are merged.
5. **Identity work runs in parallel and has a go/no-go.** #467, #468 and #469 are built in the existing code base without waiting for #455: B4 runs in both topologies and B5 is tested with WireMock. At the 10-15 mid-sprint check, if #468's tests are not green, the identity runtime moves to 10-18 and joins the 10-19 promotion; if it is still not green on 10-18, the presentation runs two services and explains identity as a limitation with the evidence so far.
6. **V2 baseline on the monolith topology by 10-15**, so the V3 target is known before the code freeze.
7. **SWE5001 code freeze 10-16 EOD.** After it, only fixes found in the 10-19 session merge, and every change stays additive to the monolith (§8).
8. **10-19:** promotion, then one evidence session for V3–V7 and the identity outage drill, the E2E run (#460) and the fallback recording; `env-down.sh` and a clean `env-status.sh` after it.
9. **Every PR stays below about 1,500 changed lines** and is reviewed within one working day.

## 7. Burndown baseline

256 h over ten working days, 25.6 h per day ideal. **Each owner updates the board's estimate field with remaining hours at the end of every working day.** Sprint 3 left the field empty and the review had to reconstruct it; this is the retrospective action the mid-sprint check verifies.

| Date | Ideal remaining h | Planned focus |
|---|---|---|
| Start (10-12) | 256 | Planning, ADR 0007, NFR-37, contract entries |
| 10-12 | 230.4 | #443 accepted; #227; #424 merged; #444, #215, #467, #468 started |
| 10-13 | 204.8 | Sprint 3 closure; #215 contract; #455 packaging |
| 10-14 | 179.2 | #223 on dev; #444, #215 and #455 merged; #446 and #449 in progress |
| 10-15 | 153.6 | Mid-sprint check and identity go/no-go; #222 Phase B; #447, #467, #469 merged; V2 baseline |
| 10-16 | 128 | SWE5001 freeze: #445, #446, #449, #456, #457, #468 merged |
| 10-19 | 102.4 | Promotion; evidence session V3–V7; fallback recording |
| 10-20 | 76.8 | Rehearsal; #226 drill; #452 merged |
| 10-21 | 51.2 | SWE5001 presentation; #448 evidence complete |
| 10-22 | 25.6 | SWE5006 freeze |
| 10-23 | 0 | Review, retro, progress report, tag |

## 8. Definition of Done

The Sprint 3 Definition of Done carries forward, with these additions for the two-course sprint:

- **New:** SWE5001 changes are additive. `topology=monolith` stays the default, the SWE5006 required checks stay required, and the EC2 deploy is confirmed healthy after any merge that touches the bootstrap jar, Dockerfiles or Compose files.
- **New:** "on ECS" evidence is captured in a recorded session (workflow run link, k6 summary, dashboard screenshot) and linked from the day log; an ElasticMQ test does not prove the SNS hop.
- **New:** every ECS session ends with `env-down.sh` and a clean `env-status.sh`, recorded in the day log.
- Where the story says "on dev", it is demonstrated on the deployed dev environment, with the evidence in the day log.
- The endpoint is in `contracts/openapi.json` before its controller PR is opened.
- Remaining hours are in the board estimate field on the day the card moves.
- Board, day log, NFR register (where applicable) and contribution record are updated before completion is counted.

## 9. Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| The messaging chain (#444 → #445 → #446) slips past 10-16 | No event-driven demo on 10-21; SWE5006 notifications late | #446 starts on 10-13 against ElasticMQ directly; mid-sprint check on 10-15 decides whether the audit-queue fan-out is cut first | @l0sgAi |
| #223 slips past 10-14 | SWE5006 paid demo only on a local stack | Highest priority for its owner; reviewer booked; backlog counts recorded before switching | @Yorick-Yu |
| ECS session fails on presentation day | No live demo | `env-up.sh` before noon; fallback recording from 10-19; rehearsal on 10-20 | @limjiechao |
| WP-B2 packaging breaks the SWE5006 image build | EC2 dev deploy red | Separate PR for the `exec` classifier and Dockerfile changes, merged with the deploy watched | @l0sgAi |
| Two courses compete for the same ten days | Burnout, cliff-shaped burndown | About 50 h per member; SWE5006 adds no feature beyond notifications; US16, US17 and BA-13 moved out; the D1–D8 design gaps are deferred | SM |
| The remote identity check sits inside the Stripe webhook transaction | A wrong error mapping marks a valid buyer `BUYER_INACTIVE` and answers Stripe 2xx: a silent payment incident | Only 404/409 map to identity business errors; timeouts and 5xx roll back with 500 so Stripe redelivers; #468 cannot merge without the webhook redelivery test | @EricWang1358 |
| Session roles (#467) change the login path shared with SWE5006 | Admin or buyer sign-in broken on EC2 dev | Verify dev sign-in and one admin request right after merge; roles apply at next login, so the demo admin signs in again after a grant | @PPsyrius |
| The attention query rewrite (#469) touches the delivered admin payments page (BA-8) | Wrong or missing buyer names for admins | IT pinned to today's nickname and "Unknown buyer" cases before the change | @Yorick-Yu |
| Identity is not ready by 10-18 | Two services on ECS instead of three | Go/no-go on 10-15 (§6 item 5); fallback recorded and explained in the presentation | @l0sgAi |
| SWE5001 exam window overlaps the SWE5006 presentation | Rehearsal and study time collide | Report drafts by 10-28 and the integrated draft by 10-31, so the exam week carries only the rehearsal and the presentation | all |
| Planned owners drift from actual work | Unfair contribution record, peer-assessment disputes | Mid-sprint check on 10-15; reassignment recorded on the card and in this file | SM |

## 10. After the sprint: reports and presentations

| Deliverable | Lead | Section owners | Due | Issue |
|---|---|---|---|---|
| SWE5001 presentation (40 min) | @limjiechao | Overview and conduct @limjiechao; logical architecture and DDD @EricWang1358; physical architecture @l0sgAi; detailed design @Yorick-Yu; DevOps @PPsyrius; demo @EricWang1358 and @Yorick-Yu | 10-21 | #461 |
| Hardening: deployed performance (US16) and security and quality closure (US17) | @EricWang1358, @PPsyrius | — | 11-02 | #453, #454 |
| SWE5006 final report | editor @limjiechao | see §11 | 11-10 | #463 |
| SWE5006 presentation | @Yorick-Yu | report section owners contribute their slides | 11-03/04 | #464 |
| SWE5001 final report | editor @l0sgAi | listed in #462 | 11-16 | #462 |

The SWE5001 report reuses the SWE5006 DevSecOps chapter and database schemas and the `docs/swe5001/impl-*` plans; drafting starts after the SWE5006 presentation.

## 11. Assignment by use case and design problem (SWE5006 report)

The report template requires one use case (analysis and design class and sequence diagrams) and one design problem (pattern, before and after) per member. Owners record them while building, not afterwards.

| Member | Use case | Design problem | Shared sections |
|---|---|---|---|
| Liu Jin | UC9 listing administration and group terms | State (product lifecycle) + Optimistic Offline Lock | 3.1 Software architecture; 3.6 Database schemas |
| Yu Qianshuo (Yorik) | UC6 refund on team expiry | Idempotency Key + Retry with Backoff + operator review | 3.2 Transition strategies (half) |
| Wang Ziang (Eric) | UC3/UC4 initiate and join a team | Factory Method / policy objects for admission (#421, #422) | 3.2 Transition strategies (half) |
| Panpakorn Siripanich (Pan) | Receive notifications | Observer via domain events + Transactional Outbox + Idempotent Receiver | 4 DevSecOps and development lifecycle |
| Lim Jiechao | UC8 view my orders and teams (with UC7 frontend) | A frontend design problem from `frontend/docs/adr/` | 1 Introduction; 2 Project conduct; 3.3 Use case model; 5 Contribution summary |
