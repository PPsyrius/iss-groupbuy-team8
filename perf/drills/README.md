# AWS drill preparation (#459)

These tools are preparation, not authorization for an AWS session. The [shared session plan](../../docs/swe5001/aws-validation-session-plan.md) owns timing, phase order, faults, recovery, evidence and stop conditions. No tool here starts/stops services, publishes SNS messages, deletes queue messages or contacts Stripe. `browse.js` sends GETs only; `poison.mjs` creates files only; `observe.py` permits named read operations only.

## Local checks, no AWS credentials

```bash
node --test perf/drills/lib.test.mjs perf/k6/lib.test.mjs perf/k6/scaling.test.mjs
python -B -m unittest discover -s perf/drills -p '*_test.py' -v
# Requires Docker; disposable isolated HTTP/PostgreSQL fixtures only:
node --test perf/k6/integration.test.mjs perf/k6/seed.integration.test.mjs perf/drills/report.integration.test.mjs
docker build -f perf/drills/operator.Dockerfile -t blocbuy-swe5001-drill-operator:20261113 perf/drills
docker run --rm --network none -v "$PWD:/repo:ro" -w /repo \
  blocbuy-swe5001-drill-operator:20261113 -c 'set -e; bash infra/swe5001/scripts/test-http-preflight.sh; bash infra/swe5001/scripts/test-v4-expiry-seed.sh; bash infra/swe5001/scripts/test-v4-expiry-tools.sh; bash infra/swe5001/scripts/test-identity-drill-report.sh'
```

Use Node 24 and the pinned [k6 1.6.1 image](../k6/README.md). The one full-duration V2 integration case remains opt-in; compressed generator fixtures do not measure application performance. The operator image pins AWS CLI and Terraform 1.16.4, with the Terraform archive SHA-256 taken from HashiCorp's published checksum list. Package versions beyond those pins are not a fully reproducible OS lock. It defaults to non-root UID/GID `10001:10001` with a writable `/home/operator`; any explicitly mounted output directory must permit that UID to write. No profile, token or source checkout is baked into the image.

## Steady browse: future authorized session only

Use a clean reviewed checkout. Before **any** HTTP, bind the actual deployed commerce source/digest, target route and two stable commerce tasks in distinct AZs. The existing collector now supports `v5` and `identity` in addition to unchanged one-task `v2`/`v3`; it does not alter counts, scaling, task definitions or policies.

```bash
# Operator host; read-only AWS calls. These examples have NOT been run on the application.
umask 077
bash infra/swe5001/scripts/http-preflight.sh v5 > "$results/preflight.json"
```

Transfer that file privately to the generator's new run directory. Set `TEST_SHA` to the reviewed tooling commit; `IMAGE_SHA` and `EXPECTED_IMAGE_DIGEST` come from independent release/digest receipts. Read the actual request-policy target into `SCALING_REQUESTS_PER_TARGET`, do not guess it from the baseline. This driver accepts the normal active 2..6 ALB request policy only; unexpected/CPU policies fail closed for review, not automatic removal.

```bash
export BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net
export CONFIRM_TARGET="$BASE_URL"
export TEST_SHA='<reviewed-full-tooling-sha>'
export IMAGE_SHA='<reviewed-full-deployed-source-sha>'
export EXPECTED_IMAGE_DIGEST='sha256:<reviewed-commerce-digest>'
export SCALING_REQUESTS_PER_TARGET='<observed-reviewed-value-per-minute>'
export RUN_LABEL='<new-phase-label>'
export KNEE_RPS=200 # Existing tested lower bound; not a located saturation knee.
name="swe5001-$RUN_LABEL"
docker create --name "$name" --cpus 8 --memory 8g --user "$(id -u):$(id -g)" \
  --mount "type=bind,source=$PWD/perf,target=/scripts,readonly" \
  --mount "type=bind,source=$results,target=/results" \
  --env BASE_URL --env CONFIRM_TARGET --env TEST_SHA --env IMAGE_SHA --env EXPECTED_IMAGE_DIGEST \
  --env SCALING_REQUESTS_PER_TARGET --env RUN_LABEL --env KNEE_RPS \
  grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1 run \
  --env DRILL=v5 --env TOPOLOGY=distributed --env PREFLIGHT_PATH=/results/preflight.json \
  --env SUMMARY_PATH=/results/summary.json --out json=/results/samples.jsonl /scripts/drills/browse.js
# In a separate terminal, start the existing monitor before starting the named container:
node perf/k6/monitor-generator.mjs "$name" "$results/generator.jsonl"
# Then attach/start from another terminal, within five minutes of preflight:
docker start --attach "$name"
# Preserve container exit status, original output and files before removing this exact container.
docker inspect "$name" --format '{{json .State}}' > "$results/container-state.json"
node perf/drills/analyze-browse.mjs "$results/summary.json" > "$results/browse-analysis.json"
```

The monitor expects the created container to start within 30 seconds. Do not wait through account/fixture preparation after starting it. Preserve the actual container exit code: attaching successfully is not by itself evidence that k6's thresholds passed. Do not use `--rm` before capturing state/monitor output.

V5 runs ten minutes at half `KNEE_RPS`; a two-second rate unit preserves exact half rates for odd candidates. Identity uses a fresh `http-preflight.sh identity` snapshot and `DRILL=identity`, with fixed light browse at 2 requests/s for fifteen minutes. Both use one extra scheduling-guard second and an exact workload request cap; that guard is not extra HTTP. Thresholds require zero invalid/non-2xx browse responses, zero drops and exact counts. Identity's expected START/webhook 500s are **not** generated by this driver. Latency is retained, but no new latency acceptance threshold is invented for these drills.

`analyze-browse.mjs` checks the summary's browse counters only and always returns `awsAcceptanceComplete=false`: raw/timing coverage, actual faults, replacement, rollback and provider recovery still need review. `TOPOLOGY=local-fixture` and `FIXTURE_SECONDS` work only against explicit loopback fixtures; AWS cannot use shortened schedules.

## Read-only observations

Run on a persistent authenticated host, using its normal temporary AWS CLI profile, not an instance profile with invented application privileges or exported static credentials. The prepared EC2 generator role has only SSM/artifact access; it cannot collect application observations. The persistent local operator workstation can do so after the live session is approved.

```bash
export AWS_REGION=ap-southeast-1
export TEST_SHA='<reviewed-full-tooling-sha>'
python perf/drills/observe.py v5 '<new-private-observer-directory>' --seconds 1200 --persistent-host
# Identity: use --seconds 1500 and a new directory (15-minute browse plus capture margins).
# V7: use --seconds 1200 and a new directory.
```

The observer requires known credential expiration covering its window plus fifteen minutes, rejects CloudShell/unconfirmed hosts and saves a first real sample before announcing readiness. It reads all three services and task identities/digests/AZs about every fifteen seconds; V7 additionally reads queue counts and alarm state. It never receives messages or reads Parameter Store/application secrets. API errors, interrupted windows and sample gaps over 45 seconds cannot produce a complete capture. A complete capture still does not prove a drill passed. Restore faults independently.

For the local Windows operator container, mount the reviewed checkout at `/repo` read-only for observations/preflight, an existing private results parent at `/results`, and **only** the selected SSO configuration/cache directory at `/root/.aws`; allow cache refresh without baking it into the image. Pass `AWS_PROFILE`, `AWS_REGION` and the reviewed `TEST_SHA`, then override `--entrypoint python3` to run `/repo/perf/drills/observe.py`. Terraform-based startup/V4/report helpers need a separately approved **writable clean checkout** for each root's ignored `.terraform` directory, not that read-only mount. Do not mount all of the home directory or share one `TF_DATA_DIR` across roots.

## Identity state report

`identity-drill-report.sh` runs the checked-in fixed `identity-state.sql` through one **billed db-init task** on commerce, with read-only transaction/connection settings. It accepts no SQL/arguments, requires a complete marker and one matching prepared payment, and emits state/counts/stock only, not user/payment/provider identifiers or bodies.

```bash
# Future authorized session only, after creating the real pending Checkout.
export DRILL_PAYMENT_ID='<that-prepared-payment-uuid>'
unset DRILL_EVENT_ID
bash infra/swe5001/scripts/identity-drill-report.sh > "$results/state-before.json"
# Once the real provider event exists, set its private identifier and keep using the same one:
export DRILL_EVENT_ID='<actual-evt_identifier>'
bash infra/swe5001/scripts/identity-drill-report.sh > "$results/state-outage.json"
# After restoration, redelivery and then duplicate delivery, capture new files for each stage.
```

An unset event ID deliberately yields `webhookReceiptsForEvent:null`, not zero. After delivery its separate event-scoped count also detects a receipt whose payment link is still null. Compare the same actor/product intent count, payment state, event/payment receipts, participation/member/order/refund counts and product stock across stages; keep other writes for that actor/product out of this interval. The report can reject incomplete data but does not decide that the outage/recovery passed. Do not hold identity down beyond the restoration deadline merely because a reporting task or provider is slow.

## Offline poison payload

```bash
# Optional: an existing active synthetic recipient, supplied privately, prepares the valid control too.
export V7_CONTROL_RECIPIENT_ID='<reviewed-active-synthetic-identity-uuid>'
node perf/drills/poison.mjs v7-first '<new-private-directory-outside-the-checkout>'
```

This refuses an existing destination and writes `message.json`, `attributes.json` and a hash/ID manifest. It uses supported `PaymentSucceeded` version 1 but omits required payload fields, unlike the acknowledged unsupported-version routing probe. With the optional recipient it also writes `control-message.json`, `control-attributes.json` and `control-manifest.json`, using distinct event/reference IDs and all required payload fields; this is explicitly a notification-transport fixture, not a Stripe payment. There is no publish function. The live plan separately requires correctly attributed SNS publishes, one control effect, actual consumer receive counts, DLQ/alarm/email capture and deletion of only the identified poison using the [DLQ runbook](../../backend/docs/operations/notification-dlq-redrive.md). Never receive from the main queue or purge a queue as a preparation shortcut.
