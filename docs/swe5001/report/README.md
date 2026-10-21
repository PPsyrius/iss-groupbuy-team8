# SWE5001 final report: framework

Team 8's BlocBuy project report for the SWE5001 practice module (Graduate Certificate in Architecting Scalable Systems). Writing rules, the evidence discipline and the definition of done are in [`AGENTS.md`](AGENTS.md); read it before writing a section.

| | |
|---|---|
| Due | Monday 16 November 2026 (briefing V2.6, slide 6) |
| Weight | 30% of the certificate, including 5% peer assessment (not company sponsored) |
| Template | "02 Project Report Template for Practice Project" (`.docx`, kept outside the repository). Keep its chapter and section numbering exactly. |
| Must incorporate | Every remark from the 21 October presentation (briefing: "incorporating remarks gathered during their project presentations"). The list is in [Assessor feedback](#assessor-feedback-21-october) below. |
| Briefing questions | Q1–Q8 ("Questions to Consider", briefing slide 9) must each have an answer the reader can find; see [Briefing coverage](#briefing-coverage). |

## Assessor feedback (21 October)

Each remark gets an ID, a home section and the artefact that closes it. A remark is closed only when that artefact is in the report.

| ID | Remark | Home section | Closing artefact |
|---|---|---|---|
| F1 | What are the metrics for load testing? | 4.1 Performance | Metric definitions table (workload, client and server metrics, pass thresholds), then the results table per run with validity caveats |
| F2 | Does CI/CD scan the container image after building? | 5.2 Continuous Integration | Pipeline diagram marking the Trivy image scan after the build and before export; the gate rule (fixable HIGH and CRITICAL fail the job) |
| F3 | How did we do context mapping? | 3.1.4 Platform Design | The method (EventStorming → subdomains → bounded contexts → relationships) and a context map with the relationship pattern on every edge |
| F4 | How are the aggregates arrived at? | 3.1.4 Platform Design | The derivation: command → invariant → consistency boundary → aggregate; one table row per aggregate with its invariants and the transaction that protects them |
| F5 | Detailed object design | 3.2.4 Detailed Design | Class diagrams at class level (real class names from the code) for the critical use cases |
| F6 | The database should be in its own subnet, separate from the services | 3.2 Physical Architecture | Either the implemented change (data subnets with no NAT route) or a recorded decision plus an AISS item with a date; see [Decisions before writing](#decisions-before-writing) |
| F7 | Show the group-buy user flow for two users | 3.2.4 Detailed Design | A two-user sequence: leader starts and pays, shares, member joins and pays, team succeeds; plus the race for the last seat |
| F8 | More perspectives from Architecting Software Solutions (for example deployment) | 3.1, 3.2 | The view set in [Diagrams](#diagrams), each with a stated viewpoint and audience |
| F9 | Detailed DDD diagrams (Platform Engineering) | 3.1.4 | Context map, aggregate diagrams with invariants and states, domain event catalogue, producer and consumer view |
| F10 | Class and sequence diagrams at class or object level for one or two critical use cases (SAD, SDP) | 3.2.4 | For UC4 (join a team by paying) and team expiry with refund: one class diagram and one sequence diagram each, plus the design patterns used |
| F11 | Data schemas and models for every data store | 3.2.3 Persistence Design | ER diagram per PostgreSQL database, Valkey key model, event and queue message schema, data classification per the template's data-group format |
| F12 | The logical architecture diagram on the slide is not standard; revise it | 3.1, 3.1.3 | A redrawn logical view in standard UML (see the logical-view rules in [`AGENTS.md`](AGENTS.md#logical-view-rules)), technology-neutral, with layers and provided and required interfaces |

## Focus areas

The assessors said three things were not shown enough in the presentation: DDD thinking, class-level design of the core functionality, and the database schema. They are the report's priority. Each needs the artefacts below; a section that lacks one is not done.

### A. DDD design and thinking (F3, F4, F9; section 3.1.4)

Show the reasoning, not only the result.

1. **Method.** How EventStorming ran: big picture (events on a timeline), then hot spots, then process level (commands, policies, read models), then strategic design. One figure of the event timeline for UC3–UC6. Source: [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§3.
2. **Subdomains.** Table: subdomain, type (core, supporting, generic), why, build or buy (Google sign-in and Stripe are bought). Source: §4.2.
3. **Bounded contexts and context map (D4).** Every edge labelled upstream or downstream and with its pattern: customer–supplier, conformist, anti-corruption layer (around Google and Stripe), open host service (the public API), published language (the versioned events). Say why each pattern was chosen. Source: §4.3–§4.5.
4. **From contexts to services.** Why four contexts share commerce-svc (they share one settlement transaction) and which became services. Source: §6, deck slide 7.
5. **Aggregate derivation (F4).** For each aggregate, one table row: the commands it handles, the invariant it protects ("a team never has more members than its size"), the consistency boundary, the root, its entities and value objects, the events it emits. State the rule used (one invariant, one transaction; reference other aggregates by ID) and name the one exception: the settlement transaction locks team, purchase intent, order and payment together, and why that trade-off was accepted.
6. **Aggregate diagrams (D7).** UML class diagram per aggregate with `«aggregate root»`, `«entity»`, `«value object»` stereotypes, plus a state machine for the team and the purchase intent.
7. **Domain events (D8).** Catalogue: event, producer aggregate, internal or published, version, consumers. Show how a new consumer subscribes without changing the producer (the audit queue).
8. **Ubiquitous language.** The glossary from §7, in an appendix.

### B. Detailed design at class and object level (F5, F7, F10; section 3.2.4)

Two critical use cases: **UC4 join a team by paying** and **team expiry with refund**. For each:

1. **Class diagram (D11, D13).** Real classes across the COLA layers: adapter (controller or webhook handler) → application service → domain (aggregate, factory, domain service, port interfaces) → infrastructure (gateway, MyBatis mapper, outbox writer). Show interfaces and the direction of dependencies (domain depends on nothing). Start from `OrderFulfilmentAdapter`, `PaidGroupDealService`, `StandardPaidGroupDealCreator`, `IntegrationOutboxWriter`, `LeasedJobRunner`.
2. **Sequence diagram (D12, D14).** Object lifelines with real method names; the transaction boundary as a combined fragment; `alt` for the business refusal (team full, so the payment is refunded). For UC4 show **two users**: the leader starts and pays, shares the link, the member joins and pays, the team succeeds. Add the race for the last seat as a second fragment.
3. **Object diagram (optional).** A team with its two memberships, orders and payments at the moment it succeeds.
4. **Design patterns.** Only patterns present in the code, each with the problem it solves: Factory Method (team creation), Observer (domain event subscribers with a fixed order), Transactional Outbox, Ports and Adapters, Idempotent Receiver (consumed-message table). Source: [`paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md), ADRs 0002 and 0007.

### C. Database schema design (F11; section 3.2.3)

1. **Table ownership per service.** Every service database runs the full Flyway migration stream, but the runtime role only gets data access to the tables its service owns (`afterMigrate__service_grants.sql`): identity owns six `sys_*`/`rel_*` tables, notification owns `biz_notification` and `sys_consumed_message`, commerce owns the rest. Show this as a table and say plainly that the unused tables exist in every database.
2. **ER diagram per database (D9).** Owned tables only, with primary keys, key columns and relationships. Relationships are logical: the team's convention forbids foreign keys and CHECK constraints ([`pgsql-ddl` skill](../../../.claude/skills/pgsql-ddl/SKILL.md)), so draw them dashed and explain how the application keeps them consistent.
3. **Conventions.** UUIDv7 primary keys, table prefixes (`sys_`, `biz_`, `rel_`), audit columns, soft delete, and why.
4. **Indexes that enforce rules.** Unique and partial unique indexes that protect invariants (for example one fulfilled payment per purchase intent, ADR 0001).
5. **Aggregate-to-table mapping.** Which tables persist which aggregate; this links section C to section A.
6. **Technical tables.** Outbox (`V26`), consumed messages (`V28`), scheduler lease (`V13`): purpose and the pattern each supports.
7. **Non-relational stores.** Valkey key model (session keys, TTL, what is lost if the node fails); the event envelope as a JSON schema with its SNS message attributes; queue and dead-letter settings.
8. **Data classification.** The template's data-group format (format, storage, security controls, trade-off) for identity data, commerce and payment data, notifications, sessions and events.

## Decisions before writing

Settle these first; they change what several sections say.

1. **F6, data subnets.** Recommended: implement two private data subnets (one per zone) with no route to the NAT instance, and move the RDS and Valkey subnet groups there. It costs nothing at runtime and turns the remark into a closed item. If the team does not implement it, record the decision, the reason and the roadmap date as an AISS item.
2. **One AISS register.** The deck's backup B2 and [`02-proposed-architecture.md` §13](../architecture-options-study/02-proposed-architecture.md) use the same IDs for different issues. Section 3.4 of the report becomes the only register; renumber once and use those IDs everywhere in the report.
3. **One AD register.** Number decisions once across 3.1.1, 3.2.1 and 3.3 (the template continues the numbering; its example repeats AD-03, which is a template typo). Map each AD to its ADR under [`backend/docs/adr/`](../../../backend/docs/adr/README.md) where one exists.
4. **Critical use cases for 3.2.4.** Default: UC4 join a team by paying (synchronous, Stripe webhook, lock order) and team expiry with refund (scheduled job, outbox, SNS and SQS, notification). Both are already drawn at component level in [`docs/presentation-kit/diagrams/`](../../presentation-kit/diagrams/); the report needs them at class level.
5. **Section owners.** Confirm the default owners in the [outline](#chapter-outline) at the first report meeting.

## Chapter outline

Template headings are fixed. "Must contain" lists what an assessor will look for; "Sources" lists where the facts already are. Default owners follow the presentation split; confirm them.

### 1. Introduction (owner: Lim)

| Section | Must contain | Sources |
|---|---|---|
| 1.1 Background | Context, current project status in one paragraph | [`project-proposal.typ`](../project-proposal.typ), deck slide 2 |
| 1.2 Business Needs | Pain points (Q1) and the platform's drivers | Proposal, deck slides 2–3 |
| 1.3 Stakeholders | Business and IT stakeholders; the seed, producers and consumers | [`02-proposed-architecture.md` §1](../architecture-options-study/02-proposed-architecture.md) |
| 1.4 Project Scope | In scope and out of scope as lists; architected but not built, said explicitly (for example payment extraction, API gateway) | Proposal, deck slides 3–4, [`microservice-refactoring-plan.md`](../microservice-refactoring-plan.md) |

### 2. Project Conduct (owner: Lim)

| Section | Must contain | Sources |
|---|---|---|
| 2.1 Project Plan | Rough WBS with estimated effort | Proposal WBS, GitHub issues |
| 2.2 Project Status | Done, verified on AWS, verified locally only, open; same honesty rule as deck slide 5 | [`docs/swe5001/evidence/`](../evidence/), [`HANDOFF.md`](../HANDOFF.md) |
| 2.3 Project Metrics | Actual milestones with dates; effort per member (≈ 50 person-days total) | Deck slide 6, agreed effort split |

### 3. Solution Overview

**3.1 Logical Architecture & Design (owner: Eric)**

| Section | Must contain | Sources |
|---|---|---|
| 3.1.1 Key Architectural Decisions | AD table with trade-off and rationale per row | [`02-proposed-architecture.md` §11](../architecture-options-study/02-proposed-architecture.md), ADRs 0007, 0008 |
| 3.1.2 Tiers and Layers | Tiers, and the COLA layers inside each service; the 18 architecture rules that enforce them | [`02-proposed-architecture.md` §2.1](../architecture-options-study/02-proposed-architecture.md), backend constraints skill |
| 3.1.3 Nodes and Subsystems | F12: redrawn logical view (D3); element catalogue: element, responsibility, provider | `logical-view.svg` |
| 3.1.4 Platform Design | F3, F4, F9: EventStorming method, subdomains, context map with patterns, aggregates and how they were derived, domain event catalogue (internal vs published), how producers and consumers use the platform (API contract, versioned events) | [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§6, `context-map.svg`, `aggregates.svg`, [`contracts/openapi.json`](../../../contracts/openapi.json) |

**3.2 Physical Architecture & Design (owner: Liu Jin; 3.2.4 Yorik)**

| Section | Must contain | Sources |
|---|---|---|
| 3.2 (intro) | UML deployment diagram (D5) and network diagram (D6): VPC, subnets per tier and zone, security-group flows, CloudFront, ALB, ECS, RDS, Valkey, SNS/SQS, NAT; F6 | `physical-deployment.svg`, [`infra/swe5001/`](../../../infra/swe5001/README.md) |
| 3.2.1 Key Architectural Decisions | AD table continued: Fargate vs EKS, ARM, Spot, disposable runtime, fck-nat, one database server, per-service databases | Deck backup B1b, [`04-aws-cost-estimate.md`](../architecture-options-study/04-aws-cost-estimate.md) |
| 3.2.2 Technology and Services | Each cloud service with its trade-off and rationale; sizes and versions | Deck backup B7, Terraform modules |
| 3.2.3 Persistence Design | F11: data groups in the template format (format, storage, security, trade-off); ER diagram per database (D9); Valkey key model; event envelope and queue schema; transactions, lock order, scheduler lease, outbox; retention per data group | Flyway migrations in [`backend/blocbuy-bootstrap/src/main/resources/db/migration/`](../../../backend/blocbuy-bootstrap/src/main/resources/db/migration/), ADRs 0001, 0002, 0007, [`infra/swe5001/db-init/init.sh`](../../../infra/swe5001/db-init/init.sh) |
| 3.2.4 Detailed Design | F5, F7, F10: per critical use case, a class diagram and a sequence diagram with real class and method names; the two-user group-buy flow; design patterns used and why | [`backend/docs/design/paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md), `uc4-join-sequence.svg`, `expiry-refund-sequence.svg` |

**3.3 Other Architectural Decisions (owner: Eric)** — decisions not covered above; reference the quality-attribute sections instead of repeating them.

**3.4 Architectural Limitation (owner: Liu Jin)** — the single AISS register in the template's table format (issue and impact, description, resolution, owner, status), each open item with a roadmap date.

### 4. Quality Attributes

| Section | Owner | Must contain | Sources |
|---|---|---|---|
| 4.1 Performance | Pan | F1: workload model, metric definitions and thresholds, results per run, what each run does and does not prove; caching and load balancing; scaling policy; capacity statement (measured, projected, next limit) | [`perf/k6/`](../../../perf/k6/README.md), evidence `20261016-http-baseline-dashboard`, `20261019-v3-autoscaling` |
| 4.2 Availability | Liu Jin | Two zones, single-zone data tier, self-healing, rollback, failure drills with "AWS" or "local only" stated per result | Evidence `20261015-local-failure-rehearsal`, deck backup B12 |
| 4.3 Security | Lim | Layered controls: Organization guardrails, Identity Center, OIDC, network isolation, encryption, secrets, pipeline scans; known gaps | Deck slide 22, [`infra/swe5001/github/`](../../../infra/swe5001/github/) |
| 4.4 Extensibility and Maintainability | Eric | Measured reuse (audit queue), service template, architecture rules, API contract checks | Deck slide 11 |

### 5. DevOps and Development Lifecycle (owner: Pan)

| Section | Must contain | Sources |
|---|---|---|
| 5.1 Source Control Strategy | Repository layout, branches (`dev`, `prod`), protection rules, how developers authenticate | [`CONTRIBUTING.md`](../../../CONTRIBUTING.md), [`impl-github-configuration.md`](../impl-github-configuration.md) |
| 5.2 Continuous Integration | F2: pipeline diagram (D10) with trigger, jobs, tests and gates per job; image build → Trivy scan → export as one chain | [`.github/workflows/`](../../../.github/workflows/), especially `swe5001-service-images.yml`, `ci-backend.yml`, `security.yml` |
| 5.3 Continuous Delivery | Environments, promotion PR into `prod`, approvals, create → deploy → smoke → release → destroy, timings from the 17 October run | `swe5001-deploy.yml`, `swe5001-release-gate.yml`, deck slides 19–20 |

### 6. Other things to be highlighted (owner: Lim)

Technical findings from the real cloud (deck backup B8), analytics (B11), cost (B9), and how the briefing's success criteria are met.

## Diagrams

The view set answers F8, F9 and F10. Every diagram is UML or a named notation, has a caption stating its viewpoint, and is referenced from the text.

| ID | View | Notation | Section | Starting point |
|---|---|---|---|---|
| D1 | System context (actors, external systems) | C4 level 1 or UML use case | 1.3 | `06-eventstorming-ddd.md` §2.1 |
| D2 | Use case diagram | UML | 1.4 | Proposal |
| D3 | Logical view, redrawn (F12) | UML component diagram, or logical detail deployment diagram with logical nodes | 3.1.3 | Content of `logical-view.svg`, redrawn to the logical-view rules |
| D4 | Context map | DDD context map with patterns | 3.1.4 | `context-map.svg` |
| D5 | Physical deployment | UML deployment (nodes, artefacts, zones) | 3.2 | `physical-deployment.svg` |
| D6 | Network and security groups | AWS architecture icons | 3.2 | New; shows the data subnets (F6) |
| D7 | Aggregates and life cycles | UML class + state machine | 3.1.4 | `aggregates.svg` |
| D8 | Domain event catalogue and producer/consumer flow | Table + UML component | 3.1.4 | ADR 0007 |
| D9 | ER diagram per database | Crow's foot or UML class | 3.2.3 | Flyway migrations |
| D10 | CI/CD pipeline | UML activity | 5.2, 5.3 | Deck slides 19–20 |
| D11 | UC4 class diagram | UML class | 3.2.4 | New |
| D12 | UC4 sequence, two users and the last seat | UML sequence | 3.2.4 | `uc4-join-sequence.svg` |
| D13 | Team expiry and refund class diagram | UML class | 3.2.4 | New |
| D14 | Team expiry and refund sequence | UML sequence | 3.2.4 | `expiry-refund-sequence.svg` |

## Briefing coverage

| Briefing question | Answered in |
|---|---|
| Q1 Pain points | 1.2 |
| Q2 Platform, common services, reusable frameworks | 3.1.4, 4.4 |
| Q3 Benefit of building on the platform, demonstrated | 4.4 |
| Q4 Scale, and proof | 4.1 |
| Q5 Technical and non-technical benefits, demonstrated | 4.1–4.4, 6 |
| Q6 Value of cloud-native design, demonstrated | 3.2.1, 3.2.2, 6 (cost) |
| Q7 Lifecycle automation and effort saved | 5.2, 5.3 |
| Q8 Effort within the guideline | 2.3 |
| Success criteria: seed, producers, consumers; more seeds; analytics | 1.3, 3.1.4, 6 |
