# Templates

Copy these shapes verbatim. They are lifted from artefacts the team already writes (issue #24,
issue #48, the Sprint 1 container issue #49, the backend design docs' verification record, the
frontend ADR stubs), so nothing here is a new convention.

## Sprint log entry

Append to `docs/agile/sprint-NN/log.md`. Newest entry at the bottom. One entry per unit of work
per author; a day with three PRs gets three entries.

```markdown
## YYYY-MM-DD - @handle - UC3 join team: payment commitment (#26)

- Kind: feature | nfr | decision | ops | chore
- Changed: `backend/blocbuy-application/.../JoinTeamService.java`, migration `V9__...`, contract regenerated
- Verified: `./mvnw verify` 211 tests green on `a1b2c3d`; not run against dev
- Decisions: `backend/docs/design/paid-group-participation.md` "Domain behavior" (optimistic lock on team capacity; backend decisions live in design docs, not ADRs)
- Problems: Stripe test key missing from CI secrets; action: asked @PPsyrius, tracked in #NN
- Next: frontend join button (#44) blocked on B1
```

Rules: date first, handle second, issue in the heading. Drop lines that do not apply. Never write
"tested" without the command and the count or status.

## NFR register row

One line in the table in `docs/quality/nfr-register.md`. Columns:

```
| NFR-NN | <attribute> | <scenario: when <stimulus>, the system <response>, measured by <measure>> | <enforced at: CI job, config path, code> | <evidence: PR #, workflow, doc> | enforced | implemented | documented | target undefined |
```

Attribute vocabulary: security, performance, availability, recoverability, observability, cost,
maintainability, interoperability, data consistency, usability, compliance. Status meaning:

- `enforced`: a CI gate or runtime control fails when it is violated.
- `implemented`: present in code or config and verified by hand at least once.
- `documented`: designed or promised, not yet in code.
- `target undefined`: the attribute matters but no measure has been set; this is a valid row and
  a report talking point.

## ADR

Frontend only. Nygard format, under sixty lines, file name `NNNN-kebab-title.md` in
`frontend/docs/adr/`, plus a row in that directory's `README.md` index. Nobody else writes ADRs:
backend decisions are recorded in the locked decision table of
`backend/docs/design/backend-skeleton-plan.md` (dated amendments in place) and in the design docs
under `backend/docs/design/`; cross-cutting decisions (CI, deploy, quality gates, process) as a
dated paragraph in the owning doc under `docs/`.

```markdown
# NNNN. <Title as a decision, e.g. "Optimistic locking for team capacity">

Date: YYYY-MM-DD

## Status

Proposed | Accepted | Superseded by NNNN

## Context

Two to five sentences: the forces, the constraint, the issue that raised it (#N).

## Decision

One paragraph in the active voice: "We will ...". Name the alternatives considered and why they
lost, one line each.

## Consequences

What becomes easier, what becomes harder, what must now be kept in sync, what would make us
revisit this.
```

## Issue: use case

```markdown
## Use Case: UCn - <title>

| Field | Value |
|---|---|
| Complexity | CRUD / medium / complex |
| Accountable Members | @handle (frontend) · @handle (backend) |
| Design Problem Evidenced | <problem> - **<Pattern>** |
| Notes | <entry point, dependencies, sprint> |

### Description
As a <role>, I can <action>, so that <benefit>.

### Design Problem
<what varies or what must be isolated, and the pattern that addresses it>

### Acceptance Criteria
- [ ] <observable behaviour, backend>
- [ ] <observable behaviour, frontend>
- [ ] Unit tests cover <the complex behaviour>

### Contribution Summary linkage
- <Member name> - <what they own in this UC>
```

Unfiled drafts of either issue shape live at `docs/agile/sprint-NN/drafts/<slug>.md` and are
deleted once the issue exists.

## Issue: non-functional requirement

```markdown
## Non-functional Requirement: <attribute> - <title>

| Field | Value |
|---|---|
| Quality attribute | security / performance / availability / recoverability / observability / cost / maintainability |
| Accountable Member | @handle |
| Register ID | NFR-NN (or "new") |
| Design Problem Evidenced | <mechanism, e.g. approval gate, immutable artifact, circuit breaker> |
| Notes | <sprint, trigger, related UC> |

### Description
As a <team | operator | buyer>, we need <capability>, so that <risk avoided or quality gained>.

### Quality attribute scenario
- Source: <who or what triggers it>
- Stimulus: <the event>
- Environment: <normal operation, peak, deploy, attack>
- Response: <what the system does>
- Measure: <the number or observable that says it worked>

### Acceptance Criteria
- [ ] <the measure holds on dev, with the command that shows it>
- [ ] <enforcement point exists: CI job, config, test>
- [ ] `docs/quality/nfr-register.md` row added or updated

### Contribution Summary linkage
- <Member name> - <what they own>
```

## Issue: engineering task or bug

Matches #56 and #57.

```markdown
## Background
<what was observed, with the request or log line; link the triggering issue or PR>

## Scope
1. <concrete change>
2. <concrete change>

## Non-goals
- <what this deliberately does not do>

## Acceptance Criteria
- [ ] <observable result>
```

## PR body

`.github/PULL_REQUEST_TEMPLATE.md` is the source of truth; this is the filled shape.

```markdown
## What
<one paragraph; same intent as the Conventional Commit title>

## Issue
Closes #N (or Refs #N when the issue stays open)

## Decisions
ADR(s) implemented or affected: `frontend/docs/adr/0009-...md` (frontend); a backend decision cites its D-table row or design doc, a cross-cutting one its section under `docs/`; or "none"

## Evidence
- [ ] Verification: `./mvnw verify` 211 tests green on a1b2c3d / CI run <link>
- [ ] Contract: unchanged | regenerated, additive | breaking, `contract-change-approved` requested because <reason>
- [ ] UI: screenshot or Playwright trace attached (frontend only)
- [ ] `docs/agile/sprint-NN/log.md` entry added
- [ ] `docs/quality/nfr-register.md` updated (NFR work only)

## Checklist
- [ ] Small PR, one concern
- [ ] No secrets committed; new config documented in the area README
```

## Sprint review

`docs/agile/sprint-NN/review.md`, drafted from the container issue's status board and the log.

```markdown
# Sprint NN review (YYYY-MM-DD to YYYY-MM-DD)

## Goal
<from the container issue>

## Plan vs actual
| Work item | Owner | Planned | Actual | Status | Evidence |
|---|---|---|---|---|---|

## Environment and CI at close
| Check | Command | Result |
|---|---|---|
| dev health | `curl -sS -o /dev/null -w '%{http_code}' <health url>` | <code or "not checked"> |
| dev page | `curl -sS -o /dev/null -w '%{http_code}' <page url>` | <code or "not checked"> |
| ci-backend last 10 runs | `gh run list --workflow ci-backend.yml --limit 10` | <n green / n red> |
| security last 10 runs | `gh run list --workflow security.yml --limit 10` | <n green / n red> |

## Demo notes
<what was shown, on which environment, with links; if dev is down, the fallback plan>

## Carried over
- <item, reason, new issue>
```

## Sprint retrospective

```markdown
# Sprint NN retrospective (YYYY-MM-DD)

## What went well
## What could have gone better
## What we will try next sprint
| Action | Owner | Tracked in |
|---|---|---|
```

Draw candidates from the log's "Problems" lines. Do not list a ceremony that did not happen.
