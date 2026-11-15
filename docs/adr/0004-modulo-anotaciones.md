# ADR-0004: Módulo `kastapi-annotations`

- Estado: Aceptado
- Fecha: 2026-11-10

## Contexto

Las anotaciones de validación las necesitan `kastapi-core` (para validar) y `kastapi-openapi` (para documentar), y `openapi` no debe depender de `core`.

## Decisión

Crear `kastapi-annotations` con el paquete `dev.kastapi.validation` que contiene solo las anotaciones públicas. `core` y `openapi` dependen de él; `core` lo expone como `api(...)` para que el usuario no lo declare aparte.

## Consecuencias

- Grafo sin ciclos.
- Un módulo extra muy pequeño, que más adelante también usará el cliente KMP (las anotaciones no dependen de la JVM, puede volverse multiplataforma).
