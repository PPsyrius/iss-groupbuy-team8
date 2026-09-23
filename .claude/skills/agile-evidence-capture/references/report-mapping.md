# Report evidence mapping

Use enforced or observed evidence, not intended process. Every ceremony claim needs a log entry, issue, or meeting artefact.

## Progress report

| Report section | Sources | Assembly |
|---|---|---|
| Project methodology | `CONTRIBUTING.md`, quality gates, agile README, GitHub templates and workflows | Cite enforced practice. |
| Period and sprint | `docs/agile/sprint-NN/log.md` | Copy index dates. |
| Sprint objectives | sprint container issue, `plan.md` | Copy the goal. |
| Plan vs actual | container status, daily log entries, `review.md` | One row per planned item with status and evidence. |
| Burndown | `docs/agile/metrics/*.csv` | Plot items not `Done` per snapshot. |
| Problems and actions | daily log `Problems` lines, `retro.md` | Add current status at report time. |
| Retrospective | `retro.md` | Record what actually occurred. |
| Contributions | daily log entries by handle, `git shortlog`, issue contribution links | One row per member per sprint. |
| Highlights | NFR register, frontend ADR index, CI and deployment docs | Prefer enforced register rows. |

## Final report and presentation

| Section | Sources |
|---|---|
| Requirements and use cases | UC issues; `backend/docs/design/*.md` analysis and UML |
| NFRs and quality attributes | NFR register, NFR issues, quality gates |
| Architecture and decisions | frontend ADRs; backend D-table and design docs; dated cross-cutting decisions |
| Design patterns | issue design-problem sections and backend design docs |
| Agile process | sprint directories, metrics, board snapshots |
| DevSecOps | CI docs, workflows, security and maintainability register rows |
| Testing | daily log verification, design verification records, CI runs |
| Individual contribution | daily log entries grouped by handle |

## Known evidence gaps as of 2026-09-16

- No Sprint 1 log, review, or retrospective; use issue #49 and the report.
- Board Size and Estimate fields are empty, so velocity is unavailable.
- Issue-to-PR links and closing keywords are uncommon.
- Acceptance criteria remain unticked on some merged work.
- GitHub has no milestone or iteration field; sprint issues and log headers define the sprint.
