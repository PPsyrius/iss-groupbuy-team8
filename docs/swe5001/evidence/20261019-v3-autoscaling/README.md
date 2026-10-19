# V3 HTTP auto-scaling session — 2026-10-19

Refs #457 and #458. This is a manual AWS session run as IAM Identity Center user `liujin` from a local macOS workstation, not a GitHub deployment run. Commerce scaled out from **1 to 3 tasks** under k6 load, and once three tasks were running the load balancer served about **455 requests per second with a server-side p95 of 13–50 ms**. The run is **not valid V3 acceptance evidence**: the load generator could not deliver the planned 600 requests per second, dropped 155,554 scheduled iterations, and `analyze-v3.mjs` rejects the run (`Generator drops invalidate V3 evidence`, exit 1). Neither issue is closed by this record.

## Provenance and scope

| Item | Observed value |
| --- | --- |
| Account / Region | `402178232802` / `ap-southeast-1` |
| Public target | `https://d3lvq02o2e7tcn.cloudfront.net` |
| Operator identity | IAM Identity Center `AdministratorAccess` session, from a local AWS CLI profile (not CloudShell) |
| k6 tooling source (`TEST_SHA`) | `9512f1e06709ea651bc74e7531250511c01b0d83`, clean checkout |
| Deployed commerce source (`IMAGE_SHA`) | `5e83d63e631f69a83cb3098221eec5d37f39c0b7`, from ECR tag `prod-5e83d63e631f69a83cb3098221eec5d37f39c0b7-run-1` and the [environment run 39198123242](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39198123242) on `prod` at the same commit |
| Commerce digest | `sha256:3cb6cc2bd7cfcf67ce11bd5bbeca694ca15e50595e0c6a7d3b882b4fb0352411`, task definition `blocbuy-swe5001-commerce-svc:15` (0.5 vCPU, 1 GB) |
| Scaling policy | `ALBRequestCountPerTarget`, target 12000 requests/target/minute, bounds 1..6 during the run (`KNEE_RPS=200`) |
| Generator | Local macOS workstation, Docker Desktop (10 CPUs, 16 GB), k6 `grafana/k6:1.6.1@sha256:a5ad6bc0…8d8e1`, 100 preallocated / 400 maximum VUs |
| Run label | `20261019-v3-evidence` |

The connection-budget report (`connection-budget.sh`) was **not** run before this session. The database CPU stayed below 19% and connections peaked at 50, but that is an observation, not a budget.

## What the operator changed

The demo floor was lowered for the run and restored afterwards, with the AWS CLI rather than Terraform ([timeline](v3-timeline.txt)):

1. 07:43:42 UTC: `register-scalable-target` min 1 / max 6 and `update-service --desired-count 1` on `commerce-svc`, then waited for a stable 1/1 deployment ([state before](pre-run-commerce-service.json), [scaling target before](pre-run-commerce-scaling-target.json)).
2. 08:20:03 UTC: `register-scalable-target` min 2 / max 6; suspension flags false. The service was at 3 tasks and is expected to scale in to 2 on its own. Two-AZ placement after scale-in was not checked in this record.

The next `env-up`/`down` reconciles these values with Terraform.

## Results

The [preflight](v3-preflight.json) passed k6 setup checks: commerce 1/1 on one completed deployment, live digest equal to the reviewed digest, route bound to the commerce target group, scaling 1..6 at target 12000. The [service observer](v3-service-observations.jsonl) sampled ECS every 15 seconds from 07:44 to 08:19 UTC with empty stderr: 45 samples at 1 task, 9 at 2 and 59 at 3.

**Scale-out.** Two successful [Application Auto Scaling activities](v3-scaling-activities.json), both triggered by the `AlarmHigh` target-tracking alarm:

| Activity start (SGT) | Desired count | Fulfilled (SGT) |
| --- | --- | --- |
| 15:55:02 | 1 → 2 | 15:56:08 |
| 15:58:02 | 2 → 3 | 15:59:05 |

The activity records state only the new count; the previous count in [scale-outs](v3-scale-outs.json) comes from the sequence, with the first step taken from the 1-task floor confirmed by the observer. Commerce stayed at 3 tasks: at about 455 requests per second, each target received about 9,200 requests per minute, below the 12,000 target, so the policy had no reason to add a fourth.

**Server-side view (load balancer, commerce target group).** [Per-minute ALB numbers](v3-alb-per-minute.tsv) and [graphs](20261019-v3-edge-alb-latency.png):

| Window (SGT) | Tasks | ALB requests/s | ALB p50 | ALB p95 |
| --- | --- | --- | --- | --- |
| 15:45–15:49 | 1 | 64–192 | 6–8 ms | 15–76 ms |
| 15:52–15:55 | 1 | 272–286 | ~1.1 s | ~1.9 s |
| 16:01–16:10 | 3 | 425–461 | 5–7 ms | 13–50 ms |

One 0.5 vCPU commerce task saturated at about 280 requests per second (CPU 90–100%, [utilisation graph](20261019-v3-services-utilisation.png)). Spikes at 15:56 and 15:59 coincide with new tasks starting.

**Client-side view (k6).** [Summary](v3-summary.json), [per-minute k6 numbers](v3-k6-per-minute.tsv), [console](v3-k6-console.txt):

| Metric | Value |
| --- | --- |
| Requests completed | 570,445 (all-run average 317/s) |
| Errors | 2 (0.0004%), both commerce 5xx during the 15:59–16:00 scale-out |
| All-run p95 / p50 | 1,671 ms / 592 ms (gate p95 < 800 ms: **failed**) |
| Dropped iterations | 155,554 (gate: zero; **failed**) |
| k6 exit | 99, thresholds `browse_latency` and `dropped_iterations` crossed |

While three tasks answered in 5–7 ms (ALB p50), k6 measured a p50 of about 490 ms. That extra time sits between the generator and the load balancer: the workstation network, Docker Desktop's network path, or CloudFront. With 400 VUs and that round trip, the generator could not exceed about 460 requests per second. Its container CPU peaked at 56% ([samples](v3-generator-stats.jsonl)), so the limit was not generator CPU. The exact cause was not isolated.

## What this run does and does not show

- **Shows:** target tracking scales commerce out under HTTP load (1 → 3 in about four minutes, triggered by the alarm, no manual scaling); once scaled, the service handled about 455 requests per second with server-side p95 at or below 50 ms; one 0.5 vCPU task saturates at about 280 requests per second.
- **Does not show:** 600 requests per second (300% of the baseline), the event-relative p95 gate, one-to-four task linearity, or a connection budget. The analyzer rejects the run because of generator drops, and its failure stands; this record does not relabel it green.
- **To repeat it validly:** run the generator closer to AWS (for example a sized EC2 instance in `ap-southeast-1`) or raise the reviewed VU maximum, run the connection budget first, and keep the analyzer's gates unchanged.

## Files

| File | Content |
| --- | --- |
| `v3-preflight.json` | Route-bound deployment snapshot used by k6 setup |
| `v3-summary.json` | k6 report: context, embedded preflight, metrics and thresholds |
| `v3-k6-per-minute.tsv`, `v3-alb-per-minute.tsv` | Per-minute client and server views |
| `v3-service-observations.jsonl` | ECS commerce observations every 15 seconds |
| `v3-scaling-activities.json`, `v3-scale-outs.json` | Raw and normalized scale-out records |
| `v3-generator-stats.jsonl` | k6 container CPU/memory every 30 seconds |
| `v3-analysis-result.txt` | Analyzer result |
| `20261019-v3-*.png`, `dashboard.json` | Dashboard widgets rendered for 15:40–16:20 SGT |

Raw k6 samples (2 GB) are kept privately on the operator's workstation and not committed.
