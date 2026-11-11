// Extensibility and Maintainability: part of 04-quality-attributes.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Extensibility and Maintainability

// Section 4.4 (owner: Lim). Links pinned to dev at b80debcd (8 November 2026).
#let lim-src(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

The platform is extended at three seams: a new event consumer, a new service and a new public operation. Each is a small, bounded edit that the pipeline checks. The platform stays maintainable because one codebase, with boundaries that fail the build when crossed (AD-18), produces both the monolith and the three services. This section answers briefing Q2 (common services and reusable frameworks) and Q3 (the benefit of building a new use case on the platform). The platform's producers and consumers are described in §3.1.4.6.

=== Three extension seams

#figure(
  table(
    columns: (0.9fr, 1.6fr, 2.1fr, 1.6fr),
    table.header([Change], [What a developer edits], [What comes with it], [What checks it]),
    [New event consumer (for example analytics or audit)], [Its subscription: one element in the `consumers` set and one filter line in the messaging module, plus its dead-letter queue name on the dashboard. Its checks: the consumer count and filter assertions in `runtime/tests/messaging.tftest.hcl` and a routing probe in `scripts/messaging-check.sh`. Its worker: new code in the consuming service. Zero producer files], [Its own SQS queue and dead-letter queue (settings in §3.2.3), a queue policy that accepts only the domain topic over TLS, and a filtered subscription], [Today `terraform test` expects exactly two consumers and asserts the filters of `notification` and `audit`, and the readiness script probes routing to those two queues before the relay starts\; a new consumer fails the test until it is added to both],
    [New service], [One entry in the services map (19–37 lines), one ECR repository name, one entry in the image workflow's service list and the start-up script, its database grants, and a thin assembly module registered in `backend/pom.xml`], [Everything the golden-path `ecs-service` module creates (below), plus the shared COLA modules], [ArchUnit rules, image scan, Terraform plan with Checkov],
    [New or changed public operation], [A controller in the shared adapter module (`blocbuy-adapter`, part of the exported application), owned by one service; the committed OpenAPI contract is regenerated], [The generated TypeScript client in the web application], [`contract-check` fails on drift and on incompatible changes; the client generation breaks the front-end type check if an operation disappears],
  ),
  kind: table,
  caption: [Extension seams and their guards (configured in the repository on 8 November 2026).],
) <tbl-seams>

*Principles behind the seams.* Each seam applies a named principle or pattern that an artefact enforces (@tbl-seam-principles).

#let lim-at(line) = "backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java#L" + str(line)
#figure(
  table(
    columns: (0.75fr, 2.1fr, 1.95fr),
    table.header([Seam], [Principle or pattern], [Enforcing artefact]),
    [New event consumer], [Open-Closed Principle at architecture level: new subscribers extend the bus, and the producer `commerce-svc` stays closed for modification. Publish–subscribe across services (SNS fan-out to SQS); Observer in process: the publisher does not know its `@TransactionalEventListener` subscribers, such as #lim-src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/integration/outbox/IntegrationOutboxWriter.java")[`IntegrationOutboxWriter`]], [`SnsOutboxEventTransport` holds only the topic ARN; `terraform test` asserts each subscription filter],
    [Layers in every service], [Dependency Inversion Principle: domain and application depend on port interfaces declared in the domain, which infrastructure implements (`OutboxEventTransport` by `SnsOutboxEventTransport`)], [ArchUnit rules #lim-src(lim-at(67))[`domain_is_framework_free`], #lim-src(lim-at(91))[`application_uses_ports_not_tech`] and #lim-src(lim-at(132))[`domain_has_no_outward_dependencies`] (all rules: AD-18)],
    [Coupling between services], [One synchronous call, `commerce-svc` → `identity-svc`, through the `UserAppService` port, implemented remotely by `RemoteUserAppService` (AD-06); everything else is events], [#lim-src(lim-at(247))[`commerce_reaches_identity_only_through_user_port`]],
    [Cohesion of each service], [Each service's persistence code uses only its own mappers. An assembly holds only service-specific code: only its main class in `identity-svc` and `notification-svc`\; in `commerce-svc` also the three classes of the remote identity client], [#lim-src(lim-at(293))[`*_persistence_is_owned`] (three rules); per-service database grants],
  ),
  kind: table,
  caption: [Seam, principle or pattern, and enforcing artefact (code at b80debcd, 8 November 2026).],
) <tbl-seam-principles>

*One liability.* The third seam breaks the Open-Closed Principle. All 14 controllers live in `backend/blocbuy-adapter`, which `blocbuy-bootstrap` pulls into all three images, so a new operation for one service modifies this shared module and changes every image. Only ownership by convention and the contract check bound it\; separate source trees were rejected for now (AD-18, option b).

*A new consumer leaves the producer untouched.* `commerce-svc` publishes every integration event to one SNS topic and knows no queue (`SnsOutboxEventTransport` addresses only the topic). A consumer's subscription is therefore one `for_each` element: the audit subscription is the set element `"audit"` plus a filter on event types that start with `Team`. The audit queue was created with the notification queue in the same pull request, #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/487")[\#487] (14 October), and it has no worker yet. It shows the property by construction, not as a later addition measured against a before state. On 17 October on AWS, the readiness check of the promotion confirmed that a `TeamSucceeded` test event reached the audit queue and that a `PaymentSucceeded` event did not. #lim-src("infra/swe5001/modules/messaging/main.tf")[Messaging module] · #lim-src("infra/swe5001/scripts/messaging-check.sh")[readiness check] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[promotion run 39008208244].

*A new service inherits its runtime from one module.* All three services are instances of the golden-path module `infra/swe5001/modules/ecs-service` (454 lines), created by two `for_each` calls. From it and the runtime stack around it, every service gets:

- a log group, a task role under a permissions boundary, and an ARM64 Fargate task definition with a read-only root file system and a writable `/tmp`;
- an image pinned by digest (the module rejects a tag), with secrets injected from SSM Parameter Store as references (AD-17);
- an ALB target group and a listener rule that forwards only requests carrying the CloudFront origin secret (AD-09);
- an ECS service with the deployment circuit breaker and automatic rollback (configured, not exercised: V6, §2.2), zone rebalancing, and the regular-Fargate and Fargate Spot mix (AD-08);
- a series in the services row of the CloudWatch dashboard, which the observability module adds for every service's target group.

Two things are deliberately outside the module: the auto-scaling policy, because each service scales on a different signal (requests per target for `commerce-svc`, queue backlog for `notification-svc`), and the alarms, which belong to the messaging module. #lim-src("infra/swe5001/modules/ecs-service/main.tf")[`ecs-service` module] · #lim-src("infra/swe5001/runtime/services.tf")[services map].

*The shared code comes from the COLA modules, not from a starter library.* The proposal planned a `blocbuy-platform-starter` that would package the response envelope, authentication, outbox and idempotent consumption. What was built is thinner. `blocbuy-platform` has three classes in 93 lines (`PlatformAutoConfiguration`, `TopologyProperties` and `TraceIdPropagationInterceptor`): they stop a service assembly from starting unless the topology is `distributed`, and propagate the `X-Trace-Id` header on outbound calls. The module also carries the per-service database grants as a SQL resource. The rest is inherited from the shared COLA modules through `blocbuy-bootstrap`: the `Result` envelope and `ErrorCode` enums, Sa-Token roles, `IntegrationOutboxWriter` and the relay, the consumer ledger (`sys_consumed_message`) and `LeasedJobRunner` (AD-16). As a result, each service assembly is small: `identity-svc` and `notification-svc` each have 26 lines of main Java and a 20-line configuration file, and `commerce-svc` has 330 lines, mostly its remote identity adapter. #lim-src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008] · #lim-src("backend/blocbuy-platform/src/main/java/com/blocbuy/platform/PlatformAutoConfiguration.java")[`PlatformAutoConfiguration`].

*Contracts evolve without a joint release.* The public API is one OpenAPI 3.1 contract (`contracts/openapi.json`, 31 operations in 27 paths, 8 November 2026). In the `contract-check` job, which gates the required `docker` check, CI regenerates the contract from the built application and fails the pull request if it differs from the committed copy. The job also runs `openapi-diff` against the base branch and fails on an incompatible change unless the pull request carries the `contract-change-approved` label. Events follow the same rule: the envelope carries `eventType` and `version`, a breaking change is a new version published alongside the old one, and a consumer acknowledges a type or version it does not know without acting on it. #lim-src(".github/workflows/ci-backend.yml")[`ci-backend.yml`] · #lim-src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007].

*What a new use case gains (Q3).* A new use case built on the platform starts with sign-in and roles, the error format, trace propagation, the outbox and duplicate-protection code (switched on per service, with its own publish or receive permission), a runtime with rollback (configured, not exercised: V6, §2.2), a dashboard series and a guarded API contract. Built from scratch, each of these would be designed, built and secured again. The evidence for this is the size of the edits above. Not measured: the team did not time the addition of a fourth service, so the effort saved is shown by construction, not as a measured number.

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
