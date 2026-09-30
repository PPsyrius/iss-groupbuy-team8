// SWE5001 Team 8 — Practice Project Proposal
// Build (from the repository root):
//   typst compile --root . docs/swe5001/project-proposal.typ "<output-dir>/SWE5001-Team8-Project-Proposal.pdf"

#import "@preview/fletcher:0.5.8" as fletcher: diagram, node, edge

#let accent = rgb("#0a6fa8")
#let muted = rgb("#5f6b78")
#let hdr = rgb("#dde8f2")
#let svc = rgb("#e6f1fa")
#let plat = rgb("#e8f5ec")
#let ext = rgb("#f4f4f4")
#let data = rgb("#fdf3e3")

#set document(title: "Project Proposal: BlocBuy — A Scalable Community Group-Buying Platform", author: "SWE5001 Team 8")
#set page(
  paper: "a4",
  margin: (x: 2.2cm, top: 2.3cm, bottom: 2.3cm),
  footer: context {
    if counter(page).get().first() > 1 {
      set text(8pt, fill: muted)
      [BlocBuy — SWE5001 Project Proposal #h(1fr) Page #counter(page).display()]
    }
  },
)
#set text(font: ("Arial", "Helvetica"), size: 10.5pt, lang: "en")
#set par(justify: true, leading: 0.62em, spacing: 0.95em)
#set heading(numbering: "1.1")
#show heading.where(level: 1): it => {
  v(0.5em)
  set text(15pt, fill: accent)
  it
  v(0.25em)
}
#show heading.where(level: 2): it => {
  v(0.35em)
  set text(12pt, fill: accent)
  it
  v(0.1em)
}
#show heading.where(level: 3): it => {
  v(0.25em)
  set text(10.5pt)
  it
}
#show raw: set text(font: ("Menlo", "Courier New"), size: 0.88em)
#set list(indent: 0.6em, spacing: 0.6em)
#set enum(indent: 0.6em, spacing: 0.6em)

#set table(
  stroke: 0.5pt + rgb("#a7b4c2"),
  inset: (x: 5pt, y: 4.5pt),
  fill: (x, y) => if y == 0 { hdr },
  align: left + horizon,
)
#show table.cell.where(y: 0): set text(weight: "bold")
#show table: set text(8.8pt)
#show table: set par(justify: false, leading: 0.5em)
#show figure: set block(breakable: true)
#show figure.where(kind: table): set figure.caption(position: bottom)
#show figure.caption: set text(9pt, fill: muted, style: "italic")

// ---------------------------------------------------------------- Title page
#page(footer: none)[
  #v(1.2cm)
  #align(center, image("../nus-iss-logo.png", width: 7.5cm))
  #v(2.2cm)
  #align(center)[
    #text(24pt, weight: "bold", fill: accent)[Project Proposal]
    #v(0.3cm)
    #text(17pt, weight: "bold")[BlocBuy — A Scalable Community \ Group-Buying Platform]
    #v(0.9cm)
    #text(12pt)[Practice Module for Graduate Certificate in Architecting Scalable Systems]
    #v(0.2cm)
    #text(12pt)[SWE5001 (SE34FT) — Team 8]
  ]
  #v(2cm)
  #align(center)[
    #text(12pt, weight: "bold")[Members]
    #v(0.2cm)
    #set text(11pt)
    Lim Jiechao (limjiechao\@u.nus.edu) \
    Liu Jin (losgai\@u.nus.edu) \
    Panpakorn Siripanich (Pan) (panpakorn.s\@u.nus.edu) \
    Wang Ziang (Eric) (ziangw\@u.nus.edu) \
    Yu Qianshuo (Yorik) (qianshuoyu\@u.nus.edu)
  ]
]

#counter(page).update(2)
#outline(title: [Contents], depth: 2, indent: 1.2em)
#pagebreak()

// ---------------------------------------------------------------- 1
= Project Title
*BlocBuy — A Scalable Community Group-Buying Platform*

= Project Sponsor
Not applicable. The project is self-initiated by the team and is not company-sponsored.

= Project Members

#figure(
  table(
    columns: (1.4fr, 1.3fr),
    [Name], [Email],
    [Lim Jiechao], [limjiechao\@u.nus.edu],
    [Liu Jin], [losgai\@u.nus.edu],
    [Panpakorn Siripanich (Pan)], [panpakorn.s\@u.nus.edu],
    [Wang Ziang (Eric)], [ziangw\@u.nus.edu],
    [Yu Qianshuo (Yorik)], [qianshuoyu\@u.nus.edu],
  ),
  caption: [Project members],
)

// ---------------------------------------------------------------- 4
= Overview

== Context and Business Problem
Group buying lets consumers obtain lower prices by pooling demand. It also gives small and local sellers access to a larger customer base without heavy marketing spend. In practice, most community group buys are still coordinated through chat groups and spreadsheets. Buyers struggle to discover relevant deals and cannot see deadlines, minimum quantities or progress clearly. Organisers carry repeated manual work: collecting payments, chasing participants and refunding them when a deal fails. Sellers have no reliable channel through which to publish team-priced offers.

BlocBuy addresses this with a platform on which:

- sellers publish products with *team pricing*: the more members, the lower the price;
- buyers pay to *initiate* a team or *join* an existing one;
- the platform settles payments, forms teams and resolves each team automatically, either succeeding when the target size is reached or expiring with automatic refunds at the deadline;
- participants are kept informed at every step.

== Platform Thinking
BlocBuy is designed as a two-sided platform rather than a single online shop. Its core interaction is the *formation of a team at a team price*: a producer publishes a team-priced offer, and consumers pay to join. Three platform properties follow from this.

- *Network effects.* Every buyer who joins a team lowers the price for the other members, so each additional consumer increases the value of the platform for everyone else.
- *Consumers who also produce.* A buyer who initiates a team, or shares an invitation link, brings new members in and so acts as a producer.
- *Reusable common services.* Identity, payment settlement and notification are capabilities that other group-buying scenarios can reuse, for example event tickets, bulk subscriptions or neighbourhood produce boxes, without rebuilding them.

== Relationship to Prior Work
The functional baseline of BlocBuy (use cases UC1–UC9, listed in @scope-use-cases) was built as a COLA modular monolith for the SWE5006 practice module. The SWE5001 project *extends the same codebase on the same trunk* rather than forking it. The microservice deployment is added alongside the monolith and selected at deployment time, so every business feature is written once and runs in both shapes. The effort in this proposal targets the SWE5001 learning outcomes: platform architecture, microservice decomposition, scalability, cloud-native design on AWS, infrastructure as code and DevSecOps. It does not re-implement the business features.

// ---------------------------------------------------------------- 5
= General Architecture

== Architectural Drivers

#figure(
  table(
    columns: (1fr, 3fr),
    [Quality attribute], [Driver and architectural response],
    [Scalability], [Browse traffic and bursty webhook and event traffic must scale independently. Stateless services behind a load balancer use target-tracking auto-scaling; asynchronous work is decoupled through queues.],
    [Consistency], [Payment settlement, team admission, stock consumption and order creation must commit atomically. Service boundaries follow transactional consistency boundaries, so no business write spans two services.],
    [Availability], [A failed task must not cause failed requests. ECS replaces unhealthy tasks behind ALB health checks, and the deployment circuit breaker rolls back failed releases automatically.],
    [Security], [No long-lived cloud credentials, least-privilege IAM, private subnets, origin-restricted load balancer, verified Stripe webhooks, RBAC on every service.],
    [Cost efficiency], [Demo-scale budget. Managed serverless containers and a disposable runtime environment that is created on demand and destroyed after use.],
    [Modifiability], [New services and new business seeds reuse a shared platform starter library and a Terraform "golden path" module.],
  ),
  caption: [Architectural drivers],
)

== Logical Architecture
The platform is decomposed into three services: two *platform services* that any business seed can reuse, and one *business application service* that owns the group-buying transaction.

#figure(
  diagram(
    spacing: (24mm, 10mm),
    node-stroke: 0.6pt + rgb("#7a8a9a"),
    node-corner-radius: 3pt,
    node-inset: 6pt,
    edge-stroke: 0.7pt + rgb("#4a5a6a"),
    // actors
    node((0, 0), [*Consumers* \ buyers joining teams], fill: ext, name: <cons>),
    node((2, 0), [*Producers* \ sellers / admins, \ team initiators, sharers], fill: ext, name: <prod>),
    // client + edge
    node((1, 1), [*Web SPA* (React) \ + API edge routing], fill: ext, name: <spa>),
    // services
    node((0, 2.4), [*identity-svc* \ _platform service_ \ Google OAuth, users, RBAC], fill: plat, name: <id>),
    node((1, 2.4), [*commerce-svc* \ _business application_ \ catalog · groupbuy \ order · payment], fill: svc, name: <com>),
    node((2, 2.4), [*notification-svc* \ _platform service_ \ event-driven notices], fill: plat, name: <noti>),
    // bus
    node((1.5, 3.7), [*Event bus* \ outbox → SNS topics → SQS queues \ #text(8pt)[TeamSucceeded · TeamExpired · PaymentSucceeded]], fill: data, name: <bus>),
    // externals
    node((0, 3.7), [Google OAuth], fill: ext, name: <google>),
    node((0.6, 4.7), [Stripe (Checkout, webhooks)], fill: ext, name: <stripe>),
    edge(<cons>, <spa>, "->"),
    edge(<prod>, <spa>, "->"),
    edge(<spa>, <id>, "->", label: [auth, users], label-side: right),
    edge(<spa>, <com>, "->", label: [browse, buy], label-side: left),
    edge(<spa>, <noti>, "->", label: [notices], label-side: left),
    edge(<com>, <id>, "->", label: text(8pt)[active-user \ check], label-side: right),
    edge(<com>, <bus>, "->", label: text(8pt)[domain events], label-side: left),
    edge(<bus>, <noti>, "->"),
    edge(<id>, <google>, "<->"),
    edge(<com>, <stripe>, "<->"),
  ),
  caption: [Logical architecture: platform services and business application],
)

#figure(
  table(
    columns: (1fr, 1.5fr, 2fr),
    [Service], [Bounded contexts], [Responsibility],
    [`identity-svc`], [identity], [Google OAuth login, user profiles, roles and permissions. Issues the session token shared by all services.],
    [`commerce-svc`], [catalog, groupbuy, order, payment], [Product listings and team pricing, team initiation and admission, purchase intents and orders, Stripe settlement and refunds. Publishes domain events.],
    [`notification-svc`], [notification], [Consumes domain events and maintains each user's notifications.],
  ),
  caption: [Services and responsibilities],
)

=== Decomposition rationale
The decomposition follows *transactional consistency boundaries*, not one service per domain noun.

- *One transaction for settlement.* In the current code, a verified Stripe webhook settles the payment, consumes product stock, admits the buyer into a team and writes the order in a single database transaction.
- *One transaction for expiry.* Team expiry cancels orders and records refund obligations in the same transaction.
- *So these four contexts stay together.* Separating catalog, groupbuy, order and payment would require sagas and compensation, which the effort budget does not allow; they remain in `commerce-svc`.
- *Identity has a narrow interface.* Other contexts use identity through one active-user check only.
- *Notification is independent.* It has no inbound dependencies.
- *No cross-context SQL joins.* Both can therefore be extracted with clean data ownership.

We evaluated a five-service split (identity, catalog, trade, payment, notification) and rejected it on effort grounds. It remains a documented evolution path: payment can be extracted later through the same outbox mechanism plus a saga.

=== Integration
- *Synchronous.* Service-to-service calls use ECS Service Connect, which provides service discovery, retries and per-service metrics. The only synchronous dependency, `commerce-svc` → `identity-svc`, has a short timeout that fails safe.
- *Asynchronous.* Domain events are written to a transactional outbox inside the business transaction, published to Amazon SNS topics and delivered to per-consumer Amazon SQS queues with dead-letter queues. Consumers are idempotent, keyed by event ID.
- *Authentication.* The session store is a shared ElastiCache (Valkey) instance. Every service validates the same token; roles are stored in the session, so authorisation needs no per-request call to `identity-svc`.

== Physical Architecture
The solution is deployed on AWS in `ap-southeast-1`.

#figure(
  diagram(
    spacing: (10mm, 9mm),
    node-stroke: 0.6pt + rgb("#7a8a9a"),
    node-corner-radius: 3pt,
    node-inset: 5pt,
    edge-stroke: 0.7pt + rgb("#4a5a6a"),
    node((1, 0), [Users (browser)], fill: ext, name: <user>),
    node((1, 1), [*Amazon CloudFront*], fill: svc, name: <cf>),
    node((0, 1), [S3 \ SPA bucket], fill: data, name: <s3>),
    node((1, 2), [*Application Load Balancer* \ path routing, origin secret], fill: svc, name: <alb>),
    node((0, 3.2), [identity-svc \ Fargate tasks], fill: plat, name: <fid>),
    node((1, 3.2), [commerce-svc \ Fargate tasks], fill: svc, name: <fcom>),
    node((2, 3.2), [notification-svc \ Fargate tasks], fill: plat, name: <fnoti>),
    node((0.5, 4.5), [RDS PostgreSQL \ 3 databases], fill: data, name: <rds>),
    node((1.5, 4.5), [ElastiCache \ Valkey], fill: data, name: <cache>),
    node((2.5, 4.5), [SNS → SQS \ + DLQ], fill: data, name: <mq>),
    node((3, 2), [fck-nat \ (egress)], fill: ext, name: <nat>),
    node((3, 1), [Stripe · Google], fill: ext, name: <ext>),
    node(enclose: (<fid>, <fcom>, <fnoti>, <rds>, <cache>), [], stroke: (paint: rgb("#9aa8b6"), dash: "dashed"), fill: none, inset: 9pt, name: <priv>),
    edge(<user>, <cf>, "->", label: [HTTPS], label-side: left),
    edge(<cf>, <s3>, "->", label: [/\*], label-side: left),
    edge(<cf>, <alb>, "->", label: [/api/\*], label-side: left),
    edge(<alb>, <fid>, "->"),
    edge(<alb>, <fcom>, "->"),
    edge(<alb>, <fnoti>, "->"),
    edge(<fcom>, <rds>, "-"),
    edge(<fcom>, <cache>, "-"),
    edge(<fcom>, <mq>, "->"),
    edge(<mq>, <fnoti>, "->", bend: -30deg),
    edge(<fnoti>, <nat>, "->"),
    edge(<nat>, <ext>, "<->"),
  ),
  caption: [Physical architecture. The dashed box is the private subnets of a two-AZ VPC.],
)

#figure(
  table(
    columns: (1fr, 1.4fr, 2fr),
    [Concern], [Choice], [Reason],
    [Compute], [Amazon ECS on Fargate, ARM64 (Graviton), 0.5 vCPU / 1 GB tasks], [Serverless containers with native Service Auto Scaling, no control-plane fee (unlike EKS), about 20% cheaper than x86.],
    [Scale-out capacity], [Fargate Spot above the minimum task count], [Cheap burst capacity without risking the baseline.],
    [Entry], [CloudFront → ALB with origin-secret header; ALB default action 403], [TLS at the edge; the ALB is unusable except through CloudFront.],
    [Network], [Two-AZ VPC; tasks, database and cache in private subnets; fck-nat instance for egress], [Private workloads at about one-tenth the cost of a managed NAT Gateway.],
    [Persistence], [RDS PostgreSQL 18 (one instance, one database and role per service)], [Logical data ownership per service at demo cost. Separate instances are the scale-out path.],
    [Cache and session], [ElastiCache for Valkey], [Shared, Redis-compatible session store for stateless services.],
    [Messaging], [SNS topics, SQS queues, dead-letter queues], [Managed, pay-per-use asynchronous delivery; the queue backlog drives consumer scaling.],
    [Secrets], [SSM Parameter Store SecureString, injected into ECS tasks], [No secrets in images or repositories.],
    [Observability], [CloudWatch Logs, metrics, dashboard and alarms; trace ID propagated across services], [One view for the scalability demo and for troubleshooting.],
  ),
  caption: [Physical design decisions],
)

== Persistence Design
Each service owns its tables and its Flyway migrations:

- `identity-svc`: the `sys_user*`, `sys_role`, `sys_permission` and `rel_*` tables;
- `notification-svc`: `biz_notification`;
- `commerce-svc`: all other business tables (products, team price policies and quotes, group deals and members, purchase intents, orders, payments, refunds, and webhook and refund logs).

Services never read another service's database; they cross boundaries only through APIs and events. Outbox and consumed-message tables live in the database of the producing and consuming service respectively.

// ---------------------------------------------------------------- 6
= Scope of Work

== Platform Components

#figure(
  table(
    columns: (1fr, 3fr),
    [Component], [In BlocBuy],
    [Seed], [Curated, team-priced product offers published by the platform operator. They attract the first buyers before independent sellers join.],
    [Producers], [Sellers and administrators who publish and manage listings (UC9). Buyers who initiate a team (UC3). Sharers whose invitation links bring new members (UC7).],
    [Consumers], [Buyers who browse offers (UC1) and pay to join teams (UC4), then track their orders and teams (UC8).],
    [Core interaction], [Forming a team at a team price, from payment through admission to automatic success or expiry with refunds (UC5, UC6).],
    [Common services], [Identity (`identity-svc`), notification (`notification-svc`), payment settlement and the shared platform starter library.],
    [Future seeds], [Other group-buying verticals (event tickets, bulk subscriptions, neighbourhood produce) reusing identity, notification and settlement.],
  ),
  caption: [Platform components],
)

== Use Cases in Scope <scope-use-cases>
The business use cases below are carried over from the functional baseline and are assigned to their owning service. The SWE5001 work makes them run on the new architecture and adds event-driven notifications.

#figure(
  table(
    columns: (0.5fr, 2.2fr, 1.1fr, 2.2fr),
    [ID], [Use case], [Owning service], [SWE5001 change],
    [UC1], [Browse and search team-priced products], [commerce], [Primary target for HTTP auto-scaling demonstration],
    [UC2], [Sign in with Google], [identity], [Extracted service; shared session across services],
    [UC3], [Initiate a team by paying for the first membership], [commerce], [Remote active-user check],
    [UC4], [Join an existing team by paying], [commerce], [Remote active-user check; publishes `PaymentSucceeded`],
    [UC5], [Resolve a team to succeeded or expired], [commerce], [Publishes `TeamSucceeded` / `TeamExpired`],
    [UC6], [Refund members of an expired team], [commerce], [Unchanged transaction; refund notice via events],
    [UC7], [Share an invitation and attribute referrals], [commerce], [Unchanged],
    [UC8], [View my orders and my teams], [commerce], [Unchanged],
    [UC9], [Administer product listings], [commerce], [Remote active-user check],
    [New], [Receive notifications for team and payment events], [notification], [New event consumers with idempotent processing],
  ),
  caption: [Use cases and owning services],
)

== Demonstrating Scalability
The following demonstrations are scripted and repeatable. Throughput and latency targets will be revised against a single-task baseline measured in the first development week.

+ *HTTP auto-scaling.* A k6 script ramps load on the public product browse API (UC1). `commerce-svc` scales from one task to up to six through target tracking on requests per target. The CloudWatch dashboard shows task count, throughput and p95 latency recovering after scale-out. The target is near-linear throughput growth from one to four tasks.
+ *Queue-driven scaling.* A burst of team expiries floods the notification queue. `notification-svc` scales on backlog per task and drains the queue, showing that producers and consumers scale independently.
+ *Self-healing.* A running task is stopped manually; ECS replaces it and the load balancer routes around it without failed requests.
+ *Safe deployment.* A deliberately broken image is deployed; the ECS deployment circuit breaker rolls back automatically.

== Cloud-Native Design
- *Managed services throughout.* Fargate, RDS, ElastiCache, SNS, SQS, CloudFront, CloudWatch and SSM Parameter Store; no self-managed servers apart from the fck-nat egress instance.
- *Twelve-factor services.* Services are stateless, configured through the environment and secrets store, log to stdout, and are disposable with fast replacement.
- *Disposable environment.* The runtime environment is created before use and destroyed afterwards, with deterministic seed data. It runs at about US\$0.20 per hour and costs about US\$1–3 per month while idle, against about US\$125 per month for running continuously. Rebuilding from zero is itself demonstrated.

== DevOps and DevSecOps
- *Source and artifacts.* One repository and one trunk (`dev`), shared with SWE5006. SWE5001 work lands through short-lived topic branches and pull-request review. The monolith and the three service images are built from the same commit, pushed to Amazon ECR and deployed by immutable digest.
- *Infrastructure as code.* Terraform only, in three layered stacks:
  - `foundation` (persistent): state, GitHub OIDC roles, ECR, parameters, budgets;
  - `edge` (persistent): CloudFront, S3;
  - `runtime` (disposable): network, ECS, data stores, messaging, observability.
- *Pipeline.*
  - On pull requests: path-filtered per-service build and test, SonarCloud quality gate, secret scanning, Trivy image scanning, `terraform plan` with checkov policy checks, and the plan posted to the pull request.
  - After merge: the image is published and the ECS rolling deployment runs with a circuit breaker.
  - Environment workflows: `env-up`, `env-down`, a nightly safety teardown and a periodic full rebuild cycle.
- *Security controls.* GitHub-to-AWS OIDC (no stored AWS keys), least-privilege task roles, security groups that reference each other rather than CIDR ranges, private subnets, an origin-secret header on the ALB, verified Stripe webhook signatures, and Sa-Token RBAC on every service.

== Platform Engineering
- *Platform starter library.* `blocbuy-platform-starter` packages the response envelope, error codes, authentication configuration, trace propagation, transactional outbox and idempotent consumption. A new service inherits these capabilities by adding one dependency.
- *Golden-path Terraform module.* `ecs-service` bundles the task definition, service, routing rule, auto-scaling, logs and Service Connect. A new service is one entry in a map, which demonstrates the effort reduction the platform offers to future seeds.

== Out of Scope
- Extracting payment or catalog into separate services (documented as the evolution path).
- Multi-region deployment and database read replicas.
- A native mobile application.
- A production launch with real payments; Stripe runs in test mode throughout.

// ---------------------------------------------------------------- 7
= Effort Estimates
The guideline is about ten person-days per member, 50 person-days in total. The estimate excludes the business features inherited from the functional baseline.

#figure(
  table(
    columns: (0.45fr, 3fr, 0.7fr),
    align: (left, left, right),
    [WBS], [Task], [Person-days],
    [1], [*Planning and reporting*], [*3.5*],
    [1.1], [Proposal and architecture baseline], [2.5],
    [1.2], [Fortnightly progress reports], [1],
    [2], [*Platform foundation*], [*6.5*],
    [2.1], [`blocbuy-platform-starter` shared library], [3],
    [2.2], [Transactional outbox, SNS/SQS publishing, idempotent consumption], [3.5],
    [3], [*Service decomposition*], [*13.5*],
    [3.1], [Extract `notification-svc` and add event consumers], [3],
    [3.2], [Extract `identity-svc`; HTTP active-user adapter; shared session], [4],
    [3.3], [Package `commerce-svc` and wire event publishing], [2],
    [3.4], [Split databases and Flyway migrations per service], [3],
    [3.5], [Demo seed data and bulk load-test data], [1.5],
    [4], [*Infrastructure as code*], [*11*],
    [4.1], [`foundation` stack: state, OIDC roles, ECR, parameters, budgets], [2],
    [4.2], [Network, ECS cluster and `ecs-service` golden-path module], [4],
    [4.3], [RDS, ElastiCache and messaging modules], [2],
    [4.4], [`edge` stack: CloudFront and S3 rewiring], [1],
    [4.5], [Auto-scaling policies, dashboard and alarms], [2],
    [5], [*DevSecOps pipeline*], [*6*],
    [5.1], [Per-service CI: build, test, SonarCloud, Trivy], [2],
    [5.2], [Terraform plan and apply pipeline with checkov], [2],
    [5.3], [ECS deployment with circuit breaker; environment up/down/cycle workflows], [2],
    [6], [*Verification*], [*4.5*],
    [6.1], [k6 load-test scripts and measured runs], [2],
    [6.2], [Resilience drills: task termination, failed-deployment rollback], [1],
    [6.3], [End-to-end regression on the deployed environment], [1.5],
    [7], [*Presentation and report*], [*5*],
    [7.1], [Slides, demo script and rehearsal], [2.5],
    [7.2], [Project report], [2.5],
    [], [*Total*], [*50*],
  ),
  caption: [Work breakdown structure and effort estimates],
)

#figure(
  table(
    columns: (1.1fr, 3fr),
    [Period], [Milestone],
    [28 Sep – 4 Oct], [Proposal submitted. `foundation` stack, network and ECS cluster. The unchanged application runs on Fargate as the first strangler step.],
    [5 – 11 Oct], [Platform starter library. Outbox and messaging. `notification-svc` extracted with event consumers.],
    [12 – 18 Oct], [`identity-svc` extracted. Databases split. Load-balancer routing and auto-scaling policies in place.],
    [19 – 22 Oct], [k6 load tests, dashboard, resilience drills, full rehearsal.],
    [23 Oct], [Project presentation (Team 8, 4.15 pm).],
    [by 16 Nov], [Project report submitted; all cloud resources destroyed.],
  ),
  caption: [Milestones],
)

// ---------------------------------------------------------------- 8
= Risks and Assumptions

#figure(
  table(
    columns: (1.6fr, 2.4fr),
    [Risk or assumption], [Mitigation],
    [Environment creation fails on presentation day.], [A periodic full rebuild cycle in CI; the environment is created on the morning of the presentation; a recorded fallback of the scaling demonstration.],
    [The synchronous identity check inside the payment webhook transaction adds a failure mode.], [A short timeout; a timeout fails the webhook with a server error instead of rejecting the buyer, so Stripe retries it, and settlement is idempotent.],
    [The cloud budget is exceeded.], [The disposable runtime environment, AWS Budgets alerts and a nightly automatic teardown.],
    [`commerce-svc` is perceived as too coarse.], [The consistency-boundary rationale is recorded as an architecture decision, with the payment-extraction evolution path.],
    [The functional baseline changes during the project.], [Both courses share one trunk, so baseline changes reach the services without a sync step. CI builds the monolith and every service on each pull request, and architecture tests reject new cross-service calls that bypass ports or events.],
  ),
  caption: [Key risks and mitigations],
)
