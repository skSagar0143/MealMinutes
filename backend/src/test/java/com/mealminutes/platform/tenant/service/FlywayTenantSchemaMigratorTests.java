package com.mealminutes.platform.tenant.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FlywayTenantSchemaMigratorTests {
    @Test void migratesOnlyValidatedTenantDatabaseAndReturnsVersion() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo info = mock(MigrationInfo.class);
        MigrationVersion version = mock(MigrationVersion.class);
        when(flyway.info()).thenReturn(infoService);
        when(infoService.current()).thenReturn(info);
        when(info.getVersion()).thenReturn(version);
        when(version.getVersion()).thenReturn("1");
        AtomicReference<String> jdbcUrl = new AtomicReference<>();
        var migrator = new FlywayTenantSchemaMigrator("jdbc:mysql://localhost:3306/{database}",
                "root", "admin-password", "classpath:db/tenant-migration",
                (url, user, password, location) -> { jdbcUrl.set(url); return flyway; });

        assertThat(migrator.migrate("tenant_abc_food")).isEqualTo(1);
        assertThat(jdbcUrl.get()).isEqualTo("jdbc:mysql://localhost:3306/tenant_abc_food");
        verify(flyway).migrate();
    }

    @Test void rejectsUnsafeDatabaseNameAndMissingVersion() {
        var migrator = new FlywayTenantSchemaMigrator("jdbc:mysql://localhost:3306/{database}",
                "root", "admin-password", "classpath:db/tenant-migration",
                (url, user, password, location) -> mock(Flyway.class));
        assertThatThrownBy(() -> migrator.migrate("bad;drop")).isInstanceOf(IllegalArgumentException.class);

        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(null);
        var noVersion = new FlywayTenantSchemaMigrator("jdbc:mysql://localhost:3306/{database}",
                "root", "admin-password", "classpath:db/tenant-migration", (url, user, password, location) -> flyway);
        assertThatThrownBy(() -> noVersion.migrate("tenant_ok")).isInstanceOf(IllegalStateException.class);
    }
}