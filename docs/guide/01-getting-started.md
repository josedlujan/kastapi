# 1. Getting started

KastAPI declares routes with typed functions: `get`, `post`, `put`, `patch` and `delete`. Each one takes two types, the **input** (`I`) and the **output** (`O`). Parameter reading, validation, serialization and the OpenAPI document all come from those types.

## Installation

Follow the [Installation section of the README](../../README.md#installation). In short: add `mavenCentral()` to your repositories and the dependency `dev.kastapi:kastapi-core:0.2.0`. You need JDK 21, Kotlin 2.3 or newer and the `kotlin("plugin.serialization")` plugin.

## Your first API

```kotlin
import dev.kastapi.*
import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String)

fun main() = kastApi(port = 8080) {
    get<NoInput, Health>("/health") { Health("ok") }
}
```

- Models are `data class`es with `@Serializable` (kotlinx.serialization).
- `NoInput` says that the route receives no data.
- The handler is a `suspend` function and its return value is serialized as JSON.
- `kastApi(...)` starts a Netty server on the given port and blocks the thread.

When it starts you have:

| URL | What it is |
| --- | --- |
| `http://localhost:8080/health` | your route |
| `http://localhost:8080/docs` | Swagger UI |
| `http://localhost:8080/openapi.json` | OpenAPI 3.1 document |

## Response status

`post` responds `201` by default and the rest `200`. Change it with `status`:

```kotlin
delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
```

If the output is `Unit`, the response has no body.

## Grouping routes

```kotlin
group("/v1", tags = listOf("users")) {
    get<NoInput, Health>("/health") { Health("ok") }   // GET /v1/health
}
```

## Next step

[Parameters: path, query and body](02-parameters.md).
