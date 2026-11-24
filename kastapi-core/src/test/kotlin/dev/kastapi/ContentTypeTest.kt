package dev.kastapi

import dev.kastapi.binding.mediaTypeProblem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
data class CtPayload(
    val s: String = "",
)

/** Routes that read a body only accept JSON, encoded in UTF-8 (reference §8). */
class ContentTypeTest {
    private fun run(
        maxBodyBytes: Long = 1_048_576,
        test: suspend HttpClient.() -> Unit,
    ) = testApplication {
        application {
            installKastApi(KastApiConfig { this.maxBodyBytes = maxBodyBytes }) {
                post<CtPayload, CtPayload>("/p") { it }
                post<NoInput, CtPayload>("/no-input") { CtPayload("no body read") }
                get<NoInput, CtPayload>("/g") { CtPayload("get") }
            }
        }
        createClient { }.test()
    }

    private suspend fun HttpClient.send(
        contentType: String?,
        body: ByteArray = """{"s":"x"}""".toByteArray(),
        path: String = "/p",
    ): HttpResponse =
        post(path) {
            if (contentType != null) {
                header(HttpHeaders.ContentType, contentType)
                setBody(body)
            } else {
                // Ktor adds application/octet-stream to a plain ByteArray: this content really has no type.
                setBody(
                    object : OutgoingContent.ByteArrayContent() {
                        override val contentType: ContentType? = null

                        override fun bytes(): ByteArray = body
                    },
                )
            }
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.assertUnsupported(
        messagePart: String? = null,
        label: String = "",
    ) {
        assertEquals(HttpStatusCode.UnsupportedMediaType, status, label)
        val body = json()
        assertEquals("415", body.getValue("status").jsonPrimitive.content, label)
        assertEquals("unsupported_media_type", body.getValue("error").jsonPrimitive.content, label)
        assertFalse(body.containsKey("details"), "a 415 must not carry details ($label)")
        if (messagePart != null) {
            val message = body.getValue("message").jsonPrimitive.content
            assertTrue(messagePart in message, "message '$message' must contain '$messagePart' ($label)")
        }
    }

    @Test
    fun `JSON media types are accepted whatever their case or structured suffix`() =
        run {
            val accepted =
                listOf(
                    "application/json",
                    "Application/JSON",
                    "application/json; charset=utf-8",
                    "application/json; charset=UTF-8",
                    "application/json;charset=\"utf-8\"",
                    "application/vnd.api+json",
                    "application/json-patch+json",
                    "application/problem+JSON; charset=utf-8",
                )
            for (type in accepted) {
                assertEquals(HttpStatusCode.Created, send(type).status, type)
            }
        }

    @Test
    fun `other media types get 415 with the standard error format`() =
        run {
            val rejected =
                listOf(
                    "text/plain",
                    "text/json",
                    "application/x-www-form-urlencoded",
                    "application/xml",
                    "application/jsonx",
                    "application/x-json",
                    "multipart/form-data; boundary=x",
                    "*/*",
                )
            for (type in rejected) {
                send(type).assertUnsupported("application/json", type)
            }
        }

    @Test
    fun `a body without a Content-Type header gets 415`() =
        run {
            send(contentType = null).assertUnsupported("missing")
        }

    @Test
    fun `a malformed Content-Type header is a problem`() {
        // Ktor's own client refuses to send these, so the check is exercised directly.
        for (type in listOf(
            "application",
            "///",
            "application/",
            "/json",
            ";charset=utf-8",
            "",
            "application/json/extra",
        )) {
            assertTrue(mediaTypeProblem(type) != null, "'$type' must not be accepted")
        }
    }

    @Test
    fun `the media type check returns no problem for JSON`() {
        for (type in listOf("application/json", "APPLICATION/JSON; CHARSET=UTF-8", "application/ld+json")) {
            assertEquals(null, mediaTypeProblem(type), type)
        }
    }

    @Test
    fun `a charset other than UTF-8 gets 415`() =
        run {
            for (type in listOf(
                "application/json; charset=iso-8859-1",
                "application/json; charset=utf-16",
                "application/json; charset=bogus",
            )) {
                send(type).assertUnsupported("UTF-8", type)
            }
        }

    @Test
    fun `an empty body is accepted with any or no Content-Type`() =
        run {
            for (type in listOf(null, "text/plain", "application/json")) {
                val response = send(type, body = ByteArray(0))
                assertEquals(HttpStatusCode.Created, response.status, "type=$type")
            }
        }

    @Test
    fun `text is always decoded as UTF-8`() =
        run {
            val body = """{"s":"ñandú ✓"}""".toByteArray(Charsets.UTF_8)
            for (type in listOf("application/json", "application/json; charset=utf-8")) {
                val response = send(type, body)
                assertEquals(HttpStatusCode.Created, response.status, type)
                assertEquals(
                    "ñandú ✓",
                    response
                        .json()
                        .getValue("s")
                        .jsonPrimitive.content,
                    type,
                )
            }
        }

    @Test
    fun `a route without input does not look at the body or its type`() =
        run {
            assertEquals(HttpStatusCode.Created, send("text/plain", path = "/no-input").status)
            assertEquals(HttpStatusCode.OK, get("/g").status)
        }

    @Test
    fun `the media type is checked before the size`() =
        run(maxBodyBytes = 100) {
            val oversized = ByteArray(10_000) { 'a'.code.toByte() }
            send("text/plain", oversized).assertUnsupported()
            assertEquals(HttpStatusCode.PayloadTooLarge, send("application/json", oversized).status)
        }

    @Test
    fun `a chunked body with the wrong type gets 415`() =
        run {
            val response =
                post("/p") {
                    header(HttpHeaders.ContentType, "text/plain")
                    setBody(ByteReadChannel("""{"s":"x"}""".toByteArray()))
                }
            response.assertUnsupported()
        }

    @Test
    fun `a chunked body with the right type is accepted`() =
        run {
            val response =
                post("/p") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(ByteReadChannel("""{"s":"x"}""".toByteArray()))
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `the message keeps a hostile header value short`() =
        run {
            val response = send("text/" + "a".repeat(5_000))
            response.assertUnsupported()
            val message =
                response
                    .json()
                    .getValue("message")
                    .jsonPrimitive.content
            assertTrue(message.length < 300, "the message has ${message.length} characters")
        }
}
