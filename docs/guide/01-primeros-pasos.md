# 1. Primeros pasos

KastAPI declara las rutas con funciones tipadas: `get`, `post`, `put`, `patch` y `delete`. Cada una recibe dos tipos, el de **entrada** (`I`) y el de **salida** (`O`). De esos tipos salen la lectura de parámetros, la validación, la serialización y el documento OpenAPI.

## Instalación

Sigue la sección [Instalación del README](../../README.md#instalación). Resumen: `./gradlew publishToMavenLocal` en este repositorio y, en tu proyecto, `mavenLocal()` más la dependencia `dev.kastapi:kastapi-core:0.1.0`. Necesitas JDK 21, Kotlin 2.4.21 y el plugin `kotlin("plugin.serialization")`.

## Tu primera API

```kotlin
import dev.kastapi.*
import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String)

fun main() = kastApi(port = 8080) {
    get<NoInput, Health>("/health") { Health("ok") }
}
```

- Los modelos son `data class` con `@Serializable` (kotlinx.serialization).
- `NoInput` indica que la ruta no recibe datos.
- El handler es una función `suspend` y su valor de retorno se serializa como JSON.
- `kastApi(...)` arranca un servidor Netty en el puerto indicado y bloquea el hilo.

Al arrancar tienes:

| URL | Qué es |
| --- | --- |
| `http://localhost:8080/health` | tu ruta |
| `http://localhost:8080/docs` | Swagger UI |
| `http://localhost:8080/openapi.json` | documento OpenAPI 3.1 |

## Status de respuesta

`post` responde `201` por defecto y el resto `200`. Cámbialo con `status`:

```kotlin
delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
```

Si la salida es `Unit`, la respuesta no lleva cuerpo.

## Agrupar rutas

```kotlin
group("/v1", tags = listOf("users")) {
    get<NoInput, Health>("/health") { Health("ok") }   // GET /v1/health
}
```

## Siguiente paso

[Parámetros: path, query y body](02-parametros.md).
