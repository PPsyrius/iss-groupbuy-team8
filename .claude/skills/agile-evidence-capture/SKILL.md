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
| Frontend ADRs | `frontend/docs/adr/` and its index | frontend architecture decisions |
| Backend ADRs | `backend/docs/adr/` and its index | backend architecture decisions |
| Backend design | D-table in `backend/docs/design/backend-skeleton-plan.md` or owning `backend/docs/design/*.md` | locked stack and focused design decisions |
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
- Establish the checked-out branch and its working-tree diff before assessing what "this branch" solves. A local change, a pushed feature branch, merged `dev`, a deployed artifact and externally accepted behavior are distinct states; record which one the evidence actually supports.

### During

- Record frontend decisions as ADRs under `frontend/docs/adr/`.
- Record backend architecture decisions in numbered `backend/docs/adr/` records and its index; amend the owning ADR when one exists. Keep locked stack decisions in the D-table and focused design decisions in their owning `backend/docs/design/*.md` files.
- Record cross-cutting decisions as dated sections in the owning document under `docs/`.
- Explain any loosened gate or suppressed finding at the enforcement point and in the log.
- Keep contracts, DDL, configuration, and their canonical documentation synchronized.
- Capture verification immediately: command, count or status, commit or branch, what it proved, and what it did not prove.
- Name the verification level: focused tests, `verify -DskipTests` (static/compile only), full Docker-backed `verify` (Testcontainers and JaCoCo aggregate), or a CI run on a stated SHA. Do not cite a previous commit's successful build as proof of the current changes; if GitHub/AWS access is unavailable, say "not checked" rather than inferring a live status from a local snapshot.

### Close

1. Append one entry per PR per author to the day's sprint log using `references/templates.md`; include issue, kind, change, verification, decisions, and problems.
2. Add or update any NFR register row.
3. Tick only acceptance criteria that now pass.
4. Use a Conventional Commit, reference the issue, and commit evidence with the change it describes.
5. Complete `.github/PULL_REQUEST_TEMPLATE.md` with decisions and verification evidence: the issue link, the ADR or decision record, the `pnpm check` / `./mvnw verify` output or CI link, the contract line, a screenshot or Playwright trace for UI changes, the day-log entry and the register row (set verified against the template 2026-09-23, T2-10). ADR mechanics: the `adr` skill; contract changes: the `api-client-regen` skill.
6. Propose board updates and missing estimates; do not write them without approval.

The advisory commit hook in `.claude/settings.json` runs `scripts/commit-evidence-hook.sh`. Address its checklist or add an `Evidence-skipped: <reason>` trailer when a deliberate exemption applies.

## Sprint boundaries

- **Open:** create the sprint log and plan when available, then run `scripts/snapshot-board.sh` for the day-zero snapshot.
- **During:** snapshot after issue or board state changes; repeated snapshots on the same unchanged day are harmless.
- **Close:** record board summary, merged PRs, CI health, and deployed-environment checks in `review.md`; draft `retro.md` from actual log problems and actions. Mark unavailable evidence as "not checked" and never invent ceremonies.

## Rules

- Write committed artefacts in English; converse with the user in their language.
- Follow `CONTRIBUTING.md` for all Markdown prose, including sprint-log entries and PR bodies as well as READMEs, ADRs and `docs/` pages: one prose paragraph or prose list item per physical source line; preserve structural line breaks in code blocks, tables, and nested lists; do not reflow historical records to adopt it.
- Keep log entries under twelve lines, counting one bullet per physical line (never hard-wrap a bullet to fit), frontend ADRs under sixty lines, backend ADRs concise without rewriting longer accepted records, and register rows to one table row.
- Prefer links over restating diffs. State what was not verified; "tested" alone is not evidence.
- Never rewrite historical records solely for a new convention; add a dated amendment.
- Keep living operational guidance at a stable, descriptive canonical path. Preserve dated acceptance evidence, sprint plans, logs and snapshots as dated records; when moving a cited living page, leave a pointer for historical citations and update links in current-facing docs. Combine same-author entries that describe the same PR into one daily entry.
- Do not mark an issue done based solely on a feature-branch commit or a historical board snapshot; check its acceptance criteria and whether the relevant code is merged, deployed or externally verified.
- Do not push or create external artefacts without confirmation.

## References

- `references/templates.md`: log, register, ADR, issue, PR, review, and retrospective templates.
- `references/nfr-scenarios.md`: quality-scenario format and examples.
- `references/report-mapping.md`: report sections mapped to evidence.
- `scripts/snapshot-board.sh`: board snapshot and summary.
- `scripts/commit-evidence-hook.sh`: advisory evidence hook; set `AGILE_EVIDENCE_HOOK_MODE=enforce` to block.
