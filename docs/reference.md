# Referencia de la API — v0.1

Describe el comportamiento público de KastAPI 0.1. Los cambios en la API pública se discuten en un issue antes de implementarse.

## 1. Hola mundo

Este código compila y funciona tal cual (es `examples/hello`):

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
    info(title = "Users API", version = "1.0.0", description = "Ejemplo de KastAPI")

    post<CreateUser, UserResponse>("/users", summary = "Crea un usuario", status = 201) { body ->
        UserResponse(id = 1, email = body.email, nickname = body.nickname)
    }

    get<UserPath, UserResponse>("/users/{id}", summary = "Obtiene un usuario") { path ->
        UserResponse(id = path.id, email = "demo@kastapi.dev")
    }

    get<ListQuery, List<UserResponse>>("/users", tags = listOf("users")) { query ->
        emptyList()
    }

    delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
}
// Swagger UI en http://localhost:8080/docs
// Esquema en http://localhost:8080/openapi.json
```

## 2. Punto de entrada

```kotlin
public fun kastApi(
    port: Int = 8080,
    host: String = "0.0.0.0",
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
)
```

- Arranca un servidor Ktor Netty y bloquea (`wait = true`).
- Variante para integrarse en una app Ktor existente (siempre se puede bajar a Ktor):

```kotlin
public fun Application.installKastApi(config: KastApiConfig = KastApiConfig(), block: KastApi.() -> Unit)
```

- `kastApi(...)` llama a `installKastApi`, así que ambas formas se comportan igual. En pruebas, usa `installKastApi` dentro de `testApplication` o `kastTest`.

`KastApiConfig` (data class pública, todo con defaults):

| Propiedad | Tipo | Default | Efecto |
| --- | --- | --- | --- |
| `docsPath` | `String?` | `"/docs"` | Ruta de Swagger UI; `null` la desactiva |
| `openApiPath` | `String?` | `"/openapi.json"` | Ruta del esquema; `null` lo desactiva (y también docs) |
| `json` | `Json` | `Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }` | Instancia de serialización |
| `validateOnStartup` | `Boolean` | `true` | Valida las rutas al arrancar (§6) |
| `maxBodyBytes` | `Long` | `1_048_576` | Tamaño máximo del cuerpo en bytes; positivo. Si se supera, responde 413 (§8) |

## 3. Declaración de rutas

En `KastApi` existen `get`, `post`, `put`, `patch`, `delete`, todas con la misma forma:

```kotlin
public inline fun <reified I : Any, reified O> KastApi.post(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,        // en post el default es 201
    noinline handler: suspend KastCall.(I) -> O,
)
```

- `I` = **modelo de entrada**, `O` = **modelo de salida**.
- Para rutas sin entrada se usa `NoInput` (objeto `@Serializable` provisto por la librería): `get<NoInput, Health>("/health") { Health("ok") }`.
- `O = Unit` → respuesta sin cuerpo (para usar con `204`).
- `KastCall` es el receptor del handler: expone `call: ApplicationCall` (escape a Ktor) y `fun header(name: String): String?`.
- Defaults de `status`: `post` → 201; el resto → 200.
- Además existe `group(prefix: String, tags: List<String> = emptyList(), block: KastApi.() -> Unit)` para agrupar rutas bajo un prefijo.

## 4. Binding de la entrada

Dado `I` y el método:

1. **Path params**: cada propiedad de `I` cuyo nombre aparezca como `{nombre}` en la ruta se toma del path.
2. **GET y DELETE**: el resto de propiedades se toman del **query string**.
3. **POST, PUT, PATCH**: si `I` no tiene propiedades de path, todo `I` es el **cuerpo JSON**. Si tiene propiedades de path, se combinan: las de path desde la URL y las demás desde el JSON del cuerpo.
4. Los valores de path y query (siempre texto) se convierten según el tipo de la propiedad: `String`, `Int`, `Long`, `Double`, `Boolean`, `enum` (por nombre serializado), y `List<T>` de esos tipos en query (`?tag=a&tag=b`).
5. Opcionalidad: propiedad con valor por defecto o tipo nullable = opcional. Sin default y no nullable = requerida.
6. Propiedades de query/path con tipos no soportados (objetos anidados) → error al arrancar (§6).

Con los valores convertidos se construye un `JsonObject` que se decodifica con `json.decodeFromJsonElement(serializer<I>(), ...)` (ver [ADR-0002](adr/0002-serialdescriptor-sin-reflexion.md)).

## 5. Validación

Anotaciones en `dev.kastapi.validation`, todas `@SerialInfo` y aplicables a propiedades:

| Anotación | Tipos | Regla |
| --- | --- | --- |
| `@Min(value: Long)` | numéricos | `>= value` |
| `@Max(value: Long)` | numéricos | `<= value` |
| `@Length(min: Int = 0, max: Int = Int.MAX_VALUE)` | `String`, `List` | longitud/tamaño en rango |
| `@NotBlank` | `String` | no vacío ni solo espacios |
| `@Email` | `String` | regex simple `^[^@\s]+@[^@\s]+\.[^@\s]+$` |
| `@Pattern(regex: String)` | `String` | coincide completo |

- Se validan propiedades anidadas (`data class` dentro de `data class`) y elementos de listas de objetos.
- Valores `null` en propiedades opcionales no se validan.
- Se recolectan **todos** los errores, no solo el primero.
- Validación basada en `SerialDescriptor.getElementAnnotations(i)` + el `JsonElement` ya decodificado; los regex se compilan una vez por ruta al arrancar.

## 6. Validación al arrancar

Con `validateOnStartup = true`, al instalar se lanza `KastApiConfigurationException` con un mensaje que lista todos los problemas si:

- Un `{param}` de la ruta no existe como propiedad en `I`.
- Una propiedad de path es nullable o tiene default.
- Una propiedad de query/path tiene tipo no soportado.
- Dos rutas con mismo método y misma ruta.
- Una anotación de validación está en un tipo incompatible (p. ej. `@Email` en `Int`).

## 7. OpenAPI y Swagger UI

- KastAPI genera un documento **OpenAPI 3.1** y lo sirve como JSON en `GET {openApiPath}`.
- `info` desde `info(...)`; si no se llama: `title = "KastAPI"`, `version = "0.0.0"`.
- Cada ruta genera su operación con `summary`, `description`, `tags`, `operationId` (`{método}_{ruta normalizada}`, p. ej. `get_users_id`).
- Parámetros de path y query como `parameters` con `required` según §4.5.
- Cuerpo como `requestBody` `application/json` con `$ref` a `components/schemas`.
- Respuesta exitosa con el `status` declarado y su esquema; `Unit` = sin contenido.
- Toda operación con entrada documenta también la respuesta `422` con el esquema de error (§8).
- Esquemas desde `SerialDescriptor`: tipos primitivos, nullable (`type: [X, "null"]`), listas (`array`), mapas `Map<String, T>` (`additionalProperties`), enums (`enum`), clases (`object` con `properties` y `required`). Nombre del componente = `serialName` simple (sin paquete); colisión de nombres → usar nombre completo.
- Las anotaciones de validación se reflejan: `@Min`→`minimum`, `@Max`→`maximum`, `@Length`→`minLength/maxLength` o `minItems/maxItems`, `@Pattern`→`pattern`, `@Email`→`format: email`, `@NotBlank`→`minLength: 1`.
- El documento se genera **una vez** al arrancar y se cachea.
- Swagger UI en `GET {docsPath}`: un HTML que carga `swagger-ui-dist` desde CDN con versión fija y apunta a `openApiPath`.

## 8. Errores

Formato único para errores (`application/json`):

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "La petición tiene 2 errores de validación",
  "details": [
    { "location": "body", "field": "age", "code": "min", "message": "debe ser mayor o igual a 18", "value": 15 },
    { "location": "body", "field": "address.zip", "code": "required", "message": "es requerido" }
  ]
}
```

| Caso | Status | `error` | `code` en details |
| --- | --- | --- | --- |
| JSON mal formado | 400 | `malformed_json` | — |
| Cuerpo mayor que `maxBodyBytes` | 413 | `payload_too_large` | — |
| Falta campo requerido | 422 | `validation_error` | `required` |
| Tipo incorrecto (p. ej. texto en `Int`) | 422 | `validation_error` | `type` |
| Campo desconocido en el body | 422 | `validation_error` | `unknown_field` |
| Falla una anotación | 422 | `validation_error` | `min`, `max`, `length`, `not_blank`, `email`, `pattern` |
| Ruta no existe | 404 | `not_found` | — |
| Método no permitido en una ruta que sí existe | 405 | `method_not_allowed` | — |
| Excepción no controlada | 500 | `internal_error` | — (sin stack trace en la respuesta) |
| `throw HttpError(status, message)` desde un handler | el indicado | `http_error` | — |

- `location`: `path`, `query` o `body`. `field` usa notación de puntos e índices: `items[2].name`.
- Mensajes en español por defecto en v0.1 (internacionalización queda para después; centraliza los textos en un solo archivo).
- `HttpError` es una excepción pública: `public class HttpError(public val status: Int, message: String) : RuntimeException(message)`.

## 9. Pruebas utilitarias (`kastapi-test`)

```kotlin
public fun kastTest(block: KastApi.() -> Unit, test: suspend KastTestClient.() -> Unit)
```

- Envuelve `testApplication` + `installKastApi`.
- `KastTestClient` expone el `HttpClient` de Ktor ya configurado con JSON, y helpers `suspend fun <reified T> HttpResponse.bodyAs(): T`.

## 10. Fuera de alcance de v0.1

Inyección de dependencias, autenticación, headers/cookies tipados, form-data y archivos, WebSockets, KSP, cliente generado, versionado de API, CORS (se usa el plugin de Ktor directamente vía `installKastApi`).
