// Availability: part of 04-quality-attributes.typ.
#import "../lib.typ": *

== Availability

By design, a failed task, a broker outage or an identity outage loses no committed payment or event (AD-04, AD-06). We tested the identity outage and a poison message locally on 15 October 2026, not on AWS, and we saw ECS replace tasks on AWS only during load tests, never in a controlled drill (AISS-13). The database, the Valkey node and the NAT instance each run in one zone, so a zone failure still stops purchases, sign-ins or outbound calls (AISS-01). @tbl-availability-failures lists each failure, what recovers it and whether we saw it on AWS or locally.

*Application recovery.* On 16 October on AWS, two healthy commerce tasks ran in different zones. That deployment passed its smoke suite at 14:10 SGT only after an interrupted start and a manual repair of suspended scaling targets (#src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[placement record], AISS-14). The ALB routes only to healthy targets, and ECS keeps the desired task count (AD-07, AD-08). The deployment circuit breaker rolls an unhealthy deployment back to the last completed one (#src("infra/swe5001/modules/ecs-service/main.tf")[ECS service module]). Configured, not tested: V5 (a task stopped under load) and V6 (a bad release rolled back) were not run on AWS.

#figure(
  table(
    columns: (0.9fr, 1.6fr, 1.35fr, 1.9fr, 0.85fr),
    table.header([Failure], [What recovers it (AD)], [Where seen, and when], [Time or result], [Limit]),
    [Task fails], [ECS starts a replacement; the ALB stops routing to it (AD-07)], [AWS, 10 and 11 Nov, during load tests; not a controlled drill], [Replacements started; not timed], [AISS-13 (V5 not run)],
    [Spot task reclaimed], [ECS replaces it from the capacity provider (AD-08)], [AWS, 11 Nov, during V3 r3], [Stopping from 19:00:30 SGT; replacement started at 19:01:59 SGT (89 s later), then healthy], [One observation],
    [Bad release], [A failed smoke test blocks the release (§5.3); the circuit breaker rolls back], [AWS configuration only; not tested], [—], [AISS-13 (V6 not run)],
    [Zone lost], [Tasks run in two zones; the data stores and the NAT instance do not (AD-10, AD-11)], [AWS, 16 Oct: two zones seen; zone loss not tested], [Purchases, sign-ins and outbound calls stop], [AISS-01],
    [Database lost], [Rebuild the environment (AD-12); no backups (`backup_retention_period = 0`)], [AWS, rebuilds 17 Oct – 11 Nov], [The session's data is lost. A rebuild takes 30 min 59 s – 33 min 47 s], [AISS-06, AISS-01],
    [Valkey lost], [None: one node, no snapshots], [AWS configuration only; not tested], [Every user signs in again; offer reads fall back to PostgreSQL], [AISS-10, AISS-01],
    [Broker unavailable], [The outbox row stays committed; the relay retries (AD-04)], [Neither AWS nor local: designed, not tested], [Notifications arrive later], [AISS-11],
    [Poison message], [SQS moves it to the DLQ after five receives (AD-04)], [Local, 15 Oct (ElasticMQ)], [Moved after five receives. The next valid message was acknowledged by the test decoder, not the production consumer. The DLQ alarm has never fired on AWS], [AISS-13],
    [Identity service down], [Commerce fails closed; the webhook returns 500 and Stripe redelivers (AD-06)], [Local, 15 Oct], [Browse stayed at HTTP 200; START and the webhook returned 500 with no order or membership. After the restore, one redelivery left one event, one membership and one order], [AISS-03, AISS-13],
  ),
  kind: table,
  caption: [Failure modes and recovery, with where each was seen. Source: #src("docs/swe5001/evidence/20261015-local-failure-rehearsal/README.md")[15 October rehearsal], #src("docs/swe5001/evidence/20261111-v3-r3/DIAGNOSIS.md")[V3 r3 diagnosis] and the records in @tbl-performance-runs.],
) <tbl-availability-failures>

*Message recovery.* Designed, not tested on AWS: a committed outbox row survives a broker outage, because the relay retries publication to SNS up to ten times (`OutboxRelayService.MAX_ATTEMPTS`). SQS moves a message that fails five receives to its DLQ (AD-04, §3.2.3). Redelivery after a consumer commit is safe, because the consumer records each event in `sys_consumed_message`. Notifications can therefore arrive later, and exhausted relay retries or dead-letter messages need an operator (#src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[integration decision], #src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/notification/messaging/SqsNotificationEventConsumer.java")[queue consumer], #src("infra/swe5001/modules/messaging/main.tf")[queue and alarm configuration]).

#block(breakable: false)[*Rebuilding the environment.* On AWS, five clean rebuilds between 17 October and 11 November took 30 min 59 s to 33 min 47 s (@tbl-delivery-timings, §5.3), above the 30 min target (AISS-06). A rebuild restores service, not data: it is neither a database restore nor uninterrupted service during a zone loss.]
