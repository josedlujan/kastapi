package dev.kastapi

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
data class JdText(
    val a: String,
)

@Serializable
data class JdNode(
    val value: String = "",
    val children: List<JdNode> = emptyList(),
)

/**
 * Deeply nested JSON must never take the server down.
 *
 * The depth limit is enforced by kotlinx.serialization's JSON parser (400 levels in 1.12). These tests do not pin
 * the exact number, which belongs to that library: they pin what KastAPI guarantees, that moderate nesting works,
 * that hostile nesting is answered with a `400` and that it never becomes a `5xx` or a stack overflow.
 */
class JsonDepthTest {
    private fun run(test: suspend HttpClient.() -> Unit) =
        testApplication {
            application {
                installKastApi {
                    post<JdText, JdText>("/text") { it }
                    post<JdNode, JdNode>("/node") { it }
                    get<NoInput, JdText>("/ping") { JdText("pong") }
                }
            }
            createClient { }.test()
        }

    private suspend fun HttpClient.postJson(
        path: String,
        body: String,
    ): HttpResponse =
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun nodes(depth: Int) = """{"children":[""".repeat(depth) + """{"value":"x"}""" + "]}".repeat(depth)

    private fun arrays(depth: Int) = """{"a":""" + "[".repeat(depth) + "]".repeat(depth) + "}"

    private suspend fun HttpResponse.errorCode() =
        Json
            .parseToJsonElement(bodyAsText())
            .jsonObject
            .getValue("error")
            .jsonPrimitive.content

    @Test
    fun `a recursive model with moderate nesting is accepted`() =
        run {
            assertEquals(HttpStatusCode.Created, postJson("/node", nodes(100)).status)
        }

    @Test
    fun `moderately nested arrays reach validation and report a type error`() =
        run {
            val response = postJson("/text", arrays(300))
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("validation_error", response.errorCode())
        }

    @Test
    fun `nesting beyond the parser limit is rejected as malformed JSON`() =
        run {
            val response = postJson("/text", arrays(5_000))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("malformed_json", response.errorCode())
        }

    @Test
    fun `a body of one million opening brackets never produces a server error`() =
        run {
            val response = postJson("/text", """{"a":""" + "[".repeat(1_000_000))
            assertTrue(response.status.value in 400..499, "expected a 4xx but got ${response.status}")
        }

    @Test
    fun `a recursive model nested far beyond the limit is rejected without a server error`() =
        run {
            val response = postJson("/node", nodes(100_000))
            assertTrue(response.status.value in 400..499, "expected a 4xx but got ${response.status}")
        }

    @Test
    fun `the server keeps working after hostile nesting`() =
        run {
            postJson("/text", arrays(400_000))
            postJson("/node", nodes(50_000))
            val after = get("/ping")
            assertEquals(HttpStatusCode.OK, after.status)
            assertFalse(after.bodyAsText().contains("error"))
        }
}
