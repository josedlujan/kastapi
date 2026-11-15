# 4. Errores

Todos los errores tienen el mismo formato (`application/json`):

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "The request has 2 validation errors",
  "details": [
    { "location": "body", "field": "age", "code": "min", "message": "must be greater than or equal to 18", "value": 15 }
  ]
}
```

`details` solo aparece en los errores de validación. `location` es `path`, `query` o `body`.

## Tabla de errores

| Caso | Status | `error` |
| --- | --- | --- |
| JSON mal formado | 400 | `malformed_json` |
| Falta un campo, tipo incorrecto, campo desconocido o falla una anotación | 422 | `validation_error` |
| Cuerpo mayor que `maxBodyBytes` | 413 | `payload_too_large` |
| La ruta no existe | 404 | `not_found` |
| Método no permitido en una ruta que existe | 405 | `method_not_allowed` |
| `throw HttpError(...)` en un handler | el indicado | `http_error` |
| Excepción no controlada | 500 | `internal_error` |

## Responder un error desde un handler

```kotlin
get<UserPath, User>("/users/{id}") { path ->
    users[path.id] ?: throw HttpError(404, "El usuario ${path.id} no existe")
}
```

`HttpError(status, message)` acepta un status entre 400 y 599. La respuesta usa el formato estándar con `error = "http_error"`.

## Errores inesperados

Una excepción no controlada responde `500` con un mensaje genérico: el cuerpo **no** incluye la excepción ni el stack trace. La excepción se registra en el log del servidor, junto con el método y la ruta.

KastAPI no trae una implementación de logging. Sin una en el classpath, el registro no se escribe en ningún sitio. Añade una, por ejemplo:

```kotlin
runtimeOnly("ch.qos.logback:logback-classic:1.6.6")
```

## Tamaño del cuerpo

```kotlin
kastApi(config = KastApiConfig(maxBodyBytes = 64 * 1024)) { /* ... */ }
```

El límite cuenta bytes y se aplica con o sin `Content-Length`. Un valor no positivo lanza `IllegalArgumentException`.

## Tu propio `StatusPages`

Si tu aplicación ya instaló `StatusPages`, KastAPI no instala el suyo y respeta tus manejadores. Instálalo **antes** de `installKastApi`: si lo haces después, Ktor lanza `DuplicatePluginException`.

La respuesta `405` no incluye el header `Allow`.

## Mensajes

Los mensajes están en inglés en v0.1. Están centralizados en un solo archivo para facilitar la internacionalización más adelante.

## Siguiente paso

[OpenAPI y Swagger UI](05-openapi.md).
