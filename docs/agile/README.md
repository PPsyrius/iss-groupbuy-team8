# Agile evidence

This directory is the durable record of how the team worked, sprint by sprint. GitHub Issues and the Kanban board are the live tools; what lives here is what the progress and final reports are written from, versioned with the code it describes.

## Layout

```
docs/agile/
├── README.md              this file
├── sprint-01/README.md    pointer to the Sprint 1 container issue and report
├── sprint-02/log/         running log, one file per day: one dated entry per PR per author
├── sprint-02/log.md       index of the day files
├── sprint-NN/plan.md      sprint goal and member-by-use-case matrix (mirrors the container issue)
├── sprint-NN/review.md    plan vs actual, written at the sprint review
├── sprint-NN/retro.md     what went well, what could be better, what we try next
└── metrics/YYYY-MM-DD.csv issue and board snapshots; burndown source data
```

Related homes:

- `docs/quality/nfr-register.md`: every non-functional requirement, its scenario, where it is enforced, and the evidence.
- `frontend/docs/adr/` and `backend/docs/adr/`: architecture decision records, numbered independently; the backend's locked stack decisions remain in the D-table of `backend/docs/design/backend-skeleton-plan.md`, and focused design decisions in `backend/docs/design/*.md`. Cross-cutting decisions (CI, deployment, quality gates, process) are dated paragraphs in the owning doc under `docs/`.
- `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md`: the shapes issues and PRs take.

## The rule

Every PR adds a sprint-log entry in the same PR. Every PR that touches a non-functional concern adds or updates an NFR register row. Every real choice between alternatives is written down where its area keeps decisions: a frontend or backend ADR, an existing backend D-table row or focused design-doc section, or a dated paragraph in the owning `docs/` page. Only `style:` and `chore(deps):` PRs are exempt.

Entries are short: three to eight lines, with the command that verified the change and the count or status it produced. A line is one bullet on one physical line; never hard-wrap a bullet to meet the limit. Links beat prose.

The one-entry-per-PR-per-author rule applies from 2026-09-22; older entries remain unchanged as historical evidence.

## How it is enforced

- The PR template asks for the log entry and register row as checkboxes; reviewers check them.
- Claude Code sessions in this repo load the `agile-evidence-capture` skill (`.claude/skills/`) and a commit hook (`.claude/settings.json`) that surfaces a checklist when evidence is missing.
- `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh` writes today's board and issue state to `metrics/`. Run it at the end of any session that moved issues; commit the CSV. Scheduled operation and CSV interpretation are in `metrics/README.md`.

## One-time setup (team lead)

Labels for NFR work do not exist yet. Create them once:

```bash
for a in security performance availability recoverability observability cost maintainability; do
  gh label create "nfr:$a" --color 5319E7 --description "Non-functional requirement: $a" || true
done
```

Board snapshots need `read:project`: `gh auth refresh -s read:project`. The scheduled run needs a separate `BOARD_SNAPSHOT_TOKEN` repository secret with access to the private board and repository/PR writes (off-repo TODO 8 in `docs/ci/devsecops-pipeline-plan.md`); local `gh` authentication does not update the secret, and missing or insufficient access fails the run.

## Sprint calendar

| Sprint | Dates | Container issue |
|---|---|---|
| 1 | 2026-09-02 to 2026-09-12 | #49 |
| 2 | 2026-09-14 to 2026-09-25 | #93 (`[T1] Sprint 2`) |
