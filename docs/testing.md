# Testing and coverage

## Current baseline

The backend contains a Spring Boot context smoke test, a tenant-registry persistence integration test, and JaCoCo Maven plugin configuration. The integration test requires MySQL and Flyway; no test execution result is claimed by this document.

## Commands

From the repository root in PowerShell:

    mvn -f backend/pom.xml test
    mvn -f backend/pom.xml verify

JaCoCo HTML report: backend/target/site/jacoco/index.html. XML report: backend/target/site/jacoco/jacoco.xml.

## Required test layers

- Fast unit tests for domain rules and state transitions.
- MVC/API tests for validation, error contracts and authorization.
- Integration tests for MySQL/Flyway, Redis and Kafka using Testcontainers.
- Architecture tests to enforce module boundaries.
- Tenant-isolation and security regression tests.
- End-to-end checkout, payment replay, inventory concurrency, retry/DLT and recovery scenarios.

Coverage thresholds should be introduced after baseline metrics and critical-module expectations are established; exclusions must not hide business logic. Coverage percentage is not a substitute for meaningful assertions or security review.
