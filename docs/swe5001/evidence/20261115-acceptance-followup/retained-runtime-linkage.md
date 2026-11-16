# #448 — 15 November retained-runtime linkage amendment

This dated amendment adds **later offline-reviewed evidence** to PR #808; it does not rewrite an earlier “linkage not yet proved” assessment as though that proof was available then. No AWS call, browser action, SQL, fault, benchmark or accepted-drill rerun was performed by this review/publication.

## Actual criterion and parent's technical interpretation

#448 requires fan-out, poison and burst captures on SWE5001 ECS “during one session”, alongside duplicate/rollback/poison ElasticMQ ITs in CI and NFR links. **Parent's explicit technical interpretation is one provisioned ECS environment campaign**, not one browser/operator sitting, unchanged tasks or uninterrupted task uptime. The canonical [runtime lifecycle table](../../../../infra/swe5001/README.md) describes runtime as existing only during a promotion run or manual session; [Environment sessions](../../../../infra/swe5001/README.md#environment-sessions) says the scripts create and destroy runtime for a manual session. This documents the interpretation and its source; it is not a fabricated quotation defining “session” in the issue, an owner waiver, an automatic issue closure or a stronger infrastructure-continuity claim.

## Direct retained source facts

All times are UTC. Exact service/task/deployment/cluster identities and source paths remain private.

| Stage | Retained observation and binding |
| --- | --- |
| Original successful startup | **13 November 03:11:30.214542–03:40:38.324553**, exit 0. The runtime apply records **118 additions, zero changes/destructions**, including cluster and all three service creation receipts. |
| Native service births | **13 November** identity **03:19:00.590000**, notification **03:19:00.599000**, commerce **03:19:02.044000**. Later V4 native service ARNs exactly match those startup creation receipts; every extracted service's cluster ARN matches the startup-created cluster. |
| Startup fan-out | **13 November** TeamSucceeded reached notification **03:35:31**, audit **03:35:32**; PaymentSucceeded reached notification **03:35:34**, and was absent from audit after three bounded 20-second polls by **03:36:36**. Readiness passed. These unsupported-version routing probes establish transport/filter behavior, **not real business notification effects**. |
| V4 R5 final native services | Wrapper observation **13 November 21:23:34.608303** retains the same three service ARNs and native births. |
| V7 R4 preflight | **14 November 02:02:54.295385** retains the exact notification-service ARN, cluster ARN and **13 November 03:19:00.599000** birth. |
| V7 restoration/final native checks | Restoration **14 November 02:20:39.067536** and three final native service reads retain that same service identity/birth; qualified closure readback is **02:31:54.470861**. |
| Subsequent examined lifecycle boundary | First examined subsequent canonical V8 down starts **14 November 02:49:41.830751**, after V7 closure; absence receipt **03:02:00.570974** reports runtime cluster/RDS/cache/ALB/queues/VPC absent, not foundation/edge/generator/evidence destruction. |

The same native notification-service birth/ARN occurs in **six** full service records across V4 and V7; extraction covers **28** selected retained files, **eight** full service records and **54** task records. Four review-manifest hashes, all 28 native-source hashes and eight lifecycle-receipt hashes were independently checked offline. This is direct service-instance evidence, not a shared-folder/name or timestamp-proximity inference.

## What continuity establishes — and what it does not

Destroying/recreating that notification service between fan-out and V7 would replace its native birth time; the exact retained match therefore supports the **same continuously provisioned notification-service instance** across the campaign. It does not certify continuous task uptime, exclude scale-to-zero intervals or unrelated changes, or establish every foundation component's immutable lifetime. Startup readiness projections omit native service birth/ARN and are supporting readiness evidence, not the immutable-birth source. The pause controls are corroboration, not an exhaustive account-wide operation ledger.

Deployments **did change**: V4's recorded deployment was created **13 November 04:20:25.096000**, V7 preflight's **14 November 01:45:05.008000**, and V7 restoration's **02:15:41.682000**. Original notification task-definition revision **38** appears in V4 final and V7 preflight/restoration, but differing deployment identities must not be relabelled unchanged tasks. The service-instance chain supplies the previously missing direct linkage under the parent's provisioned-campaign interpretation; no literal uninterrupted human/browser-session claim follows.

## All original qualifications remain

- [V4 R5](../20261114-v4-r5-live/v4-r5-pass.md) remains an **experimental 80,000-team producer/cohort** pass with its reviewed uncommitted source and commerce revision 51, not the original approximately 2,000-team scaling pass or a release promotion. R1 scaling failure and R4 unseeded setup failure remain failures. V3 and other original failure qualifications are unchanged.
- [V7 R4](../20261114-v7-r4-live/README.md) remains the approved temporary **10-second application-consumer visibility** result, source queue unchanged at **60 seconds** and original consumer default restored to **60 seconds**. It **does not prove** the original 60-second consumer meets the injection-to-alarm target. Restoration was requested before the backstop deadline, with stable readback afterward; V7 R1/R2/R3 retain their failed results.
- [Hosted CI](README.md#selected-hosted-suites) binds duplicate/rollback/poison coverage to exact head **be5e7c41**: selected four suites passed **15 tests without skips**; the overall **2,144-test** run has **two skipped opt-in benchmarks**, not zero skips. Later documentation heads require their own checks.
- This earlier campaign is separate from [new normal #460 R2 manual acceptance](normal-team-success-r2.md). It does not resolve that issue's older topology-smoke/new-manual session linkage, EC2 parity or OAuth/Valkey observation limits. The [later focused #459 R11 result](focused-identity-payment-r11.md) is separate HTTP/DB evidence, not a new acceptance claim derived from this linkage.

## Private-source provenance

Only count/timestamp assertions and hashes are public. L01 is the independent review, L02 the native extraction, L03 the lifecycle/linkage verdict, and L04 their provenance manifest. These hashes bind actual retained bytes, not a new cloud run, private-archive upload/readback or issue acceptance.

| Alias | SHA-256 |
| --- | --- |
| L01 | `dbcf7f6ecab534ea996ac684f1b1c56acd9efee688d0a5175623b563f9641108` |
| L02 | `78f1188af73f39c1ed8535750e899abb59a4752d82a452558e33aad2c5acebc5` |
| L03 | `ee0d89a323e1ffdd01d58e31fbf163655123c8542a2f8b3ea3dc36344cd280cc` |
| L04 | `1ee1956acfeaad326af9d0fe750b1079de76b1258fce8bce3fc63f21393c8fad` |

The original startup-log SHA-256 remains `47ec2380c554a85dcbac4254601e31be6ba7e25d756c190295289f17a3668c6d`. [Publication manifest](publication-manifest.json) binds this new amendment without modifying the historical V4/V7 records.
