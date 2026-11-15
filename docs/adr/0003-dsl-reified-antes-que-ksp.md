# ADR-0003: DSL con tipos reified en v0.1; KSP en v0.3

- Estado: Aceptado
- Fecha: 2026-11-10

## Contexto

La experiencia ideal ("cualquier función es un endpoint") requiere KSP para leer firmas de funciones. Los parámetros de lambdas no se pueden introspeccionar con nombres en runtime.

## Decisión

v0.1 usa un DSL `get<I, O>(path) { input -> ... }` donde la entrada es una única `data class`. Path y query se derivan de sus propiedades. KSP queda para v0.3 con controladores por función.

## Consecuencias

- Implementación más simple, sin generación de código, iteración rápida.
- El usuario escribe una `data class` por endpoint con entrada (aceptable; FastAPI también promueve modelos).
- La API del DSL debe mantenerse aunque llegue KSP: ambos estilos convivirán.
