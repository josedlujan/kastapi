package dev.kastapi.openapi

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val OPENAPI_VERSION = "3.1.0"
private const val JSON_MEDIA_TYPE = "application/json"
private const val VALIDATION_ERROR_STATUS = "422"
private const val VALIDATION_ERROR_DESCRIPTION = "Unprocessable Entity"

/** Generates the OpenAPI 3.1 document from the registered routes. */
@InternalKastApi
public object OpenApiGenerator {
    /**
     * Builds the document.
     *
     * @param info data of the `info` block.
     * @param routes the application's routes, without the documentation's own.
     * @param errorResponse descriptor of the error format, used in the 422 response.
     */
    public fun generate(
        info: ApiInfo,
        routes: List<ApiRoute>,
        errorResponse: SerialDescriptor,
    ): JsonObject {
        val schemas = SchemaGenerator()
        val operationIds = HashSet<String>()
        val paths = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
        for (route in routes) {
            val operations = paths.getOrPut(route.path) { LinkedHashMap() }
            operations[route.method.lowercase()] = operation(route, schemas, operationIds, errorResponse)
        }

        val document = LinkedHashMap<String, JsonElement>()
        document["openapi"] = JsonPrimitive(OPENAPI_VERSION)
        document["info"] = infoObject(info)
        document["paths"] = JsonObject(paths.mapValues { JsonObject(it.value) })
        document["components"] = JsonObject(mapOf("schemas" to JsonObject(LinkedHashMap(schemas.components))))
        return JsonObject(document)
    }
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
    entries["responses"] = responses(route, schemas, errorResponse)
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
): JsonObject {
    val responses = LinkedHashMap<String, JsonElement>()
    val success = LinkedHashMap<String, JsonElement>()
    success["description"] = JsonPrimitive(route.statusDescription)
    route.output?.let { success["content"] = jsonContent(schemas.schemaOf(it)) }
    responses[route.status.toString()] = JsonObject(success)

    if (route.documentsValidationError) {
        val error = LinkedHashMap<String, JsonElement>()
        error["description"] = JsonPrimitive(VALIDATION_ERROR_DESCRIPTION)
        error["content"] = jsonContent(schemas.schemaOf(errorResponse))
        responses.putIfAbsent(VALIDATION_ERROR_STATUS, JsonObject(error))
    }
    return JsonObject(responses)
}

private fun jsonContent(schema: JsonObject): JsonObject =
    JsonObject(mapOf(JSON_MEDIA_TYPE to JsonObject(mapOf("schema" to schema))))
