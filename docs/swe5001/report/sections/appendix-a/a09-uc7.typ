#import "lib-a.typ": *

// Code in this part's tables prints at 8 pt (calibration rule 28).
#show table: it => {
  show raw: set text(size: 8pt)
  it
}

== UC7 Share an Invitation <sec-a-uc07>

UC7 lets a member of an open team share an invite link. When another buyer joins the team through that link, BlocBuy records who shared it and, once the join is final, records one share credit for the sharer. UC7 extends UC4 (@sec-a-uc04) at the extension point _admission_, under the condition {joined through an invite link}. A join without a link is unchanged, and attribution can never block or undo a paid join. The design questions are an unguessable reference that grants nothing, an anonymous lookup that cannot be abused, and a credit that must not share the fate of the money. Every class, method and error code named here exists in the code on this branch. UC7 is designed and built; the evidence for each flow is listed in section 2.2.

=== Use Case and Flows of Events <sec-a-uc07-flows>

- *Actors.* Buyer as sharer (primary; the leader or any paid member of the team). Buyer as invited joiner (primary from B5; may be signed out when the link is opened).
- *Trigger.* The sharer chooses to share the team, from the listing page or from the team's row on My blocs (UC8, @sec-a-uc08).
- *Services.* commerce-svc owns every step (its group-buy and order modules). identity-svc supplies one thing: the sharer's public nickname.
- *Preconditions.* The sharer is signed in (UC2, @sec-a-uc02) and is a paid member of a team that is open and inside its deadline.
- *Postconditions.* Every attributed join has its sharer on the membership, and each accepted participation has at most one share credit. A refused or unattributed join has no credit. Credits are recorded but shown to nobody (a view of them was dropped).

#atable(
  [UC7 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off.],
  <tbl-a-uc07-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The sharer asks to share a team.],
  [*B2*], [], [The system checks that the team exists, that the sharer is a member of it, and that it is open and inside its deadline.],
  [*B3*], [], [The system returns the sharer's invitation for this team. On first use it creates one with a reference that cannot be guessed; every later request returns the same invitation.],
  [*B4*], [], [The page builds the invite link (the listing's address with the team and the reference). The sharer copies it or sends it through the device's share sheet.],
  [*B5*], [], [The invited buyer opens the link. The page keeps the team and reference for this visit and removes them from the address bar, so a copied address never passes someone else's reference on.],
  [*B6*], [], [The system resolves the reference to the sharer's public nickname, without sign-in. The listing page shows a banner that names the sharer and offers "Join this bloc".],
  [*B7*], [], [The invited buyer joins the team (UC4), signing in first if needed. The join carries the reference. Before any lock or payment, the system checks that the invitation names this team and a sharer other than the buyer, and freezes that sharer on the purchase intent.],
  [*B8*], [], [When the payment is admitted (UC4 B7), the system records the sharer on the new membership and announces that a member joined.],
  [*B9*], [], [Once the admission is final, the system records one share credit for the sharer.],
  [*A1*], [B1], [The sharer asks for links too often in a minute: the request is refused, try again later.],
  [*A2*], [B2], [The team does not exist, or the requester is not a member: the answer is "not found" in both cases, so a non-member learns nothing about the team.],
  [*A3*], [B2], [The team has closed, filled or passed its deadline: the request is refused and no invitation is created.],
  [*A4*], [B5], [The link's team or reference is malformed: the page drops it, and an older kept link is discarded too.],
  [*A5*], [B6], [The reference is malformed, unknown or deleted, or its team is gone: no name is shown; the banner invites without a name. Joining still works, unattributed.],
  [*A6*], [B6], [Too many links were opened from the same network address in a minute: as A5.],
  [*A7*], [B7], [The reference is unknown, names another team, or names the buyer; or the buyer starts a team instead (UC3): the reference is ignored and the join goes ahead unattributed.],
  [*A8*], [B8], [The admission is refused (UC4 A6) or the same payment is reported again (UC4 A8): no member is announced and no credit is recorded.],
  [*A9*], [B9], [The same join is announced twice: the sharer still gets one credit for it.],
  [*E1*], [B6], [The account service is unreachable or slow: the lookup fails and the banner invites without a name (the page waits at most 1.5 s).],
  [*E2*], [B9], [Recording the credit fails: the failure is logged and the join stands. The sharer on the membership is the source from which the missing credit is reconciled.],
)

=== Analysis Class Diagram <sec-a-uc07-ac>

@fig-a-uc07-ac is the analysis model. The sharer uses `ShareInviteUI` and the invited buyer first `InviteLandingUI`, then UC4's `JoinTeamUI`. `ShareInvitationControl`, named after the use case, handles both invitation requests: a member asking for one, and a visitor looking one up. Attribution happens inside UC4's `JoinTeamControl`, at the extension point: the team resolves the sharer through the invitation, exactly as A.6 models it. `ReferralCreditControl` reacts to "member joined" and creates the credit; it is linked to the join only by that announcement, so it can never refuse it. The entities are the canonical ones of the system view (@sec-a-reading); `ShareCredit` is the referral credit. One association is new to the system view: `PurchaseIntent` _via_ `Invitation` (B7 freezes the sharer before payment).

#afig("a-uc07-ac", orient: "portrait", [UC7 analysis class diagram (UML class notation, BCE stereotypes). `ShareInvitationControl` owns the invitation; attribution runs in UC4's `JoinTeamControl`, and `ReferralCreditControl` is reached only by the "member joined" announcement.])

=== Analysis Sequence Diagrams <sec-a-uc07-as>

UC7 has three major flows: share an invitation (@fig-a-uc07-as-share), open an invite link (@fig-a-uc07-as-open), and join through it and credit the sharer (@fig-a-uc07-as-attribute). A1 and A6 need a rate counter, E1 a remote call and E2 a transaction, so they appear in the design sequences only. A4 happens in the page alone (S2) and B4 builds the link in the page; neither involves an entity.

#afig("a-uc07-as-share", [UC7 analysis sequence diagram, share an invitation (B1–B4, A2, A3). Each member has at most one invitation per team, so sharing again returns the same link.])

#afig("a-uc07-as-open", [UC7 analysis sequence diagram, open an invite link (B5–B6, A5). Anyone may open the link before signing in; the lookup shows a display name only.])

#afig("a-uc07-as-attribute", [UC7 analysis sequence diagram, join through an invitation and credit the sharer (B7–B9, A7–A9). The sharer is fixed before payment; the credit is created only after admission and can never refuse the join.])

=== Transition: Strategies and Mapping <sec-a-uc07-transition>

The strategies of @sec-a-strategies turn the model into the design. Seven of the nine change UC7:

- *S1 Language and framework (AD-18; NFR-05):* impacted `Invitation`, `ShareCredit`, "member joined" → resultant immutable records `ShareInvite`, `ShareCredit`, `MemberJoined` and the views `ShareInviteView`, `ResolvedInviteView`; `ShareInviteAppService` is a Spring service whose transaction is an explicit `TransactionTemplate`; dynamics @fig-a-uc07-ds-mint.
- *S2 Presentation (AD-09; NFR-16):* impacted `ShareInviteUI`, `InviteLandingUI` → resultant `ShareBlock`, `RowShare` and `ShareInvitePanel` with the hook `useShareInvite`; the listing route parks the invitation in session storage (`parkInvite`) and shows `InviteBanner`; `useCheckout` adds the parked reference to a JOIN only. Not drawn.
- *S3 Distribution (AD-02, AD-06; NFR-66):* impacted `ShareInvitationControl`, `Account`, `JoinTeamControl` → resultant `GroupDealController.createInvite` and `InviteController.resolve` over one application service; `Account.publicNickname` becomes the port `UserAppService`, which in commerce-svc is `RemoteUserAppService`, an HTTP client of identity-svc's `InternalUserController` (500 ms connect, 1,500 ms read, no retry, fails closed); attribution reaches group-buy through the module port `GroupDealPurchaseSupport`; dynamics @fig-a-uc07-ds-resolve.
- *S4 Persistence (AD-03; NFR-13):* impacted `Invitation`, `ShareCredit`, `Membership`, `PurchaseIntent` → resultant `ShareInviteRepository` over `biz_share_invite` (cached by `RedisCachedShareInviteRepository`), `ShareCreditRepository` over `biz_share_credit`, and the sharer columns `biz_group_member.sharer_user_id` and `biz_purchase_intent.sharer_user_id`; dynamics @fig-a-uc07-ds-lookup. The cache holds only valid references and never past the team's deadline; PostgreSQL stays the authority (NFR-53).
- *S5 External interface:* not applied; identity-svc is part of BlocBuy (S3), and no outside system takes part.
- *S6 Security (AD-05; NFR-46, 58, 68):* impacted `ShareInvitationControl`, `InviteLandingUI` → resultant session identity in `createInvite` (never from the request), the `X-BlocBuy-Request: invite` marker, 128-bit references, `RateLimitGateway` limits of 60 a minute per member (`invite-create`) and per client address (`invite-resolve`, address from `ClientAddressResolver`), the same 404 for a missing team and a non-member, anonymous access to `GET /api/v1/invites/{ref}` only, a response that names a nickname and no user id, and `Cache-Control: no-store`.
- *S7 Scheduling:* not applied. The reconciliation of lost credits (ADR 0003) is a query an operator runs by hand.
- *S8 Concurrency (AD-03; NFR-13):* impacted `ShareInvitationControl`, `JoinTeamControl`, `ReferralCreditControl` → resultant minting without a lock, made idempotent by `insertIfAbsent` and the unique index `uq_biz_share_invite_deal_sharer`; the sharer resolved _before_ the team lock, so its lookup stays out of the window in which buyers queue on the team row (NFR-53); one credit per payment by `uq_biz_share_credit_payment`. B8 runs inside UC4's settlement transaction, which spans several aggregates as justified in A.2 S8; dynamics @fig-a-uc07-ds-credit and UC4's intent sequence (@sec-a-uc04-ds).
- *S9 Domain and integration events (AD-04; NFR-39):* impacted "member joined", `ReferralCreditControl` → resultant domain event `MemberJoined`, published by `PaidGroupDealService.joinConfirmed` inside the admission transaction, and `ShareCreditSubscriber` at `AFTER_COMMIT` in its own `REQUIRES_NEW` transaction (ADR 0003). `MemberJoined` stays in-process: it has no integration contract and no outbox row; dynamics @fig-a-uc07-ds-credit.

#atable(
  [UC7 analysis → design mapping. Every analysis object of @fig-a-uc07-ac, and every design class of @fig-a-uc07-dc-invite and @fig-a-uc07-dc-credit.],
  <tbl-a-uc07-map>,
  columns: (0.95fr, auto, 2.3fr, auto, 0.8fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`ShareInviteUI`], [boundary], [`GroupDealController.createInvite`. Frontend `ShareBlock`, `RowShare`, `ShareInvitePanel`: not drawn (S2).], [S2, S3, S6], [@fig-a-uc07-dc-invite],
  [`InviteLandingUI`], [boundary], [`InviteController.resolve`. Frontend listing route with `parkInvite`, `InviteBanner`: not drawn (S2).], [S2, S3, S6], [@fig-a-uc07-dc-invite],
  [`JoinTeamUI`], [boundary], [UC4's (@sec-a-uc04); carries the parked reference as `inviteRef`.], [S2], [not drawn],
  [`ShareInvitationControl` (mint)], [control], [Entry point `ShareInviteAppService.mint` (application, group-buy).], [S1, S6, S8], [@fig-a-uc07-dc-invite],
  [`ShareInvitationControl` (resolve)], [control], [Entry point `ShareInviteAppService.resolve` (same class).], [S3, S6], [@fig-a-uc07-dc-invite],
  [`JoinTeamControl` (B7)], [control], [UC4's entry point `PurchaseIntentAppService`; attribution in `GroupDealPurchaseSupport.resolveSharer` (`GroupDealPurchaseSupportImpl`), drawn in UC4's intent slice.], [S3, S8], [@sec-a-uc04-dc],
  [`JoinTeamControl` (B8)], [control], [UC4's entry point `OrderFulfilmentAdapter`; admission in `PaidGroupDealService.joinConfirmed`.], [S8, S9], [@fig-a-uc07-dc-credit],
  [`ReferralCreditControl`], [control], [Subscriber `ShareCreditSubscriber.onMemberJoined` (application, group-buy).], [S9], [@fig-a-uc07-dc-credit],
  [`Invitation`], [entity], [Aggregate root `ShareInvite` (record); port `ShareInviteRepository`; decorator `RedisCachedShareInviteRepository` over `ShareInviteRepositoryImpl` → `BizShareInviteMapper`; `biz_share_invite`.], [S1, S4, S8], [@fig-a-uc07-dc-invite],
  [`Team`], [entity], [Aggregate root `GroupDeal` (not drawn; @sec-a-uc05) read through `GroupDealRepository.findById`; membership test `PaidGroupDealRepository.hasActiveMember`.], [S4], [@fig-a-uc07-dc-invite],
  [`Membership`], [entity], [A `biz_group_member` row whose `sharer_user_id` is written by `PaidGroupDealRepository.insertJoinedMember`.], [S4, S8], [@fig-a-uc07-dc-credit],
  [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`; `attributedTo` sets `sharerUserId` (`biz_purchase_intent`). Drawn in UC4's intent slice.], [S4], [@sec-a-uc04-dc],
  [`Account`], [entity], [Port `UserAppService`, adapter `RemoteUserAppService` → identity-svc `InternalUserController` (not drawn); `User` lives in identity-svc (@sec-a-uc02).], [S3], [@fig-a-uc07-dc-invite],
  [`ShareCredit`], [entity], [Aggregate root `ShareCredit` (record); port `ShareCreditRepository` → `ShareCreditRepositoryImpl` → `BizShareCreditMapper` (last two not drawn); `biz_share_credit`.], [S1, S4, S8], [@fig-a-uc07-dc-credit],
  [(none)], [—], [Introduced by a strategy: `RateLimitGateway` (S6); records `ShareInviteView`, `ResolvedInviteView` (S1, S3; not drawn); `RedisCachedShareInviteRepository.Entry`, `ShareInviteRepositoryImpl.InviteWithDeadline` (S4; not drawn); `ApplicationEventPublisher`, `MemberJoined` (S9).], [S1, S3, S4, S6, S9], [@fig-a-uc07-dc-invite, @fig-a-uc07-dc-credit],
)

=== Design Class Diagrams <sec-a-uc07-dc>

The design is drawn in two slices, both in commerce-svc: minting and resolving with the cached lookup behind the invitation port (@fig-a-uc07-dc-invite), and the sharer on the membership with the credit (@fig-a-uc07-dc-credit). The B7 classes (`PurchaseIntentAppService`, `GroupDealPurchaseSupport`, `ShareInvite.attributes`, `PurchaseIntent.attributedTo`) are UC4's intent slice and are not drawn twice (@sec-a-uc04-dc). The only class that reaches another service is `RemoteUserAppService`. @fig-a-uc07-dc-invite also shows this part's representative persistence chain: port `ShareInviteRepository` → adapter `ShareInviteRepositoryImpl` → MyBatis mapper `BizShareInviteMapper`; `ShareCreditRepository`, `GroupDealRepository` and `PaidGroupDealRepository` follow the same chain. `InviteWithDeadline` is the record `ShareInviteRepositoryImpl.InviteWithDeadline`. «static» marks a class-scope operation. An association is a field reference; a dependency is a parameter, return value or created object.

#afig("a-uc07-dc-invite", [UC7 design class diagram, slice 1: mint, resolve and the cached lookup (UML, commerce-svc; identity-svc behind `UserAppService`). One application service owns both requests; `RedisCachedShareInviteRepository` implements the port it wraps, so no client can tell a cached answer from a database one.])

#afig("a-uc07-dc-credit", [UC7 design class diagram, slice 2: sharer on the membership and share credit (UML, commerce-svc). The admission service publishes `MemberJoined` and knows nothing of credits; the subscriber depends only on the event and its own repository.])

=== Design Sequence Diagrams <sec-a-uc07-ds>

Each design sequence retells a group of steps of @tbl-a-uc07-flows; the notes carry the step tags. A `critical` fragment is a database transaction, labelled with its propagation, isolation and timeout.

*Invitation.* @fig-a-uc07-ds-mint is B1–B3. Minting reads the team but takes no lock: a team that closes just after a mint refuses the later join, and the invitation then attributes nothing. A first share inserts and reads back, so two concurrent first shares by one member end with the same reference. @fig-a-uc07-ds-resolve is B5–B6. It runs without a transaction, checks the rate limit before any read, and fails closed when identity-svc does not answer; the page then shows the banner without a name. @fig-a-uc07-ds-lookup opens `findByRef`: the decorator serves a valid cache entry, else asks the wrapped repository and fills the cache; the other two operations pass straight through to `source`.

#afig("a-uc07-ds-mint", [UC7 design sequence diagram, B1–B3 with A1–A3 (commerce-svc). The rate limit refuses before the transaction opens; a missing team and a non-member get the same 404; a concurrent first mint reads back the winner.])

#afig("a-uc07-ds-resolve", [UC7 design sequence diagram, B5–B6 with A5, A6 and E1 (commerce-svc, identity-svc). The lookup is anonymous and needs no transaction; the remote nickname call is synchronous and fails closed.])

*Attribution and credit.* B7 is drawn once, in UC4's intent sequence (@sec-a-uc04-ds): inside the JOIN intent transaction (`MANDATORY`, 10 s), `resolveSharer` runs before `lockJoinTerms` takes the team row, because it reads only an immutable invitation and never refuses; `PurchaseIntent.issue(…).attributedTo(sharer)` then freezes the sharer beside the price. Its lookup is the cached one of @fig-a-uc07-ds-lookup. @fig-a-uc07-ds-credit is B8–B9: the sharer is written with the membership in the settlement transaction, and the credit is written after the commit in a new one. If the process stops between the two, the credit is lost but not the attribution, which ADR 0003's reconciliation query restores.

#afig("a-uc07-ds-lookup", [UC7 design sequence diagram, inside `ShareInviteRepository.findByRef` (commerce-svc). The decorator adds the cache to one operation and forwards the rest; a Valkey failure only costs a database read.])


#afig("a-uc07-ds-credit", [UC7 design sequence diagram, B8–B9 with A8, A9 and E2 (commerce-svc). B8 runs in UC4's settlement transaction (A.2 S8); a sharer equal to the buyer is dropped there. The listener call is synchronous on the committing thread, after commit; its own `REQUIRES_NEW` transaction and its catch keep a credit failure from reaching the paid join.])

=== Operation Specifications <sec-a-uc07-ops>

@tbl-a-uc07-ops specifies the five operations that carry UC7's rules. Signatures are those in the code.

#atable(
  [UC7 operation specifications (commerce-svc unless stated). Errors are `BizException` error codes unless named otherwise.],
  <tbl-a-uc07-ops>,
  columns: (1.35fr, 1fr, 0.8fr, 1.1fr, 1fr),
  header: ([Operation; transaction], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`ShareInviteAppService.mint(userId: UUID, dealId: UUID): ShareInviteView`. Rate limit first; then `TransactionTemplate`, `REQUIRED`, 10 s; no lock.], [B1–B3: the member's invitation into the team, minted on first use.], [`userId` from the session; marker header checked by the controller.], [At most one active `biz_share_invite` row per team and sharer; the same reference on every call.], [`RATE_LIMITED` (`COMMON_429_001`); `GROUPBUY_PLACEHOLDER` (`GROUPBUY_404_001`) for no team or a non-member; `DEAL_CLOSED` (`GROUPBUY_409_002`).],
  [`ShareInviteAppService.resolve(ref: String, clientAddress: String): ResolvedInviteView`. No transaction.], [B6: team, product, sharer's nickname and deadline for the landing page.], [Anonymous caller.], [No write. Any stored invitation resolves, whatever its team's state.], [`RATE_LIMITED`; `INVITE_NOT_FOUND` (`GROUPBUY_404_002`); `IdentityUnavailableException` (HTTP 500, E1).],
  [`RedisCachedShareInviteRepository.findByRef(ref: String): Optional<ShareInvite>`. Caller's transaction, if any.], [B6, B7: the invitation a reference names.], [None.], [A valid cache hit returns without SQL; a miss reads PostgreSQL and caches for at most min(deadline, 20–30 s).], [None: a Valkey error or bad value is logged and treated as a miss.],
  [`GroupDealPurchaseSupportImpl.resolveSharer(inviteRef: String, dealId: UUID, buyerId: UUID): Optional<UUID>`. JOIN intent transaction (`MANDATORY`, 10 s), before lock 1.], [B7: the sharer to freeze, if the reference attributes this join.], [Called before `lockJoinTerms`.], [No write; empty for a blank, unknown, other-team or self reference.], [None: attribution never fails a purchase.],
  [`ShareCreditSubscriber.onMemberJoined(event: MemberJoined): void`. `AFTER_COMMIT`; own `REQUIRES_NEW`, 10 s.], [B9: records one credit for an attributed join.], [The admission has committed.], [At most one `biz_share_credit` row per `payment_id` (`ShareCreditRepository.recordIfAbsent`, `ON CONFLICT DO NOTHING`).], [None thrown: every failure, even a failed commit, is caught and logged (E2).],
)

=== State Machines <sec-a-uc07-states>

UC7 adds no lifecycle. `ShareInvite` and `ShareCredit` are immutable once written; whether a credit is pending, earned or void follows from its team's `DealStatus` when read (ADR 0003), so no subscriber updates a credit. The team's lifecycle is drawn in @sec-a-uc05; UC7 causes none of its transitions.

=== Patterns Applied <sec-a-uc07-patterns>

#atable(
  [Patterns in the UC7 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc07-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC7], [Roles → classes], [Consequences]),
  [Decorator (GoF, structural), applied as Cache-Aside (architectural)], [Every anonymous landing page and every attributed JOIN looks an invitation up by reference. A cache must cut that load without touching `ShareInviteAppService` or `GroupDealPurchaseSupportImpl`, and without the cache ever deciding an answer PostgreSQL would not give.], [Component: `ShareInviteRepository`. ConcreteComponent: `ShareInviteRepositoryImpl`. Decorator: `RedisCachedShareInviteRepository` (`@Primary`), which implements the same interface, holds the component in `source`, adds caching to `findByRef` and forwards `findByDealAndSharer` and `insertIfAbsent`. Clients: the two services. @fig-a-uc07-dc-invite, @fig-a-uc07-ds-lookup.], [The cache is added or removed by wiring alone, and an outage degrades to a database read (NFR-53). Liability: the decorator holds the concrete `ShareInviteRepositoryImpl`, not the interface, because it needs the package-private `findByRefWithDeadline`; so decorators cannot be stacked freely.],
  [Observer (GoF, behavioural), realised as in-process publish–subscribe], [A credit must follow an attributed join, but a credit failure must never roll back a paid admission, and group-buy's admission code must not know about credits.], [Subject (notifier): `ApplicationEventPublisher`, with Spring's multicaster and transaction synchronisation behind it. Event source: `PaidGroupDealService`. Event: `MemberJoined`. Observer: `ShareCreditSubscriber`; update: `onMemberJoined`, at `AFTER_COMMIT`. @fig-a-uc07-dc-credit, @fig-a-uc07-ds-credit.], [Crediting is decoupled in code and in fate (ADR 0003). Liability: no outbox, so a crash between commit and listener loses the credit until the reconciliation query is run.],
  [Idempotent Receiver (enterprise integration pattern)], [`MemberJoined` can be delivered again for the same participation, and two first shares can race.], [Keys: `payment_id` (`uq_biz_share_credit_payment`) for `ShareCreditRepository.recordIfAbsent`; team and sharer (`uq_biz_share_invite_deal_sharer`) for `ShareInviteRepository.insertIfAbsent`.], [A duplicate changes nothing and reports `false` (A9). Liability: the guarantee lives in the database indexes, so the mapper must keep its `ON CONFLICT` clause.],
)

=== Flow-to-Design Trace <sec-a-uc07-trace>

#atable(
  [UC7 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc07-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`shareTeam`, `requestInvitation`], [`GroupDealController.createInvite` → `ShareInviteAppService.mint`], [@fig-a-uc07-ds-mint],
  [B2], [`Team.hasMember`, `Team.isOpen`], [`GroupDealRepository.findById`, `PaidGroupDealRepository.hasActiveMember`, `currentTime`; `GroupDeal.status`, `expireTime` (UC5's aggregate)], [@fig-a-uc07-ds-mint],
  [B3], [`Team.invitationOf`; «create» `Invitation`; `showLink`], [`ShareInviteRepository.findByDealAndSharer`; `ShareInvite.mint`, `insertIfAbsent`, `findByDealAndSharer` again; `ShareInviteView`], [@fig-a-uc07-ds-mint, @fig-a-uc07-ds-lookup],
  [B4], [— (page only)], [Frontend `inviteUrl` from the returned reference (S2)], [—],
  [B5], [`openLink`], [Listing route parks the invitation (S2); `InviteController.resolve`], [@fig-a-uc07-ds-resolve],
  [B6], [`resolveInvitation`, `Invitation.lookUp`, `sharer`, `Account.publicNickname`, `showBanner`], [`ShareInviteAppService.resolve` → `ShareInvite.isWellFormedRef`, `findByRef` (decorator → `findByRefWithDeadline` → `selectByRef`), `GroupDealRepository.findById`, `RemoteUserAppService.publicNickname` (HTTP to identity-svc)], [@fig-a-uc07-ds-resolve, @fig-a-uc07-ds-lookup],
  [B7], [`Team.resolveSharer`, `Invitation.attributes`; «create» `PurchaseIntent`], [`GroupDealPurchaseSupportImpl.resolveSharer` → `ShareInviteRepository.findByRef`, `ShareInvite.attributes`; `lockJoinTerms`; `PurchaseIntent.issue`, `attributedTo`], [@sec-a-uc04-ds, @fig-a-uc07-ds-lookup],
  [B8], [`Team.admit`; «create» `Membership`; `memberJoined`], [`PaidGroupDealService.joinConfirmed` → `PaidGroupDealRepository.insertJoinedMember(…, sharer, now)`; `ApplicationEventPublisher.publishEvent(MemberJoined)`], [@fig-a-uc07-ds-credit],
  [B9], [`Membership.sharer`; «create» `ShareCredit`], [`ShareCreditSubscriber.onMemberJoined` → `ShareCredit.of`, `ShareCreditRepository.recordIfAbsent`], [@fig-a-uc07-ds-credit],
  [A1], [—], [`RateLimitGateway.tryAcquire` false → `RATE_LIMITED`], [@fig-a-uc07-ds-mint],
  [A2], [`showRefused(not found)`], [`GROUPBUY_PLACEHOLDER` (`GROUPBUY_404_001`) from `mint`], [@fig-a-uc07-ds-mint],
  [A3], [`showRefused(closed)`], [`DEAL_CLOSED` (`GROUPBUY_409_002`) from `mint`], [@fig-a-uc07-ds-mint],
  [A4], [— (page only)], [Listing route drops a malformed pair (S2)], [—],
  [A5], [`showGenericBanner`], [`VALIDATION_FAILED` at the controller, or `INVITE_NOT_FOUND` (`GROUPBUY_404_002`) from `resolve`], [@fig-a-uc07-ds-resolve],
  [A6], [—], [`RateLimitGateway.tryAcquire(RESOLVE_LIMIT, clientAddress)` false → `RATE_LIMITED`], [@fig-a-uc07-ds-resolve],
  [A7], [`resolveSharer` returns none], [`resolveSharer` returns empty; `attributedTo(null)` leaves the intent unattributed], [@sec-a-uc04-ds],
  [A8], [`admit` refused], [`joinConfirmed` returns a refused or replayed `ParticipationResult`; no `MemberJoined`], [@fig-a-uc07-ds-credit],
  [A9], [— (credit already exists)], [`recordIfAbsent` returns false (`ON CONFLICT (payment_id) DO NOTHING`)], [@fig-a-uc07-ds-credit],
  [E1], [—], [`RemoteUserAppService` throws `IdentityUnavailableException`; HTTP 500; banner without a name], [@fig-a-uc07-ds-resolve],
  [E2], [—], [Exception caught in `onMemberJoined` and logged; reconciliation per ADR 0003], [@fig-a-uc07-ds-credit],
)
