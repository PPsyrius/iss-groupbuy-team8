## Non-functional Requirement: performance - Reduce backend verification latency without weakening quality gates

| Field | Value |
|---|---|
| Quality attribute | performance (delivery), maintainability |
| Accountable Member | @PPsyrius |
| Register ID | NFR-11 |
| Design Problem Evidenced | Bound integration-test concurrency and avoid SQL DEBUG output during CI while preserving isolated fixtures and complete coverage |
| Notes | Sprint 4; unfiled draft; triggered by PR #570 build-quality job 118447125228 |

### Description
As the team, we need faster backend verification without dropping tests, sharing mutable test data, or weakening required checks and coverage.

### Quality attribute scenario
- Source: a backend contributor opening or updating a pull request.
- Stimulus: run the complete eleven-project Maven reactor, including the real-time 2,000-team expiry rehearsal.
- Environment: Java 21, Docker-backed fixtures, identical source, clean compiled outputs and warm dependencies/images.
- Response: use INFO application logging and at most two isolated bootstrap integration-test JVMs; retain local serial/DEBUG defaults.
- Measure: compare complete before/after verification time, executed/skipped test inventory and JaCoCo coverage; target at least a 15% local wall-clock reduction, with GitHub-hosted savings verified separately after approval to push.

### Acceptance Criteria
- [x] Complete Docker-backed Maven verification passes before and after; no test is newly skipped or removed (1,957 → 1,958 passed, 1 unchanged opt-in skip).
- [x] Aggregate and service coverage reports remain readable and retain identical counters, including the baseline covered instructions/branches.
- [x] PR/tag validation and dev coverage select the CI profile without adding a required-check context or omitting modules (workflow/policy and effective-POM checks; hosted candidate run pending).
- [x] Policy regression tests and workflow lint pass.
- [x] NFR-11 and the October 20 sprint log record commands, measurements, limitations and the owning decision: [benchmark](../../../ci/backend-quality-benchmark-20261020.md), 480.57 → 356.27s locally (25.87% faster).

### Contribution Summary linkage
- @PPsyrius - backend CI performance diagnosis and verification.
