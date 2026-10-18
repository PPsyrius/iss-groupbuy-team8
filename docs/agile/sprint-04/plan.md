# Sprint 4 plan (2026-10-12 to 2026-10-23)

Status: draft for Sprint Planning on 2026-10-12. Numbers, owners and dates change only at that meeting; after it, this file mirrors the Sprint 4 container issue #465. Amended three times on 2026-10-11 before Planning (§12), and twice after Planning, on 2026-10-13 and 2026-10-15 (§13).

Sources read on 2026-10-11: `origin/dev` at `414ee79a`, `docs/agile/sprint-03/plan.md` and `review.md`, open issues and PRs, `docs/swe5001/HANDOFF.md`, the SWE5001 proposal, refactoring plan and implementation plans, backend ADR 0007 (#443), the SWE5001 briefing and presentation guidelines, the SWE5006 project requirements, and both course report templates.

## 1. Calendar

Sprint 4 is shared by two courses on one trunk. SWE5006 is graded on the agile process, a complete runnable system and the report; SWE5001 on a scalable microservice platform. The `dev` environment (single EC2 host) serves SWE5006; the `prod` environment (Amazon ECS, released by `dev` → `prod` promotion) serves SWE5001.

| Item | Date |
|---|---|
| Sprint Planning; ADR 0007 accepted at stand-up; NFR-37 accepted risk (#227) | Mon 2026-10-12 |
| Sprint 3 review, retrospective and progress report (#451); tag `v0.1.0-sprint3` pushed at `414ee79a` on 2026-10-11 | by Tue 2026-10-13 |
| #455 service assemblies merged; US13 Checkout, scheduling and refunds on dev (#223) | Wed 2026-10-14 |
| Mid-sprint check (owner vs actual, rebalance); **identity go/no-go** (§6 item 5); priority-tier reductions if the split is behind (§6 item 6) | Thu 2026-10-15 |
| **SWE5001 code freeze** (everything the presentation needs merged into `dev`) | Fri 2026-10-16 EOD |
| Identity fallback deadline; SWE5001 progress report 2 (#462) | Sun 2026-10-18 |
| SWE5001 Sprint 4 promotion `dev` → `prod`; evidence session (V2–V5, V7, identity outage) and fallback recording | Mon 2026-10-19 |
| SWE5001 full rehearsal; promotion fallback day | Tue 2026-10-20 |
| **SWE5001 presentation** (Team 8; #393 notice, confirmed 2026-10-11) | Wed 2026-10-21, 16:15–16:55 SGT |
| SWE5006 code freeze | Thu 2026-10-22 EOD |
| Sprint 4 Review, Retrospective, progress report; tag `v0.1.0-sprint4`; SWE5006 use-case and design-problem drafts and slides draft (#463, #464) | Fri 2026-10-23 |
| SWE5006 presentation rehearsal | Sun 2026-10-25 |
| **SWE5006 presentation** (confirmed 2026-10-11; supersedes the 3–4 Nov window in the Sprint 3 calendar) | Mon 2026-10-26, 16:30–17:15 SGT |
| SWE5001 written examination (open book, offline) | Tue 2026-11-03, 18:30–22:00 |
| SWE5006 report: integrated draft with presentation remarks | Fri 2026-11-06 |
| SWE5006 report freeze; submission | Mon 2026-11-09; Tue 2026-11-10 |
| SWE5001 report: section drafts | Wed 2026-11-11 |
| SWE5001 final report submission (#462), final `terraform destroy`, peer ranking | Mon 2026-11-16 |

The SWE5001 promotion moves from the 2026-10-20 target in #376 to 2026-10-19, so the evidence session and the fallback recording run on promoted code and 2026-10-20 stays free for recovery and the rehearsal.

## 2. Sprint goal

By 2026-10-23, the **deployed dev environment** runs the complete buyer and admin system for SWE5006: a buyer can start or join a bloc on an admin-configured listing, pay the quoted price through Stripe Sandbox and be refunded automatically when the bloc expires. On **SWE5001 `prod` ECS**, the same code runs as three services, `identity-svc`, `commerce-svc` and `notification-svc`, with identity reached through a fail-closed internal HTTP call and commerce publishing team and payment events through a transactional outbox, SNS and SQS to the notification service; HTTP scaling, queue-driven scaling, self-healing, an identity outage and a DLQ drill are captured as evidence before the 2026-10-21 presentation. Every member's SWE5006 use case and design problem is drafted for the 2026-10-26 presentation.

## 3. Starting position

| Area | State on `dev` at `414ee79a` | Sprint 4 obligation |
|---|---|---|
| Checkout, scheduling, refunds on dev | Merged and tested locally; off on dev (#223); US14 local evidence only (#224) | Turn on and prove on dev (US13); record US14 from local evidence |
| Demo data on dev | Four `US34-*` startable listings and an admin grant (#222 Phase A) | Paid START and JOIN on dev (Phase B) after #223 |
| EC2 production | Not applied (#226) | None: closed as not planned (§4 item 2) |
| Admission refactor | Draft PR #424 for #421–#423, CI green | Merge (design-problem evidence) |
| Notifications | Placeholder domain (#215); ADR 0007 drafted on this branch (#443) | Outbox, relay, consumer and one list endpoint for SWE5001 (#450 epic) |
| SWE5001 | Phase A green: monolith as one ECS service, promotion and up/down workflows (#376) | Phase B: three service assemblies, remote identity, messaging, scaling, drills |
| Identity outside its service | `UserAppService` has 14 `requireActive` call sites and one `publicNickname` caller outside identity; `StpInterfaceImpl` reads roles from the identity database; `PaymentAttentionMapper.xml` joins `sys_user`; two commerce controllers import identity's `RoleCode` | Session roles (#467), remote identity check (#468), attention query rewrite (#469), guardrails (#455) |
| SWE5006 report | Template known; no drafts | Use-case and design-problem drafts and agile artefacts by 10-23 (#463) |
| Sprint 3 records | Review drafted at freeze; no retro, no progress report; tag `v0.1.0-sprint3` pushed on 2026-10-11 | Close #232 through #451 |

## 4. Scope decisions for Sprint 4

0. **The microservice split comes before the notification system.** The three-service deployment on `prod` ECS (#455, #456, #467, #468, #469) is finished first; notification work (#215, #443–#448) takes the remaining capacity. Reviews, help and any rebalancing go to the split first, and when time runs short notification scope is cut first, in the order in §6 item 6.
1. **Grade-driven scope.** Work that the SWE5006 or SWE5001 grading criteria do not reward is not built. SWE5006 rewards the agile process and artefacts, a complete runnable system, analysis and design per use case, design problems solved with patterns, unit tests for complex classes and the DevSecOps pipeline design (report 80%, presentation 20%). SWE5001 rewards a scalable microservice platform: platform thinking and reusable services, demonstrated scalability, cloud-native services, automation and minimal security (presentation 20%, report 30%, examination 50%).
2. **Environments by course.** `dev` (single EC2 host, Compose, monolith) is the SWE5006 system; `prod` (ECS, three services) is the SWE5001 system and also over-delivers for SWE5006. No EC2 production is built: #226 is closed as not planned.
3. **The SWE5001 distributed shape is the proposal's three services: `identity-svc`, `commerce-svc` and `notification-svc`.** A first draft deferred `identity-svc`; three services are a hard requirement, so the deferral was reversed the same day. Recorded in ADR 0008 under #455.
4. **Notifications serve SWE5001 only.** The outbox, relay and consumer write `biz_notification` rows that one authenticated list endpoint exposes (#215). No header bell (#449 closed); the EC2 `dev` stack runs no broker and keeps the relay and consumer off; ElasticMQ is for local development and CI tests. ADR 0007 amended accordingly.
5. **One k6 code base, for SWE5001.** #458 measures V2–V4 on ECS; SWE5006 cites the Sprint 3 local baseline (#453 closed).
6. **NFR-37 (origin TLS) is an accepted risk** for `dev` with end date 2026-11-16, recorded in the #454 register sweep (#227).
7. **Funding the identity work.** First funded by moving #453, #454 and #382 out of the sprint; superseded by item 8.
8. **Grade-driven cuts (2026-10-11).** Closed as not planned: #226 EC2 production release, #229 signed-in E2E on dev, #449 notification bell, #452 sharer credits, #453 deployed performance, #382 and #371 admin polish, #346–#353 admin design gaps. Rescoped: #224 to a record from local evidence (1 h), #215 to persistence and one list endpoint (8 h), #445 and #446 without EC2 (10 h, 12 h), #448 (4 h), #454 to a register sweep (1 h), #457 to three dashboard rows and min 2 commerce tasks (5 h, now @PPsyrius), #458 without the bulk seed (5 h), #459 without the V6 broken-image drill (4 h), #460 to smoke and manual checks (3 h), #467 without the internal token (6 h). The freed time goes to the SWE5006 report drafts and agile artefacts inside the sprint (#463, #464). Every cut item is listed as out of scope or a known limitation in the reports.
9. **Out of scope:** keyset paging, scheduled share-credit reconciliation (NFR-39), live Stripe keys, email or push notifications, FIFO ordering, RDS Multi-AZ and Valkey replicas (reported as cost-driven availability limitations), an internal-call token (network isolation only).

## 5. Sprint 4 backlog

Capacity is 224.5 h across both courses; issue estimates in ideal days are converted at 6 h per day. **The planned owner is the person who writes the code.** If that changes, the board card and this table change the same day. Course: 5006, 5001, or both.

| ID | Story or task | Course | Owner | Reviewer | h | Issue | Due |
|---|---|---|---|---|---|---|---|
| US13 | Checkout, scheduling and expiry refunds on dev *(carry-over)* | 5006 | @Yorick-Yu | @l0sgAi | 7 | #223 | 10-14 |
| US14 | Refund idempotency acceptance record from local evidence | 5006 | @Yorick-Yu | @PPsyrius | 1 | #224 | 10-16 |
| US34 | Paid START and JOIN on dev (Phase B) *(carry-over)* | 5006 | @l0sgAi | @Yorick-Yu | 2 | #222 | 10-15 |
| US28b | NFR-37 accepted risk | 5006 | @PPsyrius | @l0sgAi | 0.5 | #227 | 10-12 |
| US17 | NFR register sweep for the report | both | @PPsyrius | @EricWang1358 | 1 | #454 | 10-22 |
| RF | Admission reuse and receipt identity refactor | 5006 | @EricWang1358 | @Yorick-Yu | 2 | #421–#423, PR #424 | 10-13 |
| DEC | Backend ADR 0007: outbox, SNS fan-out, SQS + DLQ | 5001 | @l0sgAi | @Yorick-Yu | 3 | #443 | 10-12 |
| US38 | Notification domain, persistence and one list endpoint | 5001 | @PPsyrius | @EricWang1358 | 8 | #215 | contract 10-13, merge 10-14 |
| US39 | Transactional outbox and versioned event contracts | 5001 | @Yorick-Yu | @l0sgAi | 12 | #444 | 10-14 |
| US40 | Outbox relay, SQS/SNS transports, ElasticMQ for local and CI | 5001 | @Yorick-Yu | @PPsyrius | 10 | #445 | 10-16 |
| US41 | Idempotent SQS consumer creating notifications | 5001 | @EricWang1358 | @Yorick-Yu | 12 | #446 | 10-16 |
| NFR | Effectively-once delivery, poison isolation, backlog scaling evidence | 5001 | @l0sgAi | @EricWang1358 | 4 | #448 | 10-20 |
| P1 | Messaging module: SNS topic, notification and audit queues, DLQ, alarm | 5001 | @PPsyrius | @l0sgAi | 8 | #447 | 10-15 |
| P2 | `identity-svc`, `commerce-svc` and `notification-svc` assemblies, ADR 0008, grants, guardrails | 5001 | @l0sgAi | @EricWang1358 | 21 | #455 | 10-14 |
| P3 | Distributed topology on ECS (three services) | 5001 | @l0sgAi | @PPsyrius | 11 | #456 | 10-16 |
| P4 | HTTP target-tracking scaling, three-row dashboard, min 2 commerce tasks | 5001 | @PPsyrius | @EricWang1358 | 5 | #457 | 10-16 |
| P5 | k6 scripts, V4 seed, V2 baseline, V3/V4 runs | 5001 | @PPsyrius (from @EricWang1358, 10-15) | @Yorick-Yu | 5 | #458 | V2 10-15; V3/V4 10-19 |
| P6 | Drills V5, V7, identity outage; V8 timings from existing runs | 5001 | @Yorick-Yu | @PPsyrius | 4 | #459 | 10-20 |
| P7 | Smoke suite and manual sign-in and notification checks on ECS | 5001 | @limjiechao | @Yorick-Yu | 3 | #460 | 10-19 |
| P8 | SWE5001 presentation slides, demo script, rehearsal, fallback recording (lead) | 5001 | @limjiechao | @l0sgAi | 13 | #461 | 10-21 |
| P9 | SWE5001 progress report 2 | 5001 | @l0sgAi | @limjiechao | 2 | #462 | 10-18 |
| P10 | Session roles outside identity (WP-B4) | 5001 | @PPsyrius | @l0sgAi | 6 | #467 | 10-15 |
| P11 | Remote identity check from commerce (WP-B5) | 5001 | @l0sgAi (from @EricWang1358, 10-13) | @Yorick-Yu | 15 | #468 | 10-16 |
| P12 | Admin attention nicknames through the identity port, no `sys_user` join | both | @Yorick-Yu | @PPsyrius | 6 | #469 | 10-15 |
| S3C | Sprint 3 review, retrospective, progress report | 5006 | @limjiechao | @l0sgAi | 4 | #451 | 10-13 |
| RPT | SWE5006 report drafts: one use case and one design problem each (6 h × 5), agile artefacts (@PPsyrius 5 h), chapters 1, 2, 3.3 (@limjiechao 6 h) | 5006 | all, editor @limjiechao | — | 41 | #463 | 10-23 |
| PRS | SWE5006 slide master | 5006 | @limjiechao | @Yorick-Yu | 4 | #464 | 10-23 |
| SM | Scrum Master duties (proposed; confirm at planning) | 5006 | @EricWang1358 | — | 4 | #465 | daily |
| EV | Evidence (2 h × 5) | both | all | — | 10 | — | daily |
| | **Total** | | | | **224.5** | | |

Per member:

- @l0sgAi (Liu Jin): 2 (US34) + 3 (DEC) + 4 (NFR) + 21 (P2) + 11 (P3) + 2 (P9) + 15 (P11, from @EricWang1358) + 6 (RPT) + 2 (EV) = 66 (planned 51). The highest load; Planning may move #448 to another member.
- @Yorick-Yu (Yu Qianshuo): 7 (US13) + 1 (US14) + 12 (US39) + 10 (US40) + 4 (P6) + 6 (P12) + 6 (RPT) + 2 (EV) = 48.
- @EricWang1358 (Wang Ziang): 2 (RF) + 12 (US41) + 4 (SM) + 6 (RPT) + 2 (EV) = 26 (planned 46; P11 and P5 moved away).
- @PPsyrius (Panpakorn Siripanich): 0.5 (US28b) + 1 (US17) + 8 (US38) + 8 (P1) + 5 (P4) + 5 (P5, from @EricWang1358) + 6 (P10) + 11 (RPT: 6 drafts + 5 artefacts) + 2 (EV) = 46.5 (planned 41.5).
- @limjiechao (Lim Jiechao): 3 (P7) + 13 (P8) + 4 (S3C) + 12 (RPT: 6 drafts + 6 chapters) + 4 (PRS) + 2 (EV) = 38.

SWE5001 Phase A (#376) was carried mainly by @l0sgAi and @PPsyrius; Phase B moves a large share to the other three members so that the contribution record stays balanced for peer assessment. The per-member lines above show the hours after the two amendments in §13; the total stays 224.5 h, and the planned figures are in §13.

Acceptance criteria live in each issue; the sprint-level conditions are in §8.

## 6. Dependencies and integration order

```
#443 ADR ──┬─ #444 outbox ── #445 relay ──────────┐
           ├─ #215 persistence + list endpoint ───┼─ #446 consumer ── #448 evidence
           └─ #447 messaging infra ───────────────┤
#455 assemblies (3 services) ─────────────────────┤
#467 session roles ───────────────────────────────┼─ #456 distributed runtime ── #457 scaling + dashboard
#468 remote identity check ───────────────────────┤
#469 attention query without sys_user ────────────┘
#458 V2 baseline (monolith) ── #459 drills (V5, V7, identity outage) ── #461 presentation
#223 dev rollout ── #222 Phase B ── SWE5006 demo on dev (#464)
```

1. **Day 1 (10-12):** ADR 0007 accepted; NFR-37 accepted risk; #215 contract entry and #444 envelope shape agreed, so #446 can start against them on 10-13.
2. **#223 is the SWE5006 critical path.** Without it, #222 Phase B and the paid demo have no data on dev. Target 10-14.
3. **#446 does not wait for #445's relay:** it is built against Testcontainers ElasticMQ by sending envelopes directly; it waits only for #215 persistence.
4. **#455 by 10-14, then #456; #447 before #456.** The distributed runtime is created only once both are merged.
5. **Identity work runs in parallel and has a go/no-go.** #467, #468 and #469 are built in the existing code base without waiting for #455: B4 runs in both topologies and B5 is tested with WireMock. At the 10-15 mid-sprint check, if #468's tests are not green, the identity runtime moves to 10-18 and joins the 10-19 promotion; if it is still not green on 10-18, the presentation runs two services and explains identity as a limitation with the evidence so far.
6. **Priority tiers when work collides (§4 item 0).** Tier 1, never traded away before 10-21: the split (#455, #456, #467, #468, #469), scaling (#457), the V2/V3 runs (#458), and the V5 and identity outage drills (#459). Tier 2, the event-driven core needed by V4, V7 and the fan-out demo: #443, #444, #445, #446, #215, #447. If the 10-15 check finds tier 1 behind, the reductions are, in order: (a) drop the audit-queue fan-out from #447; (b) drop the #215 list endpoint and show notification rows from the database; (c) as a last resort, stop tier 2 and present events, V4 and V7 as design only.
7. **V2 baseline on the monolith topology by 10-15**, so the V3 target is known before the code freeze.
8. **SWE5001 code freeze 10-16 EOD.** After it, only fixes found in the 10-19 session merge, and every change stays additive to the monolith (§8).
9. **10-19:** promotion, then one evidence session for V3–V5, V7 and the identity outage, the smoke run (#460) and the fallback recording; `env-down.sh` and a clean `env-status.sh` after it.
10. **Every PR stays below about 1,500 changed lines** and is reviewed within one working day.

## 7. Burndown baseline

224.5 h over ten working days, 22.45 h per day ideal. **Each owner updates the board's estimate field with remaining hours at the end of every working day.** Sprint 3 left the field empty and the review had to reconstruct it; this is the retrospective action the mid-sprint check verifies.

| Date | Ideal remaining h | Planned focus |
|---|---|---|
| Start (10-12) | 224.5 | Planning, ADR 0007, NFR-37, contract entries |
| 10-12 | 202.1 | #443 accepted; #227; #444, #215, #467, #468 started |
| 10-13 | 179.6 | Sprint 3 closure; #215 contract; #424 merged; #455 packaging |
| 10-14 | 157.2 | #223 on dev; #444, #215 and #455 merged |
| 10-15 | 134.7 | Mid-sprint check and identity go/no-go; #222 Phase B; #447, #467, #469 merged; V2 baseline |
| 10-16 | 112.3 | SWE5001 freeze: #445, #446, #456, #457, #468 merged |
| 10-19 | 89.8 | Promotion; evidence session; fallback recording |
| 10-20 | 67.4 | SWE5001 rehearsal; SWE5006 use-case and design-problem drafts in progress |
| 10-21 | 44.9 | SWE5001 presentation; #448 evidence complete |
| 10-22 | 22.5 | SWE5006 freeze; #454 register sweep; agile artefacts compiled |
| 10-23 | 0 | Review, retro, progress report, tag; SWE5006 drafts and slides draft |

## 8. Definition of Done

The Sprint 3 Definition of Done carries forward, with these additions for the two-course sprint:

- **New:** SWE5001 changes are additive. `topology=monolith` stays the default, the SWE5006 required checks stay required, and the EC2 deploy is confirmed healthy after any merge that touches the bootstrap jar, Dockerfiles or Compose files.
- **New:** "on ECS" evidence is captured in a recorded session (workflow run link, k6 summary, dashboard screenshot) and linked from the day log; an ElasticMQ test does not prove the SNS hop.
- **New:** every ECS session ends with `env-down.sh` and a clean `env-status.sh`, recorded in the day log.
- **New:** a design choice made while building is drafted into the owner's #463 use-case or design-problem section the same week.
- Where the story says "on dev", it is demonstrated on the deployed dev environment, with the evidence in the day log.
- The endpoint is in `contracts/openapi.json` before its controller PR is opened.
- Remaining hours are in the board estimate field on the day the card moves.
- Board, day log, NFR register (where applicable) and contribution record are updated before completion is counted.

## 9. Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| The messaging chain (#444 → #445 → #446) slips past 10-16 | No event-driven demo on 10-21 | #446 starts on 10-13 against ElasticMQ directly; the split keeps priority (§4 item 0), so the 10-15 check applies the reductions in §6 item 6 | @l0sgAi |
| #223 slips past 10-14 | SWE5006 paid demo only on a local stack | Highest priority for its owner; reviewer booked; backlog counts recorded before switching | @Yorick-Yu |
| ECS session fails on presentation day | No live demo | `env-up.sh` before noon; fallback recording from 10-19; rehearsal on 10-20 | @limjiechao |
| WP-B2 packaging breaks the SWE5006 image build | EC2 dev deploy red | Separate PR for the `exec` classifier and Dockerfile changes, merged with the deploy watched | @l0sgAi |
| Two presentations in six days (10-21 and 10-26) | Report drafts and slides squeezed | Grade-driven cuts (§4 item 8) moved 41 h of SWE5006 report work into the sprint; drafts due 10-23 | SM |
| SWE5001 examination (50%) on 11-03 | Study time collides with report work | SWE5006 integrated draft moved after the exam (11-06); SWE5001 report starts 11-04 | all |
| The remote identity check sits inside the Stripe webhook transaction | A wrong error mapping marks a valid buyer `BUYER_INACTIVE` and answers Stripe 2xx: a silent payment incident | Only 404/409 map to identity business errors; timeouts and 5xx roll back with 500 so Stripe redelivers; #468 cannot merge without the webhook redelivery test | @EricWang1358 |
| Session roles (#467) change the login path shared with SWE5006 | Admin or buyer sign-in broken on EC2 dev | Verify dev sign-in and one admin request right after merge; roles apply at next login, so the demo admin signs in again after a grant | @PPsyrius |
| The attention query rewrite (#469) touches the delivered admin payments page (BA-8) | Wrong or missing buyer names for admins | IT pinned to today's nickname and "Unknown buyer" cases before the change | @Yorick-Yu |
| Identity is not ready by 10-18 | Two services on ECS instead of three | Go/no-go on 10-15 (§6 item 5); fallback recorded and explained in the presentation | @l0sgAi |
| Planned owners drift from actual work | Unfair contribution record, peer-assessment disputes | Mid-sprint check on 10-15; reassignment recorded on the card and in this file | SM |

## 10. Presentations and reports

| Deliverable | Lead | Section owners | Due | Issue |
|---|---|---|---|---|
| SWE5001 presentation (40 min) | @limjiechao | Overview and conduct @limjiechao; logical architecture and DDD @EricWang1358; physical architecture @l0sgAi; detailed design @Yorick-Yu; DevOps @PPsyrius; demo @EricWang1358 and @Yorick-Yu | 10-21 | #461 |
| SWE5006 presentation | @Yorick-Yu, slide master @limjiechao | use-case and design-problem owners contribute their slides | 10-26 | #464 |
| SWE5006 final report | editor @limjiechao | see §11; drafts 10-23, integrated 11-06 | 11-10 | #463 |
| SWE5001 final report | editor @l0sgAi | listed in #462; section drafts 11-11 | 11-16 | #462 |

The SWE5001 report reuses the SWE5006 DevSecOps chapter and database schemas and the `docs/swe5001/impl-*` plans; drafting starts after the 11-03 examination.

## 11. Assignment by use case and design problem (SWE5006 report)

The report template requires one use case (analysis and design class and sequence diagrams) and one design problem (pattern, before and after) per member. Drafts are due 2026-10-23 so the 10-26 presentation can use them.

| Member | Use case | Design problem | Shared sections |
|---|---|---|---|
| Liu Jin | UC9 listing administration and group terms | State (product lifecycle) + Optimistic Offline Lock | 3.1 Software architecture; 3.6 Database schemas |
| Yu Qianshuo (Yorik) | UC6 refund on team expiry | Idempotency Key + Retry with Backoff + operator review | 3.2 Transition strategies (half) |
| Wang Ziang (Eric) | UC3/UC4 initiate and join a team | Factory Method / policy objects for admission (#421, #422) | 3.2 Transition strategies (half) |
| Panpakorn Siripanich (Pan) | Receive notifications | Observer via domain events + Transactional Outbox + Idempotent Receiver | 4 DevSecOps and development lifecycle; agile artefacts |
| Lim Jiechao | UC8 view my orders and teams (with UC7 frontend) | A frontend design problem from `frontend/docs/adr/` | 1 Introduction; 2 Project conduct; 3.3 Use case model; 5 Contribution summary |

## 12. Amendments before Planning (2026-10-11)

1. First draft: 225 h, `commerce-svc` + `notification-svc`, `identity-svc` deferred.
2. `identity-svc` reinstated as a hard requirement (#467, #468, #469; §4 item 3): 256 h.
3. The microservice split ranked above notifications (§4 item 0, §6 item 6).
4. Grade-driven cuts, environment split by course, SWE5006 presentation on 2026-10-26 and report drafts moved into the sprint (§4 items 1, 2, 4, 8): 224.5 h.

## 13. Amendments after Planning

Two owner changes were made after Sprint Planning on 2026-10-12, both recorded on the issue first. The §5 owner cells and per-member lines were updated on 2026-10-18 to match. Scope, due dates and reviewers are unchanged by this section unless the table says otherwise. The total stays 224.5 h.

| Date | Item | Change | Hours effect | Source |
|---|---|---|---|---|
| 2026-10-13 | P11 remote identity check (#468) | Owner @EricWang1358 to @l0sgAi, to rebalance Sprint 4 load; scope, dates and reviewer (@Yorick-Yu) unchanged. Delivered by PR #479 (`afec4e83`, merged 2026-10-13); issue closed 2026-10-14. | @EricWang1358 -15 h, @l0sgAi +15 h | [#468 comment](https://github.com/PPsyrius/iss-groupbuy-team8/issues/468#issuecomment-6137411205); [#479](https://github.com/PPsyrius/iss-groupbuy-team8/pull/479); [day log 2026-10-13](log/2026-10-13.md); [handoff B3 row](../../swe5001/HANDOFF.md) |
| 2026-10-15 | P5 k6 scripts and V2/V3/V4 runs (#458) | Owner @EricWang1358 to @PPsyrius, who commented "I'm taking this over since I need this for #457". He delivered the first tooling slice in PR #506 (`767d980b`, merged 2026-10-16); PR #518 (`552a6d08`, merged 2026-10-16) recorded the V2 baseline and dashboard session. Issue #458 is still open and lists both as assignees. | @EricWang1358 -5 h, @PPsyrius +5 h | [#458 comment](https://github.com/PPsyrius/iss-groupbuy-team8/issues/458#issuecomment-6195951428); [#506](https://github.com/PPsyrius/iss-groupbuy-team8/pull/506); [#518](https://github.com/PPsyrius/iss-groupbuy-team8/pull/518); [day log 2026-10-16](log/2026-10-16.md) |

Hours per member (total 224.5 h):

| Member | Planned (§5 at Planning) | After 2026-10-13 | After 2026-10-15 |
|---|---|---|---|
| @EricWang1358 | 46 | 31 | 26 |
| @l0sgAi | 51 | 66 | 66 |
| @PPsyrius | 41.5 | 41.5 | 46.5 |
| @Yorick-Yu | 48 | 48 | 48 |
| @limjiechao | 38 | 38 | 38 |
| Total | 224.5 | 224.5 | 224.5 |

Sources: issues #458, #465 and #468; PRs #479, #506 and #518; the day logs linked in the table. The #458 reassignment is recorded only in the issue comment; nothing else in the repository mentions it.

### To confirm (TODO, @EricWang1358 settles)

- [ ] Whether the reviewers and due dates of P5 (#458, reviewer @Yorick-Yu, V2 10-15 and V3/V4 10-19) and P11 (#468, reviewer @Yorick-Yu, 10-16) stand after the owner changes.
- [ ] That the board cards and the container issue #465 show the same owners and hours as this file; #458 still lists both @PPsyrius and @EricWang1358 as assignees.
- [ ] How the 6 h of P12 (#469) are split between @Yorick-Yu and @l0sgAi, who paired on it and merged it in PR #490; the figures above leave all 6 h with @Yorick-Yu.
- [ ] The outcome of the 2026-10-15 mid-sprint check and the identity go/no-go (§6 item 5), and what Sprint Planning changed relative to the draft in §5 to §9.
