CREATE TABLE platform_tenant_registry (
    tenant_id VARCHAR(36) NOT NULL,
    slug VARCHAR(63) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    provisioning_status VARCHAR(24) NOT NULL,
    tenant_database_name VARCHAR(64) NULL,
    database_secret_reference VARCHAR(255) NULL,
    schema_version INT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_tenant_registry PRIMARY KEY (tenant_id),
    CONSTRAINT uk_platform_tenant_registry_slug UNIQUE (slug),
    CONSTRAINT ck_platform_tenant_slug_format CHECK (slug REGEXP '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
) ENGINE=InnoDB;

CREATE TABLE platform_tenant_domain (
    domain_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    hostname VARCHAR(253) NOT NULL,
    verification_status VARCHAR(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_tenant_domain PRIMARY KEY (domain_id),
    CONSTRAINT uk_platform_tenant_domain_hostname UNIQUE (hostname),
    CONSTRAINT fk_platform_tenant_domain_tenant FOREIGN KEY (tenant_id)
        REFERENCES platform_tenant_registry (tenant_id),
    CONSTRAINT ck_platform_tenant_domain_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REVOKED')),
    INDEX ix_platform_tenant_domain_tenant (tenant_id),
    INDEX ix_platform_tenant_domain_lookup (hostname, verification_status)
) ENGINE=InnoDB;
