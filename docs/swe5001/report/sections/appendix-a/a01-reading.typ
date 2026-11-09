#import "lib-a.typ": *

== Reading This Appendix <sec-a-reading>

This appendix gives the detailed object design of every BlocBuy use case. For each one, it takes the flows of events through a platform-free analysis model to the classes and messages of the code, by way of the transition strategies in @sec-a-strategies. It answers two assessor remarks for every use case, not only for the two that chapter 3 discusses: remark 6 asked for the "resultant detailed object design", and remark 11 asked for class and sequence diagrams at class and object level, built the way the SAD and SDP courses teach. Section 3.2.4 discusses UC4 (joining by paying) and team expiry at chapter level; the parts below give their full detail, and the same detail for every other use case.

=== How the Parts Are Laid Out <sec-a-reading-layout>

A.1 and A.2 are shared. A.1 is this guide: notation, analysis rules and the system-level analysis model. A.2 holds the nine transition strategies, S1 to S9, that every use case applies, with the mapping rule from analysis stereotypes to design elements and the subsystem view. A.3 to A.13 hold one use case each, and A.14 traces all of them side by side. Every use-case part has the same ten subsections, in this order:

+ *Use case and flows of events.* Actors, trigger, owning service, pre- and postconditions, and numbered steps: basic flow `B1…Bn`, alternate flows `A1…` and exception flows `E1…`, each with the step it branches from.
+ *Analysis class diagram.* «boundary», «control» and «entity» only, with one control named after the use case.
+ *Analysis sequence diagrams.* One for each major flow; every message carries its step number.
+ *Transition.* The strategies that change this use case, one line each, and a table that maps every analysis object to its design elements.
+ *Design class diagrams.* Real classes with typed members, split into slices of at most about 14 classes.
+ *Design sequence diagrams.* One for each flow or transaction, retelling the steps with real operations.
+ *Operation specifications.* The key operations with signature, preconditions, postconditions, errors and transaction.
+ *State machines.* Drawn once, in the part that owns the lifecycle; other parts refer to it.
+ *Patterns applied.* Only patterns that are in the code: the problem first, then the roles and the consequences.
+ *Flow-to-design trace.* Each flow step, its analysis message and the design operations that realise it.

A subsection that does not apply says so in one sentence.

=== Notation Key <sec-a-reading-notation>

All diagrams use UML 2 notation, drawn in the Visual Paradigm style of the team's diagram kit. @tbl-a-notation lists the elements. @tbl-a-stereotypes lists every stereotype and tag that a diagram in this appendix may use.

#atable(
  [Notation used in Appendix A.],
  <tbl-a-notation>,
  columns: (auto, 1fr, 1.4fr),
  header: ([Element], [How it is drawn], [What it means here]),
  [Association], [Solid line; an open arrowhead shows navigation], [The class holds a reference to the other in a field. Multiplicities at both ends; an association name may label the line.],
  [Composition], [Solid line with a filled diamond at the whole], [The part lives and dies with the whole and is written by the whole's repository (for example a team's memberships).],
  [Dependency], [Dashed line, open arrowhead], [A parameter, return value, local variable or created object (labelled «create»). «HTTP» marks a call to another service, «async» a message through the broker.],
  [Realisation], [Dashed line, hollow triangle], [The class implements the interface.],
  [Generalisation], [Solid line, hollow triangle], [The class extends the other class.],
  [Member], [`+` public, `-` private, `~` package; `name: Type`; operations `name(p: Type): Return`], [Design members carry Java types. Analysis attributes use analysis types: Text, Money, Count, Date-time, Boolean and named enumerations such as `TeamStatus`.],
  [Lifeline], [`:Class`, or `name : Class` when two objects of one class take part], [The interface the caller holds, unless the diagram shows the implementation. Actors appear only in sequence diagrams.],
  [Synchronous message], [Solid line, filled arrowhead; the return is a dashed line], [A method call, an HTTP request (the Stripe webhook included) or a transaction-phase listener callback. The caller waits.],
  [Asynchronous message], [Solid line, open arrowhead; no return], [A message through the outbox, SNS or SQS, and nothing else. The sender does not wait.],
  [`critical`], [Fragment with a guard], [One database transaction, labelled with its propagation, isolation and timeout (for example `REQUIRED, READ COMMITTED, 10 s`).],
  [`alt`, `opt`, `break`, `loop`, `par`], [Fragments with guards], [Alternatives; an optional part; a refusal that ends the flow; a repetition; parallel work.],
  [Step tag], [`B4`, `A2`, `E1` in a message or a note], [The step of the flows-of-events table that the message realises.],
)

#atable(
  [Stereotypes and strategy tags. A design box carries one or more role stereotypes, then the tags of the strategies that produced it, for example «interface, repository, Pers, Conc».],
  <tbl-a-stereotypes>,
  columns: (auto, 1fr),
  header: ([Stereotype or tag], [Meaning]),
  [«boundary», «control», «entity»], [Analysis classes only (BCE). A «boundary» handles an actor or an external system; a «control» coordinates one use case; an «entity» holds information kept beyond one interaction.],
  [«controller»], [REST controller in the adapter layer (`..web..`, or `..web.internal..` for internal endpoints).],
  [«application service»], [Use-case orchestration in the application layer; owns the transaction.],
  [«domain service»], [A domain rule that needs more than one aggregate or value (for example `DualPricingStrategy`).],
  [«aggregate root», «entity», «value object»], [DDD building blocks in the domain layer. In a design diagram «entity» means a DDD entity inside an aggregate, not the analysis stereotype.],
  [«domain event», «integration event»], [An in-process event record (`TeamSucceeded`); a versioned cross-service contract in `blocbuy-client` (`TeamSucceededV1`).],
  [«repository», «gateway», «facade»], [Ports: persistence of one aggregate; an external system or technical service; a supplier module's published interface (for example `CatalogPurchaseSupport`).],
  [«adapter», «mapper», «data object», «converter»], [Infrastructure: a port implementation; a MyBatis mapper (the course's DAO); a row-shaped object (the course's DTO); the converter between row and aggregate.],
  [«subscriber», «scheduled job», «configuration»], [A transaction-phase event listener; a timer class in `bootstrap.scheduling`; a Spring wiring class.],
  [«interface», «abstract», «record», «enumeration», «exception»], [Java language constructs (S1).],
  [«component»], [One COLA layer of one service in the subsystem view of A.2, standing in for a package.],
  [«topic», «queue»], [Sequence-diagram participants for the SNS topic and an SQS queue.],
  [Lang, Pres, Dist, Pers, Ext, Sec, Sched, Conc, Evt], [Strategy tags for S1 Language and framework, S2 Presentation, S3 Distribution, S4 Persistence, S5 External interface, S6 Security, S7 Scheduling, S8 Concurrency, S9 Domain and integration events (@sec-a-strategies).],
)

=== Analysis Rules <sec-a-reading-bce>

The analysis models follow the BCE rules of the SAD course (03a, pages 8–13, 21 and 49–56). They describe what the system must do, not how:

- *Three stereotypes only.* A boundary handles everything that depends on the environment, so a change in how an actor or a system talks to BlocBuy touches the boundary alone. An entity holds information kept beyond one interaction. A control coordinates the others.
- *One control per use case, named after it.* `JoinTeamControl` realises UC4 "Join an existing team". A.14 traces each control to the design entry points that realise it.
- *Actors talk only to boundaries; entities never talk to actors.* Stripe and Google are reached through a gateway boundary (`PaymentGateway`, `IdentityProvider`).
- *No inheritance, aggregation or composition across stereotypes.*
- *Attributes on entities only, in analysis types.* A reference between entities is an association with multiplicities, never an identifier attribute.
- *Operations in business vocabulary*, taken from the messages of the sequence diagrams. No framework, table, lock, transaction or HTTP term appears.

=== System-Level Analysis Model <sec-a-reading-system>

A class definition is global: an attribute or association of a class holds in every use case in which the class appears (03a, page 52). @fig-a-sys-entities therefore draws every analysis entity once, with its associations and multiplicities, in the system-level style of 03a, page 55. It shows key attributes only; each use-case part adds the operations it needs and draws its own boundaries and control. Once designed, the entities sit in three services: `Account`, `Role` and `OAuthBinding` in identity-svc, `Notification` in notification-svc, and the rest in commerce-svc. An association that crosses a service line (an account's memberships, an account's notifications) becomes an identifier in the design and is never joined in SQL (S3, S4).

Every part uses the names of @fig-a-sys-entities. Where the SWE5006 report used another name for the same concept, the name here replaces it: `Listing` (UC1, UC8, UC9) is `Product`; `User`, `UserAccount` and `AdminAccount` are `Account`; `Invite` is `Invitation`. UC2's short-lived sign-in state and session hold no business fact and stay local to @sec-a-uc02. New since SWE5006 are `Account` as one entity, `PurchaseIntent` (the frozen purchase terms), `ReviewCase` (a payment an operator must check) and `Notification`.

#afig("a-sys-entities", [System-level analysis class diagram (UML class notation, «entity» only, key attributes only). `Team` is the hub: a product offers teams, a team owns its memberships, and every payment reaches a team through the purchase intent it pays.])

=== Evidence Status and the SWE5006 Report <sec-a-reading-evidence>

Everything in this appendix is designed and built. Every class, method, field, error code and table it names exists in the code of the report baseline and was checked against it. Which flows were verified on AWS, and which only locally or in automated tests, is stated once in section 2.2 and not repeated here. A diagram shows how the code is built; it is not evidence that a flow ran in a given environment.

The appendix is self-contained. It reuses the analysis model of the SWE5006 report (its section 3.4) and updates it in two ways. First, the names are unified across use cases, as above. Second, the design moves from the SWE5006 modular monolith to the three-service topology of backend ADR 0008: commerce asks identity over HTTP (S3), roles travel in the login session (S6), scheduled jobs run in commerce-svc only (S7), each service has its own database (S4), and events reach notification-svc through an outbox, SNS and SQS (S9). The business rules themselves did not change.

=== Reading Path <sec-a-reading-path>

Read A.2 first, because every part cites its strategies by number. Then read A.6 (UC4), the most complete part, which uses every convention, and the parts it includes or triggers: Pay upfront (A.12), UC5 (A.7) and UC6 (A.8). The other parts can be read in any order. A.14 closes with the cross-use-case trace. @tbl-a-parts lists the parts.

#atable(
  [Use cases and their parts. The primary actor starts the use case; the service owns its steps.],
  <tbl-a-parts>,
  columns: (auto, 1fr, auto, auto, auto),
  header: ([Use case], [Name], [Primary actor], [Service], [Part]),
  [UC1], [Browse and search listings], [Buyer (anonymous)], [commerce-svc], [@sec-a-uc01],
  [UC2], [Sign in with Google], [Buyer, Admin], [identity-svc], [@sec-a-uc02],
  [UC3], [Initiate a team by paying], [Buyer], [commerce-svc], [@sec-a-uc03],
  [UC4], [Join an existing team by paying], [Buyer], [commerce-svc], [@sec-a-uc04],
  [UC5], [Resolve a team], [Scheduler], [commerce-svc], [@sec-a-uc05],
  [UC6], [Refund members of an expired team], [Scheduler], [commerce-svc], [@sec-a-uc06],
  [UC7], [Share an invitation], [Buyer], [commerce-svc], [@sec-a-uc07],
  [UC8], [View my orders and teams], [Buyer], [commerce-svc], [@sec-a-uc08],
  [UC9], [Administer product listings], [Admin], [commerce-svc], [@sec-a-uc09],
  [«include»], [Pay upfront via Stripe Checkout], [Buyer], [commerce-svc], [@sec-a-pay-upfront],
  [—], [Receive notifications], [Buyer], [notification-svc], [@sec-a-notifications],
)
