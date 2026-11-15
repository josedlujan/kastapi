# ADR-0004: `kastapi-annotations` module

- Status: Accepted
- Date: 2026-11-10

## Context

The validation annotations are needed by `kastapi-core` (to validate) and by `kastapi-openapi` (to document them), and `openapi` must not depend on `core`.

## Decision

Create `kastapi-annotations` with the `dev.kastapi.validation` package, which contains only the public annotations. `core` and `openapi` depend on it; `core` exposes it as `api(...)` so the user does not have to declare it separately.

## Consequences

- A dependency graph without cycles.
- One extra, very small module, which the KMP client will also use later (the annotations do not depend on the JVM, so it can become multiplatform).
