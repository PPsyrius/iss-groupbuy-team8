-- V20__product_team_terms_log.sql — audit trail for administrator writes of product team terms
-- (schema: domain, PostgreSQL 18). biz_product.team_target_size / team_lifetime_seconds (V14) are
-- overwritten in place by the admin command (US25, #221), so without this table the previous terms
-- and the actor behind a change are unrecoverable. One immutable row per successful write.
-- Conventions: no FK / no CHECK — reference validity is application-managed (pgsql-ddl
-- conventions); primary key native uuid with UUIDv7 via uuidv7() (PG18+); instants timestamptz.

CREATE TABLE log_product_team_terms (
    id                    uuid        NOT NULL DEFAULT uuidv7(),
    product_id            uuid        NOT NULL,
    product_version       integer     NOT NULL,
    old_target_size       integer,
    old_lifetime_seconds  integer,
    new_target_size       integer     NOT NULL,
    new_lifetime_seconds  integer     NOT NULL,
    team_price_version    integer,
    create_time           timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by             uuid        NOT NULL,
    CONSTRAINT pk_log_product_team_terms PRIMARY KEY (id)
);

COMMENT ON TABLE  log_product_team_terms IS 'Immutable audit of administrator writes of a product''s team terms; one row per successful write, inserted in the writer''s transaction.';
COMMENT ON COLUMN log_product_team_terms.product_id IS 'Product whose terms changed; validity enforced by the application.';
COMMENT ON COLUMN log_product_team_terms.product_version IS 'biz_product.version produced by this write; ties the row to the revision a client sees.';
COMMENT ON COLUMN log_product_team_terms.old_target_size IS 'team_target_size before the write; null for a product never configured.';
COMMENT ON COLUMN log_product_team_terms.old_lifetime_seconds IS 'team_lifetime_seconds before the write; null for a product never configured.';
COMMENT ON COLUMN log_product_team_terms.new_target_size IS 'team_target_size after the write.';
COMMENT ON COLUMN log_product_team_terms.new_lifetime_seconds IS 'team_lifetime_seconds after the write.';
COMMENT ON COLUMN log_product_team_terms.team_price_version IS 'biz_team_price_policy revision appended in the same command; null when the command left the price policy alone.';
COMMENT ON COLUMN log_product_team_terms.create_by IS 'Administrator who made the write, from the trusted session.';

-- History of one product, oldest first: the read an operator makes when asking who changed a bloc size.
CREATE INDEX idx_log_product_team_terms_product
    ON log_product_team_terms (product_id, create_time);

-- Application obligations:
-- * Rows are inserted only by ProductRepository.appendTeamTermsLog, in the same transaction as the
--   biz_product update and under the biz_product row lock; a rolled-back write leaves no row.
-- * Rows are never updated or deleted, so the table carries no update_*, deleted or version column.
-- * An application rollback leaves this table applied; it only ever gains rows.
