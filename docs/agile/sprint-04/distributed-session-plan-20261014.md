# #456 closure: distributed ECS session acceptance — plan

Date: 2026-10-14 · Branch: `docs/456-distributed-session-plan-20261014` from `dev` at `35745223` · Issue: #456 (Sprint 4 container #465) · Owner: @l0sgAi · Related: [C1–C3 plan](service-runtime-plan-20261012.md) (merged in #472), [microservice split plan](microservice-split-plan-20261012.md), [session runbook](../../swe5001/runbook-environment-session.md), [handoff](../../swe5001/HANDOFF.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md)

> **Status (2026-10-14): reviewed, all §7 defaults accepted. Phase A implemented on this branch ([day log](log/2026-10-14.md)); Phases B–D pending.**

## 1. Goal

The #456 code (image matrix, `distributed` map, scripts, per-database demo data) merged in #472 on 2026-10-12. Every blocker the issue lists is now merged too. What is missing is the proof on AWS, plus three small gaps found while reading the code on 2026-10-14. This plan covers both and ends with #456 closed.

| # | Open #456 criterion | Closed by |
| --- | --- | --- |
| AC-S1 | One session reaches steady state for all three services, Google sign-in works through `identity-svc`, smoke suite green, then `env-down.sh` and a clean `env-status.sh`; run links in the day log | Phase B (B3–B5, B8), Phase C |
| AC-S2 | Same session: catalogue seed only in the commerce database; after the grant and a second sign-in an admin request on `commerce-svc` succeeds; at least one listing is startable | Phase B (B6) |
| AC-S3 | With two `commerce-svc` tasks and scheduling on, each leased job run is taken by one task only (logs or `sys_scheduler_lock` owner), recorded in the day log | Phase B (B7) |

Non-goals: V2 calibration, V3 scaling and the dashboard (#457); the routing drill, DLQ and consumer checks (#447, #448); the identity outage drill (#459); the `dev` → `prod` promotion itself (Sprint 4 plan, 10-19), which this plan only uses as a final leg.

## 2. Baseline (read on 2026-10-14 from `35745223`)

### 2.1 Scope items of #456

| Item | State | Gap |
| --- | --- | --- |
| 1. `distributed` map, ALB rules, Service Connect | Merged (#472): priorities 10/20/30, `/internal/*` block at 5, `connects_to` ordering | **Identity scaling 1/1/3 on CPU 50% was moved to #457, and #457 (#489) then decided "no CPU/identity policies".** Nobody owns it now (§3.3, Q1) |
| 2. Image matrix, per-service digests | Merged (#472): four ARM64 builds and scans | No candidate digests existed at the #447 broker-only session (log 10-14). Images upload only from `prod`, so the first session needs a hand push |
| 3. Per-service roles and environment | Merged: #472 (environment, secrets), #487 (SNS/SQS task policies, `BLOCBUY_EVENTS_*`) | None in code |
| 4. Scheduling on commerce only | Merged: `BLOCBUY_SCHEDULING_ENABLED=true` on commerce, `false` elsewhere | Two-task proof. `TF_VAR_commerce_min_capacity` defaults to 2 since #489, so a default session already runs two commerce tasks |
| 5. `distributed` accepted everywhere | Merged: `variables.tf`, `lib.sh`, `env-up.sh`, both workflows, `test-lib.sh` | **`perf/smoke/smoke.sh` has no notification check.** #215's endpoint merged in #478; the handoff C3 row already lists this gap (§3.1) |
| 6. Demo data per database | Merged: commerce-only seed, `grant-admin.sh`, `seed-dev-demo-listings.sh`, runbook section "Demo data (distributed)" | Never run on AWS. No scripted way to show the seed is **absent** from identity and notification, or to read `sys_scheduler_lock` (§3.2) |

### 2.2 Dependencies

| Issue | State |
| --- | --- |
| #455 service assemblies | Closed (#471) |
| #467 session roles outside identity | Closed |
| #468 remote identity check | Closed (#479, `afec4e83`) |
| #469 attention nicknames through the identity port | Closed (#490, `38cf1a87`) |
| #447 messaging | Merged (#487); issue open for its own session evidence |
| #457 HTTP scaling, dashboard | Merged (#489); issue open for its own session evidence |
| `prod` | Still `6c7a99ab` (monolith-era). `swe5001-env.yml` runs only from `prod`, so it cannot run `distributed` until the promotion |

### 2.3 Access

Checked 2026-10-14: the local IAM user `blocbuy-dev-losgai` is denied `ssm:GetParametersByPath`, `ecr:DescribeImages` and `ecs:DescribeClusters` on the SWE5001 resources. That is by design (SWE5006 debug user). **Every step in Phase B needs an operator with the SWE5001 admin credentials** (CloudShell or an admin profile). Q5 names the operator.

### 2.4 Facts the plan relies on (verified in code)

- `env-up.sh` (after #447) applies `runtime` with services stopped and the relay off, runs `db-init`, runs `verify-messaging.sh --bootstrap` (a 900-second filter wait, **confirmed email subscriptions required**), then starts services. A session therefore takes about 25–40 minutes, not the historical 13–15.
- `commerce-svc` scans no `com.blocbuy.notification` package (`CommerceServiceApplication`), but every assembly scans `SaTokenConfig`, whose interceptor runs on all `/api/v1/**` before handler lookup. Without a session every service answers 401 for any `/api/v1` path, owned or not (verified 2026-10-14 on a local `identity-svc` container: `/api/v1/notifications` and `/api/v1/no-such-route` both 401 `COMMON_401_001`; PR #492 review). Signed in, an unowned route answers 404 (`NoResourceFoundException` in `GlobalExceptionHandler`). So routing evidence needs a signed-in request.
- `LeasedJobRunner` logs a skipped run at `DEBUG` only. JSON logs run at `INFO`, so a skip is invisible in CloudWatch. A completed job logs at `INFO` only when it did work (for example `TeamExpirySweepJob` with something due). The lease owner is `$HOSTNAME/<uuid>`; on Fargate (`awsvpc`) the host name is per task.
- `grant-admin.sh` already runs fixed SQL as a one-off `db-init` task with container overrides (master credentials, private subnets, log group `/blocbuy/swe5001/db-init`). Its pattern can be reused for read-only reports.
- The image (`eclipse-temurin:21-jre-noble`) runs read-only; ECS Exec is off by default (`enable_exec = false`).

## 3. Design

### 3.1 Smoke check for `notification-svc`

Add check 5 to `perf/smoke/smoke.sh`: `GET /api/v1/notifications?page=1&size=1` without a session must answer 401 with the `Result` envelope and an `X-Trace-Id` header. Same shape as the `auth/me` check.

- Holds for every target the suite runs against: the `dev` EC2 monolith and the SWE5001 `monolith` both include `NotificationController`; `distributed` routes it to `notification-svc`.
- Not a routing check (§2.4): commerce would also answer 401. It proves the authentication envelope and trace id only. Routing to `identity-svc` and `notification-svc` is checked signed in during B5.
- The script stays read-only and credential-free.
- `env-up.sh` and both workflows call the same script, so nothing else changes.

### 3.2 Read-only session report (`session-report.sh`)

New `infra/swe5001/scripts/session-report.sh`, built on the `grant-admin.sh` pattern: one `db-init` task per database, **fixed SQL only, no arguments that reach SQL**. Output goes to stdout (and the `db-init` log group).

| Database | Query | Proves |
| --- | --- | --- |
| `identity`, `notification`, `commerce` | `select count(*) from domain.biz_product` and the `R__demo_catalog_seed` row in `flyway_schema_history` | AC-S2: seed in commerce only (identity and notification have the table from `db/migration` but zero rows and no seed migration) |
| `identity` | count of users holding `ADMIN` (count only, no email) | AC-S2: grant took effect |
| `commerce` | `lock_key, owner, locked_until, version` from `sys_scheduler_lock` | AC-S3: owners are task host names; `version` increases as runs proceed |

Why a script and not ECS Exec plus `psql`: Exec needs `enable_exec=true` (wider task permissions), the image has no `psql`, and a fixed report is repeatable and reviewable. Alternative in Q2.

### 3.3 Identity scaling (scope item 1)

Two options, Q1 decides:

- **(a) Descope, default.** #457 set the scaling scope to commerce (HTTP) and notification (backlog). Identity serves sign-in and the internal check only; its load in the demo is small. Record the decision in `impl-aws-infrastructure.md` §6.4 as a dated paragraph and in the #456 closing comment. No code.
- **(b) Build it.** `aws_appautoscaling_target.identity` (1..3) plus a target-tracking policy on `ECSServiceAverageCPUUtilization` 50 in `runtime/observability.tf`, suspended while `services_enabled=false`. The pause/restore logic in `env-up.sh`, `verify-messaging.sh` and `lib.sh` snapshots only commerce and notification today, so it would need a third service and new shell regression cases. About 3 h, and it touches the script paths #447 just stabilised, two days before the session.

### 3.4 Lease evidence (AC-S3) without code changes

1. Default session: commerce at 2 tasks (`TF_VAR_commerce_min_capacity=2`), regular Fargate base, two AZs (#489).
2. Make work due so a run logs at `INFO`. Nothing can be due sooner than 40 minutes: the minimum team lifetime is `ProductTeamTerms.MIN_LIFETIME_SECONDS` = `PurchaseQuoteWindow.SECONDS` = 2400 s, and a purchase intent lives for the same quote window. So START on `US34-04` (`scripts/dev-demo-listings.json`: target 2, lifetime 2400 s) as early as possible, right after the grant in B6, and leave it unpaid (checkout is off). About 40 minutes later the unpaid intent or the unfilled team becomes due and one commerce task logs the sweep at `INFO`. Do B6's remaining checks and B4/B5 evidence meanwhile.
3. Evidence:
   - CloudWatch Logs Insights on `/blocbuy/swe5001/commerce-svc`: lines from `TeamExpirySweepJob` / `PurchaseIntentExpiryJob`, grouped by `@logStream`. Each run's line appears in exactly one task stream.
   - Two `session-report.sh` snapshots a few minutes apart: `sys_scheduler_lock.owner` names one of the two task host names per key; `version` grows.
   - Both tasks `RUNNING` throughout (`describe-tasks`), so the second task really competed.
4. Optional, only with `TF_VAR_enable_exec=true` (Q6): `curl -s localhost:8589/api/actuator/prometheus | grep blocbuy_scheduler_runs_total` in each task. `outcome="skipped"` greater than zero on a task shows it lost the lease rather than not trying.

### 3.5 Where the session runs (AC-S1 wording)

AC-S1 says "`swe5001-env.yml` up on `prod`". `prod` cannot run `distributed` before the promotion, and the promotion must not happen before one manual session succeeds (status comment of 10-13: otherwise `env-up` fails at the stable-service wait and `down` destroys the environment). So:

- **Phase B (10-16):** manual session from a `dev` checkout with hand-pushed candidate images, as the runbook's "First distributed session" describes. Proves AC-S1 (functionally), AC-S2 and AC-S3.
- **Phase C (10-19):** the promotion run (`images` → `publish` → `up` → `release` → `down`) on `prod`, then one `swe5001-env.yml up` with `digests=release` (can be the 10-20 rehearsal). That is the literal AC-S1 leg. Q3.

## 4. Work breakdown

### Phase A — small PR (after this plan is approved), est. 2.5 h

| Id | File | Change |
| --- | --- | --- |
| A1 | `perf/smoke/smoke.sh` | Check 5 (§3.1); header comment lists five checks |
| A2 | `infra/swe5001/scripts/session-report.sh` (new) | §3.2; `guard_account`, fixed SQL, waits for each task, fails on non-zero exit |
| A3 | `infra/swe5001/scripts/test-db-init-task.sh` (new), `lib.sh`, `grant-admin.sh` | Offline: stubbed `aws`/`terraform`, asserts override size < 8 KiB, three databases, read-only connections, no write statements, no arguments; also covers `grant-admin.sh`, which now shares the `db-init` runner (`run_db_init_task` in `lib.sh`); wired into `infra-swe5001.yml` next to `test-lib.sh` |
| A4 | `docs/swe5001/runbook-environment-session.md` | §4 "First distributed session": drop the stale "starts only once #467/#468 are merged" note; add the messaging recipients prerequisite and the 25–40 min estimate; §5 add notification smoke check, `session-report.sh` and the lease steps of §3.4 |
| A5 | `infra/swe5001/README.md` | Script table row for `session-report.sh` |
| A6 | `docs/swe5001/HANDOFF.md` | C3 row: smoke gap closed; next actions point to this plan |
| A7 | `docs/swe5001/impl-aws-infrastructure.md` | Q1 outcome as a dated paragraph in §6.4 |
| A8 | `docs/agile/sprint-04/log/2026-10-14.md`, this plan | Day log, status |

Offline checks: `shellcheck`, `test-session-report.sh`, existing `test-lib.sh` / `test-env-status.sh` / `test-messaging-readiness.sh`, `actionlint`, `smoke.sh` against the dev CloudFront (monolith: 401).

### Phase B — operator session, 10-16, est. 4–5 h including teardown and write-up (6–8 h with one `env-up` retry)

Run from a clean `dev` checkout at the merged Phase A SHA. Every step's output goes into the day log (redacted: no emails, tokens, recipients or state).

| Step | Action | Pass when |
| --- | --- | --- |
| B0 | Preconditions: admin credentials in `402178232802` / `ap-southeast-1`; no queued or running `swe5001-env` / `swe5001-deploy` run (`gh run list`); `env-status.sh` exit 0 (nothing left); Google redirect URI present; messaging recipients ready to confirm; `SWE5001_TF_STATE_BUCKET` set | All true |
| B1 | Build and push `identity-svc`, `commerce-svc`, `notification-svc` (README "Environment sessions" loop: ARM64 `Dockerfile.ci` with `JAR=…`, Trivy, push by digest), write `/blocbuy/swe5001/candidate/<service>` | Three digests in SSM, recorded with the SHA |
| B2 | Private `runtime` plan review (`topology=distributed`); share only resource addresses and actions | No unexpected destroy; three ECS services, three databases, two scaling targets |
| B3 | `SWE5001_TOPOLOGY=distributed SWE5001_TIMINGS_FILE=… infra/swe5001/scripts/env-up.sh --candidate`; confirm the SNS subscription emails during the 900-second wait | Exit 0; smoke (now five checks) green through CloudFront; step timings saved |
| B4 | `describe-services`: identity 1, notification 1, commerce 2 running, all `COMPLETED` rollout; commerce tasks in two AZs; commerce log has no `UnknownHostException: identity-svc` | All true |
| B5 | Google sign-in in the browser; with the cookie, `GET /api/v1/auth/me` 200 and `GET /api/v1/notifications?page=1&size=1` 200 (an unowned route would answer 404 once signed in, so these prove the identity and notification ALB rules); `GET /api/v1/admin/products` as a non-admin → 403 from `commerce-svc` (session roles, #467) | Sign-in works; routing to all three services shown signed in; the 403 carries a commerce trace id |
| B6 | `session-report.sh` (seed only in commerce) → `grant-admin.sh '<email>'` (`granted = 1`) → sign out and in → `GET /api/v1/admin/products` 200 → `seed-dev-demo-listings.sh` → **immediately** START on `US34-04` and note the time (actor check over Service Connect to `identity-svc`, #468; also starts B7's 40-minute clock) → `session-report.sh` (admin count 1) | AC-S2; the START also proves commerce → identity |
| B7 | §3.4: first `session-report.sh` snapshot right after the START; second snapshot and Logs Insights by `@logStream` once at least 45 minutes have passed since the START (the 2400 s lifetime/quote window plus one sweep interval and log delivery) | AC-S3 |
| B8 | `env-down.sh`, then `env-status.sh` exit 0; confirm no queued nightly `down` against a later session | AC-S1 teardown |

Abort rule: if B3 fails twice, or B4/B5 fail without a fix inside 60 minutes, run `env-down.sh`, record the failure, and decide the fallback (§6) at the 10-17 stand-up.

### Phase C — promotion leg, 10-19 (with the Sprint 4 release)

1. Promotion PR `dev` → `prod`, merge commit. `vars.SWE5001_TOPOLOGY` unset (default `distributed`).
2. `swe5001-deploy.yml`: images, publish, up, smoke, release, down all green; release digests written for the three services.
3. `gh workflow run swe5001-env.yml --ref prod -f action=up` (release digests), smoke green, then `down` and `status` green. Can double as the 10-20 rehearsal.

### Phase D — evidence and closure, est. 1 h

- Day logs 10-15 (Phase A), 10-16 (Phase B), 10-19 (Phase C) with run links, SHAs, timings and the redacted outputs above.
- Runbook "Last verified" line; handoff C1–C3 rows to verified; `nfr-register.md` rows touched by the session (availability, two AZs) if their owners agree.
- #456 comment: AC table with links; tick AC-S1–S3; close. Note the Q1 outcome for scope item 1.

## 5. Timeline

| Date | What | Who |
| --- | --- | --- |
| 10-14 | This plan reviewed | @l0sgAi |
| 10-14/15 | Phase A PR merged; 10-15 budget check against the 11 h estimate | @l0sgAi |
| 10-16 | Phase B session (also serves #447/#457 session evidence if their owners join) | Operator (Q5) + @l0sgAi |
| 10-17 | Fallback decision point if Phase B failed | Team |
| 10-19 | Phase C promotion | Operator |
| 10-21 | Presentation (16:15 SGT) | Team |

## 6. Risks and fallback

| Risk | Mitigation |
| --- | --- |
| Messaging gate fails (unconfirmed subscriptions, filter wait) and `env-up.sh` leaves services stopped | Confirm recipients in B0; rerun from the same checkout per the runbook; never resume an unverified publisher by hand |
| Service Connect endpoint not registered when commerce starts | Terraform orders commerce after identity; runbook fix is `--force-new-deployment` on `commerce-svc` |
| `commerce-svc` fails closed when identity is down | By design (#468); B5 proves the healthy path; the outage drill is #459 |
| RDS connections: identity 1 + notification 1 + commerce 2 tasks, default Hikari 10 each ≈ 40 + `db-init` | Record `DatabaseConnections` from the dashboard in B4 |
| No `INFO` line during B7 because nothing was due, or the session is torn down before the 40-minute lifetime ends | START on `US34-04` right after the grant (§3.4 step 2); do not run B8 before 45 minutes after the START; the lock snapshots are evidence even without an `INFO` line |
| Hand-pushed images differ from what the promotion builds | Same `Dockerfile.ci`, same SHA; Phase C rebuilds and rescans in CI anyway |
| Admin email in the `db-init` log group | 7-day retention, operator-only; never copied into the day log |
| Session left running (cost) | B8 is mandatory; nightly `down` is the safety net |
| Phase B fails beyond repair by 10-18 | Set repository variable `SWE5001_TOPOLOGY=monolith`, present identity/commerce split as code plus local evidence, report the gap honestly |

Rollback for Phase A: revert the PR. `smoke.sh` check 5 is additive; `session-report.sh` is new and read-only.

## 7. Questions for the reviewer

| # | Question | Default |
| --- | --- | --- |
| Q1 | Identity CPU scaling 1/1/3 (scope item 1): descope with a dated decision, or build it now (§3.3 b, about 3 h, touches the #447 pause/restore scripts)? | Descope |
| Q2 | `session-report.sh` with fixed read-only SQL over the `db-init` task, rather than ECS Exec and manual queries? | Script |
| Q3 | Phase B (manual session from `dev`) proves AC-S1–S3; the literal "`swe5001-env.yml` on `prod`" leg is closed by Phase C after the 10-19 promotion. #456 closes after Phase C, not after Phase B? | Yes |
| Q4 | Smoke check 5 also runs against the `dev` EC2 monolith (it serves the same endpoint)? | Yes |
| Q5 | Who operates Phase B with the SWE5001 admin credentials: @PPsyrius (ran the #447 session) or @l0sgAi with temporary admin access? | @PPsyrius operates, @l0sgAi drives the checks |
| Q6 | Turn on ECS Exec (`TF_VAR_enable_exec=true`) for the session to read per-task `blocbuy_scheduler_runs_total`? | No; logs and lock snapshots suffice |
