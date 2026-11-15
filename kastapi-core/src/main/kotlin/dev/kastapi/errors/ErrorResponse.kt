package dev.kastapi.errors

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Detalle de un error de validación (referencia §8). */
@Serializable
internal data class ErrorDetail(
    val location: String,
    val field: String,
    val code: String,
    val message: String,
    val value: JsonElement? = null,
)

/** Formato único de error de la API (referencia §8). */
@Serializable
internal data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val details: List<ErrorDetail> = emptyList(),
)

/** Valores del campo `error`. */
internal object ErrorKind {
    const val VALIDATION = "validation_error"
    const val MALFORMED_JSON = "malformed_json"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val NOT_FOUND = "not_found"
    const val METHOD_NOT_ALLOWED = "method_not_allowed"
    const val HTTP_ERROR = "http_error"
    const val INTERNAL_ERROR = "internal_error"
}

/** Valores del campo `code` en los detalles. */
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

/** Valores del campo `location` en los detalles. */
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

/** Construye la respuesta 404 para una ruta que no existe. */
internal fun notFoundError(
    method: String,
    path: String,
): ErrorResponse =
    ErrorResponse(
        status = NOT_FOUND,
        error = ErrorKind.NOT_FOUND,
        message = Messages.notFound(method, path),
    )

/** Construye la respuesta 405 para un método no permitido en una ruta que sí existe. */
internal fun methodNotAllowedError(
    method: String,
    path: String,
): ErrorResponse =
    ErrorResponse(
        status = METHOD_NOT_ALLOWED,
        error = ErrorKind.METHOD_NOT_ALLOWED,
        message = Messages.methodNotAllowed(method, path),
    )

/** Construye la respuesta de un `HttpError` lanzado desde un handler. */
internal fun httpError(
    status: Int,
    message: String,
): ErrorResponse =
    ErrorResponse(
        status = status,
        error = ErrorKind.HTTP_ERROR,
        message = message,
    )

/** Construye la respuesta 500; no incluye ningún dato de la excepción. */
internal fun internalError(): ErrorResponse =
    ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        error = ErrorKind.INTERNAL_ERROR,
        message = Messages.INTERNAL_ERROR,
    )

/** Construye la respuesta 422 con todos los [details] encontrados. */
internal fun validationError(details: List<ErrorDetail>): ErrorResponse =
    ErrorResponse(
        status = UNPROCESSABLE_ENTITY,
        error = ErrorKind.VALIDATION,
        message = Messages.validationSummary(details.size),
        details = details,
    )

/** Construye la respuesta 400 para un cuerpo que no es JSON válido. */
internal fun malformedJsonError(): ErrorResponse =
    ErrorResponse(
        status = BAD_REQUEST,
        error = ErrorKind.MALFORMED_JSON,
        message = Messages.MALFORMED_JSON,
    )

/** Construye la respuesta 413 para un cuerpo que supera [maxBodyBytes]. */
internal fun payloadTooLargeError(maxBodyBytes: Long): ErrorResponse =
    ErrorResponse(
        status = PAYLOAD_TOO_LARGE,
        error = ErrorKind.PAYLOAD_TOO_LARGE,
        message = Messages.payloadTooLarge(maxBodyBytes),
    )
