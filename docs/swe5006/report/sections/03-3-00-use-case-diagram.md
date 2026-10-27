<!-- report
rubric: 3.3, 3.3.1
owner: Lim Jiechao
plan: #677
sources: ../presentation/src/diagrams/use-case.svg
status: draft
-->

## 3.3 Use Case Model

BlocBuy has nine use cases from the proposal plus one included use case, "Pay upfront via Stripe Checkout", that UC3 and UC4 share. Section 3.3.1 shows them in one UML use case diagram with the seven use cases the proposal put out of scope. Sections 3.3.2 to 3.3.12 then describe each use case in one shape: actors, preconditions, trigger, a numbered normal flow, exceptional flows numbered against the normal step they branch from (for example "4a"), and postconditions.

Three BlocBuy terms recur. A **team** (called a *bloc* in the user interface) is a group purchase of one listing: a buyer starts it, other buyers join it, and it succeeds when its seats are full before its deadline. A **listing** is a product on sale. **Pay first** means that every buyer pays the team price when they start or join, before the team is known to succeed; if it expires, the buyer gets the money back. All payments use Stripe in test mode (the Stripe *Sandbox*): no real money moves.

### 3.3.1 Use Case Diagram

Figure 3.36 is the use case diagram. It is the deck's diagram, reused unchanged.

![Figure 3.36: UML use case diagram of BlocBuy, with the system boundary, five actors, the «include» and «extend» relations and the out-of-scope package. Each in-scope use case carries its delivery status on dev as of 24 October (a team ruling, not UML notation).](../../presentation/src/diagrams/use-case.svg){.landscape}

**Actors.** Two actors are human. The *Buyer* browses, signs in, starts and joins teams, shares invite links and views their own orders and teams. The *Admin* manages listings and their team terms. Three actors are not people. The *Scheduler* is time: it triggers UC5 and, through it, UC6 when a team's deadline passes. *Stripe* is a secondary actor: it hosts the payment page, reports payment results and executes refunds. *Google* is a secondary actor for sign-in only (UC2). No merchant actor exists, because the proposal excludes a merchant self-service interface; the Admin prepares listings instead.

**Relations.** UC3 "Initiate a team" and UC4 "Join a team" both «include» "Pay upfront via Stripe Checkout": neither can complete without a verified payment, and the payment steps are the same for both, so they are written once (Section 3.3.11). UC7 "Share attribution" «extends» UC4 at the extension point *admission*, under the condition {via invite link}: a join that arrives through a member's invite link records who shared it, and a join without a link is unchanged. UC6 "Refund on team expiry" «extends» UC5 "Team lifecycle resolution" at the extension point *resolved*, under the condition {team expired}: a team that succeeds needs no refund. "Receive notifications" is drawn inside the boundary but marked "coded, off on dev": it belongs to the SWE5001 course scope (Section 3.3.12).

**Out of scope.** The package on the right lists the seven use cases that the proposal (§6.2) excludes: a merchant self-service interface, analytics dashboards, recommendations, automatic team matching, email, SMS and push notifications, native mobile apps and live payment processing. Section 1.4.2 gives the reasons.

**Delivery status.** The badges record when each use case was first shown working on the SWE5006 dev system. On 23 October on dev, a buyer started a team with a Stripe Sandbox payment, the team expired at its deadline and the payment was refunded automatically (UC3, Pay upfront, UC5 expiry, UC6). On 24 October the system demo recording on dev showed a second buyer join through an invite link and fill the team, "2 of 2 joined" (UC4, UC5 success, UC7). The other use cases were deployed and usable on dev before then. {{VERIFY: re-check every badge at the freeze SHA and date the caption (rubric G7) — Lim}}
