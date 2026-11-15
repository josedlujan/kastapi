# 3. Validation

Rules are declared with annotations from the `dev.kastapi.validation` package on the properties of the input. They come with `kastapi-core`.

| Annotation | Applies to | Rule |
| --- | --- | --- |
| `@Min(value)` | numeric types | greater than or equal to `value` |
| `@Max(value)` | numeric types | less than or equal to `value` |
| `@Length(min, max)` | `String`, `List` | length or size within the range |
| `@NotBlank` | `String` | not empty and not only whitespace |
| `@Email` | `String` | has the form `something@domain.ext` (no spaces) |
| `@Pattern(regex)` | `String` | the whole text matches the regex |

```kotlin
@Serializable
data class CreateUser(
    @Email val email: String,
    @Min(18) val age: Int,
    @Length(min = 2, max = 30) val nickname: String? = null,
)
```

## What is validated

- Body, path and query properties.
- Nested objects and each element of a list of objects.
- `null` and default values of optional properties are not validated.
- **All** errors are reported at once, not just the first one.

A body `{"email": "x", "age": 15}` responds `422`:

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "The request has 2 validation errors",
  "details": [
    { "location": "body", "field": "email", "code": "email", "message": "must be a valid email address", "value": "x" },
    { "location": "body", "field": "age", "code": "min", "message": "must be greater than or equal to 18", "value": 15 }
  ]
}
```

The possible `code` values are `required`, `type`, `unknown_field`, `min`, `max`, `length`, `not_blank`, `email` and `pattern`. The `field` uses dots and indexes: `address.zip`, `items[2].name`.

## Details

- `@Min` and `@Max` compare integers exactly and decimals as `Double`.
- `@Length` counts Unicode characters; on lists, it counts elements.
- `@Pattern` regexes are compiled once, when the route is registered.

## Be careful with `@Pattern`

KastAPI does not protect against regular expressions with catastrophic backtracking. A pattern such as `(a+)+$` can block a thread on a hostile input. Avoid nested quantifiers and limit the length with `@Length`.

## Configuration errors

An annotation on an incompatible type (for example `@Email` on an `Int`) or an invalid regex is **not ignored**: the application fails at startup with `KastApiConfigurationException`, which lists every problem found, each with its route. Also detected: `{param}` parameters without a property, duplicate routes and unsupported types in path or query.

With `KastApiConfig(validateOnStartup = false)` the consistency checks are skipped; problems that prevent building the route are still reported.

## Next step

[Errors](04-errors.md).
