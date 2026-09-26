# 0003. Referral credit is a post-commit subscriber

Date: 2026-09-25

## Status

Accepted

## Context

UC7 (#30) asks for three things. Invite links must carry the sharer. A join through one must record that sharer. And the credit must be written by a subscriber to the join event, with "no coupling into UC4's join transaction". Plan: `docs/agile/sprint-02/share-attribution-plan-20260924.md`.

Backend ADR 0002 already settled how team outcomes reach other domains. Groupbuy publishes an immutable record, and the subscribers run at `BEFORE_COMMIT`, so that a team's resolution and its consequences (confirmed orders, refunds owed) form one fact. The credit is the first consequence that should **not** share the admission's fate.

Before this change, groupbuy published `TeamSucceeded` and `TeamExpired` and nothing else. `TeamSucceeded` fires once per team, so it cannot say who joined through whom.

## Decision

**Groupbuy publishes `MemberJoined` for every accepted join.** `PaidGroupDealService.joinConfirmed` publishes `MemberJoined(dealId, productId, memberUserId, paymentId, sharerUserId, at)` after the member and the receipt are written. When the join fills the team, `MemberJoined` is published before `TeamSucceeded`. A replay and a rejection publish nothing.

**The sharer is frozen twice, and it is a fact before it is a credit.**

- It is first resolved from the invite reference when the JOIN intent is issued, under the team lock, and stored on `biz_purchase_intent.sharer_user_id` (V19) beside the price. It is not a quote term, so payment never sees it.
- At admission, `OrderFulfilmentAdapter` hands it to `joinConfirmed`, which writes it on `biz_group_member.sharer_user_id` (V18) in the admission transaction. That column is the durable "traced join".

**`ShareCreditSubscriber` credits at `AFTER_COMMIT`, in its own transaction, and never throws.** It is a `@TransactionalEventListener(phase = AFTER_COMMIT)`. It opens an explicit `TransactionTemplate` with `REQUIRES_NEW` inside a `try`, and inserts one immutable `biz_share_credit` row, which is unique on `payment_id`. It catches every failure, including a failed commit, and logs it at WARN.

**A credit's status is derived, not stored.** The team's status determines it: open is pending, succeeded is earned, expired is void. No subscriber updates a credit.

## Consequences

### Why not `BEFORE_COMMIT`, as in ADR 0002

A credit is not money. At `BEFORE_COMMIT`, a failed credit insert would roll back a confirmed payment's admission. The member, the order, the stock and the receipt would all be undone because of a referral. That is exactly the coupling #30 forbids. After the commit, the admission is final whatever the subscriber does.

### What `AFTER_COMMIT` costs, and how it is paid

- **The write needs its own transaction.** In `afterCommit` the committed transaction's connection is still bound to the thread. A plain write would join it, and no commit would follow. `REQUIRES_NEW` is mandatory, and Spring rejects any other transactional propagation on such a listener.
- **The subscriber must not throw.** An exception from `afterCommit` propagates to the caller, and here the caller is the webhook or recovery path of a payment that has already settled. The caller would answer 500, Stripe would retry, the replay would publish nothing, and the credit would still be missing. The transaction is therefore a template inside the `try` rather than an annotation around the method, so a failed *commit* is caught too.
- **A crash between the commit and the subscriber loses the credit, not the attribution.** `biz_group_member.sharer_user_id` was committed with the admission. This one set-based statement backfills every missing credit and is safe to repeat:

  ```sql
  INSERT INTO biz_share_credit (deal_id, payment_id, sharer_user_id, invitee_user_id, credit_time)
  SELECT m.deal_id, m.payment_id, m.sharer_user_id, m.user_id, m.join_time
  FROM biz_group_member m
  WHERE m.deleted = false AND m.sharer_user_id IS NOT NULL AND m.payment_id IS NOT NULL
  ON CONFLICT (payment_id) WHERE deleted = false DO NOTHING;
  ```

  Scheduling it is out of scope. The hard gates require explicit approval for a new scheduled job. Until then, an operator runs it by hand when the WARN line appears.

### Why the credit status is derived

The alternative was to have subscribers on `TeamSucceeded` and `TeamExpired` move credits from pending to earned or void. Both would run after their own commits, and so would the credit insert. Suppose a join commits, and then the team expires and commits. If the join's `afterCommit` insert is delayed past the expiry's `afterCommit` update, the update finds nothing, and the credit stays `pending` forever. A status derived from `biz_group_deal.status` cannot drift.

### Attribution never fails a purchase

At quote time, `GroupDealPurchaseSupport.resolveSharer` resolves a missing, unknown, other-team or self-referring reference to nothing, and the JOIN goes ahead unattributed. `joinConfirmed` also drops a sharer equal to the buyer rather than throwing inside the paid admission. A START ignores any reference.

### Security

- A reference is 128 random bits encoded as base64url. It names no user and cannot be forged into one.
- Only an active member can mint one. Anyone else, including a caller asking about a team that does not exist, gets the same 404.
- The browser never names a sharer. It can only present a reference that the server resolves.
- References are never logged.

### Idempotency and concurrency

- **Mint.** There is one invite per team and sharer, enforced by `uq_biz_share_invite_deal_sharer` and `INSERT … ON CONFLICT DO NOTHING`. A concurrent first mint waits for the winner and reads it back.
- **Credit.** There is one credit per participation, enforced by `uq_biz_share_credit_payment`.
- **Locks.** Minting takes no deal lock, and the credit write takes none either. The global lock order (team → intent → order → payment) is unchanged.

## Alternatives rejected

| Alternative | Why not |
| --- | --- |
| `BEFORE_COMMIT` subscriber | A credit failure would roll back paid admission; #30 forbids the coupling |
| Crediting inline in `joinConfirmed` | Same coupling, and not an Observer |
| `@Transactional(REQUIRES_NEW)` on the listener method | It cannot catch its own commit failure, which would then reach the settled webhook |
| Transactional outbox plus a dispatcher | Needs a new scheduled job (hard-gate approval) for a non-financial fact; the member column already gives a durable source to backfill from |
| Subscribers that update the credit status on `TeamSucceeded` / `TeamExpired` | They race the post-commit insert and can leave a credit stale |
| The subscriber in order, fed by an order-side event | Groupbuy owns the credit, and groupbuy → order would create a cycle; order already depends on groupbuy |
| The user id as the invite reference | Leaks identity and lets anyone name a sharer |
