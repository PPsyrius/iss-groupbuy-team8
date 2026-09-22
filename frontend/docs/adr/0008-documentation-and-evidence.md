# 0008. Documentation and evidence conventions

Date: 2026-09-16

## Status

Accepted

## Context

SWE5006 grades the written report far more than the code, and the report must evidence agile practice. The Sprint 1 progress report shipped with a TODO burndown chart and TODO contribution cells; its retrospective admitted that story points and board data were not maintained. Non-functional changes (secure cookies in PR #84, CI time in PR #75, log shipping in PR #45) merged with no issue, no rationale and no doc update. Decisions live in a locked table, dated strings inside skill files, and YAML comments. `docs/design/repository-structure.md` promised ADR, agile and metrics directories that were never created. The backend already records its decisions in a locked D1..D20 table with dated amendments and in per-use-case design docs, so a second decision format there would split one area's decisions in two.

## Decision

Evidence is written at the moment the knowledge exists, into files that map onto report sections:

- Sprint log at `docs/agile/sprint-NN/log.md`, repo-wide, one dated entry per unit of work per member. This supersedes the frontend-only `frontend/docs/sprint-2-log.md` planned in `plan.md`.
- NFR register at `docs/quality/nfr-register.md`: one scenario row per quality attribute, with enforcement point, evidence and status. Every NFR change updates it in the same PR.
- ADRs are a frontend practice only, in `frontend/docs/adr/`: Nygard format, under sixty lines, written when the choice is made. The backend keeps its locked decision table with dated amendments in `backend/docs/design/backend-skeleton-plan.md` plus design docs under `backend/docs/design/`. Cross-cutting decisions (CI, deployment, quality gates, process) are dated paragraphs in the doc that owns the concern under `docs/`. No `docs/adr/` or `backend/docs/adr/` is created.
- Board and issue snapshots at `docs/agile/metrics/YYYY-MM-DD.csv` as burndown source data.
- Issue and PR templates under `.github/` carry the shapes; every PR links its issue, names ADRs, pastes verification output or a CI link, and ticks the log and register boxes.
- The `agile-evidence-capture` skill and an advisory commit hook in `.claude/settings.json` prompt Claude Code sessions for the same artefacts. The skill absorbs the planned `sprint-evidence` skill.

Alternatives rejected: wiki pages (not reviewed with code); report-only documentation (no trace to commits); per-area sprint logs (splits one sprint's story across directories); one ADR directory per area (duplicates the backend D-table and gives cross-cutting decisions a second home away from the runbooks that already carry them).

## Consequences

Every non-chore PR grows by a few lines of log and, for NFR work, one register row. Reviewers have a checklist. Progress reports are assembled from files rather than memory (`.claude/skills/agile-evidence-capture/references/report-mapping.md`). Sprint 1 stays unlogged and is marked as such. The `docs/design/adr/` directory promised by `docs/design/repository-structure.md` is deliberately not created. The hook is advisory; if entries are still skipped after Sprint 2, switch it to enforce mode. `frontend/docs/plan.md` references to the frontend sprint log and the `sprint-evidence` skill are redirected; the deviation is recorded in its section 12.

2026-09-27 addendum: the original Decision describes the 2026-09-16 backend practice, but numbered backend ADRs were subsequently adopted in `backend/docs/adr/` (0001–0003). Backend decisions now use that index and their owning design docs; current sprint entries live under `docs/agile/sprint-NN/log/YYYY-MM-DD.md` and `log.md` is only the index. This amends the paths without removing the original evidence decision.
