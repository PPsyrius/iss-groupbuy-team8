# V8 R4: complete rebuild, smoke and destruction on AWS

On 14 November 2026, the authorized local-operator V8 R4 cycle passed: full canonical `env-up.sh --release` exited 0 in **1794.158 seconds (29 min 54.158 s)**, below the unchanged **1800-second** target, followed by successful canonical destruction of the newly created runtime and independently verified absence. The complete **900-second** routing wait, confirmed alarm recipient, routing probes, service stability and CloudFront smoke suite were retained. This is one measured pass at the recorded configuration, not a guaranteed recovery-time objective or a new promotion.

## Sanitized proof and private archive

- [Acceptance, actual configuration, source/digest hashes and attempt history](acceptance.json).
- [Encrypted-storage upload/download and read-back receipt](private-package.json).
- Archive: `session-evidence-20261114-v8-r4.tar.gz`, **1,423,619 bytes**, **1003 individually verified files**, SHA-256 `efdee10faa1902a21734dbe920749285256e0aede064aa8c9a6306484cf2d134`.
- One conditional upload to existing private **S3 AES256** storage completed at **14:21:17 SGT**. Downloaded bytes and both returned SHA-256 checksums matched; an additional host-side verification rechecked every member and all four attempts' executed-source hash maps at **14:23:04 SGT**. Local TAR copies are private but are not claimed encrypted.
- The archive preserves the original permitted raw lifecycle output, exit receipts, stage timers, role metadata without credentials, source, settings, cloud-log captures, scope reviews, failed attempts and final absence/state proofs. Canonical stdout/stderr were combined in their original logs and are not claimed to be separately captured streams.
- Credentials, authentication challenges, private Terraform variable files, binary plans, raw show JSON and secret-bearing state are excluded; scoped reviews, sanitized configuration and safe empty-state proofs are included. Excluded originals remain private locally, with their exclusion reasons and hashes recorded. Raw bucket/key locations, recipient endpoints and runtime identifiers are not published here.

## Measured phases

All times below are SGT (UTC+8). Full startup is measured from before the complete canonical invocation, after independently verified absence; it is not just the first Terraform apply.

| Phase | Actual result |
|---|---|
| Initial destruction of the prior failed runtime | Exit 0; **398.283 s**; completed **13:24:48**. This is not the new runtime's final cleanup. |
| Fresh empty baseline | Canonical and independent cluster/RDS/cache/ALB/queue/runtime-VPC absence at **13:25:22**; empty managed state and lock absence at **13:25:29**. No force unlock. |
| Full canonical startup | **13:26:04–13:55:58**; exit 0; **1794.158 s**. Initial apply: **427 s**, **118 resources added**, no changes/destroys; db-init: **62 s**; messaging gate: **985 s** including the full **900 s** wait and probes; edge: **66 s**; service stability: **199 s**; smoke: **1 s**. All step timers are in `acceptance.json`. |
| Normal-configuration and cloud-log capture | Released digests, pools, sizing, scaling, visibility, recipient and healthy targets verified before destruction. Startup logs for db-init and all three services, datastore configuration and stopped successful db-init task metadata were captured and hashed. A local collection typo was corrected and its failed receipt/source retained; canonical startup was not replayed. |
| New-runtime final canonical destruction | **14:00:09–14:13:01**; exit 0; **772.510 s** including the canonical absence check; the timed destroy step was **736 s**, with **118 resources destroyed**. |
| Independent final cleanup | Cluster, RDS, cache, ALB, queues and runtime VPC absent at **14:13:41**; **zero managed runtime resources**, state serial **526** and recognized lock-not-found response at **14:13:48**. No force unlock. |

Foundation state was unchanged. Edge, generator and existing evidence archives remained present; prior archive checksums and release/candidate pointers were rechecked unchanged after cleanup. Retained infrastructure and archives remain chargeable. Runtime recreation does not restore deleted database contents.

## Source, configuration and retained failures

The execution used a private byte-verified copy of source base `33a1a9a575c847e37c07f7df2c8cdeb09077c722` plus the seven previously reviewed V5 runtime sizing/warm-up overlays. It was **not a clean signed checkout**. R4 restored two mandatory, previously omitted edge dependencies from that approved reference: `response-headers-policy.json` and `spa-fallback.js`. Canonical lifecycle, routing/readiness and smoke scripts were unchanged. A focused edge review verified a zero-change old-origin baseline and that the deferred S3 OAC policy rewrite preserved exactly the existing permission; no IAM expansion or headers-policy/function change was introduced.

The separate operator overlay allows three SDK attempts for approved reads, including ECS waiters; CLI mutations/unknown calls retain one. Each canonical phase verified the selected existing account/role and its actual expiration, then held that role's credentials only in process memory to avoid a mid-phase SSO refresh dependency. No credential values were written to evidence or launch arguments.

The distributed runtime used the original released image digests, commerce **1024 CPU units / 2048 MiB**, pools of **five**, commerce floor **two**, request target **12000/minute**, slow start **120 s**, and normal notification visibility **60 s**. The single approved alarm recipient was confirmed without a waiver. PostgreSQL was **db.t4g.micro / 18.3**, single-zone with no backups; Valkey was **cache.t4g.micro**. Detailed live read-backs and digest/source hashes are in `acceptance.json` and the private archive. This is not acceptance of the unmodified hosted workflow, whose saved settings did not match this authorized session; no workflow dispatch, saved-setting change, promotion, release/candidate write or IAM expansion occurred.

| Attempt | Canonical up exit / full wall | Preserved outcome |
|---|---|---|
| R1 | **255 / 571.610 s** | Read-only ECS waiter connection closure after db-init submission. The sole db-init task was later verified stopped with exit 0; it was not replayed. |
| R2 | **1 / 631.846 s** | Terraform's post-apply output read failed to refresh SSO before db-init submission. Later rejected device-login renewals were separate failures, not R1's cause. The 30-minute retry window was exceeded while authentication was externally blocked; no within-window retry is claimed. Owner-completed normal SSO renewal and actual STS/export checks preceded R3. |
| R3 | **1 / 1820.049 s** | Mandatory routing passed, but edge apply failed because the private source copy omitted its two static dependencies. Canonical failure cleanup paused services; cloud logs and stopped tasks were captured before the next fresh destruction. |
| R4 | **0 / 1794.158 s** | Full startup target passed; normal read-back, new-runtime final destruction, independent absence and encrypted evidence preservation all passed. |

The precommand launcher error, over-strict read-only edge review, parser correction and collection/staging helper errors also remain recorded; none is relabeled as a passing startup. R1–R3 and the older promotion timings remain historical failures/observations. This result does not establish identity-outage, provider payment/replay, V6 or whole-campaign acceptance.

This is working-copy evidence, not yet present at the report's historical citation pins. It amends the existing [V8 record](../../impl-verification-observability.md#v8-rebuild-from-zero); the report's measured timing home remains its existing delivery-timings table in section 5.3.
