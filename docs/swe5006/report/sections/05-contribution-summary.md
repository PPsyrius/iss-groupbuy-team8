<!-- report
rubric: 5, G4
owner: Lim Jiechao (each member checks their row)
plan: #681
sources: docs/agile/sprint-0N/log/; ../report-drafts/*-contribution.md
status: draft
-->

# 5 Individual members activity contribution summary

Every member owns the analysis and design of at least one use case and one design problem solved with a design pattern, and each wrote other report sections and system work besides. Table 5.1 summarises each member's activity; the cross-credit lines after it name the teammates whose code lies behind each owned section. The Remarks column repeats each member's agreed effort from section 2.3, an estimate rather than logged time, and names whose code lies behind the member's sections. Following a team rule, the table gives no pull request, commit or line counts. {{VERIFY: each member checks and confirms their own row — all five}}

Table 5.1: Individual members' activity contribution summary.

| Member | Activity | Status | Remarks |
| --- | --- | --- | --- |
| Wang Ziang (Eric) | **UC3, UC4, Pay upfront:** Analysis, Design (3.4.1). **Design Pattern:** Factory Method (3.5.1). **Report:** 3.2 overview, 3.2.1–3.2.4, 3.2.8; 3.3.4, 3.3.5, 3.3.11; order, checkout payment and refund obligation statecharts. **Roles:** Scrum Master, Sprint 4; demo operator | Use cases on dev, 23–24 Oct; pattern in code with unit tests | 8.5 person-days. Intent ports by Liu Jin |
| Yu Qianshuo (Yorik) | **UC6:** Analysis, Design (3.4.2). **Design Pattern:** Observer (3.5.2). **Report:** 3.2.5–3.2.7, 3.2.9; 3.3.7; Appendix E. **Roles:** presentation and demo lead | UC6 on dev, 23 Oct; crash recovery local only; pattern in code | 9 person-days. Lifecycle events by Liu Jin |
| Panpakorn Siripanich (Pan) | **UC1, UC2:** Analysis, Design (3.4.3). **Design Pattern:** Template Method (3.5.3). **Report:** chapter 4; 3.3.2, 3.3.3, 3.3.12; Appendices A, D, E. **Roles:** CI code owner; pipeline demo | Use cases on dev; pattern in code | 11.5 person-days. UC1, UC2 code by Liu Jin and Lim |
| Lim Jiechao | **UC7, UC8:** Analysis, Design (3.4.4). **Design Pattern:** Strategy (3.5.4). **Report:** editor; chapters 1, 2, 5; 3.3.1, 3.3.8, 3.3.9; team statechart; Appendix B. **Roles:** Scrum Master, Sprint 3; slide master | Use cases on dev; pattern in code | 8.5 person-days. Backend and pricing code by Liu Jin and Eric |
| Liu Jin | **UC5, UC9:** Analysis, Design (3.4.5). **Design Pattern:** State with Optimistic Offline Lock (3.5.5). **Report:** 3.1, 3.3.6, 3.3.10, 3.6; Appendices B–D. **Roles:** backend code owner; production promotions | Use cases on dev; pattern in code | 12.5 person-days. First UC9 and State by Eric |

#### Main system work per member

The work below is merged into `dev`; "SWE5001" marks work that runs only on the SWE5001 environment.

- **Wang Ziang (Eric):** paid team participation with the Factory Method creator (10 September) and its admission refactoring (13 October); the first version of UC9 admin listings with the listing State (11 September); the local 100-user performance baseline (30 September); a bounded best-offer query for the catalog; the notification consumer (SWE5001); SBOM, OWASP ZAP and DORA tooling; the Definition of Done page and the agile practices index; drafts of the transition strategies and of the order, checkout payment and refund obligation statecharts. In the presentation he presented the architecture, the transition strategies and the UC4 models, and operated both browser profiles in the system demo.
- **Yu Qianshuo (Yorik):** the Stripe Checkout and refund slice (22 September); scheduled team-expiry refunds (25 September); the transactional integration outbox and its SQS and SNS transports (SWE5001); the identity outage and dead-letter queue drill (SWE5001); the repair that restored settlement and refunds on dev (24 October). He led the SWE5006 presentation and the system demo, and presented the Observer problem.
- **Panpakorn Siripanich (Pan):** CI quality and security gates (merge queue, frontend SonarCloud gate, Gitleaks and Dependabot policy, release gates); lease renewal and hardening of the scheduler's `LeasedJobRunner` (24 September); board snapshot integrity; the Terraform import of the dev frontend edge (25 September); admin console backend requests; listing deletion eligibility (1 October); the batching outbox transport; notification persistence and its list endpoint, session roles outside identity and messaging infrastructure (SWE5001); static Terraform checks for the SWE5006 stacks; coverage clean-ups. He cut the Sprint 1, 2 and 4 tags and presented the CI/CD pipeline demo.
- **Lim Jiechao:** the frontend (toolchain and `pnpm check` gate, generated API client, browse, sign-in, quote, My Orders and My Teams, sharing and invite pages, admin console); the frontend architecture decision records; the team statechart; the AWS Organization, IAM Identity Center and service control policies; the shared presentation kit. He led the SWE5001 presentation, was slide master of the SWE5006 deck and presented its project management section.
- **Liu Jin:** the COLA backend skeleton; the Google sign-in backend; catalog browse; the order system of record and the purchase-intent and settlement ports (23 September); team-lifecycle events and expiry refunds (backend ADR 0002); the leased scheduler, `LeasedJobRunner` (24 September); the My Teams endpoint and the share-attribution backend (backend ADR 0003); admin team terms (29 September); the split into three services and the production promotions of 17 and 20 October (SWE5001); the outbox relay integration. He cut the Sprint 3 tag and presented the State problem, the database schema and the types of testing.

#### Cross-credit lines

Each line names the teammates who wrote code behind a section that another member owns. The section owner wrote its analysis, design and pattern text.

- **UC1 and UC2 (3.4.3, Pan):** the browse backend and the Google sign-in backend by Liu Jin; the frontend pages by Lim; Eric extended the shared pricing seam for team quotes.
- **UC3 and UC4 (3.4.1, Eric):** the purchase-intent and settlement ports by Liu Jin.
- **UC6 and Observer (3.4.2 and 3.5.2, Yorik):** the domain events `TeamSucceeded` and `TeamExpired` and the expiry refund subscriber by Liu Jin; their integration contracts `TeamSucceededV1` and `TeamExpiredV1` by Yorik.
- **UC7 and UC8 (3.4.4, Lim):** the My Teams endpoint and the share-attribution backend by Liu Jin; the frontend pages by Lim.
- **UC9 and State (3.4.5 and 3.5.5, Liu Jin):** the first version, including the listing State, by Eric (11 September), with Liu Jin reviewing; Pan changed the states on 1 October to expose deletion eligibility; admin team terms by Liu Jin.
- **Template Method (3.5.3, Pan):** `BatchingOutboxEventTransport` by Pan; the original SQS and SNS transports by Yorik; Liu Jin opened the pull request that integrated them and Pan merged it.
- **Strategy (3.5.4, Lim):** the backend pricing code (`PricingStrategy`, `PricingStrategyResolver`) by Liu Jin and Eric.
- **Pairing:** Yorik and Liu Jin paired on one branch and one pull request to read admin attention nicknames through the identity port (14 October).
