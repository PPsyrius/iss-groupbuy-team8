// What the Cloud Cost: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== What the Cloud Cost

Calculated: the SWE5001 runtime cost about US\$10 from its first session to 8 November 2026. That is the value of the disposable design (briefing Q6): the environment exists only while it is used, and the permanent layer costs under US\$1 a month. The figures below were read from AWS Cost Explorer on 8 November 2026 and are recorded in #dev-src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[the cost record]. The two promotions of 9 November came after that read and are not included.

#figure(
  table(
    columns: (2.4fr, 1fr, 1.4fr),
    table.header([Item], [USD], [Kind]),
    [Whole account, September 2026 (SWE5006 and SWE5001)], [78.51], [Measured],
    [Whole account, October 2026 (SWE5006 and SWE5001)], [139.08], [Measured],
    [ECS, RDS and ElastiCache, which exist only in the SWE5001 runtime, October 2026], [7.11], [Measured],
    [Whole SWE5001 runtime, including its share of the load balancer, NAT instance and public addresses, 6 October – 21 October], [≈ 10.15], [Calculated],
    [Permanent SWE5001 layer (ECR images, the web bucket, an idle CloudFront distribution)], [< 1 a month], [Calculated: about 5.1 GB of images at US\$0.10 per GB-month ≈ 0.51],
  ),
  kind: table,
  caption: [Cost of the SWE5001 platform. The runtime figure is each session day's total minus the SWE5006 baseline of that period; the method is in the cost record.],
) <tbl-cost>

The runtime billed on only nine days: 6–7 October and 15–21 October. For comparison, keeping the same runtime on all the time was estimated at about US\$125 a month (AD-12, §3.2.1).

Three things did not go to plan, and each is a lesson for a production rollout:

- *The bill was about twice the estimate.* The plan expected under US\$5 for about 14 runtime hours. On 19 October the team decided to keep the environment up until the presentation (#src("docs/agile/sprint-04/log/2026-10-19.md")[sprint log]), and 19–21 October carry 85% of the cost. The decision was reasonable for a live demonstration, but nothing showed its price at the time.
- *No alert would have shown it.* The optional budget was never enabled, and no Cost Anomaly Detection monitor exists.
- *The cost cannot be split by course.* The `Course` tag is on every SWE5001 resource, but it is not activated as a cost-allocation tag, and only the management account can activate it. So the runtime figure is calculated, not read directly.

The US\$0.49 for seven sessions shown at the presentation had no committed record and is superseded by this section.
