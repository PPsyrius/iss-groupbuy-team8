# AWS readback of 15 November 2026

Read-only readback of both AWS accounts on 15 November 2026, 17:19–17:30 SGT, for the SWE5001 final review (#462, #808).

- Project account 402178232802 (profile `blocbuy`, region `ap-southeast-1`; Cost Explorer and IAM are global).
- Management account 592877955899 (profile `blocbuy-mgmt`).

Only `describe`, `get`, `list`, `lookup-events`, Cost Explorer and STS reads were used for the readback. The command outputs listed below were sanitised before publication: access keys, session tokens, source IP addresses, user-agent strings, request ids, event ids and email fields were removed, and the CloudFront custom origin header value is replaced by `[redacted]`. Account ids and resource names that the report already names are kept. Times in CloudTrail records are as AWS returned them (UTC, or SGT where the field carries `+08:00`).

## Direct operator and effort records

| Record | Evidence and scope |
| --- | --- |
| [operator-retirement.json](operator-retirement.json) | Saved-plan execution and independent absence checks: 9 edge and 23 foundation resources, 39 ECR images, four repositories and the dedicated generator with its disk/network/IAM helpers removed. Final checks completed at 15:57 SGT. Private source-receipt hashes bind the projection; raw plans and state are excluded. |
| [containerinsights-export.json](containerinsights-export.json) | Before retirement, 15,433 events from the remaining one-day-retention performance log group were downloaded and verified: 19,489,496 JSON bytes, valid pagination, unique event IDs and SHA-256. Raw logs remain private; this is not a complete campaign backup or another test. |
| [effort-estimates.json](effort-estimates.json) | Current work-package estimates: 77.5 team person-days, including Pan 23.5 and Jiechao 15.5. This supplies the revised figures without rewriting the historical reconstruction or changing participation scores. |

These are projections of operator receipts and report-review inputs, not outputs of the later read-only account survey. The raw Container Insights export, private source receipts and secret-bearing Terraform files remain outside Git. The operator's independent checks also confirmed retained state/evidence storage and absent state locks; the historical snapshot, retained log group and unlisted SSM scope prevent an “all AWS gone” or zero-cost claim.

## Commands

| File | Command |
| --- | --- |
| `01-ecs-clusters.json` | `aws ecs list-clusters` |
| `02-alb.json` | `aws elbv2 describe-load-balancers` |
| `03-rds.json` | `aws rds describe-db-instances` |
| `04-elasticache.json` | `aws elasticache describe-cache-clusters` |
| `05-nat.json` | `aws ec2 describe-nat-gateways` |
| `06-ec2.json` | `aws ec2 describe-instances` |
| `07-ebs.json` | `aws ec2 describe-volumes` |
| `08-s3.json` | `aws s3api list-buckets` (17:19 SGT) |
| `10-rds-snapshots.json` | `aws rds describe-db-snapshots` (17:19 SGT) |
| `14-ecr.json` | `aws ecr describe-repositories` |
| `20-ct-stopinstances.json` | `aws cloudtrail lookup-events` for `StopInstances`, 10–15 Nov |
| `21-ct-terminateinstances.json` | `aws cloudtrail lookup-events` for `TerminateInstances`, 10–15 Nov |
| `36-ce-monthly.json` | `aws ce get-cost-and-usage`, monthly by service, 1 Oct – 15 Nov |
| `39-ebs-snapshots.json` | `aws ec2 describe-snapshots --owner-ids self` (17:20 SGT) |
| `38-ce-daily-total.json` | `aws ce get-cost-and-usage`, daily totals, 1 Sep – 15 Nov |
| `41-cloudfront.json` | `aws cloudfront list-distributions` |
| `42-ct-15nov-pan.json` | `aws cloudtrail lookup-events` for user `pan`, 15 Nov 15:20–17:30 SGT, at most 50 events |
| `47-ct-deletebucket.json` | `aws cloudtrail lookup-events` for `DeleteBucket`, 10–15 Nov |
| `48-ct-deleterepo.json` | `aws cloudtrail lookup-events` for `DeleteRepository`, 10–15 Nov |
| `49-ct-deletedist.txt` | `aws cloudtrail lookup-events` for `DeleteDistribution` in `us-east-1` (denied) |
| `68-ct-updateservice-11nov.json` | `aws cloudtrail lookup-events` for `UpdateService`, 11 Nov 21:00–23:30 SGT |
| `A8-ct-updateservice-scalein.json` | `aws cloudtrail lookup-events` for `UpdateService`, 11 Nov 23:00–23:30 SGT |
| `A9-ec2-t3a2xl.json` | `aws ec2 describe-instances` filtered to `t3a.2xlarge` |
| `B4-ct-deletestack-edge.json` | `aws cloudtrail lookup-events` for `DeleteTable`, 15 Nov |
| `C1-iam-roles-swe5001.json` | `aws iam list-roles` |
| `C3-ct-deleterole.json` | `aws cloudtrail lookup-events` for `DeleteRole`, 15 Nov |
| `A7-ce-oct-nov-daily.json` | `aws ce get-cost-and-usage`, daily by service, 1 Oct – 15 Nov |
| `81-scp-list.json` | `aws organizations list-policies --filter SERVICE_CONTROL_POLICY` |
| `82-`, `83-`, `84-scp-*.json` | `aws organizations describe-policy` for `Org-Baseline`, `Region-Pin`, `Sandbox-Guardrails` |
| `85-`, `86-`, `87-tgt-*.json` | `aws organizations list-targets-for-policy` for the same three policies |
| `90-mgmt-ct-deletepolicy.json` | `aws cloudtrail lookup-events` for `DeletePolicy` in `us-east-1`, 1–15 Nov (management account) |
| `93-mgmt-trails.json` | `aws cloudtrail describe-trails` in `ap-southeast-1` (management account) |
| `95-sso-permsets.json` | `aws sso-admin list-permission-sets` |
| `96-ps-*.json` | `aws sso-admin describe-permission-set`, one file per permission set |
| `97-mgmt-ct-updateps.json` | `aws cloudtrail lookup-events` for `UpdatePermissionSet`, 18 Aug – 15 Nov |
| `98-parents-project.json` | `aws organizations list-parents` for the project account |
| `99-ou-root.json` | `aws organizations list-organizational-units-for-parent` for the root |
| `A0-proj-trails.json` | `aws cloudtrail describe-trails` (project account) |
| `A1-proj-guardduty.json` | `aws guardduty list-detectors` (project account) |
| `A2-proj-config.json` | `aws configservice describe-configuration-recorders` (project account) |
| `A3-proj-accessanalyzer.json` | `aws accessanalyzer list-analyzers` (project account) |
| `A4-proj-ct-useast1.txt` | `aws cloudtrail lookup-events` in `us-east-1` (project account, denied) |
| `A5-mgmt-orgtrails.json` | `aws cloudtrail describe-trails` in `us-east-1` (management account) |
| `A6-mgmt-guardduty.json` | `aws guardduty list-detectors` (management account) |

## What each group shows

**Final teardown on 15 November.**
- `20-ct-stopinstances.json`: the load generator `i-0e6ddc39c34bebeb3` was stopped at 14:19 SGT on 15 November (and earlier on 13 November); the 11 November generator `i-043f9204a5282ea72` was stopped at 22:54 SGT on 11 November.
- `21-ct-terminateinstances.json`: the generator `i-0e6ddc39c34bebeb3` was terminated at 15:47:24 SGT on 15 November; the 11 November generator was terminated at 12:05 SGT on 12 November.
- `42-ct-15nov-pan.json`: the 50 most recent calls by that user, from 15:54:43 to 15:57:15 SGT, including the deletion of the four `swe5001/*` ECR repositories and the post-teardown checks.
- `47-ct-deletebucket.json`: the SWE5001 web bucket was deleted at 15:53 SGT on 15 November.
- `48-ct-deleterepo.json`: the four `swe5001/*` ECR repositories were deleted at 15:54:52 SGT on 15 November.
- `49-ct-deletedist.txt`: the CloudFront deletion cannot be looked up, because `Region-Pin` denies `cloudtrail:LookupEvents` in `us-east-1`; `41-cloudfront.json` shows its absence instead.
- `B4-ct-deletestack-edge.json`, `C3-ct-deleterole.json`: these lookups returned no events.

**Scale-in after V3 r4 on 11 November.**
- `68-ct-updateservice-11nov.json`: commerce desired counts 2, 3, 4 and 6 from 22:30:49 to 22:37:49 SGT, the bounds restored at 22:52:50 SGT, then 5, 4, 3 and 2.
- `A8-ct-updateservice-scalein.json`: the desired count lowered from six to two at 23:09:13, 23:13:13, 23:17:13 and 23:21:13 SGT, each invoked by `ecs.application-autoscaling.amazonaws.com`; three tasks were still running at 23:21:13.

**What no longer exists (read 17:19–17:24 SGT).**
- `01-ecs-clusters.json`, `03-rds.json`, `04-elasticache.json`, `05-nat.json`: no ECS cluster, database instance, cache cluster or NAT gateway.
- `02-alb.json`, `06-ec2.json`, `07-ebs.json`, `14-ecr.json`: only the SWE5006 `dev` load balancer, EC2 instance (`t3.large`), its volume and the `dev/blocbuy` repository remain.
- `A9-ec2-t3a2xl.json`: no `t3a.2xlarge` instance (the generator type).
- `41-cloudfront.json`: two distributions, both SWE5006; no SWE5001 distribution.
- `C1-iam-roles-swe5001.json`: no role under the `/swe5001/` path.

**What remains (read 17:19–17:20 SGT).**
- `08-s3.json`: four buckets: the SWE5006 frontend and media buckets, the SWE5001 generator evidence bucket `blocbuy-swe5001-generator-20261111-402178232802` and the Terraform state bucket `blocbuy-tfstate-402178232802`.
- `39-ebs-snapshots.json`: one snapshot, `snap-08d8687ce2487f63e` (40 GiB, encrypted, 12 November), of the 11 November generator disk.
- `10-rds-snapshots.json`: no database snapshot.
- SSM Parameter Store was not listed, so the parameters seeded outside Terraform under `/blocbuy/swe5001/` are not covered by this readback.

**Governance.**
- `81-scp-list.json` to `87-tgt-sandbox.json`, `98-parents-project.json`, `99-ou-root.json`: `Org-Baseline` and `Region-Pin` are attached at the root and `Sandbox-Guardrails` at the Sandbox unit, which holds the project account; the policy text of each.
- `90-mgmt-ct-deletepolicy.json`: four `DeletePolicy` calls at 08:37 SGT on 12 November.
- `95-sso-permsets.json`, `96-ps-*.json`, `97-mgmt-ct-updateps.json`: three permission sets, each with a one-hour session (`PT1H`); no `UpdatePermissionSet` event since 18 August.
- `A0-` to `A3-proj-*.json`: no CloudTrail trail, GuardDuty detector, Config recorder or IAM Access Analyzer in the project account in `ap-southeast-1`.
- `93-mgmt-trails.json`, `A5-mgmt-orgtrails.json`, `A6-mgmt-guardduty.json`: no trail in `ap-southeast-1` or `us-east-1`, and no GuardDuty detector in the management account.
- `A4-proj-ct-useast1.txt`: `cloudtrail:LookupEvents` in `us-east-1` is denied by `Region-Pin`.

**Cost.**
- `36-ce-monthly.json`, `38-ce-daily-total.json`, `A7-ce-oct-nov-daily.json`: monthly and daily unblended cost. October is final; November figures are estimates for an open month.

## Limits

- This is a read-only readback of configuration and recorded API calls, not a test of any control.
- Cost Explorer had no data for 14 and 15 November and only part of 13 November, so the 13–15 November sessions cannot be costed from it. Open-month figures are dated readings.
- The 118 and 113 resource counts and the destroy durations come from Terraform output, not from an AWS API.
- The V5 replacement time (79.458 s) and the V7 visibility setting cannot be read again, because those resources were deleted.
- The SWE5001 CloudFront distribution no longer exists, so the 11 November observation that it had no WAF was not read again.
- CloudTrail event history covers 90 days only.
