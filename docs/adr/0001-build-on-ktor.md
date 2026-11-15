# ADR-0001: Build on Ktor

- Status: Accepted
- Date: 2026-11-10

## Context

A new HTTP framework has to solve the server, engines, plugins, TLS, HTTP/2 and testing. None of that is the project's differentiating value.

## Decision

KastAPI is a layer on top of Ktor. It uses its server (Netty by default), routing, content negotiation and status pages. It exposes `Application.installKastApi` to integrate into existing Ktor apps and `KastCall.call` as an escape hatch.

## Consequences

- We get the ecosystem (CORS, auth and metrics plugins) from day one.
- We depend on the evolution of Ktor's API; we pin the version in the catalog and test it in CI.
- Users can drop down to plain Ktor in any route.
