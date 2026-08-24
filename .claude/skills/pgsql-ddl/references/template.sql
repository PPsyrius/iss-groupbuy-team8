-- PostgreSQL 18+ example. For earlier versions, generate UUIDv7 in the
-- application and remove DEFAULT uuidv7(). Adapt; do not copy unused columns.

CREATE TABLE biz_order (
    id             uuid NOT NULL DEFAULT uuidv7(),
    tenant_id      uuid NOT NULL,
    order_no       varchar(64) NOT NULL,
    customer_id    uuid NOT NULL,
    status         varchar(32) NOT NULL DEFAULT 'pending',
    total_amount   numeric(18, 2) NOT NULL,
    currency_code  varchar(8) NOT NULL,
    remark         varchar(1024),
    create_time    timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      uuid,
    update_time    timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      uuid,
    deleted        boolean NOT NULL DEFAULT false,
    delete_time    timestamptz,
    delete_by      uuid,
    version        integer NOT NULL DEFAULT 0,

    CONSTRAINT pk_biz_order PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_biz_order_tenant_order_no
    ON biz_order (tenant_id, order_no)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_tenant_customer_time
    ON biz_order (tenant_id, customer_id, create_time DESC)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_tenant_create_time
    ON biz_order (tenant_id, create_time DESC)
    WHERE deleted = false;

COMMENT ON TABLE biz_order IS 'Business order aggregate';
COMMENT ON COLUMN biz_order.id IS 'UUIDv7 primary key';
COMMENT ON COLUMN biz_order.order_no IS 'Tenant-scoped business order number';
COMMENT ON COLUMN biz_order.customer_id IS 'Customer identifier; validated by the owning application';
COMMENT ON COLUMN biz_order.total_amount IS 'Order total in currency_code units';
COMMENT ON COLUMN biz_order.update_time IS 'Last meaningful application update time';
COMMENT ON COLUMN biz_order.version IS 'Application-managed optimistic-lock version';

-- Application obligations:
-- 1. Validate that customer_id exists, is active, and belongs to tenant_id.
-- 2. Serialize customer deletion/state changes with order creation.
-- 3. Validate status, amount, currency, and deletion-state rules.
-- 4. Maintain update_time, actor fields, delete metadata, and version.
-- 5. Run periodic orphan and cross-tenant consistency audits.
