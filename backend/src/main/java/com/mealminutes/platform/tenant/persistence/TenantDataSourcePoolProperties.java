package com.mealminutes.platform.tenant.persistence;

/**
 * Bounded pool configuration. The JDBC template is trusted deployment
 * configuration; tenant requests may provide only a validated database name.
 */
public class TenantDataSourcePoolProperties {
    private String jdbcUrlTemplate =
            "jdbc:mysql://localhost:3306/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    private int maxPoolCount = 20;
    private int maxPoolSizePerTenant = 5;
    private int minIdlePerTenant = 0;
    private long connectionTimeoutMs = 3000;
    private long idleTimeoutMs = 60000;

    public void validate() {
        if (jdbcUrlTemplate == null || !jdbcUrlTemplate.startsWith("jdbc:mysql://")
                || occurrences(jdbcUrlTemplate, "{database}") != 1) {
            throw new IllegalArgumentException("JDBC URL must be trusted MySQL configuration with one {database} placeholder");
        }
        if (maxPoolCount < 1 || maxPoolSizePerTenant < 1
                || minIdlePerTenant < 0 || minIdlePerTenant > maxPoolSizePerTenant) {
            throw new IllegalArgumentException("Invalid tenant datasource pool limits");
        }
        if (connectionTimeoutMs < 250 || connectionTimeoutMs > 120000) {
            throw new IllegalArgumentException("Connection timeout must be between 250 and 120000 ms");
        }
        if (idleTimeoutMs < 10000 || idleTimeoutMs > 3600000) {
            throw new IllegalArgumentException("Idle timeout must be between 10000 and 3600000 ms");
        }
    }

    public String jdbcUrlFor(String databaseName) {
        if (databaseName == null || !databaseName.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid tenant database name");
        }
        validate();
        return jdbcUrlTemplate.replace("{database}", databaseName);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    public String getJdbcUrlTemplate() { return jdbcUrlTemplate; }
    public void setJdbcUrlTemplate(String jdbcUrlTemplate) { this.jdbcUrlTemplate = jdbcUrlTemplate; }
    public int getMaxPoolCount() { return maxPoolCount; }
    public void setMaxPoolCount(int maxPoolCount) { this.maxPoolCount = maxPoolCount; }
    public int getMaxPoolSizePerTenant() { return maxPoolSizePerTenant; }
    public void setMaxPoolSizePerTenant(int maxPoolSizePerTenant) { this.maxPoolSizePerTenant = maxPoolSizePerTenant; }
    public int getMinIdlePerTenant() { return minIdlePerTenant; }
    public void setMinIdlePerTenant(int minIdlePerTenant) { this.minIdlePerTenant = minIdlePerTenant; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }
}