# UC10 Order branch meets the Stripe branch — conflict resolution and adaptation plan (Liu Jin)

Date: 2026-09-21. Branch: `feature/order-deal-20260919`. Counterpart: `stripe` (PR #126, not yet
merged into `dev`).

**Governing principle (decided here)**: wherever the two overlap, the `stripe` branch's
implementation wins. The Order side only renumbers, deletes and adapts; it never asks the Stripe
side to give ground. The reason is that Stripe's V9–V11 have already passed order-side review in
PR #126 and are integrated into `db/migration`, and `V11__refund_execution.sql` depends on the
`biz_refund` table created by `V9__stripe_checkout.sql`. Making that sequence move costs far more
than moving this branch.

## 0. Current baseline (measured 2026-09-21)

- This branch is already synced with `dev` (`92e5ca9`), merge commit `2a72b23`. The only conflict
  was `docs/agile/sprint-02/log.md` (both sides appended entries to the same section); both sides'
  entries were kept, ordered by date. Since this branch's merge base, `dev` has had **no backend
  source changes at all** (only `backend/README.md`), and this branch has touched none of the
  frontend changes, so everything except the log merged automatically.
- The `stripe` branch has already merged `dev` locally (`450e9df`) and fixed the frontend leftovers
  (`6a90d05`); it is waiting to be pushed to update PR #126.
- The two branches' API paths are completely disjoint: this branch adds only
  `GET /api/v1/orders/me`, Stripe adds only `/api/v1/payments/*`. **There is no interface-level
  conflict.**

`git merge-tree stripe feature/order-deal-20260919` reports only two textual conflicts:
`contracts/openapi.json` and `docs/agile/sprint-02/log.md`. **The real risks are the ones git
cannot report** — they all live in Flyway and in the test fixtures; see §1.2.

## 1. Conflict inventory

### 1.1 Textual conflicts (visible to git, 2 of them)

| File | Nature | Handling |
|---|---|---|
| `contracts/openapi.json` | Two independently regenerated JSON texts clash, though their contents are disjoint | Do not merge by hand; re-export after the code is merged, see §6 |
| `docs/agile/sprint-02/log.md` | Both sides appended to the same section; append-only | Keep both sides' entries, sorted by date |

### 1.2 Semantic conflicts (invisible to git, must be handled by hand)

**(1) A three-way Flyway version collision — highest priority**

Both sides occupy V9/V10/V11. The filenames differ but the versions are identical, and Flyway fails
at startup:

```
Found more than one migration with version 9
```

| Version | This branch | Stripe branch |
|---|---|---|
| V9 | `V9__order.sql` — `biz_order` plus 5 indexes | `V9__stripe_checkout.sql` — adds 13 columns and 3 unique indexes to `biz_payment`, creates `biz_refund`, creates `log_payment_review`, adds columns to `log_payment_webhook_event` |
| V10 | `V10__payment_refund.sql` — `biz_refund` plus `ALTER biz_payment order_id DROP NOT NULL` | `V10__payment_review_case.sql` — `biz_payment_review_case` |
| V11 | `V11__scheduler_lock.sql` — `sys_scheduler_lock` | `V11__refund_execution.sql` — adds 10 execution columns to `biz_refund`, creates `log_refund_attempt` |

**(2) `biz_refund` is created twice, and the two schemas are incompatible**

- Columns only Stripe has: `intent_id` (NOT NULL), `reason`, `source_event`, `provider_refund_id`,
  `first_attempt_at`, `next_attempt_at`, `lease_until`, `last_result`
- Columns only this branch has: `order_id`, `user_id`
- `deal_id`: nullable on Stripe, NOT NULL on this branch

This branch has **no Java code referencing `biz_refund` at all** (confirmed by grep: no repository,
mapper or service); the table is purely groundwork for Sprint 3. Moreover, per the handover memo in
`uc10-impl-plan-20260919.md` §1, `V10__payment_refund.sql` should have belonged to Yorik (UC6) in
the first place and landed on this branch by mistake (`16ab0d2`). Deleting the whole block
therefore loses no functionality that is in use.

**(3) `ALTER TABLE biz_payment ALTER COLUMN order_id DROP NOT NULL` is duplicated**

Each side wrote it once. PostgreSQL does not complain when DROP NOT NULL runs against an already
nullable column, so nothing explodes — but it is pure redundancy, and it leaves two different
answers in the migration history to "who relaxed this constraint". Stripe's
`V9__stripe_checkout.sql` keeps it; this branch deletes its copy.

**(4) The Stripe test fixture creates its own `biz_order`, which collides with this branch's real table**

`StripeLocalIntegrationTest` sets explicitly:

```java
"spring.flyway.locations=classpath:db/migration,classpath:db/stripe-fixture"
```

and `backend/blocbuy-bootstrap/src/test/resources/db/stripe-fixture/V2026092003__local_business_adapter.sql`
contains `CREATE TABLE biz_order (...)`. On the Stripe branch `biz_order` exists in no official
migration, so the fixture builds one itself. Once this branch's `V12__order.sql` lands, the same
database gets the real table from `db/migration` first and the fixture's copy second →
`relation "biz_order" already exists`, and `StripeLocalIntegrationTest` fails outright.

The good news is that the columns are compatible: the fixture's version is a subset of the real
table (it lacks `product_image`, `receiver_name`, `receiver_phone`, `shipping_address`,
`cancel_time`, `finish_time`, `create_by`, `update_by`, all of which are nullable in the real
table), and every column used by the fixture's `INSERT INTO biz_order(...)` exists in the real
table. So deleting the fixture's CREATE TABLE is enough; the INSERTs and SELECTs stay as they are.

The Stripe-side document `backend/docs/payment/stripe-local-integration-20260920.md` already
announced this step: once the team finishes the order module, this adapter is to be replaced and
the fields, transactions and event interfaces checked together.

**(5) The changes to `backend/docs/db/payment.sql` are void in full**

This branch changed two things, and with Stripe as the authority both are wrong:

- the new `biz_refund` section describes this branch's schema, which is not the table Stripe
  actually ships
- the `biz_payment.order_id` comment says "(V10 relaxed NOT NULL)", whereas under Stripe it is V9
  that does it

Payment-domain documentation belongs to Stripe. This branch reverts the file.

### 1.3 Explicitly not in conflict (do not delete by mistake)

- All of `com/blocbuy/order/**` Java: `OrderController`, `OrderAppService(Impl)`,
  `OrderSummaryView`, `Order`, `OrderStatus`, `OrderErrorCode`, `BizOrderDO` / `BizOrderMapper` /
  `OrderConverter` / `OrderRepositoryImpl`, `mapper/order/BizOrderMapper.xml`, and every unit test
  and IT. The Stripe branch has no class in the order package at all — its own `OrderHistory*`
  (controller/service/repository/mapper) was entirely self-deleted in `0b1f14d`, net change zero.
- The `biz_order` table body and 5 indexes in `V9__order.sql` (renumber only, contents untouched).
- `sys_scheduler_lock` in `V11__scheduler_lock.sql` (Stripe has no counterpart; note that this
  branch has no code using it either — it is Sprint 3 groundwork, kept but not counted as verified).
- `backend/docs/db/order.sql`, `docs/agile/sprint-02/uc10-impl-plan-20260919.md`,
  `docs/SWE5006-Team8-Project-Sprint2-Plan.docx`.
- MyBatis mapper resource paths: this branch uses `mapper/order/`, Stripe uses `mapper/payment/` —
  no collision.
- Maven POMs: Stripe changed `blocbuy-application/pom.xml` and `blocbuy-bootstrap/pom.xml` (the
  Stripe SDK dependency); this branch touches no POM.

## 2. Merge order (a precondition)

**`stripe` must reach `dev` first, and only then does this branch pull from `dev`.** The reverse
order is not acceptable.

- In the correct order, all of this branch's adaptation degenerates into one ordinary `merge dev`
  plus the local edits in §3–§6 of this document; no conflict resolution between two feature
  branches is needed.
- In the reverse order, the Stripe side would have to renumber V9–V11, and its V11 depends on the
  `biz_refund` that V9 creates — far more expensive, and it would reopen review on migrations that
  PR #126 has already approved.

Blocking condition: PR #126 merges into `dev`. Until then this branch must **not** start the
renumbering in §3 — if the final version numbers on `dev` turn out different from what we expect
(another branch jumping the queue and taking V12/V13, for example), the work has to be redone.

## 3. Step A — renumber the Flyway migrations

Stripe holds V9/V10/V11; this branch moves on to V12/V13.

```bash
cd backend/blocbuy-bootstrap/src/main/resources/db/migration
git mv V9__order.sql           V12__order.sql
git mv V11__scheduler_lock.sql V13__scheduler_lock.sql
git rm V10__payment_refund.sql
```

- `V12__order.sql`: not one character of the contents changes, only the filename.
- `V13__scheduler_lock.sql`: likewise.
- `V10__payment_refund.sql`: the whole file is deleted, for the reasons in §1.2 (2) and (3).

Before renumbering, confirm that the highest version on `dev` really is V11:

```bash
ls backend/blocbuy-bootstrap/src/main/resources/db/migration/ | sort -V | tail -5
```

If another branch has already taken V12/V13, move on to the next free pair and update the version
references in this document and in `uc10-impl-plan-20260919.md` to match.

## 4. Step B — discard the payment-side changes

```bash
git checkout origin/dev -- backend/docs/db/payment.sql
```

After reverting, confirm the file contains no `biz_refund` section and that the
`biz_payment.order_id` comment does not mention V10.

> Note: `payment.sql` on `dev` is also out of date with respect to Stripe's new V9 columns
> (`intent_id`, `operation`, `business_id`, `request_key`, `checkout_session_id` and the other 13),
> but that is a Stripe-side documentation debt and not this branch's to pay. If it matters after
> the merge, raise it in the PR and ask Yorik to update it.

## 5. Step C — adapt to the Stripe test fixture

Edit `backend/blocbuy-bootstrap/src/test/resources/db/stripe-fixture/V2026092003__local_business_adapter.sql`:
delete the whole `CREATE TABLE biz_order (...)` block and replace it with a one-line comment saying
the table is supplied by the official migration from `V12__order.sql` onwards.

The three tables `stripe_local_intent`, `stripe_local_product_terms` and
`stripe_local_expiry_request` in the same file stay untouched — they are Stripe's local test
substitutes and have nothing to do with order.

The `INSERT INTO biz_order(...)` and the three SELECTs in `PaymentBusinessFixture.java` need no
change (every column exists in the real table), but they **must be verified by an actual run**,
because the fixture inserts without `product_image` / `receiver_*` / `create_by` / `update_by` and
so depends on those columns being nullable in the real table.

Cross-module items to confirm (align with Yorik, do not change unilaterally):

- the fixture INSERT hardcodes `status='paid'`, which matches the lowercase convention of the
  `OrderStatus` enum — fine once confirmed
- the fixture's `biz_order.payment_id NOT NULL UNIQUE` is semantically equivalent to this branch's
  `uq_biz_order_payment_id` (a partial unique index, `WHERE deleted = false AND payment_id IS NOT
  NULL`)
- the fixture joins `biz_group_member.order_id`; that column has existed since V5, is always null,
  and is backfilled by the order side — out of scope for this adaptation

## 6. Step D — regenerate the contract

**Do not resolve the `contracts/openapi.json` conflict by hand.** The two sides' paths are
disjoint, so re-exporting produces the union:

```bash
cd backend && ./mvnw -B verify -Popenapi-export -DskipITs
```

After exporting, confirm that `contracts/openapi.json` contains both:

- `/api/v1/orders/me` (this branch)
- `/api/v1/payments/capabilities`, `/api/v1/payments/intents`,
  `/api/v1/payments/checkout-sessions`, `/api/v1/payments/{id}` (Stripe)

and confirm the change is **purely additive** relative to `dev` (no existing path or schema
modified), otherwise `contract-check` will demand the `contract-change-approved` label.

Frontend side: once `contracts/openapi.json` changes, the frontend's generated client must be
regenerated, or the local typecheck fails against the stale output (the `stripe` branch already hit
this once):

```bash
cd frontend && pnpm gen:api && pnpm check
```

## 7. Verification checklist (run every item after the adaptation; none is optional)

1. **Migration versions unique**: `ls db/migration | sort -V` shows no duplicate version, and
   V1–V13 are contiguous.
2. **Full backend build**: `cd backend && ./mvnw -B clean verify` (JDK 21; the default JDK 26 is
   rejected by the enforcer's `[21,22)` range). `clean` is required — the Stripe side already hit
   stale mapper XML left in `target`.
3. **Real Flyway migration**: in the previous step, the three Testcontainers tests
   `StripeCheckoutFlowTest`, `StripeLocalIntegrationTest` and `OrderRepositoryIT` must all be green
   — they cover the Stripe migrations, the Stripe fixture against the real `biz_order`, and the
   order persistence slice respectively. All the risk from §5 lands on the second one.
4. **Contract**: `contracts/openapi.json` regenerated, staged, no unstaged drift; `openapi-diff`
   reports additions only relative to `dev`.
5. **Frontend**: `cd frontend && pnpm gen:api && pnpm check && pnpm build`, both commands exit 0.
6. **Quality gates**: SpotBugs 0, Checkstyle 0, JaCoCo new-code coverage ≥ 80%
   (`docs/quality/quality-gates.md`).

## 8. Risks and rollback

| Risk | Likelihood | Impact | Handling |
|---|---|---|---|
| A local development database already ran the old version numbers (the order-side V9/V10/V11) | High | Flyway checksum mismatch; the application will not start | Delete the matching rows from `flyway_schema_history` by hand, or just rebuild the local database. CI uses a fresh testcontainer and is unaffected |
| After deleting `V10__payment_refund.sql`, the Sprint 3 order side needs `biz_refund.order_id` / `user_id` | Medium | The refund job has no order or user dimension | Open a **new** `V14__refund_order_link.sql` with `ALTER TABLE biz_refund ADD COLUMN order_id uuid, ADD COLUMN user_id uuid`; never go back and edit Stripe's V9/V11 |
| Stripe's `biz_refund.intent_id NOT NULL` blocks order-side writes | Medium | The order side has no intent to supply when initiating a refund | A Sprint 3 topic; agree the intent source with Yorik. No schema change now |
| The fixture change in §5 breaks `StripeLocalIntegrationTest` | Medium | Stripe's local integration test goes red | That test belongs to the Stripe side; @-mention Yorik in the PR before changing it, and on failure adjust the fixture to their field conventions rather than changing `V12__order.sql` |
| `sys_scheduler_lock` has no code using it and a reviewer questions it | Low | Review round trips | State in the PR description that it is Sprint 3 scheduling groundwork, citing `uc10-impl-plan-20260919.md` §2.3 |

Rollback: §3–§6 are entirely local single-branch edits with no external side effects. If any step
goes wrong, `git reset --hard` back to the commit right after merging `dev` and start again.

## 9. Evidence obligations (per the agile-evidence-capture convention, in the same PR as the adaptation)

- `docs/agile/sprint-02/log.md`: append an entry for this adaptation stating the renumbering map
  (V9→V12, V11→V13, V10 deleted), the reason for deleting `biz_refund`, the outcome of the fixture
  adaptation, and the measured output of every item in §7.
- `docs/quality/nfr-register.md`: Stripe's NFR-24 (migrations execute in version order through the
  default `db/migration` location, with no duplicate indexes) has now been genuinely exercised; add
  a sentence to its Evidence cell with the measured result after this branch's migrations were
  folded in. **Note that the NFR numbers visible on this branch predate the Stripe merge** — after
  merging, the numbers on `dev` are authoritative; do not carry over this branch's old ones.
- `backend/docs/payment/stripe-release-cleanup-20260920.md`: item 3 of that file's pre-live
  checklist ("local fixtures must stay test-only") changes meaning after §5, since the fixture no
  longer creates `biz_order` itself. Ask Yorik to update it after the merge, or make the edit in
  this PR and ask them to confirm.

## 10. Untracked files in the workspace, unrelated to this plan

`scripts/` (including `create_sprint2_issues.py`) and
`backend/blocbuy-bootstrap/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
are untracked on this branch and belong to B3 #96. Per the standing note in
`uc10-impl-plan-20260919.md` they stay outside this PR.
