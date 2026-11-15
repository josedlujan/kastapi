package dev.kastapi.errors

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Detail of a validation error (reference §8). */
@Serializable
internal data class ErrorDetail(
    val location: String,
    val field: String,
    val code: String,
    val message: String,
    val value: JsonElement? = null,
)

/**
 * The API's single error format (reference §8).
 *
 * [details] is only serialized when it is not empty, whatever the application's `Json` settings are
 * (`encodeDefaults` would otherwise write an empty list in every error).
 */
@Serializable
internal data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val details: List<ErrorDetail> = emptyList(),
)

/** Values of the `error` field. */
internal object ErrorKind {
    const val VALIDATION = "validation_error"
    const val MALFORMED_JSON = "malformed_json"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val NOT_FOUND = "not_found"
    const val METHOD_NOT_ALLOWED = "method_not_allowed"
    const val HTTP_ERROR = "http_error"
    const val INTERNAL_ERROR = "internal_error"
}

/** Values of the `code` field in the details. */
internal object ErrorCode {
    const val REQUIRED = "required"
    const val TYPE = "type"
    const val UNKNOWN_FIELD = "unknown_field"
    const val MIN = "min"
    const val MAX = "max"
    const val LENGTH = "length"
    const val NOT_BLANK = "not_blank"
    const val EMAIL = "email"
    const val PATTERN = "pattern"
}

/** Values of the `location` field in the details. */
internal object ErrorLocation {
    const val PATH = "path"
    const val QUERY = "query"
    const val BODY = "body"
}

private const val BAD_REQUEST = 400
private const val PAYLOAD_TOO_LARGE = 413
private const val NOT_FOUND = 404
private const val METHOD_NOT_ALLOWED = 405
private const val UNPROCESSABLE_ENTITY = 422
private const val INTERNAL_SERVER_ERROR = 500

/** Builds the 404 response for a route that does not exist. */
internal fun notFoundError(
    method: String,
    path: String,
): ErrorResponse =
    ErrorResponse(
        status = NOT_FOUND,
        error = ErrorKind.NOT_FOUND,
        message = Messages.notFound(method, path),
    )

/** Builds the 405 response for a method not allowed on a route that does exist. */
internal fun methodNotAllowedError(
    method: String,
    path: String,
): ErrorResponse =
    ErrorResponse(
        status = METHOD_NOT_ALLOWED,
        error = ErrorKind.METHOD_NOT_ALLOWED,
        message = Messages.methodNotAllowed(method, path),
    )

/** Builds the response for an `HttpError` thrown from a handler. */
internal fun httpError(
    status: Int,
    message: String,
): ErrorResponse =
    ErrorResponse(
        status = status,
        error = ErrorKind.HTTP_ERROR,
        message = message,
    )

/** Builds the 500 response; it includes no exception data. */
internal fun internalError(): ErrorResponse =
    ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        error = ErrorKind.INTERNAL_ERROR,
        message = Messages.INTERNAL_ERROR,
    )

/** Builds the 422 response with all the [details] found. */
internal fun validationError(details: List<ErrorDetail>): ErrorResponse =
    ErrorResponse(
        status = UNPROCESSABLE_ENTITY,
        error = ErrorKind.VALIDATION,
        message = Messages.validationSummary(details.size),
        details = details,
    )

/** Builds the 400 response for a body that is not valid JSON. */
internal fun malformedJsonError(): ErrorResponse =
    ErrorResponse(
        status = BAD_REQUEST,
        error = ErrorKind.MALFORMED_JSON,
        message = Messages.MALFORMED_JSON,
    )

/** Builds the 413 response for a body that exceeds [maxBodyBytes]. */
internal fun payloadTooLargeError(maxBodyBytes: Long): ErrorResponse =
    ErrorResponse(
        status = PAYLOAD_TOO_LARGE,
        error = ErrorKind.PAYLOAD_TOO_LARGE,
        message = Messages.payloadTooLarge(maxBodyBytes),
    )
