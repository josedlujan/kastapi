package dev.kastapi

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
data class BlNote(
    val text: String,
)

class BodyLimitTest {
    private fun json(text: String) = """{"text":"$text"}"""

    private fun limited(
        max: Long,
        test: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        application {
            installKastApi(
                KastApiConfig { maxBodyBytes = max },
            ) { post<BlNote, BlNote>("/n", status = 200) { it } }
        }
        createClient { }.test()
    }

    @Test
    fun `the default limit is 1 MiB`() {
        assertEquals(1_048_576L, KastApiConfig().maxBodyBytes)
    }

    @Test
    fun `a body larger than the limit responds 413 payload_too_large with the standard format`() =
        limited(100) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("x".repeat(200)))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("413", body.getValue("status").jsonPrimitive.content)
            assertEquals("payload_too_large", body.getValue("error").jsonPrimitive.content)
        }

    @Test
    fun `a body exactly the size of the limit is accepted`() =
        limited(json("x").length.toLong()) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("x"))
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `one byte over the limit is rejected`() =
        limited(json("x").length.toLong() - 1) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("x"))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `the limit also applies without Content-Length (chunked)`() =
        limited(100) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(ByteReadChannel(json("x".repeat(500)).toByteArray()))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `the limit counts bytes and not characters`() =
        limited(json("ñññ").toByteArray().size.toLong() - 1) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("ñññ"))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `a route without a body is not affected by the limit`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { maxBodyBytes = 1 }) { get<NoInput, BlNote>("/g") { BlNote("ok") } }
            }
            assertEquals(HttpStatusCode.OK, createClient { }.get("/g").status)
        }

    @Test
    fun `a non-positive limit is a configuration error`() {
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxBodyBytes = 0 } }
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxBodyBytes = -5 } }
        assertTrue(runCatching { KastApiConfig { maxBodyBytes = 1 } }.isSuccess)
    }
}
