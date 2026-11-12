// Analytics: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Analytics

The platform measures how it runs today, and stores the data to measure how the business runs; only the first is built.

*Operational analytics, in use.* On 16 October on AWS, the CloudWatch dashboard showed the edge, the services and the messaging in three rows of charts (#src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record]); an organisation SCP first denied its CloudFront chart, and we fixed that the same day (@tbl-findings). The load results of that day set the committed auto-scaling target; §4.1 gives the session-only target that V3 R4 ran with and what it observed.

*Alarms, configured but never exercised.* Configured, not tested: two CloudWatch alarms, `blocbuy-swe5001-notification-dlq-not-empty` and `blocbuy-swe5001-notification-queue-age`, notify an SNS topic with three email subscriptions. No session record shows an alarm firing or a notification arriving. The readiness check of every promotion that reached it found an unconfirmed recipient: two of three in #run("39008208244")[run 39008208244] (17 October), one in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October), all three in #run("42587537517")[run 42587537517] (9 November) and #run("42653347818")[run 42653347818] (10 November), and two of three in #run("42947289936")[run 42947289936] (11 November). On 11 November on AWS, the alert topic `blocbuy-swe5001-alerts` had three email subscriptions: one confirmed and two pending confirmation. Each run continued only because both up workflows default the opt-out `SWE5001_ALLOW_UNCONFIRMED_ALERTS` to `true`, although `env-up.sh` alone defaults it to `false` (@tbl-findings). An unconfirmed recipient receives no alarm (AISS-16).

*Business analytics, designed.* The data needed for four business measures is already stored, but nothing reports on it yet (@tbl-analytics).

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

Designed but not implemented: an analytics consumer is a read-only subscriber on the event topic, built exactly like the audit queue (§4.4), so `commerce-svc` does not change. Estimated: the study's pipeline from SNS through Firehose into S3, queried with Athena, costs about US\$0.20 a month (#src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[cost estimate]).
