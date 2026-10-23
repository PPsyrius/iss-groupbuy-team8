# SWE5001 final report: framework

Team 8's BlocBuy project report for the SWE5001 practice module (Graduate Certificate in Architecting Scalable Systems). This file says what the report must contain, who writes each part and by when. The writing, evidence, diagram and review rules are in [`AGENTS.md`](AGENTS.md); read it before writing or reviewing a section.

| | |
|---|---|
| Submission | Monday 16 November 2026, in Canvas (#462) |
| Weight | 30% of the certificate, including 5% peer assessment (briefing V2.6, slide 12; not company sponsored) |
| Template | "02 Project Report Template for Practice Project" (`.docx`). It is not in the repository: the editor keeps the master copy on the team's shared drive next to the draft. Keep its chapter and section numbers and titles exactly. |
| Where the report is written | One shared Google Doc (or Word file on the shared drive) created from the template. The repository holds this framework, the harness, the diagram sources and the review records; it does not hold the report text. |
| Editor | Liu Jin (@l0sgAi), per #462: owns this framework, the claims table in `AGENTS.md`, the whole-report definition of done and the final assembly |
| Must incorporate | Every lecturer remark from the 21 October presentation (#596; briefing: "incorporating remarks gathered during their project presentations"), listed as F1–F13 below |
| Briefing questions | Q1–Q8 and the success criteria, each answered where [Briefing coverage](#briefing-coverage) says |
| Deliverables besides the report | Working system, code base URL, DevSecOps pipeline scripts and test scripts (briefing slide 5): referenced from Appendix D |

## Milestones

The SWE5006 report (#463, due 1 November) and the written exam (2–6 November) compete for the same weeks, so the remark items come first. Dates marked "proposed" are the editor's plan and need the team's agreement; the others are fixed by the issues.

| Date | Milestone | Owner | Source |
|---|---|---|---|
| 26 Oct | Owners confirm their sections and F-items; #589 data-subnet change planned with operator approval | All; Pan for #589 | Proposed |
| 2 Nov | Every F-item artefact drafted: diagrams at least as reviewed sketches, tables filled | F-item owners | #596 |
| 9 Nov | #589 applied and evidenced on AWS; final AWS evidence (screenshots, status output) captured before any destroy | Pan, Liu Jin | Proposed |
| 11 Nov | All section drafts in the shared document | Section owners | #462 |
| 13 Nov | Reviews recorded; claims table rechecked; placeholders at zero | Reviewers, editor | Proposed |
| 16 Nov | Submitted; then the final destroy recorded | Editor | #462 |

**Cut order** if time runs out, cut first to last: chapter 6 depth → Appendices A and B → D20 roadmap figure → polish of D1 and D2. Never cut an F-item artefact, the persistence design or the detailed design.

## Owners

Per #462. Change #462 first, then this table.

| Sections | Owner |
|---|---|
| 1 Introduction, 2 Project Conduct | Lim (@limjiechao) |
| 3.1 Logical architecture, 3.1.1 AD table, 3.1.2, 3.1.3, 3.1.4 Platform Design, 3.3 Other decisions | Eric (@EricWang1358) |
| 3.2 Physical architecture, 3.2.1, 3.2.2 Technology and Services, 3.2.3 Persistence Design | Liu Jin (@l0sgAi) |
| 3.2.4 Detailed Design, 4.2 Availability | Yorik (@Yorick-Yu) |
| 3.4 Architectural Limitation, 4.1 Performance | Liu Jin and Eric |
| 4.3 Security, 5 DevOps and Development Lifecycle | Pan (@PPsyrius) |
| 4.4 Extensibility and Maintainability | Liu Jin |
| 6 Other highlights, appendices, assembly | Editor (Liu Jin) |

## Lecturer feedback (21 October)

F1–F12 are the remarks recorded verbatim in #596, in the same order. F13 was given orally and reported by the team after the presentation; it is not yet in #596. A remark is closed only when its closing artefact is in the report and its issue's acceptance criteria are ticked.

| ID | Remark | Issue | Home section | Closing artefact |
|---|---|---|---|---|
| F1 | What are the metrics for load testing? | #591 | 4.1 | Workload model; metric definitions with pass thresholds tied to the NFR register; results per run with what each run proves; an explicit statement of the unproven ceiling |
| F2 | Does CI/CD scan the container image after building? | #592 | 5.2 | Stage-by-stage DevSecOps table (5.2 below) with the Trivy image scan after the build and before export; environment sessions reuse the digests scanned at build |
| F3 | How did we do context mapping? | #587 | 3.1.4 | The method and a context map (D4) with upstream/downstream and the pattern on every edge, each pattern justified |
| F4 | How are the aggregates arrived at? | #587 | 3.1.4 | Derivation table: command → invariant → consistency boundary → aggregate, one row per aggregate |
| F5 | How did DDD (EventStorming) help us arrive at our proposed solution? | #595 | 3.1.4, 3.1.1 | Decision-traceability table: EventStorming finding → decision (AD) → module or service; a statement that the study was simulated and retrospective, with its inputs; what EventStorming did not decide |
| F6 | Resultant detailed object design | #585 | 3.2.4 | Class diagrams (D11, D13), an object diagram (D19) and a class responsibility table (class, responsibility, key typed operations, collaborators, multiplicities) |
| F7 | Database should be in its own subnet | #589 | 3.2, 4.3 | The implemented change: data subnets with no route to the NAT instance, shown in D6 with route tables, with AWS evidence and the automated check from #589. Mandatory, not deferrable |
| F8 | Show the group-buy user flow for two users | #590 | 3.2.4 | UML activity diagram with partitions Leader, Member, BlocBuy and Stripe (D15), including what each user sees; D12 is its class-level realisation |
| F9 | More perspectives from Architecting Software Solutions, e.g. deployment | #586 | 3.1, 3.2 | The viewpoint catalogue table and every view it lists ([Diagrams](#diagrams)) |
| F10 | Detailed DDD diagrams (Platform Engineering) | #587 | 3.1.4 | D4 context map, D7 aggregate diagrams with invariants and state machines, D8 domain event catalogue and producer/consumer flow |
| F11 | Class and sequence diagrams at class/object level for one or two critical use cases (SAD, SDP) | #585 | 3.2.4 | For UC4 and team expiry with refund: D11–D14, plus the design patterns used |
| F12 | Data schemas and models for the data stores | #588 | 3.2.3 | Everything in [focus area C](#c-database-schema-design-f12-section-323) |
| F13 | The logical architecture diagram on the slide is not standard | #586 (related) | 3.1, 3.1.3 | D3 redrawn to the [logical-view rules](AGENTS.md#logical-view-rules), with the logical-element → product mapping table in 3.2.2 |

## Decisions taken

Recorded on 23 October 2026 after an adversarial review of the first version of this framework.

1. **Location.** The framework stays in `docs/swe5001/report/`. #596 names `docs/swe5001/final-report/`; that path is to be corrected in the issue.
2. **Drafting tool.** The report is written in one shared document created from the template, not assembled from Markdown. Diagrams are drawn from committed sources and inserted as images; figure numbers and cross-references use the document's own caption and cross-reference fields.
3. **F7 is implemented.** The lecturers asked for the database in its own subnet. A deferral would read as ignoring the remark, so #589 is a required change before the final AWS evidence session, not an AISS item.
4. **Owners** follow #462 (table above).
5. **Decision IDs.** The report keeps the IDs the assessors saw in the deck (AD-02 to AD-14 in backups B1a and B1b; AISS-01 to AISS-06 in backup B2). The proposal-era IDs in [`02-proposed-architecture.md`](../architecture-options-study/02-proposed-architecture.md) §11 and §13 are mapped below and never cited by number in the report. New items continue from AD-15 and AISS-09.

### AD mapping (proposed; Eric confirms in 3.1.1)

| Study ID (02 §11) | Study decision | Report ID | Status as built |
|---|---|---|---|
| AD-01 | Modular-monolith core plus extracted services | AD-02 (consolidated) | Folded into report AD-02 |
| AD-02 | groupbuy + order one service, payment separate | AD-02 (consolidated) | Folded into report AD-02; the separate payment service was not built |
| AD-03 | Transactional outbox + SNS/SQS | AD-04 (consolidated) | Folded into report AD-04 |
| AD-04 | Stock reservation via synchronous REST | none | Not built |
| AD-05 | ALB path routing as the API gateway, CloudFront + WAF | AD-09 (edge part) | Partly built: no WAF |
| AD-06 | Shared Sa-Token session store | AD-05 | Built |
| AD-07 | One RDS instance, schema per service | AD-03, AD-11 | Built differently: one database (not schema) and role per service |
| AD-08 | ECS Fargate | AD-07 | Built |
| AD-09 | Idempotent consumers with an inbox table | AD-04 (consolidated) | Folded into report AD-04 |
| AD-10 | Jobs leased in the owning service | AD-15 (new) | Built |
| AD-11 | ADOT/X-Ray observability | none | Not built; CloudWatch only |

Consolidation records, each with one report-level status:

- **Report AD-02** consolidates study AD-01 and AD-02. Decision: split into services only where no transaction crosses, so catalog, groupbuy, order and payment stay together in commerce-svc. Status: built. Consequence: no sagas; payment extraction is roadmap work (AISS-02).
- **Report AD-04** consolidates study AD-03 and AD-09. Decision: transactional outbox to SNS and SQS with dead-letter queues, and consumers that record each event ID to skip duplicates. Status: built.

The deck has no AD-01; Eric decides whether AD-01 becomes the overall style decision or stays unused.

### AISS mapping (proposed; the 3.4 owners confirm)

| Study ID (02 §13) | Study limitation | Report ID |
|---|---|---|
| AISS-01 | Single RDS writer, single zone | AISS-01, AISS-05 |
| AISS-02 | Single NAT in one zone | AISS-01 |
| AISS-03 | Payment and team purchase scale as one unit | AISS-02 |
| AISS-04 | Shared Redis for sessions | AISS-07 (new) |
| AISS-05 | Events eventually consistent | AISS-08 (new) |
| AISS-06 | Reservations hold stock until TTL | dropped (reservation not built) |

New AISS candidates found in the review, numbered from AISS-09 by the 3.4 owners: no WAF, GuardDuty or CloudTrail in `infra/swe5001`; every service database carries the full migration stream; the capacity ceiling is unproven because the 19 October generator saturated first; no automated backups and no final snapshot, so post-seed database state has no recovery point; the demo seed is a rebuild baseline, not a recovery point (`db-init` recreates roles, databases and schemas only).

## Focus areas

The lecturers said three things were under-shown: DDD thinking, class-level design of the core functionality and the database schema. They are the report's priority; a section that lacks one of the artefacts below is not done.

### A. DDD design and thinking (F3, F4, F5, F10; section 3.1.4)

1. **Method, stated honestly.** EventStorming was simulated and retrospective: the team did not run a live workshop; the inputs were the proposal, the code, the ADRs, the migrations and the API contract ([`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1). Then: big picture (event timeline), hot spots, process level (commands, policies, read models), strategic design. One figure of the event timeline for UC3–UC6.
2. **What it changed (F5).** Decision-traceability table: finding → decision (AD) → module or service, plus what EventStorming did not decide (cost, runtime and network choices).
3. **Subdomains.** Table: subdomain, type (core, supporting, generic), reason, and build or buy. The build-or-buy column is new work (Google sign-in and Stripe are bought). Source: 06 §4.2.
4. **Bounded contexts and context map (D4).** Every edge labelled upstream or downstream with its pattern (customer–supplier, conformist, anti-corruption layer around Google and Stripe, open host service for the public API, published language for the versioned events), each justified. Source: 06 §4.3–§4.5.
5. **From contexts to services.** Count consistently: 06 treats groupbuy and order as one Team Purchase context, so commerce-svc holds three bounded contexts implemented as four modules (catalog, groupbuy, order, payment); ADR 0008 says "four contexts" because it counts modules. State the count once and explain it. Why they share one service: the settlement transaction. Source: 06 §6, deck slide 7.
6. **Aggregate derivation (F4).** One row per aggregate: commands, the invariant it protects, consistency boundary, root, entities, value objects, events emitted. State the rule (one invariant, one transaction; reference other aggregates by ID) and the accepted exception: one settlement transaction spans team, purchase intent, payment and order writes. Keep that shared transaction separate from lock acquisition; use the [verified lock order](AGENTS.md#claims-that-are-easy-to-get-wrong).
7. **Aggregate diagrams (D7).** UML class diagram per aggregate with `«aggregate root»`, `«entity»`, `«value object»`, plus state machines for the team and the purchase intent.
8. **Domain events (D8).** Catalogue: event, producer aggregate, internal or published, version, consumers; show a new consumer subscribing without a producer change (the audit queue).
9. **Ubiquitous language.** The 06 §7 glossary, in Appendix B.

### B. Detailed design at class and object level (F6, F8, F11; section 3.2.4)

Critical use cases: **UC4 join a team by paying** and **team expiry with refund**. Align with the SWE5006 report's design diagrams for the same use cases (#463: Eric UC3/UC4, Yorik UC6 refund) and reuse them where they fit, so the two reports never contradict each other.

1. **Two-user flow (D15, F8).** UML activity diagram, partitions Leader, Member, BlocBuy and Stripe: leader starts and pays, shares the link, member opens it and pays, team succeeds, both are notified; show what each user sees and what one user cannot see of the other.
2. **Class diagrams (D11, D13).** Real classes across the COLA layers: adapter (controller or webhook handler) → application service → domain (aggregate, factory, domain service, port interfaces) → infrastructure (gateway, MyBatis mapper, outbox writer). Show interfaces and the dependency direction. Start from `StripeCheckoutService`, `OrderFulfilmentAdapter`, `PaidGroupDealService`, `StandardPaidGroupDealCreator`, `IntegrationOutboxWriter` (infrastructure module) and `LeasedJobRunner` (bootstrap module).
3. **Sequence diagrams (D12, D14).** `object : Class` lifelines with real method names; the transaction as a combined fragment; `alt` for the business refusal (team full, so the payment is refunded); the race for the last seat as a second fragment.
4. **Object diagram (D19, mandatory).** A team with its two memberships, purchase intents, orders and payments at the moment it succeeds.
5. **Class responsibility table.** Class, responsibility, key operations with types, collaborators, multiplicities.
6. **Design patterns.** Only those in the code, each with the problem it solves: Factory Method (team creation), Observer (domain event subscribers with a fixed order), Transactional Outbox, Ports and Adapters, Idempotent Receiver (consumed-message table). Source: [`paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md), ADRs 0001, 0002, 0007.

### C. Database schema design (F12; section 3.2.3)

1. **Table ownership per service.** Every service database runs the full Flyway migration stream; the runtime role gets data access only to its service's tables ([`afterMigrate__service_grants.sql`](../../../backend/blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql)): identity owns six `sys_*`/`rel_*` tables, notification owns `biz_notification` and `sys_consumed_message`, commerce owns the rest. Say plainly that unused tables exist in every database (AISS candidate).
2. **ER diagram per database (D9).** Owned tables only: primary keys, key columns, relationships. The convention forbids foreign keys and CHECK constraints ([`pgsql-ddl` skill](../../../.claude/skills/pgsql-ddl/SKILL.md)), so relationships are drawn dashed and the application keeps them consistent.
3. **Conventions.** UUIDv7 primary keys; table prefixes `sys_`, `biz_`, `rel_` and `log_`; audit columns; soft delete; and why.
4. **Indexes that enforce rules.** Unique and partial unique indexes that protect invariants, for example a payment can fulfil at most one purchase intent (`uq_biz_purchase_intent_fulfilled_payment`, ADR 0001), so a replayed webhook collides at the database.
5. **Aggregate-to-table mapping**, linking C to A.
6. **Technical tables.** Scheduler lease `sys_scheduler_lock` (V13), outbox `sys_outbox` (V26), consumed messages `sys_consumed_message` (V28): purpose and pattern.
7. **Non-relational stores.** Valkey key model (session keys, TTL, loss on node failure); the event envelope as a JSON schema with its SNS message attributes; queue and dead-letter settings.
8. **Data classification** in the template's data-group format (format, storage, security controls, trade-off) for identity, commerce and payment, notifications, sessions and events, with retention per group. Retention today is "until the environment is destroyed" (no backups); production retention is a roadmap item, not a current fact.

## Chapter outline

Template headings are fixed, including subsections such as 1.4.1 and 1.4.2. "Must contain" is what an assessor looks for; "Sources" is where the facts are. Page counts are targets for the editor's length check.

### 1. Introduction (Lim), about 3 pages

| Section | Must contain | Sources |
|---|---|---|
| 1.1 Background | Context and the project status in one paragraph | [`project-proposal.typ`](../project-proposal.typ), deck slide 2 |
| 1.2 Business Needs | Pain points (Q1) and the platform's drivers | Proposal, deck slides 2–3 |
| 1.3 Stakeholders | Business and IT stakeholders (sellers, admins, buyers, the team as operator, Stripe and Google as providers); then the seed, producers and consumers | Proposal; [`02-proposed-architecture.md`](../architecture-options-study/02-proposed-architecture.md) §1 for seed, producers and consumers |
| 1.4.1, 1.4.2 | In scope and out of scope as lists; architected but not built, said explicitly (payment extraction, API gateway, stock reservation); delivery phases and what each delivered | Proposal, deck slides 3–4, [`microservice-refactoring-plan.md`](../microservice-refactoring-plan.md) |

### 2. Project Conduct (Lim), about 3 pages

| Section | Must contain | Sources |
|---|---|---|
| 2.1 Project Plan | Rough WBS with estimated effort | Proposal WBS, GitHub issues |
| 2.2 Project Status | Done; verified on AWS; verified locally only; not run; open (same honesty rule as deck slide 5) | [`docs/swe5001/evidence/`](../evidence/), [`HANDOFF.md`](../HANDOFF.md) |
| 2.3 Project Metrics | Actual milestones with dates; effort per member (about 50 person-days in total). Hours expended are reported here; contribution is assessed separately through peer assessment | Deck slide 6, the agreed effort split |

### 3. Solution Overview, about 22 pages

**3.1 Logical Architecture & Design (Eric)**

| Section | Must contain | Sources |
|---|---|---|
| 3.1 (intro) | Viewpoint catalogue table (view, viewpoint, stakeholder, concern, D-ID); element catalogue with element, responsibility and provider (custom-built, AWS-managed, third party), as the template asks | [Diagrams](#diagrams) |
| 3.1.1 Key Architectural Decisions | AD table with context, options, choice, trade-off, consequence; F5's traceability table links here | Deck backup B1a, [AD mapping](#ad-mapping-proposed-eric-confirms-in-311), ADRs 0007, 0008 |
| 3.1.2 Tiers and Layers | Tiers, the COLA layers inside each service, the development view (D16) and the 18 architecture rules that enforce the layers | [`02-proposed-architecture.md`](../architecture-options-study/02-proposed-architecture.md) §2.1 (proposal; check against the code), [`ArchitectureTest.java`](../../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java) |
| 3.1.3 Nodes and Subsystems | F13: the redrawn logical detail deployment diagram (D3) as the primary view, plus the UC4 runtime view (D17) for the dynamic relationships the template asks for | `logical-view.svg` content, redrawn |
| 3.1.4 Platform Design | Focus area A; then **API access for producers and consumers**: the OpenAPI contract and its CI check, Sa-Token RBAC, write rate limits, the standard success and error response envelope with stable error codes, idempotency, event versioning, and the API-gateway decision with what is not built | [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md), [`contracts/openapi.json`](../../../contracts/openapi.json), [`write-rate-limits.md`](../../../backend/docs/design/write-rate-limits.md), deck backup B10 |

**3.2 Physical Architecture & Design (Liu Jin; 3.2.4 Yorik)**

| Section | Must contain | Sources |
|---|---|---|
| 3.2 (intro) | UML deployment diagram (D5) and network diagram (D6) with the data subnets (F7), route tables and security-group flows; the trust-boundary view (D18) | `physical-deployment.svg`, [`infra/swe5001/`](../../../infra/swe5001/README.md) |
| 3.2.1 Key Architectural Decisions | AD table continued (deck AD-07 to AD-14, then AD-15 onward) | Deck backup B1b, [`04-aws-cost-estimate.md`](../architecture-options-study/04-aws-cost-estimate.md) |
| 3.2.2 Technology and Services | Each service with trade-off and rationale, sizes and versions; the logical-element → product mapping table that pairs D3 with D5 | Deck backup B7, Terraform modules |
| 3.2.3 Persistence Design | Focus area C; transactions, lock order, scheduler lease, outbox | [Flyway migrations](../../../backend/blocbuy-bootstrap/src/main/resources/db/migration/), ADRs 0001, 0002, 0007, [`db-init/init.sh`](../../../infra/swe5001/db-init/init.sh) |
| 3.2.4 Detailed Design | Focus area B | Focus area B |

**3.3 Other Architectural Decisions (Eric):** decisions not covered above; reference the quality-attribute sections instead of repeating them.

**3.4 Architectural Limitation (Liu Jin and Eric):** the AISS register in the template's columns (issue and impact, description, resolution, owner, status), each open item with a roadmap date, and the roadmap figure (D20).

### 4. Quality Attributes, about 10 pages

| Section | Owner | Must contain | Sources |
|---|---|---|---|
| 4.1 Performance | Liu Jin and Eric | F1: workload model; metric definitions and thresholds tied to the NFR register (client p95 and error rate, dropped iterations, server-side ALB latency and 5xx, task count, CPU, RDS CPU and connections, queue age); results per run with what each proves; capacity statement (measured, projected, next limit). The 19 October run is not valid acceptance evidence: either re-run with a generator inside AWS before the final destroy, or state the unproven ceiling as an AISS item | [`perf/k6/`](../../../perf/k6/README.md), [`nfr-register.md`](../../quality/nfr-register.md), evidence `20261016-http-baseline-dashboard` and `20261019-v3-autoscaling` |
| 4.2 Availability | Yorik | Recovery objectives table: recovery time and recovery point per component and data store, today and in production; two zones vs the single-zone data tier; self-healing, rollback, outbox, dead-letter queues; each failure drill with its run status from the claims table | Evidence `20261015-local-failure-rehearsal`, deck backup B12 |
| 4.3 Security | Pan | Layered controls: AWS Organization guardrails and the permissions boundary, Identity Center, GitHub OIDC, network isolation with the data subnets, encryption, secrets, Stripe webhook signature verification (hashing), the pipeline scans; IDS/IPS stated honestly (no WAF, GuardDuty or CloudTrail, as an AISS item) | Deck slide 22, [`infra/swe5001/foundation/`](../../../infra/swe5001/foundation/), [`quality-gates.md`](../../quality/quality-gates.md) |
| 4.4 Extensibility and Maintainability | Liu Jin | Measured reuse (audit queue), the service template, the architecture rules, the API contract check | Deck slide 11 |

### 5. DevOps and Development Lifecycle (Pan), about 6 pages

| Section | Must contain | Sources |
|---|---|---|
| 5.1 Source Control Strategy | Repository layout, branches (`dev`, `prod`), protection rules, developer authentication | [`CONTRIBUTING.md`](../../../CONTRIBUTING.md), [`impl-github-configuration.md`](../impl-github-configuration.md) |
| 5.2 Continuous Integration | F2: pipeline diagram (D10) and a stage-by-stage DevSecOps table: secret scan (Gitleaks), dependency and IaC scans (Trivy fs and config), static analysis (SonarCloud, SpotBugs), unit, integration and architecture tests, image build then Trivy image scan, SBOM (CycloneDX), passive DAST (ZAP baseline against the `dev` environment, advisory); for each: trigger, gate or advisory, evidence | [`.github/workflows/`](../../../.github/workflows/) (`swe5001-service-images.yml`, `ci-backend.yml`, `security.yml`, `sbom.yml`, `dast-baseline.yml`), [`backend-pipeline.md`](../../ci/backend-pipeline.md) |
| 5.3 Continuous Delivery | Environments, the promotion PR into `prod`, approvals, create → deploy → smoke → release → destroy with the 17 October timings; DORA metrics; the DevSecOps maturity self-assessment | `swe5001-deploy.yml`, `swe5001-release-gate.yml`, `swe5001-env.yml`, [`dora-metrics-20261022.md`](../../ci/dora-metrics-20261022.md), [`devsecops-maturity-assessment.md`](../../ci/devsecops-maturity-assessment.md) |

### 6. Other things to be highlighted (editor), about 3 pages

Technical findings from the real cloud (deck backup B8); **platform operations**: dashboards, alarms, logs, dead-letter redrive and the environment status check ([`impl-verification-observability.md`](../impl-verification-observability.md), [`env-status.sh`](../../../infra/swe5001/scripts/env-status.sh)); **analytics** as metric → insight → change made (for example the k6 baseline → the 12,000 scaling target), and the business analytics possible from stored data (deck B11); cost (deck B9).

### Appendices (editor)

A. Glossary. B. Ubiquitous language. C. Full ER diagrams if too large for 3.2.3. D. Deliverables: code base URL with the submitted tag or commit, the pipeline scripts, the test scripts (k6, unit, integration, architecture) and working-system evidence captured before the final destroy. E. Evidence register: one row per cited measurement (date, what ran, where, result, repository path).

## Diagrams

Each diagram has an ID, a viewpoint and a committed source ([diagram rules](AGENTS.md#diagram-rules)). The 3.1 viewpoint catalogue lists the same rows.

| ID | View | Viewpoint | Notation | Section | Starting point |
|---|---|---|---|---|---|
| D1 | System context | Context | C4 level 1 or UML use case | 1.3 | 06 §2.1 |
| D2 | Use cases | Functional (4+1 scenarios) | UML use case | 1.4 | Proposal |
| D3 | Logical view, redrawn (F13) | Logical | UML logical detail deployment diagram | 3.1.3 | `logical-view.svg` content |
| D4 | Context map | Domain | DDD context map with patterns | 3.1.4 | `context-map.svg` |
| D5 | Physical deployment | Deployment (4+1 physical) | UML deployment | 3.2 | `physical-deployment.svg` |
| D6 | Network: subnets, route tables, security groups | Deployment | AWS architecture icons | 3.2 | New; shows F7 |
| D7 | Aggregates and life cycles | Domain | UML class and state machine | 3.1.4 | `aggregates.svg` |
| D8 | Domain events and producer/consumer flow | Information | Table and UML component | 3.1.4 | ADR 0007 |
| D9 | ER diagram per database | Information | Crow's foot or UML class | 3.2.3 | Flyway migrations |
| D10 | CI/CD pipeline with security stages | Development | UML activity | 5.2, 5.3 | Deck slides 19–20 |
| D11 | UC4 classes | Design | UML class | 3.2.4 | New; align with #463 |
| D12 | UC4 sequence: two users and the last seat | Design | UML sequence | 3.2.4 | `uc4-join-sequence.svg` |
| D13 | Team expiry and refund classes | Design | UML class | 3.2.4 | New; align with #463 |
| D14 | Team expiry and refund sequence | Design | UML sequence | 3.2.4 | `expiry-refund-sequence.svg` |
| D15 | Two-user group-buy flow (F8) | Functional | UML activity with partitions | 3.2.4 | New |
| D16 | Modules and layers | Development (4+1 development) | UML package | 3.1.2 | Maven modules and the ArchUnit rules |
| D17 | UC4 at runtime, plus the event path | Concurrency (4+1 process) | UML communication | 3.1.3 | 02 §5 (proposal; check against the code) |
| D18 | Trust boundaries | Security | Annotated deployment | 3.2, 4.3 | New |
| D19 | Team at success | Design | UML object | 3.2.4 | New |
| D20 | AISS roadmap | Roadmap | Timeline | 3.4 | New |

`context-map.svg` and `aggregates.svg` exist both in [`docs/presentation-kit/diagrams/`](../../presentation-kit/diagrams/) and in [`architecture-options-study/diagrams/`](../architecture-options-study/diagrams/); start from the presentation-kit copies, which the deck used (last changed 19 October; the study copies date from 17 October and have Mermaid sources).

## Briefing coverage

| Briefing question or success criterion | Answered in |
|---|---|
| Q1 Pain points | 1.2 |
| Q2 Platform, common services, reusable frameworks | 3.1.4, 4.4 |
| Q3 Benefit of building on the platform, demonstrated | 4.4 |
| Q4 Scale, and proof | 4.1 |
| Q5 Technical and non-technical benefits, demonstrated | 4.1–4.4, 6 |
| Q6 Value of cloud-native design, demonstrated | 3.2.1, 3.2.2, 6 (cost) |
| Q7 Lifecycle automation and effort saved | 5.2, 5.3 |
| Q8 Effort within the guideline | 2.3 |
| Identify the seed, producers and consumers | 1.3, 3.1.4 |
| Use cases that expand the ecosystem | 1.4, 3.1.4 |
| Scaling the number of customers and producers (onboarding, not requests per second) | 3.1.4 (API access), 4.4 |
| Support for more seeds in future | 4.4 |
| Analytics to improve functionality and performance | 6 |
| Course modules: Architecting Software Solutions; Platform Engineering (robust API access, domain analysis, platform operations and data); Cloud Native Solution Design; DevSecOps | 3.1–3.2; 3.1.4 and 6; 3.2 and 4; 5 |
