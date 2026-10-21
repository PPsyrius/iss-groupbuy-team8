# Draft: repair observed SpotBugs and Gitleaks failures

## Non-functional requirement: security and maintainability

| Field | Value |
|---|---|
| Accountable member | @PPsyrius |
| Sprint | 4 (#465) |
| Register IDs | NFR-02, NFR-06 |
| State | Unfiled draft; no external issue, ruleset or board changes authorized |

## Background

[PR #559's backend run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39463373272) crashes because SpotBugs 4.10.5 is combined with plugin 4.10.4.1 and its older BCEL runtime. Repeated weekly Gitleaks runs flag twenty verifiable SHA-256 file digests and four unresolved badge-token findings. The [run-by-run audit](../../../ci/ci-failure-audit-20261021.md) also confirms CodeQL is unavailable and an Actions budget blocked the October 20 environment run before any runner started; those are external blockers, not reasons to add required jobs.

## Scope and quality scenario

- Upgrade the SpotBugs plugin and engine together so the reproduced class-loading failure disappears without disabling a detector; group future updates.
- Ignore only twenty exact historical digest fingerprints after recomputing the underlying file hashes; findings in new content at the same path remain detectable.
- Run cheap POM policy checks in the existing backend runner; keep secret scanning parallel, CodeQL opt-in, all required contexts and existing concurrency unchanged.

## Acceptance criteria

- [x] Reproduce the SpotBugs class-loading failure locally before upgrading plugin and engine together.
- [x] Full Java 21/Docker-backed `verify -Pci` passes with the aligned SpotBugs runtime (1,980 passed, one opt-in skip, zero SpotBugs findings).
- [x] Twenty ignored values match SHA-256 of the original source files; Gitleaks 8.24.3 full-history/all-ref scan falls from 24 to four findings over the same 458 commits, and 8.28.0 also reports the four unresolved findings.
- [x] Both Gitleaks versions detect synthetic new secret-shaped values in the baseline file and backend README; changed-file snapshots contain no findings.
- [x] Twenty-one Python tests, twenty-two Node tests and pinned Actionlint pass, with no new Maven build or runner job.
- [x] Runbook, quality-gate version, NFR rows and daily sprint evidence updated.
- [ ] Hosted verification passes on a pushed candidate; no push is authorized in this task.

## Non-goals and unresolved findings

No push, deployment, ruleset edit, billing change, credential operation, broad secret-scan exclusion or history rewrite is included. Four historical badge-token findings still require disposition; they are not declared safe or ignored. Current budget availability was not checked; any spending change requires an owner decision. Later Spotless runs passed and the TanStack manifest drift is already corrected; no blanket retry or skipped gate was added.

## Self-review disposition — 2026-10-21

Preserve parallel scans and existing concurrency for this repair: the audit did not establish a recent cancellation incident, while serializing dev workflows would queue delivery behind unrelated Sonar work. Aggregate guards and a custom expression interpreter add no value to these observed failures and are excluded. Ruleset changes were not authorized and CodeQL is unavailable; the final patch is limited to the reproduced runtime failure, verified digest false positives and low-cost checks.
