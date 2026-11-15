# Roadmap

El estado actual, las limitaciones conocidas y lo que falta para cada versión están en [STATUS.md](STATUS.md).

| Fase | Versión | Contenido | Condición para empezar |
| --- | --- | --- | --- |
| 1 | v0.1 | MVP: DSL tipado, binding, validación, errores, OpenAPI 3.1, Swagger UI | — |
| 2 | v0.2 | Dependencias inyectables (`depends`), headers y cookies tipados, respuestas múltiples documentadas, i18n de mensajes | v0.1 publicada y primeros usuarios |
| 3 | v0.3 | Procesador KSP: controladores por función anotada, validación y planes de binding generados en compilación | Diseño en ADR aprobado |
| 4 | v0.4 | Cliente Kotlin Multiplatform generado desde los mismos modelos | KSP estable |
| 5 | v1.0 | Auth (JWT/API key) integrada con OpenAPI `securitySchemes`, estabilidad de API, Maven Central, sitio de documentación | Feedback de adopción |

## Ideas para evaluar después

- Generación de pruebas de contrato desde el esquema.
- Integración con Koin como alternativa a la DI propia.
- Plantilla `gradle init`/arquetipo para empezar un proyecto.
- Benchmarks comparativos contra Ktor puro y Spring WebFlux.
