-- Normalize any legacy duplicate defaults before adding the uniqueness guarantee.
UPDATE customer_address AS address_row
JOIN (
    SELECT customer_user_id, MIN(address_id) AS keep_address_id
    FROM customer_address
    WHERE is_default = TRUE
    GROUP BY customer_user_id
) AS defaults_per_customer
    ON defaults_per_customer.customer_user_id = address_row.customer_user_id
SET address_row.is_default = FALSE
WHERE address_row.is_default = TRUE
  AND address_row.address_id <> defaults_per_customer.keep_address_id;

ALTER TABLE customer_address
    ADD COLUMN default_customer_user_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN is_default = TRUE THEN customer_user_id ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_customer_address_default_customer
    ON customer_address (default_customer_user_id);
