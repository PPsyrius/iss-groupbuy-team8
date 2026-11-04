#import "../lib.typ": *

// AD and AISS numbers come from ../registers.md. Replace each short description with
// context, options, choice, trade-off and consequence (AGENTS.md writing rule 3).
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  [Identifier], [Description],
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

= Solution Overview

== Logical Architecture & Design
#owner([Wang Ziang (Eric)], closes: "F8 (logical), F12")
#prompt[Describe the architecturally significant parts (or elements) of the solution and the design model showing its decomposition into subsystems using logical detail deployment diagram and other diagrams as necessary.

The individual elements of the solution must be clearly defined and assigned to the respective providers. An element (or subset of multiple elements) may be allocated to a Vendor.

A solution element is any part of the architecture of the overall solution, e.g., a COTS product, a custom-built software module, a data repository, a network device, etc. Each element can utilise the services provided by other elements and provide services of its own.

The logical view describes how elements participate in the solution. It includes static and dynamic relationships and interactions between elements. The documentation typically includes a number of diagrams expressing the different kinds of relationships — for example, dependency relationships, usage relationships, interaction relationships, etc.]
#todo(
  [Element table: element, role, provider (custom, COTS, SaaS: Google, Stripe).],
  [Logical view is technology-neutral; products appear only in 3.2 (AGENTS.md logical view rules).],
)

=== Key Architectural Decisions
#prompt[Provide a summary of the significant decisions made in arriving at the architecture of the solution that are related and/or reflected in the logical view. Documented decisions should focus on trade-off choices and rationale.]
The key architectural decisions taken are as follows:
#ad-table(
  ("AD-01", [Strangler migration to three services; the monolith stays as fallback (ADR 0008). _Trade-off to write._]),
  ("AD-02", [Split where no transaction crosses: identity, commerce, notification (ADR 0001, 0002). _Trade-off to write._]),
  ("AD-03", [One database and one role per service; ownership enforced by grants and architecture tests. _Trade-off to write._]),
  ("AD-04", [Transactional outbox → broker → queues with dead-letter queues; idempotent consumers (ADR 0007). _Trade-off to write._]),
  ("AD-05", [One shared login session with roles inside it. _Trade-off to write._]),
  ("AD-06", [One synchronous call, commerce → identity, failing closed. _Trade-off to write._]),
)

=== Tiers and Layers
#prompt[Describe the tiers and layers of the solution. E.g. Presentation layer, Business Logic layer, Data Access layer, Web Server tier and Integration tier. Adapt the section if you organized the solution using a different method (not tiered or layered approach).]
#todo(
  [Tiers: presentation, application, integration, data.],
  [Layers inside a service: COLA adapter → application → domain → infrastructure; domain depends on nothing.],
)

=== Nodes and Subsystems
#prompt[Describe the architecturally significant parts (or elements) of the solution showing the decomposition into subsystems.]
#diagram-slot("D3", [Logical view (UML component diagram). Provided and required interfaces per subsystem; one synchronous call, everything else by published events.])

=== Platform Design
#owner([Wang Ziang (Eric)], closes: "F3, F4, F9", diagrams: "D4, D7, D8")
#prompt[Where relevant, describe the elements of Domain Driven Design like domain entities, bounded contexts, aggregates, domain events, etc. In particular, describe how the platform is designed to interact with the producers and consumers.]
#todo(
  [Method: EventStorming big picture → hot spots → process level → strategic design; timeline figure for UC3–UC6.],
  [Subdomain table: core, supporting, generic; build or buy.],
  [From contexts to services: why four contexts share commerce.],
  [Aggregate derivation table (F4): commands, invariant, consistency boundary, root, entities, value objects, events.],
  [Producer and consumer interfaces; how a new consumer subscribes without changing the producer (audit queue).],
  [Ubiquitous language glossary → appendix.],
)
#diagram-slot("D4", [Context map (DDD context map). Every edge carries upstream/downstream and its pattern.])
#diagram-slot("D7", [Aggregates and life cycles (UML class + state machine). Team and purchase intent states.])
#diagram-slot("D8", [Domain event flow (UML component). Producer aggregates, published events and consumers.])

== Physical Architecture & Design
#owner([Liu Jin], closes: "F6, F8 (physical)", diagrams: "D5, D6")
#prompt[Describe the architecturally significant components (or elements) of the solution by describing the physical design which may include the network, technologies, nodes and machines where the components are deployed using physical detail deployment and other diagram as necessary. This is where you can also include and describe the cloud services that you use if any.]
#todo(
  [Mapping table: logical element (3.1) → AWS product.],
  [Network: public subnets (ALB, NAT), private application subnets (ECS tasks), data subnets (RDS, Valkey) with no route out of the VPC (AD-15). State "configured, tested offline on 4 Nov 2026" until an AWS session proves it.],
)
#diagram-slot("D5", [Physical deployment view (UML deployment). Nodes, artefacts and two availability zones.])
#diagram-slot("D6", [Network and security groups (AWS architecture icons). Public, application and data subnets per zone.])

=== Key Architectural Decisions
#prompt[Provide a summary of the significant decisions made in arriving at the architecture of the solution that are related and/or reflected in the physical view. Documented decisions should focus on trade-off choices and rationale. You should continue the numbering from the previous architectural decisions table.]
The key architectural decisions taken are as follows:
#ad-table(
  ("AD-07", [ECS Fargate on ARM. _Trade-off to write._]),
  ("AD-08", [Fargate Spot above the regular-Fargate minimum (1 : 3). _Trade-off to write._]),
  ("AD-09", [CloudFront → ALB restricted by origin prefix list and a secret header. _Trade-off to write._]),
  ("AD-10", [fck-nat instance, not a NAT Gateway. _Trade-off to write._]),
  ("AD-11", [One RDS server holding three databases. _Trade-off to write._]),
  ("AD-12", [Permanent edge stack, disposable runtime stack. _Trade-off to write._]),
  ("AD-13", [No Kubernetes and no Argo CD. _Trade-off to write._]),
  ("AD-14", [Own member account in an AWS Organization with SCP guardrails. _Trade-off to write._]),
  ("AD-15", [Separate data subnets for RDS and Valkey with no route out of the VPC (F6, \#589). _Trade-off to write._]),
)

=== Technology and Services
#prompt[Describe the architecturally significant technology and cloud services that are used in the solution and describe the trade-offs and rationale.]
#todo([Services, sizes and versions with trade-offs (deck backup B7). Check every figure against evidence.])

=== Persistence Design
#owner([Liu Jin], closes: "F11", diagrams: "D9")
#prompt[State how data is managed in the solution, e.g., in a relational database. Describe any special considerations for transaction management, concurrency, etc. Data persistence — indicate which parts of the data model must persist beyond a transaction or session and the period of persistence. Provide a logical data model for the persistent data storage used in the solution.

Describe the information architecture of the solutions describing the type of data inventory and classification that you have and how the data will be stored, secured and handled.]
#todo(
  [Table ownership per service; every database runs the full Flyway stream, the runtime role only reaches its own tables.],
  [Conventions: UUIDv7, table prefixes, audit columns, soft delete; no foreign keys or CHECK constraints.],
  [Indexes that enforce invariants; aggregate-to-table mapping.],
  [Technical tables: outbox (V26), consumed messages (V28), scheduler lease (V13).],
  [Valkey key model; event envelope schema and SNS attributes; queue and DLQ settings.],
  [Transactions and concurrency: settlement lock order (AGENTS.md claims table).],
)
#diagram-slot("D9", [Logical data model per database (crow's foot). Relationships dashed: kept by the application.])

==== Data classification
#prompt[For each data group: data format; proposed storage technology; security requirements and controls; trade-off consideration.]
#table(
  columns: (auto, 1fr, 1fr, 1fr, 1fr),
  [Data group], [Data format], [Storage technology], [Security requirements and controls], [Trade-off],
  [Identity], [], [], [], [],
  [Commerce and payment], [], [], [], [],
  [Notifications], [], [], [], [],
  [Sessions], [], [], [], [],
  [Events], [], [], [], [],
)

=== Detailed Design
#owner([Yu Qianshuo], closes: "F5, F7, F10", diagrams: "D11–D14")
#prompt[Describe the detailed design (of classes, objects, functions, etc.) of a few architecturally representative use cases or user stories.]
#todo(
  [UC4 join a team by paying: two users (leader pays, shares, member joins and pays, team succeeds) and the race for the last seat.],
  [Team expiry with refund: scheduled job, outbox, broker, notification.],
  [Real class and method names only (check with `rg`); lock order per the AGENTS.md claims table.],
  [Design patterns present in the code: Factory Method, Observer, Transactional Outbox, Ports and Adapters, Idempotent Receiver.],
)
#diagram-slot("D11", [UC4 class diagram (UML class). Adapter → application → domain → infrastructure.])
#diagram-slot("D12", [UC4 sequence, two users and the last seat (UML sequence). Transaction boundary marked.])
#diagram-slot("D13", [Team expiry and refund class diagram (UML class).])
#diagram-slot("D14", [Team expiry and refund sequence (UML sequence).])

== Other Architectural Decisions
#owner[Lim Jiechao]
#prompt[Provide a summary of the other significant decisions made in arriving at the architecture of the solution that has not been mentioned. If you explain the rationale of the decisions in other section (such as the quality attribute sections), you can make a reference to those sections.]
The key architectural decisions taken are as follows:
#ad-table(
  ("AD-16", [Scheduled jobs stay in the owning service under a lease (ADR 0002). _Trade-off to write._]),
  ("AD-17", [GitHub OIDC roles and Parameter Store; no stored cloud keys. _See 4.3._]),
  ("AD-18", [COLA layers and ArchUnit rules keep boundaries (ADR 0008). _See 4.4._]),
  ("AD-19", [Release only by a dev → prod promotion that builds, deploys, tests, releases and destroys. _See 5.3._]),
)

== Architectural Limitation
#owner[Liu Jin]
#prompt[(Optional but it's important to acknowledge any known limitation of your solution before any stakeholder point them out to you) Include any outstanding issues from a solution architecture and high-level design perspective which need to be resolved. For resolutions that extend beyond existing project plans, there should be a roadmap with a timeline for closing the issue.]
#todo([Expand impact and description; add a roadmap date to every open item (confirm at the report meeting).])
The key outstanding architectural issues in the solution are as follows:
#table(
  columns: (auto, 1.4fr, 1fr, 1.2fr, auto, auto),
  [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
  [AISS-01], [RDS, Valkey and NAT each in one zone], [], [Multi-AZ RDS, Valkey replica, NAT per zone], [Liu Jin], [Open],
  [AISS-02], [Commerce is large; payment shares the settlement transaction], [], [Extract payment with a refund saga], [Eric], [Open],
  [AISS-03], [Checkout stops while identity is down], [], [By design; later cache or circuit breaker], [Yu Qianshuo], [Open],
  [AISS-04], [Internal endpoints rely on network rules only], [], [Service token or mutual TLS], [Pan], [Open],
  [AISS-05], [One shared database server], [], [Move a database to its own server], [Liu Jin], [Open],
  [AISS-06], [Environment build takes about 32 min (create, deploy and smoke, #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[run 39008208244], 17 Oct 2026); about 15 min of it is the 900 s SNS filter-policy wait (16 Oct 2026 startup record)], [], [Start early; edge stays permanent], [Pan], [Mitigated],
  [AISS-07], [Database and application shared subnets], [], [Data subnets (AD-15)], [Liu Jin], [Configured; AWS pending],
  [AISS-08], [CloudFront → ALB is plain HTTP], [], [HTTPS listener with ACM], [Pan], [Open],
  [AISS-09], [Valkey token and origin secret in Terraform state], [], [Rotate per session; write-only when possible], [Pan], [Accepted],
  [AISS-10], [Shared session store], [], [Short-lived tokens with refresh], [Eric], [Open],
  [AISS-11], [Events eventually consistent (about 2 s)], [], [UI shows updating; API is the source of truth], [Eric], [By design],
  [AISS-12], [Linear scaling not proven beyond three tasks], [], [Distributed load generator; rerun V3], [Pan], [Open],
  [AISS-13], [V4, V5, V7 and identity outage run locally only], [], [Run the drills on AWS], [Yu Qianshuo], [Open],
  [AISS-14], [Bootstrap retry can restore scaling suspension; SCP denied a metric read], [], [Retry regression and fix], [Liu Jin], [Open],
  [AISS-15], [`CloudWatchFullAccessV2` not fenced to SWE5001], [], [Narrow to SWE5001 names], [Pan], [Accepted],
)
