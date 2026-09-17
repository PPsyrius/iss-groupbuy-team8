---
name: agile-evidence-capture
description: Capture the agile paper trail for BlocBuy (SWE5006 Team 8) while doing the work, not after. Use this skill alongside any coding task in this repository - implementing a use case, fixing a bug, changing CI or workflows, touching security, cookies, logging, caching, config, Terraform, Docker, CloudFront, performance, or any other non-functional concern, making a design or tooling choice, preparing a commit or PR, opening or closing a sprint. Also use it when the user mentions the sprint log, NFR register, ADRs, acceptance criteria, the Kanban board, burndown, retrospective, progress report, or "evidence". Run it even when the user did not ask for documentation: the course grades the report more than the code, and the artefacts are cheap to write now and expensive to reconstruct later.
---

# Agile evidence capture

## Why this exists

The SWE5006 assessment is mostly the written report. The Sprint 1 progress report had a `[TODO]` for
the burndown chart and a `[TODO]` in every contribution cell, and its retrospective admitted that
story points and board data were not maintained. Engineering evidence came straight from the repo;
process evidence had to be reconstructed from memory. Non-functional work has been the biggest
leak: secure cookie attributes (PR #84), CI time cuts (PR #75) and CloudWatch log shipping (PR #45)
all merged with no issue, no stated rationale and no doc update.

The fix is not more writing. It is writing a few lines at the moment the knowledge exists, into
files that map one-to-one onto report sections. One dated line in the sprint log while the
verification output is still on screen beats a paragraph guessed two weeks later.

## Where evidence lives

| Artefact | Path | Feeds |
|---|---|---|
| Sprint log (one per sprint, repo-wide) | `docs/agile/sprint-NN/log.md` | plan vs actual, problems, contributions |
| NFR register | `docs/quality/nfr-register.md` | quality attributes, highlights |
| ADRs (frontend only) | `frontend/docs/adr/`, README index; nobody else writes ADRs, see below | key decisions |
| Backend decisions | D-table in `backend/docs/design/backend-skeleton-plan.md` (dated amendments), design docs in `backend/docs/design/` | key decisions |
| Cross-cutting decisions (CI, deploy, quality gates, process) | the owning doc under `docs/` (`docs/ci/`, `docs/deploy/`, `docs/quality/quality-gates.md`, `docs/design/`) | key decisions |
| Board and issue snapshots | `docs/agile/metrics/YYYY-MM-DD.csv` | burndown |
| Sprint plan, review, retro | `docs/agile/sprint-NN/{plan,review,retro}.md` | sprint sections |
| Issues and PRs | GitHub, using the templates in `.github/` | backlog, traceability |

`docs/agile/README.md` is the human-facing version of this table. The full mapping from each
report section to its source artefact is in `references/report-mapping.md`; read it when drafting
a progress report or when unsure whether something is worth recording.

## Classify the work first

Decide which of these the task is, because the artefacts differ. A task can be more than one.

| Kind | Signals | Artefacts to touch |
|---|---|---|
| Feature slice | a use case issue (UC1..UC9), an endpoint, a page, a domain rule | issue AC ticked, log entry, PR body |
| NFR | security, performance, observability, availability, recoverability, cost, maintainability, compliance, DevSecOps; anything in `.github/`, `application*.yml`, `deployment/`, `infra/`, Dockerfiles, CloudFront, caches, indexes, retention, timeouts | NFR issue (if none), NFR register row, log entry, PR body; ADR if a choice was made |
| Decision | two or more viable options were weighed; something is now hard to reverse | frontend: ADR in `frontend/docs/adr/`; backend: D-table row or design-doc section; cross-cutting: a dated section in the owning doc under `docs/`; log entry pointing at it |
| Ops or process | runbooks, deploy steps, board hygiene, templates, skills | log entry; ADR only if it constrains future work |
| Chore | formatting, dependency bumps, typo fixes | nothing beyond the Conventional Commit |

When the task is an NFR and there is no issue, that is the moment the paper trail usually breaks.
Draft the issue before writing code. `references/nfr-scenarios.md` shows how to phrase the
acceptance criteria as a quality-attribute scenario (stimulus, response, measure) and lists the
attributes this project already cares about.

## Three moments in a task

### 1. Intake, before code

- Find the issue. Search `gh issue list --search "<keywords>"` and the sprint container issue
  (Sprint 1: #49; Sprint 2: the `[T1] Sprint 2` issue). Read its acceptance criteria; they are the
  definition of done for the slice.
- No issue? Draft one from `references/templates.md` in the team's own shape (header table,
  description as a user story, design problem, checkbox acceptance criteria, contribution linkage).
  Show the draft and ask before running `gh issue create`. Creating issues and labels is an
  outward-facing write; the user decides. Until it is filed, the draft lives at
  `docs/agile/sprint-NN/drafts/<slug>.md` so it ships with the PR; once filed, delete the draft
  and replace every reference to it with `#N`.
- Note the sprint and the author. Every log entry carries both, because the report's contribution
  table is per member and per sprint.
- If the task touches an NFR, check `docs/quality/nfr-register.md` for an existing row. Reuse its
  ID in the issue and the log.

### 2. During, while the knowledge is fresh

- **A frontend choice between alternatives** becomes an ADR: Nygard format, next number in
  `frontend/docs/adr/`, under sixty lines, added to that directory's README index. Write it when
  the choice is made, not at PR time, because the rejected options are forgotten fastest. Routine
  or easily reversed choices do not get an ADR; a sentence in the log entry is enough.
- **A backend choice** does not become an ADR. The backend keeps its own record: add or amend a
  dated row in the locked decision table (D1..D20) of `backend/docs/design/backend-skeleton-plan.md`
  for stack or architecture decisions, or a "Design problems and actual patterns" paragraph in the
  owning design doc under `backend/docs/design/` for use-case decisions. The log entry points there.
- **A cross-cutting choice** (CI, deployment, quality gates, team process) does not become an ADR
  either. Record it as a dated paragraph in the doc that owns the concern (`docs/ci/`,
  `docs/deploy/`, `docs/quality/quality-gates.md`, `docs/design/`), with the alternatives rejected,
  and point the log entry there. Only the frontend writes ADRs.
- **A gate loosened or a finding suppressed** gets the written reason the quality-gates exception
  process asks for, in the config file or SonarCloud comment, plus a log line.
- **A contract, DDL or config convention changed** keeps its synced artefacts in step. The backend
  and DDL skills already say what those are; this skill only asks that the log entry names them.
- **A verification run** (tests, `pnpm check`, `./mvnw verify`, a curl probe against dev) is
  captured as it happens: the command, the count or status, the commit or branch it ran against.
  This becomes the log entry's verification line and the PR's evidence section. Copy the shape of
  the "Local verification record" in `backend/docs/design/admin-listings.md`: what ran, what it
  proved, what it did not prove.

### 3. Close, before commit or PR

Do these in order; each is a few lines.

1. **Sprint log entry.** Append to `docs/agile/sprint-NN/log.md` using the template. Date, author,
   issue, kind, what changed, verification, decisions or ADRs, open problems. If a problem was
   hit, name it and the action taken; the report has a "problems encountered, action plan, status"
   table that is otherwise empty.
2. **NFR register.** Add or update the row for any NFR touched: scenario, where it is enforced,
   the evidence link (PR, workflow, config path), status.
3. **Acceptance criteria.** Tick the boxes on the issue that now pass (`gh issue edit` on the body,
   or ask the user to tick them). Leave unticked ones alone and say why in the log.
4. **Commit message.** Conventional Commit with scope, and a body when the why is not obvious.
   Reference the issue (`Refs #N` or `Closes #N`). Evidence files travel in the same commit as the
   code they describe, so one diff shows both.
5. **PR body.** Fill `.github/PULL_REQUEST_TEMPLATE.md`: issue, ADRs, evidence lines, sprint-log
   entry checkbox, NFR register checkbox. Paste the verification output or the CI run link.
6. **Board.** Propose the status move (`Ready` -> `In progress` -> `In review` -> `Done`) and, if
   Size or Estimate are empty, propose values. Board writes need the user's go-ahead.

The commit hook in `.claude/settings.json` runs `scripts/commit-evidence-hook.sh` before every
`git commit` and adds a short checklist to your context when evidence looks missing. It is
advisory by default: read it, add what is missing, or add an `Evidence-skipped: <reason>` trailer
to the commit message when the skip is deliberate. Chores (`style:`, `chore(deps):`, `revert:`)
are exempt.

## Sprint boundaries

**Opening a sprint.** Create `docs/agile/sprint-NN/` with a `log.md` header (dates, goal, link to
the sprint container issue) and, if the team wrote one, `plan.md` mirroring the container issue's
goal, member-by-use-case matrix and status board. Run `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh` once so the
burndown has a day-zero point.

**During a sprint.** Run `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh` at the end of any session that changed issue
or board state. Each run writes one dated CSV; a run on an unchanged day is harmless. Daily
snapshots are what the burndown chart is drawn from. Since 2026-09-18 the `board-snapshot`
workflow takes the daily one at 23:30 Singapore and keeps an open PR of the unmerged days; a
hand run is for a same-day snapshot you want in your own PR.

**Closing a sprint.** Gather facts before writing, from four read-only sources, and record each
result or "not checked" in `review.md`:

1. Board and issues: `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh --summary`.
2. Merges in the window: `gh pr list --state merged --search "merged:>=<sprint start>"`.
3. CI health over the window: `gh run list --workflow <each workflow> --limit 20`; a workflow
   that was red for days is a review talking point, and "gates stayed green" is a claim that
   needs this output behind it.
4. The deployed environments: `curl -sS -o /dev/null -w '%{http_code}'` against the health URL
   and one page URL from `docs/deploy/`. A demo plan that assumes dev is up when it returns 503
   is worse than no plan.

Then draft `review.md` (plan vs actual, per the container issue's status board and the log) and
`retro.md` (what went well, what could be better, what to try next) from the log entries. Show
both for the team to edit at the review meeting. Do not invent ceremonies that did not happen; if
there was no stand-up, the retro says so. If the container issue was never filed, say so and
measure against the written plan instead.

## Keep it cheap

- A log entry is under a dozen lines; the Verified and Problems lines earn their length, the
  Changed line should not restate the diff. An ADR is under sixty lines. A register row is one
  line. Take the next free `NFR-NN`; if a parallel PR took the same number, renumber at merge.
- Prefer a link to a PR, workflow run or config path over prose that restates it.
- Never rewrite existing docs to match a new convention; add a dated line that points forward.
- Ask at most one question per moment, and only when the answer changes what gets written.
- Do not push, and do not create issues, labels or board changes without confirmation.
- Say what was not verified. "Local `verify` green; not exercised against dev" is evidence.
  "Tested" is not.

## Files in this skill

- `references/templates.md`: log entry, NFR register row, ADR, issue bodies, PR body, review and
  retro skeletons. Copy from here so the shapes stay uniform.
- `references/nfr-scenarios.md`: quality-attribute scenario format and the project's attribute
  list with worked examples.
- `references/report-mapping.md`: progress report and final report sections mapped to artefacts.
- `scripts/snapshot-board.sh`: issues plus board state to `docs/agile/metrics/YYYY-MM-DD.csv`.
- `scripts/commit-evidence-hook.sh`: the PreToolUse hook; `AGILE_EVIDENCE_HOOK_MODE=enforce`
  makes it block instead of advise.
