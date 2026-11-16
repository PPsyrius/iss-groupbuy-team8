// Where to See the System Working: part of 06-highlights.typ.
#import "../lib.typ": *

== Where to See the System Working

The system is shown through recorded runs and can be rebuilt by the promotion pipeline once foundation and edge are present (§5.3); @tbl-demo lists where each part can be seen. The runtime is disposable (AD-12); these records, rather than a permanently running demonstration site, provide the repeatable points of reference.

#figure(
  table(
    columns: (1.6fr, 2.4fr, 1.2fr),
    table.header([What], [Where], [Kind]),
    [Two buyers forming one team: leader starts and pays, member joins and pays], [24 Oct SWE5006 monolith/EC2 recording (#iss(590), #iss(657); §3.2.4, @tbl-two-buyer-walkthrough). The 15 Nov distributed START/JOIN reached team 2/2 with reconciled event and recipient notifications (@tbl-followup-15nov, §4.2)], [Recorded monolith walkthrough; verified AWS distributed normal path],
    [Storefront served by the three services through CloudFront, in a browser], [#src("docs/swe5001/evidence/20261019-ecs-demo-images/README.md")[19 October record] of the session created by #run("39198123242")[run 39198123242]: the storefront loaded signed out, but the demonstration seed broke all ten product images (#iss(557))], [Verified on AWS (screenshot, signed out, no purchase)],
    [Live session for the 21 October presentation], [Runtime created by #run("39525458726")[run 39525458726] (21 Oct, 09:07 SGT, succeeded) and destroyed by #run("39658040903")[run 39658040903], which the scheduled safety net dispatched at 03:01 SGT on 22 Oct], [Session runs verified on AWS\; what was shown live is not recorded],
    [Three services created, smoke-tested through CloudFront, released and destroyed], [Promotion execution records and timings in @tbl-delivery-runs and @tbl-delivery-timings (§5.3); smoke scope in §2.2], [Verified on AWS],
    [Browse load and automatic scale-out], [#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record] (11 Nov): 726,000 requests up to 600 req/s, no errors or drops, commerce one to six tasks; scale-in to two at 23:09–23:21 from Lim's readback of ECS events (#src("docs/swe5001/evidence/20261112-issue-789/README.md")[follow-up record])], [Verified on AWS; no analyser verdict (§4.1); scale-in attributed, not tested],
    [Notification scaling, task replacement and poison isolation], [V4 R5, V5 R3 and V7 R4 result records in §4.1–§4.2 identify each workload, tested configuration and recovery outcome], [Verified on AWS within tested configurations],
    [Payment initiation while identity is down, then up], [15 Nov focused R11 START 500 → 200 and scoped DB reconciliation (@tbl-followup-15nov)], [Verified on AWS],
    [Pull-request checks: build, architecture rules, contract check, image scan, Terraform plan], [Every pull request into `dev`; `ci-backend`, `ci-frontend` and `security` report success when their files did not change, while `swe5001-service-images` and `infra-swe5001` run only when their paths change (§5.2)], [Tested in CI],
    [Release gate: source is `dev`, service images, infrastructure plan], [Every promotion pull request into `prod` (AD-19)], [Tested in CI],
    [Source code and infrastructure], [#link(repo)[github.com/PPsyrius/iss-groupbuy-team8]], [—],
  ),
  kind: table,
  caption: [Where the system can be seen working. Source: the GitHub runs and dated records named in each row.],
) <tbl-demo>

Together, these records show a working distributed group-buying platform, bounded scaling and recovery results, and the remaining production-readiness gaps (§3.4). The source and evidence under `docs/swe5001/evidence/` support reproduction after the runtime is removed; rebuilding from no stacks first requires `foundation` and `edge`, then the promotion pipeline (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], sections 4 and 5).
