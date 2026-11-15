# 3. Validación

Las reglas se declaran con anotaciones del paquete `dev.kastapi.validation` sobre las propiedades de la entrada. Vienen con `kastapi-core`.

| Anotación | Se aplica a | Regla |
| --- | --- | --- |
| `@Min(value)` | numéricos | mayor o igual a `value` |
| `@Max(value)` | numéricos | menor o igual a `value` |
| `@Length(min, max)` | `String`, `List` | longitud o tamaño dentro del rango |
| `@NotBlank` | `String` | no vacío ni solo espacios |
| `@Email` | `String` | tiene la forma `algo@dominio.ext` (sin espacios) |
| `@Pattern(regex)` | `String` | el texto completo coincide con el regex |

```kotlin
@Serializable
data class CreateUser(
    @Email val email: String,
    @Min(18) val age: Int,
    @Length(min = 2, max = 30) val nickname: String? = null,
)
```

## Qué se valida

- Propiedades de body, path y query.
- Objetos anidados y cada elemento de una lista de objetos.
- Los `null` y los valores por defecto de propiedades opcionales no se validan.
- Se reportan **todos** los errores a la vez, no solo el primero.

Un cuerpo `{"email": "x", "age": 15}` responde `422`:

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "La petición tiene 2 errores de validación",
  "details": [
    { "location": "body", "field": "email", "code": "email", "message": "debe ser un correo electrónico válido", "value": "x" },
    { "location": "body", "field": "age", "code": "min", "message": "debe ser mayor o igual a 18", "value": 15 }
  ]
}
```

Los `code` posibles son `required`, `type`, `unknown_field`, `min`, `max`, `length`, `not_blank`, `email` y `pattern`. El campo `field` usa puntos e índices: `address.zip`, `items[2].name`.

## Detalles

- `@Min` y `@Max` comparan enteros de forma exacta y decimales como `Double`.
- `@Length` cuenta caracteres Unicode; en listas, cuenta elementos.
- Los regex de `@Pattern` se compilan una sola vez, al registrar la ruta.

## Cuidado con `@Pattern`

KastAPI no protege contra expresiones regulares con backtracking catastrófico. Un patrón como `(a+)+$` puede bloquear un hilo ante una entrada hostil. Evita cuantificadores anidados y limita la longitud con `@Length`.

## Errores de configuración

Una anotación en un tipo incompatible (por ejemplo `@Email` sobre un `Int`) o un regex inválido **no se ignoran**: la aplicación falla al arrancar con `KastApiConfigurationException`, que lista todos los problemas encontrados, cada uno con su ruta. También se detectan parámetros `{param}` sin propiedad, rutas duplicadas y tipos no soportados en path o query.

Con `KastApiConfig(validateOnStartup = false)` se omiten las comprobaciones de consistencia; los problemas que impiden construir la ruta se siguen reportando.

## Siguiente paso

[Errores](04-errores.md).
