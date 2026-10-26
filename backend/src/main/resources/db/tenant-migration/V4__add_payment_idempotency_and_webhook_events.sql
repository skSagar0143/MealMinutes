ALTER TABLE payment_transaction
    ADD COLUMN idempotency_key VARCHAR(128) NULL,
    ADD COLUMN request_fingerprint CHAR(64) NULL,
    ADD COLUMN provider_order_id VARCHAR(160) NULL,
    ADD COLUMN provider_payment_id VARCHAR(160) NULL;

UPDATE payment_transaction
SET idempotency_key = CONCAT('legacy-', payment_id),
    request_fingerprint = SHA2(CONCAT(payment_id, ':', order_id), 256)
WHERE idempotency_key IS NULL OR request_fingerprint IS NULL;

ALTER TABLE payment_transaction
    MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL,
    MODIFY COLUMN request_fingerprint CHAR(64) NOT NULL,
    ADD CONSTRAINT uk_payment_order_idempotency UNIQUE (order_id, idempotency_key),
    ADD CONSTRAINT uk_payment_provider_order UNIQUE (provider_order_id),
    ADD CONSTRAINT uk_payment_provider_payment UNIQUE (provider_payment_id);

CREATE TABLE payment_webhook_event (
    webhook_event_id VARCHAR(160) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at TIMESTAMP(6) NULL,
    last_error VARCHAR(500) NULL,
    CONSTRAINT pk_payment_webhook_event PRIMARY KEY (webhook_event_id),
    CONSTRAINT ck_payment_webhook_status CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED')),
    INDEX ix_payment_webhook_status_received (processing_status, received_at)
) ENGINE=InnoDB;
