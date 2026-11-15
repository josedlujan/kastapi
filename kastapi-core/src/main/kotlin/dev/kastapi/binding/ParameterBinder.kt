package dev.kastapi.binding

import dev.kastapi.NoInput
import dev.kastapi.errors.ErrorCode
import dev.kastapi.errors.ErrorDetail
import dev.kastapi.errors.ErrorLocation
import dev.kastapi.errors.Messages
import io.ktor.http.HttpHeaders
import io.ktor.server.request.contentCharset
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

/** Resultado de construir la entrada de una ruta. */
internal sealed interface BindingResult {
    class Success(
        val input: Any?,
    ) : BindingResult

    class Failure(
        val errors: List<ErrorDetail>,
    ) : BindingResult

    /** El cuerpo no es JSON válido (400). */
    data object MalformedJson : BindingResult

    /** El cuerpo supera `maxBodyBytes` (413). */
    data object PayloadTooLarge : BindingResult
}

/**
 * Construye la entrada de la ruta: arma un `JsonObject` con los valores de path, query y cuerpo, lo valida
 * contra el descriptor y lo decodifica con el serializador de la entrada (ADR-0002).
 * Recoge todos los errores, no solo el primero.
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
        val text = call.readBodyText(maxBodyBytes) ?: return BindingResult.PayloadTooLarge
        val body = parseBody(text, json) ?: return BindingResult.MalformedJson
        val ignoreUnknownKeys = json.configuration.ignoreUnknownKeys
        errors += validateBody(serializer.descriptor, body, pathNames, ignoreUnknownKeys, rules)
        if (body is JsonObject) {
            // El path gana: se descartan del cuerpo las propiedades que vienen de la URL.
            body.filterKeys { it !in pathNames }.forEach { (key, value) -> merged[key] = value }
        }
        bodyNullables.forEach { merged.putIfAbsent(it, JsonNull) }
    }

    if (errors.isNotEmpty()) return BindingResult.Failure(errors)
    merged.putAll(urlValues)
    return decode(JsonObject(merged), json)
}

/**
 * Lee el cuerpo como texto sin cargar nunca más de [maxBodyBytes] bytes en memoria; devuelve `null` si lo supera.
 * Con `Content-Length` conocido se rechaza antes de leer; sin él (chunked) se lee hasta `maxBodyBytes + 1`.
 */
private suspend fun RoutingCall.readBodyText(maxBodyBytes: Long): String? {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > maxBodyBytes) return null
    val bytes = receiveChannel().readBuffer(maxBodyBytes + 1).readByteArray()
    if (bytes.size > maxBodyBytes) return null
    return String(bytes, request.contentCharset() ?: Charsets.UTF_8)
}

/** Un cuerpo vacío se trata como `{}`; devuelve `null` si no es JSON válido. */
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
        // Último recurso: el validador no detectó el problema (tipos poco comunes).
        val location = if (hasBody) ErrorLocation.BODY else ErrorLocation.QUERY
        BindingResult.Failure(listOf(ErrorDetail(location, "", ErrorCode.TYPE, Messages.INVALID_BODY)))
    }

private fun bindAbsent(
    param: ParamSpec,
    values: MutableMap<String, JsonElement>,
    errors: MutableList<ErrorDetail>,
) {
    // Con valor por defecto se omite: lo aplica el decodificador.
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

/** Aplica las anotaciones de validación a un valor de path o query ya convertido. */
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

/** Convierte [text] al tipo de [param]; devuelve `null` si no es válido. */
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
