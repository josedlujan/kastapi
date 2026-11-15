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

private const val SCHEMA_PREFIX = "#/components/schemas/"
private val OPERATIONS = setOf("get", "post", "put", "patch", "delete")
private val PATH_TEMPLATE = Regex("\\{([^}]+)}")

/** Texto de la propiedad [key] de este objeto JSON. */
fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

/** Objeto JSON de la propiedad [key]. */
fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

/**
 * Comprobación estructural mínima de un documento OpenAPI 3.1 (no hay un validador ligero para la JVM):
 * campos obligatorios, operaciones, parámetros, respuestas y que cada `$ref` apunte a un componente existente.
 */
fun assertValidOpenApi(document: JsonObject) {
    assertEquals("3.1.0", document.text("openapi"))
    val info = document.obj("info")
    assertTrue(info.text("title").isNotBlank(), "info.title no puede estar vacío")
    assertTrue(info.text("version").isNotBlank(), "info.version no puede estar vacío")

    val schemas = document.obj("components").obj("schemas")
    val operationIds = mutableListOf<String>()
    for ((path, item) in document.obj("paths")) {
        assertTrue(path.startsWith("/"), "la ruta $path debe empezar por /")
        for ((method, operation) in item.jsonObject) {
            assertTrue(method in OPERATIONS, "método desconocido $method en $path")
            val operationObject = operation.jsonObject
            operationIds += operationObject.text("operationId")
            assertParameters(path, operationObject)
            assertResponses(path, operationObject)
        }
    }
    assertEquals(operationIds.size, operationIds.toSet().size, "los operationId deben ser únicos: $operationIds")

    for (ref in collectRefs(document)) {
        assertTrue(ref.startsWith(SCHEMA_PREFIX), "referencia inesperada $ref")
        assertTrue(ref.removePrefix(SCHEMA_PREFIX) in schemas.keys, "la referencia $ref no existe en components")
    }
}

private fun assertParameters(
    path: String,
    operation: JsonObject,
) {
    val parameters = (operation["parameters"] as? JsonArray).orEmpty().map { it.jsonObject }
    for (parameter in parameters) {
        assertTrue(parameter.text("name").isNotBlank(), "parámetro sin nombre en $path")
        assertTrue(parameter.text("in") in setOf("path", "query"), "ubicación inválida en $path")
        assertTrue(parameter.containsKey("schema"), "el parámetro ${parameter.text("name")} no tiene schema")
        val required = parameter.getValue("required").jsonPrimitive.boolean
        if (parameter.text("in") == "path") assertTrue(required, "un parámetro de path debe ser required")
    }

    val declaredPathParameters = parameters.filter { it.text("in") == "path" }.map { it.text("name") }.toSet()
    val templateParameters = PATH_TEMPLATE.findAll(path).map { it.groupValues[1] }.toSet()
    assertEquals(templateParameters, declaredPathParameters, "los parámetros de path de $path no coinciden")
}

private fun assertResponses(
    path: String,
    operation: JsonObject,
) {
    val responses = operation.obj("responses")
    assertTrue(responses.isNotEmpty(), "la operación de $path no tiene respuestas")
    for ((status, response) in responses) {
        assertTrue(Regex("\\d{3}").matches(status), "status inválido $status en $path")
        assertTrue(response.jsonObject.text("description").isNotBlank(), "falta la descripción de $status en $path")
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
