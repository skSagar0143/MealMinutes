package com.mealminutes.platform.tenant.service;
@FunctionalInterface
public interface TenantSchemaMigrator {
    int migrate(String databaseName);
}