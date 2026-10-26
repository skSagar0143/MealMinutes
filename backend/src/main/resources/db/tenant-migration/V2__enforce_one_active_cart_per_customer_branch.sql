ALTER TABLE customer_cart
    ADD COLUMN active_cart_key VARCHAR(73)
        GENERATED ALWAYS AS (
            CASE WHEN cart_status = 'ACTIVE'
                 THEN CONCAT(customer_user_id, ':', branch_id)
                 ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uk_customer_cart_active_key (active_cart_key);
