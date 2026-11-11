// What Only the Real Cloud Revealed: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== What Only the Real Cloud Revealed

Ten problems appeared only on AWS, after mocks, local runs and Terraform plans had passed (@tbl-findings). We fixed each one or recorded it as a limitation. They are the strongest argument for testing on the target cloud.

#figure(
  table(
    columns: (1.9fr, 2.2fr, 1.4fr),
    table.header([Finding on AWS], [What happened], [Outcome]),
    [The first promotion failed five times (6 Oct)], [Image digests, run variables, a tag policy and an early HTTP 503 each failed in turn], [Fixed one at a time; first green run on 6 Oct (#run("37470479078")[run 37470479078])],
    [The read-only container file system broke the web server], [The embedded server could not write its temporary folder], [A writable `/tmp` volume for every service (§4.4)],
    [The AWS tagging API lists deleted resources], [The teardown check reported resources that no longer existed], [The check asks each service directly],
    [The apply role could not tag scaling targets (16 Oct)], [Creating the auto-scaling targets needs `application-autoscaling:TagResource`], [Permission added, scoped to the project (#pr(527))],
    [Unconfirmed alarm emails stopped deployment twice (16 Oct)], [The messaging gate waited for SNS email subscriptions that nobody had confirmed], [Opt-out for test runs, on by default (#pr(529)). Every later promotion still found an unconfirmed recipient (§6.5, AISS-16)],
    [New SNS subscription filters take up to 15 min to apply], [Events published earlier are silently dropped], [The deployment waits 15 min (900 s) (AISS-06)],
    [An organisation SCP blocked CloudFront metrics (16 Oct)], [CloudFront metrics are served only from `us-east-1`, which the region guardrail denied], [SCPs consolidated with a narrow exception (#src("docs/swe5001/evidence/20261016-scp-consolidation/README.md")[SCP record])],
    [An interrupted start-up left auto-scaling suspended], [The start-up reported success, but the scaling targets stayed paused], [Repaired by hand; no fix yet (AISS-14)],
    [The apply role could not update the edge's response headers policy (9 Nov)], [#pr(700) (27 Oct) added a header to the `edge` stack's response headers policy. The required plan check passed, because for `edge` it runs offline with a mocked provider and the live plan covers only `foundation`; the apply role may read the policy but not update it. With no promotion for 13 days, the gap surfaced at the next release, which was denied `cloudfront:UpdateResponseHeadersPolicy` and destroyed its runtime (#src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record])], [We updated the policy directly through the CloudFront API and read it back, without a Terraform apply or IAM grant (#src("docs/swe5001/evidence/20261109-edge-policy-recovery/README.md")[recovery record]). The next promotion passed on 10 Nov (#run("42653347818")[run 42653347818]) and its apply converged, but we recorded no zero-drift plan. Lessons: offline plans do not exercise the apply role's permissions, and a change made outside Terraform needs a follow-up plan that shows no changes],
    [The edge points at a deleted load balancer after teardown (read 8 Nov)], [`/api/*` on the SWE5001 distribution names the last runtime's load balancer, which no longer exists], [Harmless while the runtime is down: the next start-up passes the new load balancer to the distribution (#src("docs/swe5001/HANDOFF.md")[handoff record])],
  ),
  kind: table,
  caption: [Problems that appeared only on AWS, with their outcome. Source: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B8]\; the #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record]\; the 8 November AWS read (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[cost record], last section)\; the records of the promotions of #src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November] and #src("docs/swe5001/evidence/20261109-promotion-770/README.md")[10 November].],
) <tbl-findings>
