# AWS validation session: V4, V5, identity outage, V7 and V8

Prepared 13 November 2026 for #458/#459, with shared evidence for #448. **Reserve one 3–4½-hour AWS window**, rather than rebuilding for each test. Run separate phases, not simultaneous faults. Keep the existing [V3 r4 result and its limitations](evidence/20261111-v3-r4/README.md); this plan does not call for another V2/V3 campaign.

**14 November execution/status amendment:** The original window, order, permissions and workload below are planning history, not instructions to repeat the campaign. Subsequent separately authorized execution established [V5 R3](evidence/20261114-v4-r5-live/v5-r3-pass.md), experimental [V4 R5](evidence/20261114-v4-r5-live/v4-r5-pass.md), qualified [V7 R4](evidence/20261114-v7-r4-live/README.md) and one [V8 R4](evidence/20261114-v8-r4-live/README.md) create–smoke–destroy pass. V4 used 80,000 teams under the later producer authorization; V7 used temporary 10-second application consumer visibility, restored to 60 seconds. Identity remains failed/deferred. These outcomes authorize no new run, promotion or archive disposal; the original clean-checkout requirement does not reclassify hash-bound overlays that were uncommitted at execution.

## Scope and permission

This is a future-session plan, not permission to start the application, send load, inject faults, operate Stripe or dispatch workflows. The owner separately approved replacement-generator preparation before sleeping: one Singapore `t3a.2xlarge`, encrypted 40 GiB disk, SSM access, tool/fixture checks, then stop. The [preparation record](evidence/20261113-aws-preparation/README.md) records that narrower operation. Obtain approval for the application window after the owner returns around 09:00 SGT.

[Issue #459](https://github.com/PPsyrius/iss-groupbuy-team8/issues/459) requires V5, identity outage and V7 in **one session**; V8 reuses lifecycle timings. Its 11 October rescope removed V6's broken-image drill and the forced-failure promotion. No `BLOCBUY_FAIL_HEALTH` switch or failure workflow input exists. V6 is optional new scope, not a missing step to improvise during this session. Do not change submitted reports for this preparation.

## Time budget

These are operator allowances, not measured recovery guarantees. Offline preparation/review is about **2–4 hours**; generator setup/checks can overlap it. Review/signing the new tooling and live account/settings checks still precede execution.

| Order | Phase | Allowance | Evidence reused |
| --- | --- | ---: | --- |
| 0 | Fresh sign-in, approve scope/cleanup, restart prepared generator, inspect workflow queue and current state | 10–15 min | Account/Region, source identities, generator readiness |
| 1 | One distributed `up`, messaging readiness and smoke | 35–50 min | V8 startup/smoke; bootstrap fan-out receipts for #448 |
| 2 | Authenticated routing, active synthetic recipient, startable listing and connection budget | 15–20 min | Identity/notification routing; fixtures for later phases |
| 3 | V5: 10-minute steady browse; stop one commerce task after 3 minutes | 15–20 min | #459 self-healing, ECS events, client results |
| 4 | Identity outage and restoration, with 15-minute light browse and real provider delivery/redelivery | 20–30 min | #459 fail-closed/recovery; PaymentSucceeded effect where observed |
| 5 | V4-only configuration rollout, 2,000-team burst, capture, restore normal configuration | 35–50 min | #458 burst/scale/drain; #448 burst; TeamExpired effects |
| 6 | V7 poison, valid-message control, DLQ/alarm/email, identified-message deletion and alarm reset | 20–25 min | #459 poison; #448 poison/isolation |
| 7 | Save/verify evidence, runtime `down`, independent absence check, generator stop | 20–25 min | V8 destroy/absence; separate generator stop receipt |
| | Working phases | **170–235 min** | |
| | Contingency, not another full campaign | **20–30 min** | |

Total allowance is **190–265 minutes**, rounded to a 3–4½-hour reservation. A 09:00 start therefore aims to finish around **12:10–13:25 SGT**. A second cold startup, a longer provider retry or a revised test design needs a new time decision; do not consume teardown time silently. The core #459 session without V4 is roughly 2½–3½ hours. Keeping V4 in that same environment avoids another startup and destroy, roughly 45–70 minutes of repeated overhead.

The [existing V8 samples](impl-verification-observability.md#v8-rebuild-from-zero) measured distributed full-up times of **31m37s / 32m17s** and runtime destroys of **709s / 761s**. The startup allowance includes the 900-second messaging wait; do not add it twice. Those distributed samples missed the under-30-minute target. V8 already has three clean-run records, so it does not need three new rebuilds.

| Optional work, only if separately approved | Additional preparation | Additional live time |
| --- | ---: | ---: |
| Two-buyer paid START/JOIN to demonstrate TeamSucceeded notification for #450 | Fixture/flow review | 15–25 min if listings/accounts are ready |
| Revive V6 broken-image/circuit-breaker drill | 3–6 h for a safe failure mechanism, tests, image and review/CI | 30–60 min; not included above |

## Readiness before starting paid application runtime

- Confirm account `402178232802`, Region `ap-southeast-1`, environment `swe5001-demo`, distributed topology and **only** `https://d3lvq02o2e7tcn.cloudfront.net`. Never use SWE5006 dev. The 12 November absence receipt is historical, not a current inventory.
- Approve the operating window, costs, fault actions, fixture writes and named cleanup owner. Resolve active/queued session or promotion jobs; do not cancel someone else's operation blindly. No promotion into `prod` during the window. Finish well before 23:00 SGT, but do not rely on nightly down: its 12 November failure was not repaired by operator cleanup.
- Review/sign the preparation changes, obtain a clean execution checkout and record its real `TEST_SHA`. Record released `IMAGE_SHA`, build/scan/promotion links and all three digests separately. The staged generator snapshot is explicitly uncommitted and is **not** a clean reviewed checkout; replace/bind it to the final reviewed tooling before live work.
- Follow the [environment runbook](runbook-environment-session.md) for startup/readiness, not a new promotion. `swe5001-env.yml` has only `action`, `digests` and `topology` inputs and runs from `prod`. Its 75-minute timeout and two-hour role session apply to individual jobs, not the whole manual test window.
- Review actual runtime sizes, pool limits, commerce request target and all scaling suspension flags. Do not assume the retired R4 overrides survive a rebuild: its 1-vCPU/2-GiB commerce, `db.t4g.small`, pool cap five and CPU guard were session settings. Run the [connection-budget report/calculator](../../perf/k6/README.md#connection-budget-before-v3) before load or four-worker scaling; the report starts a billed one-off task. Stop on an insufficient budget rather than resizing without approval.
- Start with `v4_demo_enabled=false`, two stable commerce tasks in two AZs, one identity task and one notification task; verify actual task digests, healthy targets and authenticated routing. The new steady-browse preflight supports the normal **ALB-only** request policy at bounds 2..6. Extra policies, including R4's CPU guard, require a separate tooling/configuration review; never remove them silently just to pass preflight.
- Confirm alert recipients can receive and confirm SNS subscriptions during startup; require `SWE5001_ALLOW_UNCONFIRMED_ALERTS=false`. Preserve the bootstrap fan-out evidence instead of rerunning the destructive-to-service-availability verifier during load. Confirmed subscriptions are not proof of a received alarm email.
- Use a persistent generator and persistent authenticated observer host, never detached CloudShell. Keep the workstation awake. Before every phase require a first real observation, a fresh heartbeat, adequate remaining capture time and observed credential validity for the capture **plus 15 minutes**. Renew between phases where needed; do not export keys/tokens into chat or result files.
- Keep results private and outside Git. The new [drill tools](../../perf/drills/README.md) supply steady GET load, read-only ECS/queue observations and an offline poison payload builder. Faults, provider actions, valid controls and cleanup remain short explicit operator actions. No all-in-one fault runner is claimed.

## Phase 1: startup and shared fixtures

Use the existing release/session procedure and record start/end, create/readiness/smoke durations, workflow ID and every actual task revision/digest. Save the bootstrap TeamSucceeded-to-both-queues and PaymentSucceeded-to-notification-only routing receipts for #448. Its unsupported-version readiness probes are infrastructure evidence, not business notifications or poison messages. Do not run a second 15-minute messaging verifier just to obtain another copy.

Sign in through the SWE5001 URL. Verify signed-in `/api/v1/auth/me` and `/api/v1/notifications?page=1&size=1` return the correct 200 Result, with notification trace correlation; anonymous 401s do not establish routing. Prepare an active synthetic recipient and a startable demo product through the existing [demo-data procedure](runbook-environment-session.md#demo-data-distributed). Never grant privileges merely for browsing.

For identity outage, prepare one **new, unfulfilled Stripe test-mode Checkout immediately after V5**, while identity is healthy; retain its intent/payment/event identifiers privately. Do not leave a 40-minute quote/Checkout expiring while V5 and fixture setup run. Verify the actual SWE5001 webhook endpoint and test-mode configuration without printing secrets. An already-settled payment or a generic Stripe CLI sample with no matching local payment cannot prove fail-closed settlement. Use the fixed [identity state report](../../perf/drills/README.md#identity-state-report) before/outage/recovery/duplicate delivery; session-wide totals can be changed by scheduled work and are not a substitute. Do not invent an arbitrary-SQL HTTP endpoint.

## Phase 2: V5 self-healing

1. Start the read-only observer and named generator resource monitor; retain first samples. Collect `http-preflight.sh v5`, transfer it to the generator and begin load within five minutes. Bind the observed two commerce tasks to the reviewed image and target route.
2. Run the new 10-minute steady browse driver at **50% of the existing V2 candidate**: `KNEE_RPS=200` means 100 requests/s and 60,000 workload GETs, plus one setup GET. This is half a tested lower-bound candidate, not half a discovered saturation knee. Keep the existing 70/30 list/detail mix, timeouts and strict zero-error/zero-drop gates.
3. At load minute 3, record UTC immediately before/after one `aws ecs stop-task` for **one exact preflight commerce task ARN**, leaving desired count two. Do not stop identity, change desired count, deploy or adjust scaling during the measurement. Preserve the stop response and old/new task identities.
4. Record replacement `createdAt`/`startedAt`, first observed RUNNING, target health and restored service stability separately. Compare replacement against the **120-second** target from fault initiation; do not start the clock at replacement creation or credit a manually started/scaled task. Running is not the same as ALB healthy. Capture continues to the end of the load and a post-run observation.
5. Retain original k6 exit, raw samples and summary; run `analyze-browse.mjs`. Zero non-2xx alone is insufficient if Result validation fails, the request budget is short or the generator drops work. If desired count rises above two, preserve it and qualify the isolated-replacement interpretation rather than suspending scaling mid-run. A browse pass is not an automatic V5 pass.

Reset: identity available, normal configuration, two stable healthy commerce tasks, no unrelated deployment and quiet queues. Failed HTTP or replacement >120 s is a measured failure, not permission to relax the target or restart invisibly.

## Phase 3: identity outage

1. Create/check the pending Checkout and baseline state at the start of this phase, with sufficient time before its real expiry. Use a new run directory, fresh `identity` preflight and the 15-minute **2 requests/s browse-only** driver. The deliberate START/webhook failures are separate probes, not included in browse's zero-error metric. Start the observer before any fault, with at least sixteen minutes remaining before starting that load (use a 25-minute capture for this phase).
2. Save identity's original desired count and verify no unexpected identity autoscaling controller. In this single-identity setup, explicitly set desired count to **zero**, then wait for actual zero running/pending identity tasks. Simply stopping its one task would let ECS replace it and may not provide a usable outage interval. Keep commerce, Redis and notification running.
3. During a short planned outage, issue one authenticated START-intent request for the prepared listing; require 500 and no surviving business effect. Use the existing cookie privately and `X-BlocBuy-Request: checkout`; no token in arguments, logs or evidence. Complete the prepared test-mode Checkout through the provider and capture the resulting real signed webhook's **500** delivery. No fake signature, unsupported event or previously consumed webhook may substitute.
4. Restore the saved identity count independently of the observer's survival. Aim for no more than three minutes of intentional outage; if the provider event has not arrived, restore anyway and record that part as not verified. Wait for identity health/authenticated routing, then use provider redelivery of the **same event** and verify 2xx and exactly one settlement. A second delivery must not create another member/order/stock deduction. Record provider event ID, delivery times/statuses, trace IDs and scoped state counts, never the signed payload, cookie or keys.
5. Require browse to remain healthy across outage/restoration. If START/webhook returns 401/403/404 or a payment lookup is unknown, fix the fixture/route before calling it fail-closed evidence; those outcomes do not meet the expected 500 behavior. Missing rollback/state queries remain a stated NFR-66 limitation.

Reset: restore and verify identity, both commerce tasks and authenticated reads before V4. Let provider retries and real events settle; require quiet main/DLQ queues. Do not keep identity down while debugging capture or waiting for a retry.

## Phase 4: V4 real expiry burst

Follow the existing [V4 procedure and analyzer](../../perf/k6/SCALING.md#v4-real-expiring-teams-never-synthetic-sns-publication), not direct SNS injection.

1. With HTTP/fault work stopped, review a **private runtime-only saved plan** enabling `v4_demo_enabled=true` on the distributed topology. Preserve the already-verified readiness values, digests, sizes, policies and recipients. Permit only the intended commerce task-definition/service rollout; reject unrelated network/data/IAM changes. This is a separately approved exception to ordinary workflow-only runtime changes, not an invented workflow input. Do not rerun `env-up.sh`, which pauses services and repeats readiness.
2. Verify the effective commerce `demo` profile, scheduling/relay on, batch 500 and one-second expiry interval; verify notification's consumer and digest. Begin with **one stable notification task**, active unsuspended bounds 1..4 and empty visible/in-flight/delayed main/DLQ counts. Do not manually create a four-task start.
3. Use an existing active synthetic identity and existing `DEMO-P-0001`. Give this attempt a new label and private empty results directory. Run `v4-burst.sh` once with the guarded seed/digest inputs. It creates exactly **2,000 unpaid teams plus one membership each**, due in 60 seconds, then observes the real expiry → outbox → SNS → SQS → consumer path. It creates **no payment/order rows and no Stripe refunds**.
4. The collector waits at least five minutes and at most 30 by default, with 15-second samples and billed seed/report tasks. Retain seed, database report, observations, stderr, original return codes and CloudWatch backlog/age/task graphs. Analyze all 2,000 source/effect/marker counts, zero failed outbox rows, post-expiry backlog, later growth to four **before** final drain, a final quiet interval of at least three samples spanning 30 seconds, and an empty DLQ throughout.
5. A small burst may drain before CloudWatch/ECS can react. Preserve a scale-out failure/inconclusive ordering even when all effects arrive. **Do not inflate the seed, slow consumers, lower thresholds, force scaling or reuse pre-existing workers to manufacture a pass.** After ambiguous seed failure, inspect the original run-scoped report before any retry; never reseed the same label or erase evidence.
6. Restore `v4_demo_enabled=false` through a separately reviewed saved plan, wait for normal healthy commerce tasks and capture effective settings. Do not take HTTP/fault measurements while that rollout or accelerated expiry is active. Wait for notification/queues to settle before V7.

## Phase 5: V7 poison, isolation and alarm

1. Require healthy services, empty notification main/DLQ, the real consumer on, source visibility **60 s**, `maxReceiveCount=5`, confirmed email subscriptions and the DLQ alarm **OK** with actions enabled. Record configuration; do not set the alarm state artificially. Start a 20-minute read-only V7 observer with new private results.
2. Prepare one supported-but-malformed `PaymentSucceeded` **version 1** envelope with the offline builder; set its optional `V7_CONTROL_RECIPIENT_ID` to prepare the separate valid control too. Its missing required payload fields must fail decoding. Publish **once** to the actual domain SNS topic with message attribute `eventType=PaymentSucceeded` and its unique trace attribute. Missing routing attributes can be filtered out; version `2147483647` is acknowledged without effect and is not poison.
3. Retain publish time/SNS ID, envelope event ID/trace, notification SQS ID from logs/DLQ, and actual consumer WARN records for receive counts **1–5**. Do not receive from the main queue to inspect it: that would spend the consumer's retry budget. Allow roughly five minutes for redrive, then metric/alarm/email delay; do not promise that all three occur at the same instant.
4. While poison is retrying, publish one separately identified **valid ID-only control** to the same route for the active synthetic recipient, using all required `PaymentSucceededV1` fields. Record its one marker/notification while poison continues retrying. This is explicitly a notification-transport control, not a real payment or replacement for V4's business-source workload. If using a real Sandbox event instead, arrange its new payment beforehand. No valid control observed means poison isolation is not established.
5. Record **injection**, **DLQ arrival**, **CloudWatch ALARM** and **email receipt** separately. Preserve both injection→alarm and DLQ→alarm durations when assessing #448's five-minute alarm requirement; agree the timing interpretation before execution, never silently restart its clock. A synthetic alarm notification or a confirmed subscription is not a real-DLQ detection result.
6. Follow the [DLQ runbook](../../backend/docs/operations/notification-dlq-redrive.md): inspect the DLQ without hiding messages before alarm capture, preserve identifiers/reason, then delete **only this approved poison message**. Never purge either queue or bulk-redrive poison. Verify main/DLQ quiet and alarm back to OK; if another unknown dead letter appears, stop and classify it rather than deleting it.

## Evidence reuse and independent outcomes

| Evidence | Can support | Does not establish |
| --- | --- | --- |
| Same-session bootstrap routing probes | #448 fan-out routing | Actual TeamSucceeded/PaymentSucceeded business effects |
| V4 complete source/effect/scale/drain package | #458 V4; #448 burst; TeamExpired part of #450 | V3 linearity, refunds or V7 poison isolation |
| Identity outage + successful real webhook redelivery | #459/NFR-66; observed PaymentSucceeded notification | TeamSucceeded or all #450 criteria |
| V7 five receives, DLQ, valid control, real alarm/email and reset | #459 V7; #448 poison/isolation | Duplicate/rollback CI criteria or V4 scale-out |
| Existing clean-run timing table + this session's separately verified lifecycle | V8 | Under-30-minute distributed up or database-content recovery |

Keep separate pass/fail/not-verified results for every gate. #448 still needs its duplicate/rollback/poison CI evidence; #450 additionally needs actual TeamSucceeded and readable per-recipient business effects. Do not close either just because a related drill passed. No extra #450 paid-flow or V6 run is included without approval.

## Stop, restore, preserve and destroy

- Stop adding faults/work when capture fails, credentials/heartbeat become stale, digests/settings drift, business messages unexpectedly dead-letter, another operator overlaps, or the teardown reserve is threatened. Restore identity and V4's default-off configuration first; restoration must not depend on a finally block, CloudShell or the observer process surviving.
- Save summaries/raw samples, exact sources/hashes, UTC event ledger, task/target-health/scaling/queue/alarm evidence and redacted provider metadata **before** `down`. Verify private artifact copies/readback and retain failed attempts. Runtime teardown deletes databases, queues and log groups; there is no default archive step.
- Follow the normal [destroy and independent absence procedure](runbook-environment-session.md#7-destroy). Confirm current destroy permissions before starting the session; a scheduled run or the 12 November operator exception is not proof the nightly/apply role now works. Do not widen IAM permissions or reuse a secret-bearing old plan to make cleanup pass.
- The generator is separate from runtime teardown. Stop it after verified result transfer and read back its state; retained encrypted disk and evidence storage remain chargeable. Termination, snapshot restoration or archive disposal needs its own approval. Recheck boot timers and CPU credit mode after the morning restart; preparation used **standard credits**, not an unbounded Unlimited billing commitment.
- Record current create/smoke/destroy/absence timings for V8 only if the fresh pre-start check establishes an empty runtime. Reuse existing V8 samples regardless; do not run another cycle for paperwork. Publish reviewed sanitized evidence only when requested, citing the eventual actual merge commit rather than repeatedly repinning feature heads.
