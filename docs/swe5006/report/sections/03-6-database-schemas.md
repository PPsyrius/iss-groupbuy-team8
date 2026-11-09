<!-- report
rubric: 3.6
owner: Liu Jin
plan: #679
sources: figures/core-erd.svg (portrait copy of ../presentation/src/diagrams/core-erd.svg), ../presentation/src/diagrams/full-erd.svg; backend Flyway V1–V28; .claude/skills/pgsql-ddl/SKILL.md
owns: R30, R44
status: draft
-->

## 3.6 Database Schemas

BlocBuy keeps all persistent state in one PostgreSQL 18 database, in the schema `domain`: 29 tables created by the Flyway migrations V1 to V28. Each aggregate root has its own table, and relationships between tables are logical only: there are no foreign keys, and the application and unique indexes keep the references consistent. Figure 3.139 shows the eight tables at the centre of the money path; Appendix C has the full diagram and a list of all 29 tables with their purpose and owning context.

#### Core schema

The migrations live in `backend/blocbuy-bootstrap/src/main/resources/db/migration`: 28 SQL files, plus one `.conf` file that lets V23 build its indexes with `CREATE INDEX CONCURRENTLY` outside a transaction. The migrations are forward-only: there are no undo scripts. A repeatable seed script, `R__demo_catalog_seed.sql`, loads demonstration products. For each context, the reviewed source DDL is kept in `backend/docs/db/` (`catalog.sql`, `groupbuy.sql`, `order.sql`, `payment.sql` and others), and the migrations are derived from it.

Figure 3.139 draws the eight core tables in crow's foot notation. Each line is a logical foreign key, and its ends give the cardinality; Table 3.32 repeats the keys and cardinalities in print size.

![Figure 3.139: Core schema of the money path, entity-relationship diagram in crow's foot notation (FK* marks a logical foreign key with no database constraint). Each paid payment yields at most one membership, one order and one refund, and unique indexes enforce each of those limits.](../figures/core-erd.svg)

Each cardinality follows from a business rule. A product has any number of teams over time (`biz_group_deal.product_id`). A team has at least one member, its leader, and at most one active membership per buyer, enforced by the partial unique index `uq_biz_group_member_deal_user` on `(deal_id, user_id)`. A purchase intent is the server's priced offer for one START (open a new team) or JOIN (enter an existing team); a buyer may retry Checkout, so one intent can have several payment rows, but `uq_biz_payment_pending_intent` allows only one pending payment per intent. One successful payment produces at most one membership, one order and one refund obligation: `uq_biz_group_member_payment_id`, `uq_biz_order_payment_id` and the unique `payment_id` column of `biz_refund` (index `biz_refund_payment_id_key`) make a replayed Stripe webhook collide at the database instead of creating a second row. Table 3.32 lists the keys.

<!-- table: split -->

Table 3.32: Keys and cardinalities of the core tables. "Logical FK" columns reference another table's `id` without a database constraint; partial unique keys exclude soft-deleted rows.

| Table | Logical FK columns | Unique keys | Cardinality |
| --- | --- | --- | --- |
| `biz_product` | none | `uq_biz_product_product_no` (partial) | One product has 0..* teams |
| `biz_group_deal` | `product_id`, `leader_user_id` | `uq_biz_group_deal_deal_no` (partial) | One team has 1..* members and 0..* purchase intents |
| `biz_group_member` | `deal_id`, `user_id`, `payment_id`, `order_id`, `sharer_user_id` | `uq_biz_group_member_deal_user` (partial), `uq_biz_group_member_payment_id` | One buyer has 0..1 active membership per team; one payment yields 0..1 membership |
| `sys_user` | none | `uq_sys_user_username`, `uq_sys_user_email` | One user has 0..* memberships, intents, payments and orders |
| `biz_purchase_intent` | `user_id`, `deal_id`, `product_id`, `fulfilled_payment_id`, `sharer_user_id` | `uq_biz_purchase_intent_fulfilled_payment` (partial) | One intent has 0..* payment attempts, at most one pending, and 0..1 fulfilling payment |
| `biz_payment` | `intent_id`, `user_id`, `business_id` (the team), `order_id` | `uq_biz_payment_payment_no`, `uq_biz_payment_request_key`, `uq_biz_payment_pending_intent`, `uq_biz_payment_checkout_session`, `uq_biz_payment_provider_intent` | One payment yields 0..1 order and 0..1 refund |
| `biz_order` | `payment_id`, `deal_id`, `user_id`, `product_id` | `uq_biz_order_order_no` (partial), `uq_biz_order_payment_id`, `uq_biz_order_deal_user` (partial) | One buyer has 0..1 active order per team |
| `biz_refund` | `payment_id`, `intent_id`, `deal_id` | `biz_refund_payment_id_key`, `uq_biz_refund_provider_id` (partial) | One refund per payment |

All eight tables have a UUIDv7 primary key `id`.

#### Mapping decisions

The object-to-table mapping follows the team's PostgreSQL conventions, with these reasons.

**One table per aggregate root, no inheritance mapping.** Each aggregate in the domain model (for example `GroupDeal`, `PurchaseIntent`, `CheckoutPayment`, `Order`, `Product`) maps to one table, and its child entities to tables of their own (`biz_group_member` for team members). The domain has no class hierarchy that needs a single-table or joined-table inheritance mapping. The mapping is written by hand: MyBatis XML mappers read rows into data objects, and hand-written converters turn them into domain objects (section 3.2.4 describes this persistence strategy).

**Table prefixes.** A prefix states what kind of data a table holds: `biz_` for business and transaction data (14 tables), `sys_` for users, roles, jobs and system queues (9), `rel_` for many-to-many links (`rel_user_role`, `rel_role_permission`) and `log_` for append-only audit records (4). Names are lowercase `snake_case`; instants are `timestamptz` in UTC and money is `numeric(18,2)`.

**UUIDv7 surrogate keys.** Every table except one has a native `uuid` primary key filled by PostgreSQL 18's `uuidv7()`. A UUIDv7 starts with a timestamp, so new keys arrive in order and the primary-key index stays compact, unlike random UUIDs; and unlike a sequence, a key can be created anywhere, which matters when the same rows live in separate databases on the SWE5001 environment. Readable business numbers (`product_no`, `deal_no`, `order_no`, `payment_no`) are separate unique columns. The exception is `sys_consumed_message`, whose primary key is the natural pair `(event_id, consumer)`: that pair is exactly the duplicate an event consumer must reject. The outbox row's UUIDv7 doubles as the integration event's `eventId` (backend ADR 0007).

**No foreign keys and no CHECK constraints.** No migration creates either; the database enforces only `PRIMARY KEY`, `UNIQUE` and `NOT NULL`. References, allowed status values and deletion order are checked in the application, for example by `DealStatus.canTransitionTo` and, before a listing is deleted, by `AdminListingAppServiceImpl.delete`, which refuses with `PRODUCT_REFERENCED` while any team, quote or sale still points at the product. Three reasons drive the rule:

- On the SWE5001 environment each service has its own database (backend ADR 0008), so a link such as `biz_group_deal.leader_user_id` to `sys_user`, which crosses from commerce into identity, cannot be a foreign key there. One schema is kept for both environments, so the rule holds on `dev` as well.
- A START purchase intent reserves its team's `deal_id` before the team row exists (backend ADR 0001), so that the team row lock has a target; a foreign key would reject that row.
- Status vocabularies change with the business rules. Keeping them as `varchar` with the allowed values in the domain enums avoids a schema migration for every new state.

The cost is that orphan rows are possible if application code is wrong. Soft delete, below, removes the most common cause, and the unique indexes stop the most damaging duplicates. The first migration's header comment (V1) still says that foreign keys and CHECK constraints are "pushed down to the database"; every later migration states the opposite rule, and none creates either.

**Unique indexes as race backstops.** Where two requests can race, a unique index catches any duplicate that gets past the row locks: `uq_biz_group_member_deal_user` (one active membership per buyer and team, V5), `uq_biz_group_member_payment_id` and `uq_biz_group_participation_receipt_payment` (one membership and one participation receipt per payment, V7), `uq_biz_payment_request_key` (one Checkout session per client idempotency key, V9), `uq_log_payment_webhook_event_event_id` (each Stripe webhook event processed once; the event row is inserted before processing, V2), `biz_refund_payment_id_key` (one refund obligation per payment, V9), `uq_biz_refund_provider_id` (one obligation per provider refund, V11), `uq_biz_order_payment_id` (one order per payment, V12) and `uq_biz_purchase_intent_fulfilled_payment` (one fulfilled purchase intent per payment, V15). Three of them, `uq_biz_group_member_deal_user`, `uq_biz_payment_request_key` and `uq_biz_purchase_intent_fulfilled_payment`, are partial (`WHERE deleted = false`), so a soft-deleted row does not block a new one.

**Soft delete and audit columns.** 22 of the 29 tables carry `deleted boolean NOT NULL DEFAULT false`, and the ordinary business tables carry `create_time`, `update_time`, `create_by` and `update_by`. A delete sets the flag, so a row that other rows still reference never disappears. The seven tables without the flag are append-only records and queues: the four `log_` tables, `biz_payment_review_case`, `sys_outbox` and `sys_consumed_message`.

**Version column.** Ten tables carry an integer `version`, with five different meanings:

- an optimistic lock on `biz_product`, `biz_purchase_intent` and `biz_payment`: the update adds `AND version = …` with the version read earlier and treats zero affected rows as a conflict (for example in `BizProductMapper.xml` and `PaymentMapper.xml`);
- a fencing token on `biz_refund`: a refund worker claims a row with a lease, and only the holder of the latest version may write the Stripe result (`RefundMapper.xml`);
- a recorded value on `log_refund_attempt`, an append-only table whose `version` column stores the refund's fencing version that each attempt ran under, so an audit can tell which lease made which call;
- a revision number on `biz_team_price_policy`, where each price change is a new row and the highest version is the current price;
- a change counter on `biz_order`, `biz_group_deal`, `sys_scheduler_lock` and `sys_scheduled_item_retry`, incremented on every update but never compared by production code; orders change state by team in updates guarded by their pending status (`BizOrderMapper.xml` also holds a version-checked `updateIfVersion` with no production caller), and the team row relies on its guarded update (guarded update, section 3.2.8).

`biz_purchase_intent` has no status column: whether an intent is open, fulfilled or expired follows from `fulfilled_payment_id` and `expired_at`.

**Indexes for the scheduled jobs.** The background jobs read through partial indexes so that their cost does not grow with history: `idx_biz_group_deal_status_expire` for the team-expiry sweep, `idx_biz_refund_due` (unfinished refunds only) for the refund worker, `idx_biz_purchase_intent_due_expiry` (built concurrently in V23) for expiring stale purchase intents, and `idx_sys_outbox_unpublished` for the outbox relay.

#### Schema on the SWE5001 environment

On the SWE5001 ECS environment, not on the SWE5006 `dev` host, one RDS instance holds one database per service. Each database applies the full Flyway history, and a callback (`afterMigrate__service_grants.sql` in `blocbuy-platform`) grants each service's runtime role only its own tables: identity owns `sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission` and the two `rel_` tables; notification owns `biz_notification` and `sys_consumed_message`; commerce owns the rest (backend ADR 0008). The ownership column of Appendix C's table list follows this split.
