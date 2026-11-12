# AWS V3 r4 — measured outcome and verification

## Publication addendum — 12 November 2026

The original preparation-status paragraph below is historical. This package was subsequently committed and published at immutable [`0405b1f0cbfd5442cb3dfef084c69f785b272d82`](https://github.com/PPsyrius/iss-groupbuy-team8/tree/0405b1f0cbfd5442cb3dfef084c69f785b272d82/docs/swe5001/evidence/20261111-v3-r4), separately from `dev` integration; it is no longer working-tree-only. The later [#789 handoff](../20261112-issue-789/README.md) and [operator cleanup projection](../20261112-issue-789/cleanup.json) record subsequent clarification/retirement, not a changed R4 workload or canonical verdict. Original [README bytes](README.original-20261111.md) and [manifest bytes](SHA256SUMS.original-20261111) are preserved verbatim, also at that immutable commit. The current `SHA256SUMS` is explicitly a new manifest for this metadata-amended package, including those frozen copies, so the unchanged standalone checker still validates the exact current file set. Historical scan/review receipts retain their original byte scopes; the new #789 verification covers the amendment instead. All nine core data/verifier files and original scan/review receipts remain unchanged; no failed analyzer result or historical receipt is replaced by a manufactured pass.

## Report selection and branch-local package

The owner selected **R4 as the sole V3 results campaign for the report**. Earlier V3 campaigns remain historical investigation records; they are not blended into R4 totals, plots, latency statistics or scaling claims. Selecting the report's data source is separate from an acceptance verdict. The package is prepared in the working tree of `docs/swe5001-must-fix-evidence`; it is not claimed committed, pushed or available at a frozen remote URL.

| File | Intended use and boundary |
| --- | --- |
| [verification.json](verification.json) | Raw-recount aggregates and completion-minute bins, generator resources, scaling actions, provenance and exact original-tool outcomes; a derived verification projection, not an original collector receipt. |
| [browse-minutes.csv](browse-minutes.csv) | 30 completion-minute bins relative to Docker container start; browse counts, errors, p95/max and calculated completions/s. Not UTC CloudWatch buckets or exact offered arrivals. No per-minute drop count was retained here; do not create a zero-filled drop series. |
| [scaling-activities.csv](scaling-activities.csv) | Four Successful request-policy transitions, activity start/end and first sampled desired/RUNNING times, all UTC. RUNNING is not target-health/slow-start completion. |
| [service-observations.jsonl](service-observations.jsonl) | 264 source-preserving canonical-plus-supplemental projections, including pre/post-run rows and 200 in-run rows. This is a derived union, not a replacement canonical observer. |
| [cloudwatch-points.jsonl](cloudwatch-points.jsonl) | 793 retained source-specific minute points; overlapping captures and differing values are kept separately with private-source hash/JSON-pointer references. Never sum overlapping captures. |
| [cloudwatch-series.json](cloudwatch-series.json) | Series/statistic/unit/coverage metadata for both captures, including empty series. Complete status does not guarantee final metric publication. |
| [server-minutes.csv](server-minutes.csv) | 31 overlapping UTC minutes. Prefer supplemental values per metric/minute, falling back to canonical only when absent. Blank means unknown, never zero; partial edge minutes are flagged. Task counts are distinct sampled values, not interpolated continuous states. ALB seconds are converted to ms. |
| [source-projections.json](source-projections.json) | Hash/byte identities of the three copied existing projections; originals remain private and unchanged. |
| [results.py](results.py) | Standard-library-only CSV regeneration and portable consistency/hash checks. Does not contact AWS, run load or replace the original analyzer. |
| [secret-scan.json](secret-scan.json) | Selected-file Gitleaks result and byte-specific scope; not a repository-history scan or clearance of private originals. |
| [SHA256SUMS](SHA256SUMS) | Exact package file identities, excluding the manifest itself. |

Check the package with Python 3 in the approved local runtime: `python results.py` from this directory. `python results.py --write` regenerates only the three CSV files; it does not refresh the manifest or establish acceptance. The root `.gitattributes` marks only this package `-text` to preserve byte hashes across Windows checkouts; Git must not normalize these evidence bytes. Client completion-minute bins and UTC server minutes have different origins; do not join rows by index or treat a mean of minute percentiles as a whole-run percentile.

Report integration: §4.1 now uses R4-only distributed results, workload/scaling tables and a regenerated R4 timeline. Related status/milestones in §2, sizing/AISS-12 in §3, provenance in §5, demo pointers in §6 and the framework/register agree with that selection; V2 baselines remain separate. The old V3 evidence itself is preserved. See the [report framework](../../report/README.md) for ownership/reviewer and citation-freeze requirements; a committed evidence SHA is still needed before final PDF URLs can be pinned. Build and consistency checks are recorded in [REPORT-INTEGRATION.md](REPORT-INTEGRATION.md), not inferred from this source edit.

## Verified workload result

The owner-directed AWS workload ran from **22:22:40.555 to 22:52:41.886 SGT on 11 November 2026**. The original application workload remained unchanged: 1,000 preallocated/maximum VUs, the full 30-minute arrival schedule, 600-RPS peak, 70/30 list/detail selection, existing request timeouts and HTTP/error/drop thresholds. The generator moved from the laptop/Docker Desktop path to a dedicated T3a Unlimited EC2 host; this is a separate environment variant, not a server-code optimisation or a fix of the unresolved local path. Earlier [failed r3 evidence](../20261111-v3-r3/README.md) remains unchanged.

| Measurement | Independently recounted result |
| --- | ---: |
| Total HTTP requests | 726,000 |
| Setup / browse requests | 1 / 725,999 |
| Product-list / product-detail browse requests | 508,200 / 217,799 |
| HTTP status | All 726,000 were 200 |
| HTTP failures / browse errors | 0 / 0 |
| Body-predicate checks | All 725,999 passed |
| Dropped iterations | 0 in the explicit k6 summary counter; no positive raw drops |
| Browse wall latency, average / median | 9.431945 / 8 ms |
| Browse wall latency, p95 / p99 / maximum | 12 / 33 / 728 ms |
| Native k6 / monitor / streaming-upload exits | 0 / 0 / 0 |

The complete gzip stream passed decompression/CRC validation. An independent offline checker parsed and hashed **2,483,971,025 uncompressed bytes**, recounted every HTTP/error/check/browse-latency sample and reproduced the summary's latency statistics. The raw SHA-256 is `4542af4ad63b2a99a5c1406f9606cbfff0b0b3971e675572c81edf15b0da82f8`; the 136,493,550-byte compressed artifact hashes to `d94c3ff50da3e1cf2301e94d379bd4303268efbc46fe074af3d84341ad966947`. No original raw data or summary was rewritten. Expected browse arrivals integrate to 726,000; the measured 725,999 is within the original analyzer's one-arrival tolerance. All original all-run k6 thresholds report true: browse errors <1%, drops =0 and browse p95 <800 ms. This is distinct from the four-input analyzer outcome below.

The first recount checker incorrectly expected URL tags. The original workload deliberately exports only status, method, name, scenario and expected-response system tags. That checker failed after its count/error/drop/latency assertions had matched; the corrected checker used the original fixed request-name tags and retained the first checker, failure log and receipt. Protocol tags are also absent, so no full-run HTTP-version breakdown is claimed. The body predicate is the existing narrow success/page/detail-ID check, not validation of every business field or responses never received.

Thirty completion-minute bins retain the ramp and recovery. The ten central bins contain 360,000 browse completions, approximately 600/s, with per-minute browse p95 at most 13 ms and zero errors. These are completion-time bins relative to container start, not a substitute for the configured arrival schedule or exact phase-boundary arrival reconstruction. The worst all-run completion-minute p95 was 20 ms.

The preallocated/maximum contract and `vus_max` confirm **1,000 VUs**, but the active `vus` gauge peaked at **100**. This arrival-rate test does not demonstrate 1,000 simultaneous active requests or users. Lower latency naturally needs fewer active workers at the same arrival rate; neither this fact nor the AWS result identifies the faulty component on the earlier laptop path.

## Generator resources

Offline resource review found **883 records, including 880 actual running samples**, with strict UTC ordering and a maximum 2.064987-second gap. Samples bracket the complete 1,801.331413-second container execution; the copied monitor's 2,300-second limit did not truncate the run. All running samples confirm the eight-vCPU quota and eight-GiB memory limit.

- Container CPU averaged **6.109879% of eight vCPUs**, peaking at **12.721746%** over sampled intervals; the average is equivalent to 48.879034% of one CPU, not 48.9% of the whole host.
- Sampled container memory peaked at **795.734375 MiB**, or **9.713554%** of the eight-GiB limit. Recorded throttling and memory-event counters were zero; Docker reported no OOM.
- Host available memory stayed above **29.401180 GiB** and free disk above **33.535011 GiB**; no host swap was configured. Host steal averaged 0.577779% and peaked at 6.880190% over sampled intervals.
- TCP state samples peaked at 1,001 established sockets and one SYN_SENT. Retransmission counters and CPU-pressure observations were unavailable. The earlier sparse credit datapoint does not establish whole-run credit balances or final billing.

These observations support sampled generator headroom; they are not evidence about every backend pool, driver queue or resource between samples.

## Automatic scaling and backend observations

The retained activity histories and normalized events show four **Successful request-target-tracking actions**, growing desired tasks **1→2→3→4→6**. No count of five was observed. Times below are SGT; RUNNING is an aggregate ECS sample, not proof of target health or completed 120-second slow start.

| Desired transition | Activity started | First sampled RUNNING at new count |
| --- | --- | --- |
| 1→2 | 22:30:48.335 | 22:31:39.128 |
| 2→3 | 22:32:48.316 | 22:33:39.065 |
| 3→4 | 22:34:48.340 | 22:35:47.218 |
| 4→6 | 22:37:48.315 | 22:38:54.173 |

The first sampled six-task state was about 973.6 seconds after container start. This includes the offered-load ramp, metric publication, alarm evaluation and task startup; it is not isolated autoscaler reaction time. All 200 in-run service observations show ACTIVE/COMPLETED, with at most two pending tasks. Truncated service-event lists cannot exclude every transient failure or interruption.

Commerce minute-average CPU peaked at **54.7239% at 22:31**, above the 50% CPU-guard threshold; all four retained executed actions cite request tracking, not a proved CPU-policy action. RDS minute-average CPU peaked near **23.95%** and minute-maximum connections reached **40**. These service averages/counts do not expose per-task peaks or pool waits. The retained configuration remains commerce **1 vCPU/2 GiB**, RDS **db.t4g.small**, five-connection pools, request target **6,000/minute**, slow start **120 seconds**, and CPU guard **50%**; budgets remain **75/181 steady** and **130/181 replacement**. Released source remains `3a893036d70d028b29ae13ebf820283571c1ae00`; no application resize or runtime upgrade occurred.

Most CloudWatch series end at 22:51 and lack the partial 22:52 minute. Target-5XX has **no points**, not a verified zero. ALB values at 22:48 differ between the two captures despite Complete statuses; both originals are retained. Later values are preferred only in the derived comparison, never summed over overlapping captures or zero-filled. Minute ALB percentiles are not whole-run client percentiles. At 22:40–22:46 the retained request totals are approximately 36,000/minute and requests/target approximately 6,000/minute with six tasks: convergence under the offered workload, not saturated capacity.

## Operational close-out

The normal commerce floor **2–6** was explicitly restored and persistent artifact checks finished at **22:52:55 SGT**. Actual desired/running remained **6/6**, pending zero; restoring the minimum did not establish convergence to two. The temporary generator was independently read back **STOPPED at 22:54:15.876 SGT**, before the owner's 22:55 wrap-up deadline; it was not terminated, and encrypted storage/private evidence was retained. Retained disk and S3 artifacts continue to incur storage charges. The application's 23:00 safety schedule was not changed; its later execution was not live-verified.

**The outer owner-directed SSM command returned Failed, response 1**, during the final recursive S3 copy. Retained stderr includes a FIFO/special-file skip warning and copy subprocess exit 2. This is distinct from native k6/monitor/raw-stream exits 0/0/0 and independent Docker exit 0/no OOM. Six required artifacts were downloaded with matching sizes/hashes and their measurement contents verified; **full-directory upload completeness is not established**. Do not describe the entire SSM command as successful or relabel this as a failed k6 workload.

Raw data, full summaries/preflights, operator commands, environment identifiers, credentials and private endpoint material are excluded from this evidence directory. Downloading the persistent artifacts for this offline verification did not restart the generator or change application resources.

## Execution and interpretation limits

The reviewed prelaunch runner refused on its remaining-observer-window requirement. After the owner explicitly directed execution despite that refusal, a separate owner-directed wrapper invoked the unchanged k6 workload, retained the refusal and captured real generator resources/raw output. Therefore this result must not be described as an execution of the unmodified reviewed orchestration or a clean prelaunch-gate pass. The generator-only timer was moved again to 22:54 by that wrapper; the earlier 22:50 timer should not be presented as the final timer configuration. Source and command bytes are preserved privately.

The canonical observer completed its own 42-minute window with 169 samples, zero read errors and maximum gap 15.461205 seconds; its **last observation was 22:49:32.150**, and collection ended around 22:49:35, before workload completion. A separate supplement recorded 95 observations through 22:53:16 with zero errors and maximum gap 15.366203 seconds; its final AWS reads ended at 22:53:17. A source-preserving **derived union** contains 264 rows, including 200 in-run, and brackets the actual container run with maximum run-covering gap **15.257076 seconds**. Every projected row retains its original source, line and hash privately. This is supplementary coverage, **not a repaired canonical receipt**; neither original is rewritten. Wall-clock synchronization was not independently attested.

Canonical scaling history contains 54 activities without a pagination token: 52 Successful and two historical Overridden; **all four in-run actions are Successful**. Supplemental history contains 49 activities with a retained token and exactly matches canonical's first 49, including those same four actions. No additional page was fetched for the supplement; its independent history completeness is not claimed.

### Unchanged original tools

The original normalizer exited **0**, with empty stderr and no normalization blockers, producing the same four request-policy transitions. The unchanged four-input V3 analyzer against **canonical inputs** exited **1**, wrote zero stdout bytes and produced no automated result JSON. Its exact stderr was:

```text
Observations must bracket execution with strictly ordered gaps <=45s
```

The canonical sample gaps satisfy the 45-second bound, but the stream does not bracket execution end. Its original `collectionComplete=true` reports completion of its own timer, not full-run observation. The analyzer derives a measurement interval of 22:22:41.686–22:52:41.731 from the original summary, distinct from Docker's outer lifecycle timestamps; the canonical stream ends early for both intervals. Supplemental rows were **not substituted** into that invocation, and no second invocation or synthesized passing receipt was made. This exception does not emit an automated latency-and-scale-out verdict. Native k6 success must not be presented as canonical analyzer success.

The owner decides whether to accept the result; **no owner verdict has been recorded**. This page reports measured outcomes and limitations, not an automated acceptance decision. Distributed one-to-four-task throughput linearity, the broader NFR-15 workload and repeatable recovery remain separate obligations; the 600-RPS offered ceiling does not locate a saturation knee or establish those claims.

## Evidence lineage

Measurements were generated by the pinned `grafana/k6:1.6.1` workload, retained privately on EC2 and in encrypted S3, retrieved through authenticated read-only calls, then independently recounted with network-disabled local Docker. All metrics, context and `finishedAt` exactly match a separately retained EC2 readback. Source inspection found the original k6 `handleSummary` writes the report, not a wrapper manufacturing it. Workload source hashes match the frozen runner copies: `browse.js` `68b3d6624802a145b211521e3a8b44a9da0af230ec1b0b6101225488134186f4`, `lib.mjs` `e58d67966f8ed2945e44d60931a495dddf24a926551340fef10341129995d267`, and `scaling.mjs` `2dde635653a10c0f6c56b3319b93a9c7836f1658ce7d2c22ed67c26c7d02209d`. Tooling is base `b911ea63` plus disclosed content-hashed policy-binding/orchestration changes, not unchanged base bytes. Full created-container HostConfig was not retained; the resource samples independently establish actual cgroup limits instead.

The [selected verification projection](verification.json), identified by [SHA256SUMS](SHA256SUMS), records its private source hashes and preserves workload, resource, scaling, provenance and original-tool outcomes without publishing raw inputs. Its allowlisted export checked seven source files unchanged and found no configured private-path/address/endpoint/account/access-key patterns. The later [selected-file Gitleaks scan](secret-scan.json) found zero secrets in its stated byte-specific scope; neither check is a full repository/history privacy clearance. Hashes identify bytes, not external attestation; it is a derived analysis record, not an original collector receipt or owner acceptance. Offline review preserved original inputs. No rerun, AWS resource change, Git staging, commit, push or issue closure was performed during this analysis.
