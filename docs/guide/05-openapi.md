# 5. OpenAPI and Swagger UI

KastAPI generates an **OpenAPI 3.1** document from the types of your routes and serves it at two URLs:

| Default URL | Content |
| --- | --- |
| `/openapi.json` | OpenAPI document |
| `/docs` | Swagger UI |

The document is generated once at startup and served from cache.

## API information

```kotlin
kastApi {
    info(title = "Users API", version = "1.0.0", description = "KastAPI example")
    // ...
}
```

Without `info`, the title is `KastAPI` and the version is `0.0.0`.

## Documenting each route

```kotlin
get<UserPath, User>(
    "/users/{id}",
    summary = "Gets a user",
    description = "Looks it up by identifier.",
    tags = listOf("users"),
) { path -> /* ... */ }
```

Each route generates an operation with `summary`, `description`, `tags` and an `operationId` such as `get_users_id`.

## What is documented

- Path and query parameters, with `required` according to the type.
- A JSON `requestBody` with a `$ref` to `components/schemas`.
- The success response with the declared `status`. With `Unit` there is no content.
- The `422` response on every operation with input, with the standard error schema.
- Validation annotations: `@Min` → `minimum`, `@Max` → `maximum`, `@Length` → `minLength`/`maxLength` (or `minItems`/`maxItems`), `@Pattern` → `pattern`, `@Email` → `format: email`, `@NotBlank` → `minLength: 1`.
- Nullable types, lists, maps, enums and nested classes.

## Limitations in 0.1.0

- Only the `422` is documented. The `400`, `404`, `405`, `413` and `500` are not.
- `sealed`, polymorphic and contextual types are documented as an open schema (`{}`).
- Default values do not appear in the schema.

## Changing or disabling the routes

```kotlin
KastApiConfig(docsPath = "/help", openApiPath = "/schema.json")
KastApiConfig(docsPath = null)      // no Swagger UI, the document is still served
KastApiConfig(openApiPath = null)   // no document and therefore no Swagger UI
```

A `GET` route of your application that matches `docsPath` or `openApiPath` is a configuration error at startup.

## Swagger UI and the CDN

The `/docs` page loads `swagger-ui-dist` 5.34.1 from a CDN, with a pinned version and an integrity check (SRI). **Without an internet connection the interface is not shown**; `/openapi.json` is still served and you can open it in any other tool.

## Next step

[Integrating with Ktor and testing](06-ktor-integration.md).
