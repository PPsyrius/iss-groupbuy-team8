# Issue #789 — historical closure checklist and report follow-up

12 November publication update: PR #795 merged at `27530a4dac0b2f574ee5bef6a80c5b5bbdffeca6`, closing #789. The checklist and exact-base receipts below describe that earlier handoff, not current publication status. The newer report follow-up targets PR #801's branch; it needs its own source/PDF verification. The historical checker accepts `--report-root <original-combined-report>` and compares frozen metadata against the reachable #795 merge, without requiring an orphaned object.

The PR answers [#789](https://github.com/PPsyrius/iss-groupbuy-team8/issues/789) and [Liu Jin's ten-point review](https://github.com/PPsyrius/iss-groupbuy-team8/issues/789#issuecomment-6813180582). The [140-page styled PDF](../20261112-peer-style-pdf-review/README.md) is the preserved before artifact; the separate corrected build is recorded below. Use `Closes #789` in the PR body; integration and acceptance are pre-merge checks.

## Original questions and requests

12 November owner-confirmation addendum: Pan explicitly agreed to **8 Phase D person-days and 19.5 total** ([record](effort-estimate.md#12-november-2026--owner-confirmation)). Item 7 below describes the earlier handoff before that confirmation; the figures remain estimates, and other members' confirmation status is unchanged.

| Item | PR deliverable / evidence | Closure boundary |
| --- | --- | --- |
| 1. Runtime and generator | [cleanup.json](cleanup.json), §§2/5.3/6 and reply: runtime removed by 12:13:20 SGT, generator retirement by 12:06:02, 51 checks at 12:14:42 on 12 November. | The failed nightly job remains failed. Private S3 archives and one encrypted disk snapshot remain chargeable; no archive disposal or permanent-stack destruction is implied. |
| 2. Scale-in and Spot | §4.1/AISS account and reply cite Lim's reported ECS readback, including timestamps and post-load scope. | Attributed observation, not a newly retained event export, controlled drill or canonical coverage repair. |
| 3. Refused launch | §4.1/5.3 and reply distinguish hard refusal, separate owner-directed wrapper and unchanged workload. | No warning-only, bypass-flag or unchanged-launcher-pass claim. |
| 4. Same clients | Owner-confirmed same laptop/Docker Desktop for 10 November/r1/r2/r3; AWS for r4. | Identical network attachments and a common cause of every earlier failure are not established. |
| 5. Gate provenance | September plan, Liu Jin, PR #259 and Pan's later tooling; metrics opener/table. | Documented gate purpose is stated; an unrecorded individual rationale is not invented. |
| 6. Submission tag | Owner-confirmed snapshot/date; retained Trivy-step failure and skipped publication/deployment. | Signature presence is not verified signer trust; no invented CVE identifiers. |
| 7. Effort | [effort-estimate.md](effort-estimate.md), §2.3 and reply: 48 assumed hours / 6 = 8 additional pd, 19.5 total, with sensitivity. | Reconstructed estimate, not logged hours or an owner-confirmed historical team agreement. |
| 8. Owners | Explicit AISS-19/20 acceptance and AISS-07 design/verification split. | Responsibility accepted, not security gaps closed. |
| 9. Review | Exact before-PDF review and corrected styled-peer source/PDF verification in [verification.json](verification.json). | Review is bounded to #789, not whole-report F1–F12/Appendix or submission approval. |
| Evidence PR / reply | [PR draft](pr-draft.md), [reply draft](reply-draft.md), immutable evidence pins and exact-base [peer patch](peer-report.patch). | Branch commits exist; posting the PR/reply and merging remain pending. |

## Ten additional review items

| Review point | Change carried by our branch / peer handoff |
| --- | --- |
| 1. Wrong Figure 4.1 | R4-only Python/SVG/Typst inputs; the peer verification build uses the actual R4 chart rather than only changing its caption. |
| 2. Publication and stale README | Existing remote evidence is pinned to its verified immutable revision. A dated R4 README publication addendum supersedes historical preparation status. Original README/manifest bytes are retained verbatim; a clearly new current manifest keeps the unchanged exact-file-set checker usable, without rewriting old scan/review receipts or core data. The upcoming PR publishes the later handoff; final citation pinning follows publication. |
| 3. Metrics at opening | Answer-first sentence explicitly names offered/completed rate, client p95, errors and drops; server metrics remain diagnostics. |
| 4. Complete metric table | Explicit `HTTPCode_Target_5XX_Count` and `scaleOutObserved` rows; missing points remain unknown and observed scaling is distinct from controlled linearity/canonical acceptance. |
| 5. 12,000 attribution | Environment 12,000 versus committed fallback 200 versus session 6,000 are separate; capacity arithmetic remains calculated. |
| 6. Historical capacity / RDS | Clearly dated 19 October failed-run diagnostics and aligned writing harness; no blending into R4. RDS is a potential constraint, not an observed next bottleneck. |
| 7. Image assurance | Later release-digest reuse has no new image scan; weekly filesystem/config scans are not image scans; no signing/provenance attestation is claimed. |
| 8. UML | Decision after join, conditional workflow branches, explicit image-scan failure/activity-final paths and early deployment failure routes to cleanup attempts. Editable peer Mermaid and generated Typst are checked together; shared SWE5006 sources are unchanged. |
| 9. Repetition | Sizing/metrics/collection limits live in §4.1; security and operations cross-reference it. The historical SQL/experiment record owns detailed connection arithmetic and task revisions. |
| 10. Identifiers / wrapping | Unnecessary account/SCP literals removed from peer narrative. Region token is kept intact; the original reported wrap did not reproduce in the supplied style PDF. |

## Before merge

The last peer readback is `2f9c2eb2`, following #794 at `eb376a8f`, ahead of patch base `02c7b822`. Preserve those overlapping fixes when reconciling remaining #789 changes; the older build does not verify the newer result.

Read-only source regression command: `python docs/swe5001/evidence/20261112-issue-789/check_closure.py --repo .`; add `--peer-root <corrected-peer-checkout>` to verify the exact after-file hashes. The checkout needs the referenced `0405b1f0` Git object for the frozen-metadata comparison; a shallow clone without it is an unavailable precondition, not proof of changed evidence. PDF/URI/label-size checks and the exact-base patch-application round trip are separate checks in the verification receipt. The unchanged standalone R4 command remains `python docs/swe5001/evidence/20261111-v3-r4/results.py`.

- [ ] Include these answers/evidence in the PR or issue reply and record the reviewed head.
- [ ] Integrate the peer corrections without replacing structure/style; revalidate an advanced peer base.
- [ ] Check the rebuilt merge result: R4 figure/corrections and evidence destinations; pin new citations before submission.
- [ ] Obtain reviewer acceptance of the original answers and ten review dispositions; pass applicable CI.

The exact-base build verifies the handoff; reviewers check its integration before merging. `Closes #789` closes the issue when the PR merges into `dev`, the default branch.
