# Development setup (Windows, STS, Java 21)

## Prerequisites

- JDK 21 and Maven 3.6.3+ (use the Maven Wrapper once it is added).
- Spring Tool Suite (STS) with Maven integration.
- Git and Docker Desktop.
- Node.js LTS for the React frontend (frontend is introduced in a later phase).

Verify Java and Maven in PowerShell:

    java -version
    mvn -version
    git --version
    docker --version
    docker compose version

The Java output should identify version 21. Maven must run on the same JDK.

## Backend

Open the repository in STS, then import backend/pom.xml as an Existing Maven Project. From the repository root:

    mvn -f backend/pom.xml test
    mvn -f backend/pom.xml verify
    mvn -f backend/pom.xml spring-boot:run

When running, check http://localhost:8080/actuator/health. The health response should report UP if the application starts successfully.

JaCoCo HTML report path after verify: backend/target/site/jacoco/index.html.

## Configuration and secrets

Configuration is environment-variable based. Do not commit .env, passwords, access tokens, private keys or production configuration. .env.example is a documentation template, not a secrets store.

## Status caveat

These are intended commands, not claims that the build has already run in a local Java 21 environment. Record actual test/build evidence in the implementation tracker.
