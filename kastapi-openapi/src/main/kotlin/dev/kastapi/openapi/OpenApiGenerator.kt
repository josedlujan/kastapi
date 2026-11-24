package dev.kastapi.openapi

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val OPENAPI_VERSION = "3.1.0"
private const val JSON_MEDIA_TYPE = "application/json"
private const val RESPONSE_PREFIX = "#/components/responses/"

/** An error response that KastAPI itself can send, shared through `components/responses`. */
private enum class ErrorResponseKind(
    val status: String,
    val componentName: String,
    val description: String,
) {
    BAD_REQUEST("400", "BadRequest", "Bad Request: the request body is not valid JSON (malformed_json)"),
    PAYLOAD_TOO_LARGE(
        "413",
        "PayloadTooLarge",
        "Payload Too Large: the request body exceeds the maximum size (payload_too_large)",
    ),
    UNSUPPORTED_MEDIA_TYPE(
        "415",
        "UnsupportedMediaType",
        "Unsupported Media Type: the request body must be JSON encoded in UTF-8 (unsupported_media_type)",
    ),
    VALIDATION_ERROR(
        "422",
        "ValidationError",
        "Unprocessable Entity: the request has validation errors (validation_error)",
    ),
    INTERNAL_SERVER_ERROR(
        "500",
        "InternalServerError",
        "Internal Server Error: an unhandled exception (internal_error)",
    ),
}

/**
 * The errors KastAPI can send for [route]: the body errors when it reads a body, a validation error when it has input,
 * and an internal error always. Errors raised by the handler with `HttpError` are not known and not listed.
 */
private fun errorsOf(route: ApiRoute): List<ErrorResponseKind> =
    buildList {
        if (route.body != null) {
            add(ErrorResponseKind.BAD_REQUEST)
            add(ErrorResponseKind.PAYLOAD_TOO_LARGE)
            add(ErrorResponseKind.UNSUPPORTED_MEDIA_TYPE)
        }
        if (route.documentsValidationError) add(ErrorResponseKind.VALIDATION_ERROR)
        add(ErrorResponseKind.INTERNAL_SERVER_ERROR)
    }

/** Generates the OpenAPI 3.1 document from the registered routes. */
@InternalKastApi
public object OpenApiGenerator {
    /**
     * Builds the document.
     *
     * @param info data of the `info` block.
     * @param routes the application's routes, without the documentation's own.
     * @param errorResponse descriptor of the error format, used in the error responses.
     */
    public fun generate(
        info: ApiInfo,
        routes: List<ApiRoute>,
        errorResponse: SerialDescriptor,
    ): JsonObject {
        val schemas = SchemaGenerator()
        val operationIds = HashSet<String>()
        val usedErrors = HashSet<ErrorResponseKind>()
        val paths = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
        for (route in routes) {
            val operations = paths.getOrPut(route.path) { LinkedHashMap() }
            operations[route.method.lowercase()] = operation(route, schemas, operationIds, errorResponse, usedErrors)
        }

        val document = LinkedHashMap<String, JsonElement>()
        document["openapi"] = JsonPrimitive(OPENAPI_VERSION)
        document["info"] = infoObject(info)
        document["paths"] = JsonObject(paths.mapValues { JsonObject(it.value) })
        document["components"] = components(schemas, usedErrors, errorResponse)
        return JsonObject(document)
    }
}

/** `schemas`, plus the shared error `responses` that some operation uses, in a fixed order. */
private fun components(
    schemas: SchemaGenerator,
    usedErrors: Set<ErrorResponseKind>,
    errorResponse: SerialDescriptor,
): JsonObject {
    val entries = LinkedHashMap<String, JsonElement>()
    val responses = LinkedHashMap<String, JsonElement>()
    for (kind in ErrorResponseKind.entries.filter { it in usedErrors }) {
        val response = LinkedHashMap<String, JsonElement>()
        response["description"] = JsonPrimitive(kind.description)
        response["content"] = jsonContent(schemas.schemaOf(errorResponse))
        responses[kind.componentName] = JsonObject(response)
    }
    entries["schemas"] = JsonObject(LinkedHashMap(schemas.components))
    if (responses.isNotEmpty()) entries["responses"] = JsonObject(responses)
    return JsonObject(entries)
}

private fun infoObject(info: ApiInfo): JsonObject {
    val entries = LinkedHashMap<String, JsonElement>()
    entries["title"] = JsonPrimitive(info.title)
    entries["version"] = JsonPrimitive(info.version)
    info.description?.let { entries["description"] = JsonPrimitive(it) }
    return JsonObject(entries)
}

private fun operation(
    route: ApiRoute,
    schemas: SchemaGenerator,
    operationIds: MutableSet<String>,
    errorResponse: SerialDescriptor,
    usedErrors: MutableSet<ErrorResponseKind>,
): JsonObject {
    val entries = LinkedHashMap<String, JsonElement>()
    if (route.tags.isNotEmpty()) entries["tags"] = JsonArray(route.tags.map { JsonPrimitive(it) })
    route.summary?.let { entries["summary"] = JsonPrimitive(it) }
    route.description?.let { entries["description"] = JsonPrimitive(it) }
    entries["operationId"] = JsonPrimitive(uniqueOperationId(route, operationIds))
    if (route.parameters.isNotEmpty()) {
        entries["parameters"] =
            JsonArray(route.parameters.map { parameter(it, schemas) })
    }
    route.body?.let { entries["requestBody"] = requestBody(it, schemas) }
    entries["responses"] = responses(route, schemas, errorResponse, usedErrors)
    return JsonObject(entries)
}

/** `get_users_id` for `GET /users/{id}`; the root is `get_root` and a duplicate gets the `_2` suffix. */
private fun uniqueOperationId(
    route: ApiRoute,
    used: MutableSet<String>,
): String {
    val normalizedPath =
        route.path
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "root" }
    val base = "${route.method.lowercase()}_$normalizedPath"
    var candidate = base
    var suffix = 2
    while (!used.add(candidate)) candidate = "${base}_${suffix++}"
    return candidate
}

private fun parameter(
    parameter: ApiParameter,
    schemas: SchemaGenerator,
): JsonObject {
    val entries = LinkedHashMap<String, JsonElement>()
    entries["name"] = JsonPrimitive(parameter.name)
    entries["in"] = JsonPrimitive(parameter.location)
    entries["required"] = JsonPrimitive(parameter.required)
    entries["schema"] = schemas.schemaOfProperty(parameter.owner, parameter.index)
    return JsonObject(entries)
}

private fun requestBody(
    body: ApiBody,
    schemas: SchemaGenerator,
): JsonObject {
    val entries = LinkedHashMap<String, JsonElement>()
    entries["required"] = JsonPrimitive(hasRequiredProperty(body))
    entries["content"] = jsonContent(schemas.bodySchemaOf(body.descriptor, body.excluded))
    return JsonObject(entries)
}

/** `true` if the body has any property that has no default value and is not nullable. */
private fun hasRequiredProperty(body: ApiBody): Boolean =
    (0 until body.descriptor.elementsCount).any { index ->
        val name = body.descriptor.getElementName(index)
        val nullable = body.descriptor.getElementDescriptor(index).isNullable
        name !in body.excluded && !body.descriptor.isElementOptional(index) && !nullable
    }

private fun responses(
    route: ApiRoute,
    schemas: SchemaGenerator,
    errorResponse: SerialDescriptor,
    usedErrors: MutableSet<ErrorResponseKind>,
): JsonObject {
    val responses = LinkedHashMap<String, JsonElement>()
    val success = LinkedHashMap<String, JsonElement>()
    success["description"] = JsonPrimitive(route.statusDescription)
    route.output?.let { success["content"] = jsonContent(schemas.schemaOf(it)) }
    responses[route.status.toString()] = JsonObject(success)

    for (kind in errorsOf(route)) {
        // The declared success response wins if it shares the status with an error.
        if (kind.status in responses) continue
        // Registers the error schema in the same place as before, right after the operation's own schemas.
        schemas.schemaOf(errorResponse)
        responses[kind.status] = JsonObject(mapOf("\$ref" to JsonPrimitive(RESPONSE_PREFIX + kind.componentName)))
        usedErrors += kind
    }
    return JsonObject(responses)
}

private fun jsonContent(schema: JsonObject): JsonObject =
    JsonObject(mapOf(JSON_MEDIA_TYPE to JsonObject(mapOf("schema" to schema))))
