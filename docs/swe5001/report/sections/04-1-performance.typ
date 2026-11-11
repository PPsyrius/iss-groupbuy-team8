// Performance: part of 04-quality-attributes.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Performance
<sec-performance>

On AWS, one monolith task passed every plateau up to 200 requests per second (req/s) on 16 October 2026 and again on 11 November; this is a lower bound, not its capacity. Commerce scaled out in every distributed (V3) run, but all five V3 runs failed acceptance, so distributed capacity and 80% linear scaling remain unproven (AISS-12). The k6 client ran on a team machine over a local network in every run, and in the last run the load balancer answered at 9 ms p95 while the client failed (@tbl-performance-runs). The next server-side limit is the single RDS server, at 181 connections (AISS-05).

=== Workload and measurement boundary

The sessions targeted the SWE5001 CloudFront endpoint, whose origin runs in Singapore (`ap-southeast-1`). Each iteration makes one catalogue read: 70% product-list and 30% product-detail GETs on a small, repeated demonstration catalogue (#src("perf/k6/browse.js")[driver], #src("perf/k6/lib.mjs")[schedule and checks]). The driver schedules arrivals independently of response time, so req/s is the offered rate, not a count of users. V2 runs ten 60 s plateaus from 10 to 200 req/s. V3 ramps from 40 to 200 req/s over 5 min and to 600 req/s over 10 min, holds for 10 min and ramps down over 5 min.

=== Metrics and acceptance

The verification plan (#src("docs/swe5001/impl-verification-observability.md")[V2 and V3 scenarios]) sets the gates in @tbl-performance-metrics. They are stricter, team-set gates under NFR-15 (#src("docs/quality/nfr-register.md")[NFR register]), which asks for p95 below 2 s at 100 concurrent users across browse, JOIN and My Orders; this workload covers browse only. A low error rate cannot compensate for undelivered load. Queue drain (V4) was not run on AWS (AISS-12).

#figure(
  table(
    columns: (1.05fr, 2fr, 1.75fr, 1fr),
    table.header([Metric and tool], [Definition and unit], [Acceptance or use], [Threshold source]),
    [Offered and achieved load (k6)], [Scheduled arrivals in req/s versus `browse_requests` completed per interval. The one setup GET is excluded.], [V2: every plateau's full request budget. V3: the whole schedule delivered.], [Verification plan, V2 and V3],
    [Client latency (k6 `browse_latency`)], [Time in ms around the HTTP call and response validation, failed responses included. p95 and p99 bound 95% and 99% of samples.], [V2: p95 < 500 ms per plateau. V3: p95 < 800 ms outside the 3 min after each scale-out. p50 and p99 are diagnostics.], [Verification plan, V2 and V3; parent NFR-15 (2 s)],
    [Browse failures (k6 `browse_errors`)], [Failed responses as a share of completed requests (%). A pass needs HTTP 200 and a valid `Result` with the expected catalogue data.], [< 1%.], [Verification plan, V2 and V3],
    [Dropped iterations (k6)], [Scheduled iterations the client could not start (count).], [Zero. Drops are never counted as successful requests.], [`perf/k6/README.md` analysis rules],
    [Server response and throughput (CloudWatch, ALB)], [`TargetResponseTime` p50 and p95 in ms; `RequestCount` per minute divided by 60 gives req/s.], [Diagnostic only: it excludes the client and edge path.], [— (diagnostic)],
    [Resources and scale-out (ECS, CloudWatch)], [Running tasks, CPU and memory (%), scaling activity start and end, database connections.], [Explains saturation and scaling delay. V3 also needs one-to-four throughput ≥ 80% of linear.], [Verification plan, V3 (linearity)],
  ),
  kind: table,
  caption: [Load-test metrics and acceptance thresholds for the October and November runs. Source: verification plan and analysis guide.],
) <tbl-performance-metrics>

=== Runs at a glance

@tbl-performance-runs lists every load test on AWS, with the server sizing, where the k6 client ran and what the run shows. Latency is the all-run client p95, a screen, not the event-relative gate.

#figure(
  table(
    columns: (0.75fr, 1.45fr, 1.3fr, 1fr, 0.9fr, 1.6fr),
    table.header([Run], [Server side], [k6 client], [Requests and errors], [Drops and client p95], [Verdict and what it shows]),
    [16 Oct: V2, two runs], [One monolith task. Image `6c7a99ab`.], [Team Windows workstation, Docker; location not recorded; 100–400 VUs], [57,600 and 0 each], [0; ≤ 57 ms per plateau], [Passed. 200 req/s was the highest rate tested: a lower bound, not capacity.],
    [19 Oct: V3], [Three services; commerce one to six tasks of 0.5 vCPU, 1 GiB; `db.t4g.micro`; 12,000 requests per target per minute.], [Team macOS workstation, Docker; 100–400 VUs], [570,445 and 2 (0.0004%)], [155,554; 1,671 ms], [Failed: drops. Commerce 1 → 3 tasks. One task saturated at about 280 req/s; three served about 455 req/s.],
    [10 Nov: V3], [As 19 Oct, with pools of five. Source `93984f61`.], [Team Windows workstation, Docker (8 CPUs, 8 GiB); 1,000 VUs], [636,824 and 15,423 (2.42%)], [89,175; 4,191 ms], [Failed: errors, drops. Commerce 1 → 4 tasks; capacity arrived about 4 min after failures began.],
    [11 Nov: V2], [One monolith task. Source `3a893036`.], [As 10 Nov], [57,600 and 0], [0; 30 ms], [Passed. Repeats the lower bound on the released image.],
    [11 Nov: V3 r1], [Committed sizing, as 10 Nov. Source `3a893036`.], [As 10 Nov], [687,083 and 11,841 (1.72%)], [38,916; 3,957 ms], [Failed: errors, drops, p95. Committed sizing fails as on 10 Nov.],
    [11 Nov: V3 r2], [Session settings, not merged: commerce 1 vCPU, 2 GiB; `db.t4g.small`; CPU guard on.], [As 10 Nov], [720,141 and 4,561 (0.63%)], [5,858; 851 ms], [Failed: drops, p95. 98.94% of drops fell in one minute.],
    [11 Nov: V3 r3], [As V3 r2, plus 6,000 requests per target per minute and a 120 s commerce slow start.], [Team Windows laptop, Docker (8 CPUs, 8 GiB), local Wi-Fi; 1,000 VUs], [725,924 and 8,429 (1.16%)], [75; 450 ms], [Failed: errors, drops. ALB target p95 9 ms; the client's network path was diagnosed.],
    [R4], [As V3 r3.], [AWS-hosted generator (`t3a.2xlarge`, Singapore), bypassing the laptop], table.cell(colspan: 3)[#pending[Pan: R4 result]],
  ),
  kind: table,
  caption: [Load-test runs on AWS, 16 October to 11 November. Source: dated records #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October], #src("docs/swe5001/evidence/20261019-v3-autoscaling/README.md")[19 October], #src("docs/swe5001/evidence/20261110-v3-autoscaling/README.md")[10 November], #src("docs/swe5001/evidence/20261111-load-reruns/README.md")[11 November reruns], #src("docs/swe5001/evidence/20261111-v3-r3/README.md")[V3 r3] and #src("docs/swe5001/evidence/20261111-v3-r3/AWS-GENERATOR.md")[AWS generator].],
) <tbl-performance-runs>

Calculated: the V3 schedule offers 36,000 + 240,000 + 360,000 + 90,000 = 726,000 arrivals. Only V3 r3 delivered almost all of them.

=== What each run shows

*16 October baseline.* Every plateau met its p95 and error gates (@tbl-performance-v2). The analyser's `kneeRps=200` is the highest rate tested, not a located knee.

#figure(
  table(
    columns: (1fr, 1.4fr, 1.25fr, 1.25fr),
    table.header([Offered req/s], [Completed per run], [First p95 (ms)], [Repeat p95 (ms)]),
    [10], [600], [57.05], [53],
    [20], [1,200], [34], [39.05],
    [40], [2,400], [26], [27],
    [60], [3,600], [43.05], [28],
    [80], [4,800], [28], [23],
    [100], [6,000], [26], [23],
    [125], [7,500], [23], [42],
    [150], [9,000], [26], [23],
    [175], [10,500], [35], [23],
    [200], [12,000], [49], [23],
  ),
  kind: table,
  caption: [V2 results per 60 s plateau, 16 October. Source: #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[dated baseline record].],
) <tbl-performance-v2>

*19 October scale-out.* Target tracking added a second commerce task at 15:55 SGT and a third at 15:58 SGT, each in about 65 s. With three tasks the ALB p50 was 5–7 ms while the client p50 stayed near 490 ms, so the client side limited the run (#src("docs/swe5001/evidence/20261019-v3-autoscaling/v3-alb-per-minute.tsv")[ALB intervals]).

*10 November campaign.* Commerce CPU reached about 100% at 03:52 SGT and failures began at 03:53. The alarm decided at 03:56:38, and a second running task was first sampled at 03:57:43 (@fig-performance-v3-timeline, #src("docs/swe5001/evidence/20261110-v3-autoscaling/minute-correlation.csv")[minute correlation]). Later minutes near 600 req/s passed, but they cannot repair earlier failures; we did not isolate the cause (#src("docs/swe5001/evidence/20261110-v3-autoscaling/INVESTIGATION.md")[investigation]).

#import "../diagrams/performance-v3-timeline.typ": v3-timeline
#figure(
  block(breakable: false, v3-timeline),
  caption: [Scale-out timeline view (time-series chart), 10 November. Task capacity arrived after failures began; the shaded interval has no ECS observer samples.],
) <fig-performance-v3-timeline>

Tested locally, not on AWS: a Docker profile served 48,754 catalogue requests up to 400 req/s with no error, and a fault matrix showed that a failing Redis cache amplified load. We therefore added a failure-triggered backoff to the offer cache, released on 11 November (#src("docs/swe5001/evidence/20261110-catalogue-local-profile/README.md")[local profile]). Calculated: with pools of five the services need at most 55 database connections, and 55 + 3 owner + 1 operator + 15 safety = 74 free slots (#src("docs/swe5001/evidence/20261110-load-preflight/README.md")[preflight record]).

*11 November reruns.* One promotion released the source for all four reruns. For V3 r2 and r3 we changed the session, not the committed configuration. In V3 r2 the service-average CPU read 58% while one new task ran at 92–100%, so the averaged metric hid hot new tasks (#src("docs/swe5001/evidence/20261111-v3-investigation/README.md")[V3 r2 investigation]). V3 r3 ran from 18:39 to 19:09 SGT. Commerce scaled to six tasks, and one Spot task was reclaimed (§4.2). The ALB logged 725,898 requests at a target p95 of 9 ms, while k6 recorded 8,417 timeouts (#src("docs/swe5001/evidence/20261111-v3-r3/README.md")[V3 r3 record]).

Tested locally, not on AWS: static-only traffic from the same laptop reproduced the failures without commerce, and native Windows and Docker controls failed at the same time. The diagnosis points to the laptop's local Wi-Fi path, not the services; the same path may have affected the earlier V3 runs (#src("docs/swe5001/evidence/20261111-v3-r3/DIAGNOSIS.md")[diagnosis]). R4 moves the client to an AWS-hosted generator.

*What acceptance needs.* An accepted V3 delivers the whole schedule with complete observations and unchanged gates. Linear scaling also needs a controlled one-task and four-task pair: calculated efficiency is $T_4 / (4 T_1) >= 0.8$, where $T_1$ and $T_4$ are accepted throughputs. A ramp with changing task counts and dropped work supplies neither.

=== Configured optimisations and capacity limits

*Load balancing and scaling.* CloudFront forwards `/api/*` to the ALB, which routes to healthy ECS targets. Configured, not tested beyond the runs above: target tracking on `ALBRequestCountPerTarget` at 12,000 requests per target per minute, two to six commerce tasks and cooldowns of 60 s out and 180 s in (AD-20, #src("infra/swe5001/runtime/observability.tf")[policy source]). Calculated: 200 req/s × 60 s = 12,000, the V2 lower bound per task. A CPU guard is committed, off by default (`commerce_cpu_scale_out_enabled`), and enabled for the V3 r2 and r3 sessions. Only its code and variable are committed: the r3 session values (the guard at 50% instead of the default 70%, 6,000 requests per target and the 120 s slow start, with 1 vCPU and 2 GiB for commerce and `db.t4g.small`) were applied by local Terraform.

*Caching on the read path.* CloudFront does not cache `/api/*` (#src("infra/swe5001/edge/cloudfront.tf")[edge policy]). Catalogue reads take display offers from `RedisOpenDealOfferCache` with one MGET; misses use one bounded PostgreSQL batch, entries live 15–25 s, and after-commit events evict changed keys. Settlement still checks the team under its lock, so the cache affects only display. We kept no cache-hit ratio on AWS, so the saving is designed, not measured.

*Measured versus projected (briefing Q4).* On AWS, the highest accepted rate is 200 req/s on one task. Calculated: 6 tasks × 200 req/s = 1,200 req/s is the policy's arithmetic ceiling, not a forecast. One-to-four linearity and V4 queue drain remain unverified on AWS (AISS-12, NFR-64).
