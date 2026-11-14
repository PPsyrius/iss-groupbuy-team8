# V4 R5 — verified pass and raw evidence

**PASS for the stated experimental producer configuration**, cohort `v4-20261114-r5-a`, not an official release promotion or whole-campaign pass. This publication reviews the original run; it did not execute another workload, seed, analyzer or runtime mutation. The earlier R4 remains failed, unseeded setup, with all its original failures and deadlines retained.

## Original execution

| Evidence | Actual result |
| --- | --- |
| Seed | 80,000 teams and memberships; seeded 13 November 21:00:26.779536Z, expired 21:01:26.779536Z. |
| Original collector / Node analyzer | Exit **0 / 0**, `measurementPassed=true`, `passed=true`, no qualifications. |
| Complete source/effects | **80,000 each** teams, expired teams, outbox events, published events, notifications and consumed-event markers; **zero failed events**. |
| Automatic four | Unchanged original target-50 policy activity started 21:06:32.011Z and succeeded 21:07:40.473Z; first post-expiry four-task observation 21:07:34.475840Z. No forced notification count. |
| Observations | 126 original observations, maximum gap 15.624351 seconds; quiet start, backlog, four-task growth, sampled drain-after-scale and empty DLQ passed. |
| Full source proof before restoration | First complete report observed 21:10:44.596713Z; final report 21:12:08.021302Z; restore claim 21:12:40.425741Z. |
| Independent restoration | Original50 homogeneous healthy commerce two and original flags verified 21:20:32.362518Z. Final image-write proof age 26.101950 seconds with successful STOPPED SQL. |

The native commerce digest is `sha256:ed49c1bff23d55fbb974761c671ebba34263ef85f651c9402110f04350a07e2c`, reviewed uncommitted backend overlay `88770039451cf05593e76a2a174fba503178312bd253dc76c400d28673150486`. Existing revision 51 was reused; no R5 image rebuild or registration occurred. The actual frozen Node analyzer source SHA-256 is `ede9ba3c0d387e83bdb6280661ba50d07407b9a5a3fe8e93439eedfa6f4b34ae`; its output's `8bec6bbe` tooling SHA identifies the signed base only, not the effective uncommitted overlay. Original task/digest responses, source-freeze/build receipts, named environment/secret normalization and the collector's source bindings were checked without rerunning Node.

## Real historical CloudWatch capture

The later selected-profile AWS read captured **13 November 20:58:00Z through 21:22:00Z, end exclusive**, with 60-second statistics. All 20 requested series returned data and Complete status; six existing dashboard PNGs are retained privately. The real original-policy backlog expression is minute-Average visible backlog divided by minute-Average running tasks, with the original positive-task fallback, not an instantaneous per-message ratio.

| Metric | Retained measurement |
| --- | --- |
| Maximum visible backlog / maximum minute-Average backlog per task | 32,936 / 32,936. |
| Maximum oldest-message age | **153 seconds**, from 24 actual returned age samples; no fabricated zero or missing-sample fill. |
| SNS published | 80,000 across five returned minute buckets; absent buckets remain absent. |
| SQS receives / deletes | 80,000 / 80,000 in the requested window. |
| Notification / audit DLQ sampled maxima | Zero / zero, from returned data, not assumed emptiness. |
| Audit visible messages | 62,000–142,000 retained; no purge or consumer was introduced. |
| RDS connections | Observed maximum 56 against the measured database maximum 79; not a guarantee about unobserved instants or other workloads. |

The original analyzer's `queueAgeCoverageComplete=false` remains a diagnostic, not part of its passing conjunction. Its **472.693 seconds** identifies the beginning of the final sampled queue-quiet suffix, not proven end-to-end pipeline completion. Separate complete source/effect proof and final quiet observations remain necessary. Minute-Average task series can be fractional and do not replace the original integer task observations.

## Reviewable artifacts and privacy

- [Pass/source binding](v4-r5-pass.json), original [Node output](analysis.json), [collector result](collection-result.json), [database report](database-report.json) and [final acceptance receipt](acceptance.json).
- [Sanitized observations](observations.sanitized.jsonl): exact original timestamps, counts and null ages retained; private task/resource identifiers replaced by aliases. The originals and alias map remain private.
- [Historical metric definition/coverage](historical-cloudwatch-summary.json), [minute points](cloudwatch-2058-2122-points.jsonl) and [sanitized original-policy activity](notification-scaling-activities.sanitized.json); private dimensions, alarm causes, URLs and ARNs are omitted, not replaced with fabricated measurements.
- [Private package receipt](private-package.json) and [publication hashes](publication-manifest.json): the selected closed V4/V5/V7 R1 package was uploaded once to the previously approved private encrypted storage, downloaded and checked byte-for-byte and file-by-file. Raw structured AWS telemetry, task/policy/source receipts and PNGs stay private.

The selected archive SHA-256 is **`2f2be4d09d30c69a9dd8343f11020b1e696cbaf418d53db54e023cf3500e5f45`**, **8,977,504 bytes**, **109 evidence files readback-verified**. S3 AES256 was verified in both upload and download responses; local TAR copies are private but are not claimed to be encrypted. This is a new selected archive, not a replacement for the earlier comprehensive local encrypted R5 snapshot. Credentials/cookies, checkout URLs, payment/identity provider bodies, generic CLI wrappers, Terraform plans/state/private variables and active V7 R2 controls are excluded. The V7 generic cleanup response was excluded because it may contain message bodies; its immutable source hash and separate deletion/reset/failure receipts remain retained.

Final runtime readback belongs to **05:23 SGT**, not this later packaging task: it showed healthy **2/1/4**, notification automatic within unchanged 1..4 bounds, original definitions/settings, HTTP 200/UP and no SQL. This publication does not claim a new runtime check, exact 2/1/1 state, current teardown, new V7 R2 result, whole-campaign acceptance, Git commit or push.
