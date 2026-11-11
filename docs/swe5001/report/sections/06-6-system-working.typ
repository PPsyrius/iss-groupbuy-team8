// Where to See the System Working: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== Where to See the System Working

The runtime is destroyed between sessions (AD-12), so the system is shown through recorded runs and can be rebuilt by one promotion (§4.2), as on the evening of 9 November. @tbl-demo lists where each part can be seen.

#figure(
  table(
    columns: (1.6fr, 2.4fr, 1.2fr),
    table.header([What], [Where], [Kind]),
    [Two buyers forming one team: leader starts and pays, member joins and pays], [Walkthrough recorded on 24 October 2026 on the SWE5006 `dev` deployment (#iss(590), recording #iss(657)); its design is in §3.2.4. That day the only `swe5001-env` runs were the scheduled nightly `down`s, so no three-service runtime existed. No paid buyer journey on the three-service runtime is recorded (§2.2)], [Recorded on the SWE5006 monolith (EC2), same codebase, `monolith` topology\; not the three-service runtime],
    [Storefront served by the three services through CloudFront, in a browser], [#src("docs/swe5001/evidence/20261019-ecs-demo-images/README.md")[19 October record] of the session created by #run("39198123242")[run 39198123242]: the storefront loaded signed out, but all ten product images were broken by the demo seed (#iss(557))], [Verified on AWS (screenshot, signed out, no purchase)],
    [Live session for the 21 October presentation], [Runtime created by #run("39525458726")[run 39525458726] (21 October, 01:07 UTC, succeeded) and destroyed by the nightly `down` #run("39658040903")[run 39658040903] (21 October, 19:01 UTC)], [Session runs verified on AWS\; what was shown live is not recorded],
    [Three services created, smoke-tested through CloudFront, released and destroyed], [#run("39008208244")[Promotion run 39008208244] (17 October) and #run("42653347818")[run 42653347818] (9 November, #dev-src("docs/swe5001/evidence/20261109-promotion-770/README.md")[record]) built the runtime from zero (115 and 120 resources). The promotions of 19 and 20 October (#run("39259845179")[run 39259845179], #run("39435280952")[run 39435280952]) smoke-tested, released and destroyed a runtime that a session had created earlier the same day (#run("39198123242")[run 39198123242], #run("39357098022")[run 39357098022]). What the smoke suite checks is in §2.2], [Verified on AWS],
    [Auto-scaling from one to three tasks under load], [#src("docs/swe5001/evidence/20261019-v3-autoscaling/README.md")[19 October record] (§4.1)], [Scale-out verified on AWS; invalid as acceptance evidence],
    [Pull-request checks: build, architecture rules, contract check, image scan, Terraform plan], [Every pull request into `dev`\; each job runs only when its files changed and otherwise reports success (§5.2)], [Running in CI],
    [Release gate: source is `dev`, service images, infrastructure plan], [Every promotion pull request into `prod` (AD-19)], [Running in CI],
    [Source code and infrastructure], [#link(repo)[github.com/PPsyrius/iss-groupbuy-team8]], [—],
  ),
  kind: table,
  caption: [Where the system can be seen working.],
) <tbl-demo>

After the final `terraform destroy` of 16 November (@tbl-milestones) removes every stack, the GitHub Actions runs, the evidence records under `docs/swe5001/evidence/` and the source remain, and a rebuild needs an operator to re-apply `foundation` (the GitHub OIDC roles the pipeline assumes and the ECR repositories) and `edge` before any promotion can run (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], sections 4 and 5).
