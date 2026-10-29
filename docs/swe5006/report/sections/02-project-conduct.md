<!-- report
rubric: 2.1, 2.2, 2.3
owner: Lim Jiechao (Eric: Sprint 4 data; Pan: artefacts)
plan: #676
sources: docs/agile/sprint-0N/; docs/agile/definition-of-done.md; deck s4–s7
owns: R07, R12, R26, R32, R45, R46
status: draft
-->

# 2 Project Conduct

## 2.1 Project Plan

The team planned at two levels. The proposal set a project-level work breakdown of about 50 person-days, roughly ten per member, over four two-week sprints. Each sprint plan then estimated its stories in hours and named one owner per story, the person who writes the code. Every member owns the analysis and design of at least one use case and one design problem (Table 2.3).

#### Work breakdown and baseline

The proposal (section 7.1) breaks the project into nine tasks, each with a lead and an estimate in person-days: (1) project setup, Pan, 2; (2) analysis and design, Eric, 6; (3) infrastructure, Pan, 7; (4) DevSecOps pipelines, Pan, 5; (5) backend domain, Liu Jin, 14; (6) frontend single-page application, Lim, 8; (7) end-to-end tests, Lim, 2; (8) agile process, Lim, 3; (9) documentation, Yorik, 3. The leads own their tracks; the effort is spread across all five members.

#### Sprints

Table 2.1 lists the four sprints with their goals and planned capacity. The dates follow the progress reports and the team's Definition of Done page (`docs/agile/definition-of-done.md`); the sprint container issues and the sprint calendar in `docs/agile/README.md` carry slightly different dates (for Sprint 1, 2 to 12 September; for Sprint 2, 14 to 25 September). Table 4.3 dates the sprint tags.

Table 2.1: Sprints, goals and planned capacity.

| Sprint | Dates | Goal | Planned capacity |
| --- | --- | --- | --- |
| 1 | 1–12 Sep | A minimal end-to-end slice: sign in with Google, browse the listing list and detail; every member claims at least one use case | Not recorded |
| 2 | 15–26 Sep | Pay, join a team, see the order, all replay-safe | 150 h, 30 h per member |
| 3 | 28 Sep–9 Oct | Invite, pay and refund on the deployed dev environment | 150 h, 30 h per member |
| 4 | 12–23 Oct | The complete system on dev; the same code as three services on ECS for SWE5001 | 224.5 h |

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

Each member owns the analysis and design of their use cases in section 3.4 and one design problem in section 3.5. The section numbers follow the order in which the team planned its design presentation; they do not rank the work. Owning a section means writing its analysis, design and pattern (code credits: chapter 5).

Table 2.3: Ownership of use cases, design problems and report sections.

| Member | Use cases (analysis and design) | Design problem and pattern | Other report sections |
| --- | --- | --- | --- |
| Wang Ziang (Eric) | UC3, UC4 and the included Pay upfront (3.4.1) | Creating a team without naming its class: Factory Method (3.5.1) | 3.2 overview, 3.2.1–3.2.4, 3.2.8; 3.3.4, 3.3.5, 3.3.11 |
| Yu Qianshuo (Yorik) | UC6 (3.4.2) | Consequences of a team's outcome: Observer (3.5.2) | 3.2.5–3.2.7, 3.2.9; 3.3.7; Appendix E |
| Panpakorn Siripanich (Pan) | UC1, UC2 (3.4.3) | Outbox transports: Template Method (3.5.3) | 3.3.2, 3.3.3, 3.3.12; chapter 4; Appendices A, D, E |
| Lim Jiechao | UC7, UC8 (3.4.4) | Pricing modes: Strategy (3.5.4) | Editor; chapters 1, 2 and 5; 3.3.1, 3.3.8, 3.3.9; Appendix B |
| Liu Jin | UC5, UC9 (3.4.5) | Listing rules by state: State with Optimistic Offline Lock (3.5.5) | 3.1, 3.3.6, 3.3.10, 3.6; Appendices B, C, D |

The proposal (section 6.1) gave an indicative allocation: UC1 with Strategy and UC2 to Pan; UC3 with Factory Method and UC4 to Eric; UC5 with State and Observer (publisher side) and UC9 to Liu Jin; UC6 with Adapter to Yorik; UC7 with Observer (subscriber side) and UC8 to Lim. The use case owners are unchanged. The design problems changed for four of the five members, decided on 21 October; only Eric's is unchanged. The Stripe payment abstraction became gateway ports under a transition strategy (section 3.2.5), so Yorik took Observer, now applied to the consequences of a team's outcome in UC5 and UC6; Strategy for UC1 pricing went from Pan to Lim; and Pan took Template Method for the outbox transports. State moved from the team lifecycle to the listing lifecycle (sections 2.2 and 3.5.5).

#### Roles

Table 2.4: Scrum roles per sprint.

| Sprint | Scrum Master | Sprint goal drafted by |
| --- | --- | --- |
| 1 | Lim Jiechao, recorded after the sprint, on 28 September | Liu Jin |
| 2 | Wang Ziang (Eric), recorded after the sprint, on 28 September | Lim Jiechao |
| 3 | Lim Jiechao, 10 h planned | Liu Jin |
| 4 | Wang Ziang (Eric), 4 h planned; proposed in the sprint plan, confirmation not recorded | Liu Jin |

The backlog was prioritised without a Product Owner (no Product Owner, section 1.3), from a sprint goal that one member drafted in the sprint's container issue. All five members acted as developers in every sprint, and code owners review each area (Table 4.4).

#### Definition of Done

The Definition of Done grew sprint by sprint, each time as changes to the previous list, and reached 15 rules in Sprint 4. They cover acceptance criteria, the API contract before the controller, architecture boundaries, tests for replay and concurrency, the required checks, review, the rule that "on dev" means shown on the deployed dev environment (from Sprint 3), pull requests under about 1,500 changed lines, remaining hours on the board, and four rules for the two-course Sprint 4. Appendix A lists all 15 with the sprint each started.

The course gives a ladder of done steps: coded, tested, integrated, releasable, released (EPAT 01 Agile, p. 26). On 24 October the team agreed how it reads the upper three steps:

- **integrated:** merged to `dev` with every required check green;
- **releasable:** shown working on the deployed dev environment, which is lower than the course's "production ready";
- **released:** promoted to production through the gated release path and verified there by the smoke suite. Production is the shared SWE5001 release on ECS, with Stripe in test mode and no outside users.

Section 2.3 places each sprint on this ladder.

## 2.2 Project Status

BlocBuy delivers everything the proposal promised in function, and more: all nine use cases plus an admin console. All nine are shown on dev (Table 1.3). Seven design or platform choices changed during the project, and one deliverable was dropped (no SWE5006 production stack, section 1.4). Table 2.6 lists the open issues. This snapshot was read on 29 October 2026, at the freeze commit (section 1.1).

#### Promised against delivered

Table 2.5 compares the proposal with what was built. A change cites its decision record where one exists. Four of the seven changes have no ADR, and the session change has only a frontend ADR; their reasoning is in design documents and plans, and Appendix B lists them as decisions without an ADR.

Table 2.5: Promised against delivered, 29 October 2026.

| Item | Promised (proposal) | Status | What was built, and the decision record |
| --- | --- | --- | --- |
| Use cases UC1–UC9 | Nine use cases (section 6.1) | Delivered | All nine built, tested and shown on dev (Table 1.3) |
| Crash recovery of refunds | Refunds safe under replay and crash | Open | Simulated recovery tested locally and in CI (not shown on dev; Table 1.3) |
| UC9 administration | API-only listing CRUD plus database seeding | Changed | An admin console for administrators, built in Sprint 3 (frontend ADR 0015) |
| Sign-in session | Google sign-in, with a token (JWT) kept in browser local storage | Changed | Google sign-in unchanged; the server keeps a Sa-Token session in Redis and the browser holds only an HttpOnly cookie (frontend ADR 0002; decided on 30 August, before the first sprint; no backend ADR). Reasons: Table B.4 |
| Payment abstraction and team resolution | Adapter for Stripe (UC6); Observer for team resolution (UC5, UC7) | Delivered | Payment providers sit behind the domain's `CheckoutGateway` and `RefundGateway` ports, implemented by Stripe adapters (section 3.2.5); a team's resolution is published as events to ordered subscribers (backend ADRs 0002, 0003). Observer, not Adapter, is UC6's design problem (section 3.5.2) |
| Team lifecycle | State pattern for the team | Changed | A transition table for the team; State applied to listings instead (reasoning in section 3.5.5). No ADR |
| Persistence | Spring Data JPA | Changed | MyBatis behind repository ports, decided on 30 August, before the first sprint. No ADR. Reasons: Table B.4 |
| Dev environment | Amazon ECS Fargate and RDS | Changed | One EC2 host with Docker Compose and PostgreSQL on the host (backend ADR 0008) |
| Secrets | AWS Secrets Manager; no static credentials anywhere | Changed | SSM SecureString parameters and a root-only environment file on the host (CI authentication, section 4.1); one IAM access key remains (open issues in Table 2.6). No ADR |
| Pipeline security | Blocking OWASP Dependency-Check and infrastructure-as-code scans; three plan, deploy and apply roles | Changed | Trivy dependency and configuration scans run on pull requests but are not required checks; three per-job OIDC roles (image push, backend deploy, frontend deploy). No ADR |
| Production release | A first production release with a rehearsed rollback | Dropped | No separate SWE5006 production stack (section 1.4); production is the shared SWE5001 release on ECS (promoted to production, section 2.3). Backend ADR 0008 records the two topologies |
| Test and delivery evidence | End-to-end tests on dev; a burndown; saved-plan infrastructure promotion | Partial | Playwright end-to-end tests run against API stubs (frontend ADR 0011); the proposal's burndown charts generated by a scheduled pipeline were not built, and Appendix A reconstructs them afterwards; saved-plan Terraform promotion is a manual operator step |
| Quality gates | SonarCloud quality gate | Delivered beyond | Both SonarCloud gates (backend and frontend) and Checkstyle (inside `build-quality`) are required checks on every pull request (the six required checks, Table 4.2) |
| Notifications | Not in section 6.1 | Added (SWE5001) | In-app notifications for SWE5001 (off on dev, section 1.4) |

#### Outstanding issues

Table 2.6 lists the issues still open on 29 October. Appendix E lists every known limit of the dev environment with its evidence.

Table 2.6: Outstanding issues, 29 October 2026.

| Issue | Owner | State |
| --- | --- | --- |
| Crash recovery of refunds under replay (not shown on dev; Table 1.3) | Yorik | Open; rescoped on 11 October to a record from local evidence |
| One combined lock order for ADR 0001 and ADR 0002 is not yet written (locks in the order of Table 3.12, section 3.2.8) | Unassigned | Open |
| CloudFront reaches the dev load balancer over HTTP (NFR-37) | Pan | Accepted risk on dev until 16 November |
| SSH port 22 open to any address on the dev host's security group, although deploys and port forwarding use SSM and need no SSH | Pan | Closed on 27 October. Open in the AWS read-only checks of 21 and 24 October; the team decided on 24 October to close it; the check of 27 October found no rule admitting port 22 |
| Dev EBS volume not encrypted | Pan | Seen in the AWS read-only check of 19 October and still unencrypted on 27 October; EBS encryption by default is off; not raised as an issue |
| Two IAM users in the project account, one with an active access key, for port forwarding to the dev host | Lim | Disclosed and accepted in the AWS account governance record; the key is limited by its port-forwarding policy and by every service control policy of the organisation; no removal planned. Neither user has an MFA device (read-only check of 27 October) |
| Backend rollback never exercised on dev: the rollback workflow has never run | Unassigned | Open |
| The weekly full-history secret scan fails on four historical badge-token findings (NFR-02) | Unassigned | Open |
| Passive OWASP ZAP scan of dev: Medium and Low header findings (fixed; Table 4.10) | Pan | Medium and Low fixed; Informational findings under review |
| Exposure of the actuator, springdoc and error paths outside the Sa-Token interceptor not decided | Eric | Open |
| After a merge, delivery rebuilds the backend image without repeating the pull request's Trivy image scan, so the deployed image is not the scanned one | Unassigned | Known limit |
| Database and application servers share subnets in the SWE5001 infrastructure code (NFR-70) | Pan | Open; SWE5001 scope |
| Sprint 3 retrospective was reconstructed on 24 October, and the Sprint 3 review is still a draft | Lim | Open |
| Sprint 4 review, retrospective and burndown were not recorded in the sprint, although their tracking issue was closed on 26 October | Eric | Reconstructed as at 29 October in Appendix A (review and retrospective in A.5, burndown in Table A.7); no meeting recorded and not reviewed by the team |

Two proposal targets are not shown on dev: response time (performance status, section 1.4.3; results in Table E.4) and one-minute expiry (the expiry design bound, section 1.4.3).

#### Remarks from the presentation

The lecturer gave three remarks at the presentation on 26 October; the team recorded each as an issue. Table 2.7 lists each remark, its state and how the report answers it, as read on 29 October.

Table 2.7: Remarks from the presentation of 26 October and how the report answers them.

| Remark | State | How the report answers it |
| --- | --- | --- |
| Send the repository link to the lecturer | Closed by 30 October | Read access to the private repository was granted to the lecturer and the link sent; the title page gives the URL |
| Show the OWASP ZAP results before and after remediation | Open | Section 4.2 compares the scans before and after the header fixes, finding by finding (fixed; Table 4.10) |
| Submit the report by 31 October 2026, 23:59 | Open until submission; this deadline replaced 1 November | The schedule meets it: first draft on 27 October, team vetting on 28 and 29 October, content freeze on 29 October |

## 2.3 Project Metrics

The project met every planned milestone except a separate SWE5006 production release (no SWE5006 production stack, section 1.4). Its last delivery milestone was the paid journey on dev, followed by the presentation (Table 2.8). No member logged hours, so effort is an agreed estimate (Table 2.11).

#### Milestones achieved

<!-- table: split -->

Table 2.8: Milestones achieved.

| Date | Milestone | Evidence |
| --- | --- | --- |
| 27 Aug | Proposal submitted | Proposal |
| 5 Sep | Backend delivery pipeline live on dev, with health check and rollback | Sprint 1 progress report |
| 14 Sep | Sprint 1 tag `v0.1.0-sprint1` | Git tag |
| 20 Sep | Stripe Sandbox acceptance, tested locally | Sandbox acceptance record of 20 September |
| 22 Sep | Web application live on dev | Sprint 2 container issue |
| 27 Sep | Sprint 2 tag `v0.1.0-sprint2` | Git tag |
| 30 Sep | Admin console merged | Admin console pull requests |
| 6 Oct | First successful production deployment on ECS (shared SWE5001 release), the monolith as one service | Deployment pull request and workflow run |
| 11 Oct | Sprint 3 tag `v0.1.0-sprint3` | Git tag |
| 17 Oct | First promotion of the three-service release to production on ECS (shared SWE5001 release) | Promotion pull request and workflow run |
| 19 Oct | Second promotion of the three-service release to production on ECS | Promotion pull request and workflow run |
| 20 Oct | Last production promotion before the presentations, smoke suite green | Promotion pull request and workflow run |
| 23 Oct | Paid start and the automatic expiry refund shown on dev | Dev recovery acceptance record (Table E.3) |
| 24 Oct | A second buyer's paid join shown on dev, in a recording | Recording (Appendix E) |
| 24 Oct | Sprint 4 tag `v0.1.0-sprint4` | Git tag |
| 26 Oct | SWE5006 presentation, with the system demo run live on dev | Presentation tracking issue |

Tag dates are the tagger dates. The repository has no GitHub milestones or releases.

#### Definition of Done reached per sprint

Table 2.9 places each sprint's goal increment on the done ladder of section 2.1, using the team's reading of its upper steps.

Table 2.9: Done step reached per sprint.

| Sprint | Step reached | Basis |
| --- | --- | --- |
| 1 | Integrated | Backend on dev; no frontend yet, so not releasable |
| 2 | Integrated | 9 of 11 stories merged and tested, 134 of 150 h; Checkout and scheduling off on dev |
| 3 | Integrated | Merged and tested; the goal was not met on dev under rule 8 of the Definition of Done (Table A.10), which does not count a local run; 117 of 150 h |
| 4 | Released (shared SWE5001 production), and shown on dev | Promoted to production on ECS as three services with the smoke suite green; on dev, paid start, the expiry refund and a second buyer's paid join shown (Table 1.3) |

In Sprints 2 and 3 the money path worked locally, but local runs do not count as on dev.

#### Retrospective improvements

Each retrospective chose an improvement that changed the next sprint. Table 2.10 shows one per sprint and what it changed.

Table 2.10: Retrospective improvements and their effect.

| Retrospective | Finding | Change in the next sprint | Effect |
| --- | --- | --- | --- |
| Sprint 1 | No burndown and no hours were kept | Dated day logs and a scheduled daily board snapshot from 17 September | Every later sprint has a day log and daily board data |
| Sprint 2 | One pull request of about 10,000 lines; a flat burndown followed by a cliff | Pull requests under about 1,500 changed lines, remaining hours in the board daily, a mid-sprint owner check and verification in the first half; the Sprint 3 Definition of Done added the 1,500-line, hours and "on dev" rules | The Sprint 3 burndown falls from day one (Appendix A) |
| Sprint 3 (reconstructed) | The board's estimate field stayed empty, so the review had to rebuild the remaining hours | Each owner keeps the board estimate current; the mid-sprint check verifies it | Adopted but not kept: 47 cards carried an estimate on 12 October and none changed by 23 October |
| Sprint 4 (reconstructed) | Cards were not closed when their work was done: fifteen issues were still open on 23 October and the burndown stayed at 96.5 h from 19 October, although the SWE5001 presentation had run on 21 October. The Sprint 3 action on estimates was not kept either: none of the 47 estimates changed | Close a card, or split off what remains, on the day its evidence is committed, and write the review and retrospective before the next planning | Not tested: Sprint 4 was the last sprint, so there was no later sprint in which to apply it |

#### Effort per member

No member logged time, so the effort figures are an agreed estimate, not a measurement. On 19 October all five members agreed a split of the effort spent: they added up the planned hours of the issues each person actually delivered and agreed the final figures. One project serves both courses, so there is one split, about 50 person-days in total, and it is not compared with the proposal's SWE5006 baseline. Table 2.11 shows it beside the planned hours of section 2.1.

Table 2.11: Effort per member: agreed estimate beside planned hours.

| Member | Planned hours, Sprints 2–4 | Agreed estimate of effort spent, person-days |
| --- | --- | --- |
| Liu Jin | 111 | 12.5 |
| Panpakorn Siripanich (Pan) | 101.5 | 11.5 |
| Yu Qianshuo (Yorik) | 108 | 9 |
| Lim Jiechao | 98 | 8.5 |
| Wang Ziang (Eric) | 106 | 8.5 |
| **Team** | **524.5** | **50** |

Done hours were not logged; for Sprints 2 and 3 they were reconstructed at the sprint's freeze: 134 of 150 planned hours in Sprint 2, and 117 of 150 in Sprint 3, where the review credits partly done work (33 h remaining at the freeze; 66 h if only whole stories count). Sprint 4 was reconstructed from the daily board snapshots by the Sprint 3 method: the planned hours of each backlog item with an issue (214.5 of the 224.5 h) count as done on the snapshot where its issue is first closed, and the 10 h of evidence work, which has no issue, is left out. On 23 October 118 h were done on that count. The SWE5001 presentation (13 h) was delivered on 21 October but its issue stayed open, so it is added by hand: 131 of 214.5 h (61 %). By 27 October the count was 138 h, which includes the dev payment rollout (7 h), closed on 25 October. Three SWE5001 evidence issues are not counted: their day logs say live evidence was still outstanding. We give no pull request, commit or line counts per member: card sizes vary widely, so such counts do not measure effort.

#### Delivery and flow

- **Backlog:** the board is snapshotted daily (board snapshot, Appendix A.1).
- **Throughput:** 426 pull requests were merged into `dev` between the start of the project and the end of 26 October.
- **Burndowns:** the Sprint 2 and Sprint 3 burndowns were reconstructed from pull request merge dates and issue close dates, because no hours were logged. Appendix A shows both and states the method.
- **Delivery metrics:** the dev delivery proxies for the four DORA metrics (snapshot of 22 October) and the CI timings are in Appendix D.
