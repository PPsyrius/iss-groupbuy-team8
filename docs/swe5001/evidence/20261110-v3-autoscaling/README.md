# V3 distributed HTTP load — 10 November 2026 SGT

Refs #458/#591, NFR-64. **HTTP acceptance failed; no repeat was started.** This single fresh run does not replace the retained October results, establish distributed capacity or prove one-to-four linearity. AWS metrics/scaling history were recovered after the observer stopped; the final ECS observation interval remains missing. The two-task minimum is restored, not a verified settled two-task/AZ placement.

[Investigation handoff](INVESTIGATION.md): overload preceded observer loss; an unhealthy-task replacement is documented; the CloudShell collector design conflicts with documented idle-session limits. A tested, read-only follow-up is prepared for the operator's return, not executed or treated as authorization for a retry.

## Run and workload

- Run `20261110-v3-194543`, 9 November 2026 19:45:44–20:15:47 UTC (10 November 03:45:44–04:15:47 SGT), account `402178232802`, `ap-southeast-1`, SWE5001 CloudFront origin. [Original summary](summary.json), [generator context](generator-context.json), [preflight and pool budget](../20261110-load-preflight/README.md).
- Unchanged committed driver and released images: source `93984f615df79666f4c3ba62f4d6ae90fbf1f4e1`; distributed commerce revision 32, reviewed digest retained in the summary. The fresh route/digest/scaling preflight passed at a stable one-task start. Collector/orchestration files are separately identified uncommitted helpers, not attributed to that commit.
- Existing 200 requests/s candidate (12,000 requests/target/minute), not a fresh monolith V2: 40→200 requests/s over five minutes, →600 over ten minutes, hold ten minutes, →zero over five minutes; 70% catalogue list / 30% product detail GETs, existing demo seed, ten-second timeout. No request/gate/schedule changes or concurrent deployment were requested.
- Local Docker generator: eight logical CPU limit, 8 GiB memory limit, 1,000 preallocated/maximum VUs, pinned k6 1.6.1 digest. Original exit **99**, no OOM, normal completed container; wrapper tool's nonzero status is not the k6 exit code. No teardown or automatic repeat.

## Original summary gates

| Metric | Observed | Required / result |
| --- | --- | --- |
| Completed browse requests | 636,824 (353.50 requests/s over the reported run timer) | Delivered load, not server capacity or offered rate |
| Failed browse responses/timeouts | 15,423 / 636,824 = 2.4219% | <1% throughout — **fail** |
| Dropped scheduled iterations | 89,175 | Zero — **fail** |
| Client end-to-end p95 | 4,191 ms all-run | Original <800 ms diagnostic — **fail**; event-window analysis not yet performed |
| Client p99 / median | 10,000 ms / 15 ms | Descriptive only |
| k6 execution timer | 1,801,470.763 ms | Full 30-minute schedule completed, plus request completion grace |

Error and drop gates cannot be repaired by excluding post-scale-out latency windows. Even if event-filtered p95 were acceptable, those failures would remain. Missing scheduled work invalidates a clean capacity/linearity conclusion; it is not counted as successful server throughput.

## Generator observations and retained evidence

The native monitor saved 508 resource samples, with a maximum adjacent gap of 3.682 seconds. Sampled maximum Docker CPU was 75.30% (Docker's per-core percentage, not 75% of all eight allocated CPUs); sampled maximum memory was 9.31% of the 8 GiB limit. These samples do not prove the workstation/network was unconstrained. k6 reported the 1,000-active-VU ceiling at 19:53:25 UTC; request timeouts were recorded from 19:58:55 UTC. Waiting requests can exhaust VUs without saturating CPU/RAM, so do not attribute the bottleneck to the generator hardware or AWS without correlation.

Original raw JSONL (2,230,222,334 bytes), original summary, stdout/stderr, monitor samples, exit codes and container state are retained outside Git at `C:/Users/sirip/AppData/Local/Temp/opencode/swe5001-v3-20261110-live`. [Raw audit](raw-audit.json) confirms exact raw/summary agreement for browse requests, latency/error sample counts, errors, dropped iterations and all HTTP requests. Raw SHA-256: `b0b734c1fbc824b736510fa820ce5c268b2043011d25891429f1ce5b5e108775`; [retained-file hashes](retained-files.json). HTTP samples including setup comprise 621,402 status 200, six status 500, 1,250 status 503 and 14,167 status-zero transport/timeout failures. Status zero is not a server HTTP response. Do not commit the multi-gigabyte stream or private Docker host metadata.

## Recovered AWS correlation

The operator's archive was recovered without repeating load: SHA-256 `324dcb7022acf4821dc0d716c0ebfe2ae678afa9014a60363a96fdc75bdd4f4d`. [Selected original AWS files and hashes](aws-recovery-files.json), [derived correlation](aws-correlation.json), [minute-aligned client/server table](minute-correlation.csv). CloudWatch statistics cover the original workload window; UTC first/last minute buckets overlap partial test minutes. An absent 5xx metric datapoint is missing, not a fabricated zero.

| Observation | AWS evidence and limit |
| --- | --- |
| Automatic scaling | Successful request-target tracking adjusted desired count 1→2 at 19:56:39 UTC, 2→3 at 19:59:39, 3→4 at 20:02:39. [Original activities](aws/scaling-activities.json); [normalized records](scale-outs.json) use activity **StartTime**, not task-ready time. |
| Actual running tasks | [74 saved ECS observations](aws/observations.jsonl) show two running at 19:57:43, three at 20:00:21, and desired/running four with zero pending at 20:03:47, all revision 32. A transient fourth task with desired three was also sampled at 20:00:37; do not count it as the later 3→4 target adjustment. |
| Commerce saturation | Service-average CPU reached approximately 100% during 19:52–19:56, before additional capacity arrived. ALB minute p95 rose to multiple seconds (maximum minute p95 6.639 seconds). This supports an application-side overload contribution, not merely local generator CPU saturation; it is not full causal isolation. |
| Data tier | Maximum reported RDS connections 30; highest minute-average CPU 23.978%. Minimum freeable memory 63,393,792 bytes. These are declared per-minute statistics, not proof against transient pressure, local Hikari pool waits or every database bottleneck. |
| Connection-error search | Six commerce log events matched the combined OR search; identity/notification had none. Saved projections omit message bodies, so the six matches cannot distinguish pool wait timeout from instance reservation/exhaustion errors. Do not claim the no-connection-errors acceptance condition passed. |
| Later steady offered load | At 20:07–20:09 UTC, each complete minute delivered approximately 36,000 browse requests, zero measured browse errors/drops, client minute p95 19–57 ms; ALB reported approximately 600 requests/s. This useful later behaviour does **not** erase earlier failures or prove capacity/80%-linearity. |
| Restoration | [20:31:37 UTC recovery receipt](aws/recovery.json) confirms minimum two / maximum six, scaling unsuspended and desired two. Running count was three during recovery; settled two-task count, target health and two-AZ placement were not verified. No teardown occurred. |

## Collection failure and acceptance boundary

The detached CloudShell observer stopped after 20:03:47.522 UTC. At 20:27:44 UTC its PID was absent, log empty and automatic archive/restoration had not happened. CloudShell idle termination is plausible but unconfirmed. Saved observations are original and partial; no replacement samples were invented. The tested recovery restored the minimum, retrieved stored CloudWatch metrics/scaling history and connection-error projections, then archived the evidence with no reported collection warnings. Its source is retained as `recovery-operator.py`.

The original canonical analyzer was invoked with the original summary/raw samples, partial observations and normalized activity records. It exited **1** with [`Generator drops invalidate V3 evidence`](analysis.stderr.log), not a passing event-filtered result. The later missing observation coverage would also prevent full runtime/stability acceptance. No post-scale-out latency pass is claimed, and no acceptance gate or historical failure was changed.

12:29 SGT follow-up: retained alarm history confirms the delayed three-point decision; one-minute health metrics show unhealthy targets during error spikes, and RDS CPU credits stayed positive. The coarse task-count window was recovered, not replacement 15-second samples. Existing scheduled cleanup removed runtime/log groups at 07:38–07:52 SGT before the follow-up, leaving two retrieval warnings. [Updated investigation and provenance](INVESTIGATION.md#read-only-follow-up-received-at-1229-sgt). Collector fixes and the optional CPU mitigation are local, not deployed or a new acceptance result.
