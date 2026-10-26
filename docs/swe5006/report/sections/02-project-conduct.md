<!-- report
rubric: 2.1, 2.2, 2.3
owner: Lim Jiechao (Eric: Sprint 4 data; Pan: artefacts)
plan: #676
sources: docs/agile/sprint-0N/; docs/agile/definition-of-done.md; deck s4–s7
status: draft
-->

# 2 Project Conduct

## 2.1 Project Plan

The team planned at two levels. The proposal set a project-level work breakdown of about 50 person-days, roughly ten per member, over four two-week sprints. Each sprint plan then estimated its stories in hours and named one owner per story, the person who writes the code. Every member owns the analysis and design of at least one use case and one design problem (Table 2.4).

#### Work breakdown and baseline

The proposal (section 7.1) breaks the project into nine tasks, each with an estimate and a lead: (1) project setup; (2) analysis and design; (3) infrastructure; (4) DevSecOps pipelines; (5) backend domain; (6) frontend single-page application; (7) end-to-end tests; (8) agile process; (9) documentation. The baseline is 50 person-days, about 10 per member, which matches the briefing's expectation of about ten person-days per participant. {{FILL: estimate and lead per WBS task from PROP §7.1, which is not in the repository — Lim}}

#### Sprints

Table 2.1 lists the four sprints with their goals and planned capacity. The dates follow the progress reports and the team's Definition of Done page; the sprint container issues carry slightly different dates.

Table 2.1: Sprints, goals and planned capacity.

| Sprint | Dates | Goal | Planned capacity | Container issue |
| --- | --- | --- | --- | --- |
| 1 | 1–12 Sep | Walking skeleton: sign-in, browse and admin listings on dev | Not recorded | #49 |
| 2 | 15–26 Sep | Pay, join a team, see the order, all replay-safe | 150 h, 30 h per member | #93 |
| 3 | 28 Sep–9 Oct | Invite, pay and refund on the deployed dev environment | 150 h, 30 h per member | #232 |
| 4 | 12–23 Oct | The complete system on dev; the same code as three services on ECS for SWE5001 | 224.5 h | #465 |

Sprint 1 had no sprint-level estimates: its hours were never recorded, and its progress report left the contribution table unfinished. From Sprint 2 every committed story carries an hour estimate and an owner; Sprint 3 and Sprint 4 also add rows for Scrum Master duties and evidence work. Sprint 4 serves both courses, so its 224.5 h include SWE5001 platform work, 41 h of SWE5006 report drafts and 4 h for the SWE5006 slides. Its hours stayed as planned on 12 October; a later proposal to rebalance them was not applied (decided 24 October).

Table 2.2: Planned hours per member and sprint.

| Member | Sprint 1 | Sprint 2 | Sprint 3 | Sprint 4 | Sprints 2–4 |
| --- | --- | --- | --- | --- | --- |
| Liu Jin | not recorded | 30 | 30 | 51 | 111 |
| Yu Qianshuo (Yorik) | not recorded | 30 | 30 | 48 | 108 |
| Wang Ziang (Eric) | not recorded | 30 | 30 | 46 | 106 |
| Panpakorn Siripanich (Pan) | not recorded | 30 | 30 | 41.5 | 101.5 |
| Lim Jiechao | not recorded | 30 | 30 | 38 | 98 |
| **Team** | not recorded | **150** | **150** | **224.5** | **524.5** |

Section 2.3 sets the agreed estimate of effort actually spent beside these planned hours.

#### Ownership of use cases and design problems

Each member owns the analysis and design of their use cases in section 3.4 and one design problem in section 3.5. The section numbers follow the order in which the team planned its design presentation; they do not rank the work. Owning a section means writing its analysis, design and pattern; where a teammate wrote some of the code behind it, chapter 5 credits them.

Table 2.3: Ownership of use cases, design problems and report sections.

| Member | Use cases (analysis and design) | Design problem and pattern | Other report sections |
| --- | --- | --- | --- |
| Wang Ziang (Eric) | UC3, UC4 and the included Pay upfront (3.4.1) | Creating a team without naming its class: Factory Method (3.5.1) | 3.2 overview, 3.2.1–3.2.4, 3.2.8; 3.3.4, 3.3.5, 3.3.11 |
| Yu Qianshuo (Yorik) | UC6 (3.4.2) | Consequences of a team's outcome: Observer (3.5.2) | 3.2.5–3.2.7, 3.2.9; 3.3.7; Appendix E |
| Panpakorn Siripanich (Pan) | UC1, UC2 (3.4.3) | Outbox transports: Template Method (3.5.3) | 3.3.2, 3.3.3, 3.3.12; chapter 4; Appendices A, D, E |
| Lim Jiechao | UC7, UC8 (3.4.4) | Pricing modes: Strategy (3.5.4) | Editor; chapters 1, 2 and 5; 3.3.1, 3.3.8, 3.3.9; Appendix B |
| Liu Jin | UC5, UC9 (3.4.5) | Listing rules by state: State with Optimistic Offline Lock (3.5.5) | 3.1, 3.3.6, 3.3.10, 3.6; Appendices B, C, D |

The proposal (section 6.1) gave an indicative allocation: UC1 with Strategy and UC2 to Pan; UC3 with Factory Method and UC4 to Eric; UC5 with State or Observer and UC9 to Liu Jin; UC6 with Adapter to Yorik; UC7 with Observer and UC8 to Lim. The use case owners are unchanged. Three patterns moved, decided on 21 October: the Stripe payment abstraction became gateway ports under a transition strategy (section 3.2.5), so Yorik took Observer, now applied to the consequences of a team's outcome in UC5 and UC6; Strategy for UC1 pricing went from Pan to Lim; and Pan took Template Method for the outbox transports. State moved from the team lifecycle to the listing lifecycle (sections 2.2 and 3.5.5).

#### Roles

Table 2.4: Scrum roles per sprint.

| Sprint | Scrum Master | Product Owner | Record |
| --- | --- | --- | --- |
| 1 | None named | {{CONFIRM: D11 who acted as Product Owner — Lim}} | #49 |
| 2 | None named | (as above) | Sprint 2 plan, #93 |
| 3 | Lim Jiechao, 10 h planned | (as above) | #232 |
| 4 | Wang Ziang (Eric), 4 h planned {{VERIFY: Eric confirmed as Scrum Master at Sprint 4 Planning on 12 Oct; the plan names him "proposed" — Eric}} | (as above) | #465 |

All five members acted as developers in every sprint. Code owners review each area: Pan for the CI workflows, Liu Jin for the backend, Lim for the frontend, with Yorik and Eric also requested on the payment and group-buy packages.

#### Definition of Done

The Definition of Done grew sprint by sprint, each time as changes to the previous list, and reached 15 rules in Sprint 4. They cover acceptance criteria, the API contract before the controller, architecture boundaries, tests for replay and concurrency, the required checks, review, the rule that "on dev" means shown on the deployed dev environment (from Sprint 3), pull requests under about 1,500 changed lines, remaining hours on the board, and four rules for the two-course Sprint 4. Appendix A lists all 15 with the sprint each started.

The course gives a ladder of done steps: coded, tested, integrated, releasable, released (EPAT 01 Agile, p. 26). The team reads the upper three steps as follows. This reading is a team ruling, not a course rating:

- **integrated:** merged to `dev` with every required check green;
- **releasable:** shown working on the deployed dev environment, which is lower than the course's "production ready";
- **released:** promoted to production through the gated release path and verified there by the smoke suite. Production is the shared SWE5001 release on ECS, with Stripe in test mode and no outside users.

Section 2.3 places each sprint on this ladder.

## 2.2 Project Status

BlocBuy delivers everything the proposal promised in function, and more: all nine use cases plus an admin console. Eight design or platform choices changed during the project, and one deliverable, a separate SWE5006 production stack, was dropped. On dev, paid start, a second buyer's paid join and the expiry refund have all been shown, by 24 October. The open issues are crash recovery of refunds not yet shown on dev, an unreconciled lock order between two decision records, and a set of security and operations gaps on the dev host. This snapshot is dated 26 October 2026. {{VERIFY: re-read every row of Tables 2.5 and 2.6 at the freeze SHA on 30 Oct — Lim}}

#### Promised against delivered

Benchmarking the status against the proposal is a team ruling; the proposal is not a marking document. Table 2.5 compares the proposal with what was built. A change cites its decision record where one exists. Five of the eight changes have no ADR at all, and the session change has only a frontend ADR; their reasoning is in design documents and plans, and Appendix B lists them as decisions without an ADR.

Table 2.5: Promised against delivered, 26 October 2026.

| Item | Promised (proposal) | Status | What was built, and the decision record |
| --- | --- | --- | --- |
| Use cases UC1–UC9 | Nine use cases (section 6.1) | Delivered | All nine built, tested and shown on dev by 24 October (Table 1.3). UC4's paid join on dev is shown in a recording only |
| Crash recovery of refunds | Refunds safe under replay and crash | Open | Proven locally (Testcontainers and a local rehearsal on 3 October); not shown on dev (#224) |
| UC9 administration | API-only listing CRUD plus database seeding | Changed | An admin console for administrators, built in Sprint 3 (frontend ADR 0015) |
| Sign-in session | Google sign-in, with a token (JWT) kept in browser local storage | Changed | Google sign-in unchanged; the server keeps a Sa-Token session in Redis and the browser holds only an HttpOnly cookie (frontend ADR 0002; backend skeleton plan decisions D6, D7; no backend ADR) |
| Payment abstraction and team resolution | Adapter for Stripe (UC6); Observer for team resolution (UC7) | Changed | Payment providers sit behind the `CheckoutGateway` and `RefundGateway` ports (section 3.2.5); Observer applied to the consequences of a team's outcome, UC5 to UC6 (section 3.5.2). No ADR |
| Team lifecycle | State pattern for the team | Changed | A transition table, `DealStatus.canTransitionTo`, for the team; State applied to listings instead (section 3.5.5). No ADR |
| Persistence | Spring Data JPA | Changed | MyBatis behind repository ports (backend skeleton plan decisions D1, D9). No ADR |
| Dev environment | Amazon ECS Fargate and RDS | Changed | One EC2 host with Docker Compose and PostgreSQL on the host (backend ADR 0008) |
| Secrets | AWS Secrets Manager; no static credentials anywhere | Changed | SSM SecureString parameters and a root-only environment file on the host. CI uses short-lived OIDC credentials, but the project account has two IAM users for port forwarding, one with an active access key. No ADR |
| Pipeline security | Blocking OWASP Dependency-Check and infrastructure-as-code scans; three plan, deploy and apply roles | Changed | Trivy dependency and configuration scans run on pull requests but are not required checks; three per-job OIDC roles (image push, backend deploy, frontend deploy). No ADR |
| Production release | A first production release with a rehearsed rollback | Dropped | No separate SWE5006 production stack (#226, closed 11 October). Production is the shared SWE5001 release on ECS, promoted on 17 and 20 October (backend ADR 0008 records the two topologies) |
| Test and delivery evidence | End-to-end tests on dev; a burndown; saved-plan infrastructure promotion | Partial | Playwright end-to-end tests run against API stubs (frontend ADR 0011); the board is snapshotted daily, but no burndown chart is generated from it; saved-plan Terraform promotion is a manual operator step |
| Quality gates | SonarCloud quality gate | Delivered beyond | Both SonarCloud gates (backend and frontend) and Checkstyle (inside `build-quality`) are required checks on every pull request |
| Notifications | Not in section 6.1 | Added (SWE5001) | In-app notifications through a transactional outbox, SNS and SQS, coded and running on SWE5001 ECS, switched off on SWE5006 dev (backend ADR 0007) |

#### Outstanding issues

Table 2.6 lists the issues still open on 26 October. Appendix E lists every known limit of the dev environment with its evidence.

Table 2.6: Outstanding issues, 26 October 2026.

| Issue | Owner | State | Record |
| --- | --- | --- | --- |
| Refund idempotency under replay and crash shown only locally, not on dev | Yorik | Open; rescoped on 11 October to a record from local evidence | #224 |
| ADR 0001 orders locks as deal, intent, payment; ADR 0002 as team, intent, order, payment. One combined order is not yet stated | Unassigned | Open | #663 |
| CloudFront reaches the dev load balancer over HTTP (NFR-37) | Pan | Accepted risk on dev until 16 November | #227 |
| SSH port 22 open to any address on the dev host's security group | Pan | Open in the AWS read-only check of 21 October; the team decided on 24 October to close it {{VERIFY: closed or still open at the freeze date — Pan}} | — |
| Dev EBS volume not encrypted | Pan | Seen in the AWS read-only check of 19 October; not raised as an issue {{VERIFY: re-read at the freeze date — Pan}} | — |
| Two IAM users in the project account, one with an active access key, for port forwarding to the dev host | Lim | Disclosed and accepted in the AWS account governance record; no removal planned | — |
| Backend rollback never exercised on dev: the rollback workflow has never run | Unassigned | Open | #623 |
| The weekly full-history secret scan fails on four historical badge-token findings (NFR-02) | Unassigned | Open | — |
| Passive OWASP ZAP scan of dev: 0 High, 2 Medium, 4 Low findings, all content-security-policy and header issues (run of 24 October) | Pan | Open | #610, #636–#641 |
| Exposure of the actuator, springdoc and error paths outside the Sa-Token interceptor not decided | Eric | Open | #549 |
| After a merge, delivery rebuilds the backend image without repeating the pull request's Trivy image scan, so the deployed image is not the scanned one | Unassigned | Known limit | — |
| Database and application servers share subnets in the SWE5001 infrastructure code (NFR-70) | Pan | Open; SWE5001 scope | #589 |
| Sprint 3 retrospective was reconstructed on 24 October, and the Sprint 3 review is still a draft | Lim | Open | #451 |
| Sprint 4 review, retrospective and burndown are not in the repository, although their tracking issue was closed on 26 October | Eric | Open | #619 |

The performance and expiry targets need the wording of section 1.4.3: response time is met locally and on the SWE5001 ECS environment but was never measured on dev, and one-minute expiry is a design bound, not a result shown on dev.

## 2.3 Project Metrics

The project met every planned milestone except a separate SWE5006 production release, which was dropped. Its last delivery milestone was the paid journey on dev on 23 and 24 October; the presentation on 26 October followed it (Table 2.7). No member logged hours. The only actual effort figures are the person-day split the five members agreed on 19 October: about 50 person-days in total, as the proposal planned.

#### Milestones achieved

Table 2.7: Milestones achieved.

| Date | Milestone | Evidence |
| --- | --- | --- |
| 27 Aug | Proposal submitted | Proposal |
| 5 Sep | Backend delivery pipeline live on dev, with health check and rollback | Sprint 1 progress report |
| 14 Sep | Sprint 1 tag `v0.1.0-sprint1` | Git tag |
| 20 Sep | Stripe Sandbox acceptance, tested locally | Sandbox acceptance record of 20 September |
| 22 Sep | Web application live on dev | #93 |
| 27 Sep | Sprint 2 tag `v0.1.0-sprint2` | Git tag |
| 30 Sep | Admin console merged | PRs #305–#307 |
| 11 Oct | Sprint 3 tag `v0.1.0-sprint3` | Git tag |
| 17 Oct | First promotion of the three-service release to production on ECS (shared SWE5001 release) | PR #534 |
| 20 Oct | Last production promotion before the presentations, smoke suite green | PR #574, workflow run 39435280952 |
| 23 Oct | Paid start and the automatic expiry refund shown on dev | Dev recovery acceptance record of 23 October; PR #664 |
| 24 Oct | A second buyer's paid join shown on dev, in a recording | #657 |
| 24 Oct | Sprint 4 tag `v0.1.0-sprint4` | Git tag |
| 26 Oct | SWE5006 presentation, with the system demo on dev {{VERIFY: the demo ran on dev as planned — Lim}} | #464 |

Tag dates are the tagger dates. The repository has no GitHub milestones or releases.

#### Definition of Done reached per sprint

Table 2.8 places each sprint's goal increment on the done ladder of section 2.1, using the team's reading of its upper steps.

Table 2.8: Done step reached per sprint.

| Sprint | Step reached | Basis |
| --- | --- | --- |
| 1 | Integrated | Backend on dev from 5 September; no frontend yet, so not releasable |
| 2 | Integrated | 9 of 11 stories merged and tested, 134 of 150 h; Checkout and scheduling off on dev |
| 3 | Integrated | Merged and tested; the goal was not met on dev under the "on dev" rule; 117 of 150 h |
| 4 | Released (shared SWE5001 production), and shown on dev | Production on ECS promoted on 17 and 20 October with the smoke suite green; on dev, paid start and the refund on 23 October and a second buyer's paid join on 24 October |

The team agreed the Sprint 4 placement on 24 October and re-agreed its wording on 25 October. In Sprints 2 and 3 the money path worked locally, but the strict "on dev" rule does not count local runs.

#### Retrospective improvements

Each retrospective chose an improvement that changed the next sprint. Table 2.9 shows one per sprint and what it changed.

Table 2.9: Retrospective improvements and their effect.

| Retrospective | Finding | Change in the next sprint | Effect |
| --- | --- | --- | --- |
| Sprint 1 | No burndown and no hours were kept | Dated day logs and a scheduled daily board snapshot from 17 September | Every later sprint has a day log and daily board data |
| Sprint 2 | One pull request of about 10,000 lines; a flat burndown followed by a cliff | Pull requests under about 1,500 changed lines, a mid-sprint check, and the "on dev" rule in the Definition of Done | The Sprint 3 burndown falls from day one (Appendix A) |
| Sprint 3 (reconstructed) | The board's estimate field stayed empty, so the review had to rebuild the remaining hours | Each owner keeps the board estimate current; the mid-sprint check verifies it | Adopted but not kept: 47 cards carried an estimate on 12 October and none changed by 23 October |
| Sprint 4 | {{FILL: Sprint 4 retrospective finding and action — Eric (#619)}} | | |

#### Effort per member

No member logged time, so the effort figures are an agreed estimate, not a measurement. On 19 October all five members agreed a split of the effort spent: they added up the planned hours of the issues each person actually delivered and agreed the final figures. One project serves both courses, so there is one split. Table 2.10 shows it beside the planned hours of section 2.1.

Table 2.10: Effort per member: agreed estimate beside planned hours.

| Member | Planned hours, Sprints 2–4 | Agreed estimate of effort spent, person-days |
| --- | --- | --- |
| Liu Jin | 111 | 12.5 |
| Panpakorn Siripanich (Pan) | 101.5 | 11.5 |
| Yu Qianshuo (Yorik) | 108 | 9 |
| Lim Jiechao | 98 | 8.5 |
| Wang Ziang (Eric) | 106 | 8.5 |
| **Team** | **524.5** | **50** |

Done hours were not logged; for Sprints 2 and 3 they were reconstructed at the sprint's freeze: 134 of 150 planned hours in Sprint 2, and 117 of 150 in Sprint 3, where the review credits partly done work (33 h remaining at the freeze; 66 h if only whole stories count). {{FILL: Sprint 4 done hours and the method used to reconstruct them — Eric (#619)}} This report gives no pull request, commit or line counts per member: card sizes vary widely, so such counts do not measure effort.

#### Delivery and flow

- **Backlog:** the daily board snapshot of 25 October holds 219 items: 158 Done, 41 Backlog, 14 Ready, 5 In progress and 1 with no status set.
- **Throughput:** 425 pull requests were merged into `dev` between the start of the project and 26 October.
- **Burndowns:** the Sprint 2 and Sprint 3 burndowns were reconstructed from pull request merge dates and issue close dates, because no hours were logged. Appendix A shows both and states the method.
- **Delivery metrics:** the dev delivery proxies for the four DORA metrics (snapshot of 22 October) and the CI timings are in Appendix D.
