#import "../lib.typ": *

= Introduction
#owner[Lim Jiechao]

== Background
#prompt[Provide the background and context of the project and its current status from a project perspective.]
#todo(
  [Context: BlocBuy, the SWE5006 group-buying system, re-architected for scale in SWE5001.],
  [Current status in one paragraph: what runs on AWS, what is verified, what is open (link to 2.2).],
  [Sources: `docs/swe5001/project-proposal.typ`; deck slide 2.],
)

== Business Needs
#prompt[Include a brief summary of the business needs and drivers for the initiative.]
#todo(
  [Pain points (briefing Q1): a team only forms if enough buyers join in time; bursts near deadlines.],
  [Drivers for a platform: scale, independent release, new producers and consumers.],
  [Sources: proposal; deck slides 2–3.],
)

== Stakeholders
#prompt[List the key business and IT stakeholders in the initiative.]
#todo(
  [Business and IT stakeholders as a table.],
  [The seed, the producers (sellers, admins) and the consumers (buyers) — success criteria.],
  [Source: `02-proposed-architecture.md` §1.],
)
#diagram-slot("D1", [System context view (C4 level 1). Actors and external systems: Google sign-in, Stripe, AWS.])

== Project Scope
#prompt[Briefly describe the scope of the solution. State what is included in the scope and what is out of scope. If relevant, define the phases of the solution and what is delivered in each phase. Provide references to the documented scope. Also clearly define the scope for the architecture, design and implementations if you do not implement everything that you have architected.]
#diagram-slot("D2", [Use case view (UML use case diagram). UC3–UC6 are the group-buy core.])

=== Functionality in scope
#prompt[Briefly list down the requirements that are in the scope of the project.]
#todo([In-scope list: full buying journey on three services (deck slide 4).])

=== Functionality out of scope
#prompt[Briefly list down the requirements that are out of scope of the project.]
#todo(
  [Out-of-scope list.],
  [Architected but not built, said explicitly: payment extraction, API gateway, stock reservation, distributed tracing (see `registers.md`).],
)
