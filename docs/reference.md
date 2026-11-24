# API reference — v0.1

Describes the public behavior of KastAPI 0.1. Changes to the public API are discussed in an issue before they are implemented.

## 1. Hello world

This code compiles and works as is (it is `examples/hello`):

```kotlin
import dev.kastapi.*
import dev.kastapi.validation.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUser(
    @Email val email: String,
    @Min(18) val age: Int,
    @Length(min = 2, max = 30) val nickname: String? = null,
)

@Serializable
data class UserResponse(val id: Long, val email: String, val nickname: String? = null)

@Serializable
data class UserPath(val id: Long)

@Serializable
data class ListQuery(@Min(1) @Max(100) val limit: Int = 20, val search: String? = null)

fun main() = kastApi(port = 8080) {
    info(title = "Users API", version = "1.0.0", description = "KastAPI example")

    post<CreateUser, UserResponse>("/users", summary = "Creates a user", status = 201) { body ->
        UserResponse(id = 1, email = body.email, nickname = body.nickname)
    }

    get<UserPath, UserResponse>("/users/{id}", summary = "Gets a user") { path ->
        UserResponse(id = path.id, email = "demo@kastapi.dev")
    }

    get<ListQuery, List<UserResponse>>("/users", tags = listOf("users")) { query ->
        emptyList()
    }

    delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
}
// Swagger UI at http://localhost:8080/docs
// Schema at http://localhost:8080/openapi.json
```

## 2. Entry point

```kotlin
public fun kastApi(
    port: Int = 8080,
    host: String = "0.0.0.0",
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
)
```

- Starts a Ktor Netty server and blocks (`wait = true`).
- Variant to integrate into an existing Ktor app (you can always drop down to Ktor):

```kotlin
public fun Application.installKastApi(config: KastApiConfig = KastApiConfig(), block: KastApi.() -> Unit)
```

- `kastApi(...)` calls `installKastApi`, so both forms behave the same. In tests, use `installKastApi` inside `testApplication` or `kastTest`.

`KastApiConfig` (public class, everything has defaults). Create it with a builder; the properties you do not set keep their default:

```kotlin
val config = KastApiConfig {
    docsPath = "/help"
    maxBodyBytes = 64 * 1024
}
```

`KastApiConfig()` gives every default. The constructor with named arguments from 0.1 still works but is deprecated (see CONTRIBUTING for the removal schedule). `KastApiConfig` is not a data class anymore: it has `equals`, `hashCode` and `toString`, but no `copy` or `componentN`.


| Property | Type | Default | Effect |
| --- | --- | --- | --- |
| `docsPath` | `String?` | `"/docs"` | Swagger UI path; `null` disables it |
| `openApiPath` | `String?` | `"/openapi.json"` | Schema path; `null` disables it (and the docs too) |
| `json` | `Json` | `Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }` | Serialization instance |
| `validateOnStartup` | `Boolean` | `true` | Validates the routes at startup (§6) |
| `maxBodyBytes` | `Long` | `1_048_576` | Maximum body size in bytes; must be positive. If exceeded, responds 413 (§8) |
| `maxPatternInputLength` | `Int` | `10_000` | Longest text, in characters, that a `@Pattern` is matched against; a longer one is a `pattern` error. Must be positive |
| `patternTimeoutMillis` | `Long` | `100` | Longest a single `@Pattern` match may take; if exceeded, matching stops and the value is a `pattern` error. Must be positive |

## 3. Declaring routes

`KastApi` has `get`, `post`, `put`, `patch` and `delete`, all with the same shape:

```kotlin
public inline fun <reified I : Any, reified O> KastApi.post(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,        // the default is 201 for post
    noinline handler: suspend KastCall.(I) -> O,
)
```

- `I` = **input model**, `O` = **output model**.
- For routes without input, use `NoInput` (a `@Serializable` object provided by the library): `get<NoInput, Health>("/health") { Health("ok") }`.
- `O = Unit` → response without a body (to use with `204`).
- `KastCall` is the handler's receiver: it exposes `call: ApplicationCall` (the way out to Ktor) and `fun header(name: String): String?`.
- `status` defaults: `post` → 201; the rest → 200.
- Every `get` route also answers `HEAD` with the same binding, validation, status and headers, and no body. `HEAD` is not listed in the OpenAPI document.
- There is also `group(prefix: String, tags: List<String> = emptyList(), block: KastApi.() -> Unit)` to group routes under a prefix.

## 4. Input binding

Given `I` and the method:

1. **Path params**: each property of `I` whose name appears as `{name}` in the route is taken from the path.
2. **GET and DELETE**: the remaining properties are taken from the **query string**.
3. **POST, PUT, PATCH**: if `I` has no path properties, all of `I` is the **JSON body**. If it has path properties, they are combined: path properties from the URL and the others from the body's JSON.
4. Path and query values (always text) are converted according to the property type: `String`, `Int`, `Long`, `Double`, `Boolean`, `enum` (by serialized name), and `List<T>` of those types in query (`?tag=a&tag=b`).
5. Optionality: a property with a default value or a nullable type is optional. Without a default and not nullable, it is required.
6. Query/path properties with unsupported types (nested objects) → startup error (§6).

A request body must be declared as JSON: `Content-Type: application/json` or any structured type such as `application/problem+json`, in any case, with no `charset` or with `charset=utf-8`. Anything else, including a missing header, gets a `415`. The text is always decoded as UTF-8. An empty body is treated as `{}` whatever its type, and routes without input do not read the body.

With the converted values a `JsonObject` is built and decoded with `json.decodeFromJsonElement(serializer<I>(), ...)` (see [ADR-0002](adr/0002-serialdescriptor-no-reflection.md)).

## 5. Validation

Annotations in `dev.kastapi.validation`, all `@SerialInfo` and applicable to properties:

| Annotation | Types | Rule |
| --- | --- | --- |
| `@Min(value: Long)` | numeric | `>= value` |
| `@Max(value: Long)` | numeric | `<= value` |
| `@Length(min: Int = 0, max: Int = Int.MAX_VALUE)` | `String`, `List` | length/size in range |
| `@NotBlank` | `String` | not empty and not only whitespace |
| `@Email` | `String` | simple regex `^[^@\s]+@[^@\s]+\.[^@\s]+$` |
| `@Pattern(regex: String)` | `String` | matches the whole text |

- Nested properties (a `data class` inside a `data class`) and elements of lists of objects are validated.
- `null` values in optional properties are not validated.
- **All** errors are collected, not just the first one.
- Validation is based on `SerialDescriptor.getElementAnnotations(i)` plus the already decoded `JsonElement`; regexes are compiled once per route at startup.

## 6. Startup validation

With `validateOnStartup = true`, installing throws `KastApiConfigurationException` with a message that lists all the problems if:

- A `{param}` of the route does not exist as a property of `I`.
- A path property is nullable or has a default.
- A query/path property has an unsupported type.
- Two routes have the same method and the same route.
- A validation annotation is on an incompatible type (e.g. `@Email` on `Int`).

## 7. OpenAPI and Swagger UI

- KastAPI generates an **OpenAPI 3.1** document and serves it as JSON at `GET {openApiPath}`.
- `info` comes from `info(...)`; if it is not called: `title = "KastAPI"`, `version = "0.0.0"`.
- Each route generates its operation with `summary`, `description`, `tags`, `operationId` (`{method}_{normalized route}`, e.g. `get_users_id`).
- Path and query parameters as `parameters`, with `required` according to §4.5.
- Body as a `requestBody` `application/json` with a `$ref` to `components/schemas`.
- Success response with the declared `status` and its schema; `Unit` = no content.
- Besides the success response, every operation documents the errors KastAPI itself can send: `400`, `413` and `415` when it reads a body, `422` when it has input, and `500` always. They are `$ref`s to shared responses in `components/responses` (`BadRequest`, `PayloadTooLarge`, `UnsupportedMediaType`, `ValidationError`, `InternalServerError`), all with the error schema (§8); only the ones in use are listed. Errors that a handler raises with `HttpError` are not known to KastAPI and are not listed, and neither are `404` and `405`, which no operation produces by itself.
- Schemas from `SerialDescriptor`: primitive types, nullable (`type: [X, "null"]`), lists (`array`), maps `Map<String, T>` (`additionalProperties`), enums (`enum`), classes (`object` with `properties` and `required`). Component name = simple `serialName` (no package); on a name collision the full name is used.
- Validation annotations are reflected: `@Min`→`minimum`, `@Max`→`maximum`, `@Length`→`minLength/maxLength` or `minItems/maxItems`, `@Pattern`→`pattern`, `@Email`→`format: email`, `@NotBlank`→`minLength: 1`.
- The document is generated **once** at startup and cached.
- Swagger UI at `GET {docsPath}`: an HTML page that loads `swagger-ui-dist` from a CDN with a pinned version and points to `openApiPath`.

## 8. Errors

Single format for errors (`application/json`):

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "The request has 2 validation errors",
  "details": [
    { "location": "body", "field": "age", "code": "min", "message": "must be greater than or equal to 18", "value": 15 },
    { "location": "body", "field": "address.zip", "code": "required", "message": "is required" }
  ]
}
```

| Case | Status | `error` | `code` in details |
| --- | --- | --- | --- |
| Malformed JSON | 400 | `malformed_json` | — |
| Body larger than `maxBodyBytes` | 413 | `payload_too_large` | — |
| Body that is not JSON in UTF-8 according to its `Content-Type` | 415 | `unsupported_media_type` | — |
| Required field missing | 422 | `validation_error` | `required` |
| Wrong type (e.g. text in an `Int`) | 422 | `validation_error` | `type` |
| Unknown field in the body | 422 | `validation_error` | `unknown_field` |
| An annotation fails | 422 | `validation_error` | `min`, `max`, `length`, `not_blank`, `email`, `pattern` |
| Route does not exist | 404 | `not_found` | — |
| Method not allowed on an existing route | 405 | `method_not_allowed` | — (with an `Allow` header) |
| Unhandled exception | 500 | `internal_error` | — (no stack trace in the response) |
| `throw HttpError(status, message)` from a handler | the given one | `http_error` | — |

- A `405` carries an `Allow` header with the methods that KastAPI routes accept for that path, in a fixed order (`GET, HEAD, POST, PUT, PATCH, DELETE`; `HEAD` whenever there is a `GET`). For routes that the application declares with plain Ktor the header is left out, because KastAPI does not know their methods. A wrong method on a path with parameters (`PUT /users/5` when only `GET /users/{id}` exists) is a `405`, not a `404`.
- `location`: `path`, `query` or `body`. `field` uses dot and index notation: `items[2].name`.
- Messages are in English by default in v0.1 (internationalization is left for later; all texts live in a single file).
- `HttpError` is a public exception: `public class HttpError(public val status: Int, message: String) : RuntimeException(message)`.

## 9. Test utilities (`kastapi-test`)

```kotlin
public fun kastTest(block: KastApi.() -> Unit, test: suspend KastTestClient.() -> Unit)
```

- Wraps `testApplication` + `installKastApi`.
- `KastTestClient` exposes Ktor's `HttpClient` already configured for JSON, and helpers like `suspend fun <reified T> HttpResponse.bodyAs(): T`.

## 10. Out of scope for v0.1

Dependency injection, authentication, typed headers/cookies, form-data and files, WebSockets, KSP, generated client, API versioning, CORS (use the Ktor plugin directly via `installKastApi`).
