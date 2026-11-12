# Three-audit follow-up — 12 November 2026

This companion record addresses all three requested audits without altering immutable campaign originals, historical manifests, prep owner work or Git history. It is a working-tree follow-up, not a published/merged acceptance receipt. The selected V3 report dataset remains [R4 only](../20261111-v3-r4/REPORT-INTEGRATION.md).

## Report corrections (audit 1)

| Finding | Current draft correction |
| --- | --- |
| A1: remote financial/publication guarantees | §3.1 AD-02/04 and §3.2 persistence distinguish local state, refund obligation and outbox insertion from separate Stripe/SNS effects, retries and manual review. |
| A2: EventStorming chronology | §3.1.4 names the earlier 22 September ADR decisions and the simulated 25 September validation/service recommendation; the timeline caption distinguishes Java records from modeled facts. |
| A3: old V3 success/failure cross-references | §§1, 3.4 and 6 use R4 browse/scale-out results, not earlier V3 campaigns or payment/capacity claims. |
| A4: unbuilt batch-isolation guarantee | §3 corrects audit input to `Team*`, states no worker exists, and adds the online/consumer/lease-job/proposed-batch compute matrix with shared-RDS contention limits. |
| A5: inconsistent security claims | §3 qualifies app-role versus migration-owner credentials, alarm-topic encryption and persistent-secret rotation, referring to §4.3. |
| A6: names/routes | §3 uses `SessionStpInterface` and `GET /internal/users/{id}/active`, with released-source links. |
| A7: topology/zone views | Supplemental D5 views distinguish SWE5006 EC2, configured ECS monolith and distributed ECS, enabled/disabled messaging and dated zone placement; the adjacent gap review separates eligibility from observation. |
| A8: missing schema dictionary | Supporting D9 dictionary covers important fields/types/constraints for all 29 owned tables at released `3a893036`, with migration links, explicit exclusions and reduced-column exceptions. |
| Additional #772/#773/#774 findings | Corrected administrator/seller and UC12 terminology, named the recording's SWE5006 EC2 monolith, and replaced obsolete cost prose with the dated §6.3 record and its limits. D3's source/export are synchronized. |

These are content corrections, not automatic issue closure, second-owner review or evidence of new application/cloud behavior. The [fresh report receipt](../20261111-v3-r4/report-review.json) states the actual build scope and remaining warnings. Full Appendix A, remaining reviewer markers, publication/immutable pinning and outstanding operational acceptance are not silently passed.

## Non-destructive retention safeguards (audit 2)

[retention-plan.json](retention-plan.json) carries the exact original audit selection: **316 keep / 485 conditional external candidates / 10 preserve-owner-review**, plus 231 existing dependencies. These are the earlier 811-path snapshot counts, not a promise about the enlarged final diff. New report imports/dictionary/source-export pairs and this follow-up are additional protected dependencies, never deletion candidates.

The 485 candidates are **not approved removals**. Of these, 456 are hash-bound by 25 historical manifests, whose exact affected members are recorded. No archive was created, no destination/retrieval/retention owner was approved, and no file was omitted. The fallback is to keep everything. Before any later removal: approve the exact paths/destination; retain the complete original layout and a second verified durable copy; record retrieval and byte hashes; add companion qualifications for the 25 manifests; rerun current link/import/citation closure and obtain review. Temp storage alone is not a durable archive.

Keep the complete R4 package, fresh/October V2 receipts, every #775 historical source, the five functional k6 files and their tests, diagram sources/styles/imports and all ten prep receipts. Do not stage broad directories, blanket-revert k6 changes, rewrite an original manifest to conceal omissions, or infer removal permission from a low citation count. Later deletion would not shrink the already pushed commit/history.

`python docs/swe5001/evidence/20261112-audit-followup/check-retention.py` only checks presence/identity and the blocked state. It has no delete/archive option and is not a replacement for a fresh dependency audit after future changes.

### Companion locators for historical relative references

The cleanup audit's nine unresolved references are not nine deleted files. Two are intentional `NNNN-kebab-title.md` placeholders in the ADR skill/template. Six occur at line 29 of `20261111-v3-r3/checkpoint/mitigation-reassessment/REVIEW.md`, whose relative paths retain their original collection-layout base rather than their projected repository location. The seventh campaign reference is in `prelaunch/README-at-launch.md`. This companion table supplies current locations without editing those hash-bound originals; every target is already in the snapshot's keep set.

| Historical relative reference | Current retained location |
| --- | --- |
| `../rate-concurrency-review/warm-establishment/README.md` | [Warm-establishment follow-up](../20261111-v3-r3/followups/rate-concurrency-review/warm-establishment/README.md) |
| `../main-warm-check/receipt.json` | [Main warm check](../20261111-v3-r3/followups/main-warm-check/receipt.json) |
| `../windows-review/r3-connection-lifecycle/REVIEW.md` | [Connection-lifecycle follow-up](../20261111-v3-r3/followups/windows-review/r3-connection-lifecycle/REVIEW.md) |
| `../tcp-nat-review/README.md` | [TCP/NAT follow-up](../20261111-v3-r3/followups/tcp-nat-review/README.md) |
| `../runtime-compatibility-review/REVIEW.md` | [Runtime compatibility follow-up](../20261111-v3-r3/followups/runtime-compatibility-review/REVIEW.md) |
| `../windows-review/REVIEW.md` | [Original Windows diagnosis](../20261111-v3-r3/diagnosis/windows-review/REVIEW.md) |
| `prelaunch/retained-files.json` from the launch-root README | [Preserved prelaunch manifest](../20261111-v3-r3/prelaunch/retained-files.json) |

These locators repair navigation for the selected repository projection, not the relative paths inside the original files or completeness of a private archive. Dynamic/private references and remote retrieval remain outside this local link check.

## Privacy dispositions and safe viewing (audit 3)

[privacy-dispositions.json](privacy-dispositions.json) records locations/categories and original hashes without the flagged values. [safe-viewing-excerpts.json](safe-viewing-excerpts.json) supplies line-numbered viewing projections for private-target, local-lab-address and operator-home-path findings: complete sensitive lines are withheld. These excerpts are **not runnable scripts, parseable replacements for the original JSON/JSONL, complete receipts or historical manifest members**. Use them to avoid repeating metadata in review; verify measurements against the unchanged originals and their historical identities.

**Historical metadata exposure is still unresolved.** Originals are preserved because their immutable bytes and published history cannot honestly be repaired by new excerpts. Any current-tree redaction/removal, private archival destination or history remediation needs a separate owner decision and a versioned projection/manifest qualification. No credential was detected in the original audit or R4 package; this is not whole-history disclosure clearance or proof of absence. AWS account IDs/ARNs are reviewable metadata, not credentials. Explicit URL-userinfo test fixtures and documented subnet CIDRs are not automatically redacted or classified as leaks.

The [final selected secret scan](../20261111-v3-r4/secret-scan.json) identifies its exact source bytes; the final cleanup/privacy scan receipt is [final-scan.json](final-scan.json). A scanner's zero-credential result does not clear the known metadata categories. No credentials, private raw operator/Terraform payloads or private R4 stream were opened or republished for this follow-up.
