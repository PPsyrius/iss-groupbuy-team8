// What Only the Real Cloud Revealed: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== What Only the Real Cloud Revealed

Mocks, local runs and Terraform plans passed before each of these problems appeared on AWS. Each was found on a real deployment and fixed, or is recorded as a limitation (@tbl-findings). They are the strongest argument for testing on the target cloud.

#figure(
  table(
    columns: (1.9fr, 2.2fr, 1.4fr),
    table.header([Finding on AWS], [What happened], [Outcome]),
    [The first promotion failed five times (6 October)], [Image digests, run variables, a tag policy and an early HTTP 503 each failed in turn], [Fixed one at a time; first green run 6 October (#run("37470479078")[run 37470479078])],
    [The read-only container file system broke the web server], [The embedded server could not write its temporary folder], [A writable `/tmp` volume for every service (§4.4)],
    [The AWS tagging API lists deleted resources], [The teardown check reported resources that no longer existed], [The check asks each service directly],
    [The apply role could not tag scaling targets (16 October)], [Creating the auto-scaling targets needs `application-autoscaling:TagResource`], [Permission added, scoped to the project (#pr(527))],
    [Unconfirmed alarm e-mails stopped deployment twice (16 October)], [The messaging gate waited for SNS e-mail subscriptions that nobody had confirmed], [Opt-out for test runs, on by default (#pr(529)). Every later promotion still found an unconfirmed recipient (§6.5)],
    [New SNS subscription filters take up to 15 minutes to apply], [Events published earlier are silently dropped], [The deployment waits 900 seconds (AISS-06)],
    [An organisation SCP blocked CloudFront metrics (16 October)], [CloudFront metrics are served only from `us-east-1`, which the region guardrail denied], [SCPs consolidated with a narrow exception. #src("docs/swe5001/evidence/20261016-scp-consolidation/README.md")[SCP record]],
    [An interrupted start-up left auto-scaling suspended], [The start-up reported success, but the scaling targets stayed paused], [Repaired by hand; no fix yet (AISS-14)],
    [The apply role could not update the edge's response headers policy (9 November)], [#pr(700) (27 October) added a header to the `edge` stack's response headers policy. The required plan check passed, because for `edge` it runs offline with a mocked provider and the live plan covers only `foundation`; the apply role may read the policy but not update it. With no promotion for 13 days, the gap surfaced at the next release, which was denied `cloudfront:UpdateResponseHeadersPolicy` and destroyed its runtime (#dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record])], [The retained #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-edge-policy-recovery/README.md")[operator recovery account] records a direct CloudFront policy update and API readback, not a Terraform apply or IAM grant. The later #run("42653347818")[promotion] passed. Lesson: offline plans do not exercise the apply role's permissions; operator-owned changes need their own evidence],
    [The edge points at a deleted load balancer after teardown (read 8 November)], [`/api/*` on the SWE5001 distribution names the last runtime's load balancer, which no longer exists], [Harmless while the runtime is down: the next start-up passes the new load balancer to the distribution (#src("docs/swe5001/HANDOFF.md")[handoff record])],
  ),
  kind: table,
  caption: [Problems that appeared only on AWS. Sources: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B8]\; #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record]\; the 8 November AWS read, #dev-src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[cost record, last section]\; the 9 November records of the #dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[first] and #dev-src("docs/swe5001/evidence/20261109-promotion-770/README.md")[second] promotion.],
) <tbl-findings>
