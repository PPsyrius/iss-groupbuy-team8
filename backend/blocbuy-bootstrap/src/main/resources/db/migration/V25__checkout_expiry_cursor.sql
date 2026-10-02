-- Persist the explicit Checkout expiry hook's position across command invocations.
-- This operational cursor is not payment state; the payment and review rows remain authoritative.
CREATE TABLE sys_checkout_expiry_cursor (
    id               uuid        NOT NULL DEFAULT uuidv7(),
    job_name         varchar(64) NOT NULL,
    last_create_time timestamptz,
    last_payment_id  uuid,
    create_time      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        uuid,
    update_time      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        uuid,
    deleted          boolean     NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_checkout_expiry_cursor PRIMARY KEY (id),
    CONSTRAINT uq_sys_checkout_expiry_cursor_job_name UNIQUE (job_name)
);

COMMENT ON TABLE sys_checkout_expiry_cursor IS 'Durable keyset position for the bounded operator-driven Checkout expiry sweep.';
COMMENT ON COLUMN sys_checkout_expiry_cursor.last_create_time IS 'Creation instant of the last reserved payment; null before the first page or after an empty sweep.';
COMMENT ON COLUMN sys_checkout_expiry_cursor.last_payment_id IS 'Payment ID that breaks creation-time ties; null together with last_create_time.';

INSERT INTO sys_checkout_expiry_cursor (job_name) VALUES ('checkout.expiry');
