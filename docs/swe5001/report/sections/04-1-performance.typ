// Performance: part of 04-quality-attributes.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Performance
<sec-performance>

BlocBuy combines static-content edge caching with an Application Load Balancer (ALB) and independently scalable Elastic Container Service (ECS) commerce tasks. On AWS, the 16 October and fresh 11 November 2026 monolith baselines passed through 200 requests per second. The October and November distributed campaigns demonstrated automatic scale-out but failed acceptance, including the 11 November larger-task/CPU-guard variant. None establishes accepted distributed capacity or linear scaling (AISS-12).

#let perf-root = "https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/"
#let perf-source(path, body) = link(perf-root + path, body)

// Readability changes are confined to Pan's sections; shared report styles are unchanged.
#[
#show raw: set text(size: 8.5pt)
#show table.cell: set block(breakable: false)

=== Workload and measurement boundary

The dated AWS sessions targeted the SWE5001 CloudFront endpoint in Singapore (`ap-southeast-1`), not the shared SWE5006 development deployment. The driver schedules arrivals independently of response time: requests per second (RPS) is the offered rate, not a count of concurrent users. Each workload iteration performs one read: 70% product-list GETs (`page=1&size=20`) and 30% product-detail GETs. Setup reads the existing demo catalogue and selects detail IDs from that page. This is a small, repeated read working set, not a large-catalogue or checkout benchmark; the records do not establish total catalogue volume. Payment settlement, writes, browser rendering and completed purchases are outside this workload. #perf-source("perf/k6/browse.js", [Driver]) · #perf-source("perf/k6/lib.mjs", [Schedule and response validation]).

The workload exercises the catalogue-read portion of UC1, whose design is traced in Appendix A.3 and A.14. It does not verify the full NFR-15 scenario: that requirement uses 100 concurrent users, includes JOIN and My Orders, and bounds expiry lag. Arrival-rate browse scaling is assessed separately under NFR-64. A passing browse campaign would therefore leave those business-operation checks outstanding.

*Evidence snapshots.* October source links identify the inspected historical revision; November records identify their own released source and tooling. Restored evidence links use repository URLs rather than local PDF paths. #pending[Editor: publish the restored records and pin their draft `dev` URLs to the evidence-freeze commit before submission; source pins must continue to identify the code actually inspected.]

#figure(
  table(
    columns: (0.8fr, 2.1fr, 2.1fr),
    table.header([AWS run (2026)], [Topology and provenance], [Offered load and generator]),
    [16 October: V2, two runs], [One stable monolith task. Image source `6c7a99ab`; tooling `767d980b`. Route, image digest and task observations retained in the dated record.], [Ten 60 s plateaus: 10, 20, 40, 60, 80, 100, 125, 150, 175, 200 RPS. Windows/Docker generator, 100 preallocated / 400 maximum virtual users (VUs).],
    [19 October: V3], [Distributed services; commerce starts at one task, 0.5 vCPU / 1 GB. Commerce image source `5e83d63e`; tooling `9512f1e0`. Different revisions from V2.], [40 → 200 RPS over 5 min; → 600 over 10 min; hold 10 min; → zero over 5 min. macOS/Docker generator, 100 / 400 VUs.],
    [10 November SGT: V3], [Released source `93984f61`; commerce revision 32, pool five; one-task start. Collector helpers separately identified, not attributed to released source.], [Same 30-minute schedule and gates. Windows/Docker generator: eight logical CPUs, 8 GiB; 1,000 preallocated / maximum VUs.],
  ),
  kind: table,
  caption: [Workload and provenance. Full SHAs, digests, preflights and operator observations are in the #perf-source("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md", [V2 record]), #perf-source("docs/swe5001/evidence/20261019-v3-autoscaling/README.md", [October V3 record]) and #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/README.md")[November V3 record].],
) <tbl-performance-workloads>

=== Metrics and acceptance

The #perf-source("docs/swe5001/impl-verification-observability.md", [verification plan]) defines scenario acceptance; the #perf-source("perf/k6/SCALING.md", [analysis guide]) distinguishes event-relative acceptance from the runner's all-run diagnostic. A low HTTP failure rate cannot compensate for undelivered load.

#figure(
  table(
    columns: (1.05fr, 2.1fr, 1.85fr),
    table.header([Metric / source], [Definition and unit], [Acceptance / interpretation]),
    [Offered and achieved load / k6], [Scheduled arrivals in requests/s versus `browse_requests` completed per interval. One setup GET is counted by `http_reqs` but excluded from workload counts.], [V2 requires every plateau's full request budget. V3 must deliver its schedule; observed ALB RPS is reported separately.],
    [Client latency / k6 `browse_latency`], [Wall-clock ms around HTTP plus response validation. p50 is the median; p95 and p99 bound 95% and 99% of samples. Includes failed responses, excludes setup.], [V2: each plateau p95 < 500 ms. V3: p95 < 800 ms outside the 3 min after each scale-out. p50/p99 are diagnostics, not additional gates.],
    [Browse failures / k6 `browse_errors`], [Failed workload responses / completed workload requests (%). Requires HTTP 200 and a valid successful `Result` with expected catalogue data; detail ID must match.], [< 1%. Counts HTTP and payload failures, not completed orders or successful payment operations.],
    [Dropped iterations / k6], [Scheduled iterations the generator could not start (count); absent from completed-request latency/error denominators.], [Zero required. Missing or nonzero counts invalidate capacity evidence; never treat drops as successful requests.],
    [Server response and throughput / CloudWatch ALB], [`TargetResponseTime` p50/p95, reported in ms; `RequestCount` per minute / 60 gives RPS.], [Correlate with client data. ALB target timing excludes the full client/edge path and cannot substitute for the client p95 gate.],
    [Resources and scale-out / ECS, CloudWatch, scaling activities], [Running task count, CPU/memory (%), activity start/fulfilment timestamps and database connections (count).], [Explain saturation and scaling delay. V3 also requires one-to-four achieved throughput ≥ 80% of ideal linear scaling; inventory connection limits separately.],
  ),
  kind: table,
  caption: [Load-test metric definitions and thresholds for the October 2026 scenarios. Queue depth/age, drain time and dead-letter counts belong to V4, not the browse workload.],
) <tbl-performance-metrics>

=== Baseline results: 16 October 2026

Verified on AWS: both V2 runs completed exactly 57,600 workload requests plus one setup request, with zero browse failures and zero drops. Every plateau met its p95 and error thresholds. The repeat lasted 601.035 s including the terminal scheduler guard; its all-run client p50/p95/p99 were 16/25/53 ms. These all-run percentiles do not replace the per-plateau checks below. #perf-source("docs/swe5001/evidence/20261016-http-baseline-dashboard/v2-repeat-summary.json", [Repeat summary]) · #perf-source("docs/swe5001/evidence/20261016-http-baseline-dashboard/v2-repeat-calibration.json", [Calibration]).

#figure(
  table(
    columns: (1fr, 1.4fr, 1.25fr, 1.25fr),
    table.header([Offered RPS], [Completed per run], [First p95 (ms)], [Repeat p95 (ms)]),
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
  caption: [V2 AWS results, 16 October 2026. Each row is a 60 s plateau; both runs passed all rows. Source: #perf-source("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md", [dated baseline record and summaries]).],
) <tbl-performance-v2>

The repeat's sampled service observations bracketed the run and showed one running/desired monolith, no pending task and the same task definition. Sampled generator-container CPU peaked at 15.89%; host observations covered only part of the run. The first run had no usable generator-resource capture. Most importantly, neither run reached saturation: the analyzer's `kneeRps=200` means the highest tested passing rate, not a located capacity knee. It is a tested lower bound for this monolith revision and working set, not proof of capacity per distributed task.

=== Scale-out results: 19 October 2026

Verified on AWS: the target-tracking alarm automatically increased commerce from one to two tasks at 15:55:02–15:56:08 Singapore time (66 s), then from two to three at 15:58:02–15:59:05 (63 s). These are activity start-to-fulfilment durations, not the full delay from the first overload. The #perf-source("docs/swe5001/evidence/20261019-v3-autoscaling/README.md", [V3 record]) and its scaling activities show genuine scale-out, but the run is rejected acceptance evidence.

#figure(
  table(
    columns: (1.15fr, 1.8fr, 2.05fr),
    table.header([View / window], [Observed result], [What it establishes]),
    [Client, full 30 min run], [570,445 workload requests plus one setup; about 317 RPS averaged over the run. Client p50/p95/p99: 592/1,671/2,936 ms. Two browse failures (about 0.0004%).], [Error rate passed; all-run p95 diagnostic failed. The average includes ramp periods and is not steady-state throughput.],
    [Generator, full run], [155,554 dropped iterations; k6 exit 99. Retained analyzer receipt: “Generator drops invalidate V3 evidence”, exit 1.], [Acceptance rejected. The planned 600 RPS was not delivered; event-relative p95 and one-to-four linearity are unproven.],
    [ALB, 15:52–15:55, one task], [272–286 RPS; p50 about 1.1 s, p95 about 1.9 s. Commerce CPU reached 90–100%.], [Observed saturation around 280 RPS for this 0.5-vCPU commerce revision and read workload.],
    [ALB, 16:01–16:10, three tasks], [425–461 RPS (about 455); per-minute p50 5–7 ms and p95 13–50 ms.], [Observed server-side throughput and recovery after scale-out, not client latency acceptance or the maximum capacity of three tasks.],
  ),
  kind: table,
  caption: [V3 AWS observations, 19 October 2026 (times in Singapore, UTC+8). Sources: #perf-source("docs/swe5001/evidence/20261019-v3-autoscaling/v3-summary.json", [raw summary]), #perf-source("docs/swe5001/evidence/20261019-v3-autoscaling/v3-alb-per-minute.tsv", [ALB intervals]) and #perf-source("docs/swe5001/evidence/20261019-v3-autoscaling/v3-analysis-result.txt", [rejection receipt]).],
) <tbl-performance-v3>

The runner's all-run p95 is only a diagnostic screen; it cannot establish the required p95 outside post-scale-out exclusion windows. The retained analyzer rejection stands, and no new analysis of the privately retained raw samples is claimed here. At three tasks, client p50 remained about 490 ms despite ALB p50 of 5–7 ms. The generator had at most 400 VUs and its container CPU peaked at 56%; the precise network/edge/generator constraint was not isolated. Consequently, neither client-generator CPU saturation nor application capacity at 600 RPS is established by this run.

=== Fresh distributed campaign: 10 November 2026 SGT

Verified on AWS, with partial ECS observation coverage: run `20261110-v3-194543` completed 636,824 browse requests, with 15,423 failures (2.4219%), 89,175 dropped iterations and all-run client p95 of 4,191 ms; k6 exited 99. Error and drop gates failed independently of any post-scale-out latency exclusions. The canonical analyzer rejected the run; no event-filtered p95 pass or 80%-linearity result is claimed. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/README.md")[Dated result and retained summaries].

AWS activities and saved samples show automatic one-to-four scale-out, but commerce CPU reached approximately 100% before additional tasks arrived. The observer stopped after its 04:03:47 SGT sample, leaving the last part of the run without the intended ECS revision/stability observations. Recovered CloudWatch metrics and later complete minutes near 600 requests/s do not replace those samples or erase earlier failures. Six projected commerce connection-error matches could not be classified as pool waits versus instance exhaustion. The original failed evidence remains; the cause is not reduced to generator CPU, database capacity or CloudShell loss alone.

*Connection and restoration controls.* The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-load-preflight/README.md#10-november-disposition-for-775")[preflight record] rejected maximum runtime pools of 110 against 74 unreserved database slots. The operator's pool-five experiment reduced runtime maxima to 55; three owner, one operator and 15 safety slots bring the calculated budget to 74, with no overlapping full-capacity deployment. Commerce's temporary one-task start was restored to minimum/desired two, but three tasks were still running at readback. Scheduled cleanup later removed runtime. These are recorded experiment controls, not validated headroom or settled recovery.

*Local investigation, not AWS acceptance.* The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-catalogue-local-profile/README.md")[Docker profile] initially did not reproduce the overload. A later #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-catalogue-local-profile/MATRIX.md")[fault matrix] reproduced cold-start contention and optional-cache failure amplification. It informed a failure-triggered cache-backoff candidate, but cold controls and some fault transitions still failed. Candidate comparisons, rejected experiments and source-specific test receipts remain in that record. Collector/backoff changes in #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/784")[PR \#784] later reached the 11 November release and reruns below; local regressions alone do not validate deployed capacity.

=== Released-image reruns: 11 November 2026

Verified on AWS: promotion #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42947289936")[42947289936] released source `3a893036` after PRs \#784/\#785. The fresh V2 and two separately authorised V3 campaigns used that release; the unchanged driver/observer source was `b911ea63`, with a separately hashed local release-binding adapter for preparation. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/README.md")[dated rerun record] retains original results, source/digest identities and integrity receipts. These draft evidence URLs follow the publication/pinning limitation above.

#figure(
  table(
    columns: (1.05fr, 1.55fr, 1.3fr, 1.1fr),
    table.header([11 November run], [Browse requests / errors], [Drops / client p95], [Disposition]),
    [V2: released monolith], [57,600 / zero], [Zero / 30 ms all-run; 18 ms at 200 RPS], [All ten HTTP plateaus passed; lower bound only.],
    [V3 r1: 0.5 vCPU, RDS micro], [687,083 / 11,841 (1.7234%)], [38,916 / 3,957 ms], [Failed; k6 99, analyser 1.],
    [V3 r2: 1 vCPU, RDS small, CPU guard], [720,141 / 4,561 (0.633348%)], [5,858 / 851 ms], [Error gate passed; campaign failed; k6 99, analyser 1.],
  ),
  kind: table,
  caption: [11 November AWS HTTP results. Both V3 runs retained the same 30-minute schedule, eight-CPU/8-GiB generator and 1,000 preallocated/maximum VUs. Original summaries and raw/summary audits are in the dated rerun record; p95 here is the all-run diagnostic, not a post-scale-out-filtered pass.],
) <tbl-performance-20261111>

The new V2 retained 47 hash-verified observations bracketing its ten-minute workload, with one stable task and maximum gap 17 s. Its planned 15-minute observer later exited 255 after a container-role metadata HTTP 500. The covered HTTP/stability result stands, but full collector completion and AWS server-metric corroboration are not claimed. The highest passing rate remains 200 RPS, not a located saturation knee. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v2/observer-resolution.json")[Coverage disposition].

*Combined mitigation, not acceptance.* R2 increased commerce from 0.5 vCPU/1 GiB to 1 vCPU/2 GiB and RDS from `db.t4g.micro` to `db.t4g.small`, then activated a positive-only CPU guard: 50% average for one 60 s period, add one task, 60 s cooldown. It preserved images, five-connection pools, shared timeouts, health dependencies, the request policy and maxima. Calculated from measured SQL limits: the unchanged allowances require 75/181 connections at steady maxima and 130/181 across the full all-service 200% replacement envelope. These checks provide budgeted headroom, not proof that a task's pool cannot stall. The combined change improved observed error/drop totals relative to r1 but did not pass; it cannot isolate which change helped or form a like-for-like old monolith/distributed capacity pair. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v3-r2/V3-REVIEW.md")[R2 configuration, budgets and limits].

Both V3 observers retained 160 samples bracketing execution, but each recorded one failed ECS read and kept `collectionComplete=false`. R1 also retained 179 commerce pool-wait events, unhealthy replacements and seven samples above the six-running-task analyser bound. R2's maximum gap was 37.94 s, with a stable sampled revision, no count above six and no retained in-run unhealthy event; its connection/pool-wait filters found no matches. R2 reached 1,000 active VUs despite no generator OOM and sampled CPU/memory below container limits. Independent host/network headroom remains unproven. None of these diagnostics erases drops or establishes a unique bottleneck.

R2's successful scaling activities requested two, four, five and six tasks. The CPU activity requesting three was marked *Overridden*, while desired three was independently sampled before four. The normalizer retained this ambiguity as a blocker, rather than relabel it successful or invent a direct 2→4 transition. The unchanged analyser rejected generator drops before calculating event-relative p95. Later low-latency minutes do not repair the campaign, and the 80%-linearity criterion remains separate. Normal-floor restoration read back bounds 2–6 and five running/desired tasks at 08:56 UTC; it did not establish settled two-task recovery. The operator explicitly kept distributed runtime up, without further tuning, an HTTP retry or teardown. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v3-r2/load/coverage-review.json")[Activity/observer review] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v3-r2/load/analysis.stderr.txt")[Unchanged analyser rejection].

=== What the scaling evidence means

The November observations separate *capacity arriving* from *requests succeeding*. In the 03:52 SGT minute, commerce CPU was approximately 100%; the first failed response followed at 03:53:08 and the first dropped arrival at 03:53:25. The first scale-out activity began at 03:56:39, and two running tasks were first sampled at 03:57:43. The retained alarm history reports its decision at 03:56:38 against the 03:51–03:53 minute values. This establishes an observed detection/decision delay followed by task startup, not a claim that a 60-second scale-out cooldown bounds total recovery time. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/INVESTIGATION.md")[Timing investigation and alarm history].

#import "../diagrams/performance-v3-timeline.typ": v3-timeline
#figure(
  block(breakable: false, v3-timeline),
  caption: [Historical November V3 timeline (10 November 2026 SGT). Complete minute buckets only; throughput includes failed completions. The request schedule is an offered-rate reference, not delivered load. The shaded interval lacks the original fine-grained ECS observer; recovered CloudWatch series continue, but no missing task samples are interpolated. Derived from the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/minute-correlation.csv")[minute correlation] and #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/aws-correlation.json")[activity/observer record].],
) <fig-performance-v3-timeline>

The timeline shows why later low latency cannot rescue the run. At 04:07–04:09 SGT, complete minutes recorded approximately 600 completed requests/s, no browse failures or drops, and client p95 of 57, 21 and 19 ms. Earlier overload and a later timeout spike had already violated acceptance. An unhealthy-task replacement overlapped the first error spike; recovered metrics also report an unhealthy target near the second spike. These correlations motivate checking readiness and dependency behaviour, but do not isolate CPU, Redis, PostgreSQL or the edge as the single cause. See the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-v3-autoscaling/INVESTIGATION.md")[retained investigation].

Three obligations remain distinct. *Campaign acceptance* requires the scheduled work, latency and failure gates to pass with complete observations. *Capacity* requires a valid sustained maximum for a stated revision and working set; the highest passing V2 plateau is only a lower bound. *Scaling efficiency* requires comparable one- and four-task measurements under the same conditions: calculated efficiency is $T_4 / (4 T_1) >= 0.8$, where $T_1$ and $T_4$ are accepted achieved throughputs. A ramp with changing task counts and dropped work supplies neither a controlled pair nor that ratio. Cold-start and dependency-fault checks additionally ask whether capacity becomes usable without unacceptable disruption. They must not be replaced by a warmed steady-state interval.

=== Configured optimisations and capacity limits

*Load balancing and scaling.* The configured CloudFront API origin forwards to the Application Load Balancer (ALB), which distributes requests to healthy ECS targets. On 16 October, AWS readback confirmed two healthy commerce tasks in different zones and target tracking at 12,000 requests per target per minute. Calculated from V2: 200 requests/s × 60 s = 12,000 requests/min. The policy uses `ALBRequestCountPerTarget`, a maximum of six commerce tasks, and cooldowns of 60 s out / 180 s in. The normal demo minimum is two; V3 deliberately started at one. The persisted environment setting is not the checked-in fallback, which is only 200 requests per target per minute. These settings are configuration, not a throughput guarantee. #perf-source("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md", [Live policy readback]) · #perf-source("infra/swe5001/runtime/observability.tf", [Policy source]) · #perf-source("infra/swe5001/runtime/variables.tf", [Fallback]).

*Caching on the exercised read path.* CloudFront caches the static single-page application (SPA), but `/api/*` has caching disabled. The benchmark is therefore not an API edge-cache hit test. In the November released source `93984f61`, catalogue list/detail reads obtain display offers through `GroupDealQueryServiceImpl.findBestOpenDealsByProductIds` and `RedisOpenDealOfferCache`. One MGET reads the requested product keys; misses use one bounded PostgreSQL batch, and population uses a batched Lua call rather than a per-product round trip. Positive entries live 15–25 s and cached absences 5–10 s, with jitter. This avoids repeatedly deriving the same display offers and allows a newly available offer to appear sooner after an absence. Cached offers that have expired or filled are rejected; after-commit events evict affected product keys. The cache is a display optimisation, not authority to admit a purchase: settlement still checks the team under its lock. #perf-source("infra/swe5001/edge/cloudfront.tf", [Edge policy]) · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/backend/blocbuy-application/src/main/java/com/blocbuy/groupbuy/service/GroupDealQueryServiceImpl.java")[Released query service] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache.java")[Released cache].

That fallback protects the source result, not response time: repeated Redis waits can consume request capacity before PostgreSQL answers. Local fault tests reproduced this amplification and motivated PR \#784's failure-triggered backoff. It was absent from the 10 November image but reached the 11 November release; both new distributed campaigns still failed. No V2/V3 cache-hit ratio or controlled before/after database-load benefit was retained. The implementation explains the intended saving and staleness trade-off, not a measured AWS speedup. Appendix A.3 describes the browse design; its purchase counterparts in A.5/A.6 explain why a displayed offer cannot reserve a seat.

*Measured versus projected.* The measured lower bound is 200 RPS on one V2 monolith; the October V3 observations are about 280 RPS saturation on one commerce task and about 455 RPS served by three tasks during a generator-limited run. The November run's later minutes near 600 RPS remain part of a failed campaign, not validated capacity. Different revisions/topologies cannot form a controlled scaling pair. Calculated policy illustration only: six targets × 200 RPS = 1,200 RPS; this is neither a forecast nor validated capacity. The connection-budget report was missing in October and supplied in November, but arithmetic does not establish pool behaviour under load. Before any accepted repeat, recheck effective settings, explain cold/fault transitions and retain unchanged latency/error/drop gates. Validate one-to-four throughput separately. V4 queue backlog/age, drain time and empty dead-letter-queue acceptance also remain unverified on AWS (AISS-12, NFR-64). #perf-source("perf/k6/SCALING.md", [Connection-budget and repeat procedure]).

#pending[Liu Jin / Eric: review §4.1 against the dated AWS records and F1 before submission.]

]
