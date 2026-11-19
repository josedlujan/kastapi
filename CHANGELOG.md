# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-11-10

First public release (experimental): the API may change before 1.0.

### Added

- Multi-module Gradle skeleton: `kastapi-annotations`, `kastapi-core`, `kastapi-openapi`, `kastapi-test` and `examples:hello`.
- Version catalog in `gradle/libs.versions.toml` (Kotlin 2.4.21, Ktor 3.6.1, kotlinx.serialization 1.12.0).
- Code quality: ktlint, `explicitApi()` in the library modules and binary-compatibility-validator.
- Continuous integration with GitHub Actions. `apiCheck` always runs.
- Published on Maven Central under the `dev.kastapi` group: `kastapi-core`, `kastapi-annotations`, `kastapi-openapi` and `kastapi-test`, version `0.1.0`, signed, with a POM and sources and javadoc JARs (the javadoc JAR is empty for now). `./gradlew publishToMavenLocal` still works to try local changes.
- Minimal DSL in `kastapi-core`: `kastApi { }`, `installKastApi { }` and `get`, `post`, `put`, `patch` and `delete` routes with `NoInput`.
- `group(prefix, tags) { }` to group routes under a prefix.
- `KastCall` with access to the `ApplicationCall` and `header(name)`.
- `KastApiConfig` with the documented defaults.
- Responses serialized as JSON; `Unit` responds with no body. The default status is 201 for `post` and 200 for the rest.
- Path and query binding for `GET` and `DELETE`: properties of the input `data class` whose name appears as `{name}` come from the path and the rest from the query string.
- Conversion from text to `String`, `Int`, `Long`, `Double`, `Boolean`, `enum` and `List<T>` (`?tag=a&tag=b`), respecting default values and nullable types.
- `422` response with the standard error format (`location`, `field`, `code`, `message`, `value`) for missing required parameters (`required`) or wrong types (`type`), reporting all errors at once.
- All error texts live in a single internal file (`errors/Messages.kt`), in English.
- JSON body binding for `POST`, `PUT` and `PATCH`: if the input has no path properties, the whole model is the body; if it has them, they come from the URL and the rest from the body (the path wins over a repeated value in the body).
- Structural validation of the body against the `SerialDescriptor`, without reflection: missing required fields (`required`), wrong types (`type`, including `null` in a non-nullable), and unknown fields (`unknown_field`, according to `ignoreUnknownKeys` in the configuration). `field` uses dot and index notation (`address.zip`, `items[1].name`) and all errors are reported at once.
- `400` response with `malformed_json` when the body is not valid JSON. An empty body is treated as `{}`.
- Validation annotations in `dev.kastapi.validation` (`kastapi-annotations` module): `@Min`, `@Max`, `@Length`, `@NotBlank`, `@Email` and `@Pattern`.
- Validation engine that reads the annotations from the `SerialDescriptor`, without reflection. It applies to body, path and query properties, nested objects and each element of a list of objects; the error `location` is the property's origin. Errors carry a `code` (`min`, `max`, `length`, `not_blank`, `email`, `pattern`) and the received `value` for primitives.
- `null` and default values of optional properties are not validated. Annotations are only evaluated on values that passed structural validation, and all errors are reported at once.
- `@Min` and `@Max` compare integers exactly and decimals against the value converted to `Double`. `@Length` counts Unicode characters. `@Email` is evaluated in a single pass, so hostile inputs cannot make it slow.
- `@Pattern` regexes are compiled once, when the route is registered; an invalid regex fails the registration and names the property.
- `HttpError(status, message)`: public exception to respond with an HTTP error from a handler (`error = http_error`). The `status` must be between 400 and 599.
- Error handling with `StatusPages` installed by `installKastApi`: `404 not_found` for unknown routes, `405 method_not_allowed` for a method not allowed on an existing route and `500 internal_error` for unhandled exceptions, all in the standard error format.
- The `500` body includes no exception data; the exception is logged on the server with the method and the path. Coroutine cancellations are not turned into `500`.
- If the application already installed its own `StatusPages`, KastAPI does not install its own and respects its handlers.
- `KastApiConfigurationException`: thrown when KastAPI is installed (with `validateOnStartup = true`). Its message lists **all** configuration problems, each with its route: a `{param}` without a property in the input, a nullable path property or one with a default value, an unsupported type in path or query, duplicate routes (same method and same template, ignoring parameter names) and validation annotations on incompatible types.
- An invalid regex in `@Pattern` is also reported as a configuration problem, with the affected property.
- JSON Schema generator (OpenAPI 3.1) in `kastapi-openapi`, from `SerialDescriptor` and without reflection: primitives (with `format` `int32`, `int64`, `float` and `double`), nullable (`type: [X, "null"]`), lists, maps (`additionalProperties`), enums and classes (`object` with `properties` and `required`).
- Classes are registered as components and referenced with `$ref`. If two classes share a simple name, the second one uses the full name. Recursive types and generics with different arguments are supported.
- Validation annotations are reflected in the schema: `@Min`→`minimum`, `@Max`→`maximum`, `@Length`→`minLength`/`maxLength` or `minItems`/`maxItems`, `@Pattern`→`pattern`, `@Email`→`format: email` and `@NotBlank`→`minLength: 1`.
- OpenAPI 3.1 document at `GET /openapi.json` (configurable with `KastApiConfig.openApiPath`), generated once at startup and served from cache. Each route produces an operation with `summary`, `description`, `tags`, `operationId` (`get_users_id`), path and query `parameters`, a JSON `requestBody` and `responses`.
- Operations with input also document the `422` response with the standard error schema (`ErrorResponse` and `ErrorDetail` components).
- When an input mixes path and body properties, the `requestBody` uses a derived `<Name>Body` component without the properties that come from the URL.
- Swagger UI at `GET /docs` (configurable with `KastApiConfig.docsPath`): loads `swagger-ui-dist` 5.34.1 from a CDN with a pinned version and an integrity check (SRI), and points to the OpenAPI document. `docsPath = null` disables the interface and `openApiPath = null` disables both the document and the interface.
- `info(title, version, description)` in the routes block to fill in the document's `info` (defaults: `KastAPI` and `0.0.0`).
- An application `GET` route that matches `docsPath` or `openApiPath` is a configuration problem at startup.
- The classes that `kastapi-core` shares with `kastapi-openapi` are marked with `@InternalKastApi` (mandatory opt-in, excluded from `apiCheck`): they are not part of the stable API.
- `KastApiConfig.maxBodyBytes` (default 1 MiB): a larger body is rejected with `413 payload_too_large` without loading it fully into memory, both with `Content-Length` and with chunked transfer. A non-positive value throws `IllegalArgumentException`.
- `kastapi-test` module: `kastTest(block) { ... }` starts the application in memory (`testApplication` + `installKastApi`) and gives you a `KastTestClient` with a Ktor `HttpClient` configured for JSON and the `HttpResponse.bodyAs<T>()` helper.
- `examples/hello` is the reference "Hello world", unchanged, and starts with `./gradlew :examples:hello:run`. It uses `logback-classic` only in that example, to see the server logs.
- Documentation: public `README.md` (with a Spanish version, `README.es.md`), `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, a guide in `docs/guide` and `docs/STATUS.md` with the project status, known limitations and what is missing for 0.2 and 1.0.

### Known limitations

- `@Pattern` has no protection against regular expressions with catastrophic backtracking.
- `Content-Type: application/json` is not required.
- The `413` is not documented in the OpenAPI document.
- `kastapi-core` does not include a logging implementation (SLF4J): without one on the application's classpath, the `500` log entry is not written anywhere.
- If an application installs `StatusPages` after `installKastApi`, Ktor throws `DuplicatePluginException`: install it before.
- The `405` response does not include the `Allow` header.
- In the JSON schema, `sealed`, polymorphic and contextual types are documented as an open schema (`{}`) and default values are not documented.
- Of the error responses, only the `422` is documented; the `400`, `404`, `405` and `500` are not.
- Swagger UI is loaded from a CDN: without an internet connection the `/docs` interface is not shown (the `/openapi.json` document is still served).
- The project was first written in Spanish and then translated to English. Some text may still be untranslated or read awkwardly; reports and fixes are welcome.

[Unreleased]: https://github.com/josedlujan/kastapi/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/josedlujan/kastapi/releases/tag/v0.1.0
