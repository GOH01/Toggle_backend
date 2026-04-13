# Backend AGENT Guide

## Module Context

This app owns authentication, authorization, store lookup, favorites, owner application workflows, admin review workflows, public institution lookup, and persistence.

Primary packages:

- `controller` for HTTP entry points
- `dto` for request/response contracts
- `entity` for JPA models and enums
- `repository` for persistence access
- `service` for business rules and integrations
- `global` for config, security, exceptions, and shared response handling

## Tech Stack & Constraints

- Use `Java 21` and `Spring Boot 3.3`.
- Persistence uses `Spring Data JPA` with MySQL for app runtime and H2 in test/runtime-only support.
- Security uses stateless JWT authentication.
- Default app response pattern appears to use `ApiResponse` and `ErrorResponse`; keep new endpoints consistent.
- `spring.jpa.open-in-view` is disabled, so resolve lazy access inside service-layer transaction boundaries.

## Implementation Patterns

- Keep controllers thin: validate input, delegate to services, return DTOs.
- Put business logic, permission-sensitive decisions, and cross-repository orchestration in services.
- Use DTO classes for external contracts. Do not return entities directly from controllers.
- Keep repository methods focused on query intent; complex business branching does not belong in repositories.
- Add new enums/value objects in `entity` when the domain needs explicit states instead of free-form strings.
- Place framework-wide concerns under `global`, and security-specific concerns under `global/security`.

## Testing Strategy

- Primary commands:
  - `cd apps/backend && ./gradlew test`
  - `cd apps/backend && ./gradlew build`
- Add or update tests when changing:
  - auth or JWT behavior
  - role-based access rules
  - service-level business logic
  - repository query semantics
- Prefer focused service or MVC/security tests over only boot smoke coverage.

## Local Golden Rules

### Do

- Enforce role checks in both endpoint security configuration and service intent where needed.
- Validate request DTOs with Bean Validation rather than ad hoc controller checks.
- Keep entity relationships and cascade behavior explicit.
- Use configuration properties classes for new external integrations or app settings.

### Don't

- Do not put password handling, JWT parsing, or authorization shortcuts outside the security/global flow.
- Do not call external services directly from controllers.
- Do not add new committed secrets to `application.yml` or tests.
- Do not rely on implicit lazy loading from controller serialization.

## Context Map

- **[Security-specific changes](./src/main/java/com/toggle/global/security/AGENTS.md)** — filter chain, JWT provider, user principal, auth entry/denied behavior.
