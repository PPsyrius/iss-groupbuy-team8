# SWE5001 HTTP load tooling (#458)

This first #458 slice supplies the read-only V2/V3 HTTP driver, machine-checked deployment preflight, fail-closed V2 analysis and a connection-budget calculator. It does not supply the V4 expiry seed/burst driver, automate V3 event-window analysis, run AWS tests, change scaling, persist GitHub settings or close #458/#457. Results from the disposable fixture test the generator, not BlocBuy capacity, and the calibration CLI refuses them. Do not load-test shared SWE5006 `dev`.

## Local verification (no AWS credentials)

Requirements: Node 24 and Docker with Linux containers. No npm installation is needed. Pull the images before the offline integration suite; the fixture network has no external routing and exposes no host ports.

```bash
docker pull node:24-bookworm
docker pull postgres:18
docker pull grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1
node --test perf/k6/lib.test.mjs
node --test perf/k6/integration.test.mjs
# Optional ~12-minute generator/summary regression, still only a disposable HTTP fixture:
RUN_FULL_V2=1 node --test perf/k6/integration.test.mjs
```

The runner is pinned to k6 1.6.1 and its image digest because the analyzer consumes its legacy `handleSummary` metric schema. Do not enable the new machine-readable summary format or substitute a newer major without updating the regression tests. Node runs analysis only; the driver itself runs in k6, not Node. Integration tests remove only their own randomly named fixture containers, internal networks and temporary output directories. The short suite exercises all ten rate steps using a one-second local-fixture-only schedule; remote runs reject that override. Full-duration fixture validation uses the real ten-minute schedule but still cannot establish application capacity.

## Prepare evidence before starting a billable session

Use the [environment session runbook](../../docs/swe5001/runbook-environment-session.md) and an approved private plan; do not use these scripts to bypass startup or teardown. Verify no concurrent environment/promotion operation. Images must be reviewed, scanned, SHA-identified and deployed by digest. Keep credentials, cookies, Terraform state/plans, recipient addresses and private application data out of results and PRs.

- Run tooling from a clean, reviewed checkout and record its committed SHA (`TEST_SHA`) separately from the deployed source SHA (`IMAGE_SHA`), actual ECR digests, UTC interval, workflow run URL or manual-session evidence link, task definitions and load-generator resources/location. Do not label uncommitted tooling as its parent commit. An environment variable is an operator assertion, not proof of deployment: retain ECS/task-definition snapshots alongside it.
- V2 follows the explicit #458 scope: `topology=monolith`, one running monolith task, stable deployment, same existing demo seed. The distributed commerce scaling policy does not exist in that topology. Verify that no other scaling target/process can change the measured service's count. Capture task counts before/during/after; abort/discard a run if they change. Do not confuse `commerce_min_capacity=1` with scaling suspension or with reducing existing counts.
- V3 uses `topology=distributed`: begin at one commerce task with HTTP scaling enabled. Confirm scaling bounds and calibrated request target before load. Save task-count/time-series and deployment-event evidence throughout. Restore the demo floor to two and verify both AZs separately afterward; never infer placement from desired count.
- Do not run Terraform/environment workflows, messaging drills or unrelated workload during a measurement. Run k6 on the local workstation, not resource-constrained CloudShell; a saturated generator is not server capacity. Keep the operator's CloudShell active, monitor the session and confirm SNS emails during each distributed startup.
- Before V3, record the real database connection budget below. A monolith baseline is only a candidate for the distributed target; V3 must validate its transfer. This PR never changes a pool size or silently caps a task maximum to make a budget fit.

## V2 baseline

### Capture and validate the deployment before HTTP

After an approved session is running, collect the following in the operator's clean reviewed checkout, with the normal AWS account/region/state-bucket environment. This performs read-only AWS calls, starts no tasks and changes no scaling settings:

```bash
umask 077
bash infra/swe5001/scripts/http-preflight.sh v2 > "$HOME/v2-preflight.json"
```

Transfer that non-secret JSON file to the local load-generator results directory as `preflight.json`. Start load within five minutes of collection; otherwise collect a fresh snapshot. For V3 use `http-preflight.sh v3` after setting the calibrated live policy. The capture contains the fixed SWE5001 CloudFront origin, current service/task revision and digest, deployment counts/rollout state, scaling targets/policies, capture time and tooling SHA. AWS read errors or an unclean checkout fail capture; k6 setup validates the observations before it sends its first HTTP request.

The preflight must match the actual target origin, tool SHA and independently reviewed `EXPECTED_IMAGE_DIGEST`. It binds CloudFront's deployed, uncached `/api/*` behavior to the selected ECS attachment's target group, its ALB DNS name and the actual origin-protocol listener. The forwarding rule must reach that group and match CloudFront's origin header; other listener paths must be the declared disjoint internal/identity/notification routes. Extra CloudFront behaviors, API functions/Lambdas, origin-path prefixes, overlapping or unknown ALB paths/regex, selected-rule transforms and weighted alternate targets fail closed rather than attempting a general routing interpreter. This deliberately supports the repository's declared topology; route changes need review and updated tests. V2 requires the stable single monolith task with scaling absent or every suspension flag true. V3 additionally binds the HTTP scaling resource label to that same ALB/group, with active bounds 1..6 and target `KNEE_RPS * 60`. IMAGE_SHA is still source-provenance metadata: the observed digest is verified against the reviewed artifact, but an arbitrary SHA supplied by the caller is not independently proven by ECS. Retain the build/scan/digest-to-source evidence rather than inferring it from a mutable tag.

Collection needs CloudFront/ECS/Application Auto Scaling reads plus `elasticloadbalancing:DescribeTargetGroups`, `DescribeLoadBalancers`, `DescribeListeners` and `DescribeRules`. Origin-header and listener-header values are compared only in permission-restricted temporary inputs, deleted on exit; the snapshot contains only selected routing metadata and a match boolean, never those values. Do not save raw CloudFront/listener responses as evidence. A failed or denied route read stops capture; an unbound route stops k6 before its first HTTP request. AWS route behavior itself remains an operator-session verification, not something proved by the mocked capture tests.

A preflight is a point-in-time check, not continuous runtime verification. Capture task counts, task identity and deployment events during and after the run, and reject measurements if deployment/scaling conditions change. Those observations and generator resource readings remain required operator evidence before using any candidate target. No calculator output is an automatic deployment approval.

The ten rates are 10, 20, 40, 60, 80, 100, 125, 150, 175 and 200 iterations/second, each sustained for 60 seconds. Each workload iteration makes one request; surplus terminal guard iterations make none. The mix is exactly seven list and three detail requests per ten workload iterations per scenario, with IDs discovered once from the real Result page. There is no sleep/think time in this arrival-rate test. Setup fails on an empty catalogue, wrong envelope, HTML or malformed product IDs. Requests do not follow redirects; 200 error envelopes and mismatched detail IDs count as failures. End-to-end wall-clock latency includes connection establishment as well as HTTP transfer.

One ramping-arrival executor owns one shared pool of 100 preallocated VUs with a maximum of 400 total, not ten independently allocated pools. Zero-duration transitions step between ten 60-second constant-rate plateaus, followed by a one-second terminal scheduling guard and at most fifteen seconds final completion grace. The guard lets the final arrival execute if k6 reaches its nominal cutoff early without recording a drop; a hard ordinal cap permits exactly 57,600 workload requests (960 in the one-second-per-stage fixture). Surplus guard arrivals perform no HTTP and emit no browse samples; the guard is not an eleventh measurement plateau. The executor's arrival ordinal identifies each request's scheduled plateau before HTTP and is retained on completion, avoiding wall-clock boundary skew. This attribution is valid only with the mandatory zero-drop gate and one local executor; it does not support sharded/distributed load generators. Every plateau retains its own p95, error rate and completed count. Explicitly record any VU override and monitor CPU/memory/dropped iterations on the actual generator; fixture tests do not validate a workstation's capacity to generate live load. A request times out after ten seconds; total offered load is capped at 200 RPS. Inspect actual timing in live evidence; the guard does not prove a perfectly timed arrival stream.

From a POSIX shell on the load-generator workstation, create a new private output directory outside the repository. The confirmation must exactly match the origin you are authorized to load; it is not a credential. Do not include `/api` in `BASE_URL`.

```bash
results=$(mktemp -d)
chmod 700 "$results"
export BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net
export CONFIRM_TARGET="$BASE_URL"
export TEST_SHA=$(git rev-parse HEAD)
# Set this from the verified deployed image's source, not automatically from the local checkout:
export IMAGE_SHA='<full-deployed-source-sha>'
export EXPECTED_IMAGE_DIGEST='sha256:<reviewed-deployed-image-digest>'
export RUN_LABEL=20261015-v2-monolith
docker run --rm --user "$(id -u):$(id -g)" \
  --mount "type=bind,source=$PWD/perf/k6,target=/scripts,readonly" \
  --mount "type=bind,source=$results,target=/results" \
  --env BASE_URL --env CONFIRM_TARGET --env TEST_SHA --env IMAGE_SHA --env EXPECTED_IMAGE_DIGEST --env RUN_LABEL \
  grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1 run \
  --env MODE=v2 --env TOPOLOGY=monolith --env PREFLIGHT_PATH=/results/preflight.json \
  --env SUMMARY_PATH=/results/summary.json \
  --out json=/results/samples.json /scripts/browse.js
```

On PowerShell, use `$env:NAME=...`, an absolute existing results directory and `--user 0:0` instead of the POSIX UID expression; Docker bind-mount paths must be quoted. Do not pipe a nonzero k6 exit into a command that masks its status. Threshold crossing at saturation can legitimately make V2 exit 99, but setup/generator/interruption failures do not yield acceptable calibration: inspect the report and analyzer rather than treating every nonzero status as saturation. If no plateau fails, the largest tested passing rate is a tested lower bound, not a located saturation knee; expand the range only through a reviewed follow-up, not by claiming untested capacity.

```bash
node perf/k6/calibrate.mjs "$results/summary.json" > "$results/calibration.json"
```

Analysis requires exact scheduled counts for every plateau, an explicit numeric zero dropped-iteration count, p95 <500 ms and errors <1%. Only one optional trailing end-boundary arrival is allowed in the last plateau; no stage may be short, and the global count must equal the sum of all plateau counts. Missing, fractional or inconsistent counters invalidate calibration. Missing drop statistics are unknown evidence, never zero. k6's reported execution timer may be slightly below the nominal schedule (observed 599980 ms for a complete 600-second fixture); the remote timing guard permits at most one second precision difference only alongside the strict completed-count and zero-drop checks. Analysis selects the last passing plateau before the first failure, never a later isolated recovery. Partial/missing/invalid statistics and a failing first plateau produce no target. Remote reports must include the matching preflight; local-fixture reports are refused by the CLI. The candidate is `kneeRps * 60` requests/target/minute; the report states whether saturation was observed and always requires operator review. Check it against the full task-count/deployment and generator evidence before adopting it; preflight alone cannot prove an unchanged ten-minute run.

## Connection budget before V3

An authorized operator reads these fixed, non-secret SQL settings from the actual RDS instance via the private `db-init` reporting path (not an exposed database port). Do not print credentials or connection strings. This PR adds no database access or mutation endpoint.

```sql
SHOW max_connections;
SHOW superuser_reserved_connections;
SHOW reserved_connections;
```

From the operator's approved, running-session checkout and environment, `bash infra/swe5001/scripts/connection-budget.sh` runs these reads as one fixed read-only report on the existing db-init task definition. It starts a billed one-off Fargate task, accepts no SQL arguments, requires both successful task exit and a complete validated JSON report, and prints only `maxConnections` and the summed `reservedConnections`. It does not run migrations or change runtime configuration. Do not invoke it during the load measurement; collect settings first. Capture stdout to a private file and add the independently observed service pools and reviewed allowances below.

Record the source SHA/session/time and collect each service's effective Hikari maximum from its task definition plus packaged configuration (including environment overrides). The current default is ten per task, but an observed override wins. Include peak task maxima, not merely currently running tasks: commerce up to six, notification up to four and identity one. Allow explicitly for owner/Flyway, operator connections, reserved slots and additional headroom. Database/schema separation does not create separate instance connection budgets.

Save an operator-filled JSON file with `maxConnections`, `reservedConnections` (sum of both PostgreSQL reservation settings), `ownerConnections`, `operatorConnections`, `headroomConnections`, and `services` containing exactly one entry each for `identity-svc`, `commerce-svc`, and `notification-svc`, with `name`, `maxTasks` and `poolSize`. Do not fill unknown live numbers with assumed defaults.

```bash
node perf/k6/budget.mjs "$results/observed-budget.json" > "$results/connection-budget.json"
```

Exit 0 means the proposed maximum fits with the specified headroom; exit 2 means insufficient capacity; exit 1 means incomplete/invalid input. The calculation includes every service pool plus owner/operator/reserved/headroom allowances. A failed budget blocks V3 until a reviewed pool-size reduction or maximum-task-cap change is deployed and reflected in the new evidence. The calculator never makes such changes itself. A fitting arithmetic budget does not prove absence of connection errors; record that separately during V3.

## V3 and evidence closure

See [V3/V4 operator preparation](SCALING.md) for event-relative latency analysis, resource monitoring, connection-budget prerequisites and the guarded 2,000-team demo expiry driver. Live execution requires fresh authorization; local fixtures do not satisfy AWS acceptance.

Reuse the V2 Docker command with `MODE=v3`, `TOPOLOGY=distributed`, a new `RUN_LABEL`, `KNEE_RPS=<measured-knee>` and a fresh results directory containing the V3 preflight. Pass `--env KNEE_RPS` or a literal `--env KNEE_RPS=...` to the runner. The ramp goes from 20% to 100% of the candidate over five minutes, reaches 300% over the next ten minutes, holds ten minutes, then ramps to zero over five minutes. Rates are bounded by the reviewed V2 range (maximum 600 RPS in V3). Error rate <1% and zero generator drops are gates; the all-run p95 <800 ms gate is a diagnostic screen, not an implementation of the event-relative acceptance exclusion.

10 November correction: use the [persistent observer procedure](../../docs/swe5001/http-load-collection.md), not a detached CloudShell recorder. An optional, separately reviewed commerce CPU headroom policy requires explicit k6 `CONFIRM_CPU_HEADROOM=true` and the reviewed `CPU_HEADROOM_THRESHOLD` (default 70); preflight binds its CPU alarm, action and positive-only adjustment to commerce. The default still requires only the original request policy; arbitrary extra policies fail closed. Record changed tooling's real reviewed commit, not the older run's SHA. This support changes neither workload nor acceptance thresholds.

For exact V3 acceptance, correlate raw samples with real scale-out timestamps and evaluate p95 <800 ms outside the three minutes after each scale-out, with error rate <1% throughout. Retain the original failing all-run screen when reporting an event-filtered result; never relabel it green. Capture the task-count graph, ALB throughput/p95, k6 summary and ECS events on the same UTC interval; show observed one-to-four-task throughput within 80% of linear where measured, or state the missing observation. Neither a successful fixture nor the ramp's offered rate proves scaling or throughput linearity.

Persist the reviewed candidate as `SWE5001_COMMERCE_REQUESTS_PER_TARGET` in the `swe5001-demo` GitHub environment only with approval; use the matching `TF_VAR_commerce_requests_per_target` in CloudShell. Re-read both the live policy and persisted configuration. Capturing a screenshot without replacing the provisional value does not complete #457. Do not run `env-up.sh` during active load, because it deliberately pauses services and reinitializes readiness.

Preserve summaries, sanitized context/connection budget, event/time-series evidence and dashboard screenshots under `docs/swe5001/evidence/<date>-<scenario>/` with run/session links, then run the normal `env-down.sh` and independent `env-status.sh`. Only tick live criteria after observing them. V4's demo-only 2,000-team expiry seed, burst driver, drain-to-zero/DLQ and #448 evidence remain subsequent #458 work; this HTTP tooling PR must use `Refs #458`, not `Closes #458`.
