package dev.kastapi.docs

import dev.kastapi.Health
import dev.kastapi.KastApi
import dev.kastapi.NoInput
import dev.kastapi.get
import dev.kastapi.installKastApi
import dev.kastapi.post
import dev.kastapi.put
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@Serializable
enum class Priority { LOW, HIGH }

@Serializable
data class FilterQuery(
    val tag: List<String> = emptyList(),
    val priority: Priority,
)

@Serializable
data class UpdateUser(
    val id: Long,
    val name: String,
)

@Serializable
data class OptionalBody(
    val note: String? = null,
    val count: Int = 0,
)

private val json = Json

/** Downloads the OpenAPI document of the test application. */
private suspend fun ApplicationTestBuilder.openApiDocument(): JsonObject =
    json.parseToJsonElement(client.get("/openapi.json").bodyAsText()).jsonObject

private fun JsonObject.operation(
    path: String,
    method: String,
): JsonObject = obj("paths").obj(path).obj(method)

private fun JsonObject.parameters(): List<JsonObject> = getValue("parameters").jsonArray.map { it.jsonObject }

private fun ApplicationTestBuilder.startWith(block: KastApi.() -> Unit) {
    application { installKastApi(block = block) }
}

class OpenApiDocumentTest {
    @Test
    fun `the reference example document matches the snapshot`() =
        testApplication {
            startWith { exampleApi() }

            val actual = openApiDocument()

            val resource = OpenApiDocumentTest::class.java.getResource("/openapi/spec-example.json")
            if (resource == null) fail("Missing snapshot openapi/spec-example.json. Got:\n$actual")
            assertEquals(json.parseToJsonElement(resource.readText()), actual, "Got:\n$actual")
        }

    @Test
    fun `the reference example document is a valid OpenAPI 3_1 structure`() =
        testApplication {
            startWith { exampleApi() }

            assertValidOpenApi(openApiDocument())
        }

    @Test
    fun `every operation with input documents the 422 response with the error schema`() =
        testApplication {
            startWith { exampleApi() }

            val document = openApiDocument()

            val operations =
                listOf("/users" to "post", "/users" to "get", "/users/{id}" to "get", "/users/{id}" to "delete")
            for ((path, method) in operations) {
                val response = document.response(document.operation(path, method), "422")
                val schema = response.obj("content").obj("application/json").obj("schema")
                assertEquals("#/components/schemas/ErrorResponse", schema.text("\$ref"), "$method $path")
            }
        }

    @Test
    fun `an operation without input documents no 422 and no parameters and no body`() =
        testApplication {
            startWith { get<NoInput, Health>("/health") { Health("ok") } }

            val operation = openApiDocument().operation("/health", "get")

            assertEquals(setOf("200", "500"), operation.obj("responses").keys)
            assertFalse(operation.containsKey("parameters"))
            assertFalse(operation.containsKey("requestBody"))
        }

    @Test
    fun `without calling info the default title and version are used`() =
        testApplication {
            startWith { get<NoInput, Health>("/health") { Health("ok") } }

            val info = openApiDocument().obj("info")

            assertEquals("KastAPI", info.text("title"))
            assertEquals("0.0.0", info.text("version"))
            assertFalse(info.containsKey("description"))
        }

    @Test
    fun `the last call to info wins`() =
        testApplication {
            startWith {
                info(title = "Primera", version = "1")
                info(title = "Segunda", version = "2", description = "Final")
            }

            val info = openApiDocument().obj("info")

            assertEquals("Segunda", info.text("title"))
            assertEquals("2", info.text("version"))
            assertEquals("Final", info.text("description"))
        }

    @Test
    fun `the operationId uses the method and the normalized path and is always unique`() =
        testApplication {
            startWith {
                get<NoInput, Health>("/") { Health("raiz") }
                group("/v1") { get<UserPath, Health>("/users/{id}") { Health("x") } }
                get<NoInput, Health>("/a-b") { Health("uno") }
                get<NoInput, Health>("/a_b") { Health("dos") }
            }

            val document = openApiDocument()

            assertEquals("get_root", document.operation("/", "get").text("operationId"))
            assertEquals("get_v1_users_id", document.operation("/v1/users/{id}", "get").text("operationId"))
            assertEquals("get_a_b", document.operation("/a-b", "get").text("operationId"))
            assertEquals("get_a_b_2", document.operation("/a_b", "get").text("operationId"))
            assertValidOpenApi(document)
        }

    @Test
    fun `query parameters state required and their schema`() =
        testApplication {
            startWith { get<FilterQuery, Health>("/items") { Health("x") } }

            val parameters = openApiDocument().operation("/items", "get").parameters().associateBy { it.text("name") }

            val tag = parameters.getValue("tag")
            assertEquals("query", tag.text("in"))
            assertFalse(tag.getValue("required").jsonPrimitive.boolean)
            assertEquals("array", tag.obj("schema").text("type"))
            val priority = parameters.getValue("priority")
            assertTrue(priority.getValue("required").jsonPrimitive.boolean)
            assertEquals(JsonArray(listOf(JsonPrimitive("LOW"), JsonPrimitive("HIGH"))), priority.obj("schema")["enum"])
        }

    @Test
    fun `an input with path and body properties uses a derived component without the path ones`() =
        testApplication {
            startWith { put<UpdateUser, Health>("/users/{id}") { Health("x") } }

            val document = openApiDocument()

            val operation = document.operation("/users/{id}", "put")
            assertEquals(listOf("id"), operation.parameters().map { it.text("name") })
            val body = operation.obj("requestBody")
            assertTrue(body.getValue("required").jsonPrimitive.boolean)
            val schema = body.obj("content").obj("application/json").obj("schema")
            assertEquals("#/components/schemas/UpdateUserBody", schema.text("\$ref"))
            val component = document.obj("components").obj("schemas").obj("UpdateUserBody")
            assertEquals(setOf("name"), component.obj("properties").keys)
            assertEquals(JsonArray(listOf(JsonPrimitive("name"))), component["required"])
            assertFalse(document.obj("components").obj("schemas").containsKey("UpdateUser"))
            assertValidOpenApi(document)
        }

    @Test
    fun `the body is not required if none of its properties is required`() =
        testApplication {
            startWith { post<OptionalBody, Health>("/notes") { Health("x") } }

            val body = openApiDocument().operation("/notes", "post").obj("requestBody")

            assertFalse(body.getValue("required").jsonPrimitive.boolean)
        }

    @Test
    fun `the response description is the standard phrase of the status`() =
        testApplication {
            startWith { post<NoInput, Health>("/jobs", status = 202) { Health("x") } }

            val response = openApiDocument().operation("/jobs", "post").obj("responses").obj("202")

            assertEquals("Accepted", response.text("description"))
        }

    @Test
    fun `the documentation routes do not appear in the document`() =
        testApplication {
            startWith { get<NoInput, Health>("/health") { Health("ok") } }

            val paths = openApiDocument().obj("paths").keys

            assertEquals(setOf("/health"), paths)
        }

    @Test
    fun `the document is generated once and is the same on every request`() =
        testApplication {
            startWith { exampleApi() }

            val first = client.get("/openapi.json").bodyAsText()
            val second = client.get("/openapi.json").bodyAsText()

            assertEquals(first, second)
        }

    @Test
    fun `components always exists even without routes`() =
        testApplication {
            startWith { }

            val document = openApiDocument()

            assertEquals(0, document.obj("paths").size)
            assertEquals(0, document.obj("components").obj("schemas").size)
            assertValidOpenApi(document)
        }
}
