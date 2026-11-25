# Roadmap

The current status, the known limitations and what is missing for each version are in [STATUS.md](STATUS.md).

| Phase | Version | Content | Condition to start |
| --- | --- | --- | --- |
| 1 | v0.1 (released) | MVP: typed DSL, binding, validation, errors, OpenAPI 3.1, Swagger UI | — |
| 2 | v0.2 (released) | Hardening: `415` for non-JSON bodies, `Allow` on `405` and automatic `HEAD`, errors documented in OpenAPI, `KastApiConfig` builder, length and time limits for `@Pattern` | — |
| 3 | v0.3 | Injectable dependencies (`depends`), typed headers and cookies, documented multiple responses, i18n of messages. Removes the deprecated `KastApiConfig` constructor with named arguments | First users and feedback |
| 4 | v0.4 | KSP processor: controllers from annotated functions, validation and binding plans generated at compile time | Design approved as an ADR |
| 5 | v0.5 | Kotlin Multiplatform client generated from the same models | Stable KSP |
| 6 | v1.0 | Auth (JWT/API key) integrated with OpenAPI `securitySchemes`, API stability, API documentation site | Adoption feedback |

The version numbers are an order of intent, not a schedule.

## Ideas to evaluate later

- Contract test generation from the schema.
- Koin integration as an alternative to the built-in DI.
- A `gradle init` template/archetype to start a project.
- Benchmarks against plain Ktor and Spring WebFlux.
