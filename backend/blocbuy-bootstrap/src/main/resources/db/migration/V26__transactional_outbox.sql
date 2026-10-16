-- Cross-service integration facts are committed atomically with commerce state.
CREATE TABLE sys_outbox (
    id              uuid         NOT NULL DEFAULT uuidv7(),
    event_type      varchar(64)  NOT NULL,
    event_version   integer      NOT NULL,
    aggregate_id    uuid         NOT NULL,
    payload         jsonb        NOT NULL,
    trace_id        varchar(64)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    timestamptz,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_at       timestamptz,
    CONSTRAINT pk_sys_outbox PRIMARY KEY (id)
);

COMMENT ON TABLE sys_outbox IS 'Transactional integration-event queue; eventId is the UUIDv7 row id.';
COMMENT ON COLUMN sys_outbox.payload IS 'Versioned contract payload only; never credentials, email or payment-sensitive data.';

CREATE INDEX idx_sys_outbox_unpublished
    ON sys_outbox (next_attempt_at, created_at, id)
    WHERE published_at IS NULL AND failed_at IS NULL;

CREATE INDEX idx_sys_outbox_published_retention
    ON sys_outbox (published_at, id)
    WHERE published_at IS NOT NULL;
