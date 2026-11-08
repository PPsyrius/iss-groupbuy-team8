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

// Section 4.4 (owner: Lim). Links pinned to dev at b80debcd (8 November 2026).
#let lim-src(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

The platform is extended at three seams: a new event consumer, a new service and a new public operation. Each is a small, bounded edit that the pipeline checks. The platform stays maintainable because one codebase, with boundaries that fail the build when crossed (AD-18), produces both the monolith and the three services. This section answers briefing Q2 (common services and reusable frameworks) and Q3 (the benefit of building a new use case on the platform). The platform's producers and consumers are described in §3.1.4.6.

=== Three extension seams

#figure(
  table(
    columns: (0.9fr, 1.6fr, 2.1fr, 1.6fr),
    table.header([Change], [What a developer edits], [What comes with it], [What checks it]),
    [New event consumer (for example analytics or audit)], [Its subscription: one element in the `consumers` set and one filter line in the messaging module, plus its dead-letter queue name on the dashboard. Its worker: new code in the consuming service. Zero producer files], [Its own SQS queue and dead-letter queue (settings in §3.2.3), a queue policy that accepts only the domain topic over TLS, and a filtered subscription], [`terraform test` asserts every subscription's filter; the readiness script checks routing before the relay starts],
    [New service], [One entry in the services map (19–37 lines), one ECR repository name, one entry in the image workflow's service list and the start-up script, its database grants, and a thin assembly module registered in `backend/pom.xml`], [Everything the golden-path `ecs-service` module creates (below), plus the shared COLA modules], [ArchUnit rules, image scan, Terraform plan with Checkov],
    [New or changed public operation], [A controller in the shared adapter or bootstrap module (both part of the exported application), owned by one service; the committed OpenAPI contract is regenerated], [The generated TypeScript client in the web application], [`contract-check` fails on drift and on incompatible changes; the client generation breaks the front-end type check if an operation disappears],
  ),
  kind: table,
  caption: [Extension seams and their guards (configured in the repository on 8 November 2026).],
) <tbl-seams>

*A new consumer leaves the producer untouched.* `commerce-svc` publishes every integration event to one SNS topic and knows no queue (`SnsOutboxEventTransport` addresses only the topic). A consumer's subscription is therefore one `for_each` element: the audit subscription is the set element `"audit"` plus a filter on event types that start with `Team`. The audit queue was created with the notification queue in the same pull request, #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/487")[\#487] (14 October), and it has no worker yet. It shows the property by construction, not as a later addition measured against a before state. On 17 October on AWS, the readiness check of the promotion confirmed that a `TeamSucceeded` test event reached the audit queue and that a `PaymentSucceeded` event did not. #lim-src("infra/swe5001/modules/messaging/main.tf")[Messaging module] · #lim-src("infra/swe5001/scripts/messaging-check.sh")[readiness check] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[promotion run 39008208244]. #pending[Eric: §3.1.1 (AD-04) and §3.1.4.6 also describe this reuse in full, and §3.1.4.6 says the queue was "added" and "verified on AWS". Keep one home (this section) and reference it from there; §3.1.4.6 also says 23 operations and Orval, but the contract has 31 operations in 27 paths and the client generator is hey-api (`@hey-api/openapi-ts`).]

*A new service inherits its runtime from one module.* All three services are instances of the golden-path module `infra/swe5001/modules/ecs-service` (454 lines), created by two `for_each` calls. From it and the runtime stack around it, every service gets:

- a log group, a task role under a permissions boundary, and an ARM64 Fargate task definition with a read-only root file system and a writable `/tmp`;
- an image pinned by digest (the module rejects a tag), with secrets injected from SSM Parameter Store as references (AD-17);
- an ALB target group and a listener rule that forwards only requests carrying the CloudFront origin secret (AD-09);
- an ECS service with the deployment circuit breaker and automatic rollback, zone rebalancing, and the regular-Fargate and Fargate Spot mix (AD-08);
- a series in the services row of the CloudWatch dashboard, which the observability module adds for every service's target group.

Two things are deliberately outside the module: the auto-scaling policy, because each service scales on a different signal (requests per target for `commerce-svc`, queue backlog for `notification-svc`), and the alarms, which belong to the messaging module. #lim-src("infra/swe5001/modules/ecs-service/main.tf")[`ecs-service` module] · #lim-src("infra/swe5001/runtime/services.tf")[services map].

*The shared code comes from the COLA modules, not from a starter library.* The proposal planned a `blocbuy-platform-starter` that would package the response envelope, authentication, outbox and idempotent consumption. What was built is thinner. `blocbuy-platform` has three classes in 93 lines (`PlatformAutoConfiguration`, `TopologyProperties` and `TraceIdPropagationInterceptor`): they stop a service assembly from starting unless the topology is `distributed`, and propagate the `X-Trace-Id` header on outbound calls. The module also carries the per-service database grants as a SQL resource. The rest is inherited from the shared COLA modules through `blocbuy-bootstrap`: the `Result` envelope and `ErrorCode` enums, Sa-Token roles, `IntegrationOutboxWriter` and the relay, the consumer ledger (`sys_consumed_message`) and `LeasedJobRunner` (AD-16). As a result, each service assembly is small: `identity-svc` and `notification-svc` each have 26 lines of main Java and a 20-line configuration file, and `commerce-svc` has 330 lines, mostly its remote identity adapter. #lim-src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008] · #lim-src("backend/blocbuy-platform/src/main/java/com/blocbuy/platform/PlatformAutoConfiguration.java")[`PlatformAutoConfiguration`].

*Contracts evolve without a joint release.* The public API is one OpenAPI 3.1 contract (`contracts/openapi.json`, 31 operations in 27 paths, 8 November 2026). In the `contract-check` job, which gates the required `docker` check, CI regenerates the contract from the built application and fails the pull request if it differs from the committed copy. The job also runs `openapi-diff` against the base branch and fails on an incompatible change unless the pull request carries the `contract-change-approved` label. Events follow the same rule: the envelope carries `eventType` and `version`, a breaking change is a new version published alongside the old one, and a consumer acknowledges a type or version it does not know without acting on it. #lim-src(".github/workflows/ci-backend.yml")[`ci-backend.yml`] · #lim-src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007].

*What a new use case gains (Q3).* A new use case built on the platform starts with sign-in and roles, the error format, trace propagation, the outbox and duplicate-protection code (switched on per service, with its own publish or receive permission), a runtime with rollback, a dashboard series and a guarded API contract. Built from scratch, each of these would be designed, built and secured again. The evidence for this is the size of the edits above. Not measured: the team did not time the addition of a fourth service, so the effort saved is shown by construction, not as a measured number.

=== Maintainability

#figure(
  table(
    columns: (1.5fr, 2.6fr),
    table.header([Measure (8 November 2026)], [Value]),
    [Codebase], [One Maven reactor with 10 modules: six COLA layers, `blocbuy-platform` and three service assemblies; about 20,800 lines of main Java],
    [Executable boundaries], [17 ArchUnit rules (8 layer and style, 9 service boundary) and 2 contract and SQL guards (AD-18)],
    [Tests], [248 test classes with about 1,581 test methods; parameterised tests counted once],
    [Quality gate], [SonarCloud on every backend pull request, at least 80% coverage on new code, fed by the JaCoCo reports of the aggregate and all three services],
    [Topologies from one commit], [`blocbuy.topology=monolith` (default; the SWE5006 deployment) or `distributed` (ECS only): same commit and migrations, one image per assembly],
  ),
  kind: table,
  caption: [Maintainability measures, counted in the repository. Sources: #lim-src("backend/pom.xml")[`backend/pom.xml`]\; #lim-src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java")[`ArchitectureTest`].],
) <tbl-maintainability>

The trade-off of these executable boundaries, including the duties they place on every change, is stated in AD-18 (§3.3).

*Evolution paths.* Three likely next changes reuse the seams above:

- *Extract payment* (AISS-02): the outbox already publishes payment facts, so payment can move behind its own assembly, with a saga for fulfilment and refund replacing today's shared settlement transaction.
- *Add a seed* (a new group-buying vertical, §6.2): a new service from the map entry and an assembly module, reusing identity, notification and settlement.
- *Add analytics* (§6.5): a read-only consumer on the event topic, built exactly like the audit queue.
