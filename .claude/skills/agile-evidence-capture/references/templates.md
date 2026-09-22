# Evidence templates

## Daily sprint log

Append one entry per PR per author to `docs/agile/sprint-NN/log/YYYY-MM-DD.md`. Drop fields that do not apply.

```markdown
## YYYY-MM-DD - @handle - <work summary> (#N)

- Kind: feature | nfr | decision | ops | chore
- Changed: <outcome and canonical artefacts>
- Verified: `<command>` <count/status> on `<commit>`; <not verified>
- Decisions: <owning decision record or "none">
- Problems: <problem, action, tracking issue, status>
- Next: <follow-up or blocker>
```

Never write "tested" without the command and count or status.

## NFR register row

Add one row to `docs/quality/nfr-register.md`:

```markdown
| NFR-NN | <attribute> | when <stimulus>, the system <response>, measured by <measure> | <enforcement> | <evidence> | enforced | implemented | documented | target undefined |
```

Use security, performance, availability, recoverability, observability, cost, maintainability, interoperability, data consistency, usability, or compliance. Statuses mean:

- `enforced`: CI or runtime control fails on violation.
- `implemented`: present and verified at least once.
- `documented`: designed but not implemented.
- `target undefined`: relevant, but no measure is agreed.

## Frontend ADR

Use Nygard format under sixty lines in `frontend/docs/adr/NNNN-kebab-title.md` and add it to that directory's index. Backend and cross-cutting decisions use their owning design documents, not ADRs.

```markdown
# NNNN. <Decision title>

Date: YYYY-MM-DD

## Status

Proposed | Accepted | Superseded by NNNN

## Context

<Forces, constraints, and triggering issue.>

## Decision

We will <choice>. We rejected <alternatives> because <reasons>.

## Consequences

<Benefits, costs, synchronization obligations, and revisit condition.>
```

## Use-case issue

```markdown
## Use Case: UCn - <title>

| Field | Value |
|---|---|
| Complexity | CRUD / medium / complex |
| Accountable Members | @handle (frontend) / @handle (backend) |
| Design Problem Evidenced | <problem> - **<Pattern>** |
| Notes | <entry point, dependencies, sprint> |

### Description
As a <role>, I can <action>, so that <benefit>.

### Design Problem
<What varies or must be isolated, and the pattern used.>

### Acceptance Criteria
- [ ] <observable backend behaviour>
- [ ] <observable frontend behaviour>
- [ ] Unit tests cover <complex behaviour>

### Contribution Summary linkage
- <Member> - <ownership>
```

## NFR issue

```markdown
## Non-functional Requirement: <attribute> - <title>

| Field | Value |
|---|---|
| Quality attribute | security / performance / availability / recoverability / observability / cost / maintainability |
| Accountable Member | @handle |
| Register ID | NFR-NN or new |
| Design Problem Evidenced | <mechanism> |
| Notes | <sprint, trigger, related UC> |

### Description
As a <team/operator/user>, we need <capability>, so that <risk is reduced or quality improves>.

### Quality attribute scenario
- Source: <triggering actor or system>
- Stimulus: <event>
- Environment: <operating condition>
- Response: <system behaviour>
- Measure: <observable threshold>

### Acceptance Criteria
- [ ] <measure holds, with verification command>
- [ ] <CI, configuration, or test enforces it>
- [ ] NFR register row added or updated

### Contribution Summary linkage
- <Member> - <ownership>
```

## Engineering task or bug

```markdown
## Background
<Observation, decisive log or request, and triggering issue/PR.>

## Scope
1. <change>
2. <change>

## Non-goals
- <excluded work>

## Acceptance Criteria
- [ ] <observable result>
```

Unfiled issue drafts live under `docs/agile/sprint-NN/drafts/` and are deleted after filing.

## PR body

Use `.github/PULL_REQUEST_TEMPLATE.md` as the source of truth:

```markdown
## What
<One-paragraph summary.>

## Issue
Closes #N | Refs #N

## Decisions
<Frontend ADR, backend/cross-cutting design record, or none.>

## Evidence
- [ ] Verification: `<command>` <count/status> on <commit or CI link>
- [ ] Contract: unchanged | regenerated, additive | approved breaking change
- [ ] UI evidence attached when applicable
- [ ] Daily sprint-log entry added
- [ ] NFR register updated when applicable

## Checklist
- [ ] Small PR, one concern
- [ ] No secrets; new configuration documented
```

## Sprint review

```markdown
# Sprint NN review (YYYY-MM-DD to YYYY-MM-DD)

## Goal
<Container issue goal.>

## Plan vs actual
| Work item | Owner | Planned | Actual | Status | Evidence |
|---|---|---|---|---|---|

## Environment and CI at close
| Check | Command | Result |
|---|---|---|
| dev health | `curl ...` | <status or not checked> |
| dev page | `curl ...` | <status or not checked> |
| backend CI | `gh run list --workflow ci-backend.yml --limit 10` | <summary> |
| security CI | `gh run list --workflow security.yml --limit 10` | <summary> |

## Demo notes
<What was shown, environment, links, and fallback.>

## Carried over
- <item, reason, tracking issue>
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

Draw review and retrospective content from recorded facts. Do not invent ceremonies.
