package com.mealminutes.platform.tenant.service;

@FunctionalInterface
public interface DomainOwnershipVerifier {
    boolean hasTxtRecord(String recordName, String expectedValue);
}