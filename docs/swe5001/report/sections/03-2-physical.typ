// 3.2 Physical Architecture & Design, 3.2.1–3.2.3. Owner: Liu Jin (#742).
// 3.2.4 Detailed Design (Yu Qianshuo) stays in 03-solution-overview.typ.
#import "../lib.typ": *
#import "../diagrams/d5-physical-deployment.typ": d5
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d9-data-models.typ": d9-identity, d9-notification, d9-commerce


== Physical Architecture & Design

BlocBuy runs on AWS in Singapore (`ap-southeast-1`) as three containers on ECS Fargate behind CloudFront and one Application Load Balancer (ALB), with one PostgreSQL server, one Valkey node and one SNS topic with SQS queues. Only the ALB and the NAT instance sit in public subnets. The services run in private application subnets, and the data stores sit in separate data subnets that have no route out of the virtual private cloud (VPC). Everything except the edge (CloudFront and the website bucket) is created from nothing for each session or release and destroyed afterwards (AD-12).

@tab-logical-physical maps each element of the logical view (3.1.3) to the product that implements it.

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Logical element (3.1)], [Product], [Configuration],
    [CDN and edge gateway], [Amazon CloudFront], [HTTPS for viewers on the default `*.cloudfront.net` certificate; `/api/*` forwarded uncached to the ALB, everything else served from S3 and cached],
    [Web client host], [Amazon S3], [Private bucket readable only by CloudFront (origin access control)],
    [Request router], [Application Load Balancer], [Listener on HTTP port 80, reachable only from CloudFront's origin-facing addresses and only with the `X-Origin-Secret` header; path rules choose the service],
    [Identity service], [ECS Fargate, `identity-svc`], [ARM64, 0.5 vCPU, 1 GiB; one task],
    [Commerce service], [ECS Fargate, `commerce-svc`], [ARM64, 0.5 vCPU, 1 GiB; 2 to 6 tasks (1 to 6 in baseline sessions), scaled on ALB requests per target],
    [Notification service], [ECS Fargate, `notification-svc`], [ARM64, 0.5 vCPU, 1 GiB; 1 to 4 tasks, scaled on queue backlog],
    [Service-to-service call], [ECS Service Connect], [Commerce reaches identity by service name inside the VPC],
    [Relational data store], [Amazon RDS for PostgreSQL 18], [`db.t4g.micro`, 20 GiB gp3, one zone; three databases (`identity`, `commerce`, `notification`)],
    [Session store], [Amazon ElastiCache for Valkey 8.0], [`cache.t4g.micro`, one node, TLS and an AUTH token],
    [Message broker and event queues], [Amazon SNS and SQS], [One topic `blocbuy-swe5001-domain-events`; `notification` and `audit` queues, each with a dead-letter queue (DLQ)],
    [Outbound gateway], [fck-nat on EC2 `t4g.nano`], [One instance with a static network interface and an Elastic IP],
    [Configuration and secrets], [SSM Parameter Store], [Under `/blocbuy/swe5001/`; read by the task execution role at start-up],
    [Monitoring], [Amazon CloudWatch], [Logs under `/blocbuy/swe5001/*` (7 days), one dashboard, DLQ and queue-age alarms],
  ),
  caption: [Logical elements and the AWS products that implement them. Configured values from `infra/swe5001` on 4 November 2026.],
) <tab-logical-physical>

#heading(level: 3, numbering: none, outlined: false)[Deployment view]

#figure(kind: image, scale(72%, reflow: true, d5), caption: [Physical deployment view (UML deployment diagram). Only CloudFront reaches the load balancer, and the only synchronous call between services is commerce → identity (red).]) <fig-d5>

@fig-d5 shows where each artefact runs and how the nodes talk:

- *Browser traffic* reaches CloudFront over HTTPS. CloudFront serves the single-page application from S3 and forwards `/api/*` to the ALB. Stripe's webhook enters the same way.
- *The ALB* picks the service by path. Identity takes `/api/v1/auth/*` and `/api/v1/users/*`, notification takes `/api/v1/notifications*`, and commerce takes the rest of `/api/*`. A rule with the lowest priority blocks `/internal/*` at the ALB, so internal endpoints are only reachable inside the VPC.
- *Services* talk to each other only once, commerce → identity through Service Connect (AD-06). Everything else between services goes through events: commerce writes them to its outbox, a relay publishes them to SNS, and SNS delivers copies to the SQS queues that the notification service and the audit consumer read (AD-04).
- *Every task* reads its database and session settings from SSM Parameter Store when it starts. Database passwords never pass through Terraform state (3.2.2).

#heading(level: 3, numbering: none, outlined: false)[Network and security groups]

#figure(kind: image, block(breakable: false, align(left, d6)), caption: [Network view (AWS architecture style: VPC, zones, subnets and security groups). Three subnet tiers per zone; the data tier's route table has no route out of the VPC.]) <fig-d6>

The VPC (`10.50.0.0/16`) spans two availability zones and has three subnet tiers in each zone (@fig-d6):

#figure(
  table(
    columns: (auto, auto, auto, 1fr),
    [Tier], [Zone a], [Zone b], [What runs there and how it reaches out],
    [Public], [`10.50.0.0/24`], [`10.50.1.0/24`], [ALB nodes and the fck-nat instance. Default route to the internet gateway],
    [Private application], [`10.50.10.0/24`], [`10.50.11.0/24`], [ECS tasks and the one-off `db-init` task. Default route to the fck-nat network interface; free S3 gateway endpoint for image layers],
    [Data], [`10.50.20.0/24`], [`10.50.21.0/24`], [RDS and ElastiCache subnet groups only. Local VPC route only: no NAT, no internet gateway, no endpoint],
  ),
  caption: [Subnet tiers. Configured in `infra/swe5001/modules/network`; the data tier was added on 4 November 2026 (AD-15).],
) <tab-subnets>

*Separate data subnets (assessor remark F6).* Until 4 November the database, the cache and the services shared the private application subnets, separated only by security groups. The data stores now have their own subnets and route table (AD-15). This gives two independent barriers:

+ *Routing.* A data subnet has no route out of the VPC, so a compromised or misconfigured data store cannot reach the internet, even through the NAT instance.
+ *Security groups.* RDS accepts TCP 5432 only from the service group and the `db-init` group. Valkey accepts TCP 6379 only from the service group. Neither group allows outbound traffic.

Configured and tested offline, not yet on AWS: module validation rejects data CIDRs that reuse an application or public CIDR, and the `terraform test` run `data_tier_has_its_own_subnets` checks that the two sets are disjoint (19 of 19 runs passed, 4 November 2026). The runtime is rebuilt from nothing each session, so the next session creates the new layout without migrating anything. Until that session records the actual placement, this stays open as AISS-07 and NFR-70.

*Single points in the network.* Each zone has its own private route table, but both point at the single fck-nat instance in zone a. An Auto Scaling group of one replaces a failed instance, but only in zone a, because the static network interface lives there. If zone a fails, the services in zone b lose outbound access (for example to Stripe and Google) until the zone recovers. This is a cost decision (AD-10) and is listed as AISS-01.

=== Key Architectural Decisions

#prompt[Provide a summary of the significant decisions made in arriving at the architecture of the solution that are related and/or reflected in the physical view. Documented decisions should focus on trade-off choices and rationale. You should continue the numbering from the previous architectural decisions table.]

The key architectural decisions taken are as follows. Prices are on-demand list prices for `ap-southeast-1`, from the team's cost estimate of 25 September 2026 (`04-aws-cost-estimate.md`), unless the row says otherwise.

#figure(
  table(
    columns: (auto, 1fr),
    [Identifier], [Description],
    [AD-07], [*ECS Fargate on ARM64 (Graviton), not EKS, App Runner or Lambda.* _Context:_ three long-running Spring Boot services that need per-service scaling and private networking. _Options:_ EKS (Kubernetes), App Runner, Lambda, ECS on Fargate. _Choice:_ ECS on Fargate, ARM64 images. _Trade-off:_ no cluster to run or patch, and ARM64 is about 20% cheaper per vCPU-hour than x86 (estimated from the list prices); in exchange the team does not practise Kubernetes and is tied to ECS task definitions. _Consequence:_ the image pipeline builds `linux/arm64` only (5.2). Lambda was rejected because the services keep connection pools and run scheduled jobs.],
    [AD-08], [*Fargate Spot above a regular-Fargate base.* _Context:_ commerce needs a guaranteed floor for availability but extra capacity only during load. _Options:_ all on-demand, all Spot, a mixed strategy. _Choice:_ capacity provider strategy `FARGATE` with base equal to the commerce minimum (2 in demo sessions) and weight 1, plus `FARGATE_SPOT` with weight 3. _Trade-off:_ Spot capacity is cheaper but can be reclaimed with two minutes' notice; the base keeps two tasks on regular Fargate, so a reclaim never takes commerce below its floor. _Consequence:_ configured; the 16 and 19 October AWS records show the strategy on the service, but not which provider each task ran on. The cost study of 25 September noted Spot as documented for x86 only, so ARM64 Spot placement is not shown by our evidence.],
    [AD-09], [*CloudFront in front of an ALB restricted by prefix list and a secret header.* _Context:_ one HTTPS entry point for the website and the API, with the load balancer not usable directly. _Options:_ public ALB with its own certificate; API Gateway with a VPC link; CloudFront → ALB. _Choice:_ CloudFront → ALB on HTTP port 80; the ALB security group admits only CloudFront's origin-facing prefix list, and an ALB rule rejects requests without the `X-Origin-Secret` header. _Trade-off:_ one domain and no certificate to manage for the ALB; but the hop from CloudFront to the ALB is not encrypted (AISS-08) and the secret sits in Terraform state (AISS-09). _Consequence:_ requests that bypass CloudFront are refused; no per-client rate limiting (no WAF).],
    [AD-10], [*One fck-nat instance, not a NAT Gateway.* _Context:_ private tasks need outbound access to Stripe, Google and AWS APIs. _Options:_ NAT Gateway per zone, one NAT Gateway, VPC interface endpoints, a NAT instance. _Choice:_ fck-nat on one `t4g.nano` with a static network interface; an Auto Scaling group of one replaces a failed instance. _Trade-off:_ estimated about US\$4 a month instead of US\$43.07 for one NAT Gateway (730 hours at US\$0.059); in exchange, egress depends on one instance in one zone (AISS-01). _Consequence:_ a free S3 gateway endpoint keeps image-layer pulls off the instance.],
    [AD-11], [*One RDS server holding three databases.* _Context:_ each service must own its data (AD-03), but three servers would triple the largest fixed cost. _Options:_ one server per service; one shared schema; one server with a database and two roles per service. _Choice:_ one `db.t4g.micro` instance with databases `identity`, `commerce` and `notification`, each with an owner role for migrations and an app role for runtime. _Trade-off:_ isolation by grants and connection strings at the cost of one server; the services share capacity and a failure takes all three down (AISS-05). _Consequence:_ moving a database to its own server changes only the connection settings, not the code.],
    [AD-12], [*Permanent edge, disposable runtime.* _Context:_ an always-on runtime would cost about US\$125 a month (estimated in the cost study) for a system used a few hours a week. _Options:_ always on; scale to zero; destroy and rebuild. _Choice:_ the `edge` stack (CloudFront, S3) is permanent; the `runtime` stack is created for each session or release and destroyed afterwards. _Trade-off:_ estimated US\$0.12 to US\$0.20 an hour while up; but a rebuild takes about 32 minutes (17 October run, AISS-06) and no data survives a session. _Consequence:_ every release is also a rebuild-from-nothing test; backups are deliberately off.],
    [AD-13], [*No Kubernetes and no Argo CD.* _Context:_ GitOps was one option for continuous delivery. _Options:_ EKS + Argo CD; ECS with Terraform and GitHub Actions. _Choice:_ the latter. _Trade-off:_ the EKS control plane alone was estimated at about US\$73 a month, more than all our compute, and set-up at 8 to 12 person-days before any feature work (`03-k8s-argocd-assessment.md`); we give up the GitOps reconciliation loop. _Consequence:_ deployment is a pipeline run (5.3), not a controller.],
    [AD-14], [*An own member account in an AWS Organization.* _Context:_ the SWE5006 environment and the SWE5001 environment must not be able to damage each other. _Options:_ share the SWE5006 account; IAM users in a new account; an Organization member account with Identity Center. _Choice:_ the last, with service control policies (SCPs) as guardrails the account administrator cannot lift. _Trade-off:_ separate bill and blast radius and short-lived logins; but an SCP can also block legitimate calls, as a CloudFront metric read was on 16 October (AISS-14). _Consequence:_ access details are in 4.3.],
    [AD-15], [*Separate data subnets for RDS and Valkey.* _Context:_ assessor remark F6 (21 October): the database should be in its own subnet. _Options:_ keep the shared private subnets and rely on security groups; add data subnets with their own route table. _Choice:_ two data subnets, one per zone, with a route table that holds only the local VPC route. _Trade-off:_ no running cost and no code change; two more subnets and a route table to maintain, and a rebuild is needed to adopt it (free here, AD-12). _Consequence:_ configured and tested offline on 4 November 2026; not yet applied on AWS (AISS-07).],
  ),
  caption: [Physical architectural decisions (AD-07 to AD-15; numbering continues from 3.1.1 and is fixed in `registers.md`).],
) <tab-ad-physical>

=== Technology and Services

The solution uses managed AWS services wherever one exists, so the team runs no servers of its own apart from one NAT instance. Every choice favours the lowest cost that still shows the architecture, because the runtime exists only for a few hours a week (AD-12). @tab-tech lists the versions and sizes; the rationale for the main choices is in the AD rows of 3.2.1 and is not repeated here.

#figure(
  table(
    columns: (0.8fr, 1.4fr, 2.2fr),
    [Layer], [Technology and version], [Why this one, and what it costs us],
    [Application runtime], [Java 21, Spring Boot 4.1.1, MyBatis; image `eclipse-temurin:21-jre-noble`, built for `linux/arm64`], [The SWE5006 code base, reused unchanged by the strangler migration (AD-01). One base image for all three services keeps patching in one place],
    [Container platform], [ECS on Fargate, ARM64; 0.5 vCPU and 1 GiB per task], [No hosts to manage (AD-07). The task size was kept small on purpose: one 0.5 vCPU commerce task saturated at about 280 req/s on AWS on 19 Oct 2026 (evidence `20261019-v3-autoscaling`, 4.1), so the service scales out instead of up],
    [Image registry], [Amazon ECR, one repository per service; immutable tags, scan on push, lifecycle policy], [Immutable tags make a release digest refer to exactly one image; Trivy in the pipeline is the blocking scan (5.2) and ECR's scan is a second record],
    [Edge], [Amazon CloudFront (`PriceClass_200`, HTTP/2) and a private S3 bucket], [HTTPS without a custom domain or certificate; the website and the API share one origin, so the browser needs no cross-origin settings (AD-09). The price class excludes the most expensive edge locations],
    [Load balancing], [Application Load Balancer, HTTP :80, path rules], [Path-based routing to three services without a separate API gateway product; rejected API Gateway because it adds a hop and a per-request charge],
    [Service discovery], [ECS Service Connect], [Commerce calls identity by name inside the VPC with no load balancer or DNS records to manage],
    [Relational database], [Amazon RDS for PostgreSQL 18, `db.t4g.micro`, 20 GiB gp3], [PostgreSQL 18 generates UUIDv7 keys natively (3.2.3). The smallest Graviton class is enough for demo load; the single database server is the next scaling limit (AISS-05)],
    [Session store], [Amazon ElastiCache for Valkey 8.0, `cache.t4g.micro`, one node], [Redis-compatible, so Sa-Token and Spring Data Redis work unchanged; Valkey costs less than Redis OSS on ElastiCache. One node is a cost choice (AISS-01, AISS-10)],
    [Messaging], [Amazon SNS (one topic) and SQS (standard queues with DLQs)], [Fan-out to several consumers with no broker to run (AD-04); standard queues give at-least-once delivery, which the consumers handle with `sys_consumed_message`],
    [Outbound network], [fck-nat on `t4g.nano`], [About a tenth of a NAT Gateway's cost (AD-10)],
    [Configuration and secrets], [SSM Parameter Store, SecureString with the AWS-managed key], [Free at this volume; tasks read values at start-up through their execution role. Secrets Manager was not needed, because rotation happens by rebuilding the runtime],
    [Monitoring], [CloudWatch Logs (7-day retention), one dashboard, alarms to an SNS alert topic], [Managed and cheap at demo volume; logs are kept only as long as a sprint review needs them],
    [Infrastructure as code], [Terraform 1.16, AWS provider 5.100; three stacks: `foundation`, `edge` and `runtime`], [The SWE5006 environment already used Terraform; `terraform test` runs offline in CI (5.2). Stack split by lifetime: `foundation` and `edge` are permanent, `runtime` is disposable],
  ),
  caption: [Technology and services, with versions and sizes from `backend/pom.xml`, `backend/Dockerfile` and `infra/swe5001` (checked on 5 November 2026).],
) <tab-tech>

*Encryption and secrets.* Data is encrypted at rest everywhere it is stored: RDS storage, the Valkey node, SNS (AWS-managed key), SQS (SQS-managed keys), ECR and the Terraform state bucket. In transit, browsers use HTTPS to CloudFront, the services use TLS to RDS (`rds.force_ssl = 1`, `sslmode=require`) and to Valkey (with an AUTH token). The CloudFront → ALB hop is plain HTTP (AISS-08). Database passwords are generated as ephemeral values and written with write-only arguments, so they never reach Terraform state; the Valkey AUTH token and the origin secret do reach it (AISS-09). Each service connects with its own app role, and only `db-init` uses the master password.

*Cost.* Calculated from the AD rows: while it is up, the runtime costs about US\$0.12 to US\$0.20 an hour (AD-12), and nothing for the rest of the week except the permanent edge. A measured figure from the AWS bill is not cited here, because the repository holds no record of it yet; chapter 6 reports the measured cost once that record is added.

=== Persistence Design

All persistent business data lives in PostgreSQL. Each service has its own database on the shared server and can read and write only the tables it owns. Sessions live in Valkey and are lost if the node restarts. Events live in the commerce outbox table until they are published, then in SQS until a consumer has applied them. Nothing is backed up, because every session starts from seed data (AD-12).

==== Data stores and table ownership

Every service database runs the same Flyway migration stream (`V1` to `V28`), so each database contains all 29 tables. Ownership is enforced by grants, not by which tables exist: after each migration, `afterMigrate__service_grants.sql` gives the service's app role `SELECT, INSERT, UPDATE, DELETE` on the tables it owns and revokes everything on the others. The owner role runs migrations; the app role used at runtime cannot change the schema.

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Database], [Owned tables], [Tables],
    [`identity`], [6], [`sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`],
    [`notification`], [2], [`biz_notification`, `sys_consumed_message`],
    [`commerce`], [21], [Catalogue: `biz_product`, `biz_team_price_policy`, `biz_team_price_quote`, `log_product_team_terms`. Team purchase: `biz_group_deal`, `biz_group_member`, `biz_group_participation_receipt`, `biz_share_invite`, `biz_share_credit`. Order and payment: `biz_purchase_intent`, `biz_payment`, `biz_order`, `biz_refund`, `log_refund_attempt`, `biz_payment_review_case`, `log_payment_review`, `log_payment_webhook_event`. Technical: `sys_outbox`, `sys_scheduler_lock`, `sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`],
  ),
  caption: [Table ownership per database, from `afterMigrate__service_grants.sql`. Commerce owns every table not listed for identity or notification.],
) <tab-ownership>

The trade-off: one migration stream is simple to build and test, and the grants stop a service from using another's tables, which architecture tests also check. The cost is that unused, empty tables exist in every database, and a migration for one service runs in all three.

==== Conventions

The schema follows the team's PostgreSQL conventions (`pgsql-ddl` skill):

- *Keys.* Native `uuid` primary keys generated as UUIDv7 (`uuidv7()`, PostgreSQL 18). UUIDv7 is time-ordered, so new rows append to the index instead of splitting pages, and the application can create an identifier before the insert, which the outbox uses as the event ID.
- *Prefixes.* `sys_` for platform and technical tables, `biz_` for business entities, `rel_` for many-to-many links, `log_` for append-only records.
- *Audit and soft delete.* Business tables carry `create_time`, `create_by`, `update_time`, `update_by` and `deleted`; mutable aggregates also carry `version` for optimistic locking. Uniqueness rules use partial indexes `WHERE deleted = false`, so a soft-deleted row does not block a new one.
- *No foreign keys or CHECK constraints.* Relationships are logical, kept by the application inside its transactions. This lets a table move to another service's database without unpicking constraints, and keeps validation in one place (the domain model). The cost is that the database does not catch an orphaned row; the unique indexes below catch the errors that matter most.

==== Logical data models

#figure(kind: image, d9-identity, caption: [Identity database, logical data model (UML class notation for tables). Users, their Google identities and role-based permissions.]) <fig-d9-identity>

#figure(kind: image, d9-notification, caption: [Notification database, logical data model (UML class notation for tables). A notification and the consumed-message record are written in one transaction, keyed by the same event ID.]) <fig-d9-notification>

#figure(kind: image, scale(90%, reflow: true, d9-commerce), caption: [Commerce database, logical data model (UML class notation for tables; key columns only). Dashed lines are logical relationships with multiplicities; there are no foreign keys.]) <fig-d9-commerce>

==== Indexes that protect invariants

Several business rules are guaranteed by unique indexes, so that a race between two transactions ends in a constraint violation instead of a wrong state:

#figure(
  table(
    columns: (auto, 1fr),
    [Index], [Rule it protects],
    [`uq_biz_group_member_deal_user` (`deal_id, user_id`)], [A buyer joins a team at most once],
    [`uq_biz_group_member_payment_id`], [One payment admits at most one member],
    [`uq_biz_purchase_intent_fulfilled_payment`], [A purchase intent is fulfilled by at most one payment (ADR 0001)],
    [`uq_biz_payment_pending_intent` (`WHERE status = 'created'`)], [At most one open payment per purchase intent],
    [`uq_biz_payment_request_key` (`user_id, request_key`)], [A retried checkout request does not create a second payment],
    [`uq_biz_order_payment_id`, `uq_biz_order_deal_user`], [One order per payment, and one order per buyer per team],
    [`biz_refund.payment_id` (unique)], [A payment is refunded at most once],
    [`uq_log_payment_webhook_event_event_id`], [A Stripe webhook event is processed at most once],
    [`uq_sys_scheduler_lock_key`], [A scheduled job has at most one lease holder],
    [`pk_sys_consumed_message` (`event_id, consumer`)], [A consumer applies an event at most once],
  ),
  caption: [Unique indexes that enforce invariants (Flyway migrations V2–V28).],
) <tab-invariant-indexes>

==== Aggregates and their tables

#todo([Align the aggregate names with Eric's aggregate table in 3.1.4 before review.])

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Aggregate (domain class)], [Service], [Tables],
    [Team (`GroupDeal`, with `TeamMembership`)], [commerce], [`biz_group_deal`, `biz_group_member`, `biz_group_participation_receipt`],
    [Purchase intent (`PurchaseIntent`)], [commerce], [`biz_purchase_intent`],
    [Payment (`CheckoutPayment`)], [commerce], [`biz_payment`, `log_payment_webhook_event`],
    [Payment review (`PaymentReviewCase`)], [commerce], [`biz_payment_review_case`, `log_payment_review`],
    [Refund (`RefundWork`)], [commerce], [`biz_refund`, `log_refund_attempt`],
    [Order (`Order`)], [commerce], [`biz_order`],
    [Product (`Product`, `TeamPricePolicy`)], [commerce], [`biz_product`, `biz_team_price_policy`, `biz_team_price_quote`, `log_product_team_terms`],
    [Share (`ShareInvite`, `ShareCredit`)], [commerce], [`biz_share_invite`, `biz_share_credit`],
    [User (`User`)], [identity], [`sys_user`, `sys_user_oauth`, `rel_user_role`],
    [Notification (`Notification`)], [notification], [`biz_notification`],
  ),
  caption: [Aggregate-to-table mapping. Class names are in `blocbuy-domain`.],
) <tab-aggregate-tables>

==== Transactions and concurrency

One business transaction never spans two databases; this is why payment, team and order stay in one service (AD-02). Within commerce, the riskiest transaction is settlement of a Stripe webhook for a team purchase. It locks rows in a fixed order (team, then purchase intent, then payment) and then writes the membership, order and payment updates in one transaction (`OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`; ADR 0001). The fixed order prevents deadlocks between concurrent webhooks; the team row lock serialises the race for the last seat. The detailed sequence is in 3.2.4. Mutable aggregates also use their `version` column for optimistic locking on ordinary updates.

==== Technical tables

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Table], [Migration], [Purpose and pattern],
    [`sys_outbox`], [V26], [*Transactional outbox.* `IntegrationOutboxWriter` inserts the event in the same transaction as the business change (`BEFORE_COMMIT`), so an event exists if and only if the change committed. `OutboxRelayJob` publishes unpublished rows to SNS in batches and sets `published_at`. The row ID (UUIDv7) is the event ID],
    [`sys_consumed_message`], [V28], [*Idempotent receiver.* The notification consumer inserts `(event_id, consumer)` in the same transaction as the notification; a duplicate delivery inserts nothing and is acknowledged],
    [`sys_scheduler_lock`], [V13], [*Lease.* `LeasedJobRunner` gives each scheduled job (expiry, refunds, outbox relay) one holder across all tasks; PostgreSQL is the lock of record],
    [`sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`], [V17, V25], [Per-item retry back-off and a paging cursor, so one failing item cannot block a job],
  ),
  caption: [Technical tables and the patterns they support.],
) <tab-technical>

Calculated: the relay publishes about 50 events per second, and only one copy runs because it holds the scheduler lease (see 4.1 for the formula).

==== Non-relational stores and messages

*Session and cache store (Valkey).* Valkey holds only data that can be lost: login sessions, the role snapshot that lets any service authorise a request, one-time sign-in state, two read caches and rate-limit counters. Every key has a time to live (TTL), so nothing grows without bound. @tab-valkey lists the key model, taken from the code and configuration (Sa-Token 1.46.0, `application.yml`, `RedisKeys`); it has not been read from a running node.

Sa-Token is configured with the token name `blocbuy-token`, a random UUID token sent as an HTTP-only cookie, a 24-hour timeout (`timeout: 86400`) and several concurrent sessions per user (`is-concurrent: true`, `is-share: false`). The application's own keys follow `blocbuy:{profile}:{domain}:{type}:{id}`, where `{profile}` is the active Spring profile list; on ECS that is `ec2,<service>,aws`, so each service's cache keys are separate from the others'.

#figure(
  table(
    columns: (1.6fr, 0.7fr, 0.9fr, 1.8fr),
    [Key pattern], [Written by], [TTL], [Content and what is lost if the node fails],
    [`blocbuy-token:login:token:<token>`], [identity], [24 h], [Maps a token to the user ID. Lost: the user is signed out],
    [`blocbuy-token:login:session:<userId>`], [identity], [24 h], [The user's account session and the list of that user's tokens. Lost: all of the user's devices are signed out],
    [`blocbuy-token:rbac:login:{<userId>}`], [identity], [Same as the session], [`RedisLoginSnapshotGateway`. The user's roles and permissions; every service reads it to authorise a request without calling identity (AD-05). Lost: requests are refused until the user signs in again],
    [`…:rbac:login:{<userId>}:refresh`], [identity], [2 min], [A fence so that only the latest role refresh is published. Lost: harmless],
    [`blocbuy:{profile}:identity:oauth-state:<state>`], [identity], [Short (configured, default 5 min); deleted on first read], [`RedisOAuthStateStore`. One-time Google sign-in state that binds the callback to the browser that started it. Lost: a sign-in in progress must restart],
    [`blocbuy:{profile}:groupbuy:best-open-deal:<productId>`], [commerce], [15–25 s (5–10 s for "none"), with jitter], [`RedisOpenDealOfferCache`. Cached best open team for a product page. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:groupbuy:invite-ref:<ref>`], [commerce], [20–30 s with jitter, never past the team's expiry], [`RedisCachedShareInviteRepository`. Cached share-invite lookup. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:common:rate-limit:<scope>:<digest>`], [commerce], [One rate-limit window], [`RedisRateLimitGateway`. Request counter per scope and hashed subject. If Valkey is unreachable the limiter fails open and counts the event in a metric],
  ),
  caption: [Valkey key model (from code and configuration, 5 November 2026). PostgreSQL remains the system of record; no business fact lives only in Valkey.],
) <tab-valkey>

The design rule is that Valkey is never the lock of record or the only copy of a business fact: the scheduler lease is in PostgreSQL (`sys_scheduler_lock`), and caches are read-through with short, jittered TTLs so that a lost node costs latency, not correctness. The cost of a lost node is that every user is signed out (AISS-10).

*Event envelope.* Every integration event is published to SNS as JSON with this envelope (`IntegrationEventEnvelope`):

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Field], [Type], [Meaning],
    [`eventId`], [UUID], [Outbox row ID; consumers de-duplicate on it],
    [`eventType`], [string], [`TeamSucceeded`, `TeamExpired` or `PaymentSucceeded`],
    [`version`], [integer], [Payload schema version (currently 1, for example `TeamSucceededV1`)],
    [`occurredAt`], [timestamp], [When the business change committed],
    [`producer`], [string], [`blocbuy-commerce`],
    [`traceId`], [string], [Correlates logs across services],
    [`aggregateId`], [UUID], [The team or payment the event is about],
    [`payload`], [object], [Event-specific fields, for example the team, product, size and recipient user IDs],
  ),
  caption: [Integration event envelope. The SNS message attributes repeat `eventType`, `version` and `traceId`, so subscriptions can filter without parsing the body.],
) <tab-envelope>

*Queues.* SNS delivers raw messages to SQS, and each subscription filters on the `eventType` attribute. The notification queue receives `TeamSucceeded`, `TeamExpired` and `PaymentSucceeded`; the audit queue receives every event whose type starts with `Team`. Each queue has a 60-second visibility timeout, 20-second long polling and 4-day retention; after 5 failed receives a message moves to the queue's DLQ, which keeps it for 14 days. Topic and queues are encrypted (AWS-managed SNS key, SQS-managed encryption).

==== Data classification

#prompt[For each data group: data format; proposed storage technology; security requirements and controls; trade-off consideration.]

#figure(
  table(
    columns: (auto, 1fr, 1fr, 1.2fr, 1.2fr),
    [Data group], [Data format], [Storage technology], [Security requirements and controls], [Trade-off],
    [Identity], [Relational rows; Google profile as JSON (`raw_profile`)], [PostgreSQL, `identity` database], [Personal data (email, name). Only identity's app role can read it; storage encrypted; TLS required; other services see only public nicknames through an internal endpoint], [Synchronous lookup from commerce makes identity a dependency of checkout (AD-06, AISS-03)],
    [Commerce and payment], [Relational rows; money as `numeric` with currency], [PostgreSQL, `commerce` database], [No card data stored: Stripe holds it and we keep only Stripe IDs. Unique indexes stop double charges and double refunds; webhook events stored for audit], [One database for team, order and payment keeps settlement atomic, at the cost of one large service (AISS-02)],
    [Notifications], [Relational rows, immutable], [PostgreSQL, `notification` database], [Readable only by the recipient through the API; only notification's app role can write], [Eventually consistent, a few seconds behind the action (AISS-11)],
    [Sessions], [Key–value, Sa-Token session objects], [ElastiCache Valkey], [HTTP-only cookie; TLS and an AUTH token on the connection; 24-hour expiry; reachable only from the service group], [Fast and shared by all services, but one node: a restart signs everyone out (AISS-10)],
    [Events], [JSON envelope with string message attributes], [`sys_outbox` table, then SNS and SQS], [Encrypted topic and queues; IAM policies limit publish and receive to the producing and consuming task roles; payloads carry IDs, not personal data], [At-least-once delivery, so consumers must be idempotent (`sys_consumed_message`)],
  ),
  caption: [Data classification per data group (template format).],
) <tab-classification>
