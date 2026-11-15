# ADR-0002: kotlinx.serialization y SerialDescriptor en lugar de reflexión

- Estado: Aceptado
- Fecha: 2026-11-10

## Contexto

FastAPI lee los type hints en runtime. En Kotlin las opciones son `kotlin-reflect` (pesado, lento al arrancar, frágil con R8/GraalVM), procesadores de anotaciones/KSP (más complejo para un MVP) o los descriptores que el plugin de kotlinx.serialization ya genera en compilación.

## Decisión

Para v0.1 toda introspección de modelos usa `SerialDescriptor`: nombres de propiedades, opcionalidad (`isElementOptional`), nulabilidad, tipos y anotaciones `@SerialInfo` (`getElementAnnotations`). El binding construye un `JsonObject` y decodifica con `decodeFromJsonElement`. Los tipos genéricos del DSL se resuelven con funciones `inline reified` + `serializer<T>()`.

## Consecuencias

- Cero `kotlin-reflect` en runtime.
- Los modelos deben ser `@Serializable` (aceptable y esperado en Kotlin).
- Los descriptores no exponen valores por defecto: la opcionalidad se sabe, el valor default no se documenta en OpenAPI en v0.1.
- Sienta la base para mover el trabajo a KSP en la fase 3.
