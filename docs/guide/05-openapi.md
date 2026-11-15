# 5. OpenAPI y Swagger UI

KastAPI genera un documento **OpenAPI 3.1** a partir de los tipos de tus rutas y lo sirve en dos URL:

| URL por defecto | Contenido |
| --- | --- |
| `/openapi.json` | documento OpenAPI |
| `/docs` | Swagger UI |

El documento se genera una sola vez al arrancar y se sirve desde caché.

## Información de la API

```kotlin
kastApi {
    info(title = "Users API", version = "1.0.0", description = "Ejemplo de KastAPI")
    // ...
}
```

Sin `info`, el título es `KastAPI` y la versión `0.0.0`.

## Documentar cada ruta

```kotlin
get<UserPath, User>(
    "/users/{id}",
    summary = "Obtiene un usuario",
    description = "Busca por identificador.",
    tags = listOf("users"),
) { path -> /* ... */ }
```

Cada ruta genera una operación con `summary`, `description`, `tags` y un `operationId` como `get_users_id`.

## Qué se documenta

- Parámetros de path y query, con `required` según el tipo.
- `requestBody` JSON con un `$ref` a `components/schemas`.
- La respuesta exitosa con el `status` declarado. Con `Unit` no hay contenido.
- La respuesta `422` en toda operación con entrada, con el esquema del error estándar.
- Las anotaciones de validación: `@Min` → `minimum`, `@Max` → `maximum`, `@Length` → `minLength`/`maxLength` (o `minItems`/`maxItems`), `@Pattern` → `pattern`, `@Email` → `format: email`, `@NotBlank` → `minLength: 1`.
- Tipos nullable, listas, mapas, enums y clases anidadas.

## Limitaciones de 0.1.0

- Solo se documenta el `422`. No se documentan el `400`, `404`, `405`, `413` ni `500`.
- Los tipos `sealed`, polimórficos y contextuales se documentan como un esquema abierto (`{}`).
- Los valores por defecto no aparecen en el esquema.

## Cambiar o desactivar las rutas

```kotlin
KastApiConfig(docsPath = "/ayuda", openApiPath = "/schema.json")
KastApiConfig(docsPath = null)      // sin Swagger UI, el documento se sirve igual
KastApiConfig(openApiPath = null)   // sin documento y, por tanto, sin Swagger UI
```

Una ruta `GET` de tu aplicación que coincida con `docsPath` u `openApiPath` es un error de configuración al arrancar.

## Swagger UI y el CDN

La página `/docs` carga `swagger-ui-dist` 5.34.1 desde un CDN, con la versión fija y comprobación de integridad (SRI). **Sin conexión a internet, la interfaz no se muestra**; `/openapi.json` sí se sirve y puedes abrirlo en cualquier otra herramienta.

## Siguiente paso

[Integrar con Ktor y probar](06-integrar-con-ktor.md).
