<!-- report
rubric: A
owner: Panpakorn Siripanich (Pan)
plan: #681
sources: docs/agile/; docs/agile/metrics/
owns: R36
status: draft
-->

# Appendix A: Agile Artefacts {.appendix}

This appendix collects the agile artefacts of Sprints 1 to 4: the product backlog, user stories with acceptance criteria, sprint plans, burndown charts, sprint reviews, retrospectives and the Definition of Done. The briefing lists these as project deliverables. GitHub Issues and the Project board are the live tools. The durable record is `docs/agile/` in the repository: one plan, review and retrospective per sprint where they were written, one day-log file per working day from Sprint 2 on, and one board snapshot per day from 17 September 2026 (`docs/agile/metrics/YYYY-MM-DD.csv`). The board itself is not in the git archive, so the snapshots are the only record of card movement. Sprint dates follow `docs/agile/definition-of-done.md`.

Table A.1: Where each artefact lives, per sprint.

| Artefact | Sprint 1 (1–12 Sep) | Sprint 2 (15–26 Sep) | Sprint 3 (28 Sep–9 Oct) | Sprint 4 (12–23 Oct) |
|---|---|---|---|---|
| Sprint plan | Container issue #49 | Sprint 2 plan and container issue #93 | `docs/agile/sprint-03/plan.md`, issue #232 | `docs/agile/sprint-04/plan.md`, issue #465 |
| Burndown | Not kept ("TODO" in the Sprint 1 report) | Reconstructed from merge dates (Sprint 2 progress report) | Reconstructed at freeze (`sprint-03/review.md`) | Calculated here from the board snapshots (Table A.7) |
| Sprint review | Sprint 1 progress report | Sprint 2 progress report | `docs/agile/sprint-03/review.md` (draft written at freeze) | Not held; reconstructed on 30 Oct (A.5) |
| Retrospective | Sprint 1 progress report | Sprint 2 progress report | `docs/agile/sprint-03/retro.md` (reconstructed 24 Oct) | Not held; reconstructed on 30 Oct (A.5) |
| Day logs | None | `docs/agile/sprint-02/log/` | `docs/agile/sprint-03/log/` | `docs/agile/sprint-04/log/` |
| Definition of Done | Backend skeleton plan §11 | Sprint 2 progress report | Sprint 3 plan §8 | Sprint 4 plan §8; current list in `definition-of-done.md` |

#### A.1 Product backlog

The product backlog is GitHub Project board 2, "GroupBuy Kanban Board", fed by GitHub Issues created from templates in `.github/ISSUE_TEMPLATE/` (use case, task, NFR, slide feedback). On 26 October 2026 the repository-issue snapshot held 219 issues: 218 board items (159 Done, 38 Backlog, 14 Ready and 7 In progress) and one issue without a board card, not a card with an unset status. The most used labels were `backend` (81 items), `frontend` (58), `devsecops` (47), `swe5006` (47) and `documentation` (41). The board has Status, Priority, Size and Estimate fields; Priority and Size are blank on every item, and Estimate was set on 23 items by 8 October and on 47 by Sprint 4 planning on 12 October, and none changed afterwards. Ordering was therefore kept in the sprint plans, not on the board. The full item list at any date is the snapshot CSV of that day.

The top of the backlog is the nine use cases of the proposal, one issue each, all closed (Table A.2).

Table A.2: Use-case issues of the product backlog.

| Issue | Use case | State on 26 Oct |
|---|---|---|
| #24 | UC1 Browse and view listings (dual-price display) | Closed |
| #25 | UC2 Register / authenticate | Closed |
| #26 | UC3 Initiate a team (pay-first) | Closed |
| #27 | UC4 Join a team | Closed |
| #28 | UC5 Team lifecycle resolution | Closed |
| #29 | UC6 Refund on team expiry | Closed |
| #30 | UC7 Share attribution | Closed |
| #31 | UC8 View my orders and teams | Closed |
| #32 | UC9 Admin listing management (API-only CRUD) | Closed |

#### A.2 User stories with acceptance criteria

Stories are written "As a ⟨role⟩, I want ⟨goal⟩, so that ⟨benefit⟩", with owner, reviewer, estimate and issue, and their acceptance criteria are in the sprint plan or on the story issue. The Sprint 3 plan is the fullest example: seventeen backlog items, each story with acceptance criteria in §5. Table A.3 gives a sample. Recorded weaknesses: no INVEST check was made, some stories have no benefit clause (US13, US23, US27, US30), some are written for an operator or developer, and the Sprint 4 backlog lists tasks rather than stories (`docs/agile/practices.md`).

Table A.3: Sample user stories and their acceptance criteria (Sprint 3 plan §4–§5).

| Story | Statement | Acceptance criteria (abridged) |
|---|---|---|
| US25 (#221) | As an admin, I want to set a listing's team size, lifetime and team price policy, so buyers can start blocs on real listings. | Admin-only API (403 for non-admins, 401 anonymous); size at least 2, lifetime within range, team price below individual price, 400 with field errors otherwise; stale version returns 412; writes audited; a START succeeds on a configured listing (integration test); contract changes additively. All ticked when the story merged |
| US26 (#183) | As a buyer, I want to be charged the price I was quoted, so the checkout never surprises me. | Decision recorded; if honoured, START charges the quoted amount; an expired or foreign quote returns 409 with its own code; integration tests cover a policy change between quote and intent |
| US23 (#180) | As an operator, I want p95 measured at 100 concurrent users for browse, join intent and my orders. | Committed script; 100-user run with p50, p95, p99, error rate and throughput; expiry lag under load; no login bypass in any deployed profile; a miss is recorded, not hidden |
| US13 (#223) | As a buyer, I want my payment refunded automatically when my bloc expires, on the deployed environment. | Backlog counts recorded before switching; Checkout and scheduling enabled through SSM; a seeded bloc expires within one sweep, its orders are cancelled and each payment gets exactly one Sandbox refund |
| US15 (#30, #201) | As a member, I want to share an invite link that names me, so friends join my bloc and I get the credit. | Public invite endpoint returns no email or user id; the listing names the sharer; a join through the link writes one share-credit row; self-referral refused; Vitest and Playwright cover open, full and ended blocs |

#### A.3 Sprint plans

Each sprint was planned in a container issue that lists the goal, the member-by-use-case matrix and the backlog with owners and hours; from Sprint 3 the plan is also a file in the repository (Table A.4).

Table A.4: Sprint goals and planned capacity.

| Sprint | Goal (abridged) | Planned capacity | Plan |
|---|---|---|---|
| 1 (1–12 Sep) | A minimal end-to-end slice: sign in with Google, browse the listing list and detail; every member claims at least one use case | Not estimated in hours | Issue #49 |
| 2 (15–26 Sep) | A buyer pays through Stripe, becomes the leader of a new team or joins one, sees the order and team, and the team resolves automatically; every step safe to replay | 150 h (5 × 30 h) | Issue #93; Sprint 2 progress report |
| 3 (28 Sep–9 Oct) | On deployed dev: invite link naming the sharer, START or JOIN on an admin-configured listing, pay the quoted price, see order and bloc, automatic refund on expiry; p95 measured at 100 users | 150 h, 17 work items | `sprint-03/plan.md`, issue #232 |
| 4 (12–23 Oct) | Deployed dev runs the complete buyer and admin system with paid START, JOIN and refund; the same code runs as three services on SWE5001 ECS; every member's use case and design problem drafted | 224.5 h across both courses, 29 work items | `sprint-04/plan.md`, issue #465 |

#### A.4 Burndown charts

The board's Estimate field was not kept daily in any sprint, so no burndown came straight from the board. Each burndown below states how it was made. Remaining hours are read at the end of each working day.

**Sprint 2** was reconstructed for its progress report from the merge date of the pull request that satisfied each story's acceptance criteria; each story burned in full on that date (Table A.5). It stays flat for a week because no committed story merged before 22 September, then falls as the stacked payment, order and scheduling layers merged. 134 of 150 h met the Definition of Done; US23 (12 h) and the My Teams page (4 h) carried over.

Table A.5: Sprint 2 burndown, remaining hours (Sprint 2 progress report, "Daily burndown data").

| Date | Start | 15 Sep | 16 | 17 | 18 | 19 | 22 | 23 | 24 | 25 | 26 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Ideal | 150 | 135 | 120 | 105 | 90 | 75 | 60 | 45 | 30 | 15 | 0 |
| Actual | 150 | 150 | 150 | 150 | 150 | 150 | 124 | 74 | 40 | 16 | 16 |

**Sprint 3** was reconstructed from the review's planned hours, each item burned on its issue's close date (Singapore time). It follows the ideal line in the first week, then stays at 68 h on 5–7 October and 66 h on 8–9 October: Checkout on dev stayed open, refund idempotency had local evidence only, and two items waited on others. Counting partial progress, the review puts the remainder at 33 h (117 of 150 h burned). The daily board snapshots agree with the shape. Of the 20 issues listed in the Sprint 3 container issue #232 (#96, #130, #134, #180, #183, #201, #204, #205, #216 and #221 to #231), the number still open in each working day's snapshot was 18 on 29 September, 15 on 30 September, 12 on 1 October, 11 on 2 October, then 6 on every day from 5 to 9 October (calculated from `docs/agile/metrics/2026-09-29.csv` to `2026-10-09.csv`; the 28 September snapshot predates eleven of the issues).

Table A.6: Sprint 3 burndown, remaining hours (whole items, burned on close date).

| Date | Start | 28 Sep | 29 | 30 | 1 Oct | 2 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Ideal | 150 | 135 | 120 | 105 | 90 | 75 | 60 | 45 | 30 | 15 | 0 |
| Actual | 150 | 132 | 114 | 90 | 82 | 76 | 68 | 68 | 68 | 66 | 66 |

**Sprint 4** is calculated here from the daily board snapshots by the same method as Sprint 3. The planned hours of each Sprint 4 backlog item with an issue (214.5 of the 224.5 h; the 10 h of evidence work has no issue and is left out) count as remaining while the issue is open in that day's snapshot, and burn on the snapshot where it is first closed (Table A.7). The team has not reviewed this chart; the Sprint 4 review reconstructed in A.5 uses it.

Table A.7: Sprint 4 burndown, remaining hours of issue-backed work (calculated from `docs/agile/metrics/2026-10-12.csv` to `2026-10-23.csv`).

| Date | Start | 12 Oct | 13 | 14 | 15 | 16 | 19 | 20 | 21 | 22 | 23 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Ideal | 214.5 | 193.1 | 171.6 | 150.2 | 128.7 | 107.3 | 85.8 | 64.4 | 42.9 | 21.5 | 0 |
| Actual | 214.5 | 214.5 | 208.5 | 174.5 | 113.5 | 113.5 | 96.5 | 96.5 | 96.5 | 96.5 | 96.5 |
| Open items | 28 | 28 | 27 | 22 | 17 | 17 | 15 | 15 | 15 | 15 | 15 |

The SWE5001 split burned fast up to the 16 October SWE5001 code freeze. The line then stays at 96.5 h because fifteen issues were still open on 23 October: the report and presentation work (41 h and 4 h, which run past the sprint by design), Scrum Master duties, the Sprint 3 closure, the dev payment rollout (closed on 25 October) and several SWE5001 evidence issues whose work was presented on 21 October but whose issues stayed open. Issue state therefore understates what was done; this is the limit of burning on close dates when cards are not closed promptly.

#### A.5 Sprint reviews

- **Sprint 1** (Sprint 1 progress report, 12 Sep): the dev pipeline (build, scan, ECR, SSM deploy with rollback) and the CloudFront and load-balancer entry were completed; Google sign-in, browse and admin listing management were in progress; the frontend had not started.
- **Sprint 2** (Sprint 2 progress report, 26 Sep): goal substantially met. The payment-to-team-to-order chain and automatic team resolution were complete and tested, and orders were visible in the web application; teams were visible through the API only. 9 of 11 stories done, 134 of 150 h.
- **Sprint 3** (`docs/agile/sprint-03/review.md`, written at the 8 October freeze): goal not met on dev. Every code path of the goal was merged into `dev` and tested, but Checkout, refunds and refund recovery were verified only locally, because Checkout and scheduling were still off on dev. 11 of 17 items done; 117 of 150 h; the p95 target was met on a local stack. Its demo notes section was never filled in.
- **Sprint 4** (reconstructed on 30 October, below): goal partly met. The paid journey was shown on dev, the join one day after the sprint, and the three-service release reached production, but most of the SWE5001 evidence and two of the five report drafts were not finished in the sprint. Its Definition of Done placement was agreed on 24 October and re-agreed on 25 October: released (the shared SWE5001 production) and shown on dev (Table A.10).

**Sprint 4 review, reconstructed.** On 24 October the team decided to hold the Sprint 4 review and retrospective after the presentation, with its remarks. No record of either exists, and their tracking issue was closed on 26 October without them. We reconstructed both on 30 October from the sprint plan, the board snapshots of 12 to 27 October and the day logs, as the Sprint 4 row of Table 2.10 was. Who would have attended, and any discussion, are not recorded, so none is given.

The goal (Table A.4) had three parts. On dev, the paid start and the automatic expiry refund were shown on 23 October (Table E.3), the last day of the sprint, and a second buyer's paid join on 24 October in a recording (Appendix E). On production, the three-service release was promoted on 17, 19 and 20 October (Table 2.8), but of the evidence the goal named, only an HTTP scaling run was made on AWS, and it failed its own acceptance check; queue-driven scaling, self-healing, the identity outage and the DLQ drill were not captured on AWS, and the last two were rehearsed locally only. Of the five members' use-case and design-problem drafts, three were in the repository by 23 October and two were not. Table A.5.1 sets each committed item against what was delivered by 23 October; the remaining hours are in Table A.7 and the done hours in section 2.3.

Table A.5.1: Sprint 4 backlog, committed against delivered by 23 October (plan of 12 October; board snapshots, Singapore dates; day logs).

| Item | Planned owner | h | State on 23 October | Evidence |
|---|---|---|---|---|
| US13 Checkout, scheduling and expiry refunds on dev | Yorik | 7 | Shown on dev on 23 Oct; issue closed 25 Oct | Table E.3; #223 |
| US14 Refund idempotency record from local evidence | Yorik | 1 | Not done; still open at the freeze | #224 |
| US34 Paid START and JOIN on dev | Liu Jin | 2 | Issue closed 13 Oct, before the paid start (23 Oct) and join (24 Oct) were shown on dev | #222; Table E.3; Appendix E |
| US28b NFR-37 accepted risk | Pan | 0.5 | Accepted risk decided 11 Oct (Table 2.6); issue open | #227 |
| US17 NFR register sweep | Pan | 1 | Register cleaned up 19–20 Oct; issue open | #454 |
| RF Admission and receipt refactor | Eric | 2 | Closed 13 Oct | #421–#423, PR #424 |
| DEC Backend ADR 0007 | Liu Jin | 3 | Merged 11 Oct, before the sprint; still Proposed (Appendix B); issue open | #443 |
| US38 Notification persistence and list endpoint | Pan | 8 | Closed 13 Oct | #215 |
| US39 Transactional outbox | Yorik | 12 | Closed 13 Oct | #444 |
| US40 Outbox relay | Yorik | 10 | Closed 13 Oct | #445 |
| US41 Idempotent SQS consumer | Eric | 12 | Closed 16 Oct | #446 |
| NFR Event delivery evidence | Liu Jin | 4 | Consumer switch merged 16 Oct; live evidence on AWS not captured | #448 |
| P1 Messaging module | Pan | 8 | Closed 14 Oct | #447 |
| P2 Three service assemblies, ADR 0008 | Liu Jin | 21 | Closed 14 Oct | #455 |
| P3 Distributed topology on ECS | Liu Jin | 11 | Closed 14 Oct | #456 |
| P4 HTTP scaling and dashboard | Pan | 5 | Closed 16 Oct | #457 |
| P5 k6 scripts and runs | Eric | 5 | V2 baseline on ECS 16 Oct (Table E.4); the V3 run of 19 Oct failed its acceptance check; V4 not run on AWS | #458 |
| P6 Drills | Yorik | 4 | Rehearsed locally 15 Oct; AWS drills not run | #459 |
| P7 Smoke suite and manual checks on ECS | Lim | 3 | Smoke suite green at the 20 Oct promotion (Table 2.8); manual checks not recorded; issue open | #460 |
| P8 SWE5001 presentation | Lim | 13 | Presented 21 Oct; issue open | #461 |
| P9 SWE5001 progress report 2 | Liu Jin | 2 | No record in the repository | #462 |
| P10 Session roles outside identity | Pan | 6 | Closed 13 Oct | #467 |
| P11 Remote identity check | Eric | 15 | Closed 14 Oct | #468 |
| P12 Attention nicknames through the identity port | Yorik | 6 | Closed 14 Oct | #469 |
| S3C Sprint 3 review, retrospective, progress report | Lim | 4 | Retrospective reconstructed on 24 Oct, after the sprint; review still a draft (Table 2.6) | #451 |
| RPT SWE5006 report drafts | All | 41 | Yorik's, Lim's and Eric's drafts in the repository by 23 Oct; report continued to 31 Oct | #463 |
| PRS SWE5006 slide master | Lim | 4 | Deck drafted from 19 Oct; presented 26 Oct | #464 |
| SM Scrum Master duties | Eric | 4 | No mid-sprint check recorded; review and retrospective not held | #465 |
| EV Evidence | All | 10 | No issue; not counted (Table A.7) | None |

Thirteen of the 28 issue-backed items were closed by the SWE5001 code freeze of 16 October, and none between then and 23 October. They include the whole microservice split and the code of the event chain, the work the plan ranked first. Demo notes: the increment was shown at the SWE5001 presentation on 21 October, and at the SWE5006 presentation on 26 October, where the system demo ran live on dev (Table 2.8; what it showed, Appendix E) and the pipeline demo is described in section 4.3; the lecturer's remarks are in Table 2.7. Sprint 4 was the last sprint, so nothing was carried into a next sprint. Carried past its end were the dev payment rollout (closed 25 October), the refund idempotency record, the SWE5001 evidence captures and progress report, the NFR register records and the ADR 0007 status, the Sprint 3 review, and the report and presentation, which ran to 31 and 26 October by design.

**Sprint 4 retrospective, reconstructed.** Went well: the plan's priority order held, so the three-service release reached production before the SWE5001 presentation (Table A.10); the paid journey that Sprint 3 could show only locally was shown on dev; and the cuts of 11 October moved the report drafts into the sprint. Could be better: cards were not closed when their work was done, and the estimates were not kept (Table 2.10); Checkout reached dev nine days late (Table A.8, Sprint 3 row); most of the SWE5001 evidence planned for the 19 October session was not captured on AWS; and neither the mid-sprint check nor the review and retrospective is recorded. Action: the Sprint 4 action of Table 2.10, which was not tested because no sprint followed.

#### A.6 Sprint retrospectives

Each retrospective's main action, and what it changed in the next sprint, are summarised in Table A.8. The Sprint 3 retrospective was not held as a meeting; it was reconstructed on 24 October from the review and the day logs and says so.

Table A.8: Retrospective outcomes per sprint.

| Sprint | Went well | Could be better | Action for the next sprint | Effect |
|---|---|---|---|---|
| 1 | The pipeline was operational early; dev live on 5 Sep with gates, scans and rollback | No burndown or story tracking; features slower than planned because infrastructure came first | Kanban with story templates; daily burndown data; focus on features | Board snapshots automated from 17 Sep; Sprint 2 shipped the payment chain and the frontend |
| 2 | The payment, team and order chain proved by tests on real PostgreSQL, including replays and the last-seat race (run locally and in CI: Table 4.12); frontend deployed with automated delivery | Late, very large merges (one slice of about 10,000 lines); effort not recorded in hours; work drifted from the assignment table | Pull requests below about 1,500 lines; hours in the board daily; a mid-sprint owner check; verification in the first half | The 1,500-line and hours actions became DoD rules 9 and 10, and the Sprint 3 DoD added the "on dev" rule 8; the performance baseline ran on 30 Sep, in the first half; the hours were again not kept |
| 3 | 11 of 17 items done; the critical path started early; gate findings fixed, not waived; CI green at close | Goal not met on dev; dependent items blocked each other; estimates still empty; unplanned work competed; a session token was pasted into a chat | Hours in the Estimate field daily; Checkout on dev first; close blocked carry-overs instead of carrying them | Estimates set on 47 cards by Sprint 4 planning but never updated; Checkout reached dev on 23 Oct, nine days late; blocked items closed as not planned on 11 Oct |
| 4 | See A.5 | See A.5 | See A.5 | See A.5 |

The Sprint 1 and Sprint 2 retrospectives are in their progress reports; `docs/agile/README.md` promises a `retro.md` per sprint, but only Sprint 3 has one.

#### A.7 Definition of Done

The Definition of Done grew sprint by sprint, each list amending the one before; Table A.9 is the Sprint 4 list. The course's done ladder (coded, tested, integrated, releasable, released; EPAT 01 Agile, p. 26) has one reading in this project (the team's reading, section 2.1). Table A.10 gives the step each sprint reached.

Table A.9: Definition of Done, Sprint 4 (`docs/agile/definition-of-done.md`).

| # | Rule | Since |
|---|---|---|
| 1 | Acceptance criteria are satisfied and linked to the story or issue | Sprint 2 |
| 2 | The endpoint is in `contracts/openapi.json` before its controller PR is opened, and the contract-drift check passes without a waiver | Sprint 2; "before" from Sprint 3 |
| 3 | The implementation respects the COLA and DDD boundaries (ArchUnit) | Sprint 2 |
| 4 | Tests cover the main path, validation failures, and the replay and concurrency cases of the story | Sprint 2 |
| 5 | Every cross-domain reaction states its transaction phase, and a test proves it | Sprint 2 |
| 6 | Maven verify, Spotless, Checkstyle, SpotBugs, ArchUnit, Gitleaks, Trivy, both SonarCloud gates and `pnpm check` pass | Sprint 2; SonarCloud and `pnpm check` from Sprint 3 |
| 7 | Reviewed, with no secret, no unresolved blocking defect and no unapproved API break | Sprint 2 |
| 8 | Where the story says "on dev", it is demonstrated on deployed dev, with the evidence in the day log; a local run does not count | Sprint 3 |
| 9 | Fewer than about 1,500 changed lines, or one layer of a stacked series | Sprint 3 |
| 10 | Remaining hours in the board's Estimate field on the day the card moves | Sprint 3 |
| 11 | Board, day log, NFR register and contribution record updated before completion is counted | Sprint 2; day log and NFR register from Sprint 3 |
| 12 | SWE5001 changes are additive: the monolith stays the default, the SWE5006 required checks stay required, and the EC2 deploy is confirmed healthy after a change to the boot JAR, Dockerfiles or Compose files | Sprint 4 |
| 13 | "On ECS" evidence is captured in a recorded session and linked from the day log | Sprint 4 |
| 14 | Every ECS session ends with `env-down.sh` and a clean `env-status.sh`, recorded in the day log | Sprint 4 |
| 15 | A design choice made while building is drafted into the owner's report section in the same week | Sprint 4 |

Table A.10: Done-ladder step reached per sprint.

| Sprint | Step reached | Evidence |
|---|---|---|
| 1 | Integrated | Backend on dev from 5 Sep; no frontend yet, so not releasable |
| 2 | Integrated | 9 of 11 stories merged and tested; Checkout and scheduling off on dev |
| 3 | Integrated | Merged and tested; the goal was not met on dev (Table 2.9) |
| 4 | Released (shared SWE5001 production); shown on dev | Three-service production on ECS (promoted to production, section 2.3); paid START and expiry refund shown on dev on 23 October (Table E.3); a second buyer's paid JOIN shown on dev on 24 October in a recording (Appendix E) |

Recorded adherence: in Sprint 2, contracts were regenerated in the controller's pull request rather than a day ahead, and remaining effort was kept as a work log rather than in hours. Rule 10 was not kept in Sprints 3 and 4 (A.4).

#### A.8 Engineering practices and day logs

`docs/agile/practices.md` points to the evidence for each practice of the module and states what the evidence does not show:

- **Test-driven development**: red-first tests recorded in the day logs of 27 September (#205), 1 October (#326–#329), 3 October (#274) and 7 October (#421–#423). The red step is in the logs, not the history, because commits are squashed.
- **Pair programming**: one recorded pairing, Yorik and Liu Jin on the admin attention query (#469, PR #490, 14 October); how the pair worked is not recorded.
- **Refactoring**: the product page split into hooks and pure rules (#296, 30 September); admission refactored onto the existing Factory Method (#421–#423, PR #424); the attention query moved to the identity port (#469).
- **Agile architecture**: decisions written as ADRs as they were made (backend 0001–0008, frontend 0001–0015) and enforced by the ArchUnit rules (Table 3.2).
- **Continuous integration**: one mainline with reviewed, checked merges (chapter 4); a daily trunk check-in is not shown, because the team uses Branch by Feature.

The day logs (`docs/agile/sprint-0N/log/`, one file per working day, one entry per pull request per author from 22 September) are the running record behind every artefact above; they are not reproduced here.
