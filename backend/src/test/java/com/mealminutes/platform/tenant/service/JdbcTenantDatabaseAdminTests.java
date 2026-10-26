package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JdbcTenantDatabaseAdminTests {
    @Test void executesValidatedDatabaseAndLeastPrivilegeUserStatements() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        JdbcTenantDatabaseAdmin.AdminConnectionProvider provider = () -> connection;
        var admin = new JdbcTenantDatabaseAdmin("jdbc:mysql://localhost:3306/mysql", "root", "admin-secret", provider);

        admin.ensureDatabase("tenant_abc_food");
        admin.ensureUserAndGrant("tenant_abc_food", "mm_abc_food_123456", "A".repeat(43));

        verify(statement).execute("CREATE DATABASE IF NOT EXISTS tenant_abc_food CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        verify(statement).execute("CREATE USER IF NOT EXISTS 'mm_abc_food_123456'@'%' IDENTIFIED BY '" + "A".repeat(43) + "'");
        verify(statement).execute("ALTER USER 'mm_abc_food_123456'@'%' IDENTIFIED BY '" + "A".repeat(43) + "'");
        verify(statement).execute("GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_abc_food.* TO 'mm_abc_food_123456'@'%'");
    }

    @Test void rejectsUnsafeIdentifiersAndPasswordsBeforeOpeningConnections() {
        JdbcTenantDatabaseAdmin.AdminConnectionProvider provider = mock(JdbcTenantDatabaseAdmin.AdminConnectionProvider.class);
        var admin = new JdbcTenantDatabaseAdmin("jdbc:mysql://localhost:3306/mysql", "root", "admin-secret", provider);
        assertThatThrownBy(() -> admin.ensureDatabase("tenant;drop")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.ensureUserAndGrant("tenant_ok", "bad-user", "A".repeat(43)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.ensureUserAndGrant("tenant_ok", "valid_user", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }
}