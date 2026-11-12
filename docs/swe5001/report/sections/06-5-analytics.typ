// Analytics: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== Analytics

The platform measures how it runs today, and stores the data to measure how the business runs; only the first is built.

*Operational analytics, in use.* On 16 October on AWS, the CloudWatch dashboard showed the edge, the services and the messaging in three rows of charts; its CloudFront chart was first denied by an organisation SCP and fixed the same day (@tbl-findings). The load results of that day set the auto-scaling target (§4.1). Two CloudWatch alarms are configured, on the notification dead-letter queue and on the age of the oldest notification message; both notify an SNS topic with three e-mail subscriptions. They have never been exercised. No session record shows an alarm firing or a notification arriving, and the readiness check of every promotion that reached it found an unconfirmed recipient: two of three in #run("39008208244")[run 39008208244] (17 October), one in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October), and all three in #run("42587537517")[run 42587537517] and #run("42653347818")[run 42653347818] (9 November). Each run continued only because the opt-out `SWE5001_ALLOW_UNCONFIRMED_ALERTS` defaults to `true` in the deployment workflow (@tbl-findings). An unconfirmed recipient receives no alarm. #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record].

*Business analytics, designed.* The data needed for four business measures is already stored, but nothing reports on it yet:

#figure(
  table(
    columns: (1.6fr, 2fr, 2fr),
    table.header([Measure], [Why it matters], [Data already stored]),
    [Team fill rate and time to fill, per product], [Shows which offers and team sizes work, and when deadlines are too short], [`biz_group_deal` and `biz_group_member` with their timestamps],
    [Referral conversion], [Shows whether sharing brings new buyers, the platform's growth engine], [`biz_share_invite`, `biz_share_credit`, `biz_purchase_intent.sharer_user_id`],
    [Start-to-success conversion], [Shows how many started teams succeed], [`biz_group_deal` status and timestamps],
    [Refund rate per product], [Shows offers that repeatedly fail to fill], [Refunds and expired teams],
  ),
  kind: table,
  caption: [Business measures designed on stored data. Source: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B11].],
) <tbl-analytics>

*Next step, designed but not implemented.* An analytics consumer is a read-only subscriber on the event topic, built exactly like the audit queue (§4.4), so `commerce-svc` does not change. The study's pipeline from SNS through Firehose into S3, queried with Athena, was estimated at about US\$0.20 a month. #src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[Cost estimate].
