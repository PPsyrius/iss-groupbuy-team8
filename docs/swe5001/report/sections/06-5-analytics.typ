// Analytics: part of 06-highlights.typ.
#import "../lib.typ": *

== Analytics

The platform measures how it runs today, and stores the data to measure how the business runs; only the first is built.

*Operational analytics, in use.* On 16 October on AWS, the CloudWatch dashboard showed the edge, the services and the messaging in three rows of charts (#src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record]); an organisation SCP first denied its CloudFront chart, and we fixed that the same day (@tbl-findings). The load results of that day set the committed auto-scaling target; V3 r4 ran with a session-only target (§4.1).

*Alarm coverage.* Two CloudWatch alarms, `blocbuy-swe5001-notification-dlq-not-empty` and `blocbuy-swe5001-notification-queue-age`, notify an SNS topic. Every promotion that reached readiness through 11 November found at least one unconfirmed recipient; #run("42947289936")[the 11 November run] found only one of three confirmed. Both up workflows default `SWE5001_ALLOW_UNCONFIRMED_ALERTS` to `true`, allowing deployment despite this gap. An unconfirmed recipient receives no alarm (AISS-16).

*Alarm delivery.* V7 R4 verified a real DLQ alarm, one matching human email receipt and alarm reset on AWS on 14 November. This demonstrates the path from queue failure to an operator, rather than only a configured alarm. Timing and recipient-coverage limits are in §4.2 and AISS-16.

*Business analytics, designed.* The data needed for four business measures is already stored, but nothing reports on it yet (@tbl-analytics).

#figure(
  table(
    columns: (1.3fr, 1.5fr, 2.1fr, 2.1fr),
    table.header([Measure], [Why it matters], [Stored data it comes from], [From the three events alone]),
    [Team fill rate and time to fill, per product], [Shows which offers and team sizes work, and when deadlines are too short], [`biz_group_deal`: `product_id`, `status`, `start_time`, `success_time`, `expire_time`], [Fill rate: yes, because `TeamSucceeded` and `TeamExpired` carry `productId`. Time to fill: approximate, by pairing a START `PaymentSucceeded` (its `businessId` is the team's id) with that team's `TeamSucceeded`],
    [Referral conversion], [Shows whether sharing brings new buyers, the platform's growth engine], [`biz_share_invite` (`deal_id`, `sharer_user_id`), `biz_share_credit` (`sharer_user_id`, `invitee_user_id`, `credit_time`), `sharer_user_id` on `biz_group_member` and `biz_purchase_intent`], [No: no event carries an invite, a sharer or a credit],
    [Start-to-success conversion], [Shows how many started teams succeed], [`biz_group_deal`: `status`, `start_time`, `success_time`], [Approximate: START `PaymentSucceeded` events against `TeamSucceeded` events; a START payment refused at settlement creates no team but still emits `PaymentSucceeded`],
    [Refund rate per product], [Shows offers that repeatedly fail to fill], [`biz_refund` (`intent_id`, `reason`, `status`) joined to `biz_purchase_intent.product_id`, so a START refunded before its team exists is counted (its `deal_id` is empty)], [No: no refund event exists; `TeamExpired` per product is a proxy that misses payments refused at settlement],
  ),
  kind: table,
  caption: [Business measures designed on stored data. Source: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B11]. Tables and columns from the Flyway migrations V5, V9, V15, V18 and V19; event payloads from `TeamSucceededV1`, `TeamExpiredV1` and `PaymentSucceededV1`.],
) <tbl-analytics>

Designed but not implemented: an analytics consumer is a read-only subscriber on the event topic, built exactly like the audit queue (§4.4), so `commerce-svc` does not change. The topic carries only `TeamSucceeded`, `TeamExpired` and `PaymentSucceeded`, so on its own it supplies the fill rate per product and approximate time to fill and start-to-success conversion (@tbl-analytics), but only for teams started after the consumer subscribes. The topic is a standard SNS topic with no archive, so it cannot replay earlier events. Earlier teams, referral conversion and the refund rate need a read-only extract of the stored tables, which is not designed yet. Estimated: the study's pipeline from SNS through Firehose into S3, queried with Athena, costs about US\$0.20 a month (#src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[cost estimate]).
