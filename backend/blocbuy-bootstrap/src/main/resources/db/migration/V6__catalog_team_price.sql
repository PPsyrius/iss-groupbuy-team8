-- Catalog first-team pricing, PostgreSQL 18; append-only revisions and owner-scoped snapshots.
-- Application obligations and API semantics: backend/docs/design/team-price-quotes.md.
CREATE TABLE biz_team_price_policy (
    id          uuid          NOT NULL DEFAULT uuidv7(),
    product_id  uuid          NOT NULL,
    amount      numeric(18,2) NOT NULL,
    currency    varchar(4)    NOT NULL,
    valid_from  timestamptz   NOT NULL,
    valid_until timestamptz   NOT NULL,
    create_time timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   uuid          NOT NULL,
    update_time timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   uuid          NOT NULL,
    deleted     boolean       NOT NULL DEFAULT false,
    version     integer       NOT NULL,
    CONSTRAINT pk_biz_team_price_policy PRIMARY KEY (id)
);
COMMENT ON TABLE biz_team_price_policy IS 'Append-only catalog first-team price revisions; latest version is authoritative, even when unavailable.';
COMMENT ON COLUMN biz_team_price_policy.product_id IS 'Catalog product; product row lock serializes revision creation and quote issuance.';
COMMENT ON COLUMN biz_team_price_policy.amount IS 'Per-buyer major currency units; positive exact cents, less than product original price; application validated.';
COMMENT ON COLUMN biz_team_price_policy.valid_until IS 'Exclusive quotation deadline. Does not retroactively alter existing snapshots.';
COMMENT ON COLUMN biz_team_price_policy.version IS 'Monotonic product policy revision, starting at 1; expectedVersion precondition enforced under product lock.';
CREATE UNIQUE INDEX uq_biz_team_price_policy_product_version
    ON biz_team_price_policy (product_id, version);

CREATE TABLE biz_team_price_quote (
    id             uuid          NOT NULL DEFAULT uuidv7(),
    product_id     uuid          NOT NULL,
    owner_id       uuid          NOT NULL,
    policy_version integer       NOT NULL,
    amount         numeric(18,2) NOT NULL,
    currency       varchar(4)    NOT NULL,
    issued_at      timestamptz   NOT NULL,
    expires_at     timestamptz   NOT NULL,
    create_time    timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      uuid          NOT NULL,
    update_time    timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      uuid          NOT NULL,
    deleted        boolean       NOT NULL DEFAULT false,
    CONSTRAINT pk_biz_team_price_quote PRIMARY KEY (id)
);
COMMENT ON TABLE biz_team_price_quote IS 'Immutable quotation only; not payment, checkout, inventory reservation, or single-use consumption.';
COMMENT ON COLUMN biz_team_price_quote.owner_id IS 'Trusted authenticated identity; every read is scoped to this owner.';
COMMENT ON COLUMN biz_team_price_quote.policy_version IS 'Revision used at issuance; amount and currency are snapshots, never refreshed from policy.';
COMMENT ON COLUMN biz_team_price_quote.amount IS 'Per-buyer amount in major units; v1 quotation supports SGD only.';
COMMENT ON COLUMN biz_team_price_quote.expires_at IS 'Exclusive deadline, min(issued_at + 15 minutes, policy valid_until). Historical reads remain available.';
-- Owning use case introduces the permission; only the pre-existing admin role receives it.
INSERT INTO sys_permission (perm_code, perm_name, description)
VALUES ('catalog:product:write', 'Manage catalog product pricing', 'Configure and inspect first-team price policies');
INSERT INTO rel_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role CROSS JOIN sys_permission permission
WHERE role.role_code = 'admin' AND role.deleted = false
  AND permission.perm_code = 'catalog:product:write' AND permission.deleted = false;
