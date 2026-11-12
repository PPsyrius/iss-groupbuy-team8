# Promotion of 9 November, second attempt: built from zero, smoke-tested, released and destroyed — 2026-11-09

Refs #462, #589. Evidence kind: AWS (GitHub Actions run log). Written by @limjiechao from the public log of [run 42653347818](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42653347818); nothing was read from AWS for this record. It follows the failed promotion of the same day ([record](../20261109-promotion-757/README.md)).

## What ran

[PR #770](https://github.com/PPsyrius/iss-groupbuy-team8/pull/770) ("promote dev to prod (2026-11-09)") merged into `prod` at 16:17:58 UTC (merge commit `93984f61`). It is the twelfth promotion pull request into `prod`. Its only change since #757 is [#769](https://github.com/PPsyrius/iss-groupbuy-team8/pull/769), which adds the run ID and attempt to the image tag. The merge started `swe5001-deploy.yml` as run 42653347818; every job succeeded.

| Job | UTC (9 Nov) | Result |
| --- | --- | --- |
| `images / build` (four images) | 16:18:04 – 16:21:38 | success |
| `publish` | 16:21:41 – 16:23:53 | success (the #769 tag did not collide) |
| `up` | 16:23:56 – 16:55:57 | success |
| `release` (web application to S3, candidate digests promoted to release) | 16:56:02 – 16:56:48 | success |
| `down` | 16:56:53 – 17:11:25 | success |
| `evidence` | 17:11:27 – 17:11:33 | success |

The run took 53.5 minutes from merge to evidence.

Steps inside `up`, from the script's own timestamps:

| Step | UTC | Duration |
| --- | --- | --- |
| Apply runtime (services stopped): `Resources: 120 added, 0 changed, 0 destroyed.` | 16:24:28 – 16:33:05 | 517 s |
| Run `db-init` | 16:33:05 – 16:34:03 | 58 s |
| Verify messaging (includes the 900-second wait) | 16:34:03 – 16:50:30 | 987 s |
| Apply runtime again, services started: `2 added, 5 changed, 2 destroyed` | 16:50:30 – 16:51:00 | 30 s |
| Apply `edge` (point `/api/*` at the new load balancer): `0 added, 1 changed, 0 destroyed` | 16:51:00 – 16:52:00 | 60 s |
| Wait for services to be stable | 16:52:00 – 16:55:41 | 221 s |
| Smoke suite through CloudFront | 16:55:41 – 16:55:44 | 3 s |

The first apply created the data subnets again (`module.network.aws_subnet.data[0]` `subnet-06d08058845930acf`, `data[1]` `subnet-0739594cd4e38fb27`), their route table associations, and the subnet groups `module.postgres.aws_db_subnet_group.this` and `module.cache.aws_elasticache_subnet_group.this`.

## Results

- **Smoke suite:** all nine checks passed against `https://d3lvq02o2e7tcn.cloudfront.net` (health UP, product list non-empty, `auth/me` and notifications 401 without a session, internal path not served by a service, `X-Trace-Id` on the four API answers).
- **Messaging:** `TeamSucceeded` reached notification and audit; `PaymentSucceeded` reached notification and was absent from audit. The check warned three times that an alarm subscription was not confirmed and continued because `SWE5001_ALLOW_UNCONFIRMED_ALERTS=true`.
- **Edge:** the `edge` apply that failed at 09:12 UTC changed one resource this time and was not denied, so the response headers policy no longer needed an update. No pull request changed the apply role or `edge` between the two runs; the log does not show who brought the policy up to date, or how.
- **Teardown:** `Destroy complete! Resources: 120 destroyed.` at 17:10:30 UTC, then `env-status.sh`: "no runtime resource tagged Stack=runtime". The runtime existed for about 46 minutes (16:24 to 17:10 UTC).

## Not verified

- **Placement not read back.** As in the morning run, nothing read which subnets RDS and Valkey were placed in, the route tables or the security group rules. AISS-07 (report §3.4) and #589 stay open.
- **Buyer journey.** Only the smoke suite ran; no sign-in, payment or notification was exercised.
- **Cost.** Not in the Cost Explorer read of 8 November.

## 10 November reconciliation (#775)

The [retained operator account](../20261109-edge-policy-recovery/README.md) now records how the preceding response-headers policy drift was repaired: a direct CloudFront API update with matching readback, not an IAM grant or a successful Terraform recovery apply. The later [session record](../20261110-subnet-verification/README.md) supplies actual placement/connectivity observations; those were not performed by this promotion. The original run results and limitations above remain dated to their own execution.
