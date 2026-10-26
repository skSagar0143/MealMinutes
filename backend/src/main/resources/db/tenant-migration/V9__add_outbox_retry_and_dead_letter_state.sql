ALTER TABLE outbox_event
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN dead_lettered_at TIMESTAMP(6) NULL;

CREATE INDEX ix_outbox_event_dispatch
    ON outbox_event (published_at, dead_lettered_at, next_attempt_at, created_at);
