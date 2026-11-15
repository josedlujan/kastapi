# 2. Parámetros: path, query y body

La `data class` de entrada define de dónde sale cada valor.

## Reglas

1. Una propiedad cuyo nombre aparece como `{nombre}` en la ruta sale del **path**.
2. En `GET` y `DELETE`, el resto de propiedades salen del **query string**.
3. En `POST`, `PUT` y `PATCH`, si la entrada no tiene propiedades de path, **toda** la entrada es el cuerpo JSON. Si las tiene, las de path salen de la URL y las demás del cuerpo.

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

## Tipos soportados en path y query

`String`, `Int`, `Long`, `Double`, `Boolean`, `enum` (por su nombre serializado) y, solo en query, `List<T>` de esos tipos (`?tag=a&tag=b`). Un objeto anidado en path o query es un error de configuración que se detecta al arrancar.

## Opcionales y requeridos

- Con valor por defecto o tipo nullable, el parámetro es opcional.
- Sin valor por defecto y no nullable, es requerido: si falta, la respuesta es `422` con `code: "required"`.
- Los parámetros de path deben ser requeridos: no pueden ser nullable ni tener valor por defecto.

## Path y cuerpo juntos

```kotlin
@Serializable
data class UpdateUser(val id: Long, @Length(min = 2, max = 30) val name: String)

put<UpdateUser, User>("/users/{id}") { input -> /* id desde la URL, name desde el JSON */ }
```

Si el cuerpo también trae `id`, **gana el valor de la URL**. En OpenAPI, el `requestBody` de estas rutas usa un componente derivado `UpdateUserBody`, sin las propiedades que vienen de la URL.

## Cuerpo JSON

- Un cuerpo vacío se trata como `{}`.
- Los campos desconocidos son un error `422` (`unknown_field`), salvo que configures `Json { ignoreUnknownKeys = true }` en `KastApiConfig.json`.
- Un JSON mal formado responde `400` (`malformed_json`).
- El tamaño máximo es `KastApiConfig.maxBodyBytes` (1 MiB por defecto). Si se supera, responde `413`.

## Acceso a la petición de Ktor

El handler es una extensión de `KastCall`, que expone `call` (el `ApplicationCall` de Ktor) y `header(name)`:

```kotlin
get<NoInput, User>("/me") { User(1, header("X-User") ?: "anónimo") }
```

## Siguiente paso

[Validación](03-validacion.md).
