// Performance: part of 04-quality-attributes.typ.
#import "../lib.typ": *

== Performance
<sec-performance>

On 11 November 2026 on AWS, distributed V3 r4 served the tested 600 req/s browse peak, about 36,000 catalogue reads a minute, while commerce scaled from one to six tasks; the monolith V2 baseline passed up to 200 req/s on one task. These are tested lower bounds, not maximum capacities. Earlier V3 runs failed; for V3 r3 we traced the failures to the client laptop's network path, not the services (§4.1.4), so V3 r4 ran its client on AWS. V3 r4 met its k6 gates, but our analyser, which checks the k6 results against the monitoring observations, gave no verdict because monitoring ended early. We have also not yet measured whether four tasks deliver at least 80% of four times one task's throughput (AISS-12).

=== Workload and what we measured

The sessions targeted the SWE5001 CloudFront endpoint, whose origin runs in Singapore (`ap-southeast-1`). Each iteration makes one catalogue read: 70% product-list and 30% product-detail GETs on a small, repeated demonstration catalogue (#src("perf/k6/browse.js")[driver], #src("perf/k6/lib.mjs")[schedule and checks]). The driver schedules arrivals independently of response time, so req/s is the offered rate, not a count of users. For scale, 600 req/s is 36,000 catalogue reads a minute. The V3 r4 client ran in the same AWS region as the services, so its latencies leave out a buyer's own network path. V2 runs ten 60 s plateaus from 10 to 200 req/s. V3 ramps from 40 to 200 req/s over 5 min and to 600 req/s over 10 min, holds for 10 min and ramps down over 5 min.

=== Metrics and acceptance

The verification plan (#src("docs/swe5001/impl-verification-observability.md")[V2 and V3 scenarios]) sets the gates in @tbl-performance-metrics. Liu Jin committed the 500 ms V2 and 800 ms V3 limits on 30 September, and Pan later built the tooling that applies them (#src("docs/swe5001/evidence/20261112-issue-789/README.md")[gate provenance]). They are stricter browse diagnostics under NFR-15 (#src("docs/quality/nfr-register.md")[NFR register]), which asks for p95 below 2 s at 100 concurrent users across browse, JOIN and My Orders; this workload covers browse only. A low error rate cannot compensate for undelivered load. When the client falls behind, the requests it never sends are dropped iterations, and an unsent request cannot fail: on 19 October k6 dropped 155,554 iterations yet recorded only 2 errors (0.0004%). These browse gates do not judge V4 (§4.1.6).

#figure(
  table(
    columns: (1.05fr, 2fr, 1.75fr, 1fr),
    table.header([Metric and tool], [Definition and unit], [Acceptance or use], [Threshold source]),
    [Offered and achieved load (k6)], [Scheduled arrivals in req/s versus `browse_requests` completed per interval. The one setup GET is excluded.], [V2: every plateau's full request budget. V3: the whole schedule delivered.], [Verification plan, V2 and V3],
    [Client latency (k6 `browse_latency`)], [Time in ms around the HTTP call and response validation, failed responses included. p95 and p99 bound 95% and 99% of samples.], [V2: p95 < 500 ms per plateau. V3: p95 < 800 ms outside the 3 min after each scale-out. p50 and p99 are diagnostics.], [Verification plan, V2 and V3; parent NFR-15 (2 s)],
    [Browse failures (k6 `browse_errors`)], [Failed responses as a share of completed requests (%). A pass needs HTTP 200 and a valid `Result` with the expected catalogue data.], [< 1%.], [Verification plan, V2 and V3],
    [Dropped iterations (k6)], [Scheduled iterations the client could not start (count).], [Zero. Drops are never counted as successful requests.], [`perf/k6/README.md` analysis rules],
    [Server response and throughput (CloudWatch, ALB)], [`TargetResponseTime` p50 and p95 in ms; `RequestCount` per minute divided by 60 gives req/s.], [Diagnostic only: it excludes the client and edge path.], [— (diagnostic)],
    [Target errors (CloudWatch, ALB)], [`HTTPCode_Target_5XX_Count`: 5xx responses returned by the service targets, per minute (count).], [Diagnostic only: it cross-checks the client error rate. A minute with no data point is not a verified zero.], [Verification plan, dashboard edge row],
    [Scale-out observed (V3 analyser)], [`scaleOutObserved`: after a recorded scaling activity starts, a monitoring sample shows at least as many running commerce tasks as that activity asked for (true or false).], [V3: the analyser's verdict needs it together with the error and p95 gates.], [`perf/k6/scaling.mjs`],
    [Resources and scale-out (ECS, CloudWatch)], [Running tasks, CPU and memory (%), scaling activity start and end, database connections.], [Explains saturation and scaling delay. V3 also needs one-to-four throughput ≥ 80% of linear.], [Verification plan, V3 (linearity)],
  ),
  kind: table,
  caption: [Load-test metrics and acceptance thresholds for the October and November runs. Source: #src("docs/swe5001/impl-verification-observability.md")[verification plan], #src("perf/k6/README.md")[analysis guide] and #src("perf/k6/scaling.mjs")[V3 analyser].],
) <tbl-performance-metrics>

=== Runs at a glance

@tbl-performance-runs lists every browse load test on AWS (V2 and V3), with the server sizing, where the k6 client ran and what the run shows; the V5 workload and the V4 expiry bursts are reported with their drills (§4.1.6, §4.2). Read the table by its k6 client column: every V3 run from a team workstation failed, and the one V3 run from a generator on AWS passed its k6 gates. Latency is the client p95 over the whole run; it screens a run and is not the V3 gate, which leaves out the 3 min after each scale-out.

#figure(
  table(
    columns: (0.75fr, 1.45fr, 1.3fr, 1fr, 0.9fr, 1.6fr),
    table.header([Run], [Server side], [k6 client], [Requests and errors], [Drops and client p95], [Verdict and what it shows]),
    [16 Oct: V2, two runs], [One monolith task. Source `6c7a99ab`.], [Team Windows workstation, Docker; location not recorded; 100–400 VUs], [57,600 and 0 each], [0; ≤ 57 ms per plateau], [Passed. Earlier corroboration of the V2 baseline.],
    [19 Oct: V3], [Three services; commerce one to six tasks of 0.5 vCPU, 1 GiB; `db.t4g.micro`; 12,000 requests per target per minute.], [Team macOS workstation, Docker; 100–400 VUs], [570,445 and 2 (0.0004%)], [155,554; 1,671 ms], [Failed: drops. Commerce 1 → 3 tasks; the client side limited the run.],
    [10 Nov: V3], [As 19 Oct, with pools of five. Source `93984f61`.], [Team Windows laptop, Docker Desktop (8 CPUs, 8 GiB), local Wi-Fi; 1,000 VUs], [636,824 and 15,423 (2.42%)], [89,175; 4,191 ms], [Failed: errors, drops. Commerce 1 → 4 tasks; capacity arrived about 5 min after failures began.],
    [11 Nov: V2], [One monolith task. Source `3a893036`.], [As 10 Nov], [57,600 and 0], [0; 30 ms], [Passed. The V2 baseline of this report: 200 req/s is a lower bound, not capacity.],
    [11 Nov: V3 r1], [Committed sizing, as 10 Nov. Source `3a893036`.], [As 10 Nov], [687,083 and 11,841 (1.72%)], [38,916; 3,957 ms], [Failed: errors and drops; all-run p95 screen also failed.],
    [11 Nov: V3 r2], [Session settings, not merged: commerce 1 vCPU, 2 GiB; `db.t4g.small`; CPU guard on.], [As 10 Nov], [720,141 and 4,561 (0.63%)], [5,858; 851 ms], [Failed: drops; all-run p95 screen also failed. 98.94% of drops fell in one minute.],
    [11 Nov: V3 r3], [As V3 r2, plus 6,000 requests per target per minute and a 120 s commerce slow start.], [As 10 Nov (the same laptop)], [725,924 and 8,429 (1.16%)], [75; 450 ms], [Failed: errors, drops. ALB target p95 9 ms; the client's network path was diagnosed.],
    [11 Nov: V3 r4], [As V3 r3; pools of five. Source `3a893036`.], [AWS-hosted generator (`t3a.2xlarge`, Singapore), Docker (8 vCPUs, 8 GiB); 1,000 VUs], [726,000 and 0], [0; 12 ms], [k6 gates passed; no analyser verdict (monitoring ended early). Commerce 1 → 6 tasks. The V3 result of this report.],
  ),
  kind: table,
  caption: [Load-test runs on AWS, 16 October to 11 November. Source: dated records #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October], #src("docs/swe5001/evidence/20261019-v3-autoscaling/README.md")[19 October], #src("docs/swe5001/evidence/20261110-v3-autoscaling/README.md")[10 November], #src("docs/swe5001/evidence/20261111-load-reruns/README.md")[11 November reruns], #src("docs/swe5001/evidence/20261111-v3-r3/README.md")[V3 r3] and #src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4].],
) <tbl-performance-runs>

Calculated: the V3 schedule offers 36,000 (ramp to 200 req/s) + 240,000 (ramp to 600 req/s) + 360,000 (10 min hold) + 90,000 (ramp down) = 726,000 browse arrivals. V3 r4 completed 725,999 of them, within the analyser's tolerance of one arrival, plus the setup GET; V3 r3 came closest before it but dropped 75.

=== What each run shows

*V2 baseline, 11 November.* On 11 November on AWS, one monolith task running the released source passed all ten plateaus (@tbl-performance-v2): 57,600 browse requests with no errors or dropped iterations, an all-run client p95 of 30 ms and 18 ms at the 200 req/s plateau (#src("docs/swe5001/evidence/20261111-load-reruns/v2/V2-REVIEW.md")[V2 review]). We chose this run as the baseline because it tested the current release, not because its latency was lower. Our monitoring script took 47 samples before, during and after the workload, all showing one stable task, but it then stopped early (exit 255), so no server metrics back up the run (#src("docs/swe5001/evidence/20261111-load-reruns/v2/observer-resolution.json")[coverage record]). The analyser's `kneeRps=200` is the highest rate tested, not a located knee; the run shows neither maximum capacity nor a controlled comparison with V3 r4, whose sizing and client differ.

#figure(
  table(
    columns: (1fr, 1.4fr, 1.25fr, 1.25fr),
    table.header([Offered req/s], [Completed requests], [Client p95 (ms)], [Browse errors]),
    [10], [600], [112.05], [0],
    [20], [1,200], [36.05], [0],
    [40], [2,400], [27.05], [0],
    [60], [3,600], [52], [0],
    [80], [4,800], [18], [0],
    [100], [6,000], [17], [0],
    [125], [7,500], [17], [0],
    [150], [9,000], [19], [0],
    [175], [10,500], [47], [0],
    [200], [12,000], [18], [0],
  ),
  kind: table,
  caption: [V2 baseline results per 60 s plateau, 11 November; every plateau passed its gates. Source: #src("docs/swe5001/evidence/20261111-load-reruns/v2/calibration.json")[calibration].],
) <tbl-performance-v2>

*16 October corroboration.* On 16 October on AWS, two runs of an earlier revision on the same workload each completed 57,600 requests with no errors or drops and passed every plateau; the repeat's all-run p95 was 25 ms, and 23 ms at 200 req/s (#src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record], with both per-plateau tables). Revisions and client settings differ, so the two dates show repeatability, not an improvement or a regression. AD-20’s scaling target was derived from the 16 October runs (§3.2.1).

*19 October scale-out.* Target tracking added a second commerce task at 15:55 SGT and a third at 15:58 SGT, each in about 65 s. With three tasks the ALB p50 was 5–7 ms while the client p50 stayed near 490 ms, so the client side limited the run (#src("docs/swe5001/evidence/20261019-v3-autoscaling/v3-alb-per-minute.tsv")[ALB intervals]). The same record holds our first observed saturation of one task: from 15:52 to 15:55 SGT the single 0.5 vCPU commerce task served 272–286 req/s at an ALB p50 of about 1.1 s with its CPU at 90–100%, and from 16:01 to 16:10 SGT three tasks served 425–461 req/s (#src("docs/swe5001/evidence/20261019-v3-autoscaling/README.md")[19 October record]). These ALB readings come from a failed run at the committed task size, so they are not an accepted capacity and do not apply to the 1 vCPU tasks of V3 r4; the 10 November campaign again saw a 0.5 vCPU commerce task reach about 100% CPU before failures began.

*10 November campaign.* Commerce CPU reached about 100% at 03:52 SGT and failures began at 03:53. The alarm decided at 03:56:38, and a second running task was first sampled at 03:57:43 (#src("docs/swe5001/evidence/20261110-v3-autoscaling/minute-correlation.csv")[minute correlation]). Later minutes near 600 req/s passed, but they cannot repair earlier failures; we did not isolate the cause (#src("docs/swe5001/evidence/20261110-v3-autoscaling/INVESTIGATION.md")[investigation]).

Tested locally, not on AWS: a Docker profile served 48,754 catalogue requests up to 400 req/s with no error, and a fault matrix showed that a failing Redis cache amplified load. We therefore added a failure-triggered backoff to the offer cache, released on 11 November (#src("docs/swe5001/evidence/20261110-catalogue-local-profile/README.md")[local profile]).

Calculated: the micro-class server has 74 unreserved connection slots. With pools of five, the services need at most 55 connections, and 55 + 3 owner + 1 operator + 15 safety = 74, so a steady maximum uses the whole budget, with no slot left over (#src("docs/swe5001/evidence/20261110-load-preflight/README.md")[preflight record]). With the previous pools of ten, the eleven tasks (at most six commerce, four notification and one identity) would need (6 + 4 + 1) × 10 = 110 connections, more than the 74 available, so the preflight blocked the load until we set pools of five.

*11 November reruns.* One promotion released the source for all five runs of that day, V3 r4 included. For V3 r2, r3 and r4 we changed the session, not the committed configuration. In V3 r2 the service-average CPU read 58% while one new task ran at 92–100%, so the averaged metric hid hot new tasks (#src("docs/swe5001/evidence/20261111-v3-investigation/README.md")[V3 r2 investigation]). V3 r3 ran from 18:39 to 19:09 SGT. Commerce scaled to six tasks, and one Spot task was reclaimed (§4.2). The ALB logged 725,898 requests at a target p95 of 9 ms, while 8,417 of k6’s 8,429 errors were timeouts: the services answered almost every request within 10 ms, yet the client timed out, so we looked for the delay on the client's side (#src("docs/swe5001/evidence/20261111-v3-r3/README.md")[V3 r3 record]).

Tested locally, not on AWS: static-only traffic from the same laptop reproduced the failures without commerce, and native Windows and Docker controls failed at the same time. We concluded that the laptop or its network access path caused the failures, but we did not find which part; whether it also caused the earlier V3 failures is unproven (#src("docs/swe5001/evidence/20261111-v3-r3/DIAGNOSIS.md")[diagnosis]). For V3 r4 we therefore moved the client to a load generator on AWS.

=== The distributed result

On 11 November on AWS, V3 r4 ran the unchanged V3 workload from 22:22:40 to 22:52:41 SGT against the V3 r3 server settings (#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record]). The client was a load generator hosted on AWS: a `t3a.2xlarge` instance in Singapore running the pinned k6 1.6.1 container with 8 vCPUs and 8 GiB. This removed the laptop's network path from the test; it is not evidence that the services improved. The application was the released source `3a893036`; only the test scripts changed (base `b911ea63` plus scaling-policy and orchestration edits, each recorded by hash). We recounted the raw k6 output afterwards and reproduced its counts and latency statistics (#src("docs/swe5001/evidence/20261111-v3-r4/verification.json")[recount and provenance]). @tbl-performance-r4 gives the client-side results.

#figure(
  table(
    columns: (1.1fr, 1.9fr, 1.6fr),
    table.header([Measurement], [V3 r4 result], [What it shows]),
    [Requests], [726,000: one setup GET and 725,999 browse requests (508,200 list, 217,799 detail); every response HTTP 200], [The whole schedule was delivered],
    [Errors and drops], [0 browse errors; all 725,999 body checks passed; 0 dropped iterations], [The error and drop gates passed],
    [Client latency, whole run], [Mean 9.4 ms; p50 8 ms; p95 12 ms; p99 33 ms; maximum 728 ms], [Every sample was under the 800 ms gate; this is not the event-relative analyser result],
    [Ten peak minutes], [360,000 browse completions, about 600 req/s; p95 ≤ 13 ms in each minute; no errors], [The 600 req/s plateau held],
    [Process exits], [k6, monitor and raw stream: 0, 0 and 0], [The k6 thresholds passed: errors < 1%, drops = 0, p95 < 800 ms],
  ),
  kind: table,
  caption: [V3 r4 client results on AWS, 11 November. Source: #src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record] and #src("docs/swe5001/evidence/20261111-v3-r4/browse-minutes.csv")[completion minutes].],
) <tbl-performance-r4>

The 1,000 virtual users (VUs) were preallocated; the reported active-VU gauge peaked at 100, which does not demonstrate 1,000 simultaneous users. The generator container averaged 6.1% of its eight vCPUs (peak 12.7%) and used at most 796 MiB, with no throttling, so the client had headroom.

Target tracking on requests made all four scaling changes (@tbl-performance-r4-scaling, @fig-performance-v3-timeline); the last went from four tasks straight to six, so commerce never ran five. Commerce service-average CPU peaked at 54.7% at 22:31, above the 50% CPU guard, but every recorded action cites the request policy. Six tasks ran about 16 min after the load started; that span includes the ramp itself, so it is not the autoscaler's reaction time. In the peak minutes 22:40–22:46 the ALB counted about 36,000 requests a minute, about 6,000 per target with six tasks: the policy converged on its target, which says nothing about saturation. RDS CPU peaked near 24% and connections at 40 (#src("docs/swe5001/evidence/20261111-v3-r4/server-minutes.csv")[server minutes]).

#figure(
  table(
    columns: (0.8fr, 1fr, 1fr, 1.2fr),
    table.header([Desired tasks], [Activity start], [Activity end], [First sample at new count]),
    [1 → 2], [22:30:48], [22:31:58], [22:31:39],
    [2 → 3], [22:32:48], [22:33:51], [22:33:39],
    [3 → 4], [22:34:48], [22:35:54], [22:35:47],
    [4 → 6], [22:37:48], [22:38:54], [22:38:54],
  ),
  kind: table,
  caption: [Commerce scale-out in V3 r4, 11 November (SGT). A running sample does not prove target health or the end of the 120 s slow start. Source: #src("docs/swe5001/evidence/20261111-v3-r4/scaling-activities.csv")[scaling activities].],
) <tbl-performance-r4-scaling>

#import "../diagrams/performance-v3-timeline.typ": v3-timeline
#figure(
  block(breakable: false, v3-timeline),
  caption: [Scale-out timeline view (time-series chart), V3 r4, 11 November. Completed browse requests follow the offered schedule up to 600 req/s with no errors while commerce grows from one to six tasks; the shaded tail follows the end of the monitoring script.],
) <fig-performance-v3-timeline>

*Acceptance limits.* The launcher refused V3 r4 because too little monitoring time remained. We started the unchanged workload with a separate script that skipped that safety check. The monitor stopped about 3 min before the load ended, leaving insufficient observations for an analyser verdict; a second monitor covered the tail but was not fed to the analyser. The workload met the unchanged numerical k6 thresholds, but no acceptance verdict is recorded. CloudWatch's missing target 5XX data points are not a verified zero (#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record]).

Even complete analyser coverage would not establish all remaining V3 checks or one-to-four linearity (#src("perf/k6/scaling.mjs", rev: "b911ea63795c5ddb7db1f7918f7f7af03a2745ff")[acceptance checks]). Linear scaling needs a controlled one-task and four-task pair: calculated efficiency is $T_4 / (4 T_1) >= 0.8$, where $T_1$ and $T_4$ are accepted throughputs. For example, if one task accepts 100 req/s, four tasks must accept at least 320 req/s. A ramp supplies neither the pair nor the ratio. That pair of fixed-size runs is our next measurement (AISS-12).

Afterwards, Application Auto Scaling lowered the desired commerce count from six to two at 23:09–23:21 SGT, with three tasks still running at 23:21; CloudTrail shows the four steps (read on 15 November, `docs/swe5001/evidence/20261115-aws-readback/`). Lim also read a Spot interruption after the workload from the ECS events; we did not export those events, and neither was a controlled recovery test (#src("docs/swe5001/evidence/20261112-issue-789/README.md")[post-run account]).

=== Configured optimisations and capacity limits

*Load balancing and scaling.* CloudFront forwards `/api/*` to the ALB, which routes to healthy ECS targets. Configured, not tested beyond the runs above: the AD-20 request policy (§3.2.1, #src("infra/swe5001/runtime/observability.tf")[policy source]). Calculated: 200 req/s × 60 s = 12,000, the 16 October V2 lower bound per task (AD-20, §3.2.1). A CPU guard is committed, off by default (`commerce_cpu_scale_out_enabled`), and enabled for the V3 r2, r3 and r4 sessions. @tbl-perf-configs compares the three configurations. On 11 November we applied the V3 r3 and r4 session values with local Terraform from reviewed saved plans, not through CI (§5.3). Calculated for the session's `db.t4g.small` (181 connections): pooled demand is 55 steady and 110 during full replacement; with five reserved, three owner, two operator and ten safety connections the totals are 75 and 130 of 181 (#src("docs/swe5001/evidence/20261111-load-reruns/v3-r2/prechecks/replacement-budget.json")[budget]). The session's precheck used this reserve rather than the micro-class one in §4.1.4; neither is measured utilisation or pool wait.

#figure(
  table(
    columns: (1.2fr, 1fr, 1fr, 1fr),
    table.header([Setting], [Release, 11 Nov], [`dev` since 14 Nov], [V3 r3 and r4 session]),
    [Commerce task size], [0.5 vCPU, 1 GiB], [1 vCPU, 2 GiB], [1 vCPU, 2 GiB],
    [ALB slow start], [None], [120 s], [120 s],
    [Request target (requests per target per minute)], [12,000], [12,000], [6,000],
    [CPU guard], [Off], [Off], [On at 50%],
    [Database], [`db.t4g.micro`], [`db.t4g.micro`], [`db.t4g.small`],
  ),
  kind: table,
  caption: [Commerce scaling configurations. Source: the release `3a893036`, #pr(807) (merged to `dev` on 14 November) and the #src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record].],
) <tbl-perf-configs>

*Caching on the read path.* CloudFront does not cache `/api/*` (#src("infra/swe5001/edge/cloudfront.tf")[edge policy]). Catalogue reads take display offers from `RedisOpenDealOfferCache` with one MGET; misses use one bounded PostgreSQL batch, entries live 15–25 s, and after-commit events evict changed keys. Settlement still checks the team under its lock, so the cache affects only display. We kept no cache-hit ratio on AWS, so the saving is designed, not measured.

*Queue-driven scaling (V4).* Earlier V4 attempts failed in a way that is itself a result: with 2,000 teams (R1) and 20,000 teams (R2) one notification task kept up, the sampled backlog never exceeded 103 and 64 visible messages, and the policy never added a task. R3's first admission failed, and its two later 20,000-team cohorts ran but failed the scaling and producer-hold requirements; R4 failed in setup before any seed. For R5 we therefore changed only the expiry side so that a backlog would build: commerce sharded and paced its expiry runs, ran on six tasks and had scale-in held for the run; consumers, connection pools and policy thresholds stayed as committed. On 14 November on AWS, V4 R5 passed with this experimental expiry configuration, using 80,000 teams, 40 times the V4 scenario of about 2,000. Team expiries went through the outbox, SNS and SQS to the notification consumer, and all six counts matched at 80,000: teams, expired teams, outbox events, published events, notifications and consumed-event markers. No event failed. `notification-svc` grew by itself from one to four tasks under its unchanged policy (target 50, one to four tasks); we did not force the count. The 80,000 teams expired at 21:01:26 UTC (05:01 SGT). The policy started scaling out about 5 min later, and a fourth task was first observed about 6 min after expiry. Sampled on AWS, the visible backlog peaked at 32,936 messages, and the largest of 24 returned oldest-message-age samples was 153 s. RDS connections peaked at 56 of the 79 available. The final sampled quiet interval began 472.693 s (about 8 min) after expiry. The original collector and analyser both exited 0, and the quiet-start, backlog, drain-after-scale-out and empty-DLQ gates passed (#published-evidence("20261114-v4-r5-live/v4-r5-pass.md")[V4 R5 record], #published-evidence("20261114-v4-r5-live/working-report-addendum.md")[experimental configuration]).

When R5 ran, the changes were reviewed but not committed or released. We scanned its image with Trivy before use (no HIGH or CRITICAL finding), but it did not pass through the promotion pipeline. #pr(807) later merged them to `dev` as opt-in settings that are off by default. So R5 does not show the V4 scenario of about 2,000 teams (§2.2) or a stable consumer capacity. At 05:20:32 SGT on 14 November we confirmed that the original commerce task definition, two healthy commerce tasks and the original flags were back. Our drain figure is sampled, not exact end-to-end latency, and queue-age sampling was incomplete (`queueAgeCoverageComplete=false`); that diagnostic did not gate the run and does not show a queue age of zero.

*Measured versus projected (briefing Q4).* On 11 November on AWS, V3 r4 served 600 req/s, the highest rate tested, with one to six commerce tasks at the session configuration in @tbl-perf-configs and every k6 gate met; one monolith task served 200 req/s. Calculated: V3 r4’s target of 6,000 requests per target per minute gives 6 tasks × 100 req/s = 600 req/s, exactly the tested peak; the environment's 12,000 gives 6 × 200 = 1,200 req/s, the policy's arithmetic ceiling, not a forecast. One-to-four linearity (V3) and the full NFR-15 mix remain unverified on AWS (AISS-12). No V3 run has tested the `dev` configuration in @tbl-perf-configs, or the release of 11 November from the AWS load generator: V3 r1 failed at the released sizing, but from the laptop whose network path we later diagnosed (§4.1.4). V3 r4 changed the client and four server settings together, so it does not show which change mattered.
