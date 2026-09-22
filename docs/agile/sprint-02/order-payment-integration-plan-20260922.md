# Order/payment integration ports — implementation plan (Liu Jin)

Date: 2026-09-22. Branch: `feature/order-deal-20260919`. Conventions: `backend-constraints-skill`
plus `pgsql-ddl`. Prerequisite: `order-stripe-merge-plan-20260921.md` has been fully executed
(merge `d6de139`, IT fix `f3d1f4f`, storage slice `c84868d`).

## 0. Current baseline (measured 2026-09-22)

The payment slice reached `dev` and left behind **an integration contract with no production
implementation**. The class comment on `PaymentBusinessPort` says, verbatim:

> Integration contract owned jointly with groupbuy/order. No production stub is supplied.

Confirmed by grepping all of main: `PaymentBusinessPort` and `PaymentIntentPort` have zero
implementations in production code — only the test fixture `PaymentBusinessFixture`
(`blocbuy-bootstrap/src/test`). The consequences:

- `StripeCheckoutConfiguration` carries `@Profile("stripe-sprint2")`, with a comment saying
  "startup fails if the team has not supplied the real business collaborator". Under the default
  profile `StripeCheckoutService` is not registered at all.
- In other words, on `dev` the payment path is **not connected** to order or groupbuy, and
  `/api/v1/payments/*` is switched off by `StripeFeatureSwitch` under the default profile.

Already in place (`c84868d`):

- `V14__product_team_terms.sql` — `biz_product.team_target_size` / `team_lifetime_seconds`
- `V15__purchase_intent.sql` — `biz_purchase_intent` plus `uq_biz_purchase_intent_fulfilled_payment`
- the domain types `PurchaseIntent` / `PurchaseOperation` / `PurchaseIntentRepository`, and 4 new
  `OrderErrorCode` values
- the four-piece infrastructure slice plus 29 unit tests

Reusable as is, do not rebuild:

- `PaidGroupDealService.startConfirmed / joinConfirmed` — "create or join a team after payment" is
  already implemented, made idempotent internally by the `reserveAndLock` fingerprint
- `PaidGroupDealRepository.lockDeal / hasActiveMember / currentTime / complete` — **reusable only
  inside the groupbuy module**; order may reach them only indirectly, through the
  `GroupDealPurchaseSupport` introduced by amendment 1 in §3.2
- `OrderRepository.save / findByPaymentId / confirmByDealId / cancelByDealId`
- `ProductRepository.lockById`, `TeamPriceRepository.findLatestPolicy`

## 1. Boundaries and layer placement

The hard gate: **cross-domain access goes only through the other domain's application-layer
interface or query facade; reaching another module's Mapper, DO, RepositoryImpl or internal
aggregate is forbidden** (`architecture-and-ddd.md` L51). Therefore:

| Need | Wrong approach (rejected) | This plan |
|---|---|---|
| START must read the product and its group terms | an order-side mapper reading `biz_product` directly | catalog exposes the application facade `CatalogPurchaseSupport`, and order calls it |
| A successful payment must consume stock | an order-side `UPDATE biz_product` | same facade, through `consumeOneUnit` |
| JOIN must read the team's price and remaining seats | order calling `PaidGroupDealRepository.lockDeal` to obtain a `GroupDeal` | groupbuy exposes the application facade `GroupDealPurchaseSupport`, and order receives value objects only |
| Creating or joining a team | order writing `biz_group_member` | call the existing `PaidGroupDealService` |

The two `biz_product` columns (V14) belong to catalog. **`Product.reconstitute` is not touched** —
it has 14 call sites, and changing its signature for two columns has a large blast radius and zero
benefit. Instead catalog gains a small internal value object, `ProductTeamTerms`, and a mapper
query of its own; the aggregate root is unchanged.

Layer placement at a glance:

```
catalog  / application  CatalogPurchaseSupport (interface) + CatalogPurchaseSupportImpl
catalog  / domain       ProductTeamTerms (value object) + two new ProductRepository methods
catalog  / infra        ProductRepositoryImpl implementation + two new BizProductMapper statements
groupbuy / application  GroupDealPurchaseSupport (unpaid) + GroupDealSettlementSupport (paid) + both Impls
groupbuy / domain       PaidMember (value object) + PaidGroupDealRepository gains linkOrder / findPaidMember
order    / domain       PurchaseIntentRepository gains currentTime
order    / application  PurchaseIntentAppService  implements PaymentIntentPort
order    / application  OrderFulfilmentAdapter    implements PaymentBusinessPort
bootstrap / config      PaymentIntegrationConfiguration (wiring, replacing the fixture)
```

> Amendment made during implementation, 2026-09-22: the plan originally gave the group terms their
> own `ProductTeamTermsRepository`. What actually landed adds `findTeamTerms` / `consumeOneUnit` to
> the existing `ProductRepository`. The group terms belong to the Product aggregate, and under the
> "one repository per aggregate root" rule the same aggregate must not get a second repository.

The adapters live in application rather than infrastructure: they orchestrate use cases and
transactions and call three domains' applications and ports, but touch no SQL. The ArchUnit rule
`application_uses_ports_not_tech` only forbids depending on MyBatis/Redis directly, so this
satisfies it.

## 2. PR breakdown

| PR | Contents | Depends on | Notes |
|---|---|---|---|
| PR-A | catalog facade: `ProductTeamTerms` + port + impl + `CatalogPurchaseSupport` + unit tests | — | Additive only; zero change to existing catalog behaviour |
| PR-B | `PurchaseIntentAppService` (`PaymentIntentPort`) + unit tests | PR-A | START/JOIN pricing and intent issuance |
| PR-C | `OrderFulfilmentAdapter` (the six `PaymentBusinessPort` methods) + unit tests | PR-B | The core; lock order and idempotence |
| PR-D | bootstrap wiring + `StripeLocalIntegrationTest` moved onto the real implementations + ADR + NFR rows | PR-C | The fixture retires |

All four PRs were completed on 2026-09-22: `d24f8c6` (A), `0dc6fe1` (B), `ace9c24` (C), and PR-D in
this round. Every deviation found during implementation is recorded in the matching §3.x section.

PR-A should merge first, so the catalog owner reviews nothing but an additive facade. PR-C is the
heaviest and gets its own review.

## 3. Implementation specification

### 3.1 PR-A — the catalog purchase-support facade

`ProductTeamTerms` (domain, `com.blocbuy.catalog.model`):

```java
public record ProductTeamTerms(UUID productId, Integer targetSize, Integer lifetimeSeconds) {
    public boolean startable() { ... }   // both columns non-null, targetSize >= 2, lifetime within [2400, 86400]
}
```

The range checks live in the domain, not in a CHECK constraint (pgsql-ddl: constraints are the
application's job). The `V14` comments already state the same bounds; the two must stay identical.

`CatalogPurchaseSupport` (application; catalog's only public entry point here):

```java
public interface CatalogPurchaseSupport {
    /** Product row lock + latest price policy + group terms; used to price a START. The caller holds the transaction. */
    StartQuote lockStartQuote(UUID productId, Instant now);

    /** Read-only checks: on sale, in stock, currency. A JOIN pays the team's own price and needs no policy. */
    PurchasableProduct requirePurchasable(UUID productId);

    /** After a successful payment, take one unit and increment sales; a conditional update returning false when stock is gone. */
    boolean consumeOneUnit(UUID productId);
}
```

`StartQuote` carries at least `productId / title / coverImage / currency / amount (the policy
price) / targetSize / lifetimeSeconds`.

Pricing checks (lifted from the rules the fixture already proved, now written into the catalog
facade):

- the policy is not soft-deleted, `now >= valid_from`, and `now + 2400s < valid_until` (the quote
  window must fall entirely inside the policy's validity)
- `currency = 'sgd'`, `amount > 0`, `amount < product.originalPrice`
- the product has `status = on_sale`, `stock >= 1`, `currency = 'sgd'`
- the group terms are `startable()`

Any failure throws `BizException` with catalog's own `ErrorCode` (add one if it is missing; a bare
`RuntimeException` is forbidden).

`consumeOneUnit` uses a conditional update — never read-then-write:

```sql
UPDATE biz_product SET stock = stock - 1, sold_count = sold_count + 1,
       version = version + 1, update_time = clock_timestamp()
WHERE id = #{productId} AND deleted = false AND stock > 0
```

### 3.2 PR-B — `PurchaseIntentAppService implements PaymentIntentPort`

```java
UUID createIntent(UUID user, String operation, UUID businessId)
```

The contract, from the port's comments: identifiers only, with the price and group terms read
server-side; runs inside the caller's short transaction; performs no remote I/O.

- `START`: `businessId` is a productId. `lockStartQuote` supplies the price and terms →
  `dealId = UuidV7.next()` (**reserved**; the team does not exist yet, but the later lock order
  needs a target) → `quoteValidUntil = now + 2400s`, `businessDeadline = now + lifetimeSeconds`
- `JOIN`: `businessId` is a dealId. `lockDeal` supplies the team → check `status = OPEN`,
  `now < expireTime`, `currentSize < targetSize`, and that this user is not already a member
  (`hasActiveMember`) → `requirePurchasable(deal.productId())` → `amount = deal.dealPrice()`,
  `targetSize = deal.targetSize()`, `businessDeadline = deal.expireTime()`,
  `quoteValidUntil = min(now + 2400s, deal.expireTime())`
- any other operation: `BizException(PURCHASE_INTENT_TERMS_INVALID)`

Write through `PurchaseIntentRepository.save` and return the intent id.

> Note the `CheckoutQuote` invariants: `quoteValidUntil` and `businessDeadline` are both truncated
> to microseconds, `currency` accepts only `sgd`, and `amount` must have scale 2. `PurchaseIntent`
> already uses the same rules, so the two cannot disagree.

> Amendments made during implementation (PR-B landed; 3 items), 2026-09-22:
>
> 1. **JOIN does not call `PaidGroupDealRepository` directly.** §0 originally listed
>    `lockDeal / hasActiveMember / currentTime` as "directly reusable", but `lockDeal` returns
>    groupbuy's aggregate root `GroupDeal`, and order holding it breaks the same hard gate — the
>    identical reason PR-A rejected "order reads `biz_product` directly". Instead groupbuy gains the
>    application facade `GroupDealPurchaseSupport.lockJoinTerms(dealId, buyerId)`, which performs
>    all four checks (OPEN / not expired / not full / neither the leader nor an existing member)
>    inside the team row lock and hands out only the `JoinTerms` value object. Those checks
>    deliberately duplicate `GroupDeal.joinPaid`: rejecting before payment is free, rejecting after
>    payment costs a refund.
> 2. **The clock comes from order's own port.** `PurchaseIntentRepository` gains `currentTime()`
>    (`clock_timestamp()`), written the same way as in `PaidGroupDealRepository` /
>    `TeamPriceRepository`. The application server's clock is not used: both deadlines frozen into
>    the intent will later be compared against `timestamptz` values in the database.
> 3. **`PurchaseOperation.parse` replaces `fromCode` for request input.** `operation` arrives in the
>    HTTP body, so an unknown value is a user mistake rather than a programming error, and
>    `fromCode`'s `IllegalArgumentException` carries the wrong meaning. A new `parse` returning
>    `Optional` was added, and the caller converts to its own `BizException`. `ORDER_409_004`
>    (`TEAM_NOT_JOINABLE`) is therefore permanently vacant — joinability is groupbuy's rule,
>    expressed by its own error codes, and the number is retired rather than reused.

### 3.3 PR-C — `OrderFulfilmentAdapter implements PaymentBusinessPort`

**Lock order (global, single, immutable)**: `biz_group_deal` → `biz_purchase_intent` →
`biz_payment`. Any other order deadlocks against a concurrent JOIN. The `PurchaseIntentRepository`
Javadoc already fixes this, and every adapter method follows it.

| Method | Implementation notes |
|---|---|
| `lockQuote(user, intentId)` | `lockDeal(intent.dealId())` first (read the intent once without a lock to get the dealId, then lock and re-read) → `lockById(intentId)` → `requireOwner(user)` → not yet fulfilled → for JOIN, re-verify the team is still joinable → convert to `CheckoutQuote` |
| `lockForSettlement(quote)` | Re-read in the same lock order, convert the re-read result to a `CheckoutQuote` and compare it with the argument using `equals`; a mismatch is an `IllegalStateException` (port semantics: a technical failure must throw, it is not a business rejection) |
| `fulfil(event)` | See below |
| `fulfilmentEvidence(event)` | Look up `OrderRepository.findByPaymentId` and the team membership by paymentId; neither present → `NONE`; both present and agreeing on user/deal/amount/currency/status → `COMPLETE`; anything else → `INCONSISTENT` |
| `lockExpiredTeam(dealId)` | `lockDeal` → throw `IllegalStateException` when `status != EXPIRED` |

The order of `fulfil(PaymentSucceeded)` — every step must be idempotent:

1. Lock the intent (the caller already holds the team lock taken before the payment lock)
2. Already fulfilled: same payment → read back the existing order and return
   `Outcome(dealId, orderId, null)`; a different payment →
   `Outcome(null, null, "INTENT_ALREADY_FULFILLED")`
3. Buyer not active → `Outcome(null, null, "BUYER_INACTIVE")`
4. `requirePurchasable` fails → `Outcome(null, null, "PRODUCT_UNAVAILABLE")`
5. `START` → `PaidGroupDealService.startConfirmed(NewDealTerms(...), paid)`; `JOIN` →
   `joinConfirmed(dealId, paid)`. A result other than `applied` →
   `Outcome(dealId, null, "GROUP_REJECTED")`
6. `OrderRepository.save(Order.create(terms, event.confirmedAt()))`
7. Backfill `biz_group_member.order_id`
8. `PurchaseIntentRepository.markFulfilled(intent, paymentId)`
9. `consumeOneUnit` fails → throw and roll the whole thing back (stock is a funds-safety concern,
   not a business rejection)

An `Outcome` rejection code must match `[A-Z_]{1,64}` (already enforced by the record constructor)
and must carry nothing sensitive.

**An order is born `PENDING`**, not the `'paid'` the fixture uses. `OrderStatus` has only
`pending/confirmed/cancelled`; `pending → confirmed` is driven by TeamSucceeded and
`pending → cancelled` by TeamExpired. The fixture's `status='paid'` insert and the `o.status='paid'`
check in `fulfilmentEvidence` must both change to `pending` together — that is PR-D, it edits
Yorik's files, so @-mention them in the PR.

> Amendments made during implementation (PR-C landed; 5 items), 2026-09-22:
>
> 1. **groupbuy gets a second facade, `GroupDealSettlementSupport`.** Same hard gate as PR-B:
>    backfilling `biz_group_member.order_id`, reading a member by paymentId, locking the team row
>    and asserting the team has expired all land on groupbuy's tables, and order must not write them
>    itself. The paid and unpaid sides are split into two facades: `GroupDealPurchaseSupport` judges
>    joinability and rejects, while `GroupDealSettlementSupport` **locks without judging** — at
>    settlement time the team may already be full, succeeded or expired, and only
>    `PaidGroupDealService` is entitled to rule on that once the money is in. On the domain side
>    `PaidGroupDealRepository` gains `linkOrder` / `findPaidMember`, the latter returning the new
>    value object `PaidMember`.
> 2. **Every rejection must precede the first write.** The original nine steps had `consumeOneUnit`
>    (step 9) throwing on failure — that stands, but a harder rule is needed alongside it: when an
>    `Outcome` carries a rejection the caller **commits** the transaction and then raises a refund,
>    so returning a rejection after any write would commit that write next to the refund. Steps 2,
>    3 and 4 (already fulfilled, buyer, product) are therefore all read-only checks, and from step 5
>    onwards the code may only throw, never reject. `linkOrder` returning false is the same case:
>    throw.
> 3. **A START has no team row to lock.** `lockTeam` calls `lockDeal` with the reserved dealId,
>    gets nothing back, and stops there. The fixture's `pg_advisory_xact_lock` is not reproduced:
>    the reserved id is unique to one intent, any concurrent writer that could reach it blocks on
>    the intent row lock first, and `PaidGroupDealService` additionally locks its receipt by
>    paymentId — the advisory lock adds no protection.
> 4. **`fulfilmentEvidence` does not check the order status.** The fixture queried `o.status='paid'`;
>    under the real implementation an order is created `pending`, becomes `confirmed` when the team
>    succeeds and `cancelled` when it expires, and none of those lifecycle transitions unmakes the
>    fact that fulfilment happened. It now checks the ids, amount and currency across member ↔ order
>    ↔ intent, plus `member.status='paid'`.
> 5. **Business numbers are generated here.** The repository has no order-number or deal-number
>    generator. The format is `prefix + UTC date + the low 12 hex digits of a UUID`, derived from
>    paymentId / dealId rather than from a sequence — replaying the same payment cannot mint a
>    second number.
>
> **Additional note on lock order (for reviewers)**: this adapter locks team → intent, while
> `PaidGroupDealService` internally locks receipt → team. Combined, that is team → intent → receipt
> → team (re-entrant), which is a cycle on paper. It cannot deadlock in practice: a receipt is keyed
> by paymentId, so two concurrent transactions can never contend for the same receipt row. PR-D's
> concurrency IT must cover this.

### 3.4 PR-D — wiring, and the fixture retires

A new `PaymentIntegrationConfiguration` (bootstrap, **with no profile**) registers the two adapters
as `PaymentBusinessPort` / `PaymentIntentPort` beans. Whether `@Profile("stripe-sprint2")` can come
off `StripeCheckoutConfiguration` is Yorik's call — removing it would mean payment is wired under
the default profile too, with `StripeFeatureSwitch` still the runtime switch, so the
default-off behaviour is unaffected.

`StripeLocalIntegrationTest` switches to the real implementations; `PaymentBusinessFixture` either
keeps only its `stripe_local_*` local substitutes or is deleted outright — depending on whether
Yorik still needs to run payment without the order module.

> Amendments made during implementation (PR-D landed; the plan is fully executed at this point),
> 2026-09-22:
>
> 1. **`StripeCheckoutConfiguration` keeps its `@Profile`.** The plan said "Yorik decides whether it
>    comes off". Measurement overruled it: `blocbuy.payment.checkout.success-url` / `cancel-url`
>    appear in **no** `application*.yml` and are injected only by the ITs'
>    `@SpringBootTest(properties=…)`. Removing the profile would make every context under the
>    default profile fail at startup. Besides, the profile name is the one `StripeFeatureSwitch`
>    reads — it was always the rollout switch. Only the comment changed: from "startup fails without
>    the business collaborator" to "this is the rollout switch".
> 2. **`PaymentBusinessFixture` is deleted in full**, with no half kept. Its `createIntent` and all
>    six port methods are taken over by the real implementations; `lockGroup` is replaced by
>    `GroupDealSettlementSupport.lockTeam`; and `PaymentLifecycleFixture` retains only the two
>    things production code does not do — forcing a team past its deadline, and driving local
>    session cleanup.
> 3. **Two fixture tables deleted.** `stripe_local_intent` is taken over by `biz_purchase_intent`
>    (V15) and `stripe_local_product_terms` by the two `biz_product` columns (V14). Only
>    `stripe_local_expiry_request` remains — it substitutes for a scheduler this slice does not
>    ship, so it has no production counterpart.
> 4. **The `status='paid'` inconsistency disappeared on its own.** It existed only in the fixture's
>    `INSERT INTO biz_order(... 'paid' ...)` and in the `o.status='paid'` check inside
>    `fulfilmentEvidence`, and both went with the fixture. The real implementation creates orders as
>    `pending`, and `fulfilmentEvidence` does not look at order status. Nobody needed to confirm
>    anything.
> 5. **The concurrency case lands in `StripeLocalIntegrationTest`.** Added
>    `eightConcurrentWebhookReplaysStillProduceOneMemberAndOneOrder`: eight threads replay the same
>    payment simultaneously, asserting 1 team, 1 member, 1 order, 0 refunds, stock down by exactly
>    1, and the intent consumed exactly once.

## 4. Concurrency and idempotence design (the hard gate requires each line to be stated)

| Dimension | Design |
|---|---|
| Database constraints | `uq_biz_purchase_intent_fulfilled_payment` (one payment consumes one intent), `uq_biz_order_payment_id` (one payment, one order), `uq_biz_order_deal_user` (one order per user per team), and the existing `biz_group_member` unique keys |
| Locks / versions | Row locks in the fixed order group → intent → payment; `biz_purchase_intent.markFulfilled` does a CAS guarded by both `version` and `fulfilled_payment_id IS NULL`; stock uses the conditional update `stock > 0`; order state transitions use a `version` CAS |
| Idempotency keys | The provider event id (`log_payment_webhook_event`, already on the payment side) → `paymentId` (every idempotent entry point on this side) → `PaidGroupDealService`'s fingerprint |
| Failure recovery | The whole flow runs inside the caller's single local transaction; a throw at any step rolls everything back, leaving nothing half-built. On the payment side `fulfilmentEvidence` is the reconciliation entry point, and `INCONSISTENT` is handled manually or through the recovery endpoint |

Redis does not participate: orders, deal progress and payments are all strongly consistent facts,
and PostgreSQL is the only source of truth. There is no message broker; cross-aggregate consistency
comes from in-process calls inside the same local transaction.

## 5. Contract impact

**None.** This change adds and modifies no controller, endpoint, DTO or springdoc annotation — what
it fills in are internal integration ports. `contracts/openapi.json` must show zero diff; confirm
before committing with:

```bash
git diff --exit-code -- contracts/
```

A diff here means the HTTP surface was changed by mistake: re-export per item 6 of the skill and
explain why.

## 6. Tests and coverage self-check

- Domain: `ProductTeamTerms` boundary values (both ends of 2400/86400, null, targetSize = 1)
- Application: both adapters stub all three collaborators with Mockito, and **every failure branch
  needs a case** — the four `Outcome` rejection codes, the mismatch branch of `lockForSettlement`,
  and all three `fulfilmentEvidence` results
- Integration: `StripeLocalIntegrationTest` runs against the real implementations, plus a new
  concurrency case proving a replayed webhook produces exactly one membership and one order
- Coverage: run the skill's JaCoCo aggregate self-check before every PR; the target for touched
  files is 100%, not the 80% floor:

```bash
cd backend && ./mvnw -B verify
python3 - <<'EOF'
import xml.etree.ElementTree as ET
t = ET.parse('blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml')
for sf in t.iter('sourcefile'):
    missed = [l.get('nr') for l in sf.findall('line')
              if int(l.get('mi')) > 0 or int(l.get('mb')) > 0]
    if missed:
        print(sf.get('name'), '-> missed lines:', ','.join(missed))
EOF
```

The JDK must be 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`); the default 26 is rejected
by the enforcer's `[21,22)` range.

## 7. Risks

| Risk | Likelihood | Impact | Handling |
|---|---|---|---|
| The `dealId` a START reserves is written into the intent before the team exists, leaving a dangling reference if the team never opens | High (by design) | No real harm: `deal_id` has no FK, and unfulfilled intents can be cleaned up by deadline | Already stated in the V15 comments; the cleanup job is listed for Sprint 3 |
| Getting the lock order wrong and deadlocking against a concurrent JOIN | Medium | Intermittent production timeouts | The lock order is written into the port Javadoc and into this document; PR-C must have a concurrency IT |
| The two `biz_product` columns are null for every existing product, so every START is refused | High | The start-a-team path is unusable until the data is filled in | Expected behaviour, stated in the V14 comments; populating them from an admin surface is out of scope here, and PR-D's NFR row must record this unmet part |
| The fixture's `status='paid'` disagrees with `OrderStatus` | Confirmed present | The real adapter goes through the enum and `fromCode` would throw | Fixed as part of PR-D; it edits a payment-side file, so Yorik must confirm |
| The catalog owner rejects the new facade | Medium | PR-A stalls | The facade is additive only and is the single compliant route the hard gate allows; if it is still rejected, the fallback is for catalog to implement the pricing half of `PaymentIntentPort` itself, to be reconsidered at that point |

## 8. Evidence obligations

Inside each PR: one entry in `sprint-02/log.md`. PR-D additionally adds an NFR row (funds safety /
idempotence: one payment produces at most one membership and one order, and a replay produces no
second copy) and an ADR (why the purchase intent belongs to the order module, and why the group
terms belong on `biz_product`). The two `docs/db/catalog.sql` columns ship with PR-A.
