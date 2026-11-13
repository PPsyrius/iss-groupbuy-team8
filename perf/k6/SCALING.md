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

Choose an already-active synthetic identity UUID; the runner verifies it in the identity database. No identity/product bulk seed is created. The fixed SQL uses the existing `DEMO-P-0001`, creates exactly 2,000 unpaid teams and one recipient membership per team, all due 60 seconds after the transaction starts. There are no payment/order rows or direct outbox writes. Real scheduling expires the teams and writes `TeamExpired` through the existing transactional outbox → SNS → SQS → notification consumer path. A previously used run label is rejected, never reset; do not retry seed creation after ambiguous task/network failure—query retained evidence first.

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

The driver requires one stable notification task, unsuspended 1..4 scaling, reviewed digests/revisions and quiet main/DLQ queues before seed. It observes every 15 seconds, starts one billed db-init seed task and one billed read-only report task, and does not scale services, receive/purge queue messages or modify policies. It waits at least five minutes, stops waiting at 30 minutes by default (`V4_MAX_SECONDS` 300..3600), and retains partial diagnostics on failure. Successful collection is not successful acceptance: analyze the receipts separately. If source work is incomplete at the final report, retain it and collect a later fixed report with `v4-expiry-report.sh`; do not reseed or discard the initial observations.

The analyzer requires exact 2,000 source teams/expired teams/outbox events/published events/notification effects/consumer markers, zero failed outbox events, a quiet one-task starting observation, backlog at/after the seed expiry deadline followed by a later four-task observation (never four tasks before/at that first backlog sample), and no sampled visible/in-flight/delayed DLQ messages. Drain requires a final contiguous empty interval containing at least three samples and spanning at least 30 seconds; an extra closely spaced final snapshot does not reset that interval, but any renewed visible/in-flight/delayed work does. Sampling that cannot establish this ordering fails closed instead of crediting unrelated earlier scaling. Source/effect counts are run-scoped; recipient/event-ID arrays are not published in the final receipt. Queue counts are approximate and sampled, not proof against future arrivals. `oldestAgeSeconds:null` explicitly means not collected: SQS has no oldest-age queue attribute, so retain the CloudWatch age/backlog/task graphs separately. Keep original pre/post-deployment observations, logs, report timing and reviewer interpretation with the analyzer output before claiming acceptance.

## Local verification and limits

### 13 November authorized fixture-only R2

The [revised-trial record](../../docs/swe5001/evidence/20261113-aws-validation/v4-r2-plan.md) preserves the original 2,000-team R1 scaling failure and the owner's separate approval for one bounded, explicitly identified count-only R2 up to 20,000 real teams. This is a revised test workload, not permission to slow consumers, change the target/policy, force workers or relabel R1. Preserve the default 2,000-team procedure and bind every revised count through confirmation, seed/report and analysis. Complete source/effect reconciliation and final drain before stopping the larger collection; a source-paced temporary zero is not full completion. Longer duration may expose a real scaling signal but cannot guarantee overload or a pass.

Scale-out must be observed before the final quiet interval starts (`drainAfterScale`); first reaching four tasks during or after that interval fails acceptance, even if the queue drains and all source/effect counts match. If scale-out and the first quiet sample coincide, sampling cannot establish that order and the analyzer fails closed.

```bash
node --test perf/k6/lib.test.mjs perf/k6/scaling.test.mjs
bash infra/swe5001/scripts/test-v4-expiry-seed.sh
bash infra/swe5001/scripts/test-v4-expiry-tools.sh
node --test perf/k6/integration.test.mjs perf/k6/seed.integration.test.mjs perf/k6/monitor.integration.test.mjs
```

The PostgreSQL Node test applies the real migration stream and demo catalog in a disposable network-isolated PostgreSQL 18 container, checks guards/UUIDv7/counts/no payments/no seed-created outbox, verifies duplicate refusal and transaction rollback, and executes source/effect report SQL. Its direct SQL event/effect inserts test **query contracts only**. The separate backend `V4ExpiryPipelineIT` runs the actual seed SQL, waits for its real 60-second deadline and exercises scheduled expiry, transactional outbox writing, scheduled relay and the real consumer against PostgreSQL/ElasticMQ without manually writing event/effect rows. It uses the supported local SQS transport, not AWS SNS fan-out or autoscaling. `SchedulingEnvironmentTest` binds the real YAML using the exact Terraform environment-variable names and verifies both bound settings and the scheduler placeholder; explicit YAML bridges prevent underscore-separated environment names from silently leaving configuration-properties defaults unchanged. Stubbed shell tests exercise refusal-before-write and fixed report commands without credentials. Mocked Terraform verifies default-off settings and the explicit V4 override; none of these tests demonstrates live AWS scaling, drain, one-to-four linearity or connection capacity. Run the backend tests through the normal Docker-backed `mvn -B verify` gate with the repository's root-level fixtures available.
