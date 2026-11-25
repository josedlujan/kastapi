# ADR-0002: kotlinx.serialization and SerialDescriptor instead of reflection

- Status: Accepted
- Date: 2026-11-10

## Context

FastAPI reads type hints at runtime. In Kotlin the options are `kotlin-reflect` (heavy, slow at startup, fragile with R8/GraalVM), annotation processors/KSP (more complex for an MVP) or the descriptors that the kotlinx.serialization plugin already generates at compile time.

## Decision

For v0.1 all model introspection uses `SerialDescriptor`: property names, optionality (`isElementOptional`), nullability, types and `@SerialInfo` annotations (`getElementAnnotations`). Binding builds a `JsonObject` and decodes with `decodeFromJsonElement`. The DSL's generic types are resolved with `inline reified` functions plus `serializer<T>()`.

## Consequences

- Zero `kotlin-reflect` at runtime.
- Models must be `@Serializable` (acceptable and expected in Kotlin).
- Descriptors do not expose default values: optionality is known, but the default value is not documented in OpenAPI in v0.1.
- It lays the foundation for moving the work to KSP, a later phase of the [roadmap](../ROADMAP.md).
