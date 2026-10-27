<!-- report
rubric: 1.1, 1.2, 1.3, 1.4, 1.4.1, 1.4.2, 1.4.3
owner: Lim Jiechao
plan: #675
sources: PROP §4, §6.1–6.3; deck s2–s4, s12; docs/quality/nfr-register.md
owns: R01, R02, R03, R04, R05, R06, R08, R09, R19, R34, R40, R49, R50, R51
status: draft
-->

# 1 Introduction

## 1.1 Background

![BlocBuy logo: a striped shopping bag beside the wordmark BLOCBUY and the tagline ONE FOR ALL • ALL FOR ONE](../figures/blocbuy-logo.svg){.logo}

BlocBuy is a group-buying web application for Singapore that makes it safe for a buyer to commit first. Small merchants can offer a discount only against committed volume, and no single buyer can reach that volume on their own: demand exists, but no buyer commits first.

Today such groups form in chat apps and spreadsheets. Buyers cannot see how close a deal is to its target, an organiser collects the money and refunds it by hand, and small sellers have nowhere to post a team price. BlocBuy replaces the organiser with a pay-first mechanism, modelled on the early Pinduoduo playbook (project proposal, section 4, 27 August 2026):

- Every **listing** (a product on sale; buyers see only listings that are on sale) shows two prices: the individual price and a lower team price. Its **team terms**, a team size and a team lifetime, are copied by every team started on it.
- A buyer starts a **team** (shown to buyers as a *bloc*): one group purchase of one listing at a fixed team price, with a target size and a deadline. The buyer pays upfront and then shares an invite link.
- Other buyers join the team by paying upfront too. This is **pay first**: every buyer pays the team price on starting or joining, before the team is known to succeed.
- A team **succeeds** if it fills before its deadline, and every member gets the team price. Otherwise it **expires**, and every member is refunded automatically.

In the code, a listing is the `Product` aggregate (table `biz_product`) and a team the `GroupDeal` aggregate (table `biz_group_deal`). All payments run through Stripe in test mode (the Stripe *Sandbox*); no real money moves. Because money is committed before the team is known to succeed, most of the design is about money safety under concurrency and failure: no team takes more members than it has seats, and no payment is charged or refunded twice.

The same repository serves two NUS-ISS courses. This report describes the SWE5006 system: the `dev` environment, which runs BlocBuy as one modular monolith on a single Amazon EC2 host behind CloudFront. The SWE5001 course runs the same code on Amazon ECS, and that deployment is the project's shared production release (SWE5001 topology, section 1.4).

At the time of writing (26 October 2026) all four planned sprints are complete (sprint dates, Table 2.1), and all nine use cases of the proposal are built and shown on dev; Table 1.3 gives the status of each. Section 2.2 gives the full status against the proposal and the outstanding issues. {{VERIFY: re-read this status paragraph at the freeze SHA on 30 Oct (rubric G7) — Lim}}

## 1.2 Business Needs

BlocBuy serves three needs, one per stakeholder group:

- **Merchants** want to sell at a discount only against committed, prepaid volume, so that the discount pays for itself.
- **Buyers** want to reach a bulk price together without trusting an organiser with their money.
- **The platform** must work without manual collection or refunds, and every payment, refund and team transition must be auditable.

Each use case answers a specific driver. UC1 shows both prices so that buyers see the saving. UC3 makes the leader pay first, so a team exists only once real money backs it. UC4 lets a buyer take the last seat safely, even when several buyers try at once. UC5 and UC6 resolve a team at its deadline and refund every member automatically, once and traceably. UC7 attributes each join to the buyer who shared the invite link.

The proposal (section 6.3) also names maintainability and extensibility as the primary design drivers: "new pricing schemes, payment providers, and team-resolution consequences must be addable without modifying existing domain logic". Section 3.5 shows how the design patterns serve that driver.

The project has no market sizing or business success metric. The proposal states none, and no market figures were collected during the project, so none are given here.

## 1.3 Stakeholders

The stakeholders are the people and systems that use BlocBuy or decide its scope. Development tools such as the automated reviewers and Dependabot are not stakeholders; section 4.1 describes them.

<!-- table: split -->

Table 1.1: Stakeholders and how the project serves them.

| Stakeholder | Kind | Interest | How the project serves it |
| --- | --- | --- | --- |
| Buyer as team leader | Business, primary actor | Start a team at the team price without risking money | Pay upfront to start (UC3), share an invite link (UC7), automatic refund on expiry (UC6) |
| Buyer as joiner | Business, primary actor | Join a team safely, including through a friend's invite | Pay to join (UC4); the last seat goes to exactly one buyer; refund on expiry (UC6) |
| Small merchant | Business, indirect | Sell at a discount only against committed volume | Teams fill only with paid members. There is no merchant interface: merchant self-service is out of scope (section 1.4.2), and an administrator sets up the listings |
| Platform administrator | Business and IT, primary actor | Manage listings and team terms | Admin listing management (UC9) through an admin console, guarded by role-based access |
| Stripe | External system, secondary actor | Hosted Checkout, refunds and signed webhooks | The pay upfront use case and the refunds of UC6, in test mode only |
| Google | External system, secondary actor | Sign-in identity | Google sign-in for UC2 |
| Scheduler (time) | System actor | Resolve teams whose deadline has passed | A scheduled sweep that expires overdue teams and starts their refunds (UC5, UC6) |
| Development team | IT | Deliver the system and its evidence for two courses | Five members, each owning use cases, a design problem and report sections (section 2.1); one member per sprint acts as Scrum Master |
| Lecturer and project advisor (Chandrasekaran) | Academic | Agreed the scope in the proposal; grades the presentation and this report | The proposal as the scope benchmark (section 2.2) and this report |

The project has no company sponsor; the proposal lists none. No Product Owner was appointed: the team prioritised the backlog together at each Sprint Planning (section 2.1).

## 1.4 Project Scope

The scope is the nine use cases of the proposal (section 6.1), with two later additions. An admin console goes beyond the proposal's "API-only" listing administration for UC9. In-app notifications were added in Sprint 4 for SWE5001. They are coded but switched off on SWE5006 dev (backend ADR 0007): the dev profile turns off the outbox writer, the relay and the notification consumer by default (`blocbuy.events.enabled`, `blocbuy.events.relay.enabled` and `blocbuy.events.consumer.enabled` in `application-ec2.yml`), and no message broker runs there, so the SNS and SQS transports never run on dev. Notifications are therefore SWE5001 scope. Section 1.4.1 lists the functionality in scope with its status, section 1.4.2 what is out of scope, and section 1.4.3 the quality attributes. The use case diagram in section 3.3.1 shows both.

The project ran in four two-week sprints, which serve as its phases. Table 1.2 lists what each sprint delivered; section 2.1 gives the sprint dates, goals and planned effort, and section 2.3 the milestone dates and the Definition of Done step each sprint reached.

Table 1.2: Phases of the project and their delivered increments.

| Phase | Delivered increment |
| --- | --- |
| Sprint 1 | Backend skeleton and delivery pipeline on dev; Google sign-in, listing browse and admin listing APIs |
| Sprint 2 | Stripe Checkout, paid start and join, orders, team expiry and refunds, all replay-safe and tested locally; the web application on dev |
| Sprint 3 | Invite links that name the sharer, admin team terms, the admin console, a local performance baseline; Checkout still off on dev; for SWE5001, the monolith deployed to production on ECS |
| Sprint 4 | Checkout, scheduling and refunds switched on for dev; three services on ECS for SWE5001 (promoted to production, section 2.3) |

The scope is documented in three places: the proposal (sections 6.1 to 6.3), the backend and frontend architecture decision records (indexed in Appendix B), and the out-of-scope decisions in the Sprint 4 plan.

What was architected and what was implemented differ in four places:

- **Implemented and deployed for SWE5006:** the modular monolith on the EC2 dev host (backend ADR 0008).
- **Also implemented, outside SWE5006 dev:** the three-service topology on Amazon ECS. It is the SWE5001 deployment and the shared production release.
- **Designed but not implemented:** a separate SWE5006 production stack on EC2. Its Terraform stack (`infra/terraform/backend-app-stack`) exists, and a read-only plan of 25 September showed 21 resources to create, but it was never applied; its story was closed as not planned on 11 October (Table 1.4), and on 25 October the team ruled that the shared SWE5001 release is the production environment of both courses.
- **Declared but never written:** the team states `FAILED` and `CANCELED` (Figure 3.113).

The SWE5001 topology runs the same code on Amazon ECS as three services: `identity-svc` (the identity context), `commerce-svc` (catalog, group-buy, order and payment) and `notification-svc` (notification). The split follows transactional consistency: contexts that share a transaction stay in one service, so the four commerce contexts stay together. Commerce reaches identity through one internal HTTP call and reaches notification only through integration events (backend ADRs 0007 and 0008). Each service has its own database on one shared RDS instance and is granted only its own tables. The services run on ECS Fargate behind CloudFront and a load balancer, with RDS PostgreSQL, ElastiCache and SNS and SQS messaging in private subnets.

### 1.4.1 Functionality in Scope

All nine use cases of the proposal and the included use case Pay upfront via Stripe Checkout are delivered and shown on dev. Table 1.3 gives their status on 26 October, which matches the delivery badges of the use case diagram (section 3.3.1). {{VERIFY: re-read each status at the freeze SHA on 30 Oct — Lim}}

Table 1.3: Use cases in scope and their status on dev.

| Use case | Status on dev, 26 Oct | Notes | Issue closed |
| --- | --- | --- | --- |
| UC1 Browse and view listings, with dual price | On dev | Includes keyword search | 19 Sep |
| UC2 Register / authenticate | On dev | Google sign-in only, with a server-side session (changes from the proposal: Table 2.5) | 23 Sep |
| UC3 Initiate a team (pay first) | On dev since 23 Oct | A real Sandbox payment started a team and created its order | 24 Sep |
| UC4 Join a team | On dev since 24 Oct | Shown on dev on 24 October in a recording (Appendix E): a second buyer's paid join filled a team of two. The recording shows the screens; the database and logs were not read | 24 Sep |
| UC5 Team lifecycle resolution | On dev since 24 Oct; expiry since 23 Oct | Expiry: a paid team expired after its deadline, shown on dev on 23 October (Table E.3). Success: the team of two in the UC4 recording. The diagram badge carries 24 October, when both outcomes had been shown | 24 Sep |
| UC6 Refund on team expiry | On dev since 23 Oct | Shown on dev on 23 October (Table E.3): one expired team was refunded once. Crash recovery under replay is tested locally only and not shown on dev | 25 Sep |
| UC7 Share attribution | On dev | The UC4 recording shows the invite banner naming the sharer | 28 Sep |
| UC8 View my orders and teams | On dev | | 27 Sep |
| UC9 Admin listing management | On dev | Extended beyond the proposal with team terms and an admin console | 19 Sep |
| Pay upfront via Stripe Checkout («include» of UC3 and UC4) | On dev since 23 Oct | Stripe test mode only | With UC3 and UC4 |
| Receive notifications (SWE5001 scope) | Coded, off on dev | SWE5001 scope (section 1.4); no analysis and design models in section 3.4 | 13 Oct |

The order system of record is a backend story, not a use case; it is described inside UC3 and UC8.

### 1.4.2 Functionality out of Scope

The proposal (section 6.2) excluded seven capabilities, listed here close to its words and in the order of the out-of-scope package on the use case diagram:

1. Merchant self-service GUI (listing administration is API-only CRUD plus database seeding, see UC9).
2. Analytics and reporting dashboards.
3. Recommendation and personalisation.
4. Automatic team matching and virtual team completion.
5. Notifications beyond in-app status (no e-mail, SMS or push).
6. Mobile applications (the single-page application is mobile-responsive; no native apps).
7. Live payment processing (Stripe test mode only).

The admin console does not breach the first exclusion: only platform administrators use it, not merchants. The fifth exclusion removes notifications beyond in-app status, not in-app notifications, so the in-app notifications added for SWE5001 do not breach it either.

Table 1.4 lists the items the team dropped during the project. All were closed as not planned on 11 October 2026, when the Sprint 4 plan cut work that neither course required, so that its time went to the SWE5006 report and the SWE5001 platform.

Table 1.4: Items dropped during the project.

| Item dropped | Reason |
| --- | --- |
| A separate SWE5006 production release on EC2, with a rollback drill | Production is the shared SWE5001 release on ECS, with gated promotion and a deployment circuit breaker (promoted to production, section 2.3) |
| Signed-in end-to-end journey on dev | Waited on Checkout in dev; end-to-end tests run against API stubs instead (frontend ADR 0011) |
| Notification bell in the header | Notifications serve SWE5001 only; one list endpoint is enough there |
| Invite credits view for the sharer | Attribution is recorded (UC7); a page reporting it was not needed by either course |
| Deployed performance tuning | SWE5006 cites the local baseline; the SWE5001 load test covers ECS |
| Remaining admin console polish and design gaps | The console's core screens had merged on 30 September; one design gap (the team terms panel) was completed before the cut |

RabbitMQ and Elasticsearch are excluded from the technology stack by a team decision of 30 August 2026; cross-service events use a transactional outbox with Amazon SNS and SQS instead (backend ADR 0007).

### 1.4.3 Quality Attributes

The proposal (section 6.3) commits to six quality attributes, each with a target. Table 1.5 states each target, where the report verifies it, and its status in short. The notes below the table qualify two of the statuses, and section 2.2 lists the open gaps. The full non-functional register, NFR-01 to NFR-74 with their controls and evidence, is kept in the repository (`docs/quality/nfr-register.md`, last checkpoint 14 October).

Table 1.5: Quality attributes, targets and status.

| Attribute | Proposal target | Verified in | Status, 26 Oct |
| --- | --- | --- | --- |
| Correctness under concurrency | Team capacity never exceeded; no payment double-charged or double-refunded | 3.2.8, 3.4.1, 4.2 | Tested locally, not on dev (Table 4.12) |
| Performance | Interactive operations within 2 s at 100 concurrent users; expiry resolved within 1 minute of the deadline | 4.2 (test), 2.2 (result) {{CONFIRM: D20 Should Table 1.5 point straight to Table E.4 for the performance results and drop the 2.2 pointer, since 2.2 only points back here and the numbers are in 4.2 and Table E.4? — Lim}} | Response time never measured on SWE5006 dev (NFR-15); met locally and on the SWE5001 ECS environment. Expiry: a design bound. Both are explained below the table |
| Security | Google sign-in; role-based admin; signed Stripe webhooks; no credentials in code | 3.2.6, 4.1, 4.2 | Implemented (open issues in Table 2.6) |
| Maintainability and extensibility | A new pricing scheme, payment provider or team consequence without editing existing domain logic | 3.5 | Seams in place (section 3.5); no second implementation has exercised them yet |
| Code quality | SonarCloud quality gate on every pull request; unit tests on the complex classes | 4.2 | Enforced: both SonarCloud gates are required checks (NFR-06) |
| Auditability | Every payment, refund and transition traceable; every artefact traceable to a commit | 3.6, 4.1 | Implemented; see sections 3.6 and 4.1 |

Two statuses need more than a table cell:

- **Expiry within one minute is a design bound, not a guarantee.** The expiry sweep runs at a fixed delay of one minute by default (`blocbuy.scheduling.team-expiry.interval`, `PT1M`), so a team can wait up to one sweep interval plus the sweep's own run time after its deadline; section 3.2.7 explains why. Tested locally on 30 September, in one run with 30 team deadlines inside a load window, the worst lag from deadline to committed expiry was 58.18 s; this is an upper bound that includes the observer's polling delay (`docs/perf/sprint3-baseline.md`). Expiry and refund were shown on dev on 23 October (Table E.3), in one run; one run is not a bound, so the target is not shown to hold on dev.
- **Performance was never measured on SWE5006 dev.** The local run of 30 September met p95 below 2 s for browse, join and My Orders at 100 users. The k6 run of 16 October ran on the SWE5001 ECS environment, as one monolith task under anonymous browse load only, not on the SWE5006 dev host.

Scalability is not a proposal target and is not claimed; the SWE5001 scaling work is outside the scope of SWE5006.
