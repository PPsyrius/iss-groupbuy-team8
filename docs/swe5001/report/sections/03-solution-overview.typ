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

#include "03-2-physical.typ"

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

#include "03-4-limitations.typ"
