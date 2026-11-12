# SWE5001 pre-PR review — 12 November 2026

Scope: `d521e57a`, `1007f6b7`, `af2ec5b2` and the documentation cleanup in this working tree. This supplements the [earlier byte-specific verification](evidence/20261112-issue-789/verification.json); it does not overwrite its receipts or claim a fresh report build.

## Findings

| Finding | Disposition |
| --- | --- |
| PR draft covered only the last commit and delayed the closing keyword. | Covers evidence/tooling, audit/report and #789 work; uses `Closes #789`. GitHub default branch is `dev`; acceptance stays in the pre-merge checklist. |
| Current handoff said the committed branch was uncommitted/local-only. | README/reply/checklist now distinguish branch commits, historical receipts and the still-unopened PR. |
| NFR-64 and daily logs repeated detailed run diaries. | Condensed NFR-64/70, updated teardown NFR-52 and shortened this branch's 10–12 November log entries. Canonical evidence links retain detail; other authors/PRs are unchanged. |
| Windows checkout converted hash-bound LF evidence and the generated R4 SVG to CRLF. | Restored only hash-proven Git originals; froze the three affected packages with `-text` and pinned the SVG to LF. No data/content changes or manifest rewrites. The unchanged retention and chart checkers now pass. |
| Broader default-rule secret scan flagged a checksum in the old verification receipt. | Verified the exact value against the referenced file's SHA-256. One false positive, no credential detected; no scanner exclusion or historical receipt edit added. |
| Peer advanced from handoff base `02c7b822` to #794 at `eb376a8f`, then `2f9c2eb2`. | **Before merge:** reconcile residual #789 changes with the newer peer fixes and rebuild/review. Direct patch application to `eb376a8f` failed; a private three-way check found nine conflicted source files. Preserve the newer diagrams, object view and prose. |

## Verification

Fresh commands/results and exact scan inputs are recorded in [pre-pr-checks-20261112.json](pre-pr-checks-20261112.json). Focused k6 tests passed 53/53; closure/source checks passed 56/56 against their recorded base. The checks also cover retention integrity, documentation links/lengths and a directory secret scan of the three-commit file set plus current edits. No AWS request, load experiment, application/API change, PR creation, commit or push is part of this review.

The initial k6 container lacked writable `/tmp`; rerunning with tmpfs passed without code changes. The initial retention failure was checkout newline conversion; original hashes were retained and the checker was not weakened.

## Remaining boundaries

- Peer integration/build and applicable PR CI are not complete; `Closes #789` is correct in the draft now, with those checks required before merge.
- [Historical metadata exposure](evidence/20261112-audit-followup/README.md#privacy-dispositions-and-safe-viewing-audit-3) remains a separate owner decision. A zero-secret scan does not clear it.
- R4 canonical coverage, controlled linearity, broader NFR-15 and whole-report/Appendix acceptance retain their recorded limits. Private retained archives still incur storage charges.
