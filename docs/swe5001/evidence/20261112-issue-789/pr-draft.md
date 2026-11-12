# PR draft

**Title:** `docs(swe5001): consolidate evidence, audits and #789 corrections`

**Base:** `dev` · **Head:** `docs/swe5001-must-fix-evidence`

## What

Combine evidence restoration, three-audit report reconciliation and #789's nine original items plus ten review points, including pre-PR cleanup and the latest V2 baseline selection.

- Evidence/tooling: retain campaign provenance and diagnostics; bind k6's reviewed scaling-policy target independently of workload, with regression tests and unchanged acceptance thresholds.
- Report: select the latest 11 November V2 baseline and R4-only V3 results, retaining earlier runs as history; correct architecture/security/delivery/cost claims, topology views, workload matrix and the 29-table typed dictionary.
- Preservation: keep original R4 data/receipts and frozen metadata, with a dated publication addendum; add privacy projections and non-destructive retention safeguards.
- Close-out: record operator runtime/generator retirement separately from failed nightly cleanup; retain private chargeable archives and one encrypted disk snapshot.
- Peer handoff: provide the exact-base 23-file correction patch against `02c7b822`, preserving the editor's structure/style.

## Issue

Closes #789.

Refs #462, #591, #592, #589 and #775.

## Decisions

- R4 measured the tested 600-RPS peak, not maximum capacity, controlled linearity or a passing canonical coverage verdict.
- Historical metadata exposure and the 485 conditional archival candidates require separate decisions; no history rewrite or candidate deletion.
- No application API/schema change, GitHub-role expansion or foundation/edge/SWE5006 teardown.

## Evidence

- Recorded verification: 56 source/data/patch checks; whitespace-strict exact-base application; 141-page styled peer and 83-page combined builds; zero detected overflows/relative PDF URLs; UML labels ≥8 pt.
- Recorded scan/link scope: 324 local links, 75 repository files and two PDF text exports; zero secret findings. Historical receipts are byte-specific, not whole-history privacy clearance.
- Contract unchanged; UI not applicable. Sprint logs and NFR register updated.
- [Verification and closure map](https://github.com/PPsyrius/iss-groupbuy-team8/tree/docs/swe5001-must-fix-evidence/docs/swe5001/evidence/20261112-issue-789).
- [Pre-PR review](https://github.com/PPsyrius/iss-groupbuy-team8/blob/docs/swe5001-must-fix-evidence/docs/swe5001/pre-pr-review-20261112.md) records fresh checks after documentation cleanup.

## Before merge

- [ ] Reconcile remaining #789 corrections with the newer peer work (last readback `2f9c2eb2`), then rebuild/review. The retained patch/build target earlier `02c7b822`.
- [ ] Check evidence destinations and pin new citations before submission.
- [ ] Reviewer accepts the closure map and applicable CI passes.

Full Appendix A acceptance and remaining AISS security remediation are outside this PR.
