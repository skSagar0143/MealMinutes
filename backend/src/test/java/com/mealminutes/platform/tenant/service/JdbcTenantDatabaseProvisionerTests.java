package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JdbcTenantDatabaseProvisionerTests {
    @Test void provisionsDeterministicDatabaseAndAppliesMigration() {
        TenantDatabaseAdmin admin = mock(TenantDatabaseAdmin.class);
        TenantCredentialSecretStore secrets = mock(TenantCredentialSecretStore.class);
        TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
        AtomicReference<String> ref = new AtomicReference<>();
        when(secrets.createOrGet(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            ref.set(invocation.getArgument(0));
            return new TenantCredentialSecretStore.TenantCredentials(invocation.getArgument(1), invocation.getArgument(2));
        });
        when(migrator.migrate(anyString())).thenReturn(1);

        var result = new JdbcTenantDatabaseProvisioner(admin, secrets, migrator).provision("abc-food");

        assertThat(result.databaseName()).matches("tenant_abc_food_[0-9a-f]{8}");
        assertThat(result.databaseName()).hasSizeLessThanOrEqualTo(64);
        assertThat(result.secretReference()).isEqualTo("secret://mealminutes/tenants/abc-food/database");
        assertThat(result.schemaVersion()).isEqualTo(1);
        assertThat(ref.get()).isEqualTo(result.secretReference());
        verify(admin).ensureDatabase(result.databaseName());
        verify(admin).ensureUserAndGrant(eq(result.databaseName()), startsWith("mm_abc_food_"), matches("[A-Za-z0-9_-]{43}"));
        verify(migrator).migrate(result.databaseName());
    }

    @Test void rejectsInvalidSlugBeforeTouchingInfrastructure() {
        TenantDatabaseAdmin admin = mock(TenantDatabaseAdmin.class);
        TenantCredentialSecretStore secrets = mock(TenantCredentialSecretStore.class);
        TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
        assertThatThrownBy(() -> new JdbcTenantDatabaseProvisioner(admin, secrets, migrator).provision("bad slug"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(admin, secrets, migrator);
    }

    @Test void rejectsMismatchedStoredUsername() {
        TenantDatabaseAdmin admin = mock(TenantDatabaseAdmin.class);
        TenantCredentialSecretStore secrets = mock(TenantCredentialSecretStore.class);
        TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
        when(secrets.createOrGet(anyString(), anyString(), anyString()))
                .thenReturn(new TenantCredentialSecretStore.TenantCredentials("different_user", "A".repeat(43)));
        assertThatThrownBy(() -> new JdbcTenantDatabaseProvisioner(admin, secrets, migrator).provision("abc-food"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(admin, migrator);
    }
}