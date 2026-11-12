# R4-only V3 report integration

The report now uses **V3 R4 instead of the earlier V3 results**, not merely an evidence pointer. Earlier campaign records remain unchanged outside the report. V2 monolith baselines remain separate and are not converted into a controlled R4 capacity comparison.

| Report area | Replacement |
| --- | --- |
| §2 project status and milestone | R4 measured workload/scale-out success; canonical collection and controlled-linearity limits stated separately. |
| §3.2 technology/sizing and §3.4 AISS-12 | Actual R4 commerce/RDS sizing distinguished from defaults; no obsolete one-task saturation or three-task throughput result. |
| §4.1 Performance | R4-only workload, results and automatic-scaling tables; raw verification, resource headroom, configuration and evidence boundaries. |
| §4.1 timeline | SVG regenerated exclusively from the R4 package; exact task samples and separate client/UTC-server time origins, with caption kept with the plot. |
| §5.3 operations | Released source versus session tooling, owner-directed launch, native success versus outer-upload failure, restoration and generator stop. |
| §6 demonstration pointers | R4 replaces the old V3 demonstration result. |
| Framework, writing harness and AISS register | Aligned with the R4 selection and remaining maximum-capacity/linearity obligations. |

## Verification

- `python results.py`: portable evidence/CSV/hash consistency; this is not a rerun of k6 or the original canonical analyzer.
- `python docs/swe5001/report/diagrams/performance-v3-timeline.py --check`: exact SVG regeneration from 30 R4 browse bins, 200 in-run task observations, 29 complete CPU minutes and four scaling actions.
- Offline Typst compilation of **chapters 1–6**, with existing fonts/packages and no network. The [PDF/source review receipt](report-review.json) records the final page count, PDF hash and result locations; the generated PDF is a local review artefact under `report/build/`, not committed evidence or a submission-ready full report.
- PDF text checks find the R4 counts, scaling timestamps, tool outcome and operational provenance, and exclude the old V3 result numbers. PDF links contain no relative URI destinations; this is not remote-link availability verification. R4 result pages have no page-boundary text overflow and were visually checked.
- The V2 baseline text and availability section remain unchanged. The only change to the security tail in the #775 follow-up is pinning its historical subnet-evidence URL to the already published commit; the protection check records that explicit exception rather than claiming byte identity. The two cache/query source files cited at released source `3a893036` have no diff from the inspected working-tree files.
- Selected-file secret scanning and exact package hashes are recorded in [secret-scan.json](secret-scan.json) and [SHA256SUMS](SHA256SUMS). These are working-tree checks, not a history-wide disclosure clearance.

## #775 point-by-point draft check — 12 November 2026

The owner personally closed #775 after accepting the already published branch evidence. Its historical records are pinned to `a05c33fd2b3646ed5aa9a383ee5a0b73bd3eb137` in the current draft; this does not claim they have landed on `dev`. The follow-up checks the report text, not just the presence of evidence files.

| #775 point | Where the current draft addresses it |
| --- | --- |
| Dated subnet/connectivity evidence and citations in §§1, 2.2 and 6.1 | §1.1 status paragraph, §2.2 data-subnet row and §6.1 F6 row cite the same published record; §3.2/AD-15/AISS-07 and §4.3 retain its scoped placement/connectivity result. |
| One-off reporting task, manual pool/scaling changes and their disposition | §5.3 identifies the read-only SQL report, distinguishes recording time from unverified execution time, records manual task revisions/pool cap and minimum restoration, and says scheduled destruction ended the experiment—not a live pool-ten rollback or settled two-task recovery. |
| Pool size and normal minimum decision | §5.3 separates the ended experiment from PR #784's durable distributed-only pool five and the committed minimum-two/default-one-baseline option, citing released Terraform source. R4's other overrides are not mislabeled as committed defaults. |
| 9 November edge fix | §5.3 identifies the operator's direct API update, ETag/readback, absence of a new GitHub-role grant and the retained-account/CloudTrail-access evidence boundary; §6.4 points to the same record. |
| D10 and no remaining diagram slot in §5 | §5.2 imports the editable D10 source and renders both UML activity views, with captions for image validation and deployment dependencies; no `#diagram-slot` remains in §5. |

The fresh chapters-only build and PDF/source review receipt cover this wording and the later [three-audit corrections](../20261112-audit-followup/README.md). Full Appendix A compilation, second-owner review and final source/evidence publication remain separate obligations.

## Remaining submission work

The full report with unchanged Appendix A was **not rebuilt in this replacement**; the earlier full-build eight-GiB OOM is not claimed fixed by a chapters-only compile. The fresh receipt records remaining font/layout warnings rather than carrying an earlier PDF's warning status forward. Second-owner review, draft/reviewer markers, final evidence publication/full-commit citation pinning and final whole-document verification remain outstanding; submission readiness is not the goal of this peer-review checkpoint. No staging, commit, push, issue closure, load rerun or AWS change was performed. R4's native measurement success does not alter the original canonical analyzer exit, outer upload failure or the owner's acceptance authority.
