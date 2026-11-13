# SWE5001 Environment Session Runbook

How to bring the SWE5001 ECS environment up for a short test, demonstration or presentation, check it, and destroy it afterwards. For the design behind it see the [AWS infrastructure plan](impl-aws-infrastructure.md); for the scripts themselves see [`infra/swe5001/README.md`](../../infra/swe5001/README.md#environment-sessions).

Last verified: 2026-10-06, promotion [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) (up, smoke, release and down all green).

## 1. What exists when

| Part | Lifecycle | Cost while idle |
| --- | --- | --- |
| `edge`: CloudFront distribution `E3N39GHVFV6CT3`, SPA bucket | Always on | A few cents a month |
| `foundation`: ECR repositories, GitHub OIDC roles | Always on | ECR storage, under USD 1 a month |
| `runtime`: VPC, NAT instance, ALB, ECS on Fargate, RDS, Valkey | **Only during a session or a promotion run** | None when absent; about USD 0.12–0.15 an hour when up with one service (list-price estimate); two more Fargate tasks with `distributed` |

`runtime` runs one of two service maps (`topology`, [`services.tf`](../../infra/swe5001/runtime/services.tf)): `distributed`, the default since #456, with `identity-svc`, `commerce-svc` and `notification-svc`, each on its own database ([backend ADR 0008](../../backend/docs/adr/0008-dual-deployment-topology.md)); or `monolith`, the fallback, with the SWE5006 image as one service.

URL: <https://d3lvq02o2e7tcn.cloudfront.net>. Without `runtime`, the SPA loads but every `/api/*` call returns **502 Bad Gateway**: CloudFront still points at the last, destroyed ALB. That is the normal idle state, not a fault.

A promotion run (`dev` → `prod` merge) creates `runtime`, smoke-tests it, publishes the release and destroys it again. A **session** keeps `runtime` up until you destroy it.

## 2. Before the first session (one time)

- [ ] A release exists for every service of the topology: `/blocbuy/swe5001/release/<service>` holds a digest (`monolith` first written by run 37470479078; the three services by the first promotion after #456). Before that promotion, see "First distributed session" in section 4.
- [ ] The application parameters are seeded (`identity/GOOGLE_CLIENT_ID`, `identity/GOOGLE_CLIENT_SECRET`, `commerce/STRIPE_API_KEY`, `commerce/STRIPE_WEBHOOK_SECRET`; [README](../../infra/swe5001/README.md#2-seed-the-parameters)). A missing one stops the task with `ResourceInitializationError: ... invalid ssm parameters`.
- [ ] For Google sign-in: `https://d3lvq02o2e7tcn.cloudfront.net/api/v1/auth/oauth2/google/callback` is an authorised redirect URI of the Google OAuth client. Without it, browsing works but sign-in fails. The URI is the same for both topologies; with `distributed` the ALB routes it to `identity-svc`.
- [ ] You can dispatch workflows in the repository (`gh auth status`; write access).
- [ ] Stripe test-mode Checkout is on for `commerce-svc` in the `distributed` topology (`BLOCBUY_STRIPE_CHECKOUT_ENABLED=true`, return URLs `https://d3lvq02o2e7tcn.cloudfront.net/payments/return`); the `monolith` fallback keeps it off. Before a payment demo: a Stripe test-mode webhook endpoint exists at `https://d3lvq02o2e7tcn.cloudfront.net/api/v1/webhooks/stripe` with the events `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`; its signing secret is in `commerce/STRIPE_WEBHOOK_SECRET` and a `sk_test_` key is in `commerce/STRIPE_API_KEY`. Tasks read both parameters at start, so overwrite them before the deployment (or force a new deployment afterward). A missing parameter stops the task as described above; a placeholder or invalid value starts the task but fails later: a bad `STRIPE_API_KEY` makes Start/Join fail at Checkout, and a bad `STRIPE_WEBHOOK_SECRET` makes every webhook fail signature verification, so payments stay unconfirmed and no payment or team notification arrives.

## 3. Timeline for a presentation

| When | Do |
| --- | --- |
| At least one day before | Full rehearsal: sections 4–6, then section 7 |
| T − 60 min | Section 4 (start; includes the 15-minute SNS readiness wait and recipient confirmation) |
| T − 15 min | Section 5 (check) |
| T | Present; follow section 6 |
| Right after | Section 7 (destroy) |

T − 60 min is the minimum planning allowance for distributed sessions after #447 adds the 15-minute routing-readiness wait; the historical monolith timings below do not include it. The original V8 under-30-minute up target is now unproven, not an acceptance claim. For 21 October (16:15–16:55 SGT) the [verification plan](impl-verification-observability.md), section 7, starts the session before 12:00, which leaves time for a second `up` after a failure and gives the dashboard a full hour of data.

## 4. Start

1. Make sure nothing else holds the Terraform state queue: no promotion running and no stale queued `down`, which would destroy the session when it reaches the front of the queue.

   ```bash
   gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 5
   ```

   ```bash
   gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-deploy.yml --limit 3
   ```

   A `down` that is `queued`, `pending` or `waiting` is stale: cancel it with `gh run cancel <id>`. Wait for a running promotion to finish.

2. Dispatch `up` with the release digests (the default):

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=up
   ```

   Use `-f digests=candidate` only to test an image that a promotion published but did not release, and `-f topology=monolith` only for the fallback.

3. Watch it (about 13–15 minutes: `runtime` apply about 8.5, `db-init` 1, service start, `edge` about 1, stable service, smoke suite about 1):

   ```bash
   gh run watch --repo PPsyrius/iss-groupbuy-team8 $(gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 1 --json databaseId --jq '.[0].databaseId')
   ```

The run is green only after every service is stable and the smoke suite passed through CloudFront (health, product list, unauthenticated `/auth/me` and `/notifications`, trace header, internal path blocked). With `distributed`, health and the product list come from `commerce-svc`. The two 401 checks prove the authentication envelope, not routing: every service answers 401 for protected unauthenticated `/api/v1` paths, including routes it does not own. Routing to `identity-svc` and `notification-svc` is checked signed in ([acceptance evidence](#acceptance-evidence-456)). The job summary shows the URL, the topology and the step timings.

**First distributed session (before the first promotion after #456).** `prod` and the release digests do not have the three services yet, so an operator runs the session from a `dev` checkout instead of the workflow: push the three images by hand and write their candidate digests ([README](../../infra/swe5001/README.md#environment-sessions)), then:

```bash
SWE5001_TOPOLOGY=distributed infra/swe5001/scripts/env-up.sh --candidate
```

Plan for 25–40 minutes: the messaging gate (#447) waits 900 seconds and fails unless the SNS email subscriptions are confirmed, so have the recipients ready to confirm during that wait (testing sessions may export `SWE5001_ALLOW_UNCONFIRMED_ALERTS=true` to downgrade this to a warning; never for a graded or evidence session) ([messaging check](#messaging-fan-out-check-447)). Then follow [demo data](#demo-data-distributed) and [acceptance evidence](#acceptance-evidence-456). Destroy with `env-down.sh` from the same checkout (section 8, fallback destroy). The step list and pass criteria of the first session are in the [#456 session plan](../agile/sprint-04/distributed-session-plan-20261014.md), Phase B.

## 5. Check

- Open <https://d3lvq02o2e7tcn.cloudfront.net>: the product list loads.
- Health from a terminal:

  ```bash
  curl -s https://d3lvq02o2e7tcn.cloudfront.net/api/actuator/health
  ```

  Expect `"status":"UP"`.

### Data-subnet verification (#589)

After an authorised session starts from a reviewed revision containing AD-15, follow the [subnet-verification checklist](subnet-verification.md). Its read-only collector records actual ECS task ENIs, RDS/Valkey subnet groups and ENIs, effective route-table associations and security-group rules without reading secrets or Terraform state. Collection completion is not acceptance: compare actual application/data subnet IDs, then record the separate connectivity checks before teardown. Do not start a session or promote code just to run the collector without operator approval.

### Messaging fan-out check (#447)

**Infrastructure-only exception (#447, 2026-10-13).** If runtime is confirmed absent and service image candidates are missing, an operator may review a `-target=module.messaging` runtime plan (`topology=distributed`, `services_enabled=false`, `messaging_verified=false`, empty image digests) for a disposable broker-only drill. A valid `frontend_url` is still required by root-variable validation, but no ECS, network, database, cache or scaling resource may appear in the resource changes. Supply recipients privately and review only redacted resource summaries. This is an exceptional partial deployment, never a promotion or evidence that the full topology works; full plan/verification must follow when images are available. The normal verifier expects provisioned ECS services, so it cannot be used unchanged for this mode; run the reviewed `messaging-check.sh` helpers only after confirming no running producer/consumer, with private receipt storage and an EXIT cleanup handler. Destroy and verify the partial runtime after the drill; do not leave it for a later session with fresh databases.

Run only in an explicitly authorised distributed session, after reviewing the private runtime plan (share addresses/actions only) and applying the foundation's narrow SNS/KMS permission amendment. No customer-managed key is introduced: the domain topic uses `alias/aws/sns`; the alarm-metadata topic has the documented no-CMK exception. Configure team recipients as described in the [Terraform README](../../infra/swe5001/README.md#messaging-447). The automated `env-up.sh` gate keeps the relay off and services stopped until it passes; confirm the SNS emails during its 900-second propagation wait. Empty recipient input fails before any apply; unconfirmed subscriptions fail the gate unless the testing-phase opt-out `SWE5001_ALLOW_UNCONFIRMED_ALERTS=true` is set (see the [Terraform README](../../infra/swe5001/README.md#messaging-447)). The opt-out still requires recipients and still runs the 900-second wait and routing probes.

For a separate check against an existing quiet session, run from the same reviewed checkout and operator environment:

```bash
bash infra/swe5001/scripts/verify-messaging.sh
```

This changes no Terraform configuration. It snapshots desired counts, scaling bounds and all three suspension flags for **both commerce and notification**, suspends their targets, stops their tasks and waits for zero running counts. After the 900-second filter-settle bound, it requires confirmed email recipients (unless `SWE5001_ALLOW_UNCONFIRMED_ALERTS=true`, which logs a warning per pending subscription instead) and no visible/in-flight/delayed messages before publishing. The latter protects business messages' receive/redrive budgets: defer a busy-session drill, never purge retained messages. Approximate queue counts are not a proof of absolute emptiness, so unrelated receipts encountered defensively are retained privately and released on exit without deletion. Do not run a concurrent Terraform apply, environment workflow, or business workload during the check; manual CloudShell commands do not share GitHub's concurrency lock.

The script publishes unique ID-only envelopes with unsupported version `2147483647`, checks raw bodies and `eventType` attributes, and deletes only matching probes. ADR 0007 requires consumers to acknowledge unknown versions without effects, so delayed/duplicate probes carry no real notification recipients; #446 must preserve that rule. TeamSucceeded must reach both queues. PaymentSucceeded must reach notification and remain absent from audit across three 20-second long polls. Each receive search also has a 200-batch/180-second bound; exhaustion is inconclusive and fails, not proof of a missing event. The payment exclusion observation is bounded, not a guarantee about every future delivery.

EXIT/INT/TERM handlers release held receipts and restore both services' original counts and scaling flags on success or failure. If restoration fails, the script returns failure and retains private recovery records rather than claiming success; SIGKILL/runner loss cannot run a trap. Record the SHA, target, routing/readiness output and restoration in the daily log, and wait for services to reach steady state afterwards. Queue scaling, actual email delivery and poison-message acceptance remain separate V4/V7 drills (#448/#459); confirmed subscriptions alone do not prove receipt of an alarm email.

Bootstrap verification invoked by `env-up.sh` differs: only the nested verifier receives `--bootstrap`; it expects services already stopped and relay disabled, and never resumes them itself. An up failure after infrastructure changes explicitly stops services again and retains private snapshots (including after an edge/smoke failure). Rerun from the same checkout or tear down; do not restore an old publisher blindly after a failed routing gate. Recovery snapshots can retain pre-pause scaled counts that a later retry sees as zero; restore those counts only after readiness succeeds, using the reviewed `restore_services` helper and the private snapshot. Keep paths/receipt handles private and never attach the full state or a secret-bearing plan.

After the session, `env-down.sh` deletes the topic, four queues, subscriptions, alarms and scalable target. `env-status.sh` treats stale tagged SNS/SQS/alarm ARNs as deleted only after successful type-specific lookup or a recognised not-found response; access-denied/network failures remain failures. Save evidence before down: there is no persistent event archive.

### HTTP scaling and dashboard (#457)

For the deferred fresh campaign after the reviewed promotion has merged and completed, use the [post-merge HTTP load-test plan](http-load-retest.md). It sequences a new-release V2 baseline, subnet/connection-budget checks, generator sizing, V3 evidence and teardown; preparing that plan does not start or authorise a session.

Distributed sessions default to a commerce minimum of two tasks; identity and notification start at one. V2 uses the issue-specified single-task monolith, not a distributed session with only its commerce floor changed; use the [k6 preparation and preflight procedure](../../perf/k6/README.md). For distributed V3, export `TF_VAR_commerce_min_capacity=1` before `env-up.sh`; restore it to 2 for V5 and the presentation. Export `TF_VAR_commerce_requests_per_target` with the reviewed V2 candidate converted to requests/minute; the checked-in default 200 is provisional. These Terraform environment variables must remain set consistently for the session. `env-up.sh` reads the edge distribution ID automatically; a direct full distributed runtime plan must supply `cloudfront_distribution_id` too.

Open the runtime `dashboard_name` output (`blocbuy-swe5001`) in CloudWatch. Capture Edge, Services and Messaging rows with live data, plus ECS task ENIs/subnet AZs proving commerce placement in both AZs; two desired tasks and enabled AZ rebalancing alone do not prove placement. Existing scaled counts above the selected floor are preserved on reruns. The dashboard is runtime-only and `env-status.sh` checks its exact name because dashboards cannot be tagged.

Before using GitHub workflow sessions after V2, persist `SWE5001_COMMERCE_REQUESTS_PER_TARGET` in the `swe5001-demo` environment; both manual and promotion up consume it. `SWE5001_COMMERCE_MIN_CAPACITY` defaults to 2 and can be set to 1 for a workflow baseline, but restore 2 before V5/presentation. These are non-secret settings, not deployment switches. CloudShell exports alone do not persist calibration into later GitHub runs. The commerce floor runs on regular Fargate to avoid independent Fargate/Spot spreading; actual two-AZ placement remains a live check. Dashboard rows use separate charts for incompatible units, retaining a one-minute period.

Selecting minimum 1 does not reduce an existing scaled count: use a fresh baseline session, or separately perform V2's authorised suspend/scale-to-one procedure before measuring. Startup deliberately preserves higher counts rather than silently undoing autoscaling.

2026-10-16 operator checkpoint: [V2 and dashboard evidence](evidence/20261016-http-baseline-dashboard/README.md) records the approved/persisted 12000 requests/target/minute candidate and demo floor 2. Saturation was not observed; V3 must validate transfer from the older monolith image to the distributed candidate. Verify the live target value, its ALB/target-group resource label, both scaling targets' bounds and **all suspension flags**, not just startup exit 0. The interrupted-bootstrap retry returned success with commerce scaling suspended and notification still at minimum 0. Messaging readiness had passed before the operator restored commerce 2–6 and notification 1–4 with flags false; the snapshot-restoration defect is not fixed by that repair. Do not automatically unsuspend an intentional operator/baseline pause or bypass messaging readiness. A later CloudFront `GetMetricData` SCP denial also means an earlier working dashboard screenshot is not proof of ongoing metric access.

Keep Terraform backend working data separate for each root. Do not export one shared `TF_DATA_DIR` across `edge` and `runtime`: `env-up.sh` initializes both, then returns to edge, and shared backend metadata can make the edge apply fail. Under CloudShell home-storage pressure, use a private `/tmp` **`TF_PLUGIN_CACHE_DIR`**, leaving each root's `.terraform` backend metadata separate; never publish raw runtime/edge state or plans. **10 November correction:** never host a long HTTP acceptance observer in detached CloudShell. AWS's 20–30-minute interactive idle limit is not reset by a running process, an open tab or `nohup`. Use the [persistent capture procedure](http-load-collection.md) with explicit credential lifetime and shared heartbeat checks; keep restoration independent and preserve interrupted evidence. Short interactive recovery commands remain separate. Check process, phase and exit receipts after interruption; never launch a concurrent retry merely because the UI disconnected.

### Demo data (distributed)

Each service has its own database. The demo catalogue (10 products, deals, policies) is seeded only into `commerce`; `identity` starts without users and admins. For admin or START/JOIN demos, after section 5:

1. Sign in once with Google through the session URL; this creates the account in `identity`.
2. Grant the admin role, as an operator with the same environment as `env-up.sh`:

   ```bash
   infra/swe5001/scripts/grant-admin.sh '<google email>'
   ```

   It runs [`scripts/grant-dev-admin.sql`](../../scripts/grant-dev-admin.sql) as a one-off `db-init` task against `identity` and prints `granted = 1` (or `already_admin = 1` on a rerun); it fails when the account has not signed in yet. The email is written to the `db-init` log group.
3. Sign out and sign in again: roles travel in the session (#467), so the grant takes effect at the next sign-in. An admin request such as the admin product list (`GET /api/v1/admin/products`) now succeeds; it is served by `commerce-svc` from the role in the session.
4. Make listings startable (team terms and a team price policy in force) through the audited admin API, with the admin's `blocbuy-token` cookie value:

   ```bash
   BLOCBUY_BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net/api/v1 BLOCBUY_TOKEN=<cookie value> scripts/seed-dev-demo-listings.sh
   ```

   It is idempotent and prints a summary; `--check` makes no writes. A buyer account can then START a deal on one of those listings; the START also exercises the actor check from `commerce-svc` to `identity-svc` over Service Connect (#468).

### Acceptance evidence (#456)

Routing per service, signed in (the smoke suite cannot prove it): use the browser's authenticated session through CloudFront after the first Google sign-in; `GET /api/v1/auth/me` and `GET /api/v1/notifications?page=1&size=1` both answer 200. For notifications also require `code = COMMON_200_000`, a `data.items` array (which may be empty), and `X-Trace-Id`; correlate the trace with `notification-svc` logs. Once the session passes Sa-Token, a service that does not own a route answers 404 (`NoResourceFoundException`), so the two 200s prove the ALB rules to `identity-svc` (priority 10) and `notification-svc` (priority 20); the admin product list answering from `commerce-svc` covers the catch-all. Record status and trace only, never the session cookie or private notification contents.

`session-report.sh` runs fixed, read-only SQL once as the `db-init` task (no arguments; the report holds no emails or tokens):

```bash
infra/swe5001/scripts/session-report.sh
```

It prints, per database, the catalogue row count and whether the `R__demo_catalog_seed.sql` migration ran (expected only in `commerce`), the number of accounts holding the admin role in `identity` (1 after the grant), and the `sys_scheduler_lock` rows in `commerce` (`lock_key`, `owner`, `locked_until`, `version`). Run it before the grant and again at the end.

Success requires task exit 0 and the final `session-report: done` marker. If log delivery is incomplete, the script exits non-zero; any printed rows are partial evidence only. Read the complete task stream in `/blocbuy/swe5001/db-init` before recording the report.

One runner per job with two `commerce-svc` tasks (the default floor):

1. Confirm both tasks are `RUNNING` for the whole check (`aws ecs list-tasks --cluster blocbuy-swe5001 --service-name commerce-svc`, then `describe-tasks` for their AZs).
2. Make work due, so a run logs at `INFO` (a skipped run logs at `DEBUG` only). Nothing becomes due sooner than 40 minutes: the minimum team lifetime and the purchase quote window are both 2400 s. Right after the admin grant and listing seed, START on `US34-04` (target 2, lifetime 2400 s) and leave it unpaid; note the time and keep the session up for at least 45 minutes after it.
3. At least 45 minutes after the START, in CloudWatch Logs Insights on `/blocbuy/swe5001/commerce-svc`:

   ```text
   fields @timestamp, @logStream, @message
   | filter @message like /Team expiry sweep|Intent expiry sweep|Refund worker/
   | sort @timestamp asc
   ```

   Each run's line appears in exactly one task's log stream.
4. Run `session-report.sh` right after the START and again after step 3: each `lock_key` has one `owner`, the host name of one commerce task, and `version` grows between the reports.

Record the outputs (redacted) and the Logs Insights result in the day log before `down`.

## 6. During the session

For the separately approved V4/V5/identity/V7 campaign, use the [shared validation session plan](aws-validation-session-plan.md) for phase isolation, the reviewed V4-only runtime-plan exception, restoration, evidence and time budget. Preparing that plan or its stopped replacement generator does not authorize the application session. The November campaign must recheck current state and cleanup-role readiness rather than treating the historical October timing/cost allowances below as current guarantees.

- **Do not merge into `prod`.** A promotion starts `swe5001-deploy`, whose `down` job destroys the session.
- **Finish before 23:00 SGT.** The nightly `down` (`ENABLE_SWE5001_NIGHTLY_DOWN=true`) destroys `runtime` then.
- Do not change `runtime` resources in the console or run Terraform locally against `runtime`; the next `down` would leave drift or leftovers.
- The databases start empty apart from the demo catalogue seed (`commerce` only with `distributed`); data created in a session, including the admin grant, is lost at `down`.

## 7. Destroy

1. Dispatch `down` (about 9–10 minutes):

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=down
   ```

2. Confirm in the run log: `Destroy complete! Resources: <n> destroyed.` (71 with `monolith`) followed by `no runtime resource tagged Stack=runtime`. The run fails if anything is left.
3. Optional second check at any later time:

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=status
   ```

   Green means nothing tagged `Stack=runtime` exists. Deleted resources stay listed in the tagging API for over an hour; `env-status.sh` checks each one with its own describe call, so they do not count.

After `down`, `/api/*` returns 502 again (section 1).

### 12 November 2026: experimental log-bucket recovery

The normal script has no archival step and does not manage the separate load generator. It destroys runtime data, queues and log groups, retries once after 60 seconds, then calls the absence check. The R3/session-added ALB-log bucket was in runtime state but not the released `runtime/alb.tf`; its saved `force_destroy=false` meant a nonempty bucket would block deletion, potentially after other resources were destroyed. The late nightly job first failed on the GitHub apply role's missing bucket-read permissions. Do not infer absence from the cron or widen the role blindly; see the [dated failure and operator recovery](evidence/20261112-issue-789/README.md).

The owner separately authorized operator recovery: review runtime-only saved plans with the existing authenticated assignment; remove writers/compute while retaining the bucket/security controls; privately archive and SHA-256 readback-verify all objects with the ALB writer absent; delete only verified originals; then destroy the empty bucket and independently verify absence. That recovery removed 127 Terraform-managed resources, left empty managed runtime state and passed normal `env-status.sh` plus 51 direct checks. The separate stopped generator was retired only after an encrypted disk snapshot completed; its original disk and temporary support resources are absent. The private evidence/log S3 bucket and one encrypted disk snapshot remain chargeable archives. Foundation/edge and SWE5006 were not targeted. This historical operator exception does not change the normal script, repair the nightly role, authorize another session or approve archive disposal; [safe projection](evidence/20261112-issue-789/cleanup.json).

## 8. When something goes wrong

| Symptom | Meaning | Do |
| --- | --- | --- |
| `up` red | `runtime` **stays up and keeps costing** | Read the cause (below), then run section 7 |
| `up` red at `wait for services to be stable` or the smoke suite | A service does not start or does not answer | In CloudShell (admin), per service (`identity-svc`, `commerce-svc`, `notification-svc`, or `monolith`): `aws logs tail /blocbuy/swe5001/<service> --since 1h`, and `aws ecs describe-services --cluster blocbuy-swe5001 --services <service> --query 'services[0].events[:10]'` for placement errors such as missing parameters. `commerce-svc` answering 500 on purchases while the others are healthy usually means `identity-svc` is unreachable (fail-closed by design) |
| `commerce-svc` purchases or admin listing actions answer 500 and its log shows `UnknownHostException: identity-svc` or connection refused | Its tasks were deployed before `identity-svc` registered its Service Connect endpoint; Terraform creates commerce after identity to prevent this, but the endpoint registration itself is unverified on AWS | Operator: `aws ecs update-service --cluster blocbuy-swe5001 --service commerce-svc --force-new-deployment`, then wait for it to be stable |
| The notification DLQ alarm fires, or V7 left a message in `blocbuy-swe5001-notification-dlq` | The consumer could not apply a message 5 times. V7's message is a malformed envelope by design, so it is expected; any other message is a malformed envelope from the producer or a failing dependency | Follow the [notification DLQ runbook](../../backend/docs/operations/notification-dlq-redrive.md): inspect, find the `messageId`/`traceId` in the `notification-svc` log, and decide per message. Record the V7 poison message (`eventId`, `eventType`, `version`, `traceId`, reason) in the evidence and do not redrive it (`start-message-move-task` would only bounce it back after 5 more receives); delete it with `delete-message` so the alarm returns to OK, otherwise it stays in ALARM and later dead letters send no email. Redrive with `start-message-move-task` only retryable failures after the cause is fixed; replay is idempotent |
| `up` or `down` waits in `queued` | Another state-writing run is ahead in `swe5001-tfstate` | Wait; check section 4, step 1 for a stale `down` |
| `down` red with `Destroy complete!` | Verification found a resource type it does not know | Look at the listed ARNs in the console; if they are gone, re-run `status` later |
| `down` red before `Destroy complete!` | Destroy failed (often a permission or dependency error) | Re-run `down` once; if it fails again, use the fallback below |
| Terraform reports a state lock | A cancelled run left a lock | Do not force-unlock while a run is active; ask an operator to check `s3://blocbuy-tfstate-402178232802/swe5001/` for `runtime.tfstate.tflock` |

Fallback destroy, as an operator in CloudShell, from a clone of the repository with Terraform 1.16 on `PATH`:

```bash
export TF_DATA_DIR="$(mktemp -d /tmp/swe5001-down.XXXXXX)"
```

```bash
AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=blocbuy-tfstate-402178232802 infra/swe5001/scripts/env-down.sh
```

It is the script the workflows run and ends with the same verification. Never paste plan output, parameter values or decrypted responses into a chat, issue or PR.

## 9. Cost guards

- A session of two hours costs well under USD 1; a forgotten one is capped by the nightly `down` at under a day (about USD 3).
- Once a week, check Cost Explorer for the last seven days by service: RDS, ElastiCache, EC2 and Elastic Load Balancing should be near zero outside sessions. The AWS budget is off (`enable_budget = false`).
- Keep `ENABLE_SWE5001_NIGHTLY_DOWN=true`.
