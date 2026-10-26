CREATE TABLE platform_user_account (
    user_id VARCHAR(36) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    platform_role VARCHAR(32) NOT NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_user_account PRIMARY KEY (user_id),
    CONSTRAINT uk_platform_user_account_email UNIQUE (email),
    CONSTRAINT ck_platform_user_role CHECK (platform_role IN ('PLATFORM_ADMIN', 'CUSTOMER')),
    CONSTRAINT ck_platform_user_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE platform_refresh_session (
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    replaced_by_session_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_refresh_session PRIMARY KEY (session_id),
    CONSTRAINT uk_platform_refresh_session_hash UNIQUE (token_hash),
    CONSTRAINT fk_platform_refresh_session_user FOREIGN KEY (user_id)
        REFERENCES platform_user_account (user_id),
    INDEX ix_platform_refresh_session_user (user_id),
    INDEX ix_platform_refresh_session_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE platform_tenant_membership (
    membership_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    tenant_role VARCHAR(32) NOT NULL,
    membership_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_tenant_membership PRIMARY KEY (membership_id),
    CONSTRAINT uk_platform_tenant_membership_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_platform_tenant_membership_tenant FOREIGN KEY (tenant_id)
        REFERENCES platform_tenant_registry (tenant_id),
    CONSTRAINT fk_platform_tenant_membership_user FOREIGN KEY (user_id)
        REFERENCES platform_user_account (user_id),
    CONSTRAINT ck_platform_tenant_membership_role CHECK (tenant_role IN ('OWNER', 'MANAGER', 'STAFF', 'CUSTOMER')),
    CONSTRAINT ck_platform_tenant_membership_status CHECK (membership_status IN ('INVITED', 'ACTIVE', 'SUSPENDED')),
    INDEX ix_platform_tenant_membership_user (user_id)
) ENGINE=InnoDB;