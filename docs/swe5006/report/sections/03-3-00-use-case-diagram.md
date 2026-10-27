<!-- report
rubric: 3.3, 3.3.1
owner: Lim Jiechao
plan: #677
sources: ../presentation/src/diagrams/use-case.svg
owns: R35
status: draft
-->

## 3.3 Use Case Model

BlocBuy has nine use cases from the proposal plus one included use case, "Pay upfront via Stripe Checkout", that UC3 and UC4 share. Section 3.3.1 shows them in one UML use case diagram with the seven use cases the proposal put out of scope. Sections 3.3.2 to 3.3.12 then describe each use case in one shape: actors, preconditions, trigger, a numbered normal flow, exceptional flows numbered against the normal step they branch from (for example "4a"), and postconditions.

### 3.3.1 Use Case Diagram

Figure 3.36 is the use case diagram.

![Figure 3.36: UML use case diagram of BlocBuy, with the system boundary, five actors, the «include» and «extend» relations and the out-of-scope package. The badges on the in-scope use cases mark delivery on dev and are not UML notation; Table 1.3 gives the status.](../../presentation/src/diagrams/use-case.svg){.landscape}

**Actors.** Two actors are human. The *Buyer* browses, signs in, starts and joins teams (team, section 1.1), shares invite links and views their own orders and teams. The *Admin* manages listings and their team terms. A buyer who has not signed in yet is called a *visitor* in sections 3.3.3 and 3.4.3: a visitor can browse listings (UC1) and open an invite link, and must sign in (UC2) before starting or joining a team. Three actors are not people. The *Scheduler* is time: it triggers UC5 and, through it, UC6 when a team's deadline passes. *Stripe* is a secondary actor: it hosts the payment page, reports payment results and executes refunds. *Google* is a secondary actor for sign-in only (UC2). No merchant actor exists, because the proposal excludes a merchant self-service interface; the Admin prepares listings instead.

**Relations.** UC3 "Initiate a team" and UC4 "Join a team" both «include» "Pay upfront via Stripe Checkout": neither can complete without a verified payment, and the payment steps are the same for both, so they are written once (section 3.3.11). UC7 "Share attribution" «extends» UC4 at the extension point *admission*, under the condition {via invite link}: a join that arrives through a member's invite link records who shared it, and a join without a link is unchanged. UC6 "Refund on team expiry" «extends» UC5 "Team lifecycle resolution" at the extension point *resolved*, under the condition {team expired}: a team that succeeds needs no refund. "Receive notifications" is drawn inside the boundary but marked as switched off (off on dev, section 1.4); section 3.3.12 describes it.

**Out of scope.** The package on the right holds the seven exclusions of section 1.4.2.

**Delivery status.** The badges mark which use cases are delivered on dev; Table 1.3 gives the status of each. {{VERIFY: re-check every badge at the freeze SHA and date the caption (rubric G7) — Lim}}
