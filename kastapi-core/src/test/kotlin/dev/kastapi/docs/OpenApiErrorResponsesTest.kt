package dev.kastapi.docs

import dev.kastapi.KastApi
import dev.kastapi.KastApiConfig
import dev.kastapi.NoInput
import dev.kastapi.get
import dev.kastapi.installKastApi
import dev.kastapi.post
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
data class ErBody(
    val name: String,
)

@Serializable
data class ErQuery(
    val limit: Int = 10,
    val mode: String = "ok",
)

@Serializable
data class ErOut(
    val ok: Boolean = true,
)

private const val BODY_ERRORS = "400,413,415,422,500"

/** The error responses in the OpenAPI document are exactly the ones KastAPI can send (reference §7). */
class OpenApiErrorResponsesTest {
    private fun KastApi.sampleRoutes() {
        post<ErBody, ErOut>("/body") { body -> if (body.name == "boom") error("boom") else ErOut() }
        get<ErQuery, ErOut>("/query") { query -> if (query.mode == "boom") error("boom") else ErOut() }
        get<NoInput, ErOut>("/plain") { ErOut() }
    }

    private fun run(
        config: KastApiConfig = KastApiConfig(maxBodyBytes = 64),
        routes: KastApi.() -> Unit = { sampleRoutes() },
        test: suspend HttpClient.(JsonObject) -> Unit,
    ) = testApplication {
        application { installKastApi(config, routes) }
        val document = Json.parseToJsonElement(client.get("/openapi.json").bodyAsText()).jsonObject
        client.test(document)
    }

    private fun JsonObject.codes(
        path: String,
        method: String,
    ): String =
        obj("paths")
            .obj(path)
            .obj(method)
            .obj("responses")
            .keys
            .joinToString(",")

    @Test
    fun `each kind of operation documents the errors it can produce`() =
        run { document ->
            assertEquals("201,$BODY_ERRORS", document.codes("/body", "post"))
            assertEquals("200,422,500", document.codes("/query", "get"))
            assertEquals("200,500", document.codes("/plain", "get"))
            assertValidOpenApi(document)
        }

    @Test
    fun `error responses are references to shared responses that use the error schema`() =
        run { document ->
            val operation = document.obj("paths").obj("/body").obj("post")
            for (status in BODY_ERRORS.split(",")) {
                val raw = operation.obj("responses").obj(status)
                assertTrue(raw.containsKey("\$ref"), "$status must be a reference")
                val schema =
                    document
                        .response(operation, status)
                        .obj("content")
                        .obj("application/json")
                        .obj("schema")
                assertEquals("#/components/schemas/ErrorResponse", schema.text("\$ref"), status)
            }
            assertEquals(
                setOf(
                    "BadRequest",
                    "PayloadTooLarge",
                    "UnsupportedMediaType",
                    "ValidationError",
                    "InternalServerError",
                ),
                document.obj("components").obj("responses").keys,
            )
        }

    @Test
    fun `each shared response names the error code it stands for`() =
        run { document ->
            val expected =
                mapOf(
                    "BadRequest" to "malformed_json",
                    "PayloadTooLarge" to "payload_too_large",
                    "UnsupportedMediaType" to "unsupported_media_type",
                    "ValidationError" to "validation_error",
                    "InternalServerError" to "internal_error",
                )
            val responses = document.obj("components").obj("responses")
            for ((name, code) in expected) {
                assertTrue(code in responses.obj(name).text("description"), "$name must mention $code")
            }
        }

    @Test
    fun `only the shared responses in use are listed`() {
        run(routes = { get<NoInput, ErOut>("/plain") { ErOut() } }) { document ->
            assertEquals(setOf("InternalServerError"), document.obj("components").obj("responses").keys)
        }
        run(routes = { get<ErQuery, ErOut>("/query") { ErOut() } }) { document ->
            assertEquals(
                setOf("ValidationError", "InternalServerError"),
                document.obj("components").obj("responses").keys,
            )
        }
        run(routes = { }) { document ->
            assertFalse(document.obj("components").containsKey("responses"), "no operations, no shared responses")
        }
    }

    @Test
    fun `a success status that matches an error code keeps the success response`() =
        run(routes = { post<ErBody, ErOut>("/accepted", status = 415) { ErOut() } }) { document ->
            val response =
                document
                    .obj("paths")
                    .obj("/accepted")
                    .obj("post")
                    .obj("responses")
                    .obj("415")
            assertFalse(response.containsKey("\$ref"), "the declared success response must win")
            assertTrue(
                response
                    .obj("content")
                    .obj("application/json")
                    .obj("schema")
                    .text("\$ref")
                    .endsWith("/ErOut"),
            )
        }

    @Test
    fun `every documented error is really sent and the undocumented body errors are not`() =
        run { document ->
            val documented =
                document
                    .obj("paths")
                    .obj("/body")
                    .obj("post")
                    .obj("responses")
                    .keys
            val sent =
                mapOf(
                    "400" to sendBody("application/json", "{"),
                    "413" to sendBody("application/json", """{"name":"${"x".repeat(200)}"}"""),
                    "415" to sendBody("text/plain", """{"name":"a"}"""),
                    "422" to sendBody("application/json", """{"name":1}"""),
                    "500" to sendBody("application/json", """{"name":"boom"}"""),
                )
            for ((status, actual) in sent) {
                assertTrue(status in documented, "$status is sent but not documented")
                assertEquals(status, actual, "the request built for $status got $actual")
            }

            val queryDocumented =
                document
                    .obj("paths")
                    .obj("/query")
                    .obj("get")
                    .obj("responses")
                    .keys
            assertEquals("422", get("/query?limit=x").status.value.toString())
            assertEquals("500", get("/query?mode=boom").status.value.toString())
            assertTrue("422" in queryDocumented && "500" in queryDocumented)
            // A GET does not read the body: none of the body errors can happen, and none is documented.
            val ignoredBody =
                get("/query") {
                    header(HttpHeaders.ContentType, "text/plain")
                    setBody("x".repeat(500))
                }
            assertEquals(200, ignoredBody.status.value)
            for (status in listOf("400", "413", "415")) assertFalse(status in queryDocumented, status)
        }

    private suspend fun HttpClient.sendBody(
        contentType: String,
        body: String,
    ): String =
        post("/body") {
            header(HttpHeaders.ContentType, contentType)
            setBody(body)
        }.status.value.toString()
}
