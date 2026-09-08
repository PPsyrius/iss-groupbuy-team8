-- V5__groupbuy.sql — groupbuy domain tables (schema: domain, PostgreSQL 18).
-- Derived from backend/docs/db/groupbuy.sql (source of truth). Conventions: no FK / no CHECK —
-- reference validity, allowed status values, and concurrency rules are application-managed
-- (pgsql-ddl conventions); primary keys are native uuid with UUIDv7 via uuidv7() (PG18+);
-- money numeric; instants timestamptz (UTC).

-- One group deal campaign per product per time window.
CREATE TABLE biz_group_deal (
    id              uuid           NOT NULL DEFAULT uuidv7(),
    deal_no         varchar(64)    NOT NULL,
    product_id      uuid           NOT NULL,
    leader_user_id  uuid           NOT NULL,
    deal_price      numeric(18, 2) NOT NULL,
    currency        varchar(4)     NOT NULL,
    target_size     integer        NOT NULL,
    current_size    integer        NOT NULL DEFAULT 0,
    status          varchar(16)    NOT NULL,
    start_time      timestamptz    NOT NULL,
    expire_time     timestamptz    NOT NULL,
    success_time    timestamptz,
    create_time     timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       uuid,
    update_time     timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       uuid,
    deleted         boolean        NOT NULL DEFAULT false,
    version         integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_group_deal PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_group_deal IS 'Group deal campaigns; PostgreSQL is the source of truth for deal progress, never Redis.';
COMMENT ON COLUMN biz_group_deal.deal_no IS 'Business-facing deal number; unique among active rows.';
COMMENT ON COLUMN biz_group_deal.product_id IS 'Product under deal; validity enforced by the application.';
COMMENT ON COLUMN biz_group_deal.leader_user_id IS 'User who started the deal; first member of the group.';
COMMENT ON COLUMN biz_group_deal.deal_price IS 'Per-unit group price in major units, e.g. 12.34.';
COMMENT ON COLUMN biz_group_deal.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_group_deal.target_size IS 'Paid members required for the deal to succeed.';
COMMENT ON COLUMN biz_group_deal.current_size IS 'Current paid member count; mutated only via application concurrency protocol.';
COMMENT ON COLUMN biz_group_deal.status IS 'Lifecycle status (e.g. open, succeeded, failed, expired, canceled); allowed values enforced by the application.';
COMMENT ON COLUMN biz_group_deal.expire_time IS 'Deal closes at this instant if the target is unmet; UTC.';
COMMENT ON COLUMN biz_group_deal.success_time IS 'Instant the target size was reached; UTC.';
COMMENT ON COLUMN biz_group_deal.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

CREATE UNIQUE INDEX uq_biz_group_deal_deal_no
    ON biz_group_deal (deal_no)
    WHERE deleted = false;

CREATE INDEX idx_biz_group_deal_product_id
    ON biz_group_deal (product_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_group_deal_leader_user_id
    ON biz_group_deal (leader_user_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_group_deal_status_expire
    ON biz_group_deal (status, expire_time)
    WHERE deleted = false;

-- One row per user joining one deal; carries participation state beyond a pure relation.
CREATE TABLE biz_group_member (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    deal_id     uuid        NOT NULL,
    order_id    uuid,
    user_id     uuid        NOT NULL,
    member_role varchar(16) NOT NULL,
    status      varchar(16) NOT NULL,
    join_time   timestamptz NOT NULL,
    create_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   uuid,
    update_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   uuid,
    deleted     boolean     NOT NULL DEFAULT false,
    CONSTRAINT pk_biz_group_member PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_group_member IS 'Deal participation records; one active row per user per deal.';
COMMENT ON COLUMN biz_group_member.deal_id IS 'Owning deal; validity enforced by the application.';
COMMENT ON COLUMN biz_group_member.order_id IS 'Backing order for this participation; null until the order exists.';
COMMENT ON COLUMN biz_group_member.user_id IS 'Participating user; validity enforced by the application.';
COMMENT ON COLUMN biz_group_member.member_role IS 'Participation role (leader, member); allowed values enforced by the application.';
COMMENT ON COLUMN biz_group_member.status IS 'Participation status (e.g. joined, paid, refunded, left); allowed values enforced by the application.';
COMMENT ON COLUMN biz_group_member.join_time IS 'Instant the user joined; UTC.';

CREATE UNIQUE INDEX uq_biz_group_member_deal_user
    ON biz_group_member (deal_id, user_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_group_member_order_id
    ON biz_group_member (order_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_group_member_user_id
    ON biz_group_member (user_id)
    WHERE deleted = false;

-- Application obligations:
-- * Join path: lock the deal row (SELECT ... FOR UPDATE) in the same transaction, verify
--   status = open, expire_time not passed, current_size < target_size, then insert member and
--   bump current_size. The partial unique index uq_biz_group_member_deal_user is the backstop
--   against duplicate joins; map violation to 409.
-- * current_size counts paid members only; payment success and refund transitions adjust it
--   under the same deal-row lock. Deal success/failure transitions are idempotent.
-- * Expiry handled by a scheduled idempotent job reading idx_biz_group_deal_status_expire;
--   database state is the backstop (no MQ in the stack).
-- * Deal/member writes verify the product and user exist and are active before insert.
-- * Physical purge order: biz_group_member before biz_group_deal; orphan audit on deal_id,
--   order_id, user_id references.
