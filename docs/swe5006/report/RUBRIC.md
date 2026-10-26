# SWE5006 Final Report Rubric

Team 8 · BlocBuy · 26 Oct 2026 · harness for #463 and #695

## Sources and how to read this

The final report is due **31 Oct 2026, 23:59** (Chandrasekaran, 26 Oct; #695 supersedes the 1 Nov date in #463 and BRF slide 7). No marking rubric for the report has been published, so this rubric is built from the documents that state what the report must contain, then from team rulings. It is the specification the report is drafted and graded against, in the same way that [`../presentation/RUBRIC.md`](../presentation/RUBRIC.md) governs the deck.

| Code | Document | What it governs |
| --- | --- | --- |
| TPL | Project Report Template for Practice Module (`SWE5006 - Project Report Template for Practice Module.docx`, kept out of the repository) | Chapters, sections, their order and what each must contain (the italic blue guidance text) |
| BRF | Practice module briefing deck (Chandra, 20 Aug 2026) | Project-wide deliverables (slides 5–6: code base, agile artefacts, readable diagrams, unit tests for complex classes), schedule (slide 7: the report incorporates presentation remarks), weights (slide 13: report 80% including 5% peer assessment) |
| REM | Lecturer remarks from the 26 Oct presentation, collated into one GitHub issue (presentation rubric G9) | What the report must answer or correct after the talk |
| PROP | Our submitted proposal (27 Aug 2026) | What we told the lecturers we would build. Not a marking document; it is the benchmark for scope and status |
| SAD, SDP, EPAT, DSOEA | Module slides and memos, cited as module, deck and page (for example SDP 05a p35) | Course vocabulary and what lecturers look for |
| PLAN | The report plan: index comment on #463 and the chapter plans #675–#681 (24–26 Oct) | Section numbers, owners, sources per bullet, GAPs and decisions D1–D14 |
| Team | Our own ruling | Everything the documents above do not state |

Precedence, highest first:

1. TPL for structure and per-section content.
2. BRF for project-wide deliverables and dates; #695 for the submission date.
3. REM for corrections and additions the lecturers asked for.
4. Module material for vocabulary and framing.
5. Team rulings, always labelled as such, including the settled PLAN decisions. A ruling that uses PROP as a benchmark says so.

In every table, **Source** cites what a document requires and **Ruling** records what we decided. A ruling is never presented in the report as a course requirement, and "GoF only" is a team reading of the module pattern index, not a template rule (PLAN D1).

## Ground rules

| # | Rule | Source | Ruling |
| --- | --- | --- | --- |
| G1 | Deadline and schedule | #695 | First integrated draft 27 Oct EOD (Lim); each member vets their own sections and reviews the rest 28–29 Oct; content freeze 30 Oct EOD; final proofread and submission by 31 Oct 23:59, submitter and time posted on #695 |
| G2 | Follow the template | TPL | Keep every template chapter and section, its number, title and order. Add subsections only beneath a template section. Correct the template typo "cotribution" in chapter 5's title (PLAN D11). No executive summary (TPL has none) |
| G3 | Template placeholders | TPL TOC | `<Transition Strategy N>` → 3.2.1–3.2.9, `<Use Case Description N>` → 3.3.2–3.3.12, `<Use Case N> by <Member>` → 3.4.1–3.4.5, `<Design Problem N> by <Member>` → 3.5.1–3.5.5, numbered in the 24 Oct §2 order: 1 Eric, 2 Yorik, 3 Pan, 4 Lim, 5 Liu Jin (PLAN part 1). The numbers are fixed; the deck cites them |
| G4 | One use case and one design problem per member | TPL 3.4, 3.5 | Every member's name is in the heading of at least one 3.4.N and one 3.5.N. Code written by someone else is credited in that section and in chapter 5 (cross-credit lines, PLAN #681) |
| G5 | English only | `CONTRIBUTING.md` | All committed and submitted text is English |
| G6 | Evidence | Team | Every factual claim is traceable to merged code, a GitHub issue or PR, the board, a CI run, AWS (read-only) or a recorded artefact, and states its kind (see Evidence kinds). Never upgrade a result |
| G7 | One freeze SHA | Team (PLAN D6, part 4) | On 30 Oct pick one `dev` commit. Every count, version, coverage figure and status in the report is re-read at that SHA or dated explicitly |
| G8 | Readable without GitHub | BRF slide 6; team | The marker reads the PDF. Name work by what it does; explain BlocBuy terms (bloc, team, Sandbox) at first use. Issue and PR numbers may appear only in tables and appendices, never as the only explanation. ADR numbers are allowed because Appendix B lists them |
| G9 | Course vocabulary | Module material | Analysis objects as «boundary», «control», «entity» (SAD); EPAT DoD ladder (coded, tested, integrated, releasable, released); Agile Testing Quadrants; Continuous **Delivery** for a manually approved promotion (EPAT 07 CI p34–35); GoF class-level pattern names; a simple static factory is not GoF Factory Method (SDP 01a p32–35) |
| G10 | Incorporate presentation remarks | BRF slide 7; REM | Every remark in the REM issue is turned into a report task tagged with a section number and either answered in the report or recorded as not adopted, with a reason, in the Remarks register below |
| G11 | Code base deliverable | BRF slide 5 | Repository URL on the title page; lecturers get read access or a `git archive` of the freeze SHA (PLAN D13, #693) |
| G12 | Figures legible in print | BRF slide 6 | Every figure passes F1–F8 |

## Evidence kinds

Each claim is one of these. Say which in the sentence.

| Kind | Wording | Requirement |
| --- | --- | --- |
| Verified on dev | "On 23 October on dev, …" | Link to the record (day log, PR body, recording, acceptance doc) |
| Verified on the SWE5001 environment | "On the SWE5001 ECS environment (not the SWE5006 dev host), …" | Only as context; never as a SWE5006 result |
| Verified locally | "Tested locally (Testcontainers), not on dev: …" | Link to the local record |
| Calculated or estimated | "Calculated: …", "Agreed estimate: …" | Show the formula or the source |
| Designed, not built | "Designed but not implemented: …" | Point to the design artefact |

## Claims that are easy to get wrong

Settled in PLAN; re-check at the freeze SHA.

| Topic | Correct statement | Source |
| --- | --- | --- |
| Systems | SWE5006 system = `dev`, a modular monolith on one EC2 host behind CloudFront. Production is the shared SWE5001 release (ruleset prod-release, code-owner-approved promotion PR); no separate SWE5006 prod stack was built (#226) | backend ADR 0008; presentation R 4.4 |
| Expiry | The design bound is one sweep interval plus sweep time, not a one-minute guarantee. Local worst lag 58.18 s over 30 teams; one dev bloc closed 18.9 s after its deadline and was refunded 40.6 s after (23 Oct). Do not claim the target met on dev | PR #671; `docs/perf/sprint3-baseline.md`; PR #664 |
| Performance | Met locally (30 Sep) and on SWE5001 ECS (16 Oct); never measured on SWE5006 dev (NFR-15) | `docs/quality/nfr-register.md` |
| Scalability | Not a PROP target; not claimed | presentation decision 8 |
| Notifications | Coded, off on SWE5006 dev (ADR 0007); SWE5001 scope, no §3.4 models | PLAN D4 |
| Test counts | One recount at the freeze SHA: static counts from `git ls-tree`, executed counts from the build-quality CI log of the same SHA. Never Sonar's `tests` figure | PLAN D6 |
| Required checks | The live ruleset's 6: `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`; `contract-check` is required by policy only | PLAN D7; `CONTRIBUTING.md` |
| Counts | 17 ArchUnit rules; Flyway V1–V28 (28 SQL files plus one `.conf`); sprint dates from `docs/agile/definition-of-done.md`; sprint tags by tagger date | PLAN part 3 data conflicts |
| Effort | No hours were logged. The only actuals are the person-day split agreed on 19 Oct (Liu Jin 12.5, Pan 11.5, Yorik 9, Lim 8.5, Eric 8.5), shown as an agreed estimate beside planned hours. No PR or commit counts per member | PLAN D12 |
| Static credentials | CI authenticates to AWS through GitHub OIDC; disclose the one accepted IAM user access key in the AWS account governance README | presentation R 4.2 |
| Lock order | ADR 0001 (deal → intent → payment) and ADR 0002 (team → intent → order → payment) are not yet reconciled; state the reconciled order or the open issue (#663) | PLAN #677 3.2.8 |
| Programme name | "Practice Module for Certificate in Designing Modern Software Systems" (template line), not the deck's "Graduate Certificate…" | PLAN part 3 |
| Proposal exclusions | PROP §6.2 excludes notifications "beyond in-app status"; do not write that it excluded in-app notifications | PLAN D4 |

## Section requirements

Owner and plan issue per row; the plan issue holds the bullets and sources to hydrate. "Rubric row" codes are cited in section files and reviews.

### Front matter and chapter 1 Introduction (Lim · #675)

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| F | Title "Project Report for BlocBuy", module line, Team 8, five members' full names, contents | TPL title page | Add the repository URL and the dev URL under the members (G11). Contents regenerated from the headings; footer "Page X of Y" kept |
| 1.1 | Background, context and current status from a project perspective | TPL 1.1 | The problem in one sentence ("demand exists, but no buyer commits first"); the pay-first mechanism; two courses, one repository (state which system this report describes); one status paragraph pointing to 2.2. Do not reuse `README.md`'s organiser-era text |
| 1.2 | Business needs and drivers | TPL 1.2 | Needs per stakeholder; drivers per use case; PROP §6.3 maintainability quote. No invented market figures (GAP: say none exist) |
| 1.3 | Key business and IT stakeholders | TPL 1.3 | Table: stakeholder, kind, interest, how served. Buyer as leader and joiner, merchant (no GUI), admin, Stripe, Google, Scheduler, the team with roles (name who acted as Product Owner, PLAN D11), lecturer. Tools are not stakeholders |
| 1.4 | Scope, phases, references, and architecture-versus-implementation scope | TPL 1.4 | Nine PROP use cases plus admin console; notifications as SWE5001 scope; four sprints as phases; explicitly list what is architected but not implemented (SWE5006 prod EC2 stack, FAILED and CANCELED team states) |
| 1.4.1 | Functionality in scope | TPL 1.4.1 | Table UC1–UC9 plus the «include» Pay upfront: name, status on dev at the freeze date (badges of `use-case.svg`) |
| 1.4.2 | Functionality out of scope | TPL 1.4.2 | PROP §6.2 list verbatim, in diagram order; items dropped during the project with reason; RabbitMQ and Elasticsearch excluded by decision |
| 1.4.3 | Quality attributes | TPL 1.4.3 | Table: attribute, PROP §6.3 target, where verified (section), status (pointer to 2.2). Cite the NFR register; do not copy it. Apply the expiry and performance wording guards |

### Chapter 2 Project Conduct (Lim; Eric supplies Sprint 4 data; Pan the artefacts · #676)

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 2.1 | Rough WBS tasks and estimated effort; who owns (a) each use case's analysis and design and (b) each design problem | TPL 2.1 | Project-level WBS with planned hours; ownership matrix (UC1–UC2 Pan, UC3–UC4 and Pay upfront Eric, UC5 and UC9 Liu Jin, UC6 Yorik, UC7–UC8 Lim; design problems per G3) against PROP §6.1's allocation; roles per sprint (Scrum Master, PO). State the missing S1–S2 sprint-level estimates |
| 2.2 | Current status and outstanding issues | TPL 2.2 | Promised-versus-delivered table against PROP (promised, delivered, changed, dropped), each change citing its ADR or stating that it has none; outstanding issues stated openly; snapshot at the freeze date |
| 2.3 | Milestones achieved and rough total effort per member | TPL 2.3 | Milestone table with achieved dates (sprint tags, releases, demo evidence); DoD ladder step reached per sprint (Sprint 4 as recorded at its review); one retrospective improvement per sprint and the change it made next sprint; effort per PLAN D12 |

### Chapter 3 System Design

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 3.1 | UML views of the software architecture: components if distributed, logical layers and packages; the target platform (stacks, languages, frameworks, databases) | TPL 3.1 | Owner Liu Jin (#677). Describe the SWE5006 monolith: COLA four layers × bounded-context packages (`logical-layers.svg`), the deployment view `dev-deployment.svg` as the component view, the stack with versions at the freeze SHA, each decision by ADR number; decisions without an ADR go to Appendix B. The SWE5001 topology is a short note only (PLAN D10) |
| 3.2 | Transition strategies sufficient to design every use case end to end | TPL 3.2 | Owners Eric (overview, 3.2.1–3.2.4, 3.2.8) and Yorik (3.2.5–3.2.7, 3.2.9) (#677). Overview maps every analysis object type to at least one strategy |
| 3.2.N | Per strategy: analysis object types affected, use case types affected, typical class-diagram changes with rationale, typical sequence-diagram changes with rationale; enough for a newly joined designer | TPL 3.2.1 | Nine strategies: 1 language and framework, 2 presentation, 3 distribution, 4 persistence, 5 external interface, 6 security, 7 scheduling, 8 concurrency, 9 domain events. Each has the four parts, a separate "Rationale:" line for static and for dynamic, and a before/after class and sequence figure. Yorik's refund-recovery mechanisms (Idempotency Key, Retry, operator review) live in 3.2.5 (PLAN D1) |
| 3.3.1 | Use case diagram with in-scope **and** out-of-scope use cases | TPL 3.3.1 | Owner Lim. `use-case.svg`: actors including Stripe, Google and the Scheduler, «include»/«extend» where they apply, out-of-scope package visible, delivery badges re-checked at freeze |
| 3.3.N | Per in-scope use case: flow of events with normal and relevant exceptional flows, detailed enough to identify analysis objects | TPL 3.3.2 | 3.3.2 UC1 Pan · 3.3.3 UC2 Pan · 3.3.4 UC3 Eric · 3.3.5 UC4 Eric · 3.3.6 UC5 Liu Jin · 3.3.7 UC6 Yorik · 3.3.8 UC7 Lim · 3.3.9 UC8 Lim · 3.3.10 UC9 Liu Jin · 3.3.11 Pay upfront («include», Eric, Yorik reviews) · 3.3.12 Receive notifications (Pan, SWE5001 scope). One shape for all: actors, preconditions, trigger, normal flow (numbered), exceptional flows (numbered against the normal step), postconditions |
| 3.4.N | Per member: analysis class diagram; analysis sequence diagram per major flow; design class diagram; design sequence diagram per major flow; design complies with the transition strategies | TPL 3.4.1 | Owners per G3 (#678). Each 3.4.N has the five parts A–E in the 3.4 checklist below. "For each use case in the scope" (TPL): either the full set for every in-scope use case the member owns, or the primary set plus a stated gap; record which (PLAN D3 risk). Statecharts: team, Order and checkout payment in 3.4.1; refund obligation in 3.4.2; listing in 3.5.5 (PLAN D9; resolve the deck's 3.4.5 note before freeze) |
| 3.5.N | Per member: design problem with class and sequence diagrams (brute force if needed); candidate patterns; motivation for the choice; solution class diagram; solution sequence diagram(s); implementation decisions; implementation follows the solution | TPL 3.5.1 | Owners and patterns: 3.5.1 Factory Method (Eric), 3.5.2 Observer (Yorik), 3.5.3 Template Method (Pan), 3.5.4 Strategy (Lim), 3.5.5 State with Optimistic Offline Lock (Liu Jin) (PLAN D1, #679). Each has the parts in the 3.5 checklist below |
| 3.6 | Diagrams of the database schema(s) | TPL 3.6 | Owner Liu Jin. Core ERD in the text (`core-erd.svg`) with keys, cardinality and mapping decisions (UUIDv7 keys, table prefixes, no foreign keys or CHECK constraints by team convention, soft delete, optimistic version column); full ERD and table list in Appendix C |

#### 3.4 checklist (every 3.4.N)

- **A** Analysis class diagram: «boundary», «control», «entity» only, no platform types.
- **B** Analysis sequence diagram for each major flow (name the flows; state whether exceptional flows are separate major flows or `alt` fragments).
- **C** Design class diagram: real classes, interfaces and layers from the code at the freeze SHA.
- **D** Design sequence diagram for each major flow, matching B one to one, with transaction boundaries where they matter.
- **E** Compliance table: each transition strategy applied (3.2.N) → which analysis object it transformed → which design element resulted.

#### 3.5 checklist (every 3.5.N)

1. Problem: where it shows in a diagram, the cause for redesign, **what varies** (SDP 06 p10; SDP 05a), with a **before** class diagram and a **before** sequence diagram. A hypothetical design is labelled "brute-force design (never committed)"; a real one cites its commit (PLAN D8).
2. Candidate patterns, at least two, each with why it fits or not.
3. Motivation for the chosen pattern, including its liability and why the flexibility is needed (SDP 05a p35).
4. Solution class diagram.
5. Solution sequence diagram(s).
6. Implementation decisions, naming the implementing classes and their unit tests (BRF slide 6).

### Chapter 4 DevSecOps and Development Lifecycle (Pan; testing from Liu Jin · #680)

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 4.1 | Source and artefact management: project structure, repository strategy, branching strategy, developer authentication | TPL 4.1 | Monorepo layout; branching named in EPAT terms (EPAT 07 CI p41–44); rulesets and code owners; GitHub and AWS sign-in for humans; OIDC for CI with the disclosed access key; artefacts (SHA-tagged images with digests, SBOM, saved-plan Terraform, hashed frontend assets, committed OpenAPI contract versus generated client) |
| 4.2 | CI pipeline: triggers per job, the jobs, the tests | TPL 4.2 | PR-time and post-merge jobs separately; security scanners, contract drift, policy as code; tests by level and Agile Testing Quadrant with the complex classes under unit test; the k6 performance test (tool, scenario, gates); ZAP before/after evidence (#694); CI technical findings with root cause and fix |
| 4.3 | CD pipeline: number of environments, who approves promotion, how a deployment is verified | TPL 4.3 | Environments and gates, approver per gate, health check and smoke verification, rollback as configured and as actually exercised (state "never run" where true); Continuous Delivery, not Deployment; CD findings |

### Chapter 5 and appendices (Lim; each member checks their row · #681)

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 5 | Individual members' activity contribution summary | TPL 5 | One row per member: roles, owned use case(s) with Analysis, Design and Design Pattern labelled, other sections owned, cross-credit lines. No PR, commit or line counts |
| A | Agile artefacts | BRF slide 6 | Owner Pan. Product backlog, user stories with acceptance criteria, sprint plans, burndowns (with method), reviews, retrospectives, DoD, for Sprints 1–4. Placement in an appendix is a team ruling |
| B | ADR index and decisions without an ADR | Team | Owners Liu Jin, Lim |
| C | Full ERD and table list | Team | Owner Liu Jin |
| D | Test inventory, CI timings and green rates | Team | Owners Liu Jin, Pan; freeze-SHA figures (G7) |
| E | Known limits and evidence | Team | Owners Pan, Yorik |

## Figure requirements

BRF slide 6 asks for crisp, readable diagrams; the thresholds below are team rulings.

| # | Requirement |
| --- | --- |
| F1 | UML or a named notation (crow's foot for ERDs), named in the caption |
| F2 | Caption: "Figure N.M: <viewpoint>. <what to notice>." Every figure is referenced from the text |
| F3 | Vector (Mermaid drawn by the build, or SVG) or PNG at 2–3×; no screenshots of diagrams |
| F4 | Labels at least 8 pt at printed size on A4; split a diagram rather than shrink it |
| F5 | Dark lines on white; colours follow `docs/presentation-kit/diagrams/STYLE.md` so report and deck match |
| F6 | Every class, method, table and event name on a design diagram exists in the code at the freeze SHA |
| F7 | Editable source in the repository: a Mermaid fence in the section, an SVG/PNG under `figures/` with its source beside it, or the deck path it is reused from |
| F8 | Sequence diagrams: lifelines `object : Class`, synchronous and asynchronous messages distinct, `alt`/`opt`/`loop` fragments, transaction boundary marked where it matters |

## Remarks register

Filled from the REM issue (G10). One row per lecturer remark.

| # | Remark (verbatim) | Section | Action | Status |
| --- | --- | --- | --- | --- |
| REM-01 | {{FILL: remarks issue not yet created (presentation G9)}} | | | |

## Checklist and open decisions

By the 30 Oct freeze every box is ticked and every decision settled.

- [ ] Every row F–E above has its section written, with no `{{FILL}}`, `{{VERIFY}}` or `{{CONFIRM}}` marker left (`npm --prefix docs/swe5006/report run check`)
- [ ] Every member's name heads one 3.4.N and one 3.5.N (G4)
- [ ] Every 3.4.N has A–E; every 3.5.N has parts 1–6 including before diagrams
- [ ] Every 3.2.N has the four parts with two rationale lines
- [ ] Every analysis object type is covered by some 3.2.N
- [ ] Every REM row resolved (G10)
- [ ] Freeze SHA recorded and every number re-read at it (G7)
- [ ] Every figure passes F1–F8
- [ ] Each member vetted their own sections and reviewed the rest (#695)
- [ ] Assembled into the template; contents, figure numbers and cross-references updated; page count checked
- [ ] Submitted by 31 Oct 23:59; submitter and time posted on #695

Decisions (numbers follow PLAN part 3; settled ones are folded into the rows above):

- **D6** Test recount at the freeze SHA. Owner Liu Jin.
- **D7** Fix the 11-check and "four checks" statements in the operator guide and pipeline plan. Owner Pan.
- **D8** Before class and sequence diagrams for 3.5.2, 3.5.3, 3.5.4 by 28 Oct. Owners Yorik, Pan, Lim.
- **D9** Team statechart: 3.4.1 (PLAN) or 3.4.5 (deck s6 cue). Owner Lim with Eric and Liu Jin.
- **D11** Who acted as Product Owner. Owner Lim.
- **D13** Lecturers' code access: read access or `git archive`. Owner Lim (#693).
- **D14** Authoring pipeline: Markdown in `sections/` compiled with Typst in the template's layout (first draft built this way on 26 Oct at Liu Jin's request), or a final pass in Word. Owner Lim, by 27 Oct.
- **D15** REM issue created and its remarks mapped to sections. Owner Lim, 27 Oct.
- **D16** Confirm G8 (issue and PR numbers only in tables and appendices; ADR numbers allowed) and F4 (8 pt minimum); both were proposed with this harness on 26 Oct. Owner Lim.
