# SWE5001 final report: framework

Team 8's BlocBuy project report for the SWE5001 practice module (Graduate Certificate in Architecting Scalable Systems). Writing rules, the evidence discipline and the definition of done are in [`AGENTS.md`](AGENTS.md); read it before writing a section.

| | |
|---|---|
| Due | Monday 16 November 2026 (briefing V2.6, slide 6) |
| Weight | 30% of the certificate, including 5% peer assessment (not company sponsored) |
| Template | "02 Project Report Template for Practice Project" (`.docx`, kept outside the repository). The report is written in Typst ([`main.typ`](main.typ)) with the template's cover, chapter and section structure; keep its numbering and titles exactly. |
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
| F13 | How did DDD (EventStorming) help us arrive at our proposed solution? (#596 point 5) | 3.1.4 Platform Design | The EventStorming method, the findings-to-decisions table with each decision's status at study time, the events-to-contexts table, and what EventStorming did not decide |

## Focus areas

The assessors said three things were not shown enough in the presentation: DDD thinking, class-level design of the core functionality, and the database schema. They are the report's priority. Each needs the artefacts below; a section that lacks one is not done.

### A. DDD design and thinking (F3, F4, F9; section 3.1.4)

Show the reasoning, not only the result.

1. **Method.** How EventStorming ran: big picture (events on a timeline), then hot spots, then process level (commands, policies, read models), then strategic design. One figure of the event timeline for UC3–UC6. Source: [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§3.
2. **Subdomains.** Table: subdomain, type (core, supporting, generic), why, build or buy (Google sign-in and Stripe are bought). Source: §4.2.
3. **Bounded contexts and context map (D4).** Every edge labelled upstream or downstream and with its pattern: customer–supplier, conformist, anti-corruption layer (around Google and Stripe), open host service (the public API), published language (the versioned events). Say why each pattern was chosen. Source: §4.3–§4.5.
4. **From contexts to services.** Why four contexts share commerce-svc (they share one settlement transaction) and which became services. Source: §6, deck slide 7.
5. **Aggregate derivation (F4).** For each aggregate, one table row: the commands it handles, the invariant it protects ("a team never has more members than its size"), the consistency boundary, the root, its entities and value objects, the events it emits. State the rule used (one invariant, one transaction; reference other aggregates by ID) and explain the accepted settlement exception: one transaction spans team, purchase intent, payment and order writes. Distinguish that shared transaction boundary from lock acquisition; use the [verified lock order](AGENTS.md#claims-that-are-easy-to-get-wrong) for the UC4 sequence.
6. **Aggregate diagrams (D7a–D7d) and life cycles.** UML class diagrams by context with `«aggregate root»`, `«entity»`, `«value object»` stereotypes. The team, purchase intent and checkout payment state machines are placed in §3.1.4 from the source-book diagrams (`a-uc05-st-deal`, `a-uc03-st-intent`, `a-pay-st-payment`, unchanged); the order, listing and refund life cycles are one table (`tbl-lifecycles`).
7. **Domain events (D8, D8b).** Catalogue: event, producer aggregate, internal or published, version, consumers. Show how a new consumer subscribes without changing the producer (the audit queue).
8. **Ubiquitous language.** The glossary from §7, in an appendix.

### B. Detailed design at class and object level (F5, F7, F10; section 3.2.4)

Two critical use cases: **UC4 join a team by paying** and **team expiry with refund**. For each:

1. **Class diagram (D11, D13).** Real classes across the COLA layers: adapter (controller or webhook handler) → application service → domain (aggregate, factory, domain service, port interfaces) → infrastructure (gateway, MyBatis mapper, outbox writer). Show interfaces and the direction of dependencies (domain depends on nothing). Start from `OrderFulfilmentAdapter`, `PaidGroupDealService`, `StandardPaidGroupDealCreator`, `IntegrationOutboxWriter`, `LeasedJobRunner`.
2. **Sequence diagram (D12a, D12b, D14a, D14b).** Object lifelines with real method names; the transaction boundary as a combined fragment; `alt` for the business refusal (team full, so the payment is refunded). For UC4 show **two users**: the leader starts and pays, shares the link, the member joins and pays, the team succeeds. The race for the last seat is drawn in full only in the Appendix A source book (`a-uc04-ds-race`); the report states it in a fragment and in text.
3. **Object diagram (optional).** A team with its two memberships, orders and payments at the moment it succeeds.
4. **Design patterns.** Only patterns present in the code, each with the problem it solves: Factory Method (team creation), Observer (domain event subscribers with a fixed order), Transactional Outbox, Ports and Adapters, Idempotent Receiver (consumed-message table). Source: [`paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md), ADRs 0002 and 0007.

### C. Database schema design (F11; section 3.2.3)

1. **Table ownership per service.** Every service database runs the full Flyway migration stream, but the runtime role only gets data access to the tables its service owns (`afterMigrate__service_grants.sql`): identity owns six `sys_*`/`rel_*` tables, notification owns `biz_notification` and `sys_consumed_message`, commerce owns the rest. Show this as a table and say plainly that the unused tables exist in every database.
2. **ER diagram per database (D9a–D9d).** Owned tables only, with primary keys, key columns and relationships. Relationships are logical: the team's convention forbids foreign keys and CHECK constraints ([`pgsql-ddl` skill](../../../.claude/skills/pgsql-ddl/SKILL.md)), so draw them dashed and explain how the application keeps them consistent.
3. **Conventions.** UUIDv7 primary keys, table prefixes (`sys_`, `biz_`, `rel_`), audit columns, soft delete, and why.
4. **Indexes that enforce rules.** Unique and partial unique indexes that protect invariants (for example one fulfilled payment per purchase intent, ADR 0001).
5. **Aggregate-to-table mapping.** Which tables persist which aggregate; this links section C to section A.
6. **Technical tables.** Outbox (`V26`), consumed messages (`V28`), scheduler lease (`V13`): purpose and the pattern each supports.
7. **Non-relational stores.** Valkey key model (session keys, TTL, what is lost if the node fails); the event envelope as a JSON schema with its SNS message attributes; queue and dead-letter settings.
8. **Data classification.** The template's data-group format (format, storage, security controls, trade-off) for identity data, commerce and payment data, notifications, sessions and events.

## Decisions before writing

Settled on 4 November 2026 (Phase 0). Sections are written against these; change them only by editing this list in a pull request.

1. **F6, data subnets: implemented.** Two data subnets (`10.50.20.0/24`, `10.50.21.0/24`, one per zone) hold RDS and Valkey; their route table has only the local VPC route, so there is no NAT, internet or S3-endpoint path. ECS tasks stay in the private application subnets. `terraform test` (`data_tier_has_its_own_subnets`) asserts the two sets are disjoint. Configured and tested offline, not yet applied on AWS: the next runtime session creates it (the runtime is rebuilt from zero each session, so there is no migration). Recorded as AD-15 and AISS-07 (#589). Write it in 3.2 as "configured" until an AWS session proves it.
2. **One AISS register** and 3. **one AD register**: [`registers.md`](registers.md). The deck backup slides B1–B2 are the base; the study's and the main slides' IDs are not used. The register maps old IDs to new ones and each AD to its ADR.
4. **Critical use cases for 3.2.4.** UC4 join a team by paying, and team expiry with refund (both confirmed). Both are already drawn at component level in [`docs/presentation-kit/diagrams/`](../../presentation-kit/diagrams/); the report needs them at class level.
5. **Section owners.** Confirmed below.

10 November 2026 reconciliation (#775): the [dated session record](../evidence/20261110-subnet-verification/README.md) now verifies AD-15 placement and required connectivity on AWS; AISS-07 is resolved for that recorded session. This supersedes item 1's earlier “configured only” writing instruction, not its original decision. Negative packet/failover tests remain outside the result. The [pool/scaling experiment disposition](../evidence/20261110-load-preflight/README.md#10-november-disposition-for-775), [operator edge repair](../evidence/20261109-edge-policy-recovery/README.md) and D10 in §5 close the other report gaps without importing executable fixes from PR #784.

11 November 2026 session addendum: the [released-image rerun record](../evidence/20261111-load-reruns/README.md) adds a passing V2 lower bound and two failed V3 campaigns. Its r2 variant used commerce 1 vCPU/2 GiB, RDS `db.t4g.small` and a 50% CPU guard through reviewed, hashed session-only overlays, not merged configuration. Historical 0.5-vCPU/micro-class facts below and in the writing harness remain dated observations/defaults, not that variant's sizing. Do not convert its improved error/drop totals into acceptance or revise other owners' historical sections silently; §4.1 owns the result and limitations, §4.3 the fresh metadata boundary, and §5 the operational provenance. Original report bytes/staged state were restored and verified before this continuation; evidence publication/pinning and second-owner review remain outstanding.

### Section owners

Derived from three sources: the presenter of each deck section on 21 October (v3 deck), the report section named in each feedback issue (#585–#592, #595, tracked in #596) and merged PR authorship. Where the sources disagree, the work was moved to balance load; the issue's original owner reviews.

| Member (GitHub) | Sections | Diagrams | Feedback closed | Basis |
|---|---|---|---|---|
| Lim Jiechao (`limjiechao`) | 1, 2, 3.3, 4.4, 6; final PDF build | D1, D2 | Q1, Q3, Q8, success criteria | Presented intro, conduct, analytics; owns #675, #676, #681. Takes 3.3 and 4.4 from Eric to balance load |
| Wang Ziang, Eric (`EricWang1358`) | 3.1 (3.1.1–3.1.4) | D3, D3b, D4, D4a, D5b, D7a–D7d, D8, D8b | F3, F4, F8 (logical), F9, F12 | Presented logical design and DDD; assigned #586, #587, #595 |
| Liu Jin (`l0sgAi`) | 3.2 (3.2.1–3.2.3), 3.4 | D5, D5a, D6, D9a–D9d | F6, F8 (physical), F11 | Presented physical design; built the SWE5001 infrastructure; assigned #586, #588, #589 |
| Yu Qianshuo (`Yorick-Yu`) | 3.2.4, 4.2 | D11, D12a, D12b, D13, D14a, D14b | F5, F7, F10 | Assigned #585, #590; wrote the outbox and the failure drills (#459). Did not present, so takes a full design section |
| Panpakorn Siripanich, Pan (`PPsyrius`) | 4.1, 4.3, 5 | D10a, D10b | F1, F2 | Presented DevOps and security; wrote the k6 tooling and the V2/V3 runs (#458); assigned #592. 4.1 moves from #591's owners (Liu Jin, Eric), who review it |

Reviewer pairs (second owner per [`AGENTS.md`](AGENTS.md#workflow-per-section)): Eric ↔ Yu Qianshuo (DDD and class diagrams must use the same names), Liu Jin ↔ Pan (physical, security and performance share the network and scaling facts), Lim reviews Liu Jin's 3.4 and Eric reviews Lim's 3.3.

## Citation snapshot policy (11 November 2026)

Report PDF hyperlinks must be absolute repository or external URLs, not paths relative to the checkout. Historical implementation claims retain the revision actually inspected; each dated evidence record separately identifies the tested application and collector/tooling revisions. Restored November evidence uses draft `blob/dev/` destinations until publication. Those new paths are not claimed to exist remotely while the work remains local. At evidence freeze, the editor must publish the records, replace these draft URLs with the full commit SHA containing the frozen records, compile again, and check PDF `/URI` annotations and destination accessibility outside the checkout. Do not repin historical source claims to a newer implementation simply to make all links use one SHA. A portable URL is not proof of publication or of performance acceptance.

## Cross-section facts

Facts that one section states and another depends on. The owner of the second section checks them before the final review. The 6 November 2026 reconciliation aligned the aggregate names and AD-01 to AD-06. On 11 November the report adopted the boundary of PR #747 (unmerged) for `ShareInvite` and `ShareCredit` across 3.1.4, 3.2.3, D4 and D7, with three code-checked corrections: `SessionStpInterface` reads the RBAC snapshot through Sa-Token's `SaTokenDao` (not `RedisLoginSnapshotGateway`, which writes it at login); the internal route is `/internal/users/{id}/active`; ADR 0003 is `0003-share-credit-post-commit-subscriber.md`. The EventStorming study keeps its own count of 23 operations.

| Stated in | Fact | Must match |
|---|---|---|
| 3.1.4 (Eric) | `ShareInvite` (`biz_share_invite`, `ShareInviteRepository`) and `ShareCredit` (`biz_share_credit`, `ShareCreditRepository`) are aggregate roots; both reference `GroupDeal` by `dealId`, outside its lock; `ShareCreditSubscriber` writes the credit `AFTER_COMMIT` in `REQUIRES_NEW` (ADR 0003). Eleven aggregate roots in all, plus the refund and review records, which are not roots | 3.2.3 aggregate-to-table mapping, D4, D7a–D7d |
| 3.2.2 (Liu Jin) | The committed size is 0.5 vCPU, 1 GiB and `db.t4g.micro`; V3 r4 ran commerce at 1 vCPU, 2 GiB and RDS at `db.t4g.small` as session-only settings; §4.1 reports the tested 600 req/s under one-to-six scaling, not per-task saturation or linearity | 4.1 (Pan) |
| 3.4 AISS-13 (Liu Jin) | The identity outage and a poison message have local results only; V4 and V5 were not run | 4.2 (Yu Qianshuo) |
| 3.2.3 (Liu Jin) | Settlement lock order team → purchase intent → payment | 3.2.4 UC4 sequence (Yu Qianshuo) |
| 3.4 AISS-04, -08, -09, -15 (Liu Jin) | Security gaps and their R1 targets | 4.3 (Pan) |
| 3.2.2 (Liu Jin) | The cost figure lives only in §6.3; §3.2.2 points to it | 6 (Lim) |
| 3.1 (Eric), 3.2 (Liu Jin) | Queues are `blocbuy-swe5001-notification` and `blocbuy-swe5001-audit` on AWS; `notification-events` is the local ElasticMQ name only | 4.x, 5.x |

## Chapter outline

Template headings are fixed. "Must contain" lists what an assessor will look for; "Sources" lists where the facts already are. Owners are confirmed in [Section owners](#section-owners).

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
| 3.1.2 Tiers and Layers | Tiers, and the COLA layers inside each service; the 19 architecture checks (17 ArchUnit rules + 2 plain tests) that enforce them | [`02-proposed-architecture.md` §2.1](../architecture-options-study/02-proposed-architecture.md), backend constraints skill |
| 3.1.3 Nodes and Subsystems | F12: redrawn logical view (D3, D3b); element catalogue: element, responsibility, provider | `d3-logical-view`, `d3b-logical-events-data` |
| 3.1.4 Platform Design | F3, F4, F9, F13: EventStorming method, subdomains, context map with patterns, aggregates and how they were derived, domain event catalogue (internal vs published), how producers and consumers use the platform (API contract, versioned events) | [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§6, `d4-context-map`, `d4a-eventstorming-timeline.typ`, `d7a-aggregates` to `d7d-aggregates`, `d8-domain-events`, `d8b-integration-events`, three source-book statecharts, [`contracts/openapi.json`](../../../contracts/openapi.json) |

**3.2 Physical Architecture & Design (owner: Liu Jin; 3.2.4 Yu Qianshuo)**

| Section | Must contain | Sources |
|---|---|---|
| 3.2 (intro) | UML deployment diagrams (D5, D5a) and network diagram (D6): VPC, subnets per tier and zone, security-group flows, CloudFront, ALB, ECS, RDS, Valkey, SNS/SQS, NAT; F6 | `d5-physical-deployment`, `d5a-communication-paths`, [`infra/swe5001/`](../../../infra/swe5001/README.md) |
| 3.2.1 Key Architectural Decisions | AD table continued: Fargate vs EKS, ARM, Spot, disposable runtime, fck-nat, one database server, per-service databases | Deck backup B1b, [`04-aws-cost-estimate.md`](../architecture-options-study/04-aws-cost-estimate.md) |
| 3.2.2 Technology and Services | Each cloud service with its trade-off and rationale; sizes and versions | Deck backup B7, Terraform modules |
| 3.2.3 Persistence Design | F11: data groups in the template format (format, storage, security, trade-off); ER diagram per database (D9); Valkey key model; event envelope and queue schema; transactions, lock order, scheduler lease, outbox; retention per data group | Flyway migrations in [`backend/blocbuy-bootstrap/src/main/resources/db/migration/`](../../../backend/blocbuy-bootstrap/src/main/resources/db/migration/), ADRs 0001, 0002, 0007, [`infra/swe5001/db-init/init.sh`](../../../infra/swe5001/db-init/init.sh) |
| 3.2.4 Detailed Design | F5, F7, F10: per critical use case, a class diagram and a sequence diagram with real class and method names; the two-user group-buy flow; design patterns used and why | [`backend/docs/design/paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md); the Appendix A source book (UC4, UC5, UC6 and pay upfront), whose content the section carries itself |

**3.3 Other Architectural Decisions (owner: Lim)** — decisions not covered above; reference the quality-attribute sections instead of repeating them.

**3.4 Architectural Limitation (owner: Liu Jin)** — the single AISS register in the template's table format (issue and impact, description, resolution, owner, status), each open item with a roadmap date.

### 4. Quality Attributes

| Section | Owner | Must contain | Sources |
|---|---|---|---|
| 4.1 Performance | Pan | F1: workload model, metric definitions and thresholds, every run with its verdict, the V3 r4 result with its limits; caching and load balancing; scaling policy; capacity statement (measured, projected, next limit) | [`perf/k6/`](../../../perf/k6/README.md), [V2 baseline](../evidence/20261016-http-baseline-dashboard/README.md), [V3 r4](../evidence/20261111-v3-r4/README.md) |
| 4.2 Availability | Yu Qianshuo | Two zones, single-zone data tier, self-healing, rollback, failure drills with "AWS" or "local only" stated per result | Evidence `20261015-local-failure-rehearsal`, deck backup B12 |
| 4.3 Security | Pan | Layered controls: Organization guardrails, Identity Center, OIDC, network isolation, encryption, secrets, pipeline scans; known gaps | Deck slide 22, [`infra/swe5001/github/`](../../../infra/swe5001/github/) |
| 4.4 Extensibility and Maintainability | Lim | Measured reuse (audit queue), service template, architecture rules, API contract checks | Deck slide 11 |

### 5. DevOps and Development Lifecycle (owner: Pan)

| Section | Must contain | Sources |
|---|---|---|
| 5.1 Source Control Strategy | Repository layout, branches (`dev`, `prod`), protection rules, how developers authenticate | [`CONTRIBUTING.md`](../../../CONTRIBUTING.md), [`impl-github-configuration.md`](../impl-github-configuration.md) |
| 5.2 Continuous Integration | F2: pipeline diagram (D10) with trigger, jobs, tests and gates per job; image build → Trivy scan → export as one chain | [`.github/workflows/`](../../../.github/workflows/), especially `swe5001-service-images.yml`, `ci-backend.yml`, `security.yml` |
| 5.3 Continuous Delivery | Environments, promotion PR into `prod`, approvals, create → deploy → smoke → release → destroy, timings from the 17 October run | `swe5001-deploy.yml`, `swe5001-release-gate.yml`, deck slides 19–20 |

### 6. Other things to be highlighted (owner: Lim)

Technical findings from the real cloud (deck backup B8), analytics (B11), cost (B9), and how the briefing's success criteria are met.

## Diagrams

The view set answers F8, F9 and F10. Every diagram is UML or a named notation, has a caption stating its viewpoint, and is referenced from the text. The report lists the views in its view catalogue (`tbl-views`, chapter 3 opener).

**Appendix A is a source book, not part of the report.** `sections/07-appendix-a.typ` and `sections/appendix-a/` hold the team's full analysis-to-design specification for every use case; `main.typ` does not include it. Chapters 1–6 stand alone: they never reference an Appendix A figure, table or section, and where they need its content they carry it (a short table, a sentence, or a source-book diagram placed unchanged with `afig` and a body label).

| ID | View | Notation | Section | Source (id) |
|---|---|---|---|---|
| D1 | System context (actors, external systems) | UML component diagram at C4 level 1 | 1.3 | `d1-system-context` |
| D2 | Use cases | UML use case diagram | 1.4 | `d2-use-cases` |
| D3, D3b | Logical view (F12) in two figures: request path; events and data | UML component diagrams, role names only | 3.1.3 | `d3-logical-view`, `d3b-logical-events-data` |
| D4 | Context map | UML component diagram with DDD context-map labels | 3.1.4 | `d4-context-map` |
| D4a | EventStorming process-level timeline, UC3–UC6 | Typst table in sticky colours (not a kit drawing) | 3.1.4 | [`d4a-eventstorming-timeline.typ`](diagrams/d4a-eventstorming-timeline.typ) |
| D5, D5a | Physical deployment: nodes, artefacts, zones and subnets; communication paths | UML deployment diagrams | 3.2 | `d5-physical-deployment`, `d5a-communication-paths` |
| D5b | Logical deployment: tiers to node types, no products | UML deployment diagram | 3.1.2 | `d5b-logical-deployment` |
| D6 | Network (F6), with the security-group table beside it | AWS architecture diagram conventions (groups, no service icons), drawn in Typst with the kit palette (not a kit drawing) | 3.2 | [`d6-network.typ`](diagrams/d6-network.typ), [`d6-security-groups.typ`](diagrams/d6-security-groups.typ) |
| D7a–D7d | Aggregates by context | UML class diagrams | 3.1.4 | `d7a-aggregates`, `d7b-aggregates`, `d7c-aggregates`, `d7d-aggregates` |
| (life cycles) | Team, purchase intent and checkout payment state machines; order, listing and refund life cycles as a table | UML state machine diagrams (source-book diagrams reused unchanged) and `tbl-lifecycles` | 3.1.4 | `a-uc05-st-deal`, `a-uc03-st-intent`, `a-pay-st-payment` (labels `fig-st-team`, `fig-st-intent`, `fig-st-payment`) |
| D8, D8b | In-process domain events; integration events from the outbox to the consumers | UML component diagrams | 3.1.4 | `d8-domain-events`, `d8b-integration-events` |
| D9a–D9d | Data model per database (identity, notification, commerce team, commerce payment) | UML class diagrams with the «table» stereotype | 3.2.3 | `d9a-identity-data`, `d9b-notification-data`, `d9c-commerce-team-data`, `d9d-commerce-payment-data` |
| D10a, D10b | Continuous integration; delivery | UML activity diagrams | 5.2, 5.3 | `d10a-ci-image-chain`, `d10b-delivery` |
| D11 | UC4 design classes (a subset of the source book) | UML class diagram | 3.2.4 | `d11-uc4-design-classes` |
| D12a, D12b | UC4 with two buyers: the leader starts, the member joins; the last-seat race is drawn in full only in the source book | UML sequence diagrams | 3.2.4 | `d12a-two-buyers-start`, `d12b-two-buyers-join` |
| D13 | Team expiry design classes (a subset of the source book) | UML class diagram | 3.2.4 | `d13-expiry-design-classes` |
| D14a–D14c | Team expiry; refund retry; outbox relay | UML sequence diagrams | 3.2.4 | `d14a-team-expiry`, `d14b-refund-retry`, `d14c-outbox-relay` |

Every UML diagram, in the chapters and in the Appendix A source book, is drawn with one kit (`docs/swe5006/report/typst/uml.typ`): its source is `diagrams/appendix-a/src/<id>.mmd`, generated into `gen/<id>.typ` and placed with `afig` (see [`diagrams/appendix-a/README.md`](diagrams/appendix-a/README.md)). D4a and D6 are the two non-UML views; they use the kit's palette and fonts.

The additional §4.1 [V3 r4 timeline](diagrams/performance-v3-timeline.svg) is a data plot, not an architecture view. Its adjacent [Python source](diagrams/performance-v3-timeline.py) regenerates the SVG from the V3 r4 evidence package only, with the standard library (`python docs/swe5001/report/diagrams/performance-v3-timeline.py`, run in Docker; `--check` compares without writing). Client completion bins and complete UTC CPU minutes keep their separate time origins; task-count dots are actual samples; shading marks the tail after the canonical observer ended. The offered schedule is a reference, not reconstructed arrivals. The SVG and Python source come from Pan's commit `0405b1f0`.

The 12 November [audit follow-up](../evidence/20261112-audit-followup/README.md) is reflected in §3.2 as a topology table (`tbl-topologies`) and observed task zones, not as a separate D5 drawing; its typed schema dictionary is covered by D9a–D9d with `tbl-invariant-indexes` and `tbl-aggregate-tables`.

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
