CREATE TABLE customer_order_status_history (
    history_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    note VARCHAR(500) NULL,
    correlation_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_order_status_history PRIMARY KEY (history_id),
    CONSTRAINT fk_customer_order_status_history_order FOREIGN KEY (order_id)
        REFERENCES customer_order (order_id) ON DELETE CASCADE,
    INDEX ix_customer_order_status_history_order (order_id, created_at),
    INDEX ix_customer_order_status_history_actor (actor_user_id, created_at)
) ENGINE=InnoDB;
