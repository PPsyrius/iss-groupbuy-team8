# OWASP ZAP before/after evidence

Report tracking: [#694](https://github.com/PPsyrius/iss-groupbuy-team8/issues/694); remediation: [#610](https://github.com/PPsyrius/iss-groupbuy-team8/issues/610); quality requirement: NFR-71.

## Before remediation: captured 2026-10-26

- [GitHub issue-list screenshot](before-2026-10-26/issue-list.png): full-page capture of the actual authenticated GitHub issue list, showing all twelve finding issues #636–#647 open, the open report-evidence issue #694 and closed scanner-implementation issue #606 (14 search results, not 14 security findings). The earlier locally rendered substitute was removed after authenticated browser access became available.
- [ZAP report summary image](before-2026-10-26/scan-summary.png): screenshot of the unmodified downloaded HTML report from run 40298983391, rendered locally with external network requests blocked.
- [Corrected issue records](encoding-correction-2026-10-26/issues.json): UTC capture time, titles, bodies, states, labels, timestamps and comments for fifteen unique issues (the ZAP title search plus #610 and #694). [Corrected open repository issues](encoding-correction-2026-10-26/open-issues.json) additionally preserves the 58-item open-issue list at capture time; it is not a security-findings count. Use these derivatives for report quotations; the [encoding erratum](encoding-correction-2026-10-26/README.md) documents the original exports' corruption and verification against GitHub. Original files remain frozen in `before-2026-10-26/` for auditability.
- [Latest pre-fix report](before-2026-10-26/run-40298983391/zap-baseline/report.html), [JSON](before-2026-10-26/run-40298983391/zap-baseline/report.json), [coverage](before-2026-10-26/run-40298983391/zap-baseline/coverage.md), and [run metadata](before-2026-10-26/run-40298983391.json): [scheduled run 40298983391](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/40298983391), 2026-10-26, workflow revision `9135ad8bdca9a5084b4ea2d11d619e13b8ba9f1d`. Artifact: `zap-baseline`.
- Original 2026-10-22 baseline: [web report](before-2026-10-26/run-39733450177/zap-web/report.html), [API report](before-2026-10-26/run-39733450177/zap-api/report.html), and [run metadata](before-2026-10-26/run-39733450177.json) from [run 39733450177](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39733450177). Artifacts: `zap-web` and `zap-api`. These older artifacts predate the consolidated coverage-verified scanner; do not treat their coverage as equivalent to the latest run.

| Severity | Latest pre-fix alert types | Finding issues |
|---|---:|---|
| High | 0 | None |
| Medium | 2 | #636–#637 |
| Low | 4 | #638–#641 |
| Informational | 6 | #642–#647 |

Latest scan coverage is COMPLETE: 10/10 required application responses and 67/67 enabled passive rules processed with the queue drained. Target: `https://d1b3np88z657cp.cloudfront.net/`. A successful workflow establishes scan completion, not absence of findings. Workflow revision is recorded, but the deployed application revision was not independently established. This is anonymous passive evidence only, with no authenticated or active testing claimed. Informational alerts are triage observations, not automatically confirmed vulnerabilities.

## Capture and integrity

2026-10-26 decision: preserve dated issue exports, images and original report artifacts together in the repository so the comparison does not depend on mutable issue states or thirty-day Actions retention. Do not overwrite this before directory when fixes land. Raw HARs are not included. A targeted credential-pattern check found only explanatory references, not credential material; this is not a comprehensive security audit.

2026-10-26 encoding erratum: original issue JSON had UTF-8 text misdecoded as CP437 during capture. Its original checksums establish preservation of those corrupted bytes, not source-text fidelity. [Corrected derivatives and source verification](encoding-correction-2026-10-26/README.md) are stored separately; all changed fields across 13 issues matched GitHub, while historical issue metadata remains from the original capture. Downloaded scan artifacts and screenshots were not changed.

The issue export used `gh issue list --repo PPsyrius/iss-groupbuy-team8 --state all --search '"[OWASP ZAP]" in:title' --limit 100 --json number,title,state,stateReason,body,labels,url,createdAt,updatedAt,closedAt,comments`, supplemented by `gh issue view` for #610 and #694 and deduplicated by issue number. The full open-issue export used `gh issue list --state open --limit 1000` with summary fields. Run metadata came from `gh run view --json`; artifact downloads used:

```powershell
gh run download 39733450177 --repo PPsyrius/iss-groupbuy-team8 --dir docs/quality/evidence/zap/before-2026-10-26/run-39733450177
gh run download 40298983391 --repo PPsyrius/iss-groupbuy-team8 --dir docs/quality/evidence/zap/before-2026-10-26/run-40298983391
```

The issue-list PNG was captured directly from the authenticated browser at `https://github.com/PPsyrius/iss-groupbuy-team8/issues?q=is%3Aissue%20%22%5BOWASP%20ZAP%5D%22%20in%3Atitle`, without modifying the page. The ZAP report PNG was captured with local Playwright Chromium at 1600 × 1100. [SHA256SUMS.txt](before-2026-10-26/SHA256SUMS.txt) fingerprints every other file in that snapshot directory; the local `.gitattributes` preserves those bytes across Windows checkouts. Checksums establish later file integrity, not independent authenticity.

## After remediation

After #610 is merged and deployed, run the same passive scan against the same target, preserve the results under a new `after-YYYY-MM-DD/` directory, and repeat the issue export and images. Record the scan run and deployed revision separately. Add `comparison.md` keyed by ZAP alert reference and issue number, with before/after severity and instance counts, disposition (fixed, accepted with reason, or pending), and evidence links. Explain changes to scanner rules or coverage rather than interpreting all count changes as application fixes. No post-fix run or remediation outcome is established by this snapshot.
