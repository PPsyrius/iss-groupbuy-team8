# Promotion of 9 November: data subnets applied, edge apply denied, runtime destroyed — 2026-11-09

Refs #462, #589. Evidence kind: AWS (GitHub Actions run logs). Written by @limjiechao from the public run logs of [run 42587537517](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42587537517) and [run 42642746273](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42642746273); nothing was read from AWS for this record. The session was operated by Pan (@PPsyrius), who promoted the data subnets (AD-15) to answer the assessors' remark F6 that the database should be in its own subnet ([#589](https://github.com/PPsyrius/iss-groupbuy-team8/issues/589)).

## What ran

[PR #757](https://github.com/PPsyrius/iss-groupbuy-team8/pull/757) ("`dev` to `prod` promotion for subnet test") merged `dev` at `b80debcd` into `prod` at 08:39:23 UTC (merge commit `e177a3fb`). It is the eleventh promotion pull request into `prod`. The merge started `swe5001-deploy.yml` as run 42587537517.

| Job | UTC (9 Nov) | Result |
| --- | --- | --- |
| `images / build` (monolith, identity-svc, commerce-svc, notification-svc) | 08:39:30 – 08:42:51 | success |
| `publish` (push images, record digests) | 08:42:55 – 08:45:09 | success |
| `up` | 08:45:13 – 09:17:01 | **failure** |
| `release` | — | skipped |
| `down` (always runs) | 09:17:05 – 09:24:15 | success |
| `evidence` | 09:24:17 – 09:24:23 | success (artifact `swe5001-promotion-e177a3f…`, two-day retention) |

Steps inside `up`, from the script's own timestamps:

| Step | UTC | Duration |
| --- | --- | --- |
| Apply runtime (services stopped, relay off, scaling suspended): `Apply complete! Resources: 120 added, 0 changed, 0 destroyed.` | 08:46:00 – 08:54:33 | 513 s |
| Run `db-init` (one-off ECS task) | 08:54:33 – 08:55:33 | 60 s |
| Verify messaging before enabling the relay (includes the 900-second wait for SNS filter policies) | 08:55:33 – 09:12:06 | 993 s |
| Apply runtime again, services started: `Resources: 2 added, 5 changed, 2 destroyed.` | 09:12:06 – 09:12:40 | 34 s |
| Apply `edge` (point `/api/*` at the new load balancer) | 09:12:40 – 09:12:56 | failed |

## What was created

The first runtime apply created, among its 120 resources:

| Terraform address | ID in the log |
| --- | --- |
| `module.network.aws_subnet.data[0]` | `subnet-00b56f334f3d6f469` |
| `module.network.aws_subnet.data[1]` | `subnet-01ee4b3303fe9ab8e` |
| `module.postgres.aws_db_subnet_group.this` | `blocbuy-swe5001` |
| `module.cache.aws_elasticache_subnet_group.this` | `blocbuy-swe5001` |

This is the first time the data subnets of AD-15 existed on AWS. The messaging check passed its routing part (`TeamSucceeded` reached notification and audit; `PaymentSucceeded` reached notification and was absent from audit, as filtered). The check warned three times that an alarm subscription was not confirmed and continued because `SWE5001_ALLOW_UNCONFIRMED_ALERTS=true`.

## Where it failed

The `edge` apply was denied at 09:12:56 UTC:

```text
Error: updating CloudFront Response Headers Policy (03faf3c6-02b3-4a63-b3af-4154bc434e74): operation error CloudFront:
UpdateResponseHeadersPolicy, https response error StatusCode: 403, ... AccessDenied: User:
arn:aws:sts::402178232802:assumed-role/blocbuy-swe5001-gh-apply/SWE5001-Up-42587537517 is not authorized to perform:
cloudfront:UpdateResponseHeadersPolicy on resource: arn:aws:cloudfront::402178232802:response-headers-policy/03faf3c6-...
because no identity-based policy allows ...
  with aws_cloudfront_response_headers_policy.security, on cloudfront.tf line 32
```

`up` then paused the services and exited ("up failed after infrastructure changes; services are paused until a successful retry").

Why it failed:

- [PR #700](https://github.com/PPsyrius/iss-groupbuy-team8/pull/700) (merged 27 October) added a `custom_headers_config` block (Permissions-Policy) to `aws_cloudfront_response_headers_policy.security` in `infra/swe5001/edge/cloudfront.tf`.
- The apply role `blocbuy-swe5001-gh-apply` may only read that policy: statement `ReadEdgePolicies` in `infra/swe5001/foundation/github-oidc.tf` grants `cloudfront:GetOriginAccessControl` and `cloudfront:GetResponseHeadersPolicy`, and nothing grants `cloudfront:UpdateResponseHeadersPolicy`. The comment above it says this is deliberate: response headers policies have no name or tag scoping, so the role must not change them; an operator owns changes to `edge` beyond re-pointing `/api/*`.
- The required `infra-plan` check could not see this. For `edge` it runs only offline checks (`terraform validate`, `terraform test` with a mocked AWS provider, Checkov) in `.github/workflows/infra-swe5001.yml`; the live plan covers only `foundation`, under the read-only plan role. No check exercises the apply role's permissions.
- No promotion ran between 20 October and 9 November, so the gap stayed hidden for 13 days after #700 merged, until the next `edge` apply.

## Teardown

`down` destroyed the runtime: `Destroy complete! Resources: 120 destroyed.` at 09:23:27 UTC (366 s), including both data subnets and both subnet groups. `env-status.sh` then reported "no runtime resource tagged Stack=runtime". The runtime existed for about 37 minutes (08:46 to 09:23 UTC). `release` was skipped, so no digest and no web application were released.

## Second run

[Run 42642746273](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42642746273), a manual `workflow_dispatch` on `prod` at the same commit (15:15 UTC), built all four images, then failed in `publish` at 15:19:17 UTC: ECR refused the tag `prod-e177a3fb…-run-1` for `swe5001/monolith` because it "already exists … and cannot be overwritten". The tag was built from the commit and the run attempt, so a new run of the same commit reused the first run's immutable tag. `up` and `release` were skipped; `down` succeeded (15:19:24 – 15:20:06 UTC). [PR #769](https://github.com/PPsyrius/iss-groupbuy-team8/pull/769) (merged 15:44 UTC the same day) adds the run ID to the tag. When this retry failed, that fix had not been exercised by a run, so this record does not claim it as the confirmed root cause. The later promotion that day ([run 42653347818](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42653347818), [record](../20261109-promotion-770/README.md)) published with the new tag; it ran a new commit, so it does not show that rerunning the same commit no longer collides. The second run never reached the edge step, so the permission gap above was not retried.

## Not verified

- **No smoke test ran.** The smoke suite runs in `up` after the start-up script, which stopped at the `edge` apply, so nothing was requested through CloudFront.
- **Placement not read back.** Which subnets RDS and Valkey were placed in, the route tables of the data subnets and the security group rules were not read from AWS during the 37 minutes. AISS-07 (report §3.4) and #589 stay open.
- **Edge state after the failure not read.** Whether the distribution's `/api/*` origin changed before the error is not visible in the log.
- **Cost.** The session is not in the Cost Explorer read of 8 November ([cost record](../20261108-cost-explorer/README.md)).
- **Permission fix.** On 9 November no pull request grants `cloudfront:UpdateResponseHeadersPolicy` or applies the #700 change to `edge` by an operator; the next promotion would fail the same way until one of the two happened. Update: the next promotion, the same evening, passed the `edge` step without a change to the apply role ([record](../20261109-promotion-770/README.md)).

## 10 November reconciliation (#775)

The original run's failure and unverified placement remain unchanged. The later [subnet/session record](../20261110-subnet-verification/README.md) supplies the separate AWS placement/connectivity observations that closed #589. The recovered [operator edge-repair account](../20261109-edge-policy-recovery/README.md) records a direct CloudFront policy update and API readback, not an operator Terraform apply or new grant to the GitHub apply role. It qualifies the earlier “permission fix” uncertainty without attributing the repair to this failed run.
