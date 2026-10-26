ALTER TABLE customer_order
    ADD COLUMN fulfillment_type VARCHAR(16) NOT NULL DEFAULT 'PICKUP' AFTER order_status,
    ADD COLUMN request_fingerprint CHAR(64) NOT NULL DEFAULT '' AFTER idempotency_key,
    ADD CONSTRAINT ck_customer_order_fulfillment CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY'));
