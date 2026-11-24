package dev.kastapi.docs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private const val SCHEMA_PREFIX = "#/components/schemas/"
private const val RESPONSE_PREFIX = "#/components/responses/"
private val OPERATIONS = setOf("get", "post", "put", "patch", "delete")
private val PATH_TEMPLATE = Regex("\\{([^}]+)}")

/** Text of the [key] property of this JSON object. */
fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

/** JSON object of the [key] property. */
fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

/** The response object for [status] in [operation], following a `$ref` to `components/responses` if there is one. */
fun JsonObject.response(
    operation: JsonObject,
    status: String,
): JsonObject {
    val response = operation.obj("responses").obj(status)
    val ref = (response["\$ref"] as? JsonPrimitive)?.content ?: return response
    return obj("components").obj("responses").obj(ref.removePrefix(RESPONSE_PREFIX))
}

/**
 * Minimal structural check of an OpenAPI 3.1 document (there is no lightweight validator for the JVM):
 * required fields, operations, parameters, responses and that each `$ref` points to an existing component.
 */
fun assertValidOpenApi(document: JsonObject) {
    assertEquals("3.1.0", document.text("openapi"))
    val info = document.obj("info")
    assertTrue(info.text("title").isNotBlank(), "info.title must not be blank")
    assertTrue(info.text("version").isNotBlank(), "info.version must not be blank")

    val schemas = document.obj("components").obj("schemas")
    val operationIds = mutableListOf<String>()
    for ((path, item) in document.obj("paths")) {
        assertTrue(path.startsWith("/"), "the path $path must start with /")
        for ((method, operation) in item.jsonObject) {
            assertTrue(method in OPERATIONS, "unknown method $method in $path")
            val operationObject = operation.jsonObject
            operationIds += operationObject.text("operationId")
            assertParameters(path, operationObject)
            assertResponses(path, operationObject, document)
        }
    }
    assertEquals(operationIds.size, operationIds.toSet().size, "operationIds must be unique: $operationIds")

    val responses = (document.obj("components")["responses"] as? JsonObject).orEmptyObject()
    for (ref in collectRefs(document)) {
        when {
            ref.startsWith(SCHEMA_PREFIX) -> {
                assertTrue(
                    ref.removePrefix(SCHEMA_PREFIX) in schemas.keys,
                    "the reference $ref does not exist in components",
                )
            }

            ref.startsWith(RESPONSE_PREFIX) -> {
                assertTrue(
                    ref.removePrefix(RESPONSE_PREFIX) in responses.keys,
                    "the reference $ref does not exist in components",
                )
            }

            else -> {
                fail("unexpected reference $ref")
            }
        }
    }
    val used =
        collectRefs(document.obj("paths"))
            .filter {
                it.startsWith(RESPONSE_PREFIX)
            }.map { it.removePrefix(RESPONSE_PREFIX) }
            .toSet()
    assertEquals(used, responses.keys, "components.responses must hold exactly the responses that operations use")
}

private fun assertParameters(
    path: String,
    operation: JsonObject,
) {
    val parameters = (operation["parameters"] as? JsonArray).orEmpty().map { it.jsonObject }
    for (parameter in parameters) {
        assertTrue(parameter.text("name").isNotBlank(), "parameter without a name in $path")
        assertTrue(parameter.text("in") in setOf("path", "query"), "invalid location in $path")
        assertTrue(parameter.containsKey("schema"), "the parameter ${parameter.text("name")} has no schema")
        val required = parameter.getValue("required").jsonPrimitive.boolean
        if (parameter.text("in") == "path") assertTrue(required, "a path parameter must be required")
    }

    val declaredPathParameters = parameters.filter { it.text("in") == "path" }.map { it.text("name") }.toSet()
    val templateParameters = PATH_TEMPLATE.findAll(path).map { it.groupValues[1] }.toSet()
    assertEquals(templateParameters, declaredPathParameters, "the path parameters of $path do not match")
}

private fun assertResponses(
    path: String,
    operation: JsonObject,
    document: JsonObject,
) {
    val responses = operation.obj("responses")
    assertTrue(responses.isNotEmpty(), "the operation of $path has no responses")
    for (status in responses.keys) {
        assertTrue(Regex("\\d{3}").matches(status), "invalid status $status in $path")
        val response = document.response(operation, status)
        assertTrue(response.text("description").isNotBlank(), "missing description for $status in $path")
    }
}

private fun collectRefs(element: JsonElement): List<String> =
    when (element) {
        is JsonObject -> element.flatMap { (key, value) -> refsOf(key, value) }
        is JsonArray -> element.flatMap { collectRefs(it) }
        else -> emptyList()
    }

private fun refsOf(
    key: String,
    value: JsonElement,
): List<String> = if (key == "\$ref" && value is JsonPrimitive) listOf(value.content) else collectRefs(value)

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())
