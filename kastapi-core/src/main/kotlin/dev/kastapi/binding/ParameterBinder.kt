package dev.kastapi.binding

import dev.kastapi.NoInput
import dev.kastapi.errors.ErrorCode
import dev.kastapi.errors.ErrorDetail
import dev.kastapi.errors.ErrorLocation
import dev.kastapi.errors.Messages
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.RoutingCall
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Result of building a route's input. */
internal sealed interface BindingResult {
    class Success(
        val input: Any?,
    ) : BindingResult

    class Failure(
        val errors: List<ErrorDetail>,
    ) : BindingResult

    /** The body is not valid JSON (400). */
    data object MalformedJson : BindingResult

    /** The body exceeds `maxBodyBytes` (413). */
    data object PayloadTooLarge : BindingResult

    /** The body is not JSON encoded in UTF-8 according to its `Content-Type` (415). */
    class UnsupportedMediaType(
        val message: String,
    ) : BindingResult
}

/**
 * Builds the route's input: assembles a `JsonObject` with the path, query and body values, validates it
 * against the descriptor and decodes it with the input's serializer (ADR-0002).
 * Collects all the errors, not just the first one.
 */
@OptIn(ExperimentalSerializationApi::class)
internal suspend fun RoutePlan.bind(
    call: RoutingCall,
    json: Json,
    maxBodyBytes: Long,
): BindingResult {
    if (noInput) return BindingResult.Success(NoInput)

    val urlValues = mutableMapOf<String, JsonElement>()
    val errors = mutableListOf<ErrorDetail>()

    for (param in params) {
        val raw =
            when (param.location) {
                ParamLocation.PATH -> call.pathParameters.getAll(param.name)
                ParamLocation.QUERY -> call.request.queryParameters.getAll(param.name)
            }

        if (raw.isNullOrEmpty()) {
            bindAbsent(param, urlValues, errors)
        } else {
            bindPresent(param, raw, urlValues, errors)
        }
    }

    val merged = mutableMapOf<String, JsonElement>()
    if (hasBody) {
        val mediaTypeProblem = mediaTypeProblem(call.request.headers[HttpHeaders.ContentType])
        val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        // A declared body with the wrong type is rejected without reading it.
        if (mediaTypeProblem != null && declared != null && declared > 0) {
            return BindingResult.UnsupportedMediaType(mediaTypeProblem)
        }
        val bytes = call.readBodyBytes(maxBodyBytes, declared)
        // An empty body is treated as `{}` whatever its type; anything else must be JSON in UTF-8.
        if (mediaTypeProblem != null && (bytes == null || bytes.isNotEmpty())) {
            return BindingResult.UnsupportedMediaType(mediaTypeProblem)
        }
        if (bytes == null) return BindingResult.PayloadTooLarge
        val body = parseBody(String(bytes, Charsets.UTF_8), json) ?: return BindingResult.MalformedJson
        val ignoreUnknownKeys = json.configuration.ignoreUnknownKeys
        errors += validateBody(serializer.descriptor, body, pathNames, ignoreUnknownKeys, rules)
        if (body is JsonObject) {
            // The path wins: properties that come from the URL are discarded from the body.
            body.filterKeys { it !in pathNames }.forEach { (key, value) -> merged[key] = value }
        }
        bodyNullables.forEach { merged.putIfAbsent(it, JsonNull) }
    }

    if (errors.isNotEmpty()) return BindingResult.Failure(errors)
    merged.putAll(urlValues)
    return decode(JsonObject(merged), json)
}

/**
 * Reads the body without ever loading more than [maxBodyBytes] bytes into memory; returns `null` if it exceeds that.
 * With a known `Content-Length` ([declared]) it is rejected before reading; without it (chunked) it reads up to
 * `maxBodyBytes + 1`.
 */
private suspend fun RoutingCall.readBodyBytes(
    maxBodyBytes: Long,
    declared: Long?,
): ByteArray? {
    if (declared != null && declared > maxBodyBytes) return null
    val bytes = receiveChannel().readBuffer(maxBodyBytes + 1).readByteArray()
    return if (bytes.size > maxBodyBytes) null else bytes
}

/** Longest `Content-Type` value echoed back in an error message. */
private const val MAX_ECHOED_HEADER = 80

/**
 * Why a request's [header] (its `Content-Type`, or `null` if it has none) is not acceptable for a JSON body, or `null`
 * if it is: `application/json` or any
 * structured JSON type such as `application/problem+json`, in any case, and with no `charset` or `charset=utf-8`.
 */
internal fun mediaTypeProblem(header: String?): String? {
    if (header == null) return Messages.contentTypeNotJson(null)
    val type =
        try {
            ContentType.parse(header)
        } catch (_: BadContentTypeFormatException) {
            return Messages.contentTypeNotJson(printable(header))
        }
    val isJson =
        type.contentType.equals("application", ignoreCase = true) &&
            (
                type.contentSubtype.equals("json", ignoreCase = true) ||
                    type.contentSubtype.endsWith("+json", ignoreCase = true)
            )
    if (!isJson) return Messages.contentTypeNotJson(printable(header))
    val charset = type.parameter("charset")?.trim()?.trim('"')
    if (charset != null &&
        !charset.equals("utf-8", ignoreCase = true)
    ) {
        return Messages.contentTypeNotUtf8(printable(charset))
    }
    return null
}

/** A request-controlled value made safe to echo: control characters removed and the length limited. */
private fun printable(value: String): String {
    val clean = value.filter { it >= ' ' && it != '\u007f' }
    return if (clean.length > MAX_ECHOED_HEADER) clean.take(MAX_ECHOED_HEADER) + "..." else clean
}

/** An empty body is treated as `{}`; returns `null` if it is not valid JSON. */
private fun parseBody(
    text: String,
    json: Json,
): JsonElement? =
    if (text.isBlank()) {
        JsonObject(emptyMap())
    } else {
        try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }
    }

private fun RoutePlan.decode(
    element: JsonObject,
    json: Json,
): BindingResult =
    try {
        BindingResult.Success(json.decodeFromJsonElement(serializer, element))
    } catch (_: SerializationException) {
        // Last resort: the validator did not detect the problem (uncommon types).
        val location = if (hasBody) ErrorLocation.BODY else ErrorLocation.QUERY
        BindingResult.Failure(listOf(ErrorDetail(location, "", ErrorCode.TYPE, Messages.INVALID_BODY)))
    }

private fun bindAbsent(
    param: ParamSpec,
    values: MutableMap<String, JsonElement>,
    errors: MutableList<ErrorDetail>,
) {
    // With a default value it is omitted: the decoder applies it.
    if (param.hasDefault) return

    if (param.nullable) {
        values[param.name] = JsonNull
    } else {
        errors += ErrorDetail(param.location.label, param.name, ErrorCode.REQUIRED, Messages.REQUIRED)
    }
}

private fun RoutePlan.bindPresent(
    param: ParamSpec,
    raw: List<String>,
    values: MutableMap<String, JsonElement>,
    errors: MutableList<ErrorDetail>,
) {
    if (!param.isList) {
        val element = convert(param, raw.first())
        if (element == null) {
            errors += typeError(param, param.name, raw.first())
        } else {
            values[param.name] = element
            checkRules(param, element, errors)
        }
        return
    }

    val converted = raw.map { convert(param, it) }
    if (converted.all { it != null }) {
        val array = JsonArray(converted.filterNotNull())
        values[param.name] = array
        checkRules(param, array, errors)
    } else {
        converted.forEachIndexed { index, element ->
            if (element == null) errors += typeError(param, "${param.name}[$index]", raw[index])
        }
    }
}

/** Applies the validation annotations to an already converted path or query value. */
private fun RoutePlan.checkRules(
    param: ParamSpec,
    value: JsonElement,
    errors: MutableList<ErrorDetail>,
) {
    for (rule in rules.rulesOf(serializer.descriptor, param.index)) {
        val violation = rule.check(value) ?: continue
        val detail =
            ErrorDetail(param.location.label, param.name, violation.code, violation.message, value as? JsonPrimitive)
        errors += detail
    }
}

private fun typeError(
    param: ParamSpec,
    field: String,
    text: String,
): ErrorDetail =
    ErrorDetail(param.location.label, field, ErrorCode.TYPE, expectedTypeMessage(param), JsonPrimitive(text))

private fun expectedTypeMessage(param: ParamSpec): String =
    when (param.scalar) {
        ScalarType.STRING -> Messages.EXPECTED_TEXT
        ScalarType.INT, ScalarType.LONG -> Messages.EXPECTED_INTEGER
        ScalarType.DOUBLE -> Messages.EXPECTED_DECIMAL
        ScalarType.BOOLEAN -> Messages.EXPECTED_BOOLEAN
        ScalarType.ENUM -> Messages.expectedOneOf(param.enumNames)
    }

/** Converts [text] to the type of [param]; returns `null` if it is not valid. */
private fun convert(
    param: ParamSpec,
    text: String,
): JsonElement? =
    when (param.scalar) {
        ScalarType.STRING -> JsonPrimitive(text)
        ScalarType.INT -> text.toIntOrNull()?.let { JsonPrimitive(it) }
        ScalarType.LONG -> text.toLongOrNull()?.let { JsonPrimitive(it) }
        ScalarType.DOUBLE -> text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { JsonPrimitive(it) }
        ScalarType.BOOLEAN -> text.toBooleanStrictOrNull()?.let { JsonPrimitive(it) }
        ScalarType.ENUM -> if (text in param.enumNames) JsonPrimitive(text) else null
    }
