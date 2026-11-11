# Cost Explorer read, 11 and 12 November 2026

Second read of the SWE5001 runtime cost, after the 9 November promotions. It updates [`20261108-cost-explorer`](../20261108-cost-explorer/README.md) and uses the same method. All reads are read-only, account 402178232802, Cost Explorer in `us-east-1`, `UnblendedCost`. Every November figure is an estimate, and the bill is not final.

## Commands

```bash
aws ce get-cost-and-usage --time-period Start=2026-09-01,End=2026-11-12 --granularity MONTHLY \
  --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE
aws ce get-cost-and-usage --time-period Start=2026-11-08,End=2026-11-13 --granularity DAILY \
  --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE --region us-east-1
aws ce get-cost-and-usage --time-period Start=2026-11-08,End=2026-11-12 --granularity DAILY \
  --metrics UnblendedCost UsageQuantity \
  --filter '{"Dimensions":{"Key":"SERVICE","Values":["Amazon Elastic Compute Cloud - Compute"]}}' \
  --group-by Type=DIMENSION,Key=USAGE_TYPE --region us-east-1
aws budgets describe-budgets --account-id 402178232802 --region us-east-1
aws ce get-anomaly-monitors --region us-east-1
```

## Results

| Day (UTC) | Account total (USD) | SWE5006 `t3.large` (BlocBuy-dev) | SWE5001 share, calculated (USD) |
|---|---|---|---|
| 8 Nov | 1.45 | 0 | ≈ 0 (baseline only) |
| 9 Nov | 4.70 | 1.92 (18.1 h; restarted on 9 Nov) | ≈ 1.40 = ECS 0.60 + RDS 0.21 + ElastiCache 0.15 + NAT 0.04 + load balancer and VPC above baseline 0.40 |
| 10 Nov | 1.68 (partial day) | 1.04 | ≈ 0.02 |
| 11 Nov | not yet billed | — | estimated 6–7 (runtime about 0.27/h from 05:35Z, scale-outs, generator 0.54) |

- Whole account, months: September 78.51, October 139.08, 1–11 November 36.79 (estimated).
- **Calculated:** the SWE5001 runtime cost about **US$11.6** billed to 10 November (10.15 to 8 November, from the previous record, plus about 1.4 on 9 November).
- The 8 November record estimated about US$3.2 for 9 November above a US$1.5/day baseline. That figure counted the SWE5006 `t3.large`, which was stopped on 7 November and restarted on 9 November. The US$1.5/day baseline holds only while that instance is stopped; with it running, the baseline is about US$3.4–3.9/day.
- The 11 November session (three services at the larger session sizes, and the AWS-hosted load generator) is not included. Re-read on or after 13 November.
- On 12 November the account had no AWS Budget and no Cost Anomaly Detection monitor.
- The `Course` cost-allocation tag is still not activated, so the split by course is calculated, not measured.
