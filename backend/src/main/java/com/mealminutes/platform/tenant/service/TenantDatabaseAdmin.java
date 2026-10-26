package com.mealminutes.platform.tenant.service;
public interface TenantDatabaseAdmin {
    void ensureDatabase(String databaseName);
    void ensureUserAndGrant(String databaseName, String username, String password);
}