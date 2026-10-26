ALTER TABLE customer_address
    ADD COLUMN default_customer_user_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN is_default = TRUE THEN customer_user_id ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_customer_address_default_customer
    ON customer_address (default_customer_user_id);
