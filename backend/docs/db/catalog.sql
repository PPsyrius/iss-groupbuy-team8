-- catalog.sql — catalog domain DDL (schema: domain, PostgreSQL 18).
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); money numeric; instants timestamptz (UTC).

-- Sellable products eligible for group deals.
CREATE TABLE biz_product (
    id             uuid           NOT NULL DEFAULT uuidv7(),
    product_no     varchar(64)    NOT NULL,
    title          varchar(256)   NOT NULL,
    subtitle       varchar(256),
    description    text,
    cover_image    varchar(1024),
    images         jsonb,
    category       varchar(64),
    original_price numeric(18, 2) NOT NULL,
    currency       varchar(4)     NOT NULL,
    stock          integer        NOT NULL DEFAULT 0,
    sold_count     integer        NOT NULL DEFAULT 0,
    status         varchar(16)    NOT NULL,
    team_target_size      integer,
    team_lifetime_seconds integer,
    create_time    timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      uuid,
    update_time    timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      uuid,
    deleted        boolean        NOT NULL DEFAULT false,
    version        integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_product PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_product IS 'Sellable products; source rows for group deals and order snapshots.';
COMMENT ON COLUMN biz_product.product_no IS 'Business-facing product number; unique among active rows.';
COMMENT ON COLUMN biz_product.images IS 'Ordered list of image URLs beyond the cover; display data only, never filtered on.';
COMMENT ON COLUMN biz_product.category IS 'Classification label; allowed values enforced by the application/dictionary.';
COMMENT ON COLUMN biz_product.original_price IS 'List price in major units, e.g. 12.34; deal pricing lives on biz_group_deal.';
COMMENT ON COLUMN biz_product.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_product.stock IS 'Remaining sellable units; mutated only via application concurrency protocol.';
COMMENT ON COLUMN biz_product.sold_count IS 'Cumulative sold units; derived counter maintained by the application.';
COMMENT ON COLUMN biz_product.team_target_size IS 'Paid members a team started on this product needs to succeed; null means the product cannot start a team. Application requires >= 2. Added by V14__product_team_terms.sql.';
COMMENT ON COLUMN biz_product.team_lifetime_seconds IS 'Seconds a team started on this product stays open, from the START checkout. Null means the product cannot start a team. Application requires 2400..86400. Added by V14__product_team_terms.sql.';
COMMENT ON COLUMN biz_product.status IS 'Lifecycle status (e.g. draft, on_sale, off_sale); allowed values enforced by the application.';
COMMENT ON COLUMN biz_product.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

CREATE UNIQUE INDEX uq_biz_product_product_no
    ON biz_product (product_no)
    WHERE deleted = false;

CREATE INDEX idx_biz_product_category
    ON biz_product (category)
    WHERE deleted = false;

CREATE INDEX idx_biz_product_status
    ON biz_product (status)
    WHERE deleted = false;

-- Application obligations:
-- * stock mutation (reserve on order create, release on cancel/expire) uses the optimistic
--   version column or an atomic UPDATE ... SET stock = stock - n WHERE stock >= n; never
--   read-then-write without a guard. Funds/inventory safety outranks other rules.
-- * status value set and on/off-sale transitions validated in application.
-- * sold_count is a derived counter; scheduled reconciliation against biz_order can repair it.
-- * team_target_size / team_lifetime_seconds are read under the product row lock that issues a
--   START quote; their bounds live in ProductTeamTerms, not in a CHECK. Existing rows keep null,
--   so a product only becomes startable once an administrator configures both.
-- * Product deletion is soft only while no active deal/order references it; physical purge is
--   application-orchestrated after child retention checks.
