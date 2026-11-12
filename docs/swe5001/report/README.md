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

## Focus areas

The assessors said three things were not shown enough in the presentation: DDD thinking, class-level design of the core functionality, and the database schema. They are the report's priority. Each needs the artefacts below; a section that lacks one is not done.

### A. DDD design and thinking (F3, F4, F9; section 3.1.4)

Show the reasoning, not only the result.

1. **Method.** How EventStorming ran: big picture (events on a timeline), then hot spots, then process level (commands, policies, read models), then strategic design. One figure of the event timeline for UC3–UC6. Source: [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§3.
2. **Subdomains.** Table: subdomain, type (core, supporting, generic), why, build or buy (Google sign-in and Stripe are bought). Source: §4.2.
3. **Bounded contexts and context map (D4).** Every edge labelled upstream or downstream and with its pattern: customer–supplier, conformist, anti-corruption layer (around Google and Stripe), open host service (the public API), published language (the versioned events). Say why each pattern was chosen. Source: §4.3–§4.5.
4. **From contexts to services.** Why four contexts share commerce-svc (they share one settlement transaction) and which became services. Source: §6, deck slide 7.
5. **Aggregate derivation (F4).** For each aggregate, one table row: the commands it handles, the invariant it protects ("a team never has more members than its size"), the consistency boundary, the root, its entities and value objects, the events it emits. State the rule used (one invariant, one transaction; reference other aggregates by ID) and explain the accepted settlement exception: one transaction spans team, purchase intent, payment and order writes. Distinguish that shared transaction boundary from lock acquisition; use the [verified lock order](AGENTS.md#claims-that-are-easy-to-get-wrong) for the UC4 sequence.
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

Settled on 4 November 2026 (Phase 0). Sections are written against these; change them only by editing this list in a pull request.

1. **F6, data subnets: implemented.** Two data subnets (`10.50.20.0/24`, `10.50.21.0/24`, one per zone) hold RDS and Valkey; their route table has only the local VPC route, so there is no NAT, internet or S3-endpoint path. ECS tasks stay in the private application subnets. `terraform test` (`data_tier_has_its_own_subnets`) asserts the two sets are disjoint. Configured and tested offline, not yet applied on AWS: the next runtime session creates it (the runtime is rebuilt from zero each session, so there is no migration). Recorded as AD-15 and AISS-07 (#589). Write it in 3.2 as "configured" until an AWS session proves it.
2. **One AISS register** and 3. **one AD register**: [`registers.md`](registers.md). The deck backup slides B1–B2 are the base; the study's and the main slides' IDs are not used. The register maps old IDs to new ones and each AD to its ADR.
4. **Critical use cases for 3.2.4.** UC4 join a team by paying, and team expiry with refund (both confirmed). Both are already drawn at component level in [`docs/presentation-kit/diagrams/`](../../presentation-kit/diagrams/); the report needs them at class level.
5. **Section owners.** Confirmed below.

10 November 2026 reconciliation (#775): the [dated session record](../evidence/20261110-subnet-verification/README.md) now verifies AD-15 placement and required connectivity on AWS; AISS-07 is resolved for that recorded session. This supersedes item 1's earlier “configured only” writing instruction, not its original decision. Negative packet/failover tests remain outside the result. The [pool/scaling experiment disposition](../evidence/20261110-load-preflight/README.md#10-november-disposition-for-775), [operator edge repair](../evidence/20261109-edge-policy-recovery/README.md) and D10 in §5 close the other report gaps without importing executable fixes from PR #784.

11 November 2026 release/session distinction: the [released-image baseline record](../evidence/20261111-load-reruns/README.md) and selected R4 campaign identify released source `3a893036` separately from hashed session tooling and applied configuration. R4 commerce 1 vCPU/2 GiB, RDS `db.t4g.small` and its CPU guard are session facts, not merged default IaC. §4.1 owns the measured result and limits, §4.3 the metadata boundary and §5 the operational provenance; earlier campaign records remain archived outside the report's selected V3 results. Evidence publication/pinning and second-owner review remain outstanding.

R4 results-selection and integration addendum (11 November campaign): the owner selected [AWS V3 R4](../evidence/20261111-v3-r4/README.md) as the **sole V3 results dataset for the report**, superseding earlier campaigns as the report's chosen V3 result, not rewriting their history. §4.1's results, workload/scaling tables and regenerated timeline now use R4 only; §§2, 3.2/3.4, 5 and 6 and AISS-12 reconcile the related status, sizing, operational provenance and capacity boundaries. Native workload success remains distinct from canonical analyzer exit 1 and the outer-upload failure; no owner acceptance or controlled linearity is inferred. The source package includes verified request/latency aggregates, completion-minute CSVs, scaling actions, source-preserving service/CloudWatch projections and a portable checker. Source publication, full evidence-commit pinning, PDF verification and second-owner review remain subject to the checks recorded with this integration.

### Section owners

Derived from three sources: the presenter of each deck section on 21 October (v3 deck), the report section named in each feedback issue (#585–#592, #595, tracked in #596) and merged PR authorship. Where the sources disagree, the work was moved to balance load; the issue's original owner reviews.

| Member (GitHub) | Sections | Diagrams | Feedback closed | Basis |
|---|---|---|---|---|
| Lim Jiechao (`limjiechao`) | 1, 2, 3.3, 4.4, 6; final PDF build | D1, D2 | Q1, Q3, Q8, success criteria | Presented intro, conduct, analytics; owns #675, #676, #681. Takes 3.3 and 4.4 from Eric to balance load |
| Wang Ziang, Eric (`EricWang1358`) | 3.1 (3.1.1–3.1.4) | D3, D4, D7, D8 | F3, F4, F8 (logical), F9, F12 | Presented logical design and DDD; assigned #586, #587, #595 |
| Liu Jin (`l0sgAi`) | 3.2 (3.2.1–3.2.3), 3.4 | D5, D6, D9 | F6, F8 (physical), F11 | Presented physical design; built the SWE5001 infrastructure; assigned #586, #588, #589 |
| Yu Qianshuo (`Yorick-Yu`) | 3.2.4, 4.2 | D11, D12, D13, D14 | F5, F7, F10 | Assigned #585, #590; wrote the outbox and the failure drills (#459). Did not present, so takes a full design section |
| Panpakorn Siripanich, Pan (`PPsyrius`) | 4.1, 4.3, 5 | D10 | F1, F2 | Presented DevOps and security; wrote the k6 tooling and the V2/V3 runs (#458); assigned #592. 4.1 moves from #591's owners (Liu Jin, Eric), who review it |

Reviewer pairs (second owner per [`AGENTS.md`](AGENTS.md#workflow-per-section)): Eric ↔ Yu Qianshuo (DDD and class diagrams must use the same names), Liu Jin ↔ Pan (physical, security and performance share the network and scaling facts), Lim reviews Liu Jin's 3.4 and Eric reviews Lim's 3.3.

## Citation snapshot policy (11 November 2026)

Report PDF hyperlinks must be absolute repository or external URLs, not paths relative to the checkout. Historical implementation claims retain the revision actually inspected; each dated evidence record separately identifies the tested application and collector/tooling revisions. Restored November evidence uses draft `blob/dev/` destinations until publication. Those new paths are not claimed to exist remotely while the work remains local. At evidence freeze, the editor must publish the records, replace these draft URLs with the full commit SHA containing the frozen records, compile again, and check PDF `/URI` annotations and destination accessibility outside the checkout. Do not repin historical source claims to a newer implementation simply to make all links use one SHA. A portable URL is not proof of publication or of performance acceptance.

## Cross-section facts

Facts that one section states and another depends on. The owner of the second section checks them before the final review. The 6 November 2026 reconciliation aligned the aggregate names and AD-01 to AD-06. The 9 November local follow-up adopts the repository-boundary correction proposed in PR #747 for `ShareInvite` and `ShareCredit` across 3.1.4, 3.2.3, D4 and D7; this does not resolve or merge the remote PR. A second-owner review remains required.

| Stated in | Fact | Must match |
|---|---|---|
| 3.1.4 (Eric) | `ShareInvite` and `ShareCredit` have separate repositories and reference `GroupDeal` by `dealId`, outside its aggregate lock; credit is written `AFTER_COMMIT` in `REQUIRES_NEW` (ADR 0003) | 3.2.3 aggregate-to-table mapping, D4, D7; second-owner review pending |
| 3.2.2 (Liu Jin) | R4 retained commerce 1 vCPU/2 GiB and RDS small class; §4.1 reports the tested 600-RPS browse peak under one-to-six scaling, not per-task saturation or controlled linearity | 4.1 (Pan); second-owner review required |
| 3.4 AISS-13 (Liu Jin) | The identity outage and a poison message have local results only; V4 and V5 were not run | 4.2 (Yu Qianshuo) |
| 3.2.3 (Liu Jin) | Settlement lock order team → purchase intent → payment | 3.2.4 UC4 sequence (Yu Qianshuo) |
| 3.4 AISS-04, -08, -09, -15 (Liu Jin) | Security gaps and their R1 targets | 4.3 (Pan) |
| 3.2.2 (Liu Jin) | Measured cost not cited until a record is committed (red marker) | 6 (Lim) |
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
| 3.1.2 Tiers and Layers | Tiers, and the COLA layers inside each service; the 18 architecture rules that enforce them | [`02-proposed-architecture.md` §2.1](../architecture-options-study/02-proposed-architecture.md), backend constraints skill |
| 3.1.3 Nodes and Subsystems | F12: redrawn logical view (D3); element catalogue: element, responsibility, provider | `logical-view.svg` |
| 3.1.4 Platform Design | F3, F4, F9: EventStorming method, subdomains, context map with patterns, aggregates and how they were derived, domain event catalogue (internal vs published), how producers and consumers use the platform (API contract, versioned events) | [`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §1–§6, `context-map.svg`, `aggregates.svg`, [`contracts/openapi.json`](../../../contracts/openapi.json) |

**3.2 Physical Architecture & Design (owner: Liu Jin; 3.2.4 Yu Qianshuo)**

| Section | Must contain | Sources |
|---|---|---|
| 3.2 (intro) | UML deployment diagram (D5) and network diagram (D6): VPC, subnets per tier and zone, security-group flows, CloudFront, ALB, ECS, RDS, Valkey, SNS/SQS, NAT; F6 | `physical-deployment.svg`, [`infra/swe5001/`](../../../infra/swe5001/README.md) |
| 3.2.1 Key Architectural Decisions | AD table continued: Fargate vs EKS, ARM, Spot, disposable runtime, fck-nat, one database server, per-service databases | Deck backup B1b, [`04-aws-cost-estimate.md`](../architecture-options-study/04-aws-cost-estimate.md) |
| 3.2.2 Technology and Services | Each cloud service with its trade-off and rationale; sizes and versions | Deck backup B7, Terraform modules |
| 3.2.3 Persistence Design | F11: data groups in the template format (format, storage, security, trade-off); ER diagram per database (D9); Valkey key model; event envelope and queue schema; transactions, lock order, scheduler lease, outbox; retention per data group | Flyway migrations in [`backend/blocbuy-bootstrap/src/main/resources/db/migration/`](../../../backend/blocbuy-bootstrap/src/main/resources/db/migration/), ADRs 0001, 0002, 0007, [`infra/swe5001/db-init/init.sh`](../../../infra/swe5001/db-init/init.sh) |
| 3.2.4 Detailed Design | F5, F7, F10: per critical use case, a class diagram and a sequence diagram with real class and method names; the two-user group-buy flow; design patterns used and why | [`backend/docs/design/paid-group-participation.md`](../../../backend/docs/design/paid-group-participation.md), `uc4-join-sequence.svg`, `expiry-refund-sequence.svg` |

**3.3 Other Architectural Decisions (owner: Lim)** — decisions not covered above; reference the quality-attribute sections instead of repeating them.

**3.4 Architectural Limitation (owner: Liu Jin)** — the single AISS register in the template's table format (issue and impact, description, resolution, owner, status), each open item with a roadmap date.

### 4. Quality Attributes

| Section | Owner | Must contain | Sources |
|---|---|---|---|
| 4.1 Performance | Pan | F1: workload model, metric definitions and thresholds, baseline and selected R4 results with limits; caching/load balancing; scaling policy; measured capacity boundary | [`perf/k6/`](../../../perf/k6/README.md), [V2 baseline](../evidence/20261016-http-baseline-dashboard/README.md), [V3 R4](../evidence/20261111-v3-r4/README.md) |
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

The view set answers F8, F9 and F10. Every diagram is UML or a named notation, has a caption stating its viewpoint, and is referenced from the text.

| ID | View | Notation | Section | Starting point |
|---|---|---|---|---|
| D1 | System context (actors, external systems) | C4 level 1 or UML use case | 1.3 | `06-eventstorming-ddd.md` §2.1 |
| D2 | Use case diagram | UML | 1.4 | Proposal |
| D3 | Logical view, redrawn (F12) | UML component diagram, or logical detail deployment diagram with logical nodes | 3.1.3 | Content of `logical-view.svg`, redrawn to the logical-view rules |
| D4 | Context map | DDD context map with patterns | 3.1.4 | `context-map.svg` |
| D5 | Distributed deployment, topology comparison and dated zone allocation | UML deployment (nodes, artefacts, zones) | 3.2 | [`d5-physical-deployment.typ`](diagrams/d5-physical-deployment.typ), [`d5-topology-comparison.typ`](diagrams/d5-topology-comparison.typ) |
| D6 | Network and security groups | AWS architecture icons | 3.2 | New; shows the data subnets (F6) |
| D7 | Aggregates and life cycles | UML class + state machine | 3.1.4 | `aggregates.svg` |
| D8 | Domain event catalogue and producer/consumer flow | Table + UML component | 3.1.4 | ADR 0007 |
| D9 | Logical data models and typed supporting dictionary | UML class for tables + bounded dictionary | 3.2.3 | [`d9-data-models.typ`](diagrams/d9-data-models.typ), [`d9-schema-dictionary.typ`](diagrams/d9-schema-dictionary.typ), released Flyway V1–V28 |
| D10a/b | Service-image job and deployment caller | UML activity, separate views | 5.2, 5.3 | [`d10-cicd-pipeline.typ`](diagrams/d10-cicd-pipeline.typ); broader CI paths are tabulated in §5.2 |
| D11 | UC4 class diagram | UML class | 3.2.4 | New |
| D12 | UC4 sequence, two users and the last seat | UML sequence | 3.2.4 | `uc4-join-sequence.svg` |
| D13 | Team expiry and refund class diagram | UML class | 3.2.4 | New |
| D14 | Team expiry and refund sequence | UML sequence | 3.2.4 | `expiry-refund-sequence.svg` |

The additional §4.1 [V3 R4 timeline](diagrams/performance-v3-timeline.svg) is a data plot, not an architecture view. Its adjacent [Python source](diagrams/performance-v3-timeline.py) regenerates the SVG exclusively from the portable R4 package with the standard library (`python docs/swe5001/report/diagrams/performance-v3-timeline.py`, run in Docker; `--check` compares without writing). Client completion bins and complete UTC CPU minutes retain their separate time origins; task-count dots are actual observations. Shading marks the tail after the canonical observer ended, covered separately by supplemental samples. The offered schedule is a reference, not reconstructed arrivals; the plot does not repair the canonical analyzer or prove controlled linearity.

### Three-audit follow-up — 12 November 2026

The [follow-up record](../evidence/20261112-audit-followup/README.md) maps all eight report findings plus terminology/recording/cost corrections, the non-destructive retention safeguards and privacy dispositions. D5 now distinguishes the historical SWE5006 EC2 monolith, configured ECS monolith fallback and distributed ECS, including enabled/disabled messaging; its zone inset separates dated observation from configuration. D9's supporting dictionary covers important typed fields for all 29 owned tables at `3a893036`, with explicit omissions and migration links. The workload/compute matrix distinguishes implemented online/consumer/lease work from proposed batch offload. These §3 amendments await Eric/Liu Jin's review; they do not change the section ownership or certify whole-report acceptance.

D3's editable PlantUML and SVG were regenerated together with checksum-verified PlantUML 1.2026.10 and Graphviz in a disposable Java container; renderer packages were installed only inside that container, not on the host or in project dependencies. The D4a Mermaid change is a source comment only; its visible implemented-versus-modeled legend is in the report caption, so its existing SVG need not change. See the [fresh source/PDF receipt](../evidence/20261111-v3-r4/report-review.json) for the exact reviewed bytes, chapter-only scope and remaining warnings. Historical metadata exposure, durable archival approval and submission sign-off remain explicitly separate from a clean draft for peer review.

## Briefing coverage

12 November follow-up to [#789](../evidence/20261112-issue-789/README.md): existing evidence is published at immutable `0405b1f0`, still separate from `dev` integration. The [current verification](../evidence/20261112-issue-789/verification.json) covers the factual/review corrections; original R4 core data and scan/review receipts remain unchanged historical snapshots. Its README has a dated publication addendum with verbatim original README/manifest copies and an explicitly new current manifest. The refreshed [peer-source patch](../evidence/20261112-issue-789/peer-report.patch) targets the styled/restructured source at `02c7b822354656a4b6b7d269c876bc0e76635295`; its [hash manifest](../evidence/20261112-issue-789/peer-base.json) and [closure map](../evidence/20261112-issue-789/closure-checklist.md) cover the nine original items and ten additional review points. The owner's [140-page styled PDF](../evidence/20261112-peer-style-pdf-review/README.md) is the before artifact; the fresh corrected peer integration build is a separate artifact, not an edit to that file or a claim that its remote branch has changed. Preserve the peer's structure/style when integrating, and revalidate an advanced base.

Later on 12 November, the owner separately authorized runtime and generator retirement after log archival. The [safe cleanup projection](../evidence/20261112-issue-789/cleanup.json) records 127 Terraform-managed runtime deletes by 12:13:20 SGT, completed generator retirement by 12:06:02, normal `env-status.sh` exit 0 and 51 independent checks at 12:14:42. Sections 2, 4.1, 5.3 and 6 now distinguish that operator recovery from the failed nightly workflow and historical R4 stop. The private S3 evidence/log archives and one completed encrypted generator-disk snapshot remain chargeable; foundation/edge and SWE5006 were not targeted. The new cleanup projection and its PDF URL remain local/draft until explicitly published and pinned; no all-stack destruction, zero-cost account or submission acceptance is claimed.

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
