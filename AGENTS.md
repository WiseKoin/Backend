# WiseKoin Backend Contributor Guide

## Scope and stack

- This repository is the Spring Boot REST backend. The Android client lives in the separate `D:\AndroidStudioProjects\WiseKoin` repository; do not mix commits or paths between the two repositories.
- Use Java 21, Spring Boot 4, Maven Wrapper, PostgreSQL, Spring Data JPA, Spring Security, Jakarta Validation, and Flyway as already configured.
- Keep work inside the requested module. The main package is `com.wisekoin.backend`; feature code is grouped under `module/<feature>` with controllers, services, repositories, entities, DTOs, and mappers.

## Architecture and API contracts

- Controllers own HTTP concerns only: routes, request binding, validation, authentication principals, and response status. Put business rules and ownership checks in services.
- Never accept a user ID from the client for owned resources. Resolve the authenticated user with `CustomUserDetails` or `Principal`, and verify ownership for every referenced wallet, category, goal, debt, recurring transaction, and transaction.
- Use request/response DTOs at the API boundary. Do not expose JPA entities or silently change JSON field names, enum values, methods, status codes, or endpoint paths; the Android client consumes these contracts.
- Apply `@Valid` and precise Jakarta constraints to full-update/create requests. For PATCH DTOs, preserve the distinction between an omitted field and an explicitly supplied `null` where clearing is allowed.
- Financial amounts use `BigDecimal`, never `double` or `float`. Keep balance-changing operations transactional and publish the existing transaction events so wallet, goal, and debt state remains consistent.
- Prefer the existing exception types and `GlobalExceptionHandler` response shape. Do not leak stack traces, SQL details, credentials, provider payloads, or sensitive account data.
- Authentication is stateless JWT. Keep access-token and rotating refresh-token behavior compatible with `/api/auth/login`, `/register`, `/refresh`, and `/logout`. Any security control must be enforced here even if the Android UI also validates it.
- Schema changes require a new append-only migration in `src/main/resources/db/migration`. Do not edit a migration that may already have run. Avoid relying on `ddl-auto=update` for production schema evolution.

## Configuration and secrets

- Read deployment values from environment-backed properties. Never commit `.env` files, database credentials, JWT secrets, API keys, production URLs, or generated certificates.
- Update `.env.development.example`, `.env.production.example`, Docker files, and `README.md` when a required environment variable or operational step changes.
- Keep external AI calls behind the receipt/voice services, with bounded uploads, timeouts, and user-safe failures. Avoid logging uploaded media or provider secrets.

## Code style and tests

- Follow the existing AOSP Google Java Format enforced by Spotless. Avoid wildcard imports and keep field annotations on separate lines.
- Add or update tests for changed behavior. Service tests should cover happy paths, validation, ownership boundaries, insufficient-balance/compensation behavior, and transaction rollback. Security or HTTP contract changes need focused security/controller coverage.
- Prefer deterministic tests: inject/use the application `Clock`, avoid wall-clock sleeps, and mock remote AI calls. Keep tests independent of developer-specific databases and files.
- Use the Maven Wrapper from the repository root. A Java 21 JDK is required.

## Verification

Run the narrowest relevant test while iterating, then the full checks before handoff:

```powershell
.\mvnw.cmd -Dtest=ClassName test
.\mvnw.cmd test
.\mvnw.cmd spotless:check
```

Use `.\mvnw.cmd spotless:apply` only to format intended Java changes, then review the diff. For container changes, also run the relevant development or production Compose config validation without committing environment files.

## Change discipline

- Preserve unrelated working-tree changes. Keep migrations, API changes, tests, and documentation in the same logical change.
- If an API contract changes, inspect the Android service and DTO with the same feature name and explicitly call out the required client change.
- Do not claim an integration is verified solely because code compiles; test the service behavior and, where practical, the HTTP contract.
