# AWS cost of the SWE5001 runtime, read from Cost Explorer

Date: 2026-11-08. Owner: @limjiechao. Issue: \#462 (report chapter 6; the measured-cost marker in §3.2.2). Scope: account 402178232802 (`swe5006-team8`), which hosts both the SWE5006 `dev` environment and the SWE5001 stacks. Read-only: AWS Cost Explorer and `describe`/`list` calls with the `blocbuy` profile; nothing was created or changed.

## Why

The presentation (21 October) showed a runtime cost of US$0.49 for seven sessions and a September bill of US$78.51, but no record of either was committed, so the report could not cite them. This record replaces both with figures read on 8 November 2026.

## How the bill was read

```sh
aws ce get-cost-and-usage --time-period Start=2026-09-01,End=2026-11-09 --granularity MONTHLY \
  --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE --region us-east-1 --profile blocbuy
aws ce get-cost-and-usage --time-period Start=2026-10-01,End=2026-11-09 --granularity DAILY \
  --metrics UnblendedCost --region us-east-1 --profile blocbuy
aws ce get-cost-and-usage --time-period Start=2026-10-01,End=2026-11-09 --granularity DAILY --metrics UnblendedCost \
  --filter '{"Dimensions":{"Key":"SERVICE","Values":["Amazon Elastic Container Service","Amazon Relational Database Service","Amazon ElastiCache"]}}' \
  --region us-east-1 --profile blocbuy
```

Cost Explorer days are UTC. November is month to date and marked estimated by AWS. Rows are rounded to cents and services below USD 0.005 are omitted, so a column may not sum exactly to its total.

## Account bill (both courses)

| Service (USD) | Sep 2026 | Oct 2026 | 1–8 Nov 2026 (est.) | Used by |
|---|---:|---:|---:|---|
| EC2 compute | 42.94 | 78.79 | 16.19 | SWE5006 `t3.large`; SWE5001 fck-nat on session days |
| Elastic Load Balancing | 15.37 | 20.33 | 4.33 | SWE5006 ALB; SWE5001 ALB on session days |
| Amazon VPC (public IPv4) | 9.70 | 12.24 | 2.58 | mostly SWE5006 |
| AWS WAF | 0.00 | 4.77 | 1.91 | SWE5006 media distribution |
| Amazon ECS (Fargate) | 0.00 | 4.44 | 0.00 | SWE5001 runtime |
| EC2 other (EBS) | 3.84 | 3.87 | 0.91 | mostly SWE5006 |
| Amazon RDS | 0.00 | 1.52 | 0.00 | SWE5001 runtime |
| Amazon ElastiCache | 0.00 | 1.15 | 0.00 | SWE5001 runtime |
| Amazon ECR, S3, Cost Explorer API | 0.18 | 0.48 | 0.20 | both |
| Tax | 6.48 | 11.49 | 2.35 | both |
| **Total** | **78.51** | **139.08** | **28.49** | |

## SWE5001 runtime

The three services that exist only in the SWE5001 runtime (ECS, RDS, ElastiCache) billed on nine UTC days and on no other day between 1 October and 8 November:

| UTC day | 6 Oct | 7 Oct | 15 Oct | 16 Oct | 17 Oct | 18 Oct | 19 Oct | 20 Oct | 21 Oct | Total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ECS + RDS + ElastiCache (USD) | 0.15 | 0.04 | 0.06 | 0.23 | 0.12 | 0.23 | 1.37 | 2.30 | 2.59 | 7.09 |
| Day total above the SWE5006 baseline (USD) | 0.33 | 0.08 | 0.11 | 0.48 | 0.22 | 0.34 | 1.90 | 3.17 | 3.52 | 10.15 |

- **Measured:** ECS, RDS and ElastiCache cost USD 7.11 in October 2026 (monthly view; the daily rows above round to 7.09).
- **Calculated:** the whole SWE5001 runtime, including its share of the load balancer, NAT instance and public IPv4 addresses, cost about USD 10.15. The calculation is each session day's total minus the SWE5006 baseline of that period (USD 3.63 per day before 13 October, USD 3.89 from 13 October, when the SWE5006 media WAF started), with the USD 0.02 Cost Explorer API fees of 16 and 17 October removed. It is an estimate, because the account cannot split shared services by course (next section).
- 19–21 October carry 85% of the runtime cost. At USD 3.17 and USD 3.52 above baseline, 20 and 21 October each match a runtime that stayed up for most of the UTC day. This follows the decision of 19 October to keep the environment up until the presentation (`docs/agile/sprint-04/log/2026-10-19.md`).
- The persistent SWE5001 layer (four ECR repositories holding 28 images of about 182 MB each, the SPA bucket with 5.88 MB, and an idle CloudFront distribution) costs under USD 1 a month. Calculated: about 5.1 GB of images at USD 0.10 per GB-month is about USD 0.51; ECR for the whole account was USD 0.36 in October.

## Limits of this record

- **No per-course split.** The `Course` cost-allocation tag is not activated; the member account cannot list or activate cost-allocation tags ("Linked account doesn't have access to cost allocation tags"), and grouping by `Course` returns only the untagged bucket. Activation is a management-account action.
- **No budget or anomaly monitor.** `aws budgets describe-budgets --account-id 402178232802` returns none and there are no Cost Anomaly Detection monitors; the optional `blocbuy-swe5001-monthly` budget was never enabled (`enable_budget = false`).
- **Day granularity only.** Session start and stop times are not visible here; the GitHub Actions run history gives them.

## Other observations from the same read

- **Runtime absent.** No ECS cluster, SWE5001 load balancer, RDS instance, ElastiCache group, SNS topic, SQS queue, NAT instance or VPC endpoint exists; the `runtime` stack is destroyed. `foundation` (four `swe5001/*` ECR repositories, immutable tags, scan on push) and `edge` (CloudFront `E3N39GHVFV6CT3`, the SPA bucket) remain. (`aws ecs list-clusters`, `aws elbv2 describe-load-balancers`, `aws rds describe-db-instances`, `aws cloudfront get-distribution`.)
- **Edge origin after teardown.** The `/api/*` origin of `E3N39GHVFV6CT3` still names `blocbuy-swe5001-2079388654`, the load balancer of the last runtime, which no longer exists. (`aws cloudfront get-distribution-config --id E3N39GHVFV6CT3`.)
- **Leftover log group.** `/aws/ecs/containerinsights/blocbuy-swe5001/performance` (created by Container Insights, 0 bytes) survived `terraform destroy`; `env-status.sh` checks only `Stack=runtime` tags, so it does not report it. (`aws logs describe-log-groups`.)
- **GitHub OIDC roles.** `blocbuy-swe5001-gh-plan` trusts only the subject `…:environment:swe5001-plan`; `blocbuy-swe5001-gh-apply` and `blocbuy-swe5001-gh-deploy` trust only `…:environment:swe5001-demo`, each with audience `sts.amazonaws.com` and no branch or wildcard subject. (`aws iam get-role --role-name blocbuy-swe5001-gh-<role> --query Role.AssumeRolePolicyDocument`.)
- **No WAF on the SWE5001 distribution.** The account's only web ACL is attached to the SWE5006 media distribution. (`aws wafv2 list-web-acls --scope CLOUDFRONT --region us-east-1`.)
