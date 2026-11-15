// What the Cloud Cost: part of 06-highlights.typ.
#import "../lib.typ": *

== What the Cloud Cost

Briefing Q6 asks whether the cloud-native design was worth it. Our runtime existed only while we used it, and it cost about a tenth of what one month always on would have cost, but about twice our own plan, with nothing to warn us. @tbl-cloud-value shows what the design gave us and what we traded for it; the rest of §6.3 shows the cost and how we derived it.

#figure(
  table(
    columns: (1.1fr, 2fr, 1fr, 1.5fr, 1.4fr),
    table.header([Property], [Evidence], [Kind], [Trade-off accepted], [Limit]),
    [Disposable runtime], [Fresh provisioning and teardown verified by promotion records (§5.3)], [Verified on AWS], [About 32 min of start-up in October and November; one V8 R4 run on 14 Nov stayed below 30 min (§5.3, AISS-06). Runtime data and logs not captured first are lost], [The 11 Nov load-test runtime outlived its session, and we had to destroy it by hand on 12 Nov (§5.3)],
    [Pay only while used], [About US\$11.6 for every session billed to 10 Nov, against about US\$125 a month always on (AD-12; @tbl-cost)], [Calculated (one term estimated)], [No always-on service; every demonstration needs a rebuild], [The 11 Nov session is estimated, not billed (@tbl-cost)],
    [Elastic scale-out], [One to six commerce tasks under the V3 r4 browse workload (11 Nov), and independent automatic notification scale-out in V4 R5’s experimental expiry workload (14 Nov, §4.1)], [Verified on AWS, separately scoped runs], [Spot tasks can be reclaimed (AD-08); request and backlog policies follow different signals, not measured capacity ceilings], [V3 r4 still has no analyser verdict and linearity is unproven; V4’s pass does not change that (§4.1, AISS-12)],
    [Managed services, no servers to run], [ECS Fargate, RDS, ElastiCache, SNS and SQS (AD-07, AD-04, AD-11)], [Verified on AWS (every promotion, §5.3)], [Tied to ECS task definitions and AWS-managed services; less control over versions and tuning], [Data tier single-zone for cost (AISS-01); the NAT instance (fck-nat) is self-run (AD-10)],
    [Problems found only on the cloud], [Ten problems that local tests and plans missed (@tbl-findings)], [Verified on AWS], [Each finding cost a promotion or a session to surface], [Found, not all fixed (AISS-06, AISS-14)],
  ),
  kind: table,
  caption: [Value of the cloud-native design and its evidence. Sources: §2.2, §4.1, §6.3, §6.4.],
) <tbl-cloud-value>

Estimated (one term from list prices): the SWE5001 runtime cost about US\$11.6 from its first session to 10 November 2026 (billed to 10 November, read on 11 and 12 November). For comparison, our #src("docs/swe5001/project-proposal.typ")[proposal] estimated about US\$125 a month for running the runtime continuously; it gives the figure without a sizing (AD-12, §3.2.1). Estimated: while it is up at the release sizing of 11 November (every task 0.5 vCPU, 1 GiB), the runtime costs about US\$0.17–0.20 an hour (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], section 9); at the larger sizing of the 11 November session it cost about US\$0.27 an hour at list prices. The permanent layer cost under US\$1 a month until we destroyed it after the final campaign on 15 November (§5.3).

The figure is an estimate because the shared account cannot split its bill by course. We read the bill from AWS Cost Explorer on 8, 11 and 12 November (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record], #src("docs/swe5001/evidence/20261111-cost-explorer/README.md")[11 November record]). For each October session day, the SWE5001 share is that day's total minus the SWE5006 baseline of the period. That method fails for 9 November: the SWE5006 `t3.large` was stopped on 7 November and restarted on 9 November (US\$1.92 that day), and the baseline of about US\$1.5 a day holds only while it is stopped. So we summed the SWE5001 share of 9 November from its own services.

Two later periods are not in the figure. The 11 November session (larger commerce tasks, `db.t4g.small` and a `t3a.2xlarge` load generator) had not been billed at the read; estimated from list prices, it adds about US\$6–7 to the end of that UTC day, of which about US\$0.54 is the load generator (@tbl-cost). Each further hour the runtime stayed up added about US\$0.27, until its destroy on 12 November (§5.3). The validation sessions of 13–15 November were not yet in Cost Explorer on 15 November and are not costed. On 15 November Cost Explorer showed US\$56.18 for the whole account from 1 to 15 November, an open-month estimate that includes SWE5006 and is incomplete for 13–15 November; we attribute none of it to SWE5001.

#figure(
  table(
    columns: (2.4fr, 1fr, 1.4fr),
    table.header([Item], [USD], [Kind]),
    [Whole account, September 2026 (SWE5006 and SWE5001)], [78.51], [Measured],
    [Whole account, October 2026 (SWE5006 and SWE5001)], [139.08], [Measured],
    [Whole account, 1–11 Nov 2026 (SWE5006 and SWE5001; read on 12 Nov, open month)], [36.79], [Measured (Cost Explorer estimate for an open month)],
    [Whole account, 1–15 Nov 2026 (SWE5006 and SWE5001; read on 15 Nov, open month, 13–15 Nov incomplete)], [56.18], [Measured (Cost Explorer estimate for an open month)],
    [ECS, RDS and ElastiCache, which exist only in the SWE5001 runtime, October 2026], [7.11], [Measured],
    [Whole SWE5001 runtime, including its share of the load balancer, NAT instance and public addresses, 6–21 Oct], [≈ 10.15], [Calculated],
    [SWE5001 sessions billed on 9 Nov (UTC billing day: the promotions of 9 and 10 Nov and the placement session)], [≈ 1.4], [Estimated: the SWE5001 services of that day (ECS 0.60, RDS 0.21, ElastiCache 0.15, NAT 0.04) plus the load balancer and address increase over 8 Nov (about 0.40)],
    [*Whole SWE5001 runtime to 10 Nov*], [*≈ 11.6*], [Calculated: 10.15 + 1.4 (estimated)],
    [SWE5001 session of 11 Nov (UTC billing day), estimated on 12 Nov from list prices, before it was billed: runtime from 13:35 SGT at about 0.27 an hour to 08:00 SGT on 12 Nov (≈ 5.0), scale-outs and the monolith session before it (≈ 0.6) and the load generator, 1.43 h at 0.3776 (≈ 0.54)], [≈ 6–7], [Estimated from list prices (12 Nov)],
    [Permanent SWE5001 layer until 15 Nov (ECR images, the web bucket, an idle CloudFront distribution; destroyed after the final campaign, §5.3)], [< 1 a month], [Calculated: about 5.1 GB of images at US\$0.10 per GB-month ≈ 0.51],
  ),
  kind: table,
  caption: [Cost of the SWE5001 platform; the method is in the text above. Source: AWS Cost Explorer, read on 8, 11 and 12 November.],
) <tbl-cost>

The runtime billed mainly on ten days to 10 November: 6–7 October, 15–21 October and 9 November (Cost Explorer days are UTC, so the sessions of the night of 9 to 10 November fall on 9 November). Estimated: Multi-AZ RDS (+US\$39.99 for the `db.t4g.small` with 20 GB that the study priced; our `db.t4g.micro` would add less, and the study did not price it), a Valkey replica (+US\$14.02, 0.0192 × 730 h) and one NAT Gateway in each of two zones (2 × US\$43.07, before data charges) would add about US\$140 a month, an upper bound for our sizing, at the list prices of 25 September for `ap-southeast-1` (#src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[cost study]); the fck-nat instance they replace is not subtracted.

Three things did not go to plan, and each is a lesson for a production rollout:

- *The bill was about twice the estimate.* The plan expected under US\$5 for about 14 runtime hours. On 19 October we decided to keep the environment up until the presentation (#src("docs/agile/sprint-04/log/2026-10-19.md")[sprint log]), and 19–21 October carry 85% of the SWE5001 runtime cost of October (about US\$10.15), not of the whole account's bill. The decision was reasonable for a live demonstration, but nothing showed its price at the time.
- *No alert would have shown it.* The optional budget was never enabled; on 12 November on AWS, the account still had no budget and no Cost Anomaly Detection monitor.
- *The cost cannot be split by course.* The `Course` tag is on every SWE5001 resource, but it is not activated as a cost-allocation tag, and only the management account can activate it. So the runtime figure is calculated, not read directly.
