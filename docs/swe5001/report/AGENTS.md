# SWE5001 final report: writing harness

Rules for anyone, human or agent, writing or reviewing Team 8's SWE5001 project report. The structure, the assessor feedback (F1–F12), the diagram list (D1–D14) and the section owners are in [`README.md`](README.md). All committed text is English ([`CONTRIBUTING.md`](../../../CONTRIBUTING.md)).

## Where things go

```
docs/swe5001/report/
  README.md             framework: outline, feedback map, diagram list, briefing coverage
  AGENTS.md             this file
  registers.md          the one AD and AISS numbering
  main.typ              cover, contents and the chapter includes
  lib.typ               shared styles and scaffold helpers (prompt, owner, todo, diagram-slot)
  sections/NN-name.typ  one Typst file per template chapter (01-introduction.typ … 06-highlights.typ)
  diagrams/             diagram sources (PlantUML, Mermaid or draw.io) and their exported SVG/PNG
  parts/<member>/       one member's sections, diagrams, README.md and standalone part.typ, until the reconcile PR
  build/                compiled PDF (git-ignored)
```

Write chapters in Typst under `sections/`, one file per template chapter. Typst numbers the headings, so keep the template's heading order and titles. Review happens on diffs of the `.typ` files. Build from the repository root:

```bash
mkdir -p docs/swe5001/report/build
typst compile --root . docs/swe5001/report/main.typ docs/swe5001/report/build/report.pdf
```

**Individual parts.** Until every section is drafted, each member writes in `parts/<member>/` and does not edit the shared chapter files under `sections/`. A part keeps its section files at the template heading level (`==` for a section such as 3.2), imports the shared `lib.typ`, `diagrams/style.typ` and the numbers in `registers.md`, and has a `README.md` listing its sections, status, cross-part facts and the exact include lines for the reconcile. A `part.typ` builds the part alone with the template's section numbers. One reconcile PR then wires every part into `sections/`, checks the cross-part facts, and runs the whole-report review. See [`parts/liu-jin/`](parts/liu-jin/README.md) for an example.

The scaffold helpers mark unfinished work: `#prompt[…]` is the template's own guidance text, `#todo(…)` lists what the section must still contain, `#owner(…)` names the owner, `#diagram-slot(…)` reserves a figure, and `#pending[…]` shows written text that still needs team review or a cross-part check (red on a light-red highlight). Delete them as the section is written and reviewed; a finished section contains none. Use `\#123` for issue numbers in text, because a bare `#` starts Typst code.

## Writing rules

1. **Answer first.** Each section opens with one or two sentences that answer the template's question for that section; detail follows.
2. **Follow the template.** Keep its chapter and section numbers and titles. Add subsections under a template section; never rename or reorder the template's own.
3. **Decisions as trade-offs.** Every AD row states context, the options considered, the choice, the trade-off and the consequence. A decision without a rejected option is a description, not a decision.
4. **Explain how, not only what.** The assessors asked "how did we do context mapping" and "how are the aggregates arrived at". For method questions, show the steps and the intermediate result, then the outcome.
5. **Real names at class level.** Class, method, table and event names in diagrams and text must exist in the code. Check each one before committing (`rg -n "class OrderFulfilmentAdapter" backend`).
6. **Reference, don't repeat.** A fact lives in one section; other sections link to it by section number or ID.
7. **Plain language.** Short sentences, active voice, the team's ubiquitous language ([`06-eventstorming-ddd.md` §7](../architecture-options-study/06-eventstorming-ddd.md)). Define an acronym on first use.

## Evidence rules

Every factual claim is one of four kinds. Say which, every time.

| Kind | Wording | Requirement |
|---|---|---|
| Verified on AWS | "On 19 October on AWS, …" | Link to the record under [`docs/swe5001/evidence/`](../evidence/) |
| Verified locally | "Tested locally, not on AWS: …" | Link to the local record |
| Calculated or estimated | "Calculated: …", "Estimated: …" | Show the formula or the source of the estimate |
| Designed, not built | "Designed but not implemented: …" | Point to the AISS item or roadmap entry |

- Never upgrade a result: a local result is never written as an AWS result, an estimate never as a measurement, a configured setting never as a tested behaviour.
- Every number carries its date, its unit and its source. Use the same value everywhere; if two sources disagree, resolve it before writing.
- Give limits next to the claims they limit (for example: the 19 October run is not valid V3 acceptance evidence because the generator dropped iterations).

### Claims that are easy to get wrong

Initially checked against the repository on 21 October 2026; the lock-order clarification was rechecked on 22 October 2026. Recheck if the code changes.

| Topic | Correct statement |
|---|---|
| Image scanning | Each service image is built, then scanned by Trivy (`swe5001-service-images.yml`); fixable HIGH or CRITICAL findings fail the job before the image is exported |
| CodeQL | Not running (`ENABLE_CODEQL=false`); static analysis is SonarCloud |
| CloudFront → ALB | Plain HTTP, restricted by CloudFront's origin-facing prefix list and the `X-Origin-Secret` header; not encrypted |
| Secrets in state | Database passwords are write-only; the Valkey auth token and the origin secret are in Terraform state |
| Scaling target | 12,000 requests per target per minute (200 req/s), set in the `swe5001-demo` environment; the checked-in fallback is 200 |
| Capacity | One 0.5 vCPU commerce task saturates at about 280 req/s; three tasks served about 455 req/s, the generator's limit; linear scaling not proven |
| Event relay | About 50 events/s, calculated; one copy only, because it runs under the scheduler lease |
| Lock order | UC4 webhook settlement: team → purchase intent → payment, then fulfilment and order writes (`OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`; [ADR 0001](../../../backend/docs/adr/0001-payment-business-integration.md)). Expiry's `BEFORE_COMMIT` subscribers run order(10) → payment(20) ([ADR 0002](../../../backend/docs/adr/0002-team-lifecycle-events.md)); subscriber invocation order is not the webhook's row-lock order |
| SNS wait | The 15 minutes in a rebuild is a deliberate wait for subscription filter policies to apply, not for the topic |
| Data tier | One `db.t4g.micro`, single zone, no backups; one Valkey node; each service has its own database and roles |
| Table ownership | Every service database runs the full Flyway migration stream; the runtime role gets data access only to its service's tables (`afterMigrate__service_grants.sql`). Do not write "each database contains only its service's tables" |
| Referential integrity | No foreign keys or CHECK constraints by team convention; relationships are kept by the application |
| Not run on AWS | V4 burst, V5 self-healing, V7 poison message, identity outage; local results only |

## Diagram rules

- Use UML or a named notation (C4, DDD context map, crow's foot) and say which in the caption.
- Each caption names the viewpoint and what the reader should notice: "Figure 3.5: Physical deployment view. Only the ALB and NAT sit in public subnets."
- Commit the editable source next to the export in `diagrams/`; never commit an image without its source.
- Reuse [`docs/presentation-kit/diagrams/STYLE.md`](../../presentation-kit/diagrams/STYLE.md) colours so report and deck match.
- One diagram, one purpose. Split a diagram that needs more than one sentence to explain what it shows.
- Class and sequence diagrams for 3.2.4 show real classes, methods and transaction boundaries, not boxes named after services.

### Logical view rules

The assessors found the presentation's logical diagram non-standard (F12). It mixed logical elements with AWS products, used `«node»` for a routing rule, and put timeouts and comments on the arrows. The report's logical view follows these rules:

1. **Technology-neutral.** Name roles, not products: "CDN and edge gateway", not CloudFront and ALB; "message broker" and "event queue", not SNS and SQS; "session store", not Valkey. Products belong in the physical view (3.2), with a mapping table from logical element to product.
2. **One notation per diagram.** Either a UML component diagram (components, provided interfaces as lollipops, required interfaces as sockets, dependencies) or a logical detail deployment diagram (logical nodes such as client, web tier, application tier, integration tier and data tier, with components deployed on them). Do not mix the two.
3. **Layers visible.** Arrange elements in tiers (presentation, application, integration, data) so the reader sees 3.1.2 in the picture.
4. **Interfaces, not URLs.** Label each connection with the interface and its style (synchronous REST, asynchronous published event), not with paths, timeouts or notes. Put details in the text or a table.
5. **Correct stereotypes.** `«component»`, `«subsystem»`, `«database»`, `«external system»`; `«node»` only in deployment diagrams.
6. **Producers and consumers.** Show which interfaces the platform offers to producers (sellers, admins) and consumers (buyers), and which to future services (published events).

### UML checks for class and sequence diagrams

- Class diagrams: visibility, key attributes and operations, interfaces with `«interface»`, realisation and dependency arrows drawn correctly, multiplicities on associations.
- Sequence diagrams: lifelines named `object : Class`, synchronous and asynchronous messages drawn differently, return messages where values matter, `alt`, `opt` and `loop` fragments, the transaction boundary marked.
- Every element on a detailed design diagram traces to a class in the code; every element on a DDD diagram traces to a row in the aggregate or context table.

## IDs and traceability

| Prefix | Meaning | Register |
|---|---|---|
| `AD-nn` | Architectural decision | Report 3.1.1, 3.2.1, 3.3, numbered in [`registers.md`](registers.md) |
| `AISS-nn` | Architectural limitation | Report 3.4 only; numbered in [`registers.md`](registers.md) |
| `F-nn` | Assessor remark from 21 October | README feedback table |
| `D-nn` | Diagram | README diagram list |
| `NFR-nn` | Quality requirement | [`docs/quality/nfr-register.md`](../../quality/nfr-register.md) |

Link ADs to their ADR in [`backend/docs/adr/`](../../../backend/docs/adr/README.md) where one exists.

## Workflow per section

1. Read the section's row in the README outline and the template's own guidance text for it.
2. Collect the sources listed there; note any fact you cannot trace to a file or record.
3. Draft in `sections/`, answer first.
4. Run the definition-of-done checklist below.
5. Open a pull request into `dev`; another section owner reviews it against this file.
6. When a section closes a feedback item, mark it in the PR description (`Closes F5`).

## Definition of done (per section)

- [ ] Template number and title unchanged; every template prompt for the section is answered.
- [ ] Opening sentences answer the section's question.
- [ ] Every claim is marked by kind and linked to its evidence.
- [ ] Every number has a date, unit and source, and matches the other sections.
- [ ] Every class, method, table and event name exists in the code.
- [ ] Every diagram has a notation, a caption with its viewpoint, and a committed source.
- [ ] Decisions list the rejected options and the trade-off.
- [ ] Limitations found while writing are in 3.4 with an owner and a date.
- [ ] The feedback items this section owns (README) are closed by a concrete artefact.
- [ ] Reviewed by a second owner.

## Definition of done (whole report)

- [ ] F1–F12 each closed by the artefact named in the README.
- [ ] Focus areas A (DDD), B (class-level design) and C (database schema) each have every artefact the README lists.
- [ ] Q1–Q8 and the success criteria each answered where the briefing coverage table says.
- [ ] One AD register and one AISS register, with no conflicting IDs.
- [ ] All D1–D14 present and referenced from the text.
- [ ] Effort per member and milestones match section 2.3 and the agreed split.
- [ ] No `#prompt`, `#todo`, `#owner`, `#diagram-slot` or `#pending` left; the PDF builds without warnings; contents, figure numbers and cross-references checked in the PDF.
- [ ] Submitted before 16 November 2026.
