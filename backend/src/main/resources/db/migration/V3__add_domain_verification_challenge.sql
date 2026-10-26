ALTER TABLE platform_tenant_domain
    ADD COLUMN verification_challenge_hash VARCHAR(64) NULL,
    ADD COLUMN verification_expires_at TIMESTAMP(6) NULL;