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
| G8 | Readable without GitHub, in the team's voice | BRF slide 6; team | The marker reads the PDF. Name work by what it does; BlocBuy terms (bloc, team, Sandbox) are defined once, in the 1.1 glossary (G13). Issue and PR numbers are never the only explanation of a piece of work. ADR numbers are allowed because Appendix B lists them. Voice (Source: Team): report text never contains internal process language: rubric codes (G4, G7, "Evidence kinds"), PLAN decision numbers, "team ruling", "this draft", "re-read at the freeze commit", "checked by a code search", plan file names, day-log paths. Commit hashes, workflow run IDs, AWS identifiers and issue or PR numbers appear only in appendix tables. The freeze SHA is stated once, at its home (R50) |
| G9 | Course vocabulary | Module material | Analysis objects as «boundary», «control», «entity» (SAD); EPAT DoD ladder (coded, tested, integrated, releasable, released); Agile Testing Quadrants; Continuous **Delivery** for a manually approved promotion (EPAT 07 CI p34–35); GoF class-level pattern names; a simple static factory is not GoF Factory Method (SDP 01a p32–35) |
| G10 | Incorporate presentation remarks | BRF slide 7; REM | Every remark in the REM issue is turned into a report task tagged with a section number and either answered in the report or recorded as not adopted, with a reason, in the Remarks register below |
| G11 | Code base deliverable | BRF slide 5 | Repository URL on the title page; lecturers get read access or a `git archive` of the freeze SHA (PLAN D13, #693) |
| G12 | Figures legible in print | BRF slide 6 | Every figure passes F1–F9 |
| G13 | One voice, one memory | Team | Each argument, fact, figure and table has one canonical home, listed in the Canonical homes and Canonical figures registries. The full statement appears only at its home. Elsewhere, cite the home in at most one clause, or say nothing. Never repeat the measured figures (for example 18.9 s, 40.6 s, 58.18 s) outside their home. References may point forward. A limitation is stated once, plainly, at its home, without commentary about grading or rulings |

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

Settled in PLAN; re-check at the freeze SHA. The correct statement governs wherever a topic is mentioned; the full statement appears only at its home, the Canonical homes row named in the last column.

| Topic | Correct statement | Source | Home |
| --- | --- | --- | --- |
| Systems | SWE5006 system = `dev`, a modular monolith on one EC2 host behind CloudFront. Production is the shared SWE5001 release (ruleset prod-release, code-owner-approved promotion PR); no separate SWE5006 prod stack was built (#226) | backend ADR 0008; presentation R 4.4 | R49, R04, R19 |
| Expiry | The design bound is one sweep interval plus sweep time, not a one-minute guarantee. Local worst lag 58.18 s over 30 teams; one dev bloc closed 18.9 s after its deadline and was refunded 40.6 s after (23 Oct). Do not claim the target met on dev | PR #671; `docs/perf/sprint3-baseline.md`; PR #664 | R05, R01 |
| Performance | Met locally (30 Sep) and on SWE5001 ECS (16 Oct); never measured on SWE5006 dev (NFR-15) | `docs/quality/nfr-register.md` | R06 |
| Scalability | Not a PROP target; not claimed | presentation decision 8 | R40 |
| Notifications | Coded, off on SWE5006 dev (ADR 0007); SWE5001 scope, no §3.4 models | PLAN D4 | R03 |
| Test counts | One recount at the freeze SHA: static counts from `git ls-tree`, executed counts from the `SonarCloud Backend` CI log of the push to `dev` (`build-quality` runs on a push only for `v*` tags). Never Sonar's `tests` figure | PLAN D6 | R41 |
| Required checks | The live ruleset's 6: `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`; `contract-check` is required by policy only | PLAN D7; `CONTRIBUTING.md` | R42 |
| Counts | 17 ArchUnit rules; Flyway V1–V28 (28 SQL files plus one `.conf`); sprint dates from `docs/agile/definition-of-done.md`; sprint tags by tagger date | PLAN part 3 data conflicts | R43, R44, R45 |
| Effort | No hours were logged. The only actuals are the person-day split agreed on 19 Oct (Liu Jin 12.5, Pan 11.5, Yorik 9, Lim 8.5, Eric 8.5), shown as an agreed estimate beside planned hours. No PR or commit counts per member | PLAN D12 | R46 |
| Static credentials | CI authenticates to AWS through GitHub OIDC; disclose the one accepted IAM user access key in the AWS account governance README | presentation R 4.2 | R47 |
| Lock order | ADR 0001 (deal → intent → payment) and ADR 0002 (team → intent → order → payment) are not yet reconciled; state the reconciled order or the open issue (#663) | PLAN #677 3.2.8 | R11 |
| Programme name | "Practice Module for Certificate in Designing Modern Software Systems" (template line), not the deck's "Graduate Certificate…" | PLAN part 3 | R48 |
| Proposal exclusions | PROP §6.2 excludes notifications "beyond in-app status"; do not write that it excluded in-app notifications | PLAN D4 | R34 |

## Canonical homes

Source: Team (G13). This registry is the report's memory. Before writing, find your topic here. If your section is the home, state it in full. If not, use the reference form, or say nothing. If the topic has no row, add one in the same pull request before writing about it. Each section file declares the rows it is home to in its `owns:` header line (AGENTS.md).

### How to choose a home

A fact's home is the section whose template or rubric row requires it. When a topic has both a claim and its evidence, it gets two homes: one holds the claim or status, the other the evidence. Neither repeats the other, and the row names both. These homes are fixed:

- **BlocBuy glossary** (team and bloc, listing, pay first, Sandbox): 1.1. Every other definition is deleted.
- **Shared modelling conventions** (analysis models use «boundary», «control» and «entity» only; a `critical` fragment marks the transaction boundary; the strategy tag legend; how provider and actor lifelines are drawn): 3.4.0. No 3.4.N restates them.
- **Delivery status per use case**: Table 1.3. **Evidence of dev runs, timings and incidents**: Appendix E.
- **Authorship and code credits**: chapter 5. A 3.4.N or 3.5.N states what its owner identified, analysed and decided, then "(code credits: chapter 5)".
- **Design detail** (HTTP codes, error constants, locks, transactions, batch sizes, rate limits, storage keys): 3.2.N or 3.4.N. A 3.3.N use case describes behaviour in the actors' terms and carries no dev evidence or authorship.
- **SWE5001 topology**: 1.4, as one paragraph. Everywhere else, one clause plus a reference.

### Registry

IDs R01–R39 come from a whole-report read of the 27 Oct draft that found each of these told 2 to 18 times; R40–R49 add the "easy to get wrong" facts that had no row, and R50 is the home of the freeze SHA (G8). Section, table and figure numbers are those of the report after the integration pass of 28 Oct; when renumbering moves them, update this table in the same change. "Allowed extra mentions" are the only places besides the home where the topic may appear, each in the reference form unless the row says otherwise.

| ID | Argument or fact | Home | Home owner | Reference form used elsewhere | Allowed extra mentions |
| --- | --- | --- | --- | --- | --- |
| R01 | On 23 Oct on dev a paid START expired 18.9 s after its deadline and was refunded 40.6 s after it; one run, not a bound | Status: Table 1.3 (UC5 and UC6 rows). Times and evidence: Table E.3 | Lim (Table 1.3); Pan, Yorik (Table E.3) | "shown on dev on 23 October (Table E.3)" | Table 1.3 status cell, without seconds (1.4.1 asks for status on dev); Table 2.8 milestone row, date only (2.3 asks for achieved dates) |
| R02 | On 24 Oct a second buyer's paid JOIN filled a two-seat team on dev, shown in a recording, not a database or log read | Status: Table 1.3 (UC4 row). Evidence: Appendix E | Lim (Table 1.3); Pan, Yorik (Appendix E) | "shown on dev on 24 October in a recording (Appendix E)" | Table 1.3 status cell; Table 2.5 delivered cell (2.2 asks promised against delivered); Table 2.8 milestone row, date only |
| R03 | Notifications (outbox writer, relay, SNS and SQS transports) are coded but switched off on SWE5006 dev (ADR 0007), so they are SWE5001 scope | 1.4 | Lim | "(off on dev, section 1.4)" | Table 1.3 status cell; Table 2.5 row; 3.3.12 and 3.5.3 one sentence each, because their content depends on it; Table C.1 SWE5001-owner column |
| R04 | No separate SWE5006 production stack was built: the reviewed plan of 21 resources was never applied and the story closed on 11 Oct; production is the shared SWE5001 release | Fact: 1.4. Pipeline consequence (environments): 4.3 | Lim (1.4); Pan (4.3) | "(no SWE5006 production stack, section 1.4)" | Table 1.4 dropped-item row (1.4.2 asks for it); Table 2.5 dropped row (2.2 asks for it) |
| R05 | Expiry within one minute is a design bound (one sweep interval plus sweep time), not a guarantee; local worst lag 58.18 s over 30 teams | Claim, status and the 58.18 s figure: 1.4.3 (Table 1.5). Why the bound follows from the sweep: 3.2.7 | Lim (1.4.3); Yorik (3.2.7) | "the expiry design bound (section 1.4.3)" | Table 3.5 strategy row, the concern only, no figures; Table E.1 row cites 1.4.3 |
| R06 | Performance was never measured on SWE5006 dev; p95 met locally (30 Sep) and on SWE5001 ECS (16 Oct, k6, p95 23–53 ms) | Status: 1.4.3 (Table 1.5). Method (tool, scenario, gates): 4.2. Numbers: Table E.4 | Lim (1.4.3); Pan (4.2); Pan, Yorik (Table E.4) | "(performance status, section 1.4.3; results in Table E.4)" | Table 2.6 outstanding-issue row (2.2 asks for outstanding issues), reference form |
| R07 | Dev host gaps: SSH port 22 open on 21 and 24 Oct and closed on 27 Oct, root EBS volume unencrypted, one IAM access key, rollback never run | Open issues with owners: Table 2.6. Rollback as configured and as exercised: 4.3 | Lim (Table 2.6); Pan (4.3) | "(open issues in Table 2.6)" | Table 1.5 security status cell; Table E.1 rows cite Table 2.6; 3.1 and 4.3 keep the one-host design limit without the SSH and EBS dates |
| R08 | Glossary: a team is shown to buyers as a bloc; listing; pay first; Stripe Sandbox is test mode with no real money | 1.1 | Lim | none, or "(team, section 1.1)" at first use in a chapter | none |
| R09 | Crash recovery of refunds under replay is proven locally (Testcontainers and a local rehearsal on 3 Oct) but not shown on dev | Status: Table 1.3 (UC6 row). Local evidence: 3.4.2 D | Lim (Table 1.3); Yorik (3.4.2) | "(not shown on dev; Table 1.3)" | Table 2.5 delivered cell. One wording for the evidence level: D18 |
| R10 | Listener phases: consequences of the same fact run BEFORE_COMMIT and roll back together; derived effects run AFTER_COMMIT and cannot undo the fact | 3.2.9 | Yorik | "(listener phases, section 3.2.9)" | 3.5.2 motivation, one sentence on who chooses the phase (the Observer angle) |
| R11 | Lock order: one combined order per path; ADR 0001 (deal → intent → payment) and ADR 0002 (team → intent → order → payment) are not yet reconciled into one written order | 3.2.8 (Table 3.12) | Eric | "locks in the order of Table 3.12 (section 3.2.8)" | Table 2.6 open-issue row. The product row's position: D17 |
| R12 | Production on ECS was first deployed on 6 Oct as one service and promoted as three services on 17, 19 and 20 Oct | 2.3 (Table 2.8) | Lim | "(promoted to production, section 2.3)" | Table 2.9 step cell, without dates |
| R13 | Optimistic Offline Lock on listings: the version is checked after a short row lock, plus a guarded `WHERE version =` update; a stale save gets 412 | 3.5.5 | Liu Jin | "(Optimistic Offline Lock, section 3.5.5)" | 3.6, the column's meaning only. Which tables compare a version: D19 |
| R14 | Twenty buyers race for one last seat in a Testcontainers test: one wins, nineteen refund obligations persist; not shown on dev | Mechanism: 3.2.8. Test: Table 4.12 | Eric (3.2.8); Pan (Table 4.12) | "(tested locally: Table 4.12)" | Table 1.5 status cell |
| R15 | Guarded update: the team row is written only `WHERE current_size AND status` match, so a race loser changes zero rows; the team `version` is incremented but never compared | 3.2.8 | Eric | "(guarded update, section 3.2.8)" | 3.6, the column's meaning only |
| R16 | Single dev host: a deploy restarts the only API container and the database shares the host | Design consequence (CD finding): 4.3. The 502s of 3–4 Oct: Table E.1 | Pan (4.3); Pan, Yorik (Table E.1) | "(single host, section 4.3)" | 3.1 "one host", without the incident |
| R17 | `DealStatus` declares FAILED and CANCELED but no code writes them; a team ends only SUCCEEDED or EXPIRED | 3.4.5, team statechart (Figure 3.113) | Liu Jin | "(declared but never written, Figure 3.113)" | 1.4, one line under "architected but not implemented" (1.4 asks for that list) |
| R18 | Why not State for the team: one move from OPEN, so the transition table `DealStatus.canTransitionTo` suffices; State is used for listings instead | 3.5.5 motivation | Liu Jin | "(reasoning in section 3.5.5)" | Table 2.5 and Table B.4 rows, reference form only; 3.4.5 C "a transition table, not State (section 3.5.5)" |
| R19 | SWE5001 topology: the same code runs on ECS as identity-svc, commerce-svc and notification-svc, split by transactional consistency | 1.4, one paragraph | Lim | "(SWE5001 topology, section 1.4)" | 1.1 one clause, "two courses, one repository" (1.1 asks which system the report describes) |
| R20 | The dev webhook endpoint used an older Stripe API version, so three paid sessions were stranded; the operator recovered each with one refund | Appendix E | Pan, Yorik | "(the 23 Oct webhook fault, Appendix E)" | 3.3.11 flow 6b keeps the generic exceptional flow, without the incident |
| R21 | A `critical` fragment marks a database transaction, because UML has no transaction fragment | 3.4.0 | Lim | "(transaction drawn as a critical region, section 3.4.0)" | none |
| R22 | Refund retry rules: 23 h idempotency cut-off, then search only; `finish` fenced by version after an expired lease; fixed 30 s delay | 3.2.5 | Yorik | "(retry rules in section 3.2.5)" | 3.3.7, the exceptional flow in buyer terms; 3.2.8, the lease only |
| R23 | Who wrote each member's pattern and use-case code | Chapter 5 (Table 5.1) | Lim | "(code credits: chapter 5)" | none |
| R24 | Analysis models use only «boundary», «control» and «entity»; actors talk only to boundaries; no platform types | 3.4.0 | Lim | none | none |
| R25 | Transactional outbox: the event row is written in the business commit and a scheduled relay publishes it later, at least once | 3.2.9 | Yorik | "through a transactional outbox (section 3.2.9)" | 3.3.12, the flow in actor terms |
| R26 | The proposal's JWT and JPA were replaced by a Sa-Token server session and MyBatis, without an ADR | Change: Table 2.5. Reasons: Table B.4 | Lim (Table 2.5); Liu Jin, Lim (Table B.4) | "(changes from the proposal: Table 2.5)" | 3.2.6 keeps the cookie design, without the history |
| R27 | OWASP ZAP before and after: the 2 Medium and 4 Low header findings of 22 and 26 Oct were fixed on 27 Oct; 6 Informational remain | 4.2 (Table 4.10) | Pan | "(fixed; Table 4.10)" | Table 2.6 row and Table 2.7 remark row, without run IDs; Table E.1 row points to 4.2 |
| R28 | `RefundService.reconcile` exists and is unit-tested, but no endpoint, command or job exposes it | 3.4.2 D | Yorik | "(not exposed; section 3.4.2)" | Table 3.15 row, "not exposed (see D)" |
| R29 | Expiry sweep: lease, up to 100 due teams oldest first, one transaction per team, database clock read under the lock, failure deferred 5 min | 3.2.7 | Yorik | "(sweep design in section 3.2.7)" | 3.3.6, the flow in business terms without lease, batch size or clock |
| R30 | Unique indexes are the last line of defence behind the row locks | 3.6 (index list) | Liu Jin | "(index list in section 3.6)" | 3.2.8, one sentence |
| R31 | ShareCreditSubscriber runs after commit in its own REQUIRES_NEW transaction and swallows failures, so a credit problem never undoes a paid admission | 3.5.2 | Yorik | "an after-commit Observer (section 3.5.2)" | Table 3.18 row, reference form |
| R32 | Done-ladder reading: integrated is merged with checks green; releasable is shown on dev; released is promoted to the shared SWE5001 production | 2.1 | Lim | "(the team's reading, section 2.1)" | none |
| R33 | Dev request path: CloudFront → private S3, or ALB (CloudFront range and origin header only) → EC2 with PostgreSQL and Redis on loopback | 3.1 (Figure 3.3) | Liu Jin | "(request path in section 3.1)" | 4.3 keeps only CD-specific facts (instance role, secrets file, logs) |
| R34 | The proposal's seven out-of-scope capabilities, verbatim; §6.2 excludes notifications "beyond in-app status", not in-app notifications | 1.4.2 | Lim | "the seven exclusions of section 1.4.2" | 3.3.1, the out-of-scope package of the diagram (3.3.1 asks for it) |
| R35 | Actors: buyer, admin, the Scheduler (time), Stripe and Google as secondary actors; no merchant actor | 3.3.1 | Lim | "(actors, section 3.3.1)" | Table 1.1, the stakeholder interest column only |
| R36 | Board snapshot of 26 Oct: 219 items, 159 Done, 38 Backlog, 14 Ready, 7 In progress, 1 more | A.1 | Pan | "(board snapshot, Appendix A.1)" | none. What the one remaining item is: D21 |
| R37 | Code ownership: Pan for CI, Liu Jin for the backend, Lim for the frontend, Yorik and Eric on the payment and group-buy packages | 4.1 (Table 4.4) | Pan | "code owners review each area (Table 4.4)" | none |
| R38 | SonarCloud coverage: backend 100.0 % overall and 99.97 % on new code; frontend 99.3 % and 99.25 % | Table D.3 | Liu Jin, Pan | "(coverage: Table D.3)" | 4.2 states the gate result without the percentages |
| R39 | The strategy tag legend for design class diagrams (Lang 3.2.1, Pres 3.2.2, …) | 3.4.0 | Lim | none | none |
| R40 | Scalability is not a proposal target and is not claimed | 1.4.3 | Lim | none; say nothing | none |
| R41 | Test counts: one recount at the freeze SHA; static counts from `git ls-tree`, executed counts from the `SonarCloud Backend` CI log of the push to `dev`; never Sonar's `tests` figure | Static, by quadrant and level: Table 4.11. Executed: Table D.2 | Pan, testing from Liu Jin (Table 4.11); Liu Jin, Pan (Table D.2) | "(test counts: Table 4.11)" | Table D.1, the per-module breakdown, whose totals equal Table 4.11 |
| R42 | Required checks: the live ruleset's six; `contract-check` is required by policy only | 4.1 (Table 4.2) | Pan | "(the six required checks, Table 4.2)" | Table 4.8 "Required" column |
| R43 | 17 ArchUnit rules | 3.1 (Table 3.2) | Liu Jin | "(Table 3.2)" | none |
| R44 | Flyway V1–V28: 28 SQL files plus one `.conf` | 3.6 | Liu Jin | "(migrations, section 3.6)" | Table 4.6 artefact row (4.1 asks for artefacts) |
| R45 | Sprint dates come from `docs/agile/definition-of-done.md`; sprint tags are dated by tagger date | Sprint dates: Table 2.1. Tags: Table 4.3 | Lim (Table 2.1); Pan (Table 4.3) | "(sprint dates, Table 2.1)" | Table 1.2 phase names; Table A.4 (Appendix A artefact) |
| R46 | Effort: no hours were logged; the only actuals are the person-day split agreed on 19 Oct, shown as an agreed estimate beside planned hours | 2.3 (Table 2.11) | Lim | "(effort, Table 2.11)" | none |
| R47 | CI authenticates to AWS through GitHub OIDC; the one accepted IAM user access key is disclosed | 4.1 (Table 4.5) | Pan | "(CI authentication, section 4.1)" | Table 2.6 open-issue row for the key (R07) |
| R48 | Programme name: "Practice Module for Certificate in Designing Modern Software Systems" | Title page (front matter) | Lim | the name verbatim | none |
| R49 | The report describes the SWE5006 system: `dev`, a modular monolith on one EC2 host behind CloudFront | 1.1 | Lim | "(the SWE5006 system, section 1.1)" | 3.1, its opening clause |
| R50 | Every count, version, coverage figure and status is read at one freeze commit of `dev` (G7), named once | 1.1, the status paragraph | Lim | "at the freeze commit (section 1.1)", or nothing | Appendix tables that list a run or commit read at it |
| R51 | No Product Owner was appointed; the team prioritised the backlog together at each Sprint Planning | 1.3 | Lim | "(no Product Owner, section 1.3)" | none |
| R52 | Backend Maven modules: each module's layer, what it holds and its allowed dependencies | 3.1 (Table 3.1) | Liu Jin | "(modules, Table 3.1)" | Table 3.8 in 3.2.1, the package pattern per module only |
| R53 | The Checkout payment page expires after 31 minutes and never later than the intent or the team's deadline | 3.4.1 D | Eric | "(Checkout page lifetime, section 3.4.1)" | none |
| R54 | The error constants of the use cases' refusals: `PRODUCT_NOT_STARTABLE`, `TEAM_PRICE_UNAVAILABLE`, `TEAM_PRICE_QUOTE_EXPIRED`, `ALREADY_MEMBER`, `PAYMENT_WEBHOOK_SIGNATURE_INVALID` (UC3, UC4, Pay upfront); `OAUTH_PROVIDER_ERROR`, `OAUTH_TOKEN_INVALID`, `UNAUTHENTICATED` (UC2); `ORDER_NOT_FOUND` (UC8); `PRODUCT_NUMBER_CONFLICT` (UC9) | The design text of the matching 3.4.N: 3.4.1 D, 3.4.3 D, 3.4.4 D, 3.4.5 C | Eric (3.4.1); Pan (3.4.3); Lim (3.4.4); Liu Jin (3.4.5) | none; a 3.3.N use case describes the refusal in the actors' terms | none |

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
| F9 | A diagram is drawn once (Source: Team). A second figure over the same participants or classes is allowed only if it shows a different viewpoint or only what changed, and its caption names the figure it extends ("…; the expiry itself is Figure 3.114"). A strategy figure in 3.2.N shows the transformation on a minimal excerpt, not a full use case. A 3.5.N solution figure shows only the pattern's collaboration. An image path is used by one figure only. The Canonical figures registry below says which figure is kept for each flow drawn more than once |

## Canonical figures

Source: Team. One row per flow that the 27 Oct draft drew more than once. The kept figure is the flow's only full drawing; every other figure in the row takes the role given, or is deleted and its place in the text cites the kept figure. Figure numbers are those of the report after the integration pass of 28 Oct; a figure deleted in that pass is shown as "former N, deleted"; when renumbering moves them, update this table in the same change. Roles: **cite only** (delete the figure, keep a sentence that cites the kept one), **delta** (redraw it to show only what it adds, caption names the kept figure), **excerpt** (a minimal pattern or strategy fragment, caption names the kept figure), **keep** (a different viewpoint, unchanged).

| ID | Flow | Kept figure and its section | Other figures and their allowed role | Owners involved |
| --- | --- | --- | --- | --- |
| FG01 | Team expiry, design sequence | Figure 3.112, UC5 D6 deadline passes (3.4.5) | 3.55 (3.4.2): delta, from the refund-obligation write onward. 3.26 (3.2.7): excerpt, the lease and batch mechanics only. 3.33 (3.2.9): excerpt, the listener phases only. 3.119 (3.5.2): keep as the brute-force before design, caption cites 3.115. 3.122 (3.5.2): excerpt, the Observer collaboration only; 3.33 and 3.122 draw the same subscriber calls, so one cites the other | Liu Jin, Yorik |
| FG02 | Team expiry, analysis sequence | Figure 3.103, UC5 B6 deadline passes (3.4.5) | former 3.52, deleted (3.4.2): cite only; the per-order and per-payment loops are stated in the text. 3.24 (3.2.7): keep as the three-box before fragment | Liu Jin, Yorik |
| FG03 | Expiry objects, analysis and design class | Figures 3.97, UC5 analysis class, and 3.106, UC5 design class (3.4.5) | 3.49 and 3.53 (3.4.2): delta, the refund side only (RefundControl, the refund worker), with ResolveTeamControl or GroupDealExpiryService as one reference box | Liu Jin, Yorik |
| FG04 | Refund attempt | Figure 3.56, design sequence (3.4.2) | former 3.21, deleted (3.2.5): cite only, or excerpt of the idempotency key, the retry `alt` and operator review | Yorik |
| FG05 | Webhook settlement | Figure 3.44, settlement shared by UC3 and UC4 (3.4.1) | 3.20 (3.2.5): excerpt, the provider boundary only (verify and re-read outside the transaction). 3.31 (3.2.8): excerpt, the transaction boundary and listener phases only, caption cites 3.44 | Eric, Yorik |
| FG06 | Paid join admission (last seat) | Figure 3.46 (3.4.1) | 3.111 (3.4.5): delta, starting at `TeamSucceeded`, caption cites 3.46 | Eric, Liu Jin |
| FG07 | Publish a listing | Figure 3.107, UC9 D1 publish (3.4.5) | 3.138 (3.5.5): excerpt, the state dispatch only (`changeStatus` → `Product.publish` → `ProductStatus.publish`), caption cites 3.107 | Liu Jin |
| FG08 | Stale-version save | Figure 3.139, version check (3.5.5) | 3.108 (3.4.5): delta, the banner, reload and retry steps, caption cites 3.139 for the version check | Liu Jin |
| FG09 | Pricing browse | Figure 3.71, UC1 design sequence (3.4.3) | 3.132 (3.5.4): excerpt, the Strategy collaboration only (the resolver and its strategy), caption cites 3.71 | Pan, Lim |
| FG10 | Cross-module ports | Figure 3.12, settlement seams (3.2.3) | 3.2 (3.1): keep, the architecture-wide port sample. 3.41 (3.4.1): delta, the purchase-intent side only | Eric, Liu Jin |
| FG11 | Observer participants | Figures 3.118 to 3.121 (3.5.2) | former 3.33, deleted (3.2.9): cite only, it is the analysis view of 3.118's expiry half. 3.32 (3.2.9): keep as the strategy excerpt that adds the `TeamExpired` record, the outbox writer and the cache subscriber, or fold those three into 3.121 and cite it | Yorik |
| FG12 | State classes of a listing | Figure 3.136, State solution class (3.5.5) | 3.105 (3.4.5): delta, ProductStatus as one box without its operations, caption cites 3.136 | Liu Jin |

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
- [ ] Every figure passes F1–F9
- [ ] Each member vetted their own sections and reviewed the rest (#695)
- [ ] Assembled into the template; contents, figure numbers and cross-references updated; page count checked
- [ ] Every Canonical homes row's full statement exists only at its home (G13)
- [ ] Every other mention uses the row's reference form or is removed
- [ ] Every Canonical figures row (FG01–FG12) is applied
- [ ] F9 passes: no flow drawn in full twice, no image path used by two figures
- [ ] G13 and the G8 voice rule checked by one whole-report read of the compiled PDF (AGENTS.md workflow)
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

Conflicting versions of one fact (G13). Two versions cannot both be true, so no section keeps its own variant: the home owner settles each one, the home holds the only wording, and every other section takes the reference form of its Canonical homes row.

- **D17** Lock order, position of the product row (R11). Table 3.12 in 3.2.8 ends the combined order with the product row (team → intent → order → payment → receipt → product); 3.5.5 calls the admin path "the same product-first order that the team-price quotation writers use" and 3.5.4 says the quote locks the product first; 3.4.5 D calls "team, then purchase intent, then payment, then receipt" the documented order. State per path where the product row comes, in Table 3.12 only. Owner Eric.
- **D18** Crash-recovery evidence level (R09). 1.1 and Table 1.3 say "tested locally only"; Table 2.5 says "proven locally (Testcontainers and a local rehearsal on 3 October)"; Appendix E says "tested in CI (`StripeRolloutAcceptanceIT`)". Choose one evidence kind. Owner Yorik (3.4.2 D holds the evidence); Lim updates the Table 1.3 cell to match.
- **D19** Scope of optimistic version checks (R13, R15). 3.6 says `biz_product`, `biz_order`, `biz_purchase_intent` and `biz_payment` all compare a version; Table B.4 says optimistic checks apply on admin writes only; 3.2.4 names only the listing, and R15 says the team `version` is never compared. State which tables compare a version, in 3.5.5, and the column's meaning per table in 3.6. Owner Liu Jin.
- **D20** Performance pointer loop (R06). Table 1.5 points to "2.2 (result)"; 2.2 only points back to 1.4.3; the numbers are in 4.2 and Table E.4. Make 1.4.3 hold the status and point straight to Table E.4, and drop the 2.2 pointer. Owner Lim.
- **D21** Board snapshot remainder (R36). 2.3 says "1 with no status set"; A.1 says "1 issue without a card". State which, in A.1. Owner Pan.
- **D22** Name of the Sprint 3 placement rule. Table 2.9 says the goal was "not met on dev under the 'on dev' rule"; Table A.10 says "under rule 8". Name the rule in Table 2.9 (2.3 asks for the Done step per sprint), without a rubric or plan code; A.10 cites it. Owner Lim.
- **D23** Evidence kind of the last-seat race test (R14). 3.2.8 said "Tested locally (Testcontainers), not on dev"; 3.4.1 said "run locally and in CI, not on dev". Choose one evidence kind at 3.2.8, citing a `build-quality` log if CI is kept; Table 4.12, Table 1.5 and R14's reference form follow. Owner Eric.
