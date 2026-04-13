# Backend Security AGENT Guide

## Module Context

This package owns authentication and authorization plumbing for the backend:

- JWT creation and validation
- request authentication filter
- `CustomUserDetailsService` and principal loading
- security filter chain and role gating
- unauthorized/forbidden response handling

## Tech Stack & Constraints

- Security is stateless. Preserve `SessionCreationPolicy.STATELESS` unless the whole auth model changes.
- JWT behavior is configured through `JwtProperties` and related config under `global/config`.
- Role enforcement currently distinguishes admin routes with Spring Security path rules. Keep route protection and token claims aligned.
- Error behavior must remain API-friendly and JSON-based through the custom entry point and access denied handler.

## Implementation Patterns

- Put path-level authorization in `SecurityConfig`.
- Put token parsing/signing rules in `JwtTokenProvider`.
- Put request authentication extraction in `JwtAuthenticationFilter`.
- Keep principal loading in `CustomUserDetailsService` and `CustomUserPrincipal`.
- When adding public endpoints, update allowlists deliberately and verify they do not widen adjacent paths.

## Testing Strategy

- Run `cd apps/backend && ./gradlew test` after security changes.
- Add tests for:
  - public vs authenticated endpoint access
  - admin-only endpoint protection
  - invalid, expired, or missing token handling
  - password encoding and authentication flow when auth logic changes

## Local Golden Rules

### Do

- Fail closed when token parsing or claim validation is uncertain.
- Keep admin and user authority names consistent with persisted role values and `hasRole` usage.
- Review CORS and preflight handling when changing browser-facing auth behavior.

### Don't

- Do not permit broad wildcard paths just to unblock a frontend screen.
- Do not log raw JWTs, passwords, or sensitive auth payloads.
- Do not add alternate authentication paths that bypass the filter chain without a documented design change.
