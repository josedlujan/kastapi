# ADR-0003: DSL with reified types in v0.1; KSP in v0.3

- Status: Accepted
- Date: 2026-11-10
- Update: the [roadmap](../ROADMAP.md) now places KSP in v0.4, after the features of v0.3. The decision does not change.

## Context

The ideal experience ("any function is an endpoint") requires KSP to read function signatures. Lambda parameters cannot be introspected by name at runtime.

## Decision

v0.1 uses a DSL `get<I, O>(path) { input -> ... }` where the input is a single `data class`. Path and query are derived from its properties. KSP is left for v0.3, with function-based controllers.

## Consequences

- Simpler implementation, no code generation, fast iteration.
- The user writes one `data class` per endpoint with input (acceptable; FastAPI also promotes models).
- The DSL's API must be kept even when KSP arrives: both styles will coexist.
