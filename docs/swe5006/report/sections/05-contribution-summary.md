<!-- report
rubric: 5, G4
owner: Lim Jiechao (each member checks their row)
plan: #681
sources: docs/agile/sprint-0N/log/; ../report-drafts/*-contribution.md
owns: R23
status: draft
-->

# 5 Individual members activity contribution summary

Every member owns the analysis and design of at least one use case and one design problem solved with a design pattern, and each wrote other report sections and system work besides. Table 5.1 summarises each member's activity; the cross-credit lines after it name the teammates whose code lies behind each owned section. The Remarks column names whose code lies behind the member's sections; agreed effort is in section 2.3 (effort, Table 2.11). The table gives no pull request, commit or line counts. {{VERIFY: each member checks and confirms their own row — all five}}

Table 5.1: Individual members' activity contribution summary.

| Member | Activity | Status | Remarks |
| --- | --- | --- | --- |
| Wang Ziang (Eric) | **UC3, UC4, Pay upfront:** Analysis, Design (3.4.1). **Design Pattern:** Factory Method (3.5.1). **Report:** 3.2 overview, 3.2.1–3.2.4, 3.2.8; 3.3.4, 3.3.5, 3.3.11; order, checkout payment and refund obligation statecharts. **Roles:** Scrum Master, Sprint 2; proposed Scrum Master, Sprint 4; demo operator | Use cases on dev (Table 1.3); pattern in code with unit tests | Intent ports by Liu Jin |
| Yu Qianshuo (Yorik) | **UC6:** Analysis, Design (3.4.2). **Design Pattern:** Observer (3.5.2). **Report:** 3.2.5–3.2.7, 3.2.9; 3.3.7; Appendix E. **Roles:** presentation and demo lead | UC6 on dev; crash recovery not shown on dev (Table 1.3); pattern in code | Lifecycle events by Liu Jin |
| Panpakorn Siripanich (Pan) | **UC1, UC2:** Analysis, Design (3.4.3). **Design Pattern:** Template Method (3.5.3). **Report:** chapter 4; 3.3.2, 3.3.3, 3.3.12; Appendices A, D, E. **Roles:** CI code owner; pipeline demo | Use cases on dev; pattern in code | UC1, UC2 code by Liu Jin and Lim |
| Lim Jiechao | **UC7, UC8:** Analysis, Design (3.4.4). **Design Pattern:** Strategy (3.5.4). **Report:** editor; chapters 1, 2, 5; 3.3.1, 3.3.8, 3.3.9; team statechart; Appendix B. **Roles:** Scrum Master, Sprints 1 and 3; slide master | Use cases on dev; pattern in code | Backend and pricing code by Liu Jin and Eric |
| Liu Jin | **UC5, UC9:** Analysis, Design (3.4.5). **Design Pattern:** State with Optimistic Offline Lock (3.5.5). **Report:** 3.1, 3.3.6, 3.3.10, 3.6; Appendices B–D. **Roles:** backend code owner; production promotions | Use cases on dev; pattern in code | First UC9 and State by Eric |

#### Main system work per member

The work below is merged into `dev`; "SWE5001" marks work that runs only on the SWE5001 environment.

- **Wang Ziang (Eric):** paid team participation with the Factory Method creator (10 September) and its admission refactoring (13 October); the first version of UC9 admin listings with the listing State (11 September); the local 100-user performance baseline (30 September); a bounded best-offer query for the catalog; the notification consumer (SWE5001); SBOM, OWASP ZAP and DORA tooling; the Definition of Done page and the agile practices index; drafts of the transition strategies and of the order, checkout payment and refund obligation statecharts. In the presentation Eric presented the architecture, the transition strategies and the UC4 models, and operated both browser profiles in the system demo.
- **Yu Qianshuo (Yorik):** the Stripe Checkout and refund slice (22 September); scheduled team-expiry refunds (25 September); the transactional integration outbox and its SQS and SNS transports (SWE5001); the identity outage and dead-letter queue drill (SWE5001); the repair that restored settlement and refunds on dev (24 October). Yorik led the SWE5006 presentation and the system demo, and presented the Observer problem.
- **Panpakorn Siripanich (Pan):** CI quality and security gates (merge queue, frontend SonarCloud gate, Gitleaks and Dependabot policy, release gates); lease renewal and hardening of the scheduler's `LeasedJobRunner` (24 September); board snapshot integrity; the Terraform import of the dev frontend edge (25 September); admin console backend requests; listing deletion eligibility (1 October); the batching outbox transport; notification persistence and its list endpoint, session roles outside identity and messaging infrastructure (SWE5001); static Terraform checks for the SWE5006 stacks; coverage clean-ups. Pan cut the Sprint 1, 2 and 4 tags and presented the CI/CD pipeline demo.
- **Lim Jiechao:** the frontend (toolchain and `pnpm check` gate, generated API client, browse, sign-in, quote, My Orders and My Teams, sharing and invite pages, admin console); the frontend architecture decision records; the team statechart; the AWS Organization, IAM Identity Center and service control policies; the shared presentation kit. Lim led the SWE5001 presentation, was slide master of the SWE5006 deck and presented its project management section.
- **Liu Jin:** the COLA backend skeleton; the Google sign-in backend; catalog browse; the order system of record and the purchase-intent and settlement ports (23 September); team-lifecycle events and expiry refunds (backend ADR 0002); the leased scheduler, `LeasedJobRunner` (24 September); the My Teams endpoint and the share-attribution backend (backend ADR 0003); admin team terms (29 September); the split into three services and the production promotions of 17 and 20 October (SWE5001); the outbox relay integration. Liu Jin cut the Sprint 3 tag and presented the State problem, the database schema and the types of testing.

#### Cross-credit lines

Each line names the teammates who wrote code behind a section that another member owns. The section owner wrote its analysis, design and pattern text.

- **UC1 and UC2 (3.4.3, Pan):** the browse backend and the Google sign-in backend by Liu Jin; the frontend pages by Lim; Eric extended the shared pricing seam for team quotes. Pan later added the role list to the current-user response (30 September) and wrote `LoginSessionPublisher`, which stores the session's roles before the browser gets its cookie (13 October).
- **UC3 and UC4 (3.4.1, Eric):** the purchase-intent and settlement ports by Liu Jin. Patterns that touch these use cases belong to others: the Observer to Yorik (3.5.2), Template Method to Pan (3.5.3) and Strategy to Lim (3.5.4); the transactional outbox writer and relay are Yorik's. The Idempotent Receiver of the notification consumer is Eric's own, outside UC3 and UC4.
- **UC6 and Observer (3.4.2 and 3.5.2, Yorik):** the domain events `TeamSucceeded` and `TeamExpired` and the expiry refund subscriber by Liu Jin; their integration contracts `TeamSucceededV1` and `TeamExpiredV1` by Yorik.
- **UC7 and UC8 (3.4.4, Lim):** the backend by Liu Jin: the two owner-scoped list endpoints (My Orders and My Teams), invite minting and resolving, and the share-credit subscriber; the Redis cache in front of invite lookups by Yorik (5 October); the rate limit on invite minting by Eric (3 October); the frontend pages by Lim.
- **UC9 and State (3.4.5 and 3.5.5, Liu Jin):** the first version, including the listing State, its versioned writes and the 412 stale-version rule, by Eric (11 September), with Liu Jin reviewing; Pan changed the states on 1 October to expose deletion eligibility, replacing `requireDeletable` with `deletionError`; admin team terms and the first-team price in one command by Liu Jin (29 September); the admin console screens by Lim (from 30 September).
- **UC5 (3.4.5, Liu Jin):** the expiry sweep and its scheduler job by Liu Jin (24 September), hardened by Pan; the last-seat admission (`GroupDeal.joinPaid`, `PaidGroupDealService.joinConfirmed`), shared with UC4, by Eric (9 September), with the `TeamSucceeded` publication added by Liu Jin (23 September).
- **Template Method (3.5.3, Pan):** `BatchingOutboxEventTransport` and the review fix by Pan (13 October); the outbox relay and the original SQS and SNS transports by Yorik; Liu Jin opened the pull request that brought the relay onto `dev` (13 October) and Pan merged it.
- **Strategy (3.5.4, Lim):** the backend pricing code by Liu Jin, with UC1 browsing (8 September: `PricingStrategy`, `DualPricingStrategy`, `PricingStrategyResolver`, `PricingMode`), and by Eric, who added the first-team quote `quoteTeamPrice` and its client `TeamPriceAppServiceImpl` (9 September).
- **Pairing:** Yorik and Liu Jin paired on one branch and one pull request to read admin attention nicknames through the identity port (14 October).
