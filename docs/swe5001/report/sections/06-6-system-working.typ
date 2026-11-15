// Where to See the System Working: part of 06-highlights.typ.
#import "../lib.typ": *

== Where to See the System Working

The system is shown through recorded runs and can be rebuilt by one promotion (§4.2), as on 10 and 11 November; @tbl-demo lists where each part can be seen. The runtime is normally destroyed between sessions (AD-12); the 11 November session was still up at the status date and was destroyed on 12 November (§2.2). @tbl-delivery-runs (§5.3) lists the runs from 9 November.

#figure(
  table(
    columns: (1.6fr, 2.4fr, 1.2fr),
    table.header([What], [Where], [Kind]),
    [Two buyers forming one team: leader starts and pays, member joins and pays], [Historical 24 Oct SWE5006 monolith/EC2 walkthrough (#iss(590), recording #iss(657)) remains unchanged (§3.2.4, @tbl-two-buyer-walkthrough). New 15 Nov SGT distributed normal paid START/JOIN reached team 2/2, with reconciled event and exact-one notification per buyer. Fixture, authorized admin grant, earlier failed attempt/refund and parity limits are in @tbl-followup-15nov (§4.2)], [AWS qualified normal-path pass; new source LOCAL WORKING-TREE, unpushed],
    [Storefront served by the three services through CloudFront, in a browser], [#src("docs/swe5001/evidence/20261019-ecs-demo-images/README.md")[19 October record] of the session created by #run("39198123242")[run 39198123242]: the storefront loaded signed out, but the demonstration seed broke all ten product images (#iss(557))], [Verified on AWS (screenshot, signed out, no purchase)],
    [Live session for the 21 October presentation], [Runtime created by #run("39525458726")[run 39525458726] (21 Oct, 09:07 SGT, succeeded) and destroyed by #run("39658040903")[run 39658040903], which the scheduled safety net dispatched at 03:01 SGT on 22 Oct], [Session runs verified on AWS\; what was shown live is not recorded],
    [Three services created, smoke-tested through CloudFront, released and destroyed], [#run("39008208244")[Promotion run 39008208244] (17 Oct), #run("42653347818")[run 42653347818] (10 Nov, 00:18–01:11 SGT, #src("docs/swe5001/evidence/20261109-promotion-770/README.md")[record]) and #run("42947289936")[run 42947289936] (11 Nov) built the runtime from zero (115, 120 and 120 resources). The promotions of 19 and 20 Oct (#run("39259845179")[run 39259845179], #run("39435280952")[run 39435280952]) smoke-tested, released and destroyed a runtime that a session had created earlier the same day (#run("39198123242")[run 39198123242], #run("39357098022")[run 39357098022]). What the smoke suite checks is in §2.2], [Verified on AWS],
    [Browse load and automatic scale-out], [#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record] (11 Nov): 726,000 requests up to 600 req/s, no errors or drops, commerce one to six tasks; scale-in to two at 23:09–23:21 from Lim's readback of ECS events (#src("docs/swe5001/evidence/20261112-issue-789/README.md")[follow-up record])], [Verified on AWS; no analyser verdict (§4.1); scale-in attributed, not tested],
    [Notification scaling, task replacement and poison isolation], [V4 R5 remains experimental, V5 R3 corrected-sizing/warm-up and V7 R4 temporary 10-second consumer visibility, not original 60-second timing acceptance; owner email is minute-only, original configuration restored (§4.1–§4.2). Published records: #published-evidence("20261114-v4-r5-live/working-report-addendum.md")[V4/V5] and #published-evidence("20261114-v7-r4-live/README.md")[V7]. New offline service-birth linkage supports one provisioned campaign, not continuous uptime (@tbl-followup-15nov)], [AWS configuration-bounded results; new linkage LOCAL WORKING-TREE],
    [Payment initiation while identity is down, then up], [15 Nov focused R11 START 500 → 200 with scoped DB reconciliation (@tbl-followup-15nov). Historical 13 Nov identity R1 stays failed; no R11 card confirmation or AWS webhook/redelivery/replay proof], [AWS focused validation; source LOCAL WORKING-TREE, unpushed],
    [Pull-request checks: build, architecture rules, contract check, image scan, Terraform plan], [Every pull request into `dev`; `ci-backend`, `ci-frontend` and `security` report success when their files did not change, while `swe5001-service-images` and `infra-swe5001` run only when their paths change (§5.2)], [Tested in CI],
    [Release gate: source is `dev`, service images, infrastructure plan], [Every promotion pull request into `prod` (AD-19)], [Tested in CI],
    [Source code and infrastructure], [#link(repo)[github.com/PPsyrius/iss-groupbuy-team8]], [—],
  ),
  kind: table,
  caption: [Where the system can be seen working. Source: the GitHub runs and dated records named in each row.],
) <tbl-demo>

After the final `terraform destroy` of 16 November (@tbl-milestones) removes every stack, the GitHub Actions runs, the evidence records under `docs/swe5001/evidence/` and the source remain. A rebuild then needs us to re-apply `foundation` (the GitHub OIDC roles the pipeline assumes and the ECR repositories; the account's GitHub OIDC provider is only read by `foundation`, so it survives the destroy) and `edge` before any promotion can run (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], sections 4 and 5).
