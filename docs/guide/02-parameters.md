# 2. Parameters: path, query and body

The input `data class` defines where each value comes from.

## Rules

1. A property whose name appears as `{name}` in the route comes from the **path**.
2. In `GET` and `DELETE`, the remaining properties come from the **query string**.
3. In `POST`, `PUT` and `PATCH`, if the input has no path properties, **the whole** input is the JSON body. If it has them, the path properties come from the URL and the rest from the body.

```kotlin
@Serializable
data class UserPath(val id: Long)

@Serializable
data class Search(
    @Min(1) @Max(100) val limit: Int = 20,
    val search: String? = null,
    val tag: List<String> = emptyList(),
)

get<UserPath, User>("/users/{id}") { path -> /* path.id */ }
get<Search, List<User>>("/users") { query -> /* ?limit=5&search=ana&tag=a&tag=b */ }
```

## Supported types in path and query

`String`, `Int`, `Long`, `Double`, `Boolean`, `enum` (by its serialized name) and, in query only, `List<T>` of those types (`?tag=a&tag=b`). A nested object in path or query is a configuration error detected at startup.

## Optional and required

- With a default value or a nullable type, the parameter is optional.
- Without a default value and not nullable, it is required: if it is missing, the response is `422` with `code: "required"`.
- Path parameters must be required: they cannot be nullable or have a default value.

## Path and body together

```kotlin
@Serializable
data class UpdateUser(val id: Long, @Length(min = 2, max = 30) val name: String)

put<UpdateUser, User>("/users/{id}") { input -> /* id from the URL, name from the JSON */ }
```

If the body also contains `id`, **the URL value wins**. In OpenAPI, the `requestBody` of these routes uses a derived component `UpdateUserBody`, without the properties that come from the URL.

## JSON body

- The body must be declared as JSON: `Content-Type: application/json` (or a structured type such as `application/problem+json`), with no `charset` or with `charset=utf-8`. Anything else, including a missing header, gets a `415 unsupported_media_type`. Text is always decoded as UTF-8.
- An empty body is treated as `{}`, whatever its `Content-Type`.
- Unknown fields are a `422` error (`unknown_field`), unless you configure `Json { ignoreUnknownKeys = true }` in `KastApiConfig.json`.
- Malformed JSON responds `400` (`malformed_json`).
- The maximum size is `KastApiConfig.maxBodyBytes` (1 MiB by default). If it is exceeded, the response is `413`.

`HEAD` requests to a `get` route are bound and validated in the same way and get the same status, without a body.

## Access to the Ktor request

The handler is an extension on `KastCall`, which exposes `call` (Ktor's `ApplicationCall`) and `header(name)`:

```kotlin
get<NoInput, User>("/me") { User(1, header("X-User") ?: "anonymous") }
```

## Next step

[Validation](03-validation.md).
