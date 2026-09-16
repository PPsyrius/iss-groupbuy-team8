# Report sections and where their evidence comes from

The progress report template (see `docs/SWE5006-Team8-Project-Progress-Report-Sprint1.docx`) has
fixed sections. Every artefact this skill writes exists to fill one of them. When drafting a
report, work down this table and pull from the named sources; when deciding whether something is
worth recording, check that it lands in a row here.

## Progress report

| Report section | Source artefact | How to assemble |
|---|---|---|
| 1.2 Project methodology | `CONTRIBUTING.md`, `docs/quality/quality-gates.md`, `docs/agile/README.md`, `.github/` templates and workflows | Cite what is enforced, not what is intended. Every claim about a ceremony needs a log entry or issue that shows it happened. |
| 2.1 Reporting period, 2.2 Sprint number | `docs/agile/sprint-NN/log.md` header | Dates are in the header. |
| 2.3 Sprint objectives | Sprint container issue (#49 for Sprint 1; `[T1] Sprint 2` issue), `sprint-NN/plan.md` | Copy the goal verbatim. |
| 2.4 Sprint accomplishment, plan vs actual | Container issue status board, `log.md` entries, `review.md` | One row per planned item: planned, actual, status, link to the PR or log entry. |
| 2.5 Burndown chart | `docs/agile/metrics/*.csv` | Count items not `Done` per snapshot date; plot. `snapshot-board.sh --summary` prints today's counts. |
| Problems encountered, action plan, status | `log.md` "Problems" lines, `retro.md` actions table | Each problem line already names the action; add the status at report time. |
| Sprint retrospective | `retro.md` | Written at the review meeting from the log; do not fabricate. |
| 3. Team member contributions | `log.md` entries grouped by handle, `git shortlog -sn --since <sprint start>`, issue "Contribution Summary linkage" | One row per member per sprint: activity, status, PR links. Empty cells mean the log was not kept, so keep it. |
| 4. Other highlights | `docs/quality/nfr-register.md`, `frontend/docs/adr/README.md`, `docs/ci/`, `docs/deploy/` | Register rows with status `enforced` are the strongest material. |

## Final report and presentation

| Section | Source |
|---|---|
| Requirements, use cases | Issues #24..#32 and later UC issues; `backend/docs/design/*.md` "Course analysis and UML" |
| Non-functional requirements and quality attributes | `docs/quality/nfr-register.md`, NFR issues, `docs/quality/quality-gates.md` |
| Architecture and key decisions | `frontend/docs/adr/` (the only ADRs); `backend/docs/design/backend-skeleton-plan.md` D-table and `backend/docs/design/*.md`; dated decision sections in `docs/ci/`, `docs/deploy/`, `docs/quality/quality-gates.md` |
| Design patterns applied | Issue "Design Problem" sections, design docs "Design problems and actual patterns" |
| Agile process evidence | `docs/agile/sprint-NN/`, `docs/agile/metrics/`, board snapshots |
| DevSecOps | `docs/ci/`, `.github/workflows/`, register rows for security and maintainability |
| Testing | Log "Verified" lines, design docs verification records, CI runs |
| Individual contribution | Log entries by handle across sprints |

## What is missing today (as of 2026-09-16)

Keep this list current; it is the honest "what could have gone better" for the next retro.

- No Sprint 1 log, review or retro exist; Sprint 1 evidence is the container issue #49 and the
  report itself.
- The board's Size and Estimate fields are unset on every item, so there is no velocity.
- Issue-to-PR links are rare; only one PR uses a closing keyword.
- Acceptance-criteria boxes are not ticked even for merged work.
- No milestones or iteration field on GitHub; sprints exist as issues and log headers.
