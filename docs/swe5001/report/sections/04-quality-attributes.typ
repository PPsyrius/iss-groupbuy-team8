#import "../lib.typ": *

= Quality Attributes

== Performance
#owner([Panpakorn Siripanich (Pan)], closes: "F1, briefing Q4")
#prompt[The purpose of this section is to describe the solution handles the performance requirement of the system. You can describe the use of caching, load balancing, any optimization that you plan/have done in the design of the system.]
#todo(
  [Metric definitions table: workload model, client and server metrics, pass thresholds.],
  [Results per run (16 Oct V2, 19 Oct V3) with what each run does and does not prove.],
  [Caching, load balancing, scaling policy (12,000 requests per target per minute).],
  [Capacity: measured, projected, next limit; linear scaling not proven (AISS-12).],
)

== Availability

BlocBuy can keep the catalogue available while a payment dependency recovers. Its Amazon Elastic Container Service (ECS) tasks can run across availability zones, but the academic deployment still depends on a single-zone database, one Valkey node and one NAT instance. A zone failure can therefore interrupt purchases, sessions or outbound calls even while an application task survives. The proposed production changes are recorded under AISS-01 in §3.4. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/runtime/data-stores.tf")[Data configuration] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/report/registers.md")[Availability limitations].

*Application recovery.* On 16 October 2026, AWS showed two healthy commerce tasks in different zones. The Application Load Balancer routes to healthy targets, and ECS maintains the desired number of tasks. The deployment circuit breaker is configured to roll back an unhealthy deployment to the last completed deployment. Replacing a failed task restores capacity, while rolling back a release restores a known task definition. The team observed placement and a successful deployment. V5 still needs a controlled task stop under load; V6 describes the configuration only. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[AWS placement record] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/modules/ecs-service/main.tf")[ECS health checks and rollback] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/issues/459")[Drill scope].

#figure(
  table(
    columns: (1.0fr, 2.3fr, 1.8fr),
    table.header([Failure], [Observed locally, 15 October 2026], [What remains to verify on AWS]),
    [Identity unavailable], [Browse returned HTTP 200. START and a correctly signed webhook returned 500. The payment stayed in its initial state, with no committed order or membership.], [Stop the deployed identity tasks and measure the interruption and recovery. See AISS-03 and AISS-13.],
    [Identity restored], [Redelivery returned HTTP 200. Another delivery left one event, one membership and one order.], [Repeat against deployed services. The local identity and Stripe providers were substitutes.],
    [Malformed queue message], [ElasticMQ moved the message to its dead-letter queue after five receives. A subsequent valid message could be processed.], [Exercise the production consumer and observe its CloudWatch alarm. The local decoder used accelerated retry spacing.],
  ),
  kind: table,
  caption: [Failure isolation. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/docs/swe5001/evidence/20261015-local-failure-rehearsal/README.md")[dated rehearsal] used the real transaction and a local queue.],
)

*Message recovery.* In the distributed configuration, a committed outbox row survives a broker outage. The relay retries publication to Amazon Simple Notification Service (SNS). Its subscriptions deliver events to separate Simple Queue Service (SQS) queues. SQS moves repeatedly failing messages to a dead-letter queue. Redelivery after a successful consumer commit is safe because the event and consumer identify an existing processing record. Notifications can arrive later, and exhausted relay retries or dead-letter messages require operator attention. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0007-cross-service-events-sns-sqs.md")[Integration decision] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/notification/messaging/SqsNotificationEventConsumer.java")[Queue acknowledgement] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/infra/swe5001/modules/messaging/main.tf")[Queue and alarm configuration].

#block(breakable: false)[*Rebuilding the environment.* The 17 October 2026 three-service promotion created 115 runtime resources in its initial apply (520 s), passed its smoke suite (5 s) and destroyed the runtime (709 s). The entire up job took 31 min 37 s, above the 30-minute target. A second clean three-service session that day took 32 min 17 s for the up job. These are rebuild timings for a disposable environment; they establish neither a database restore time nor uninterrupted service during a zone loss. #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[Promotion run] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39014054529")[Second clean session] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/impl-verification-observability.md#v8-rebuild-from-zero")[V8 timing records].]

== Security
#owner[Panpakorn Siripanich (Pan)]
#prompt[The purpose of this section is to describe the security that will be incorporated into the solution which includes any security services: IAM, encryption, hashing, IDS, IPS, etc.]
#todo(
  [Layered controls: Organization SCPs, Identity Center, OIDC, network isolation incl. data subnets (AD-15), encryption, secrets, pipeline scans.],
  [Known gaps by AISS number (AISS-04, -08, -09, -15).],
)

== Extensibility and Maintainability
#owner([Lim Jiechao], closes: "briefing Q2, Q3")
#prompt[The purpose of this section is to describe the solution handles the maintainability requirement. How the platform and applications can be extended, enhanced and maintained in the future? You can refer to the platform section if necessary.]
#todo(
  [Measured reuse: the audit queue added without changing the producer.],
  [Golden-path `ecs-service` module; architecture rules; API contract checks.],
)
