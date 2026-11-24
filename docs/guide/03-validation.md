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

A regular expression with catastrophic backtracking can keep a thread busy for a long time. Modern JDKs optimize many classic cases, but not all: with a pattern such as `(.*a){20}`, a 28-character input takes several seconds, and `^(a{1,9}){1,9}$` needs about two seconds for 40 characters (measured on JDK 21). A hostile client can send such inputs on purpose.

KastAPI limits the damage with two settings in `KastApiConfig`:

```kotlin
KastApiConfig {
    maxPatternInputLength = 10_000   // longest text a @Pattern is matched against (default)
    patternTimeoutMillis = 100       // longest a single match may take (default)
}
```

- A text longer than `maxPatternInputLength` is not matched: it is a `pattern` error.
- A match that takes longer than `patternTimeoutMillis` is stopped and the value is a `pattern` error. The server logs a warning with the property name (never the value).

The limits are a safety net, not a reason to write expensive patterns: the clock is checked while the regex reads the text, so a match that is stuck between two reads is not interrupted. Avoid nested or repeated groups with open-ended quantifiers, and limit the text with `@Length`. Raise `maxPatternInputLength` only for fields that really need it.

## Configuration errors

An annotation on an incompatible type (for example `@Email` on an `Int`) or an invalid regex is **not ignored**: the application fails at startup with `KastApiConfigurationException`, which lists every problem found, each with its route. Also detected: `{param}` parameters without a property, duplicate routes and unsupported types in path or query.

With `KastApiConfig { validateOnStartup = false }` the consistency checks are skipped; problems that prevent building the route are still reported.

## Next step

[Errors](04-errors.md).
