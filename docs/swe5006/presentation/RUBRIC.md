# SWE5006 Presentation Rubric

Team 8 · BlocBuy · Oct 21, 2026 · @Jiechao

## Sources and how to read this

The presentation is on 26–27 Oct 2026 and the report is due 1 Nov 2026 (BR slide 7). No marking rubric for the presentation has been published, so this rubric is built from the documents that state what the talk must cover, then from our own rulings.

| Code | Document | What it governs |
| --- | --- | --- |
| CA | Canvas announcement "Project Presentation Guidelines – 26, 27 Nov 2026" (Chandra). The title's "Nov" is a typo for Oct, confirmed by BR slide 7 | Sequence, time per section, backup videos, interruptions |
| PDF | Practice Project Presentation Guidelines, page 1 | What each section must cover (items 1a–5b) |
| RT | Project Report Template for Practice Module | How each item is expanded in detail. The PDF outline reuses RT's headings |
| BR | Practice module briefing deck (Chandra, 20 Aug 2026) | Project-wide deliverables (slides 5–6), schedule (slide 7), assessment weights (slide 13). These are deliverables of the whole project, not presentation marking criteria |
| PROP | Our submitted proposal (27 Aug 2026) | What we told the lecturers we would build. Not a marking document; we benchmark against it because the lecturers approved it and read our progress reports against it |
| SAD, SDP, EPAT, DSOEA | Module slides and memos, cited as module, deck and PDF page (for example EPAT 01 Agile p26) | Course vocabulary and what lecturers look for |
| Team | Our own ruling | Everything the documents above do not state |

Precedence, highest first:

1. CA for order and timing. The one exception is a team ruling: §2 runs 6 min against CA's 5 so that each member presents their own design problem (G4, Time budget), and §1 gives up the minute.
2. PDF for what to cover.
3. RT for the detail behind each PDF item.
4. BR for project-wide deliverables and dates.
5. Module material for vocabulary and framing.
6. Team rulings, always labelled as such. A ruling that uses PROP as a benchmark says so.

PDF page 2 (presentation tips) is set aside **only where it conflicts with page 1**. Any page 2 item that does not conflict still applies; see open decision 1.

In every table, **Source** quotes what a document requires and **Ruling** records what we decided. A ruling is never cited as a document requirement.

## Ground rules

Forty minutes is a hard stop, interruptions included. The talk shows one example for each item and points to the report for the rest.

| # | Rule | Source | Ruling |
| --- | --- | --- | --- |
| G1 | Time box | CA: 40 min, then 5 min Q&A. PDF: 40 min including Q&A | 40 min hard stop, interruptions included. Treat CA's 5 min Q&A as a bonus |
| G2 | Scripted content | CA ranges sum to 33–39 min. PDF content sums to 30 min | Script 34 min and keep 6 min as buffer |
| G3 | Interruptions | CA: lecturers may ask questions during the presentation | Answer in a sentence or two, then continue. The time comes out of the buffer |
| G4 | Speakers | CA: the team may divide topics | One speaker per section, except §2, where each member presents their own design problem (2.5). Order §2 so that 2.1–2.4 lead into their speaker's own problem, and the full-form problem's owner closes §2 and opens §3: seven handovers in all. Rehearse each handover; each costs 10–30 s |
| G5 | Sequence | CA and PDF use the same order. CA merges Overview with Conduct and adds Testing between design and DevSecOps | Follow CA §1 → §5. Inside §2, follow the PDF and RT order (2.1 → 2.6) |
| G6 | Demo backups | CA: record a video in advance for each demo | Record backups for §4 and §5 by 24 Oct, on the final Sprint 4 build deployed to `dev`. Sprint 4 ends on 23 Oct (Sprint 4 plan §1), so the recordings can show the final sprint build |
| G7 | Slide and diagram legibility | BR slide 6: "crisp and readable presentation slides with good flow" | Every diagram and schema must pass V1–V7 |
| G8 | Talk versus report | RT asks for per-use-case, per-member detail that cannot fit in 40 min | Each item gets one example on a slide, and the slide names its report section. The report holds the full set |
| G9 | Capture lecturer remarks | BR slide 7: the report incorporates remarks gathered during the presentation | All five members take notes during the Q&A and log every question and remark verbatim; after the talk they collate the notes into one GitHub issue. Turn that issue into report tasks by 27 Oct (team decision, 24 Oct) |
| G10 | Code on hand | BR slide 6: well structured source code with unit tests for complex classes. RT 3.5: the implementation follows the design after the pattern is applied | Not a presentation item, but a likely question. Keep the IDE open on the classes implementing each pattern and on their unit tests |

## Time budget

The script runs 34 min with a 6 min buffer. §1 sits at the bottom of its CA range so that §2 can run 1 min over CA's 5 min to give each member a slot for their design problem (G4); every other section sits at or above the bottom of its CA range.

| Section | CA (min) | PDF content + Q&A (min) | Ruling (min) |
| --- | --- | --- | --- |
| §1 Introduction and project management | 3–4 | 2+1 and 2+1 | 3 |
| §2 Analysis, design and patterns | 5 | 7+2 | 6 |
| §3 Testing | 3 | none | 3 |
| §4 CI/CD pipeline demo | 10–12 | 6+2 | 10 |
| §5 System demo | 12–15 | 13+4 | 12 |
| Buffer for interruptions | none | none | 6 |
| **Total** | **33–39** | **40** | **40** |

Inside §1 and §2 the time is split by team ruling. Both are tight, so rehearse them against a timer.

| Item | Ruling (min) |
| --- | --- |
| 1.1 Context and business problem | 0.5 |
| 1.2 Scope and quality attributes | 0.5 |
| 1.3 Status, outstanding issues, promised versus delivered | 0.5 |
| 1.4 Milestones and effort | 0.25 |
| 1.5 Ownership matrix | 0.25 |
| 1.6 Sprints and Definition of Done | 0.5 |
| 1.7 Retrospectives | 0.25 |
| 1.8 Agile artefacts | 0.25 |
| **§1 total** | **3** |
| 2.1 Software architecture | 0.75 |
| 2.2 Transition strategies | 1.25 |
| 2.3 Use case diagram | 0.5 |
| 2.4 Analysis and design models | 1 |
| 2.5 Design problems and patterns: one full-form problem (1 min) and one 20 s slot for each other member | 2.25 |
| 2.6 Database schema | 0.25 |
| **§2 total** | **6** |

## §1 Project Introduction & Project Management Summary (3 min)

Cover the problem, scope and quality attributes, then status against what we proposed, effort, ownership, Definition of Done and the effect of each retrospective.

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 1.1 | Context and the business problem the whole solution addresses | PDF 1a; RT 1.1–1.2 | One slide. State the problem in one sentence |
| 1.2 | Scope: functionality in scope, functionality out of scope, quality attributes | PDF 1b; RT 1.4.1–1.4.3 | Use PROP §6.2 exclusions and §6.3 NFRs as the starting list, with their numeric targets. The quality attributes listed here are the ones §5 demonstrates |
| 1.3 | Current status, including outstanding issues, and the scope that was designed but not implemented | PDF 2a; RT 1.4, 2.2 | One delta table benchmarked against PROP (team ruling): promised, delivered, changed, dropped. Each change's ADR, or the fact that it has none, is cited in the report and in the slide's record reference (`data-ref`, review round 5), not on the slide face; the slide states how many changes still lack an ADR. Known deltas to check: the auth approach (PROP: Google sign-in with JWT; built: Sa-token cookie), Checkstyle, automated burndown, ECS circuit-breaker rollback, e2e suite, UC8 as sacrificial scope. State outstanding issues openly |
| 1.4 | Actual milestones achieved and total effort spent by each member | PDF 2b; RT 2.3 | One table: milestone, date achieved, effort per member. Effort figures must match the fortnightly progress reports already submitted |
| 1.5 | Who owns each use case's analysis and design, and each design problem | RT 2.1, 3.4, 3.5 | Ownership matrix. Every member owns at least one use case and one design problem. Start from PROP §6.1's indicative allocation and show what changed |
| 1.6 | Sprints planned and the Definition of Done achieved | PDF 2c | For each sprint, state which step of the DoD ladder was reached: coded, tested, integrated, releasable or released (EPAT 01 Agile p26). Sprint 4 ends on 23 Oct; state its DoD as recorded at the Sprint 4 review, not as hoped |
| 1.7 | Sprint retrospective and how it shaped the next sprint | PDF 2d | For each sprint, one improvement and the change it made in the following sprint (EPAT SCRUM_Process_Team01.xlsx, Sprint Retrospective sheet: focus on one or two improvements) |
| 1.8 | Agile process artefacts and practices | BR slide 6: product backlog, user stories, sprint plans, burndown charts, sprint reviews and retrospectives are project deliverables | Not a PDF item; the full set goes in a report appendix. On the slide: one story card with acceptance criteria and one burndown. One line on an EPAT practice we used, with evidence (TDD on the state machine, pairing, or refactoring to a pattern, which 2.5's "before" diagram already shows) |

## §2 Analysis & Design and Design Patterns (6 min)

Present in the PDF and RT order, 2.1 to 2.6. That order works because transition strategies are system-wide rules, and the per-use-case models then show they follow them.

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 2.1 | Software architecture: logical layers and packages, components if distributed, and the target platform (stack, languages, frameworks, database). Show the architectural decisions behind the logical architecture | PDF 3a; RT 3.1 | One logical layer and package diagram (the 4+1 logical view, SAD 04a) plus a one-line stack. Cite each decision by ADR number |
| 2.2 | Transition strategies, enough to cover the design end to end. For each strategy: the **types** of analysis objects affected, the **types** of use cases affected, typical class-diagram changes with rationale, typical sequence-diagram changes with rationale | PDF 3b; RT 3.2; SAD 04a | Coverage is per design concern, not per object, but every analysis object type must land in some strategy. Slide: a strategy table (strategy, the quality attribute or concern it serves, object types affected, what they become) plus one before/after pair. Candidates, to check against the ADRs: **language and framework** (Java and Spring dependency injection, SAD 04a activity 1); **presentation** (GUI boundary becomes a React route, component and TanStack Query hook); **distribution** (control becomes a REST controller; the generated TypeScript client plays the stub); **persistence** (entity becomes a domain aggregate behind a repository port in the domain layer, one per aggregate root, implemented in the infrastructure layer by an adapter over MyBatis mappers); **external interface** (the Stripe boundary becomes an adapter plus a webhook endpoint); **security** (authentication and role checks applied across controllers); **scheduling** (the Scheduler actor becomes a scheduled job driving expiry); **concurrency** (the join race). Concurrency belongs here because PROP claims no GoF pattern for UC4 |
| 2.3 | Use case diagram covering use cases in scope **and** out of scope, with actors and include, extend and generalisation where they apply | PDF 3c; RT 3.3 | Mark out-of-scope use cases visibly (PROP §6.2). Show Stripe as a secondary actor and the Scheduler as an actor, consistently with 2.4. Showing delivery status per use case is a team ruling |
| 2.4 | Analysis and design models for each in-scope use case: analysis class diagram, analysis sequence diagram per major flow, design class diagram, design sequence diagram per major flow. Designs follow the transition strategies; every member owns at least one use case | PDF 3d; RT 3.4 | Slide: one use case (UC4, join) taken from analysis to design. The analysis version uses boundary, control and entity stereotypes and no platform; the design version applies 2.2. **The team-formation statechart is required** (team ruling): SAD 03c p4 and p11 call for one when an object's state affects more than one use case, and Team state gates UC4, UC5, UC6 and UC8. PROP §5.1 also calls it the heart of the domain. RT does not list it, so it goes on this slide or on the State pattern slide in 2.5 |
| 2.5 | Design problems and patterns. For each problem: the problem shown with class and sequence diagrams (as brute force if needed), candidate patterns, why one was chosen, class and sequence diagrams after applying it, implementation decisions. Every member owns at least one | PDF 3e; RT 3.5; SDP 06 design case study p10 | Slides: one problem in the full before/after form, and each other problem in a 20 s slot presented by its owner (G4). Each problem states, in order: where it shows in a diagram, the cause for redesign, and **what varies** (SDP 06 design case study p10; SDP 05a p4 and p12–14), then the pattern. Name the implementing classes (G10). State the liability and why the flexibility is needed (SDP 05a p35). Be ready to defend Strategy for two fixed prices, and if the UC3 factory is a static `create(type)`, call it a simple factory, not GoF Factory Method (SDP 01a p32–35). GoF class-level patterns only (SDP 00a p5); a DAO or repository belongs in 2.2 |
| 2.6 | Database schemas | PDF 3f; RT 3.6 | Core entities on the slide (V6) plus the key mapping decisions (keys, any inheritance strategy, SAD 04a). Full ERD in the appendix |

## §3 Types of Testing Conducted (3 min)

Show testing as one map in the course's vocabulary, then introduce the performance test that §5 runs.

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 3.1 | Types of testing conducted | CA | One slide on the EPAT Agile Testing Quadrants (EPAT 03 TDD p7: Q1 unit and component; Q2 functional and story; Q3 exploratory and UAT; Q4 performance and security), also labelled by test level (EPAT 03 TDD p5: unit, integration, system). For each type: the tool, where it runs in the pipeline, and one thing it caught. Name the complex classes under unit test, as PROP §6.3 promised: the state machine, capacity enforcement and refund idempotency |
| 3.2 | Scripted performance test: the tool, the scenario and the target | PDF 5b (placement in §3 is a team ruling) | Use PROP §6.3's targets: interactive operations respond within 2 s with up to 100 concurrent users, and team expiry resolves within 1 min of the window lapsing. Introduce it here and run it in §5 |
| 3.3 | Security testing | Team | Name the scanner types only (static analysis, dependency, image and IaC, secrets) and point to §4 for where they gate the pipeline |

## §4 CI/CD Pipeline Demo (10 min, live with recorded backup)

Demo the pipeline live, structured around PDF 4a–4c and RT 4.1–4.3. Those sections ask exactly what our strongest material answers: how source and artifacts are managed, how developers authenticate, and who approves promotion between environments.

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 4.1 | Live demonstration of the pipeline | CA | If a full run takes more than about 8 min, trigger it beforehand and walk through the finished run (team ruling) |
| 4.2 | Strategy to manage the source code **and artifacts**: project structure, repository strategy, branching strategy, how developers authenticate | PDF 4a; RT 4.1 | Source: show the monorepo layout, name the branching pattern in EPAT terms (EPAT 07 CI p41–44), explain how developers sign in to GitHub and AWS, and show that CI authenticates to AWS through GitHub OIDC with no stored keys. Do not claim the project has no static credentials: disclose the one accepted IAM user access key recorded in the AWS account governance README. Artifacts: git-SHA-tagged container images with digest pinning, the SBOM, saved-plan Terraform promotion, content-hashed frontend assets, and the committed OpenAPI contract versus the gitignored generated client |
| 4.3 | Continuous integration: what triggers each job, the jobs, the tests | PDF 4b; RT 4.2 | Show PR-time jobs and post-merge jobs separately, including the security scanners, the contract drift check and the policy-as-code checks |
| 4.4 | Continuous delivery: how many environments, who approves promotion between them, how a deployment is verified | PDF 4b; RT 4.3 | Show the environment gate and the approver. Show the verification: the health check or smoke test, and the circuit-breaker rollback if configured. Use the correct term: a manual production approval is Continuous **Delivery**, not Continuous Deployment (EPAT 07 CI p34–35) |
| 4.5 | Technical findings and issues encountered | PDF 4c | Two or three real issues, each with its root cause and fix |
| 4.6 | Recorded backup video | CA | Store it in the cloud and on a USB drive |

## §5 System Demo (12 min, live with recorded backup)

Demonstrate the significant use cases through the quality attributes we listed in 1.2. PDF 5b's scalability, performance and maintainability are examples, not a mandatory set.

| # | Requirement | Source | Ruling |
| --- | --- | --- | --- |
| 5.1 | Some of the significant use cases | PDF 5a | Choose use cases that also show a quality attribute, such as UC4 join. Walk the complete group-buy loop if time allows: initiate, join, succeed or expire, confirm or refund |
| 5.2 | How the solution handles some of its important quality attributes | PDF 5b; RT 1.4.3 | "Important" means our own listed attributes. Demonstrate two or three, for example correctness under concurrency (the last-slot race), refund idempotency, team expiry within 1 min (run with a short window), and security. Claim scalability only if auto-scaling is configured and can be triggered on stage; otherwise state it as a deliberate trade-off (PROP does not list scalability as an NFR) |
| 5.3 | Performance, shown by running the scripted performance test | PDF 5b | Run the test introduced in 3.2 and show the result against PROP's 2 s at 100 concurrent users. Pre-run it and show the report if a live run would exceed about 2 min |
| 5.4 | Maintainability, by showing or running the DevOps pipeline | PDF 5b | Point back to the §4 run rather than repeating it (team ruling). Optionally push a small change through the pipeline |
| 5.5 | Recorded backup video | CA | Store it in the cloud and on a USB drive |

## Visibility requirements

Every diagram must be readable from the back row and in the recordings. BR slide 6 asks for crisp and readable slides with good flow; the specific thresholds below are team rulings.

| # | Requirement |
| --- | --- |
| V1 | One idea per slide. Show the full diagram briefly for orientation, then crop to the part being discussed |
| V2 | Export as SVG, or as PNG at 2–3× scale. No low-resolution screenshots |
| V3 | Labels at least 18 pt as they appear on the slide. Split a diagram rather than shrink it |
| V4 | High contrast: dark lines on white, no pale fills |
| V5 | Highlight the part being discussed with an outline or by greying out the rest, not with a laser pointer |
| V6 | The schema slide shows only core entities, with PK, FK, cardinality and the columns that matter to a decision. The full ERD goes in the appendix, with a zoomable copy open during the talk |
| V7 | For demos and recordings: browser at 125–150% zoom, terminal and IDE at 18 pt or larger, recorded at 1080p. Check on a projector or TV from across the room before 26 Oct |

## Checklist and open decisions

By 25 Oct, every box below should be ticked and every open decision settled. Backup videos are due a day earlier, on 24 Oct (G6).

- [ ] Every requirement row from 1.1 to 5.5 has a slide, a demo step or an appendix slide
- [ ] Every §2 example slide names the report section that holds the full set
- [ ] Promised-versus-delivered table (1.3) complete, each change's ADR (or its absence) cited in the report and the slide's record reference
- [ ] Transition strategies checked against the current ADRs, with every analysis object type covered and any gaps written up
- [ ] Team-formation statechart drawn and consistent with the State pattern code
- [ ] Ownership matrix complete: each member has a use case and a design problem
- [ ] Each pattern's implementing classes named, with the IDE ready on them (G10)
- [ ] Effort figures in 1.4 reconciled with the submitted progress reports
- [ ] One story card, one burndown and the agile artefacts appendix ready (1.8)
- [ ] Performance script runs and its result is recorded against 2 s at 100 concurrent users
- [ ] Full rehearsal finishes in 34 min or less, with §1 inside 3 min and §2 inside 6 min
- [ ] Both backup videos recorded by 24 Oct and stored in the cloud and on a USB drive
- [ ] Every diagram and schema checked against V1–V7 on a projector or TV
- [ ] A speaker assigned to each section, with the handovers rehearsed
- [x] Note-takers for each presentation day (G9): all five members, notes collated into one GitHub issue after the talk (decided 24 Oct)
- [x] Questions to Chandra (is Q&A inside or outside the 40 min, and is there a marking rubric for the presentation?): decided 24 Oct not to send them. The deck works to the stricter reading: Q&A inside the 40 min (G1) and no published marking rubric (Sources). The report due date is settled: 1 Nov (BR slide 7)

Open decisions:

1. What PDF page 2 says, and which of its items do not conflict with page 1 and therefore still apply.
2. What is deployed now against UC1–UC9: done, partial or dropped.
3. Which transition strategies the ADRs already cover, and which of the 2.2 candidates are missing.
4. Who owns which use case and which design problem.
5. Which GoF patterns are in the code, in which classes, and whether brute-force "before" diagrams exist. Specifically: is State one class per state, and is the UC3 factory static?
6. Whether the auth change from PROP was recorded in an ADR.
7. Which performance test tool is used, whether a script exists, and whether it has met the target.
8. Whether auto-scaling is configured, which decides whether scalability is demonstrated or stated as a trade-off.
9. Whether the presentation is also graded individually. **Settled 24 Oct (team decision):** the presentation is graded as a team, so speaking is not compulsory for every member. The individual portion is in the report: 80% including 5% peer assessment (BR slide 13), plus each member's own §3.4.N and §3.5.N. The §2 split stays (G4): each member shows their own report work.
