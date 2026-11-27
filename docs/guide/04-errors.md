# 4. Errors

All errors have the same format (`application/json`):

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

`details` only appears in validation errors. `location` is `path`, `query` or `body`.

## Error table

| Case | Status | `error` |
| --- | --- | --- |
| Malformed JSON | 400 | `malformed_json` |
| Missing field, wrong type, unknown field or a failed annotation | 422 | `validation_error` |
| Body larger than `maxBodyBytes` | 413 | `payload_too_large` |
| Body that is not JSON in UTF-8 (wrong or missing `Content-Type`, or another `charset`) | 415 | `unsupported_media_type` |
| The route does not exist | 404 | `not_found` |
| Method not allowed on an existing route | 405 | `method_not_allowed`, with an `Allow` header |
| `throw HttpError(...)` in a handler | the given one | `http_error` |
| Unhandled exception | 500 | `internal_error` |

## Responding with an error from a handler

```kotlin
get<UserPath, User>("/users/{id}") { path ->
    users[path.id] ?: throw HttpError(404, "User ${path.id} does not exist")
}
```

`HttpError(status, message)` accepts a status between 400 and 599. The response uses the standard format with `error = "http_error"`.

## Unexpected errors

An unhandled exception responds `500` with a generic message: the body does **not** include the exception or the stack trace. The exception is logged on the server, together with the method and the path.

KastAPI does not ship a logging implementation. Without one on the classpath, the log entry is not written anywhere. Add one, for example (the [logging guide](07-logging.md) shows what else KastAPI logs and how to log every request):

```kotlin
runtimeOnly("ch.qos.logback:logback-classic:1.6.8")
```

## Content type

Routes that read a body only accept JSON in UTF-8. Send `Content-Type: application/json` (the `charset` is optional and can only be `utf-8`):

```bash
curl -X POST localhost:8080/users -H 'Content-Type: application/json' -d '{"email": "a@b.co", "age": 20}'
```

`curl -d` on its own sends `application/x-www-form-urlencoded`, and that gets a `415`. The type is checked before the body is read, so a large body with the wrong type is rejected cheaply.

## Body size

```kotlin
kastApi(config = KastApiConfig { maxBodyBytes = 64 * 1024 }) { /* ... */ }
```

The limit counts bytes and applies with or without `Content-Length`. A non-positive value throws `IllegalArgumentException`.

## Your own `StatusPages`

If your application already installed `StatusPages`, KastAPI does not install its own and respects your handlers. Install it **before** `installKastApi`: if you do it afterwards, Ktor throws `DuplicatePluginException`.

## Methods, `Allow` and `HEAD`

A request with a method that the path does not accept gets a `405` with an `Allow` header listing what is accepted:

```
DELETE /users            ->  405, Allow: GET, HEAD, POST
PUT    /users/5          ->  405, Allow: GET, HEAD, DELETE
```

Every `get` route also answers `HEAD`: same validation, status and headers (including `Content-Length`), and no body. It is useful for health checks and for clients that only need to know whether a resource exists. `OPTIONS` is not answered automatically; it gets a `405` with `Allow`. To handle `OPTIONS`, for example for CORS, install the Ktor plugin you need.

The `Allow` header only covers KastAPI routes. A `405` produced by a route declared with plain Ktor does not carry it.

## Messages

Messages are in English in v0.1. They are kept in a single file to make internationalization easier later.

## Next step

[OpenAPI and Swagger UI](05-openapi.md).
