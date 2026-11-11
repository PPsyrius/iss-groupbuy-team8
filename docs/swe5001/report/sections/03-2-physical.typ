// Physical Architecture & Design: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "../figures.typ": afig
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d6-security-groups.typ": d6-sg-table

// AD and AISS numbers come from ../registers.md. Every AD row uses the template shared
// with §3.1.1 and §3.3: Driver, Context, Options considered, Choice, Trade-off, Consequence, Record.
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  table.header([Identifier], [Description]),
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

#let subnet-record = "docs/swe5001/evidence/20261110-subnet-verification/README.md"

== Physical Architecture & Design

The SWE5001 deployment configuration targets AWS in Singapore (`ap-southeast-1`): three containers on ECS Fargate behind CloudFront and one Application Load Balancer (ALB), with one PostgreSQL server, one Valkey node and one domain-event SNS topic with SQS queues. Only the ALB and the NAT instance sit in public subnets; ECS uses private application subnets. Separate data subnets with no route out of the virtual private cloud (VPC) hold the data stores; their placement was verified on AWS on 10 November (AD-15, @fig-d6-network). Runtime is disposable; the permanent edge and foundation stacks remain between sessions (AD-12).

@tbl-logical-physical maps each logical element of @tbl-logical-elements (§3.1.3) to the product that implements it. The last three rows have no logical counterpart: they exist only because of where the system runs.

#figure(
  table(
    columns: (1fr, 0.9fr, 1.6fr),
    table.header([Logical element (§3.1.3)], [Product], [Configuration]),
    [Web client], [React single-page app; files in Amazon S3], [TypeScript client generated from `contracts/openapi.json`; private bucket readable only by CloudFront (origin access control)],
    [CDN and edge gateway (edge)], [Amazon CloudFront], [HTTPS for viewers on the default `*.cloudfront.net` certificate; `/api/*` forwarded uncached to the ALB, everything else served from S3 and cached],
    [CDN and edge gateway (routing)], [Application Load Balancer], [Listener on HTTP port 80, reachable only from CloudFront's origin-facing addresses and only with the `X-Origin-Secret` header; path rules choose the service],
    [`identity-svc`], [ECS Fargate service (Spring Boot 4, Java 21)], [ARM64, 0.5 vCPU, 1 GiB; one task],
    [`commerce-svc`], [ECS Fargate service (Spring Boot 4, Java 21)], [ARM64, 0.5 vCPU, 1 GiB; 2 to 6 tasks (1 to 6 in baseline sessions), scaled on ALB requests per target (AD-20)],
    [`notification-svc`], [ECS Fargate service (Spring Boot 4, Java 21)], [ARM64, 0.5 vCPU, 1 GiB; 1 to 4 tasks, scaled on queue backlog (AD-20)],
    [AuthAPI, CommerceAPI, NotificationAPI], [REST over HTTPS under `/api/v1`], [One contract, `contracts/openapi.json`],
    [PaymentWebhookAPI], [`StripeWebhookController` (`/api/v1/webhooks/stripe`)], [Reached through CloudFront and the ALB; the logical view draws the provider calling the interface directly],
    [CheckoutAPI], [Stripe Checkout Sessions and Refunds API, test mode], [`CheckoutGateway` and `RefundGateway`, implemented by `StripeCheckoutGateway`; called through fck-nat],
    [OIDC sign-in], [Google OAuth 2.0 and OpenID Connect], [`OAuthIdentityGateway`, implemented by `GoogleOAuthGateway`; called through fck-nat],
    [`UserAppService` (the one synchronous call)], [ECS Service Connect], [Commerce reaches identity by service name inside the VPC: `RemoteUserAppService` calls `InternalUserController` under `/internal/users` over HTTP (for example `/internal/users/{id}/active`)],
    [PublishedEvents V1], [`IntegrationEventEnvelope` JSON in `sys_outbox`], [`TeamSucceededV1`, `TeamExpiredV1` and `PaymentSucceededV1`; `OutboxRelayService` publishes them through `SnsOutboxEventTransport` (AD-04)],
    [Message broker], [Amazon SNS], [Topic `blocbuy-swe5001-domain-events`],
    [Event queue (notification)], [Amazon SQS], [Queue `blocbuy-swe5001-notification` with the dead-letter queue (DLQ) `blocbuy-swe5001-notification-dlq`; read by `SqsNotificationEventConsumer`],
    [Event queue (audit)], [Amazon SQS], [Queue `blocbuy-swe5001-audit` with a DLQ; the subscription takes event types that start with `Team`; no consumer (it demonstrates fan-out, ADR 0007)],
    [Identity, commerce and notification data stores], [Amazon RDS for PostgreSQL 18], [`db.t4g.micro`, 20 GiB gp3, one zone; three databases (`identity`, `commerce`, `notification`) on one server],
    [Session store], [Amazon ElastiCache for Valkey 8.0], [`cache.t4g.micro`, one node, TLS and an AUTH token; Sa-Token sessions and the role snapshot, read through `SessionStpInterface`],
    [Identity provider], [Google Sign-In (external)], [Called from identity through fck-nat],
    [Payment provider], [Stripe, test mode (external)], [Called from commerce through fck-nat; Stripe's webhook arrives through CloudFront],
    [— (outbound gateway)], [fck-nat on EC2 `t4g.nano`], [One instance with a static network interface and an Elastic IP],
    [— (configuration and secrets)], [SSM Parameter Store], [Under `/blocbuy/swe5001/`; read by the task execution role at start-up],
    [— (monitoring)], [Amazon CloudWatch], [Logs under `/blocbuy/swe5001/*` (7 days), one dashboard, two alarms on the notification queue (DLQ not empty, oldest-message age)],
  ),
  kind: table,
  caption: [Logical elements of @fig-d3-logical-view and @fig-d3b-logical-events-data and the products that implement them. Source: `infra/swe5001` and `backend` at the freeze commit (§1.1).],
) <tbl-logical-physical>

*Deployment view.* Two UML deployment diagrams show the physical layout. @fig-d5-deployment places each artefact in its region, VPC, availability zone and subnet; @fig-d5a-communication-paths shows the paths between the nodes.

#afig("d5-physical-deployment", lbl: <fig-d5-deployment>, [Physical deployment view (UML deployment diagram). Each availability zone holds a public, a private application and a data subnet; ECS spreads the three services' tasks over both private subnets, while RDS and Valkey each run in one data subnet that AWS chooses.])

#afig("d5a-communication-paths", [Communication path view (UML deployment diagram). Only CloudFront reaches the load balancer, every outbound call to Stripe, Google, SNS and SQS leaves through fck-nat, and a path drawn to the private application subnets applies to all three services.])

The paths in @fig-d5a-communication-paths work as follows:

- *Browser traffic* reaches CloudFront over HTTPS. CloudFront serves the single-page application from S3 and forwards `/api/*` to the ALB. Stripe's webhook enters the same way.
- *The ALB* picks the service by path. Identity takes `/api/v1/auth/*` and `/api/v1/users/*`, notification takes `/api/v1/notifications*`, and commerce takes the rest of `/api/*`. A high-priority rule (priority 5; the ALB evaluates lower numbers first) returns 404 for `/internal/*` and `/api/internal/*` before any routing rule matches, so internal endpoints are only reachable inside the VPC.
- *Services* talk to each other only once, commerce → identity through Service Connect (AD-06). Everything else between services goes through events: commerce writes them to its outbox, a relay publishes them to SNS, and SNS delivers copies to two SQS queues (AD-04): `notification-svc` reads the notification queue, and the audit queue has no consumer (it demonstrates fan-out, ADR 0007).
- *Every task* reads its secrets (database, Valkey, OAuth and Stripe) from SSM Parameter Store when it starts; host, port and URL are plain environment variables. Database passwords never pass through Terraform state (§3.2.2).
- *All three services* read the shared login session from Valkey over TLS with an AUTH token (AD-05).
- *Outbound calls* leave through fck-nat: identity exchanges the Google sign-in code, commerce calls the Stripe API and publishes to SNS, and `notification-svc` long-polls SQS. The same path carries the tasks' other calls to AWS APIs; S3 traffic uses the gateway endpoint instead.

AWS chooses each data store's zone; on 10 November RDS ran in `ap-southeast-1b` and Valkey in `ap-southeast-1a`. The monolith fallback topology (`topology = monolith`) runs the SWE5006 image as one ECS service on `/api/*` in the same network; it is not drawn.

*Network and security groups.* @fig-d6-network draws the network in AWS group notation: every subnet with its CIDR, its route table and the security group of each resource.

#figure(kind: image, block(breakable: false, align(left, d6)), caption: [Network view (AWS architecture icons convention: VPC, availability zone and subnet groups, no service icons). The data subnets' route table holds only the local route, so the data tier has no path out of the VPC.]) <fig-d6-network>

The VPC (`10.50.0.0/16`) spans two availability zones and has three subnet tiers in each zone (@tbl-subnets):

#figure(
  table(
    columns: (auto, 1fr),
    table.header([Tier], [What runs there and how it reaches out]),
    [Public], [ALB nodes and the fck-nat instance. Default route to the internet gateway],
    [Private application], [ECS tasks and the one-off `db-init` task. Default route to the fck-nat network interface; free S3 gateway endpoint for image layers],
    [Data], [RDS and ElastiCache subnet groups only. Local VPC route only: no NAT, no internet gateway, no endpoint],
  ),
  kind: table,
  caption: [Subnet tiers and what runs in each; the CIDRs are in @fig-d6-network. Source: `infra/swe5001/modules/network`; the data tier was added on 4 November 2026 (AD-15).],
) <tbl-subnets>

*Separate data subnets (assessor remark F6).* Before the 4 November configuration change, the database, the cache and the services used the same eligible private application subnet set, separated only by security groups. AD-15 gives the data stores their own subnets and route table; the 10 November record verifies that layout for the recorded session. The configuration provides two independent barriers:

+ *Routing.* A data subnet has no route out of the VPC, so a compromised or misconfigured data store cannot reach the internet, even through the NAT instance.
+ *Security groups.* RDS accepts TCP 5432 only from the service group and the `db-init` group. Valkey accepts TCP 6379 only from the service group. Neither group allows outbound traffic. Full rules: @tbl-security-groups.

#d6-sg-table()

Tested locally, not on AWS: module validation rejects reused data CIDRs, and `terraform test` `data_tier_has_its_own_subnets` checks disjoint sets (19 of 19 runtime runs passed, 4 November). On 10 November on AWS, the #src(subnet-record)[dated record] retained the actual application and data ENIs, subnet groups, effective routes and security groups and required connectivity. This resolves AISS-07's placement and connectivity gap and updates NFR-70, without claiming negative packet tests or failover. Scheduled cleanup later removed that runtime; its subnet IDs are historical, not reusable live-state assertions.

*Single points in the network.* Each zone has its own private route table, but both point at the single fck-nat instance in zone a. An Auto Scaling group of one replaces a failed instance, but only in zone a, because the static network interface lives there. If zone a fails, the services in zone b lose outbound access (for example to Stripe and Google) until the zone recovers. This is a cost decision (AD-10) and is listed as AISS-01.

=== Key Architectural Decisions

The physical decisions buy isolation and a small bill rather than always-on capacity: managed containers on ARM (AD-07, AD-08), one guarded entry point (AD-09), single shared data and egress nodes (AD-10, AD-11), a runtime that exists only while used (AD-12), no Kubernetes (AD-13), a tag-fenced stack in the shared team account (AD-14), data subnets with no route out (AD-15) and scaling on requests per task (AD-20). Prices are on-demand list prices for `ap-southeast-1` from our cost estimate of 25 September 2026 (`04-aws-cost-estimate.md`), unless the row names another source.

#figure(
  ad-table(
    ("AD-07", [
      *ECS Fargate on ARM64 (Graviton), not EKS, App Runner or Lambda.* \
      *Driver:* Cost; scalability. \
      *Context:* Three long-running Spring Boot services that need per-service scaling and private networking. \
      *Options considered:* (a) EKS (Kubernetes), rejected for its control-plane fee and set-up time (AD-13); (b) App Runner, rejected because it runs no queue consumers or background workers; (c) Lambda, rejected because the services keep connection pools and run scheduled jobs; (d) ECS on Fargate. \
      *Choice:* (d), with ARM64 images. \
      *Trade-off:* No cluster to run or patch, and ARM64 is about 20% cheaper per vCPU-hour than x86 (estimated from the list prices); in exchange we do not practise Kubernetes and are tied to ECS task definitions. \
      *Consequence:* The image pipeline builds `linux/arm64` only (§5.2). \
      *Record:* No ADR; options study `03-k8s-argocd-assessment.md`.
    ]),
    ("AD-08", [
      *Fargate Spot above a regular-Fargate base.* \
      *Driver:* Cost; availability (a guaranteed floor). \
      *Context:* Commerce needs a guaranteed floor for availability but extra capacity only during load. \
      *Options considered:* (a) all on-demand, rejected because burst capacity would pay the full price; (b) all Spot, rejected because a reclaim could take commerce below its floor; (c) a mixed strategy. \
      *Choice:* (c): capacity provider strategy `FARGATE` with base equal to the commerce minimum (two in demonstration sessions) and weight 1, plus `FARGATE_SPOT` with weight 3. \
      *Trade-off:* Spot capacity is cheaper but can be reclaimed with two minutes' notice; the base keeps two tasks on regular Fargate, so a reclaim never takes commerce below its floor. \
      *Consequence:* Configured, not tested: the 16 and 19 October AWS records show the strategy on the service, but not which provider each task ran on. The cost study of 25 September noted Spot as documented for x86 only, so ARM64 Spot placement is not shown by our evidence. \
      *Record:* No ADR.
    ]),
    ("AD-09", [
      *CloudFront in front of an ALB restricted by prefix list and a secret header.* \
      *Driver:* Security (one guarded entry point). \
      *Context:* One HTTPS entry point for the website and the API, with the load balancer not usable directly. \
      *Options considered:* (a) a public ALB with its own certificate, rejected because it needs a custom domain and leaves the ALB open to direct calls; (b) API Gateway with a VPC link, rejected because it adds a hop and a per-request charge; (c) CloudFront → ALB. \
      *Choice:* (c): CloudFront → ALB on HTTP port 80; the ALB security group admits only CloudFront's origin-facing prefix list, and an ALB rule rejects requests without the `X-Origin-Secret` header. \
      *Trade-off:* One domain and no certificate to manage for the ALB; but the hop from CloudFront to the ALB is not encrypted (AISS-08) and the secret sits in Terraform state (AISS-09). \
      *Consequence:* Requests that bypass CloudFront are refused; there is no per-client rate limiting (no WAF). \
      *Record:* No ADR; options study `02-proposed-architecture.md`.
    ]),
    ("AD-10", [
      *One fck-nat instance, not a NAT Gateway.* \
      *Driver:* Cost. \
      *Context:* Private tasks need outbound access to Stripe, Google and AWS APIs. \
      *Options considered:* (a) a NAT Gateway per zone, rejected at twice the price of one; (b) one NAT Gateway, rejected at about US\$46.72 a month; (c) VPC interface endpoints, rejected because each costs money per zone and none reaches Stripe or Google; (d) a NAT instance. \
      *Choice:* (d): fck-nat on one `t4g.nano` with a static network interface; an Auto Scaling group of one replaces a failed instance. \
      *Trade-off:* Calculated: about US\$11 a month (about US\$4 for the `t4g.nano`, from the module's own estimate, plus two public IPv4 addresses at US\$0.005 an hour each) instead of about US\$46.72 for one NAT Gateway with its Elastic IP (US\$43.07 + US\$3.65); in exchange, egress depends on one instance in one zone (AISS-01). \
      *Consequence:* A free S3 gateway endpoint keeps image-layer pulls off the instance. \
      *Record:* No ADR; `04-aws-cost-estimate.md`.
    ]),
    ("AD-11", [
      *One RDS server holding three databases.* \
      *Driver:* Cost. \
      *Context:* Each service must own its data (AD-03), but three servers would triple the largest fixed cost. \
      *Options considered:* (a) one server per service, rejected because it triples that cost; (b) one shared schema, rejected because it breaks data ownership (AD-03); (c) one server with a database and two roles per service. \
      *Choice:* (c): one `db.t4g.micro` instance with databases `identity`, `commerce` and `notification`, each with an owner role for migrations and an app role for runtime. \
      *Trade-off:* Isolation by grants and connection strings at the cost of one server; the services share capacity and a failure takes all three down (AISS-05). \
      *Consequence:* Moving a database to its own server changes only the connection settings, not the code. \
      *Record:* No ADR; `04-aws-cost-estimate.md`.
    ]),
    ("AD-12", [
      *Permanent edge, disposable runtime.* \
      *Driver:* Cost. \
      *Context:* An always-on runtime would cost about US\$125 a month (estimated in the project proposal) for a system used a few hours a week. \
      *Options considered:* (a) always on, rejected for that cost; (b) scale to zero, rejected because the database, the cache, the ALB and the NAT instance still bill while idle; (c) destroy and rebuild. \
      *Choice:* (c): the `edge` stack (CloudFront, S3) is permanent; the `runtime` stack is created for each session or release and destroyed afterwards. \
      *Trade-off:* Estimated US\$0.17 to US\$0.20 an hour while up (`impl-aws-infrastructure.md` §9); but a full up job took 31 min 37 s on 17 October (@tbl-delivery-timings, AISS-06), and no data survives a session. \
      *Consequence:* Every release is also a rebuild-from-nothing test; backups are deliberately off. \
      *Record:* No ADR; project proposal.
    ]),
    ("AD-13", [
      *No Kubernetes and no Argo CD.* \
      *Driver:* Cost (money and effort). \
      *Context:* GitOps was one option for continuous delivery. \
      *Options considered:* (a) EKS + Argo CD, rejected for the reasons below; (b) ECS with Terraform and GitHub Actions. \
      *Choice:* (b). \
      *Trade-off:* The EKS control plane alone was estimated at about US\$73 a month, more than all our compute, and set-up at 8 to 12 person-days before any feature work (`03-k8s-argocd-assessment.md`); we give up the GitOps reconciliation loop. \
      *Consequence:* Deployment is a pipeline run (§5.3), not a controller. \
      *Record:* No ADR; `03-k8s-argocd-assessment.md`.
    ]),
    ("AD-14", [
      *Our own SWE5001 stack in the shared team account, fenced by tags.* \
      *Driver:* Security (isolation between the two environments). \
      *Context:* The SWE5006 environment and the SWE5001 runtime share our member account 402178232802 in an AWS Organization; neither must be able to damage the other. \
      *Options considered:* (a) a separate member account (`swe5001-poc`, since closed); (b) the shared account with no fence, rejected because either project could change the other's resources; (c) the shared account with the SWE5001 apply role fenced to its own resources. \
      *Choice:* (c). The SWE5001 apply role denies actions on resources without the SWE5001 tags or name prefix (#src("infra/swe5001/foundation/github-oidc.tf")[`github-oidc.tf`], "Guardrail for the shared account"); service control policies (SCPs) and Identity Center govern the whole account, and an account administrator cannot lift an SCP. \
      *Trade-off:* One account, one set of short-lived logins and one bill shared by both projects (§6.3); the fence is an IAM policy that an administrator can change, weaker than an account boundary. An SCP can also block legitimate calls, as a CloudFront metric read was on 16 October (§4.3, @tbl-findings). \
      *Consequence:* Access details are in §4.3. \
      *Record:* No ADR; `docs/deploy/aws-account-governance/README.md`.
    ]),
    ("AD-15", [
      *Separate data subnets for RDS and Valkey.* \
      *Driver:* Security (assessor remark F6). \
      *Context:* Assessor remark F6 (21 October): the database should be in its own subnet. \
      *Options considered:* (a) keep the shared private subnets and rely on security groups, rejected because it leaves one barrier; (b) add data subnets with their own route table. \
      *Choice:* (b): two data subnets, one per zone, with a route table that holds only the local VPC route. \
      *Trade-off:* No running cost and no code change; two more subnets and a route table to maintain, and a rebuild is needed to adopt it (free here, AD-12). \
      *Consequence:* Tested locally, not on AWS, on 4 November; verified on AWS on 10 November (§3.2, AISS-07). The recorded runtime was later destroyed. \
      *Record:* No ADR; \#589 and the #src(subnet-record)[10 November subnet record].
    ]),
    ("AD-20", [
      *Commerce scales on load-balancer requests per task.* \
      *Driver:* Scalability. \
      *Context:* `commerce-svc` takes every checkout and team request; on 16 October on AWS, one task passed every plateau up to 200 req/s (V2, §4.1). \
      *Options considered:* (a) target tracking on CPU, rejected because CPU lags a request burst; (b) step scaling on alarms, rejected because it needs tuned alarm thresholds; (c) target tracking on `ALBRequestCountPerTarget`. \
      *Choice:* (c), at 12,000 requests per target per minute (calculated: 200 req/s × 60 s), two to six tasks (one in baseline sessions), cooldowns of 60 s out and 180 s in. `notification-svc` tracks its queue backlog per task (target 50 messages, one to four tasks). \
      *Trade-off:* The request count follows the burst itself and needs no tuned thresholds; but the target comes from one baseline run, and one-minute metrics let a fast burst outrun it (AISS-12). An opt-in CPU step guard exists and is off by default (`commerce_cpu_scale_out_enabled`). \
      *Consequence:* The 12,000 value is the GitHub environment setting, and the Terraform fallback is 200. On 19 October on AWS, commerce scaled from one to three tasks, but no V3 campaign has passed acceptance (§4.1). \
      *Record:* No ADR; #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October policy record].
    ]),
  ),
  kind: table,
  caption: [Physical architectural decisions AD-07 to AD-15 and AD-20; numbering continues from §3.1.1. Source: `registers.md`.],
) <tbl-ad-physical>

=== Technology and Services

The solution uses managed AWS services wherever one exists, so we run no servers of our own apart from one NAT instance. Every choice favours the lowest cost that still shows the architecture, because the runtime exists only for a few hours a week (AD-12). @tbl-tech lists the versions and sizes; the rationale for the main choices is in the AD rows of §3.2.1 and is not repeated here.

#figure(
  table(
    columns: (0.8fr, 1.4fr, 2.2fr),
    table.header([Layer], [Technology and version], [Why this one, and what it costs us]),
    [Application runtime], [Java 21, Spring Boot 4.1.2, MyBatis; image `eclipse-temurin:21-jre-noble`, built for `linux/arm64`], [The SWE5006 code base, reused unchanged by the strangler migration (AD-01). One base image for all three services keeps patching in one place],
    [Container platform], [ECS on Fargate, ARM64; 0.5 vCPU and 1 GiB per task], [No hosts to manage (AD-07). The task size was kept small on purpose: on 19 October on AWS, one 0.5 vCPU commerce task saturated at about 280 req/s (§4.1), so the service scales out instead of up. The 11 November load sessions (V3 r2 and r3) ran a larger, session-only configuration; §4.1 reports it, and the configured size is unchanged],
    [Image registry], [Amazon ECR, one repository per service plus `swe5001/monolith`; immutable tags, scan on push, lifecycle policy], [Immutable tags make a release digest refer to exactly one image; Trivy in the pipeline is the blocking scan (§5.2). Configured, not tested: ECR also scans each pushed image; its findings are not part of the gate],
    [Edge], [Amazon CloudFront (`PriceClass_200`, HTTP/2) and a private S3 bucket], [HTTPS without a custom domain or certificate; the website and the API share one origin, so the browser needs no cross-origin settings (AD-09). The price class excludes the most expensive edge locations],
    [Load balancing], [Application Load Balancer, HTTP :80, path rules], [Path-based routing to three services without a separate API gateway product; API Gateway was rejected because it adds a hop and a per-request charge],
    [Service discovery], [ECS Service Connect], [Commerce calls identity by name inside the VPC with no load balancer or DNS records to manage],
    [Relational database], [Amazon RDS for PostgreSQL 18, `db.t4g.micro`, 20 GiB gp3], [PostgreSQL 18 generates UUIDv7 keys natively (§3.2.3). The smallest Graviton class is enough for the demonstration; the 11 November load sessions used a larger class (§4.1). The single database server is the next scaling limit (AISS-05)],
    [Session store], [Amazon ElastiCache for Valkey 8.0, `cache.t4g.micro`, one node], [Redis-compatible, so Sa-Token and Spring Data Redis work unchanged; Valkey costs less than Redis OSS on ElastiCache. One node is a cost choice (AISS-01, AISS-10)],
    [Messaging], [Amazon SNS (one domain-event topic, plus an alarm topic) and SQS (standard queues with DLQs)], [Fan-out to several consumers with no broker to run (AD-04); standard queues give at-least-once delivery, which the consumers handle with `sys_consumed_message`],
    [Outbound network], [fck-nat on `t4g.nano`], [About a quarter of a NAT Gateway's cost (AD-10)],
    [Configuration and secrets], [SSM Parameter Store, SecureString with the AWS-managed key], [Free at this volume; tasks read values at start-up through their execution role. Secrets Manager was not needed, because a rebuild rotates every secret except the origin secret (AISS-09)],
    [Monitoring], [CloudWatch Logs (7-day retention), one dashboard, alarms to an SNS alert topic], [Managed and cheap at demonstration volume; logs are kept only as long as a sprint review needs them],
    [Infrastructure as code], [Terraform 1.16, AWS provider 5.100; three stacks: `foundation`, `edge` and `runtime`], [The SWE5006 environment already used Terraform; `terraform test` runs offline in CI (§5.2). Stack split by lifetime: `foundation` and `edge` are permanent, `runtime` is disposable],
  ),
  kind: table,
  caption: [Technology and services with versions and sizes. Source: `backend/pom.xml`, `backend/Dockerfile` and `infra/swe5001`, checked on 11 November 2026.],
) <tbl-tech>

*Encryption and secrets.* Configured, not tested: the data stores, the domain-event topic (`alias/aws/sns`), the queues, ECR and the state bucket are encrypted at rest; the alarm topic `blocbuy-swe5001-alerts` is not. The CloudFront → ALB hop is plain HTTP (AISS-08), and the Valkey AUTH token and the origin secret are in Terraform state (AISS-09). Database passwords are generated as ephemeral values and written with write-only arguments, so they never reach Terraform state. @tbl-security-boundaries gives each boundary, its control and its limit (§4.3).

*Cost.* Estimated: while it is up, the runtime costs about US\$0.17 to US\$0.20 an hour (AD-12), and nothing for the rest of the week except the permanent edge. Estimated from Cost Explorer: every session billed to 10 November cost about US\$11.6 in total (read on 11 and 12 November; the 11 November session is not yet billed). §6.3 shows the method.

*Workloads and compute.* @tbl-workload-compute shows which compute runs each kind of workload and what scales it.

#figure(
  table(
    columns: (auto, 1fr, 1fr, 1fr, 1fr),
    table.header([Workload], [Example], [Runtime], [Scaling signal], [Why]),
    [Online API], [Checkout, team pages, sign-in], [One ECS Fargate service per context group (`identity-svc`, `commerce-svc`, `notification-svc`)], [`commerce-svc`: ALB requests per target, two to six tasks (AD-20); `identity-svc`: one task, not scaled], [Long-lived connection pools and a shared session (AD-07)],
    [Event consumer], [Notification from `TeamSucceeded`], [`notification-svc` on Fargate], [Notification queue backlog per task, target 50, one to four tasks (AD-20)], [Long polling needs a running worker; Lambda and App Runner rejected (AD-07)],
    [Scheduled job], [Team and purchase-intent expiry, refunds, outbox relay and retention], [In-process in `commerce-svc`, under `LeasedJobRunner`], [None: one lease holder runs each job], [The job stays with the data it changes (AD-16)],
    [Heavy batch (not built)], [Settlement reconciliation, payout aggregation], [ECS scheduled task or AWS Batch reading the audit queue], [Schedule or queue depth], [Designed but not implemented: keeps long jobs off the checkout tasks],
  ),
  kind: table,
  caption: [Workloads and the compute that runs them. Source: `infra/swe5001/runtime`, AD-07, AD-16, \#741.],
) <tbl-workload-compute>

=== Persistence Design

All persistent business data lives in PostgreSQL. Each service has its own database on the shared server and can read and write only the tables it owns. Sessions live in Valkey and are lost if the node restarts. Events live in the commerce outbox table until they are published, then in SQS until a consumer has applied them. Nothing is backed up, because every session starts from seed data (AD-12).

In code, every aggregate reaches its tables the same way: a repository interface in `blocbuy-domain`, implemented by a `…RepositoryImpl` in `blocbuy-infrastructure` that calls a MyBatis mapper and maps its data objects (`…DO`) to the domain class, usually through a converter. For the team: `PaidGroupDealRepository`, `PaidGroupDealRepositoryImpl`, `PaidGroupDealMapper`, `BizGroupDealDO` and `GroupDealConverter`.

==== Data stores and table ownership

Every service database runs the same Flyway migration stream (`V1` to `V28` at the freeze commit, §1.1), so each database contains all 29 tables. Ownership is enforced by grants, not by which tables exist: after every Flyway run (each service start), `afterMigrate__service_grants.sql` gives the service's app role `SELECT, INSERT, UPDATE, DELETE` on the tables it owns and revokes everything on the others (@tbl-ownership). The owner role runs migrations; the app role used at runtime cannot change the schema.

#figure(
  table(
    columns: (auto, auto, 1fr),
    table.header([Database], [Owned tables], [Tables]),
    [`identity`], [6], [`sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`],
    [`notification`], [2], [`biz_notification`, `sys_consumed_message`],
    [`commerce`], [21], [Catalogue: `biz_product`, `biz_team_price_policy`, `biz_team_price_quote`, `log_product_team_terms`. Team purchase: `biz_group_deal`, `biz_group_member`, `biz_group_participation_receipt`, `biz_share_invite`, `biz_share_credit`. Order and payment: `biz_purchase_intent`, `biz_payment`, `biz_order`, `biz_refund`, `log_refund_attempt`, `biz_payment_review_case`, `log_payment_review`, `log_payment_webhook_event`. Technical: `sys_outbox`, `sys_scheduler_lock`, `sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`],
  ),
  kind: table,
  caption: [Table ownership per database; commerce owns every table not listed for identity or notification. Source: `afterMigrate__service_grants.sql`.],
) <tbl-ownership>

The trade-off: one migration stream is simple to build and test, and the grants stop a service from using another's tables, which architecture tests also check. The cost is that unused, empty tables exist in every database, and a migration for one service runs in all three.

==== Conventions

The schema follows our PostgreSQL conventions (`pgsql-ddl` skill):

- *Keys.* Native `uuid` primary keys generated as UUIDv7 (`DEFAULT uuidv7()`, PostgreSQL 18). UUIDv7 is time-ordered, so new rows append to the index instead of splitting pages, and the database assigns it at insert, so the outbox row ID serves as the event ID. One exception: `sys_consumed_message` has the composite key (`event_id`, `consumer`).
- *Prefixes.* `sys_` for platform and technical tables, `biz_` for business entities, `rel_` for many-to-many links, `log_` for append-only records.
- *Audit and soft delete.* Ordinary business tables carry `create_time`, `create_by`, `update_time`, `update_by` and `deleted`; most mutable aggregates also carry `version` for optimistic locking (exceptions under the figures). Active-row uniqueness rules use partial indexes `WHERE deleted = false`, so a soft-deleted row does not block a new one; rules that must hold for every row ever written (one member, order, refund or review case per payment; one webhook event; one notification per event and recipient) use plain unique indexes.
- *No foreign keys or CHECK constraints.* Relationships are logical, kept by the application inside its transactions. This lets a table move to another service's database without unpicking constraints, and keeps validation in one place (the domain model). The cost is that the database does not catch an orphaned row; the unique indexes below catch the errors that matter most.

==== Logical data models

@fig-d9a-identity-data and @fig-d9b-notification-data show the two smaller databases. The commerce database owns 21 tables, so its model is split in two: catalogue and team purchase (@fig-d9c-commerce-team-data), and purchase intent, payment, order, refund and the technical tables (@fig-d9d-commerce-payment-data; their patterns are in @tbl-technical). A START intent reserves its `deal_id` before the team row exists, so the team it refers to may not exist yet.

Every table also carries the audit columns `create_by`, `create_time`, `update_by`, `update_time` and `deleted`, which the figures omit. The exceptions: `rel_user_role` and `rel_role_permission` have no `update_*` columns; the append-only `log_` tables have only `create_time` (`log_product_team_terms` also `create_by`); `biz_payment_review_case` has only `create_time`; `sys_outbox` has only `created_at`, and `sys_consumed_message` only `consumed_at`. A `version` column, drawn where it exists, is on `biz_product`, `biz_team_price_policy` (where it is also the revision number), `biz_group_deal`, `biz_purchase_intent`, `biz_payment`, `biz_order`, `biz_refund`, `sys_scheduler_lock` and `sys_scheduled_item_retry`; `sys_user`, `biz_group_member` and `biz_payment_review_case` have none.

#afig("d9a-identity-data", [Identity data view (UML class diagram, «table» stereotype; {partial UK} = unique among rows with `deleted = false`). Each Google identity, role grant and permission grant refers to its owner by an ID column only.])

#afig("d9b-notification-data", [Notification data view (UML class diagram, «table» stereotype). The consumed-message row and the notifications for one event are written in one transaction; `uq_biz_notification_event_user` keeps one notification per event and recipient, even after a soft delete.])

#afig("d9c-commerce-team-data", [Commerce data view, catalogue and team purchase (UML class diagram, «table» stereotype; {partial UK} = unique among rows with `deleted = false`). Every link is a logical ID column, because the schema has no foreign keys; `payment_id` refers to `biz_payment` in @fig-d9d-commerce-payment-data.])

#afig("d9d-commerce-payment-data", [Commerce data view, purchase intent, payment, order, refund and technical tables (UML class diagram, «table» stereotype; {partial UK} = unique among rows with `deleted = false`). A payment has at most one order, one refund and one review case, enforced by plain unique indexes on `payment_id`.])

==== Indexes that protect invariants

Several business rules are enforced by unique indexes, so that a race between two transactions ends in a constraint violation instead of a wrong state (@tbl-invariant-indexes):

#figure(
  table(
    columns: (auto, auto, 1fr),
    table.header([Index], [Partial on `deleted`], [Rule it protects]),
    [`uq_biz_group_member_deal_user` (`deal_id, user_id`)], [Yes], [A buyer joins a team at most once],
    [`uq_biz_group_member_payment_id`], [No], [One payment admits at most one member],
    [`uq_biz_purchase_intent_fulfilled_payment`], [Yes], [One payment fulfils at most one purchase intent, and so produces at most one order (ADR 0001)],
    [`uq_biz_payment_pending_intent` (also `status = 'created'`)], [Yes], [At most one open payment per purchase intent],
    [`uq_biz_payment_request_key` (`user_id, request_key`)], [Yes], [A retried checkout request does not create a second payment],
    [`uq_biz_order_payment_id`, `uq_biz_order_deal_user`], [No; yes], [One order per payment, and one order per buyer per team],
    [`biz_refund.payment_id` (unique)], [No], [A payment is refunded at most once],
    [`uq_log_payment_webhook_event_event_id`], [No], [A Stripe webhook event is processed at most once],
    [`uq_sys_scheduler_lock_key`], [Yes], [A scheduled job has at most one lease holder],
    [`pk_sys_consumed_message` (`event_id, consumer`)], [No], [A consumer applies an event at most once],
    [`uq_biz_notification_event_user` (`event_id, user_id`)], [No], [A notification is created at most once per event and recipient, even after a soft delete],
  ),
  kind: table,
  caption: [Unique indexes that enforce invariants. Source: Flyway migrations V2–V28.],
) <tbl-invariant-indexes>

==== Aggregates and their tables

@tbl-aggregate-tables maps the twelve aggregates of @tbl-aggregates (§3.1.4), eleven roots with a domain class and the Refund aggregate, which has none, to their repositories and tables. The technical tables and the role reference data follow, so each of the 29 tables appears once.

#figure(
  table(
    columns: (auto, auto, auto, 1fr),
    table.header([Aggregate (root)], [Context], [Repository], [Tables]),
    [`User`], [`identity`], [`UserRepository`], [`sys_user`],
    [`OAuthIdentity`], [`identity`], [`UserOAuthRepository`], [`sys_user_oauth` (refers to `User` by `user_id`)],
    [`Product`], [`catalog`], [`ProductRepository`], [`biz_product`, `log_product_team_terms` (written in the same transaction)],
    [`TeamPricePolicy`], [`catalog`], [`TeamPriceRepository`], [`biz_team_price_policy`, `biz_team_price_quote` (`TeamPriceQuote`)],
    [`GroupDeal`], [`groupbuy`], [`PaidGroupDealRepository`], [`biz_group_deal`, `biz_group_member` (`PaidMember`), `biz_group_participation_receipt` (`ParticipationReceipt`)],
    [`ShareInvite`], [`groupbuy`], [`ShareInviteRepository`], [`biz_share_invite` (refers to `GroupDeal` by `deal_id`)],
    [`ShareCredit`], [`groupbuy`], [`ShareCreditRepository`], [`biz_share_credit` (refers to `GroupDeal` by `deal_id`)],
    [`PurchaseIntent`], [`order`], [`PurchaseIntentRepository`], [`biz_purchase_intent`],
    [`Order`], [`order`], [`OrderRepository`], [`biz_order`],
    [`CheckoutPayment`], [`payment`], [`CheckoutPaymentRepository`], [`biz_payment`, `log_payment_webhook_event`, `biz_payment_review_case` (read model `PaymentReviewCase`), `log_payment_review`],
    [Refund (no domain class)], [`payment`], [`RefundRepository`], [`biz_refund`, `log_refund_attempt` (`RefundWork` is a claim snapshot of a `biz_refund` row)],
    [`Notification`], [`notification`], [`NotificationRepository`], [`biz_notification`],
    [Technical (no aggregate)], [`notification`], [`ConsumedMessageRepository`], [`sys_consumed_message`],
    [Technical (no aggregate)], [commerce platform], [—], [`sys_outbox`, `sys_scheduler_lock` (`LeasedJobRunner`), `sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`; see @tbl-technical],
    [Reference data (no aggregate)], [`identity`], [`RbacMapper`], [`sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`],
  ),
  kind: table,
  caption: [Aggregates and the tables they own, in the order of @tbl-aggregates. Source: `blocbuy-domain` repositories and Flyway migrations V1–V28.],
) <tbl-aggregate-tables>

`ShareInvite` and `ShareCredit` are roots of their own: each has its own repository and refers to `GroupDeal` by `dealId`; the credit is written after the team commits, in its own transaction (§3.1.4, ADR 0003).

==== Transactions and concurrency

One business transaction never spans two databases; this is why payment, team and order stay in one service (AD-02). Within commerce, the riskiest transaction is settlement of a Stripe webhook for a team purchase. `settle` first claims the webhook event (an insert into `log_payment_webhook_event`), then locks team, purchase intent and payment in that order, then writes the membership, order and payment updates in one transaction (`OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`; ADR 0001). Expiry follows the global order team → purchase intent → order → payment, and its subscribers run order (`@Order(10)`) before payment (`@Order(20)`). The fixed order prevents deadlocks between concurrent webhooks and the expiry sweep; the team row lock serialises the race for the last seat.

Write transactions run at PostgreSQL's default READ COMMITTED with `SELECT … FOR UPDATE` row locks; read-only views that combine several queries use REPEATABLE READ for one snapshot. Most mutable aggregates also use their `version` column for optimistic locking on ordinary updates. The detailed sequence is in §3.2.4.

==== Technical tables

#figure(
  table(
    columns: (auto, auto, 1fr),
    table.header([Table], [Migration], [Purpose and pattern]),
    [`sys_outbox`], [V26], [*Transactional outbox.* `IntegrationOutboxWriter` inserts the event in the same transaction as the business change (`BEFORE_COMMIT`), so an event exists if and only if the change committed. `OutboxRelayJob` runs `OutboxRelayService` under the scheduler lease; it publishes unpublished rows to SNS in batches and sets `published_at`. The row ID, assigned by the database default `uuidv7()`, is the event ID],
    [`sys_consumed_message`], [V28], [*Idempotent receiver.* The notification consumer inserts `(event_id, consumer)` in the same transaction as the notification; a duplicate delivery inserts nothing and is acknowledged],
    [`sys_scheduler_lock`], [V13], [*Lease.* `LeasedJobRunner` gives each of the five scheduled jobs (AD-16) one holder across all tasks; PostgreSQL is the lock of record],
    [`sys_scheduled_item_retry`], [V17], [Per-item retry back-off (`failure_count`, `retry_after`), so one failing item cannot block a job],
    [`sys_checkout_expiry_cursor`], [V25], [The keyset position (`last_create_time`, `last_payment_id`) of the operator-run Checkout expiry sweep, so one run resumes where the last stopped],
  ),
  kind: table,
  caption: [Technical tables and the patterns they support. Source: Flyway migrations V13–V28.],
) <tbl-technical>

Calculated: the relay publishes at most about 50 events/s (100 rows per 2 s tick), and only one copy runs because it holds the scheduler lease (AD-16, §3.3).

==== Non-relational stores and messages

*Session and cache store (Valkey).* Valkey holds only data that can be lost: login sessions, the role snapshot that lets any service authorise a request, one-time sign-in state, two read caches and rate-limit counters. Every key has a time to live (TTL), so nothing grows without bound. Configured, not tested: @tbl-valkey lists the key model from the code and configuration (Sa-Token 1.46.0, `application.yml`, `RedisKeys`); it was not read from a running node.

Sa-Token is configured with the token name `blocbuy-token`, a random UUID token sent as an HTTP-only cookie, a 24-hour timeout (`timeout: 86400`) and several concurrent sessions per user (`is-concurrent: true`, `is-share: false`). The application's own keys follow `blocbuy:{profile}:{domain}:{type}:{id}`, where `{profile}` is the active Spring profile list; on ECS that is `ec2,<service>,aws`, so each service's cache keys are separate from the others'.

#figure(
  table(
    columns: (1.6fr, 0.7fr, 0.9fr, 1.8fr),
    table.header([Key pattern], [Written by], [TTL], [Content and what is lost if the node fails]),
    [`blocbuy-token:login:token:<token>`], [identity], [24 h], [Maps a token to the user ID. Lost: the user is signed out],
    [`blocbuy-token:login:session:<userId>`], [identity], [24 h], [The user's account session and the list of that user's tokens. Lost: all of the user's devices are signed out],
    [`blocbuy-token:rbac:login:{<userId>}`], [identity], [Same as the session], [`RedisLoginSnapshotGateway`. The user's roles and permissions; every service reads it to authorise a request without calling identity (AD-05). Lost: requests are refused until the user signs in again],
    [`…:rbac:login:{<userId>}:refresh`], [identity], [120 s], [A fence so that only the latest role refresh is published. Lost: harmless],
    [`blocbuy:{profile}:identity:oauth-state:<state>`], [identity], [Short (configured, default 5 min); deleted on first read], [`RedisOAuthStateStore`. One-time Google sign-in state that binds the callback to the browser that started it. Lost: a sign-in in progress must restart],
    [`blocbuy:{profile}:groupbuy:best-open-deal:<productId>`], [commerce], [15–25 s (5–10 s for "none"), with jitter], [`RedisOpenDealOfferCache`. Cached best open team for a product page. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:groupbuy:invite-ref:<ref>`], [commerce], [20–30 s with jitter, never past the team's expiry], [`RedisCachedShareInviteRepository`. Cached invite-link lookup. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:common:rate-limit:<scope>:<digest>`], [commerce], [One rate-limit window], [`RedisRateLimitGateway`. Request counter per scope and hashed subject. If Valkey is unreachable the limiter fails open and counts the event in a metric],
  ),
  kind: table,
  caption: [Valkey key model; PostgreSQL remains the system of record, and no business fact lives only in Valkey. Source: code and configuration, read on 5 November 2026.],
) <tbl-valkey>

The design rule is that Valkey is never the lock of record or the only copy of a business fact: the scheduler lease is in PostgreSQL (`sys_scheduler_lock`), and caches are read-through with short, jittered TTLs so that a lost node costs latency, not correctness. The cost of a lost node is that every user is signed out (AISS-10).

*Event envelope.* Every integration event is published to SNS as JSON with the envelope of @tbl-envelope (`IntegrationEventEnvelope`). The SNS message attributes repeat `eventType`, `version` and `traceId`, so subscriptions can filter without parsing the body.

#figure(
  table(
    columns: (auto, auto, 1fr),
    table.header([Field], [Type], [Meaning]),
    [`eventId`], [UUID], [Outbox row ID; consumers de-duplicate on it],
    [`eventType`], [string], [`TeamSucceeded`, `TeamExpired` or `PaymentSucceeded`],
    [`version`], [integer], [Payload schema version (currently 1, for example `TeamSucceededV1`)],
    [`occurredAt`], [timestamp], [When the outbox row was written, in the business transaction],
    [`producer`], [string], [`blocbuy-commerce`],
    [`traceId`], [string], [Correlates logs across services],
    [`aggregateId`], [UUID], [The team or payment the event is about],
    [`payload`], [object], [Event-specific fields, for example the team, product, size and recipient user IDs],
  ),
  kind: table,
  caption: [Integration event envelope fields. Source: `IntegrationEventEnvelope` and `OutboxMessageFactory`.],
) <tbl-envelope>

*Queues.* SNS delivers raw messages to SQS, and each subscription filters on the `eventType` attribute. The notification queue receives `TeamSucceeded`, `TeamExpired` and `PaymentSucceeded`; the audit queue receives every event whose type starts with `Team`. Each queue has a 60 s visibility timeout, 20 s long polling and 4-day retention; after five failed receives a message moves to the queue's DLQ, which keeps it for 14 days. Topic and queues are encrypted (AWS-managed SNS key, SQS-managed encryption).

==== Data classification

#figure(
  table(
    columns: (auto, 1fr, 1fr, 1.2fr, 1fr, 1.1fr),
    table.header([Data group], [Data format], [Storage technology], [Security requirements and controls], [Retention], [Trade-off]),
    [Identity], [Relational rows; Google profile as JSON (`raw_profile`)], [PostgreSQL, `identity` database], [Personal data (email, name). Only identity's app role can read it; storage encrypted; TLS required; other services see only public nicknames and whether the account is active, through an internal endpoint], [Until the runtime is destroyed; no backup (AD-12)], [Synchronous lookup from commerce makes identity a dependency of checkout (AD-06, AISS-03)],
    [Commerce and payment], [Relational rows; money as `numeric` with currency], [PostgreSQL, `commerce` database], [No card data stored: Stripe holds it and we keep only Stripe IDs. Unique indexes stop double charges and double refunds; webhook events stored for audit], [Until the runtime is destroyed; no backup (AD-12)], [One database for team, order and payment keeps settlement atomic, at the cost of one large service (AISS-02)],
    [Notifications], [Relational rows, immutable], [PostgreSQL, `notification` database], [Readable only by the recipient through the API; only notification's app role can write], [Until the runtime is destroyed (AD-12)], [Eventually consistent, about 2 s behind the action (AISS-11)],
    [Sessions], [Key–value, Sa-Token session objects], [ElastiCache Valkey], [HTTP-only cookie; TLS and an AUTH token on the connection; 24-hour expiry; reachable only from the service group], [24 h TTL], [Fast and shared by all services, but one node: a restart signs everyone out (AISS-10)],
    [Events], [JSON envelope with string message attributes], [`sys_outbox` table, then SNS and SQS], [Encrypted topic and queues; IAM policies limit publish and receive to the producing and consuming task roles; payloads carry IDs, not personal data], [Outbox rows 7 days after publishing (`OutboxRetentionJob`); SQS 4 days; DLQ 14 days], [At-least-once delivery, so consumers must be idempotent (`sys_consumed_message`)],
  ),
  kind: table,
  caption: [Data classification per data group (template format); logs are kept 7 days in CloudWatch. Source: `infra/swe5001`, `OutboxRetentionJob` and the Sa-Token configuration.],
) <tbl-classification>
