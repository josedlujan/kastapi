package dev.kastapi.errors

/**
 * All KastAPI error texts (in English in v0.1) live here.
 *
 * Keeping them in one place makes later internationalization easier (see the roadmap, v0.3).
 */
internal object Messages {
    // Validation messages for API clients.
    const val REQUIRED = "is required"
    const val NOT_NULL = "must not be null"
    const val NOT_BLANK = "must not be blank"
    const val EMAIL = "must be a valid email address"
    const val PATTERN = "does not match the required format"
    const val UNKNOWN_FIELD = "unknown field"
    const val UNIT_CHARACTERS = "characters"
    const val UNIT_ITEMS = "items"
    const val EXPECTED_TEXT = "must be a string"
    const val EXPECTED_INTEGER = "must be an integer"
    const val EXPECTED_DECIMAL = "must be a decimal number"
    const val EXPECTED_BOOLEAN = "must be true or false"
    const val EXPECTED_OBJECT = "must be an object"
    const val EXPECTED_ARRAY = "must be an array"
    const val INTERNAL_ERROR = "Internal server error"
    const val MALFORMED_JSON = "The request body is not valid JSON"
    const val INVALID_BODY = "The request body could not be interpreted"

    /** [received] is the (shortened) `Content-Type` header, or `null` if the request has none. */
    fun contentTypeNotJson(received: String?): String =
        if (received == null) {
            "Content-Type must be application/json, but the header is missing"
        } else {
            "Content-Type must be application/json, but was $received"
        }

    fun contentTypeNotUtf8(charset: String): String =
        "Content-Type must be application/json encoded in UTF-8, but the charset was $charset"

    fun payloadTooLarge(maxBodyBytes: Long): String = "The request body exceeds the maximum of $maxBodyBytes bytes"

    fun expectedOneOf(values: Collection<String>): String = "must be one of: ${values.joinToString(", ")}"

    fun min(value: Long): String = "must be greater than or equal to $value"

    fun max(value: Long): String = "must be less than or equal to $value"

    /** [unit] is "characters" or "items". */
    fun length(
        min: Int,
        max: Int,
        unit: String,
    ): String =
        when {
            min > 0 && max < Int.MAX_VALUE -> "must have between $min and $max $unit"
            min > 0 -> "must have at least $min $unit"
            else -> "must have at most $max $unit"
        }

    fun notFound(
        method: String,
        path: String,
    ): String = "Route $method $path does not exist"

    fun methodNotAllowed(
        method: String,
        path: String,
    ): String = "Method $method is not allowed on $path"

    fun validationSummary(count: Int): String =
        if (count == 1) {
            "The request has 1 validation error"
        } else {
            "The request has $count validation errors"
        }

    // Configuration messages for whoever builds with KastAPI (reported at startup, see StartupValidator).
    // Each problem is shown after "METHOD /path: ", which the validator adds.
    const val RESERVED_ROUTE = "the route is reserved for the API documentation."
    const val APPLIES_TO_NUMBER = "numeric types"
    const val APPLIES_TO_TEXT = "String"
    const val APPLIES_TO_TEXT_OR_LIST = "String and List"

    fun configurationProblems(problems: List<String>): String {
        val header =
            if (problems.size == 1) {
                "KastAPI found 1 configuration problem:"
            } else {
                "KastAPI found ${problems.size} configuration problems:"
            }
        return problems.mapIndexed { index, problem -> " ${index + 1}. $problem" }.joinToString("\n", "$header\n")
    }

    fun inputMustBeClass(typeName: String): String = "input '$typeName' must be a @Serializable data class or NoInput."

    fun pathParameterWithoutProperty(
        name: String,
        typeName: String,
    ): String = "path parameter {$name} does not exist as a property of '$typeName'."

    fun pathPropertyNullable(name: String): String = "path property '$name' must not be nullable."

    fun pathPropertyHasDefault(name: String): String = "path property '$name' must not have a default value."

    fun duplicateRoute(first: String): String = "duplicate route: already declared as $first."

    fun incompatibleAnnotation(
        annotation: String,
        property: String,
        typeName: String,
        appliesTo: String,
    ): String = "annotation @$annotation on '$property' (type '$typeName') only applies to $appliesTo."

    fun invalidHttpErrorStatus(status: Int): String = "HttpError status must be between 400 and 599, but was $status"

    // Message for the server log (never sent to the client).
    fun unhandledException(
        method: String,
        path: String,
    ): String = "Unhandled exception in $method $path"

    /** Server-side log line when a `@Pattern` match is stopped by the time limit. */
    fun patternTimeout(
        property: String,
        millis: Long,
    ): String = "the @Pattern annotation on '$property' took longer than $millis ms and the value was rejected"

    fun invalidPattern(
        property: String,
        regex: String,
    ): String = "the @Pattern annotation on '$property' has an invalid regular expression: $regex"

    fun unsupportedParameter(
        property: String,
        typeName: String,
    ): String = "property '$property' (type '$typeName') cannot be bound from path or query."
}
