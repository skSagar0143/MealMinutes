CREATE TABLE restaurant_profile (
    restaurant_id VARCHAR(36) NOT NULL,
    restaurant_name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NULL,
    logo_url VARCHAR(2048) NULL,
    support_email VARCHAR(254) NULL,
    support_phone VARCHAR(32) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_restaurant_profile PRIMARY KEY (restaurant_id),
    CONSTRAINT ck_restaurant_profile_currency CHECK (currency REGEXP '^[A-Z]{3}$')
) ENGINE=InnoDB;

CREATE TABLE restaurant_branch (
    branch_id VARCHAR(36) NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120) NULL,
    postal_code VARCHAR(20) NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'IN',
    phone VARCHAR(32) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    pickup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_restaurant_branch PRIMARY KEY (branch_id),
    CONSTRAINT uk_restaurant_branch_slug UNIQUE (slug),
    CONSTRAINT ck_restaurant_branch_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_restaurant_branch_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    INDEX ix_restaurant_branch_city_active (city, is_active)
) ENGINE=InnoDB;

CREATE TABLE menu_category (
    category_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_menu_category PRIMARY KEY (category_id),
    CONSTRAINT uk_menu_category_slug UNIQUE (slug),
    INDEX ix_menu_category_active_sort (is_active, sort_order)
) ENGINE=InnoDB;

CREATE TABLE menu_item (
    item_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NULL,
    price DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    image_url VARCHAR(2048) NULL,
    vegetarian BOOLEAN NOT NULL DEFAULT FALSE,
    vegan BOOLEAN NOT NULL DEFAULT FALSE,
    allergens VARCHAR(1000) NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_menu_item PRIMARY KEY (item_id),
    CONSTRAINT uk_menu_item_slug UNIQUE (slug),
    CONSTRAINT fk_menu_item_category FOREIGN KEY (category_id) REFERENCES menu_category (category_id),
    CONSTRAINT ck_menu_item_price CHECK (price >= 0),
    CONSTRAINT ck_menu_item_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    INDEX ix_menu_item_category_available (category_id, is_available, display_order)
) ENGINE=InnoDB;

CREATE TABLE menu_item_modifier_group (
    modifier_group_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    required_selection BOOLEAN NOT NULL DEFAULT FALSE,
    min_selections INT NOT NULL DEFAULT 0,
    max_selections INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_menu_item_modifier_group PRIMARY KEY (modifier_group_id),
    CONSTRAINT ck_modifier_group_selection CHECK (min_selections >= 0 AND max_selections >= min_selections)
) ENGINE=InnoDB;

CREATE TABLE menu_item_modifier (
    modifier_id VARCHAR(36) NOT NULL,
    modifier_group_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price_delta DECIMAL(12,2) NOT NULL DEFAULT 0,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_menu_item_modifier PRIMARY KEY (modifier_id),
    CONSTRAINT fk_menu_item_modifier_group FOREIGN KEY (modifier_group_id) REFERENCES menu_item_modifier_group (modifier_group_id),
    CONSTRAINT ck_menu_item_modifier_price CHECK (price_delta >= 0),
    INDEX ix_menu_item_modifier_group (modifier_group_id, is_available)
) ENGINE=InnoDB;

CREATE TABLE customer_address (
    address_id VARCHAR(36) NOT NULL,
    customer_user_id VARCHAR(36) NOT NULL,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    recipient_phone VARCHAR(32) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120) NULL,
    postal_code VARCHAR(20) NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'IN',
    delivery_instructions VARCHAR(500) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_address PRIMARY KEY (address_id),
    INDEX ix_customer_address_customer (customer_user_id, is_default)
) ENGINE=InnoDB;

CREATE TABLE customer_cart (
    cart_id VARCHAR(36) NOT NULL,
    customer_user_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    cart_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_cart PRIMARY KEY (cart_id),
    CONSTRAINT fk_customer_cart_branch FOREIGN KEY (branch_id) REFERENCES restaurant_branch (branch_id),
    CONSTRAINT ck_customer_cart_status CHECK (cart_status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED')),
    INDEX ix_customer_cart_customer_status (customer_user_id, cart_status)
) ENGINE=InnoDB;

CREATE TABLE customer_cart_item (
    cart_item_id VARCHAR(36) NOT NULL,
    cart_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    item_notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_cart_item PRIMARY KEY (cart_item_id),
    CONSTRAINT fk_customer_cart_item_cart FOREIGN KEY (cart_id) REFERENCES customer_cart (cart_id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_cart_item_item FOREIGN KEY (item_id) REFERENCES menu_item (item_id),
    CONSTRAINT uk_customer_cart_item UNIQUE (cart_id, item_id),
    CONSTRAINT ck_customer_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_customer_cart_item_price CHECK (unit_price_snapshot >= 0)
) ENGINE=InnoDB;

CREATE TABLE customer_order (
    order_id VARCHAR(36) NOT NULL,
    order_number VARCHAR(32) NOT NULL,
    customer_user_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    subtotal DECIMAL(12,2) NOT NULL,
    tax_total DECIMAL(12,2) NOT NULL DEFAULT 0,
    delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_total DECIMAL(12,2) NOT NULL DEFAULT 0,
    grand_total DECIMAL(12,2) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    delivery_address_snapshot JSON NULL,
    customer_note VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_order PRIMARY KEY (order_id),
    CONSTRAINT uk_customer_order_number UNIQUE (order_number),
    CONSTRAINT uk_customer_order_idempotency UNIQUE (customer_user_id, idempotency_key),
    CONSTRAINT fk_customer_order_branch FOREIGN KEY (branch_id) REFERENCES restaurant_branch (branch_id),
    CONSTRAINT ck_customer_order_totals CHECK (subtotal >= 0 AND tax_total >= 0 AND delivery_fee >= 0 AND discount_total >= 0 AND grand_total >= 0),
    CONSTRAINT ck_customer_order_status CHECK (order_status IN ('CREATED', 'PAYMENT_PENDING', 'CONFIRMED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED')),
    INDEX ix_customer_order_customer_created (customer_user_id, created_at),
    INDEX ix_customer_order_branch_status (branch_id, order_status, created_at)
) ENGINE=InnoDB;

CREATE TABLE customer_order_item (
    order_item_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NULL,
    name_snapshot VARCHAR(160) NOT NULL,
    description_snapshot VARCHAR(1000) NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    customization_snapshot JSON NULL,
    CONSTRAINT pk_customer_order_item PRIMARY KEY (order_item_id),
    CONSTRAINT fk_customer_order_item_order FOREIGN KEY (order_id) REFERENCES customer_order (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_order_item_menu FOREIGN KEY (item_id) REFERENCES menu_item (item_id) ON DELETE SET NULL,
    CONSTRAINT ck_customer_order_item_values CHECK (unit_price_snapshot >= 0 AND quantity BETWEEN 1 AND 99 AND line_total >= 0),
    INDEX ix_customer_order_item_order (order_id)
) ENGINE=InnoDB;

CREATE TABLE payment_transaction (
    payment_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(160) NULL,
    webhook_event_id VARCHAR(160) NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_transaction PRIMARY KEY (payment_id),
    CONSTRAINT fk_payment_transaction_order FOREIGN KEY (order_id) REFERENCES customer_order (order_id),
    CONSTRAINT uk_payment_provider_reference UNIQUE (provider_reference),
    CONSTRAINT uk_payment_webhook_event UNIQUE (webhook_event_id),
    CONSTRAINT ck_payment_transaction_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_transaction_status CHECK (payment_status IN ('PENDING', 'AUTHORIZED', 'SUCCEEDED', 'FAILED', 'REFUND_PENDING', 'REFUNDED')),
    INDEX ix_payment_transaction_order (order_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE outbox_event (
    event_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    schema_version INT NOT NULL,
    correlation_id VARCHAR(64) NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (event_id),
    CONSTRAINT ck_outbox_event_schema_version CHECK (schema_version >= 1),
    INDEX ix_outbox_event_pending (published_at, created_at)
) ENGINE=InnoDB;

CREATE TABLE processed_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB;

CREATE TABLE audit_event (
    audit_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(64) NULL,
    correlation_id VARCHAR(64) NULL,
    details JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_event PRIMARY KEY (audit_id),
    INDEX ix_audit_event_resource (resource_type, resource_id, created_at),
    INDEX ix_audit_event_actor (actor_user_id, created_at)
) ENGINE=InnoDB;