# Changelog

Todos los cambios relevantes de este proyecto se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

## [0.1.0] - 2026-11-10

Primera versión pública (experimental): la API puede cambiar antes de 1.0.

### Added

- Publicación en el repositorio Maven local (`./gradlew publishToMavenLocal`): `dev.kastapi:kastapi-core`, `kastapi-annotations`, `kastapi-openapi` y `kastapi-test`, versión `0.1.0`, con POM y JAR de fuentes. Maven Central queda para una versión posterior.
- `README.md` público, `CONTRIBUTING.md` y plantillas de issues y pull requests.
- Guía en `docs/guide`: primeros pasos, parámetros, validación, errores, OpenAPI e integración con Ktor.
- `docs/STATUS.md`: estado del proyecto, limitaciones conocidas y lo que falta para 0.2 y 1.0.

- Esqueleto multi-módulo de Gradle: `kastapi-annotations`, `kastapi-core`, `kastapi-openapi`, `kastapi-test` y `examples:hello`.
- Catálogo de versiones en `gradle/libs.versions.toml` (Kotlin 2.4.21, Ktor 3.6.1, kotlinx.serialization 1.12.0).
- Calidad de código: ktlint, `explicitApi()` en módulos de librería y binary-compatibility-validator.
- Integración continua con GitHub Actions.
- DSL mínimo en `kastapi-core`: `kastApi { }`, `installKastApi { }` y rutas `get`, `post`, `put`, `patch` y `delete` con `NoInput`.
- `group(prefix, tags) { }` para agrupar rutas bajo un prefijo.
- `KastCall` con acceso al `ApplicationCall` y `header(name)`.
- `KastApiConfig` con los valores por defecto de la especificación.
- Respuestas serializadas como JSON; `Unit` responde sin cuerpo. El status por defecto es 201 en `post` y 200 en el resto.
- Binding de path y query para `GET` y `DELETE`: las propiedades de la `data class` de entrada cuyo nombre aparece como `{nombre}` salen del path y el resto del query string.
- Conversión de texto a `String`, `Int`, `Long`, `Double`, `Boolean`, `enum` y `List<T>` (`?tag=a&tag=b`), con respeto de valores por defecto y tipos nullable.
- Respuesta `422` con el formato de error estándar (`location`, `field`, `code`, `message`, `value`) para parámetros requeridos ausentes (`required`) o con tipo incorrecto (`type`), reportando todos los errores a la vez.
- Los textos de error se centralizan en un único archivo interno (`errors/Messages.kt`).
- Binding del cuerpo JSON en `POST`, `PUT` y `PATCH`: si la entrada no tiene propiedades de path, todo el modelo es el cuerpo; si las tiene, salen de la URL y el resto del cuerpo (el path gana ante un valor repetido en el cuerpo).
- Validación estructural del cuerpo contra el `SerialDescriptor`, sin reflexión: campos requeridos ausentes (`required`), tipos incorrectos (`type`, incluido `null` en no nullable) y campos desconocidos (`unknown_field`, según `ignoreUnknownKeys` de la configuración). Los `field` usan notación de puntos e índices (`address.zip`, `items[1].name`) y se reportan todos los errores a la vez.
- Respuesta `400` con `malformed_json` cuando el cuerpo no es JSON válido. Un cuerpo vacío se trata como `{}`.
- Anotaciones de validación en `dev.kastapi.validation` (módulo `kastapi-annotations`): `@Min`, `@Max`, `@Length`, `@NotBlank`, `@Email` y `@Pattern`.
- Motor de validación que lee las anotaciones del `SerialDescriptor`, sin reflexión. Se aplica a propiedades de body, path y query, a objetos anidados y a cada elemento de una lista de objetos; el `location` del error es el de origen de la propiedad. Los errores llevan `code` (`min`, `max`, `length`, `not_blank`, `email`, `pattern`) y el `value` recibido para primitivos.
- Los `null` y los valores por defecto de propiedades opcionales no se validan. Las anotaciones solo se evalúan sobre valores que pasaron la validación estructural, y se reportan todos los errores a la vez.
- `@Min` y `@Max` comparan enteros de forma exacta y decimales contra el valor convertido a `Double`. `@Length` cuenta caracteres Unicode.
- Los regex de `@Pattern` se compilan una sola vez, al registrar la ruta; un regex inválido hace fallar el registro indicando la propiedad.
- `HttpError(status, message)`: excepción pública para responder con un error HTTP desde un handler (`error = http_error`). El `status` debe estar entre 400 y 599.
- Manejo de errores con `StatusPages` instalado por `installKastApi`: `404 not_found` para rutas inexistentes, `405 method_not_allowed` para un método no permitido en una ruta que existe y `500 internal_error` para excepciones no controladas, todos con el formato de error estándar.
- El `500` no incluye datos de la excepción en el cuerpo; la excepción se registra en el log del servidor con el método y la ruta. Las cancelaciones de corrutina no se convierten en `500`.
- Si la aplicación ya instaló su propio `StatusPages`, KastAPI no instala el suyo y respeta sus manejadores.
- `KastApiConfigurationException`: se lanza al instalar KastAPI (con `validateOnStartup = true`) y su mensaje lista **todos** los problemas de configuración, cada uno con su ruta: parámetro `{param}` sin propiedad en la entrada, propiedad de path nullable o con valor por defecto, tipo no soportado en path o query, rutas duplicadas (mismo método y misma plantilla, sin contar los nombres de los parámetros) y anotaciones de validación en tipos incompatibles.
- Un regex inválido en `@Pattern` también se reporta como problema de configuración, con la propiedad afectada.
- Generador de esquemas JSON Schema (OpenAPI 3.1) en `kastapi-openapi`, a partir de `SerialDescriptor` y sin reflexión: primitivos (con `format` `int32`, `int64`, `float` y `double`), nullable (`type: [X, "null"]`), listas, mapas (`additionalProperties`), enums y clases (`object` con `properties` y `required`).
- Las clases se registran como componentes y se referencian con `$ref`. Si dos clases comparten nombre simple, la segunda usa el nombre completo. Los tipos recursivos y los genéricos con distintos argumentos están soportados.
- Las anotaciones de validación se reflejan en el esquema: `@Min`→`minimum`, `@Max`→`maximum`, `@Length`→`minLength`/`maxLength` o `minItems`/`maxItems`, `@Pattern`→`pattern`, `@Email`→`format: email` y `@NotBlank`→`minLength: 1`.
- Documento OpenAPI 3.1 en `GET /openapi.json` (configurable con `KastApiConfig.openApiPath`), generado una sola vez al arrancar y servido desde caché. Cada ruta genera una operación con `summary`, `description`, `tags`, `operationId` (`get_users_id`), `parameters` de path y query, `requestBody` JSON y `responses`.
- Las operaciones con entrada documentan también la respuesta `422` con el esquema del error estándar (componentes `ErrorResponse` y `ErrorDetail`).
- Cuando una entrada mezcla propiedades de path y de cuerpo, el `requestBody` usa un componente derivado `<Nombre>Body` sin las propiedades que vienen de la URL.
- Swagger UI en `GET /docs` (configurable con `KastApiConfig.docsPath`): carga `swagger-ui-dist` 5.34.1 desde un CDN con versión fija y comprobación de integridad (SRI) y apunta al documento OpenAPI. `docsPath = null` desactiva la interfaz y `openApiPath = null` desactiva el documento y la interfaz.
- `info(title, version, description)` en el bloque de rutas para completar el bloque `info` del documento (por defecto, `KastAPI` y `0.0.0`).
- Una ruta `GET` de la aplicación que coincide con `docsPath` u `openApiPath` es un problema de configuración al arrancar.
- Las clases que `kastapi-core` comparte con `kastapi-openapi` están marcadas con `@InternalKastApi` (opt-in obligatorio, excluidas de `apiCheck`): no forman parte de la API estable.

- Módulo `kastapi-test`: `kastTest(block) { ... }` levanta la aplicación en memoria (`testApplication` + `installKastApi`) y entrega un `KastTestClient` con el `HttpClient` de Ktor configurado con JSON y el helper `HttpResponse.bodyAs<T>()`.
- `examples/hello` es el "Hola mundo" de la referencia, sin cambios, y arranca con `./gradlew :examples:hello:run`. Usa `logback-classic` solo en ese ejemplo, para ver los logs del servidor.
- El paso `apiCheck` de la integración continua se ejecuta siempre.

- `KastApiConfig.maxBodyBytes` (por defecto 1 MiB): un cuerpo mayor se rechaza con `413 payload_too_large` sin cargarlo entero en memoria, tanto con `Content-Length` como en transferencia chunked. Un valor no positivo lanza `IllegalArgumentException`.

### Known limitations

- `@Pattern` no tiene protección contra expresiones regulares con backtracking catastrófico.
- No se exige `Content-Type: application/json`.
- El `413` no se documenta en el documento OpenAPI.
- `kastapi-core` no incluye una implementación de logging (SLF4J): sin una en el classpath de la aplicación, el registro del `500` no se escribe en ningún sitio.
- Si una aplicación instala `StatusPages` después de `installKastApi`, Ktor lanzará `DuplicatePluginException`: hay que instalarlo antes.
- La respuesta `405` no incluye el header `Allow`.
- En el esquema JSON, los tipos `sealed`, polimórficos y contextuales se documentan como un esquema abierto (`{}`) y los valores por defecto no se documentan.
- Del conjunto de respuestas de error solo se documenta el `422`; no se documentan el `400`, el `404`, el `405` ni el `500`.
- Swagger UI se carga desde un CDN: sin conexión a internet la interfaz de `/docs` no se muestra (el documento `/openapi.json` sí se sirve).


[Unreleased]: https://github.com/josedlujan/kastapi/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/josedlujan/kastapi/releases/tag/v0.1.0
