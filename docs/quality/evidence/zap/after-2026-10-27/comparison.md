# Passive ZAP before/after comparison — 2026-10-27

Refs #610, #636–#647 and #694; NFR-16/NFR-71. [Before run 40298983391](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/40298983391) scanned dev on 26 October; [after run 40459212995](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/40459212995) scanned the same origin on 27 October after the owner's [image repair and Terraform policy apply](../poc-image-repair-2026-10-27/README.md). The owner clarified GitHub CLI execution authorization; the agent dispatched the passive scan but executed no AWS command. No issues were closed or commented on externally.

## Completion and comparability

Both scans completed 10/10 identical required application probes and 67/67 enabled passive rules with the queue drained. Both reports identify ZAP 2.17.0. The complete passive-start rule identity/name/enabled/threshold configurations match after sorting by rule ID. The workflow and baseline script (including its generated passive-statistics code) have no Git diff between before source `9135ad8bdca9a5084b4ea2d11d619e13b8ba9f1d` and after source `2f23d9236a0870156e2ee46af76877e4cc5386ee`; no scanner scope or rule weakening explains the removed alert types. The policy archive was tied to the merged SHA, but the immutable deployed frontend artifact digest was not independently established.

| Severity | Before alert types | After alert types |
|---|---:|---:|
| High | 0 | 0 |
| Medium | 2 | 0 |
| Low | 4 | 0 |
| Informational | 6 | 6 |

Instance counts below are the JSON reports' `count` values, not the number of distinct vulnerabilities or issues; the HTML/Markdown report labels some cache observations systemic. All twelve finding issues remained OPEN in the initial after-scan capture. Subsequently, the owner explicitly directed closure of the six no-longer-detected header/CSP findings with short evidence comments; [the real post-closure state](post-closure/README.md) shows #636–#641 CLOSED and #642–#647 OPEN. Unperformed functional acceptance remains in tracker #610 and was not marked passed. "Not detected" is bounded anonymous passive remediation evidence, not universal security assurance or automatic proof of every acceptance criterion.

| Issue | Alert reference | Before severity / instances | After instances | Disposition |
|---|---|---|---:|---|
| #636 | `10055-4` | Medium / 2 | 0 | Closed per owner direction: deployed image-CSP finding remediated; PoC image substitution acknowledged |
| #637 | `10055-6` | Medium / 2 | 0 | Closed per owner direction: deployed inline-style finding remediated; remaining functional acceptance in #610 |
| #638 | `90004-2` | Low / 2 | 0 | Closed per owner direction: deployed COEP finding remediated; progressive protection and integration limits retained |
| #639 | `90004-3` | Low / 2 | 0 | Closed per owner direction: deployed COOP finding remediated; real provider-return acceptance retained in #610 |
| #640 | `90004-1` | Low / 5 | 0 | Closed per owner direction: deployed CORP finding remediated; not a replacement for authentication/CORS |
| #641 | `10063-1` | Low / 5 | 0 | Closed per owner direction: deployed Permissions-Policy finding remediated; remaining feature checks in #610 |
| #642 | `10109` | Informational / 2 | 2 | Still observed; JS-aware/authenticated crawl review remains separate |
| #643 | `10049-1` | Informational / 2 | 2 | Still observed; non-storable robots/sitemap response review pending |
| #644 | `10015` | Informational / 3 | 3 | Still observed; cache-directive review pending |
| #645 | `10050-1` | Informational / 5 | 5 | Still observed; shared-cache safety review pending |
| #646 | `10049-3` | Informational / 5 | 5 | Still observed; versioned/unversioned cacheable-asset review pending |
| #647 | `10049-2` | Informational / 2 | 2 | Still observed; SPA HTML revalidation review pending |

## Preserved evidence

- Before: [report JSON](../before-2026-10-26/run-40298983391/zap-baseline/report.json) and [coverage](../before-2026-10-26/run-40298983391/zap-baseline/coverage.md); frozen files are unchanged.
- After: [report HTML](run-40459212995/zap-baseline/report.html), [JSON](run-40459212995/zap-baseline/report.json), [coverage summary](run-40459212995/zap-baseline/coverage.md), [coverage JSON](run-40459212995/zap-baseline/coverage.json) and [run/artifact metadata](run-40459212995.json). All ten downloaded artifact files are retained unmodified, including start/finish passive snapshots, sanitized request inventory and ZAP plan.
- Report JSON SHA-256: `fa1c59bd9e0b514b76620ed74989384975355f3e08ebf600dac26933e464b6f5`; coverage JSON SHA-256: `97ad8f2bb3b79cbaaa0a3f5e84ff7ae84063e947106a7e0fadc2608dc2c9d7e0`. [SHA256SUMS.txt](SHA256SUMS.txt) fingerprints the other files in this after directory; local attributes preserve bytes across Windows checkouts. Hashes establish integrity, not independent attestation.
- Hosted anonymous Chromium [browser verification](../poc-image-repair-2026-10-27/browser-verification.json) additionally passed exact headers, 15 catalog/four detail images, stylesheet/isolation assertions and an unapproved-image pre-request block; it complements the scanner's non-browser coverage.
- [Actual authenticated GitHub issue-list screenshot](issue-list-open.png), captured after deployment/scan but BEFORE issue closures: 14 search results comprise twelve open findings, open report-evidence issue #694 and closed scanner issue #606. This is a full-page browser capture, not a locally rendered substitute. [Detailed UTF-8 issue export](issues.json) contains 15 unique records (the title search plus #610/#694); [open repository summaries](open-issues.json) contain 57 records, not 57 security findings. [Capture metadata](capture-metadata.json) records separate browser/export times and methods; this is not an atomic snapshot.
- [ZAP summary screenshot](scan-summary.png) renders the unmodified downloaded after HTML report at 1600 × 1100 in local Docker Chromium with external requests blocked, matching the before capture method. [Closure review](closure-review.md) keeps issue-specific acceptance separate from scanner absence. Individually satisfied issues may be closed before a final state-labeled capture within this same PR; a prior evidence-PR merge or second PR is not inherently required.
- [Final post-closure screenshot/exports](post-closure/README.md) preserve the later owner-authorized disposition and short evidence comments, without replacing the intermediate open-state capture or requiring another PR. Six findings are closed at the documented remediation scope; six Informational issues and tracker #610 remain open.

## Limits and remaining acceptance

The after coverage inventory still records seven unfetched discovered nodes; all ten required probes nevertheless passed. Reports retain two logged ZAP warnings; successful coverage verification, passive execution and the drained queue establish the documented scan completion, not exhaustive endpoint coverage or a warning-free scanner. No active attack, authenticated crawl, payment, physical-iOS test, private-history browser inspection or real OAuth/Stripe integration was performed by this scan. The six Informational alerts remain triage items, not fixes attributable to the isolation headers. The PoC's generic replacement pictures do not preserve purchase-time image identity.

A targeted scan of the downloaded text artifacts for origin-secret/header credentials, Set-Cookie, bearer-token headers, AWS access-key patterns and private-key markers found no matches; this is not a comprehensive secret audit. No raw HAR, Terraform state, saved plan, database rows or image-field backup is included. The intermediate after-scan screenshot/export records all findings still open; the separate post-closure subdirectory records the later six closures. No historical before or intermediate screenshot/export is relabeled or overwritten.
