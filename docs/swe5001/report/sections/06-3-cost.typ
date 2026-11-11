// What the Cloud Cost: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== What the Cloud Cost

The cloud-native design paid off in five properties, each with dated evidence (@tbl-cloud-value); this answers briefing Q6.

#figure(
  table(
    columns: (1.3fr, 2.4fr, 1fr, 1.6fr),
    table.header([Property], [Evidence], [Kind], [Limit]),
    [Disposable runtime], [Built from zero by the promotions of 17 Oct, 10 Nov and 11 Nov (115, 120 and 120 resources) and destroyed after each (§6.6)], [Verified on AWS], [Each rebuild loses runtime data and logs not retained beforehand],
    [Pay only while used], [About US\$11.6 for every session billed to 10 Nov, against about US\$125 a month always on (AD-12; @tbl-cost)], [Calculated], [The 11 Nov session was not yet billed],
    [Elastic scale-out], [One to six commerce tasks under the V3 R4 browse workload up to 600 req/s, with no errors or drops (11 Nov, §4.1)], [Verified on AWS], [Analyser verdict missing; no maximum capacity, linearity or scale-in record (AISS-12)],
    [Managed services, no servers to run], [ECS Fargate, RDS, ElastiCache, SNS and SQS (AD-07, AD-04, AD-11)], [Configured, not tested], [The NAT instance (fck-nat) is self-run (AD-10)],
    [Problems found only on the cloud], [Ten problems that local tests and plans missed (@tbl-findings)], [Verified on AWS], [Found, not all fixed (AISS-06, AISS-14)],
  ),
  kind: table,
  caption: [Value of the cloud-native design and its evidence. Sources: §2.2, §4.1, §6.3, §6.4.],
) <tbl-cloud-value>

Calculated: the SWE5001 runtime cost about US\$11.6 from its first session to 10 November 2026 (billed to 10 November, read on 11 November). The figure is an estimate, because the shared account cannot split its bill by course. The environment exists only while it is used, and the permanent layer costs under US\$1 a month. Estimated: while it is up at the committed sizing, the runtime costs about US\$0.17–0.20 an hour (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], section 9); at the larger sizing of the 11 November session it cost about US\$0.27 an hour at list prices. We read the bill from AWS Cost Explorer on 8, 11 and 12 November (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record], #src("docs/swe5001/evidence/20261111-cost-explorer/README.md")[11 November record]). The 11 November session (larger commerce tasks, `db.t4g.small` and a `t3a.2xlarge` load generator) had not been billed at the read and is not included; estimated from list prices, it adds about US\$6–7, of which about US\$0.54 is the load generator (@tbl-cost).

#figure(
  table(
    columns: (2.4fr, 1fr, 1.4fr),
    table.header([Item], [USD], [Kind]),
    [Whole account, September 2026 (SWE5006 and SWE5001)], [78.51], [Measured],
    [Whole account, October 2026 (SWE5006 and SWE5001)], [139.08], [Measured],
    [Whole account, 1–11 Nov 2026 (SWE5006 and SWE5001; 11 Nov not yet billed)], [36.79], [Measured (Cost Explorer estimate for an open month)],
    [ECS, RDS and ElastiCache, which exist only in the SWE5001 runtime, October 2026], [7.11], [Measured],
    [Whole SWE5001 runtime, including its share of the load balancer, NAT instance and public addresses, 6–21 Oct], [≈ 10.15], [Calculated],
    [SWE5001 sessions billed on 9 Nov (UTC billing day: the promotions of 9 and 10 Nov and the placement session)], [≈ 1.4], [Estimated: the SWE5001 services of that day (ECS 0.60, RDS 0.21, ElastiCache 0.15, NAT 0.04) plus the load balancer and address increase over 8 Nov (about 0.40)],
    [*Whole SWE5001 runtime to 10 Nov*], [*≈ 11.6*], [Calculated: 10.15 + 1.4],
    [Permanent SWE5001 layer (ECR images, the web bucket, an idle CloudFront distribution)], [< 1 a month], [Calculated: about 5.1 GB of images at US\$0.10 per GB-month ≈ 0.51],
  ),
  kind: table,
  caption: [Cost of the SWE5001 platform. The October runtime figure is each session day's total minus the SWE5006 baseline of that period. For 9 November the baseline method does not apply: the SWE5006 `t3.large` was stopped on 7 November and restarted on 9 November (US\$1.92 that day), so the SWE5001 share is summed from its own services instead. The SWE5006 baseline is about 1.5 a day only while that instance is stopped. Source: AWS Cost Explorer, read on 8, 11 and 12 November.],
) <tbl-cost>

The runtime billed on ten days to 10 November: 6–7 October, 15–21 October and 9 November (Cost Explorer days are UTC, so the sessions of the night of 9 to 10 November fall on 9 November). For comparison, keeping the same runtime on all the time was estimated at about US\$125 a month (AD-12, §3.2.1). Estimated: Multi-AZ RDS (+US\$39.99), a Valkey replica (+US\$14.02, 0.0192 × 730 h) and one NAT Gateway in each of two zones (2 × US\$43.07, before data charges) would add about US\$140 a month at the list prices of 25 September for `ap-southeast-1` (#src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[cost study]); the fck-nat instance they replace is not subtracted.

Three things did not go to plan, and each is a lesson for a production rollout:

- *The bill was about twice the estimate.* The plan expected under US\$5 for about 14 runtime hours. On 19 October we decided to keep the environment up until the presentation (#src("docs/agile/sprint-04/log/2026-10-19.md")[sprint log]), and 19–21 October carry 85% of the October cost. The decision was reasonable for a live demonstration, but nothing showed its price at the time.
- *No alert would have shown it.* The optional budget was never enabled; on 12 November on AWS, the account still had no budget and no Cost Anomaly Detection monitor.
- *The cost cannot be split by course.* The `Course` tag is on every SWE5001 resource, but it is not activated as a cost-allocation tag, and only the management account can activate it. So the runtime figure is calculated, not read directly.

The US\$0.49 for seven sessions shown at the presentation had no committed record and is superseded by this section.
