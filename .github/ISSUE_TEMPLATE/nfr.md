---
name: Non-functional requirement
about: Security, performance, availability, recoverability, observability, cost or maintainability work, as a checkable scenario
title: "NFR: <attribute> - <title>"
labels: enhancement
---

## Non-functional Requirement: <attribute> - <title>

| Field | Value |
|---|---|
| Quality attribute | security / performance / availability / recoverability / observability / cost / maintainability |
| Accountable Member | @handle |
| Register ID | NFR-NN in `docs/quality/nfr-register.md`, or "new" |
| Design Problem Evidenced | <mechanism, e.g. approval gate, immutable artifact, idempotency key> |
| Notes | <sprint, what triggered this, related UC> |

### Description
As a <team / operator / buyer>, we need <capability>, so that <risk avoided or quality gained>.

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
