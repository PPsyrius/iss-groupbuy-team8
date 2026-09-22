---
name: agile-evidence-capture
description: Capture BlocBuy's SWE5006 evidence while doing the work. Use with coding, NFR, design, tooling, commit, PR, sprint, board, report, retrospective, ADR, acceptance-criteria, or evidence tasks, even when documentation was not explicitly requested.
---

# Agile evidence capture

## Purpose

The course grades the written report heavily. Record decisions, verification, contribution, and problems while the facts are available instead of reconstructing them later.

## Evidence map

| Artefact | Path | Purpose |
|---|---|---|
| Daily sprint log | `docs/agile/sprint-NN/log/YYYY-MM-DD.md`; `log.md` is the index | plan vs actual, verification, problems, contribution |
| NFR register | `docs/quality/nfr-register.md` | quality scenarios and evidence |
| Frontend ADRs | `frontend/docs/adr/` and its index | frontend decisions only |
| Backend decisions | D-table in `backend/docs/design/backend-skeleton-plan.md` or owning `backend/docs/design/*.md` | backend decisions |
| Cross-cutting decisions | dated section in the owning `docs/` file | CI, deployment, quality, process |
| Board snapshots | `docs/agile/metrics/YYYY-MM-DD.csv` | burndown |
| Sprint records | `docs/agile/sprint-NN/{plan,review,retro}.md` | sprint planning and outcomes |
| Issues and PRs | GitHub templates under `.github/` | acceptance criteria and traceability |

See `docs/agile/README.md` for the human-facing index and `references/report-mapping.md` when drafting reports.

## Classify the work

| Kind | Evidence required |
|---|---|
| Feature or bug | issue acceptance criteria, daily log entry, PR body |
| NFR | NFR issue if absent, register row, daily log entry, PR body |
| Decision | owning decision record plus a log pointer |
| Ops or process | daily log entry; decision record only for durable constraints |
| `style:` or `chore(deps):` | Conventional Commit only; these are the only log exemptions |

NFR signals include security, performance, availability, recoverability, observability, cost, maintainability, compliance, DevSecOps, and changes under `.github/`, deployment, infrastructure, runtime configuration, Docker, caches, indexes, retention, or timeouts. Use `references/nfr-scenarios.md` for measurable acceptance criteria.

## Workflow

### Intake

- Find the issue and read its acceptance criteria. Search with `gh issue list --search "<keywords>"` and check the sprint container.
- If no issue exists, draft one from `references/templates.md` under `docs/agile/sprint-NN/drafts/<slug>.md`. Ask before creating an issue, label, or board change; delete the draft after filing and replace its references with `#N`.
- Record the sprint and author. Reuse an existing NFR register ID when applicable.

### During

- Record frontend decisions as ADRs under `frontend/docs/adr/`.
- Record backend decisions in the D-table or owning backend design document, never as backend ADRs.
- Record cross-cutting decisions as dated sections in the owning document under `docs/`.
- Explain any loosened gate or suppressed finding at the enforcement point and in the log.
- Keep contracts, DDL, configuration, and their canonical documentation synchronized.
- Capture verification immediately: command, count or status, commit or branch, what it proved, and what it did not prove.

### Close

1. Append one entry per PR per author to the day's sprint log using `references/templates.md`; include issue, kind, change, verification, decisions, and problems.
2. Add or update any NFR register row.
3. Tick only acceptance criteria that now pass.
4. Use a Conventional Commit, reference the issue, and commit evidence with the change it describes.
5. Complete `.github/PULL_REQUEST_TEMPLATE.md` with decisions and verification evidence.
6. Propose board updates and missing estimates; do not write them without approval.

The advisory commit hook in `.claude/settings.json` runs `scripts/commit-evidence-hook.sh`. Address its checklist or add an `Evidence-skipped: <reason>` trailer when a deliberate exemption applies.

## Sprint boundaries

- **Open:** create the sprint log and plan when available, then run `scripts/snapshot-board.sh` for the day-zero snapshot.
- **During:** snapshot after issue or board state changes; repeated snapshots on the same unchanged day are harmless.
- **Close:** record board summary, merged PRs, CI health, and deployed-environment checks in `review.md`; draft `retro.md` from actual log problems and actions. Mark unavailable evidence as "not checked" and never invent ceremonies.

## Rules

- Write committed artefacts in English; converse with the user in their language.
- Keep log entries under twelve lines, ADRs under sixty lines, and register rows to one table row.
- Prefer links over restating diffs. State what was not verified; "tested" alone is not evidence.
- Never rewrite historical records solely for a new convention; add a dated amendment.
- Do not push or create external artefacts without confirmation.

## References

- `references/templates.md`: log, register, ADR, issue, PR, review, and retrospective templates.
- `references/nfr-scenarios.md`: quality-scenario format and examples.
- `references/report-mapping.md`: report sections mapped to evidence.
- `scripts/snapshot-board.sh`: board snapshot and summary.
- `scripts/commit-evidence-hook.sh`: advisory evidence hook; set `AGILE_EVIDENCE_HOOK_MODE=enforce` to block.
