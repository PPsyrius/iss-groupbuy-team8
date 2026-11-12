# V3 r2 review — 11 November 2026

**Failed campaign, preserved unchanged.** The single authorised r2 workload ran from approximately 08:16:02.753 to 08:46:03.344 UTC and completed 720,141 browse requests, with 4,561 errors (0.633348%), 5,858 dropped iterations and all-run client p95 851 ms. Original k6 exit was 99. The error-rate gate passed; the zero-drop gate and all-run latency diagnostic failed. The unchanged committed analyser exited 1 with `Generator drops invalidate V3 evidence`. No event-relative latency pass, distributed capacity or separate 80%-linearity acceptance is claimed.

## Variant and provenance

The approved combined variant increased commerce from 512 CPU units/1024 MiB to 1024 CPU units/2048 MiB, resized RDS from `db.t4g.micro` to `db.t4g.small`, and activated a positive-only CPU guard at 50% for one 60-second average, adding one task with a 60-second cooldown. Commerce revision 37 was used. Identity revision 20 and notification revision 34 retained their sizes. Images, five-connection pools, health dependencies, shared timeouts, request target 12,000/target/minute, maxima six/four, workload, 1,000-VU allocation and acceptance gates were unchanged. The preflight verified one stable commerce task with active scaling and the exact guard.

Tooling source is `b911ea63795c5ddb7db1f7918f7f7af03a2745ff`; released source is `3a893036d70d028b29ae13ebf820283571c1ae00`. The session-only release adapter, release lock, local Terraform overlays and selected deployment receipts are separately hashed in `provenance-frozen.json` and `deployment/`. They are not merged IaC or a new GitHub promotion. The original adapter/observer/capture tests passed 45 tests; the outcome-neutral offline r2 diagnostic tests passed seven. These are tooling checks, not application acceptance.

## Budgets and observations

The post-resize fixed SQL report measured 181 maximum connections, five reserved. The unchanged steady allowances require 75/181; the full all-service 200% replacement envelope requires 130/181, leaving 51 slots after those allowances. Fresh subnet metadata/actual ENIs/controls and regional Fargate quota accounting passed. These are scoped prechecks, not proof that no request can wait on its local five-connection pool or that quota is reserved. One currently confirmed SNS email subscription meets established session practice; pending subscriptions are not an exception or blocker.

The original observer completed its 40-minute timer with 160 samples from 08:15:11.948 to 08:55:11.859 UTC. Samples bracket execution, the maximum gap is 37.943512 seconds, and the sampled task revision remained stable. One ECS read failed at 08:26:18.579 UTC, so `collectionComplete=false` remains authoritative. This is not credential-expiry evidence; the fresh role window was measured to expire at 09:15:01 UTC. No sampled count exceeded six and no retained in-run unhealthy-task event was found. Those are sampled diagnostics, not proof that every task/health transition was observed.

The generator retained 508 resource samples, maximum gap 3.616 seconds, peak Docker CPU 135.90% against an eight-CPU limit, and peak memory 10.09% of 8 GiB; no OOM occurred. Active VUs reached the configured 1,000 ceiling. Independent physical-host/ISP/network headroom was not established. Of the browse failures, 4,448 had no HTTP response and 113 were HTTP 503. Completion-minute bins concentrate failures/drops in minutes 9–15, followed by recovery; completion bins are not scheduled-arrival attribution and later good minutes cannot repair the campaign.

## Scaling and server correlation

| UTC activity start | Policy | Requested desired count | Original status |
| --- | --- | --- | --- |
| 08:26:44.977 | Request target tracking | 2 | Successful |
| 08:29:11.212 | CPU headroom | 3 | **Overridden**, not successful |
| 08:29:44.971 | Request target tracking | 4 | Successful |
| 08:30:44.952 | Request target tracking | 5 | Successful |
| 08:33:11.249 | CPU headroom | 6 | Successful |

Observation independently recorded desired three before four; the overridden CPU activity must neither be relabelled successful nor silently erased. The frozen normalizer retained it as a blocker and exited 1. `load/scale-outs.json` is a successful-activity-only diagnostic with that blocker, **not a fully accepted normalized desired-count sequence**. It must not be cited as proof of a direct actual 2→4 transition or used to claim an event-filtered latency pass. Both policies' original activities and the sampled desired/running/pending timeline are retained. Unhealthy replacement is not counted as policy scale-out. The first CPU activity occurred after request-based scale-out; this variant does not demonstrate that the CPU guard eliminated initial scaling delay.

The immediate post-run one-minute diagnostic readback reported commerce average CPU peaking at about 57.84%, RDS average CPU at about 24.68% and database connections at 40/181. ALB target p95 peaked around 6.97 seconds in the 08:28 UTC minute. All three services' retained connection-limit/pool-wait filter results were empty; the separate commerce pool-wait review also found no matches. This differs from r1's pool-wait evidence, but it is not a controlled isolation of the effect of larger tasks, RDS or the guard. Service-average CPU and database-wide headroom do not prove the absence of per-task stalls or identify a unique root cause.

## Preservation and restoration

The 2,603,433,426-byte raw JSONL stream passed the raw/summary consistency audit; SHA-256 is `9f120188b3afa3c01e9d488f80feb3eb8a44acc97d81325a028ef63dcda95c2f`. All 94 files in r1's sealed manifest were separately byte-verified unchanged. A Python 3.9-only supplemental client diagnostic failed on nanosecond timestamp parsing; its failure receipt is retained, and the exact frozen source subsequently completed offline on Python 3.13.8. No HTTP workload was repeated and no raw samples were changed.

Independent normal-floor restoration read back minimum two, maximum six, all suspension flags false, and five desired/running commerce tasks with zero pending at 08:56:06 UTC. It preserved the scaled count above two; it does not claim settled two-task/two-AZ recovery. The observer never restored runtime. Original observer archive, restored archive, original exit codes and later diagnostic additions are retained separately; archive/hash verification receipts document integrity rather than acceptance.

No further tuning, workload retry, Git commit/push/PR/promotion or teardown was dispatched. Runtime remains up. The existing nightly 23:00 SGT safety schedule was not changed. This new configuration variant does not repair r1 or create a like-for-like old 0.5-vCPU monolith/distributed capacity pair. Report integration must preserve every failed campaign and the remaining acceptance boundaries.
