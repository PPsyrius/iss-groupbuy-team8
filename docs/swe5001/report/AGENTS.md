# SWE5001 final report: writing harness

Rules for anyone, human or agent, who writes, reviews or checks Team 8's SWE5001 project report. What the report must contain, the lecturer feedback (F1–F13), the diagram list (D1–D20), the owners and the milestones are in [`README.md`](README.md). All committed text is English ([`CONTRIBUTING.md`](../../../CONTRIBUTING.md)).

This harness applies to the SWE5001 final report only. The progress-report rules of the agile evidence skill (`.claude/skills/agile-evidence-capture/references/progress-report-rules.md`) do not apply to it; where they differ, this file wins for this report.

## Where things go

| What | Where |
|---|---|
| Report text | One shared Google Doc or Word file created from the template, kept by the editor on the team's shared drive. Not in the repository |
| Framework, harness | `docs/swe5001/report/README.md`, `AGENTS.md` |
| Diagram sources and exports | `docs/swe5001/report/diagrams/`, named by ID: `D03-logical-view.drawio` with `D03-logical-view.png` |
| Section reviews | A comment on the section's remark issue (#585–#592, #595), or on #462 for sections without one, in the [review format](#review-procedure) |
| Evidence the report cites | Existing records under [`docs/swe5001/evidence/`](../evidence/); new records go there too, one folder per session |

## Writing rules

1. **Answer first.** Each section opens with one or two sentences that answer the template's question for that section; detail follows.
2. **Follow the template.** Keep its chapter and section numbers and titles, including its own subsections. Add subsections under a template section; never rename or reorder the template's own.
3. **Decisions as trade-offs.** Every AD row states context, the options considered, the choice, the trade-off and the consequence. A decision without a rejected option is a description, not a decision.
4. **Explain how, not only what.** The lecturers asked how context mapping was done, how the aggregates were arrived at and how EventStorming helped. For method questions, show the steps and the intermediate result, then the outcome.
5. **Write for a reader without repository access.** The repository is private. In the report body: no links into the repository, no issue or PR numbers, no workflow run IDs. Class, method and table names appear only in 3.2.3, 3.2.4 and chapter 5 (workflow and tool names); elsewhere, say what the thing does. Every measurement the text relies on gets a row in Appendix E (date, what ran, where, result, repository path).
6. **Real names at class level.** Every class, method, table and event name must exist in the code at the commit the report cites. Check each one (`rg -n "class OrderFulfilmentAdapter" backend`).
7. **Reference, don't repeat.** A fact lives in one section; other sections refer to it by section number or ID.
8. **Plain language.** Short sentences, active voice, the team's ubiquitous language ([`06-eventstorming-ddd.md`](../architecture-options-study/06-eventstorming-ddd.md) §7). Define an acronym on first use.
9. **Proposal documents are not the as-built system.** [`02-proposed-architecture.md`](../architecture-options-study/02-proposed-architecture.md) describes the proposal (schema per service, WAF, X-Ray, stock reservation); check every statement taken from it against the code or the Terraform before using it.

## Evidence rules

Every factual claim is one of six kinds. Say which, every time.

| Kind | Wording in the report | What the writer must hold |
|---|---|---|
| Verified on AWS | "On 19 October on AWS, …" | The evidence record and its Appendix E row |
| Verified locally | "Tested locally, not on AWS: …" | The local record or the named test, and its Appendix E row |
| In the code | "The settlement service locks the team first." | The file, at the commit the report cites |
| Configured, not tested | "Configured to scale out above 12,000 requests per task per minute." | The Terraform file, workflow or GitHub variable that sets it |
| Calculated or estimated | "Calculated: …", "Estimated: …" | The formula or the source of the estimate |
| Designed, not built | "Designed but not implemented: …" | The AISS item or roadmap entry |

- Never upgrade a claim: local is never written as AWS, an estimate never as a measurement, a configured value never as tested behaviour, a proposal never as built.
- Measurements carry date, run and unit; code facts carry the commit; configured values cite where they are set. Use one value everywhere; if two sources disagree, resolve it before writing.
- Give limits next to the claims they limit (for example: the 19 October run is not valid V3 acceptance evidence because the generator dropped iterations).

### Placeholders

- Write `{{VERIFY: claim — where to check}}` for a fact you cannot confirm yet, `{{CONFIRM: question — who decides}}` for a decision that is not yours, and `{{FILL: what}}` for a value that does not exist yet.
- Never settle a `{{CONFIRM}}` yourself; agents never decide items in the README's decisions or owners tables.
- Before submission, a search of the shared document for `{{` returns nothing.

### Claims that are easy to get wrong

Owner: the editor. Rechecked from `origin/dev` at `da0ab556` on 23 October 2026. Rerun every check before 13 November, after #589 merges (rows marked †) and whenever a row's file changes; then update this date.

| Topic | Correct statement | Check |
|---|---|---|
| Image scanning | Each service image is built, then scanned by Trivy; fixable HIGH or CRITICAL findings fail the job before export. `swe5001-env.yml` deploys existing release or candidate digests without rescanning: they were scanned when built | `rg -n "trivy-action\|severity\|exit-code" .github/workflows/swe5001-service-images.yml`; `rg -n "digests" .github/workflows/swe5001-env.yml` |
| Static analysis | SonarCloud and SpotBugs; CodeQL is off (`ENABLE_CODEQL=false`) | `gh variable list --repo PPsyrius/iss-groupbuy-team8`; `rg -n spotbugs backend/pom.xml` |
| SBOM and DAST | CycloneDX SBOM (`sbom.yml`); passive ZAP baseline (`dast-baseline.yml`) against the `dev` environment (`FRONTEND_URL_DEV`, the SWE5006 single-server environment), advisory, not against the SWE5001 ECS runtime | `rg -n cyclonedx .github/workflows/sbom.yml`; `rg -n FRONTEND_URL_DEV .github/workflows/dast-baseline.yml` |
| Absent security services | No WAF, GuardDuty or CloudTrail in `infra/swe5001` | `rg -il "aws_wafv2\|guardduty\|cloudtrail" infra/swe5001` returns nothing |
| CloudFront → ALB | Plain HTTP (`http-only`), restricted by CloudFront's origin-facing prefix list and the `X-Origin-Secret` header; not encrypted | `rg -n "origin_protocol_policy\|X-Origin-Secret" infra/swe5001/edge/cloudfront.tf` |
| Secrets in state | Database passwords are write-only (`password_wo`, `value_wo`); the Valkey auth token and the origin secret are in Terraform state | `rg -n "_wo \|auth_token\|nonsensitive" infra/swe5001` |
| Scaling target | 12,000 requests per target per minute (200 req/s), set as `SWE5001_COMMERCE_REQUESTS_PER_TARGET` in the `swe5001-demo` environment; the checked-in fallback is 200 requests per target per minute (provisional) | `gh variable list --env swe5001-demo --repo PPsyrius/iss-groupbuy-team8`; `rg -n -A3 'variable "commerce_requests_per_target"' infra/swe5001/runtime/variables.tf` |
| Capacity | One 0.5 vCPU commerce task saturates at about 280 req/s; three tasks served about 455 req/s, the generator's limit; linear scaling not proven | [`20261019-v3-autoscaling/README.md`](../evidence/20261019-v3-autoscaling/README.md) |
| Event relay | About 50 events/s, calculated (100 rows per 2-second tick); one copy only, because it runs under the scheduler lease | [ADR 0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md); `rg -n LEASE backend/blocbuy-bootstrap/src/main/java/com/blocbuy/bootstrap/scheduling/OutboxRelayJob.java` |
| Lock order | UC4 webhook settlement: the event is claimed (duplicate insert) before any lock, then team → purchase intent → payment, then fulfilment and order writes (`OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`; [ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md)). Expiry's `BEFORE_COMMIT` subscribers run order(10) → payment(20). [ADR 0002](../../../backend/docs/adr/0002-team-lifecycle-events.md) "Lock order and concurrency" still states team → intent → order → payment; this row supersedes it until ADR 0002 is amended | `rg -n "claimEvent\|lockForSettlement" backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/StripeCheckoutService.java` |
| SNS wait | The 15 minutes in a rebuild is a deliberate wait for subscription filter policies to apply, not for the topic | `rg -n "sleep 900" infra/swe5001/scripts/messaging-check.sh` |
| Data tier † | One `db.t4g.micro`, single zone, no backups; one Valkey node; each service has its own database and roles | `rg -n "instance_class\|multi_az\|backup_retention_period" infra/swe5001/modules/rds-postgres/` |
| Subnets † | Until #589 merges, RDS and Valkey use the same private subnets as the ECS tasks | `rg -n subnet_ids infra/swe5001/runtime/` |
| Table ownership | Every service database runs the full Flyway migration stream; the runtime role gets data access only to its service's tables. Do not write "each database contains only its service's tables" | [`afterMigrate__service_grants.sql`](../../../backend/blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql) |
| Referential integrity | No foreign keys or CHECK constraints, by convention; the application keeps relationships. The header comment of `V1__init.sql` ("invariants … CHECK, FK … pushed down to the database") is stale: never quote it | `rg -n "REFERENCES\|CHECK \(" backend/blocbuy-bootstrap/src/main/resources/db/migration` returns nothing |
| Table prefixes | `sys_`, `biz_`, `rel_` and `log_` | `rg -o "CREATE TABLE \w+" backend/blocbuy-bootstrap/src/main/resources/db/migration` |
| Fulfilment uniqueness | A payment can fulfil at most one purchase intent (partial unique index on `fulfilled_payment_id`) | `rg -n -A2 uq_biz_purchase_intent_fulfilled_payment backend/blocbuy-bootstrap/src/main/resources/db/migration` |
| Bounded contexts | commerce-svc holds three bounded contexts (Catalog and Pricing, Team Purchase, Payment and Refund) in four modules; ADR 0008's "four contexts" counts modules | `06-eventstorming-ddd.md` §4.3–§4.4; [ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) |
| EventStorming | Simulated and retrospective; no live workshop | `06-eventstorming-ddd.md` §1 |
| Drill status | V3 auto-scaling: on AWS, 19 October, not valid acceptance evidence. Identity outage and V7 poison message: local only, 15 October. V4 burst: local integration test only (`V4ExpiryPipelineIT`), no evidence record. V5 self-healing: not run anywhere | `ls docs/swe5001/evidence/`; [`20261015-local-failure-rehearsal/README.md`](../evidence/20261015-local-failure-rehearsal/README.md) |

## Diagram rules

- **Tools.** Component, deployment, network, trust-boundary and activity diagrams: draw.io (diagrams.net, runs in the browser, nothing to install); commit the `.drawio` file. Class, sequence, state and ER diagrams may use Mermaid (`.mmd`, as in `architecture-options-study/diagrams/`) or draw.io; Mermaid has no lollipop or socket notation, so never use it for component diagrams. A hand-written SVG that follows STYLE.md is its own source.
- **Exports.** Commit a PNG at twice the printed size (or an SVG) next to each source, with the same base name. Never commit an image without its source.
- **Legibility.** Labels at least 9 pt at the printed width of an A4 page; split a diagram that cannot meet this.
- **Style.** Use the colours of [`docs/presentation-kit/diagrams/STYLE.md`](../../presentation-kit/diagrams/STYLE.md) so report and deck match. In the report, the logical-view rules below override STYLE.md's stereotypes.
- **Deck diagrams are read-only.** Never edit `docs/presentation-kit/diagrams/`; copy a diagram into `docs/swe5001/report/diagrams/` under its D-ID first.
- **Notation and caption.** Use UML or a named notation (C4, DDD context map, crow's foot) and name it in the caption. Each caption names the viewpoint and what the reader should notice: "Figure 3.5: Physical deployment view. The database sits in its own subnets."
- **One diagram, one purpose.** Split a diagram that needs more than one sentence to explain what it shows.

### Logical view rules

The lecturers found the presentation's logical diagram non-standard (F13). It mixed logical elements with AWS products, used `«node»` for a routing rule and put timeouts and comments on the arrows. The report's logical view (D3) follows these rules:

1. **Technology-neutral.** Name roles, not products: "CDN and edge gateway", not CloudFront and ALB; "message broker" and "event queue", not SNS and SQS; "session store", not Valkey. Products belong in the physical view, with the mapping table in 3.2.2.
2. **One notation per diagram.** D3 is a UML logical detail deployment diagram (the template's term): logical nodes such as client, web tier, application tier, integration tier and data tier, with components on them and their interfaces. A UML component diagram (provided interfaces as lollipops, required interfaces as sockets) may supplement it; never mix the two notations in one diagram.
3. **Layers visible.** Arrange elements in tiers so the reader sees 3.1.2 in the picture.
4. **Interfaces, not URLs.** Label each connection with the interface and its style (synchronous REST, asynchronous published event), not with paths, timeouts or notes.
5. **Correct stereotypes.** `«component»`, `«subsystem»`, `«database»`, `«external system»`; `«node»` and `«execution environment»` only for nodes.
6. **Producers and consumers.** Show which interfaces the platform offers to producers (sellers, admins), to consumers (buyers) and to future services (published events).

### UML checks for class, object, sequence and activity diagrams

- Class diagrams: visibility, key attributes and typed operations, `«interface»`, realisation and dependency arrows drawn correctly, multiplicities on associations.
- Object diagrams: `name : Class` instances with attribute values, links matching the class diagram's associations.
- Sequence diagrams: lifelines named `object : Class`, synchronous and asynchronous messages drawn differently, return messages where values matter, `alt`, `opt` and `loop` fragments, the transaction boundary marked.
- Activity diagrams: one partition per actor, a single start node, decisions with guards, the end for each outcome.
- Every element on a detailed design diagram traces to a class in the code; every element on a DDD diagram traces to a row in the aggregate or context table.

## IDs and traceability

| Format | Meaning | Register |
|---|---|---|
| `F1` … `F13` | Lecturer remark | README feedback table, linked to its issue |
| `D1` … `D20` | Diagram | README diagram list; file names use two digits (`D03-…`) |
| `AD-01` … | Architectural decision | Report 3.1.1, 3.2.1, 3.3; deck IDs kept, new from AD-15 ([mapping](README.md#ad-mapping-proposed-eric-confirms-in-311)) |
| `AISS-01` … | Architectural limitation | Report 3.4 only; deck IDs kept, new from AISS-09 ([mapping](README.md#aiss-mapping-proposed-the-34-owners-confirm)) |
| `NFR-01` … | Quality requirement | [`docs/quality/nfr-register.md`](../../quality/nfr-register.md) |

Link ADs to their ADR in [`backend/docs/adr/`](../../../backend/docs/adr/README.md) where one exists (in the repository; in the report, by title).

## Workflow per section

1. Read the section's row in the README outline and the template's own guidance text for it.
2. Collect the sources listed there; mark anything you cannot trace with a placeholder.
3. Draft in the shared document, answer first.
4. Run the per-section definition of done below.
5. Ask another section owner for a review; if nobody takes it within two days, the editor assigns a reviewer on #462.
6. Fix the findings; the reviewer marks the review resolved; the owner ticks the matching acceptance criteria on the remark issue.

Repository changes (diagram sources, this framework, evidence records) go through a pull request into `dev` that follows [`CONTRIBUTING.md`](../../../CONTRIBUTING.md):

- Body: `Refs #462` and `Refs #<remark issue>`, plus a line per remark the PR contributes to, for example `F7: D6 with the data subnets`.
- Never `Closes #<remark issue>`: those issues close by hand when every acceptance criterion is ticked, and #589 also needs the applied infrastructure change.
- Add the sprint-log entry under the sprint open on that day. Sprint 4 ended on 23 October; if no later sprint is in [`docs/agile/README.md`](../../agile/README.md) yet, ask the process owner (Lim) to open it before logging, and do not invent one.

### Review procedure

The reviewer posts one comment with this table and a verdict (Ready, Ready after fixes, Not ready):

| Item (DoD line or F-ID) | Met / Partial / Missing / Conflict | Location in the document | Fix |
|---|---|---|---|

An agent review is independent and read-only: it reports findings in this format and changes nothing. The editor runs the whole-report definition of done before submission and records the result on #462.

## Definition of done (per section)

- [ ] Template number and title unchanged; every template prompt for the section is answered.
- [ ] Opening sentences answer the section's question.
- [ ] Every claim has its kind and the evidence the kind requires; measurements have Appendix E rows.
- [ ] Numbers match the claims table and the other sections.
- [ ] Every class, method, table and event name exists in the code at the cited commit.
- [ ] Every diagram has a notation, a caption with its viewpoint, a committed source and legible labels.
- [ ] Decisions list the rejected options and the trade-off.
- [ ] Limitations found while writing are in 3.4 with an owner and a date.
- [ ] The F-items this section owns are closed by their artefacts.
- [ ] No placeholders left; within the page target.
- [ ] Reviewed in the review format, with every finding resolved.

## Definition of done (whole report)

- [ ] F1–F13 each closed by the artefact named in the README; #596's twelve remarks cross-checked one by one.
- [ ] Focus areas A, B and C each have every artefact the README lists.
- [ ] Q1–Q8, every success criterion and every course module answered where the briefing coverage table says.
- [ ] One AD register and one AISS register, consistent with the mapping tables; no proposal-era ID cited.
- [ ] D1–D20 present (or cut by the README's cut order, with the cut recorded on #462) and referenced from the text.
- [ ] Claims table rechecked and redated; the report agrees with it.
- [ ] Effort and milestones in 2.3 match the agreed split.
- [ ] Appendices D and E complete; working-system evidence captured before the final destroy.
- [ ] Table of contents, figure numbers and cross-reference fields updated in the shared document; a search for `{{` finds nothing.
- [ ] Submitted in Canvas by 16 November 2026; every member reminded to rank in the Peer Grading System (unique ranks; no rank means zero peer marks).
