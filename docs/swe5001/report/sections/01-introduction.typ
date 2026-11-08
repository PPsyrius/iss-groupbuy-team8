#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

= Introduction

== Background

BlocBuy is a community group-buying platform: buyers form a team at a team price, pay to start or join it, and the platform settles the payments and either completes the team or refunds every member when it expires. Team 8 built these functions (use cases UC1–UC9) for SWE5006 as a modular monolith. For SWE5001 the team re-architected the same code into a platform of three services on Amazon Web Services (AWS), and kept the monolith as a fallback topology (AD-01, §3.1.1).

*Starting point.* On 29 September 2026 BlocBuy was one Java 21 and Spring Boot 4 application in the Alibaba COLA layering, with six bounded contexts in one deployable unit. It ran under Docker Compose on one Amazon EC2 host with PostgreSQL 18 and Redis 8, and the SWE5006 deployment still runs that way. #src("docs/swe5001/microservice-refactoring-plan.md")[Refactoring plan, §1].

*What SWE5001 added.* The same commit now also assembles into `identity-svc`, `commerce-svc` and `notification-svc`. They run on Amazon ECS Fargate, each with its own database, and exchange events through a transactional outbox, one Amazon SNS topic and one Amazon SQS queue per consumer. Terraform creates the runtime for each working session and destroys it afterwards. The logical design is in §3.1 and the physical design in §3.2.

*Status on 8 November 2026.* The three-service release was promoted to `prod`, built from zero on AWS and smoke-tested through CloudFront on 17, 19 and 20 October (§2.2). Since the presentation on 21 October only the two permanent stacks, `foundation` and `edge`, exist; the runtime is destroyed. After the presentation the team answered the assessors' remarks (§6.1). The one infrastructure change among them, separate data subnets (AD-15), is configured and tested offline but not yet applied on AWS, and several availability drills have local results only (AISS-13). The evidence status of every capability is in §2.2, and every open limitation is in §3.4.

== Business Needs

Group buying works only if enough people join before the deadline. Today that coordination happens in chat groups and spreadsheets, so it is slow, opaque and manual for everyone involved (@tbl-pain-points).

#figure(
  table(
    columns: (1fr, 2.2fr, 2.2fr),
    table.header([Who], [Pain point (briefing Q1)], [What BlocBuy does instead]),
    [Buyers], [Cannot see a deal's deadline, minimum team size or progress; joining means messaging an organiser], [Team-priced listings show the team price, the target team size and the deadline; a buyer starts or joins a team by paying (UC3, UC4)],
    [Organisers], [Collect payments, chase participants and refund by hand when a deal falls through], [The platform admits members on payment, completes the team at its target size and refunds every member automatically on expiry (UC5, UC6)],
    [Sellers], [Have no channel for offers priced for a group of buyers], [Listings carry team price rules (UC9), and members who share invitation links bring in more buyers (UC7)],
  ),
  kind: table,
  caption: [Pain points and BlocBuy's answer. Sources: #src("docs/swe5001/project-proposal.typ")[proposal, §4.1], #src("docs/swe5001/presentation/src/slides-main.js")[presentation slide 2].],
) <tbl-pain-points>

The case for a _platform_ rather than one application rests on three properties. First, the team price, which is lower than the solo price, holds only if the team reaches its target size before the deadline, so every member wants others to join and demand arrives in bursts as deadlines approach. Second, consumers also produce: the buyer who starts a team, or shares an invitation, brings in the next buyers. Third, identity, payment settlement and notification are needed by any future group-buying vertical, not only by this one.

These properties give the architecture its drivers. @tbl-drivers only names each driver and points to the section that answers it.

#figure(
  table(
    columns: (1fr, 2.6fr, 1fr),
    table.header([Driver], [What it demands], [Answered in]),
    [Scalability], [Browsing and payment or event bursts scale independently of each other], [§3.2, §4.1],
    [Consistency], [Payment, admission and the order settle in one transaction, and an expiry records its refund obligations in the same transaction; nobody pays for a seat that does not exist], [§3.1.4, §3.2.3, §3.2.4],
    [Availability], [A failed task or a broker outage loses no committed payment or event], [§4.2],
    [Security], [No stored cloud keys, least privilege, and a data tier unreachable from outside], [§3.2, §4.3],
    [Cost], [A demonstration-scale budget: the runtime exists only while it is used], [§3.2.1 (AD-12), §6.3],
    [Modifiability], [A new service, consumer or seed reuses the platform instead of starting again], [§4.4],
  ),
  kind: table,
  caption: [Architectural drivers. Source: #src("docs/swe5001/project-proposal.typ")[proposal, Table 2].],
) <tbl-drivers>

== Stakeholders

The project was self-initiated and has no company sponsor. Its stakeholders are the people who use the platform and the people who build, run and assess it (@tbl-stakeholders).

#figure(
  table(
    columns: (0.8fr, 1.4fr, 2.6fr),
    table.header([Group], [Stakeholder], [Interest in the platform]),
    table.cell(rowspan: 4)[Business], [Buyer as team leader], [Starts a team by paying and shares it; wants the team to fill before the deadline],
    [Buyer as joiner], [Joins by paying; wants a fair price, a clear deadline and an automatic refund if the team fails],
    [Seller or platform administrator], [Publishes listings and team price rules; wants teams to fill, and a channel that grows with demand],
    [Future vertical owner], [Wants to launch another group-buying offer (event tickets, bulk subscriptions, neighbourhood produce) without rebuilding sign-in, payment or notification],
    table.cell(rowspan: 5)[IT], [Team 8 as platform engineers], [Build and operate the services, the pipeline and the AWS stacks; each member's area is in §2.3],
    [AWS organisation administrator (Lim Jiechao)], [Owns the AWS Organization, its service control policies (SCPs) and IAM Identity Center; teammates work only in the member account. #src("docs/deploy/aws-account-governance/README.md")[Governance record]],
    [External providers], [Google (sign-in) and Stripe (payments, in test mode), each behind an anti-corruption layer (§3.1.4)],
    [Future service teams], [Consume the published events and the public API without changing the producers (§4.4)],
    [Module lecturers (Heng Boon Kui, Lee Chiang)], [Assess the architecture, the design and the delivered system; their remarks of 21 October are answered in §6.1],
  ),
  kind: table,
  caption: [Business and IT stakeholders.],
) <tbl-stakeholders>

The briefing's first success criterion asks for the seed, the producers and the consumers of the platform. @tbl-platform-roles names them, and @fig-d1-context places them around the system.

#figure(
  table(
    columns: (1fr, 3fr),
    table.header([Platform role], [In BlocBuy]),
    [Seed], [Curated, team-priced product offers published by the platform operator to attract the first buyers before independent sellers join],
    [Producers], [Sellers and administrators who publish and manage listings (UC9); buyers who start a team (UC3); sharers whose invitation links bring new members (UC7)],
    [Consumers], [Buyers who browse offers (UC1), pay to join teams (UC4), track their orders and teams (UC8) and receive notifications],
    [Core interaction], [Forming a team at a team price: payment, admission, then automatic success or expiry with refunds (UC5, UC6)],
    [Common services], [Sign-in and roles (`identity-svc`), notification (`notification-svc`), payment settlement, and the shared platform modules (§4.4)],
    [Future seeds], [Other group-buying verticals that reuse identity, notification and settlement (§6.2)],
  ),
  kind: table,
  caption: [Seed, producers and consumers. Source: #src("docs/swe5001/project-proposal.typ")[proposal, Table 5].],
) <tbl-platform-roles>

#figure(
  image("../diagrams/d1-system-context.svg", width: 100%),
  caption: [System context view (C4 model, level 1). People are grouped by platform role. At run time BlocBuy depends only on Google sign-in, Stripe and AWS; GitHub reaches AWS only by assuming its OIDC roles, for plans, environment sessions and releases (AD-17).],
) <fig-d1-context>

== Project Scope

SWE5001 changed how BlocBuy is built and run, not what it does. The business functions UC1–UC9 already existed. The project made them run in a second, distributed topology, added event-driven notifications, and built the cloud platform, the pipeline and the evidence around them. The work ran in five phases (@tbl-phases); their dates are in §2.3.

#figure(
  table(
    columns: (1fr, 3fr),
    table.header([Phase], [Delivered]),
    [0. Study and proof of concept], [The architecture options study (viability, cost, Kubernetes, EventStorming) and a throw-away proof of concept in a separate AWS account],
    [A. Strangler step 0], [The permanent `foundation` and `edge` stacks, the disposable `runtime` stack, the release pipeline, and the unchanged monolith running on ECS],
    [B. Split], [Three service assemblies from one codebase, a database per service, the remote identity check, the outbox, SNS/SQS messaging and the idempotent notification consumer],
    [C. AWS evidence], [The load baseline, auto-scaling, three-service promotions and the 21 October presentation],
    [D. Remarks and report], [Answers to the assessors' remarks (diagrams and data subnets) and this report],
  ),
  kind: table,
  caption: [Phases of the project.],
) <tbl-phases>

=== Functionality in scope

Every use case keeps its SWE5006 behaviour in the monolith and has one owning service in the distributed topology (@tbl-scope-uc, @fig-d2-use-cases).

#figure(
  table(
    columns: (0.45fr, 2.1fr, 0.9fr, 2.2fr),
    table.header([ID], [Use case], [Owning service], [SWE5001 change]),
    [UC1], [Browse and search team-priced products], [commerce], [Primary target for HTTP auto-scaling],
    [UC2], [Sign in with Google], [identity], [Separate service; one session shared by all services],
    [UC3], [Initiate a team by paying for the first membership], [commerce], [Remote active-user check],
    [UC4], [Join an existing team by paying], [commerce], [Remote active-user check; publishes `PaymentSucceeded`],
    [UC5], [Resolve a team to succeeded or expired], [commerce], [Publishes `TeamSucceeded` and `TeamExpired`],
    [UC6], [Refund members of an expired team], [commerce], [Same transaction; refund notice through events],
    [UC7], [Share an invitation and attribute referrals], [commerce], [Unchanged],
    [UC8], [View my orders and my teams], [commerce], [Unchanged],
    [UC9], [Administer product listings], [commerce], [Remote active-user check],
    [New], [Receive notifications for team and payment events], [notification], [New event consumer with idempotent processing],
  ),
  kind: table,
  caption: [Use cases in scope and their owning services. Source: #src("docs/swe5001/project-proposal.typ")[proposal, Table 6].],
) <tbl-scope-uc>

#figure(
  image("../diagrams/d2-use-cases.svg", width: 100%),
  caption: [Use case view (UML use case diagram). Use cases are grouped by the service that owns them; UC3–UC6 form the group-buy core that the detailed design in §3.2.4 follows.],
) <fig-d2-use-cases>

Besides the use cases, the scope covered the platform itself:

- *Platform capabilities:* the three service assemblies, the shared modules and the golden-path `ecs-service` Terraform module (§4.4); the outbox and messaging (§3.2.3); three Terraform stacks (§3.2.2); the promotion pipeline (§5).
- *Four scripted demonstrations* promised in the proposal: HTTP auto-scaling, queue-driven scaling, self-healing and safe deployment rollback. §2.2 states which of them ran on AWS, which ran locally, and which did not run.

=== Functionality out of scope

The proposal excluded six items (#src("docs/swe5001/project-proposal.typ")[proposal, §6.7]):

- Extracting payment or the catalogue into separate services; this is the documented evolution path (AISS-02).
- Moving service code into separate source trees, deferred until SWE5006 feature work ends (AD-18).
- Replacing or changing the SWE5006 monolith deployment.
- Multi-region deployment and database read replicas.
- A native mobile application.
- A production launch with real payments; Stripe runs in test mode throughout.

During the project the team also left out email and push notifications; notifications are in-app only.

*Architected but not built.* Some designs exist only on paper. @tbl-not-built lists them, so that no later section is read as a claim that they run.

#figure(
  table(
    columns: (1.4fr, 2.4fr, 0.9fr),
    table.header([Designed], [Status], [Where]),
    [Payment as its own service, with a saga for fulfilment and refund], [Designed; payment shares commerce's settlement transaction today], [AISS-02, §4.4],
    [API gateway with partner keys, quotas and rate limits], [Designed; external partners have no entry point], [§3.1.4.6, AISS-04],
    [Service-to-service token or mutual TLS on internal calls], [Designed; internal endpoints rely on network rules], [AISS-04],
    [Multi-AZ RDS, a Valkey replica and a NAT instance per zone], [Designed for production; one of each today], [AISS-01],
    [Stock reservation], [Studied; not carried into the built design], [—],
    [Distributed tracing with AWS Distro for OpenTelemetry and X-Ray], [Studied; only a trace-ID header is propagated], [§4.4],
    [Analytics pipeline (SNS → Firehose → S3 → Athena)], [Studied; operational analytics only], [§6.5],
    [AWS WAF on the SWE5001 distribution], [Cut on 11 October to stay within the effort guideline], [§4.3],
  ),
  kind: table,
  caption: [Architected but not built. Sources: #src("docs/swe5001/report/registers.md")[AD and AISS registers]\; #src("docs/swe5001/architecture-options-study/README.md")[architecture options study].],
) <tbl-not-built>
