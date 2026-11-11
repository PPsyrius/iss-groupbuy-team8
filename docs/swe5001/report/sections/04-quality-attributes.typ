#import "../lib.typ": *

= Quality Attributes

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

== Availability

BlocBuy can keep the catalogue available while a payment dependency recovers. Its Amazon Elastic Container Service (ECS) tasks can run across availability zones, but the academic deployment still depends on a single-zone database, one Valkey node and one NAT instance. A zone failure can therefore interrupt purchases, sessions or outbound calls even while an application task survives. The proposed production changes are recorded under AISS-01 in §3.4. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/runtime/data-stores.tf")[Data configuration] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/report/registers.md")[Availability limitations].

*Application recovery.* On 16 October 2026, AWS showed two healthy commerce tasks in different zones. The Application Load Balancer routes to healthy targets, and ECS maintains the desired number of tasks. The deployment circuit breaker is configured to roll back an unhealthy deployment to the last completed deployment. Replacing a failed task restores capacity, while rolling back a release restores a known task definition. The team observed placement and a successful deployment. V5 still needs a controlled task stop under load; V6 describes the configuration only. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[AWS placement record] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/modules/ecs-service/main.tf")[ECS health checks and rollback] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/issues/459")[Drill scope].

#figure(
  table(
    columns: (1.0fr, 2.3fr, 1.8fr),
    table.header([Failure], [Observed locally, 15 October 2026], [What remains to verify on AWS]),
    [Identity unavailable], [Browse returned HTTP 200. START and a correctly signed webhook returned 500. The payment stayed in its initial state, with no committed order or membership.], [Stop the deployed identity tasks and measure the interruption and recovery. See AISS-03 and AISS-13.],
    [Identity restored], [Redelivery returned HTTP 200. Another delivery left one event, one membership and one order.], [Repeat against deployed services. The local identity and Stripe providers were substitutes.],
    [Malformed queue message], [ElasticMQ moved the message to its dead-letter queue after five receives. A subsequent valid message could be processed.], [Exercise the production consumer and observe its CloudWatch alarm. The local decoder used accelerated retry spacing.],
  ),
  kind: table,
  caption: [Failure isolation. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/evidence/20261015-local-failure-rehearsal/README.md")[dated rehearsal] used the real transaction and a local queue.],
)

*Message recovery.* In the distributed configuration, a committed outbox row survives a broker outage. The relay retries publication to Amazon Simple Notification Service (SNS). Its subscriptions deliver events to separate Simple Queue Service (SQS) queues. SQS moves repeatedly failing messages to a dead-letter queue. Redelivery after a successful consumer commit is safe because the event and consumer identify an existing processing record. Notifications can arrive later, and exhausted relay retries or dead-letter messages require operator attention. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0007-cross-service-events-sns-sqs.md")[Integration decision] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/notification/messaging/SqsNotificationEventConsumer.java")[Queue acknowledgement] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/modules/messaging/main.tf")[Queue and alarm configuration].

#block(breakable: false)[*Rebuilding the environment.* The 17 October 2026 three-service promotion created 115 runtime resources in its initial apply (520 s), passed its smoke suite (5 s) and destroyed the runtime (709 s). The entire up job took 31 min 37 s, above the 30-minute target. A second clean three-service session that day took 32 min 17 s for the up job. These are rebuild timings for a disposable environment; they establish neither a database restore time nor uninterrupted service during a zone loss. #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[Promotion run] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39014054529")[Second clean session] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/impl-verification-observability.md#v8-rebuild-from-zero")[V8 timing records].]

== Security
<sec-security>

BlocBuy layers organisation guardrails, federated access, restricted network paths, service-level authorisation and CI security gates. The 10 November 2026 SGT record verifies dedicated data-subnet placement and required connectivity for that session. These controls do not establish end-to-end encryption or complete least privilege: the origin path uses HTTP and some secrets enter Terraform state.

#let security-source(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

#[
#show raw: set text(size: 8.5pt)
#show table.cell: set block(breakable: false)

=== Assets, threats and assessment scope

The assessed deployment is an academic demonstration, not a trading service: there is no real inventory offered for sale and no real payment acceptance. The seeded catalogue supplies test data; Stripe test-mode flows demonstrate payment integration rather than commercial settlement. That reduces direct financial exposure, but cloud credentials, developer accounts, session data and deployment authority still require protection. It is not a blanket acceptance of the residual risks below.

#figure(
  table(
    columns: (1fr, 1.8fr, 1.7fr),
    table.header([Asset / threat], [Implemented or configured response], [Evidence and remaining exposure]),
    [Deployment authority / unauthorised release], [Reviewed promotion, branch checks and environment-scoped OIDC roles restrict the path from source to cloud changes.], [§5 records GitHub readback and dated jobs. Broad apply privileges and unconfirmed developer-account controls remain.],
    [Payment and resource integrity / forged callback or another buyer's data], [Signature-verified webhooks, server-fixed purchase terms and application ownership checks complement login.], [Appendix A.6, A.10 and A.12 trace the design; §4.2 distinguishes local failure tests from AWS acceptance.],
    [Sessions and internal services / compromised account or peer], [Shared role snapshots and network refusals bound access.], [Local authorisation regressions are retained; an internal peer still lacks authenticated service identity (AISS-04).],
    [Secrets and personal data / interception or privileged read], [TLS on viewer/data connections, encrypted stores and restricted state access protect different boundaries.], [@tbl-security-boundaries identifies the HTTP gap; authorised state readers still see some secrets (AISS-08/09).],
    [Auditability / inability to reconstruct misuse], [Dated operational receipts retain selected actions and results.], [They are not continuous security-event retention; the newer governance handoff below still needs its retained basis.],
  ),
  kind: table,
  caption: [Threat-to-control argument for the demonstration scope. Configuration and design are not penetration-test evidence; detailed mechanisms and dated checks follow.],
) <tbl-security-threats>

=== Organisation guardrails and human access

Verified historically on AWS, not rechecked in this drafting session: the 16–17 October 2026 #security-source("docs/deploy/aws-account-governance/README.md", [account-governance audit]) records the project as a member account under a separately administered AWS Organization. IAM Identity Center gives team members single sign-on (SSO) and one-hour sessions through administrator or power-user permission sets. Organisation administration remains with the management-account owner. This separates workload administration from the authority to relax organisation-wide controls (AD-14).

Identity Center sign-in has multi-factor authentication (MFA) enabled, owner-confirmed by Pan on 9 November 2026. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-source-control-delivery/README.md#owner-confirmation-identity-center-mfa")[confirmation record] is owner testimony, not an independent AWS audit. MFA addresses account sign-in; it does not reduce the permissions available after sign-in.

The October access description is not a November least-privilege finding. Lim's #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/756")[8 November owner handoff] reports an empty developers group, administrator access for members of the admins group, and no configured CloudTrail trail, GuardDuty, Config or Access Analyzer in either account. These are attributable owner-reported observations, not independently reverified here. No configured trail is not the same as no CloudTrail event history. #pending[Lim / Liu Jin: supply or reconcile the retained basis for the 8 November privilege and audit-service findings, including account/region scope, before treating them as the current governance baseline.]

The recorded service control policies (SCPs) are permission ceilings, not grants. `Org-Baseline` blocks member-account exit/closure, creation of a separate Identity Center instance and protected SSO-role changes. `Region-Pin` restricts regional work to Singapore with specified global/scoped exceptions. `Sandbox-Guardrails` restricts costly services and instance classes; it does not impose a spending cap. SCPs also constrain member-account administrator sessions, but not the management account or service-linked roles. On 16 October, project-account probes allowed Singapore EC2 inspection and CloudFront metric reads in `us-east-1`, denied corresponding regional calls in `eu-west-1`, and denied tagging the caller's protected SSO role. Account-exit and Identity Center creation were checked in policy content, not attempted destructively. #security-source("docs/swe5001/evidence/20261016-scp-consolidation/README.md", [Dated SCP probes]).

Two qualifications prevent an overbroad claim. First, the governance audit records two project-account IAM users, including one active access key; federated team/CI access does not mean the entire account has no long-lived credentials. Second, an SCP deny cannot be overridden by a member-account IAM allow. The October CloudFront metric denial was corrected and probed in the #security-source("docs/swe5001/evidence/20261016-scp-consolidation/README.md", [dated SCP consolidation]); the unavailable `us-east-1` CloudTrail read in the November report investigation is a separate audit-access limit. Neither proves unrestricted global-service access. #security-source("docs/deploy/aws-account-governance/README.md", [IAM-user exceptions]).

=== Machine identities, permissions and secrets

Configured in the source inspected on 9 November 2026: GitHub Actions uses OpenID Connect (OIDC) to obtain temporary AWS role credentials instead of stored AWS access keys. Trust checks both audience `sts.amazonaws.com` and the exact environment subject built from immutable repository/owner IDs. The `swe5001-plan` environment reaches the foundation-plan role; `swe5001-demo` reaches separate apply/deploy roles and admits only `prod`. The branch restriction is a GitHub environment control, not a branch name embedded in that environment-scoped OIDC subject. GitHub readback on 9 November confirmed the demo environment has a selected `prod` branch policy and no required environment reviewer; the promotion PR is the review gate, not a second environment approval. #security-source("infra/swe5001/foundation/github-oidc.tf", [Role trust and policies]) · #security-source("infra/swe5001/foundation/main.tf", [Immutable subject construction]) · #security-source("docs/swe5001/impl-github-configuration.md", [Approval model]).

The configured plan role can read foundation state but explicitly denies other state-object reads, including secret-bearing runtime and edge state. Apply-created workload roles must remain under `/swe5001/` with the operator-managed permissions boundary. That boundary caps access; each execution/task role still needs its own grant. A shared ECS execution role reads the configured Parameter Store scopes for startup injection, while service task roles carry runtime permissions such as event publication or queue consumption. These are scoped controls, not proof that every permission is least-privilege: the apply role retains broad managed policies, including `CloudWatchFullAccessV2` (AISS-15). Dedicated live secret-denial and cross-stack isolation checks remain outstanding under NFR-51. #security-source("infra/swe5001/foundation/boundary.tf", [Workload boundary]) · #security-source("infra/swe5001/modules/ecs-cluster/main.tf", [Shared execution role]) · #security-source("docs/swe5001/HANDOFF.md", [Recorded isolation limits]).

Application credentials are configured as AWS Systems Manager (SSM) Parameter Store `SecureString` values and injected into ECS through parameter references, not copied into committed task configuration. Database passwords use ephemeral generation and Terraform write-only arguments. In contrast, the Valkey AUTH token is retained in runtime state and the origin secret is retained in edge/runtime state. Encrypted storage and restricted state access do not make those values invisible to authorised state readers (AISS-09). The origin secret is operator-seeded; rebuilding runtime is not evidence that it rotated. Raw plans/state and full environment dumps must therefore remain private. #security-source("infra/swe5001/modules/rds-postgres/main.tf", [Write-only database passwords]) · #security-source("infra/swe5001/modules/cache/main.tf", [Valkey token]) · #security-source("infra/swe5001/edge/main.tf", [Persistent origin-secret reference]) · #security-source("infra/swe5001/runtime/ecs.tf", [Task secret injection]).

=== Network and database isolation

Configured, not freshly probed on AWS: the public ALB accepts HTTP from CloudFront's origin-facing managed prefix list; service forwarding additionally requires the matching `X-Origin-Secret` header, with a default refusal. ECS tasks have no public IP and accept their service port from the ALB or peer-service security group. An explicit ALB rule blocks `/internal/*` and `/api/internal/*`; CloudFront sends only `/api/*` to the API origin. These restrictions reduce direct external access, but neither the prefix list nor the shared header authenticates an individual internal caller. A compromised peer remains inside the trusted network (AISS-04). #security-source("infra/swe5001/runtime/security-groups.tf", [Network rules]) · #security-source("infra/swe5001/runtime/alb.tf", [Listener refusals]) · #security-source("infra/swe5001/modules/ecs-service/main.tf", [Task placement and forwarding]).

*Network separation was verified on AWS on 10 November 2026 SGT.* The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated record] binds AD-15 to released source `93984f61`: all four running application-task ENIs used the application subnets; RDS and Valkey managed ENIs used the separate data subnets (`10.50.20.0/24`, `10.50.21.0/24`). Effective data routes were local-only. PostgreSQL ingress was TCP 5432 from the service/database-init groups; Valkey ingress was TCP 6379 from the service group only. Public health/catalogue and operator-observed signed-in identity/notification requests succeeded. This resolves AISS-07's recorded placement/connectivity gap (NFR-70), not negative packet-isolation or failover acceptance. The runtime was later destroyed by scheduled cleanup; these are historical observations, not current cloud placement. See §3.2 and @fig-d6-network.

Source inspection also confirms a database and app/owner roles per service on one RDS server. The full Flyway migration stream runs in every database; the grants callback restricts each app role to its service-owned tables. This is an access-control boundary, not a claim that foreign tables are absent or that services have separate database servers. The 12 October local record reports foreign-table permission denials; that is not a fresh restricted-role HTTP test on ECS. Flyway owner credentials are also injected into the service process for migration, so the app-role restriction alone is not a complete process-compromise boundary. #security-source("backend/blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql", [Grant/revoke callback]) · #security-source("docs/agile/sprint-04/log/2026-10-12.md", [Local grant tests]) · §3.2.3.

*Fresh metadata check, 11 November 2026.* Before the larger-task V3 r2 campaign, the committed read-only subnet collector and separately hashed local reviewer again matched all four application-task ENIs to the application subnet set and both store ENIs to the disjoint data subnet set. Local-only data routes and restricted security-group sources passed. This supplements the historical result with new placement/control metadata; signed-in identity/notification connectivity and negative packet isolation were not retested by that collector. RDS was now `db.t4g.small`, not the historical micro class, with PostgreSQL 18.3 unchanged. The session's CLI used the existing Identity Center `AdministratorAccess` assignment: a read-only observer command allowlist is not IAM least privilege. No IAM permission/session-policy change is claimed. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v3-r2/prechecks/subnet-review.json")[Fresh subnet review] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/README.md")[Session provenance and boundaries].

=== Encryption boundaries

The following are source/configuration observations as of 9 November 2026, not new transport probes. Transport Layer Security (TLS), encryption at rest and source restrictions address different threats; one does not establish the others.

One narrow control was observed directly: on 10 November SGT, three anonymous static HTTPS responses at Singapore edge `SIN3-P4` each matched all nine expected security-header values. The sampled root, login fallback and JavaScript asset establish those response values, not API coverage, browser CSP compatibility or global propagation. The HTML/asset metadata did not establish a new November frontend build. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-spa-security-headers/README.md")[Retained header observations].

#figure(
  table(
    columns: (1.1fr, 2.2fr, 1.7fr),
    table.header([Boundary], [Configured protection], [Limit]),
    [Browser → CloudFront], [HTTP redirects to HTTPS. The edge applies shared content-security, framing, content-type, referrer and transport-security headers.], [Viewer minimum remains TLSv1, not TLS 1.2-only. No web application firewall (WAF) is configured.],
    [CloudFront → ALB → task], [CloudFront origin is `http-only`; the ALB listener and target group use HTTP. Origin prefix-list/header restrictions apply.], [No encryption of these HTTP hops. The header restricts forwarding but supplies no confidentiality (AISS-08).],
    [Task → PostgreSQL], [RDS sets `rds.force_ssl=1`; task JDBC URL uses `sslmode=require`. Database storage encryption is enabled.], [This establishes the declared encryption requirement, not a claim of JDBC certificate/hostname verification or a fresh live TLS check.],
    [Task → Valkey], [Transit and at-rest encryption enabled, AUTH token required, Spring Redis SSL enabled.], [Token persists in Terraform state; authentication does not remove state-reader exposure.],
    [Domain events and static storage], [Domain SNS topic uses `alias/aws/sns`; SQS consumer/dead-letter queues use managed server-side encryption. Consumer queue policy denies insecure transport. SPA bucket uses AES256 server-side encryption with public access blocked and distribution-scoped origin access control.], [Alarm-metadata SNS topic has no encryption key. Managed encryption does not prove consumer authorisation.],
  ),
  kind: table,
  caption: [Configured encryption and access boundaries. Sources: #security-source("infra/swe5001/edge/cloudfront.tf", [edge policy]), #security-source("infra/swe5001/modules/ecs-service/main.tf", [HTTP target]), #security-source("infra/swe5001/modules/rds-postgres/main.tf", [RDS]), #security-source("infra/swe5001/runtime/ecs.tf", [client SSL settings]), #security-source("infra/swe5001/modules/cache/main.tf", [Valkey]), #security-source("infra/swe5001/modules/messaging/main.tf", [messaging]) and #security-source("infra/swe5001/edge/s3.tf", [SPA bucket]).],
) <tbl-security-boundaries>

=== Application and delivery controls

Implemented in the inspected source: Sa-Token checks login on protected `/api/v1/**` routes, while catalogue/invite GETs, OAuth entry/callbacks and signature-authenticated provider webhooks have defined public paths. Business services still own resource-ownership checks; login alone is not authorisation. Outside identity, `SessionStpInterface` reads one validated role/permission snapshot per request; a missing or malformed snapshot supplies no permissions. The 12 October record reports local Redis-backed authorisation regressions, not current authenticated ECS acceptance (NFR-58). Internal identity endpoints remain distributed-only and hidden from the public contract, but hiding documentation is not caller authentication. #security-source("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SaTokenConfig.java", [Login/public-path policy]) · #security-source("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SessionStpInterface.java", [Permission snapshots]) · #security-source("backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/internal/InternalUserController.java", [Internal endpoints]) · #security-source("docs/agile/sprint-04/log/2026-10-12.md", [Dated local verification]).

Configured task hardening includes a read-only container root filesystem and a writable `/tmp` volume. The image-security control and its dated executions are explained once in §5.2 and @fig-d10-cicd-pipeline: the exact built image is scanned before SWE5001 export/publication, and fixable HIGH/CRITICAL findings fail that job. Dependency, secret and static-analysis gates are separate controls. Unfixed/lower-severity exclusions, the dev fast path without a fresh image scan, disabled CodeQL and event-selected rather than complete-history secret scanning remain qualified there. A passing scan or promotion does not establish penetration-test coverage or prove the dedicated IAM denial paths. #security-source("infra/swe5001/modules/ecs-service/main.tf", [Container hardening]) · NFR-02, NFR-03 and NFR-51.

=== Residual risks and verification still required

The owning decisions and proposed roadmap remain in §3.4; this table states the security consequence rather than treating a report draft as a remediation.

The most consequential gaps are privileged control-plane access, secret-bearing state and the unencrypted origin path: they can affect deployment authority or disclose credentials/data. Missing authenticated peer identity increases the impact of a compromised task. Audit-service coverage affects detection and investigation and remains subject to the owner reconciliation above. Unencrypted alarm metadata is a narrower confidentiality concern and does not carry the same consequence as a credential-bearing path. This ordering prioritises the technical exposures; the team has not yet confirmed which risks it accepts for the course or which must block real-user operation.

#figure(
  table(
    columns: (0.7fr, 2.1fr, 2.2fr),
    table.header([AISS], [Current limit], [Required follow-up, not implemented by this report]),
    [04], [Internal calls rely on network trust, without a service token or mutual TLS.], [Add authenticated service identity; test denial from an untrusted peer as well as an external source.],
    [07 (resolved)], [Placement/connectivity verified in the 10 November SGT session, not a failover or negative packet test.], [Retain the dated evidence and recheck effective placement for a future deployment; do not reuse destroyed subnet IDs as current state.],
    [08], [Origin and ALB-to-task HTTP hops are unencrypted.], [Design and deploy verified TLS for the relevant hops; do not treat a prefix list or secret header as encryption.],
    [09], [Origin secret and Valkey token are exposed to authorised state readers.], [Restrict state/plan access, rotate through an approved procedure and adopt a write-only/alternative secret path where supported.],
    [15], [Apply-role CloudWatch privileges are not fully fenced. The November CloudTrail read limit is separate from the corrected October metric SCP denial.], [Narrow permissions and test intended cross-stack denials; preserve audit-access limits rather than expanding grants to recover history.],
  ),
  kind: table,
  caption: [Dated security limitations; IDs and owners follow the report's AISS register. Proposed follow-up is not implemented remediation.],
)

#pending[Liu Jin: review §4.3 against the governance record, infrastructure configuration and §3.4 before submission.]

]

== Extensibility and Maintainability

// Section 4.4 (owner: Lim). Links pinned to dev at b80debcd (8 November 2026).
#let lim-src(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

The platform is extended at three seams: a new event consumer, a new service and a new public operation. Each is a small, bounded edit that the pipeline checks. The platform stays maintainable because one codebase, with boundaries that fail the build when crossed (AD-18), produces both the monolith and the three services. This section answers briefing Q2 (common services and reusable frameworks) and Q3 (the benefit of building a new use case on the platform). The platform's producers and consumers are described in §3.1.4.6.

=== Three extension seams

#figure(
  table(
    columns: (0.9fr, 1.6fr, 2.1fr, 1.6fr),
    table.header([Change], [What a developer edits], [What comes with it], [What checks it]),
    [New event consumer (for example analytics or audit)], [Its subscription: one element in the `consumers` set and one filter line in the messaging module, plus its dead-letter queue name on the dashboard. Its checks: the consumer count and filter assertions in `runtime/tests/messaging.tftest.hcl` and a routing probe in `scripts/messaging-check.sh`. Its worker: new code in the consuming service. Zero producer files], [Its own SQS queue and dead-letter queue (settings in §3.2.3), a queue policy that accepts only the domain topic over TLS, and a filtered subscription], [Today `terraform test` expects exactly two consumers and asserts the filters of `notification` and `audit`, and the readiness script probes routing to those two queues before the relay starts\; a new consumer fails the test until it is added to both],
    [New service], [One entry in the services map (19–37 lines), one ECR repository name, one entry in the image workflow's service list and the start-up script, its database grants, and a thin assembly module registered in `backend/pom.xml`], [Everything the golden-path `ecs-service` module creates (below), plus the shared COLA modules], [ArchUnit rules, image scan, Terraform plan with Checkov],
    [New or changed public operation], [A controller in the shared adapter module (`blocbuy-adapter`, part of the exported application), owned by one service; the committed OpenAPI contract is regenerated], [The generated TypeScript client in the web application], [`contract-check` fails on drift and on incompatible changes; the client generation breaks the front-end type check if an operation disappears],
  ),
  kind: table,
  caption: [Extension seams and their guards (configured in the repository on 8 November 2026).],
) <tbl-seams>

*Principles behind the seams.* Each seam applies a named principle or pattern that an artefact enforces (@tbl-seam-principles).

#let lim-at(line) = "backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java#L" + str(line)
#figure(
  table(
    columns: (0.75fr, 2.1fr, 1.95fr),
    table.header([Seam], [Principle or pattern], [Enforcing artefact]),
    [New event consumer], [Open-Closed Principle at architecture level: new subscribers extend the bus, and the producer `commerce-svc` stays closed for modification. Publish–subscribe across services (SNS fan-out to SQS); Observer in process: the publisher does not know its `@TransactionalEventListener` subscribers, such as #lim-src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/integration/outbox/IntegrationOutboxWriter.java")[`IntegrationOutboxWriter`]], [`SnsOutboxEventTransport` holds only the topic ARN; `terraform test` asserts each subscription filter],
    [Layers in every service], [Dependency Inversion Principle: domain and application depend on port interfaces declared in the domain, which infrastructure implements (`OutboxEventTransport` by `SnsOutboxEventTransport`)], [ArchUnit rules #lim-src(lim-at(67))[`domain_is_framework_free`], #lim-src(lim-at(91))[`application_uses_ports_not_tech`] and #lim-src(lim-at(132))[`domain_has_no_outward_dependencies`] (all rules: AD-18)],
    [Coupling between services], [One synchronous call, `commerce-svc` → `identity-svc`, through the `UserAppService` port, implemented remotely by `RemoteUserAppService` (AD-06); everything else is events], [#lim-src(lim-at(247))[`commerce_reaches_identity_only_through_user_port`]],
    [Cohesion of each service], [Each service's persistence code uses only its own mappers. An assembly holds only service-specific code: only its main class in `identity-svc` and `notification-svc`\; in `commerce-svc` also the three classes of the remote identity client], [#lim-src(lim-at(293))[`*_persistence_is_owned`] (three rules); per-service database grants],
  ),
  kind: table,
  caption: [Seam, principle or pattern, and enforcing artefact (code at b80debcd, 8 November 2026).],
) <tbl-seam-principles>

*One liability.* The third seam breaks the Open-Closed Principle. All 14 controllers live in `backend/blocbuy-adapter`, which `blocbuy-bootstrap` pulls into all three images, so a new operation for one service modifies this shared module and changes every image. Only ownership by convention and the contract check bound it\; separate source trees were rejected for now (AD-18, option b).

*A new consumer leaves the producer untouched.* `commerce-svc` publishes every integration event to one SNS topic and knows no queue (`SnsOutboxEventTransport` addresses only the topic). A consumer's subscription is therefore one `for_each` element: the audit subscription is the set element `"audit"` plus a filter on event types that start with `Team`. The audit queue was created with the notification queue in the same pull request, #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/487")[\#487] (14 October), and it has no worker yet. It shows the property by construction, not as a later addition measured against a before state. On 17 October on AWS, the readiness check of the promotion confirmed that a `TeamSucceeded` test event reached the audit queue and that a `PaymentSucceeded` event did not. #lim-src("infra/swe5001/modules/messaging/main.tf")[Messaging module] · #lim-src("infra/swe5001/scripts/messaging-check.sh")[readiness check] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[promotion run 39008208244].

*A new service inherits its runtime from one module.* All three services are instances of the golden-path module `infra/swe5001/modules/ecs-service` (454 lines), created by two `for_each` calls. From it and the runtime stack around it, every service gets:

- a log group, a task role under a permissions boundary, and an ARM64 Fargate task definition with a read-only root file system and a writable `/tmp`;
- an image pinned by digest (the module rejects a tag), with secrets injected from SSM Parameter Store as references (AD-17);
- an ALB target group and a listener rule that forwards only requests carrying the CloudFront origin secret (AD-09);
- an ECS service with the deployment circuit breaker and automatic rollback (configured, not exercised: V6, §2.2), zone rebalancing, and the regular-Fargate and Fargate Spot mix (AD-08);
- a series in the services row of the CloudWatch dashboard, which the observability module adds for every service's target group.

Two things are deliberately outside the module: the auto-scaling policy, because each service scales on a different signal (requests per target for `commerce-svc`, queue backlog for `notification-svc`), and the alarms, which belong to the messaging module. #lim-src("infra/swe5001/modules/ecs-service/main.tf")[`ecs-service` module] · #lim-src("infra/swe5001/runtime/services.tf")[services map].

*The shared code comes from the COLA modules, not from a starter library.* The proposal planned a `blocbuy-platform-starter` that would package the response envelope, authentication, outbox and idempotent consumption. What was built is thinner. `blocbuy-platform` has three classes in 93 lines (`PlatformAutoConfiguration`, `TopologyProperties` and `TraceIdPropagationInterceptor`): they stop a service assembly from starting unless the topology is `distributed`, and propagate the `X-Trace-Id` header on outbound calls. The module also carries the per-service database grants as a SQL resource. The rest is inherited from the shared COLA modules through `blocbuy-bootstrap`: the `Result` envelope and `ErrorCode` enums, Sa-Token roles, `IntegrationOutboxWriter` and the relay, the consumer ledger (`sys_consumed_message`) and `LeasedJobRunner` (AD-16). As a result, each service assembly is small: `identity-svc` and `notification-svc` each have 26 lines of main Java and a 20-line configuration file, and `commerce-svc` has 330 lines, mostly its remote identity adapter. #lim-src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008] · #lim-src("backend/blocbuy-platform/src/main/java/com/blocbuy/platform/PlatformAutoConfiguration.java")[`PlatformAutoConfiguration`].

*Contracts evolve without a joint release.* The public API is one OpenAPI 3.1 contract (`contracts/openapi.json`, 31 operations in 27 paths, 8 November 2026). In the `contract-check` job, which gates the required `docker` check, CI regenerates the contract from the built application and fails the pull request if it differs from the committed copy. The job also runs `openapi-diff` against the base branch and fails on an incompatible change unless the pull request carries the `contract-change-approved` label. Events follow the same rule: the envelope carries `eventType` and `version`, a breaking change is a new version published alongside the old one, and a consumer acknowledges a type or version it does not know without acting on it. #lim-src(".github/workflows/ci-backend.yml")[`ci-backend.yml`] · #lim-src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007].

*What a new use case gains (Q3).* A new use case built on the platform starts with sign-in and roles, the error format, trace propagation, the outbox and duplicate-protection code (switched on per service, with its own publish or receive permission), a runtime with rollback (configured, not exercised: V6, §2.2), a dashboard series and a guarded API contract. Built from scratch, each of these would be designed, built and secured again. The evidence for this is the size of the edits above. Not measured: the team did not time the addition of a fourth service, so the effort saved is shown by construction, not as a measured number.

=== Maintainability

#figure(
  table(
    columns: (1.5fr, 2.6fr),
    table.header([Measure (8 November 2026)], [Value]),
    [Codebase], [One Maven reactor with 10 modules: six COLA layers, `blocbuy-platform` and three service assemblies; about 20,800 lines of main Java],
    [Executable boundaries], [17 ArchUnit rules (8 layer and style, 9 service boundary) and 2 contract and SQL guards (AD-18)],
    [Tests], [248 test classes with about 1,581 test methods; parameterised tests counted once],
    [Quality gate], [SonarCloud on every backend pull request, at least 80% coverage on new code, fed by the JaCoCo reports of the aggregate and all three services],
    [Topologies from one commit], [`blocbuy.topology=monolith` (default; the SWE5006 deployment) or `distributed` (ECS only): same commit and migrations, one image per assembly],
  ),
  kind: table,
  caption: [Maintainability measures, counted in the repository. Sources: #lim-src("backend/pom.xml")[`backend/pom.xml`]\; #lim-src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java")[`ArchitectureTest`].],
) <tbl-maintainability>

The trade-off of these executable boundaries, including the duties they place on every change, is stated in AD-18 (§3.3).

*Evolution paths.* Three likely next changes reuse the seams above:

- *Extract payment* (AISS-02): the outbox already publishes payment facts, so payment can move behind its own assembly, with a saga for fulfilment and refund replacing today's shared settlement transaction.
- *Add a seed* (a new group-buying vertical, §6.2): a new service from the map entry and an assembly module, reusing identity, notification and settlement.
- *Add analytics* (§6.5): a read-only consumer on the event topic, built exactly like the audit queue.
