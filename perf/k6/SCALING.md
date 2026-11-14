# V3/V4 operator preparation (#458)

These tools prepare evidence; they do not close #458 or establish AWS capacity from fixtures. The [verification plan](../../docs/swe5001/impl-verification-observability.md) owns acceptance. All AWS commands below require a separately authorized live session, a clean reviewed checkout, the existing account/region/state-bucket environment and private results outside the checkout. They were not run against AWS during local preparation on 2026-10-16.

## V3: budget and observations before load

1. Run the existing billed read-only `connection-budget.sh` report only after session authorization. Inventory effective Hikari maximum pools for **every** service and deployed autoscaling maximums (including notification), operator/reporting connections and RDS reservations; pass observed values to `budget.mjs` as described in [README](README.md#connection-budget-before-v3). Retain settings and arithmetic together. A fitting estimate does not prove absence of `too many clients`; inspect logs across the full run.
2. Decide the V3 starting floor explicitly. The persisted floor is currently 2; the original one-to-four linearity criterion needs a separately reviewed one-task reference/window, not a claim inferred from a two-task start. `12000` requests/target/minute is a tested lower-bound candidate from monolith V2, not observed saturation or distributed capacity. Do not silently change either setting.
   For sessions after the 10 November collection failure, follow the [persistent capture procedure](../../docs/swe5001/http-load-collection.md): a detached CloudShell observer cannot guarantee this evidence window. The optional CPU scale-out guard must be independently reviewed, explicitly confirmed and bound in preflight; include its actual activities in the same event-window analysis, without changing any acceptance gate.
3. Keep `v4_demo_enabled=false`. Capture the V3 preflight and start `bash infra/swe5001/scripts/http-observe.sh v3 > observations.jsonl` **before** the runner. It reads ECS every 15 seconds for 35 minutes by default (`OBSERVE_SECONDS` bounded to 30..7200); keep stderr separately. Run the existing 30-minute V3 workload with k6 `--out json=/results/samples.jsonl`. Leave the observer alive until a post-run sample, then capture a fresh route/digest preflight.
4. For local generator CPU/memory, create a named k6 container without `--rm`, start `node perf/k6/monitor-generator.mjs <name> <new-private-file.jsonl>`, then start the container. The monitor waits up to 30 seconds for a created container to run, validates and saves its first sample immediately, strips terminal controls, and never stops/removes containers. Review sample gaps and host saturation separately; Docker CPU may exceed 100% on multiple cores. Remove only that completed container after retaining evidence.
5. Export the original successful Application Auto Scaling activities and normalized scale-out records. Record the effective activity timestamp and explain its meaning; do not guess timestamps from screenshot pixels or desired-count changes. Normalized shape:

```json
{"schema":1,"toolingSha":"<40-character tooling SHA>","runLabel":"<same run>","events":[{"at":"<UTC ISO timestamp>","from":1,"to":4,"source":"application-autoscaling","activityId":"<original ActivityId>"}]}
```

```bash
node perf/k6/analyze-v3.mjs summary.json samples.jsonl observations.jsonl scale-outs.json > analysis.json
```

The analyzer checks the route-bound preflight, full nominal arrival count (one boundary arrival tolerance), explicit zero generator drops, raw/summary count consistency, bracketing ECS observations with gaps at most 45 seconds and a stable task revision. Only latency samples in `[scale-out timestamp, timestamp + 180 seconds)` are excluded; errors remain all-run and p95 uses linear interpolation. Preserve k6's original all-run latency failure even when the event-relative gate passes. Exit 0 means these HTTP/scale-out gates pass, 2 means failed gates, and 1 means invalid/incomplete evidence. `acceptanceComplete` deliberately stays false: actual ALB achieved throughput at one/four tasks (at least 80% linear), connection headroom/errors, generator headroom, deployment stability and dashboard graphs still need review. Offered RPS is not achieved throughput or capacity.

## V4: real expiring teams, never synthetic SNS publication

**Deployment review required:** the new runtime Terraform input `v4_demo_enabled` defaults false and rejects non-distributed topology. A reviewed V4-only deployment with `true` adds the commerce `demo` profile and changes only its team-expiry sweep to 500 items per one-second tick (the existing batch cap remains 500). It neither seeds automatically nor bypasses the services/messaging-readiness gates. Default expiry is 100 items per minute, which otherwise spreads 2,000 teams over about twenty sweeps. Scheduling/lease/relay throughput still determines the observed event burst; no four-second completion is promised. Restore the input to false after V4, before V2/V3 or ordinary sessions. No workflow/environment variable persistence has been added for this flag; use a separately reviewed runtime plan/apply rather than assuming `env-up.sh` propagates it.

Choose an already-active synthetic identity UUID; the runner verifies it in the identity database. No identity/product bulk seed is created. The fixed SQL uses the existing `DEMO-P-0001`, creates exactly 2,000 unpaid teams by default and one recipient membership per team, all due 60 seconds after the transaction starts. Separately approved revised cohorts are limited to the explicit 20,000/40,000/80,000 allowlist below. There are no payment/order rows or direct outbox writes. Real scheduling expires the teams and writes `TeamExpired` through the existing transactional outbox → SNS → SQS → notification consumer path. A previously used run label is rejected, never reset; do not retry seed creation after ambiguous task/network failure—query retained evidence first.

A 2,000-event workload may drain before CloudWatch's scaling alarm and ECS startup react; local correctness tests cannot resolve that AWS timing risk. Do not slow consumers, inflate the seed, lower scaling thresholds or count pre-existing tasks to manufacture acceptance. If the observed burst does not cause scale-out, retain the failed/insufficient evidence and review the workload/acceptance design separately before changing runtime behavior.

```bash
# BILLED AWS work: only in the explicitly authorized V4 session.
export V4_RUN_LABEL=v4-20261019-a
export V4_RECIPIENT_ID=<already-active-synthetic-identity-uuid>
export EXPECTED_IMAGE_DIGEST=sha256:<reviewed-commerce-digest>
export EXPECTED_NOTIFICATION_DIGEST=sha256:<reviewed-notification-digest>
export CONFIRM_V4_SEED=2000
export V4_RESULTS_DIR=<new-empty-private-directory-outside-checkout>
bash infra/swe5001/scripts/v4-burst.sh
node perf/k6/analyze-v4.mjs "$V4_RESULTS_DIR/seed.json" \
  "$V4_RESULTS_DIR/observations.jsonl" "$V4_RESULTS_DIR/database-report.json"
```

The driver requires one stable notification task, unsuspended 1..4 scaling, reviewed digests/revisions and quiet main/DLQ queues before seed. It observes every 15 seconds, starts one billed db-init seed task and a final billed read-only report task, and does not scale services, receive/purge queue messages or modify policies. Historical 2,000-team collection waits at least five minutes; revised 20,000/40,000/80,000 collection defaults to and requires at least 600 seconds (`V4_MIN_SECONDS`). The per-trial maximum defaults to 1,800 seconds (`V4_MAX_SECONDS` 300..3600) and must be strictly greater than the minimum; this safety window is not an overall session deadline. Revised collection retains each incomplete report and checks at most once per minute while the sampled queue is quiet, then requires exact source/effect completion followed by three quiet samples spanning at least thirty seconds. It retains partial diagnostics on failure. Successful collection is not successful acceptance: analyze the receipts separately. If source work is incomplete at the final report, retain it and collect a later fixed report with `v4-expiry-report.sh`; do not reseed or discard the initial observations.

The analyzer requires exact 2,000 source teams/expired teams/outbox events/published events/notification effects/consumer markers, zero failed outbox events, a quiet one-task starting observation, backlog at/after the seed expiry deadline followed by a later four-task observation (never four tasks before/at that first backlog sample), and no sampled visible/in-flight/delayed DLQ messages. Drain requires a final contiguous empty interval containing at least three samples and spanning at least 30 seconds; an extra closely spaced final snapshot does not reset that interval, but any renewed visible/in-flight/delayed work does. Sampling that cannot establish this ordering fails closed instead of crediting unrelated earlier scaling. Source/effect counts are run-scoped; recipient/event-ID arrays are not published in the final receipt. Queue counts are approximate and sampled, not proof against future arrivals. `oldestAgeSeconds:null` explicitly means not collected: SQS has no oldest-age queue attribute, so retain the CloudWatch age/backlog/task graphs separately. Keep original pre/post-deployment observations, logs, report timing and reviewer interpretation with the analyzer output before claiming acceptance.

## Local verification and limits

### 13 November authorized fixture-only R2

The [revised-trial record](../../docs/swe5001/evidence/20261113-aws-validation/v4-r2-plan.md) preserves the original 2,000-team R1 scaling failure and the owner's separate approval for one bounded, explicitly identified count-only R2 up to 20,000 real teams. This is a revised test workload, not permission to slow consumers, change the target/policy, force workers or relabel R1. Preserve the default 2,000-team procedure and bind every revised count through confirmation, seed/report and analysis. Complete source/effect reconciliation and final drain before stopping the larger collection; a source-paced temporary zero is not full completion. Longer duration may expose a real scaling signal but cannot guarantee overload or a pass.

Scale-out must be observed before the final quiet interval starts (`drainAfterScale`); first reaching four tasks during or after that interval fails acceptance, even if the queue drains and all source/effect counts match. If scale-out and the first quiet sample coincide, sampling cannot establish that order and the analyzer fails closed.

### Producer-only V4 R3 source preparation

The owner's later producer-only scope permits an experimental commerce image without official release promotion, a temporary commerce **scale-in-only** suspension with independent restoration, and measured-rate-sized fresh isolated cohorts up to 80,000. This does not retroactively pass R1/R2 or earlier failed/interrupted R3 cohorts: retain every original receipt, error and analysis, and keep the public name **V4 R3**. Choose each count from measured producer/consumer rates before seeding; a larger cohort changes only workload size, not notification throughput settings. No notification image/worker change, slowdown, forced worker, queue hold, policy/target-50/bounds-1..4 change, pool/IAM expansion or teardown is part of these helpers.

`V4_EXPECTED_COUNT` accepts only `2000`, `20000`, `40000` or `80000`; arbitrary intermediate counts, leading zeroes, exponent notation and counts above 80,000 fail closed. `CONFIRM_V4_SEED` must exactly equal that count. All newly generated seed receipts bind both numeric `expectedCount` and `confirmedCount`; reports bind `expectedCount`, and analysis requires an explicit matching `--expected-count` for every revised workload. Historical 2,000/20,000 receipts remain analyzable as originally captured, without fabricating fields. The fixed SQL scopes labels by their exact numeric team suffix, excluding prefix-neighbor labels, refuses reused labels including soft-deleted teams, and bounds transferred event IDs to expected count plus one for surplus detection. Large report JSON is streamed through stdin rather than an oversized shell argument. Neither fixed SQL adds DDL, privileges, expired teams or outbox/event/effect rows.

The driver currently checks notification scaling only and has no commerce all-suspensions-false preflight to relax. Notification's three suspension flags must remain false in every mode. The separate operator control must require a scoped, explicitly confirmed producer-only hold mode and a run/count/source/digest-bound receipt before accepting commerce `DynamicScalingInSuspended=true`; commerce bounds stay 2..6 and its scale-out/scheduled flags stay false. That receipt must identify an independently armed restore control for the original commerce suspension flags and desired count; no restore action is implemented or implied by this driver. Default operator preflight must continue to reject any commerce suspension. Exact-source review and the existing signed-source guard still precede execution; never identify an uncommitted overlay as the signed baseline.

```bash
# Future authorized trial only, after exact-source, rate, restore and live-setting review.
# Select one approved count; use a genuinely new label and private empty results directory.
export V4_EXPECTED_COUNT=40000
export CONFIRM_V4_SEED=40000
export V4_MIN_SECONDS=600
export V4_MAX_SECONDS=3600
# Set the recipient, reviewed commerce/unchanged notification digests, fresh label and results as above.
bash infra/swe5001/scripts/v4-burst.sh
node perf/k6/analyze-v4.mjs "$V4_RESULTS_DIR/seed.json" \
  "$V4_RESULTS_DIR/observations.jsonl" "$V4_RESULTS_DIR/database-report.json" \
  --expected-count "$V4_EXPECTED_COUNT"
```

Every cohort must reconcile exact teams/expired teams/outbox/published/notification/consumer-marker counts, zero failed outbox rows, empty sampled DLQ, backlog followed by later automatic four-task growth before final drain, full observation coverage and deployment provenance. These preparations neither guarantee a pass nor replace the original autoscaling activities, task/queue/age graphs, independent restore receipts or operator review. Do not add another cohort while prior source/effects remain incomplete, or reuse any prior cohort label or private results directory.

```bash
node --test perf/k6/lib.test.mjs perf/k6/scaling.test.mjs
bash infra/swe5001/scripts/test-v4-expiry-seed.sh
bash infra/swe5001/scripts/test-v4-expiry-tools.sh
node --test perf/k6/integration.test.mjs perf/k6/seed.integration.test.mjs perf/k6/monitor.integration.test.mjs
```

The PostgreSQL Node test applies the real migration stream and demo catalog in a disposable network-isolated PostgreSQL 18 container, checks guards/UUIDv7/counts/no payments/no seed-created outbox, verifies duplicate refusal and transaction rollback, and executes source/effect report SQL. Its direct SQL event/effect inserts test **query contracts only**. The separate backend `V4ExpiryPipelineIT` runs the actual seed SQL, waits for its real 60-second deadline and exercises scheduled expiry, transactional outbox writing, scheduled relay and the real consumer against PostgreSQL/ElasticMQ without manually writing event/effect rows. It uses the supported local SQS transport, not AWS SNS fan-out or autoscaling. `SchedulingEnvironmentTest` binds the real YAML using the exact Terraform environment-variable names and verifies both bound settings and the scheduler placeholder; explicit YAML bridges prevent underscore-separated environment names from silently leaving configuration-properties defaults unchanged. Stubbed shell tests exercise refusal-before-write and fixed report commands without credentials. Mocked Terraform verifies default-off settings and the explicit V4 override; none of these tests demonstrates live AWS scaling, drain, one-to-four linearity or connection capacity. Run the backend tests through the normal Docker-backed `mvn -B verify` gate with the repository's root-level fixtures available.
