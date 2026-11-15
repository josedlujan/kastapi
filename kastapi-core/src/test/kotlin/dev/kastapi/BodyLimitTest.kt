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
                KastApiConfig(maxBodyBytes = max),
            ) { post<BlNote, BlNote>("/n", status = 200) { it } }
        }
        createClient { }.test()
    }

    @Test
    fun `el limite por defecto es 1 MiB`() {
        assertEquals(1_048_576L, KastApiConfig().maxBodyBytes)
    }

    @Test
    fun `un cuerpo mayor que el limite responde 413 payload_too_large con el formato estandar`() =
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
    fun `un cuerpo exactamente del tamano del limite se acepta`() =
        limited(json("x").length.toLong()) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("x"))
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `un byte por encima del limite se rechaza`() =
        limited(json("x").length.toLong() - 1) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("x"))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `el limite se aplica tambien sin Content-Length (chunked)`() =
        limited(100) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(ByteReadChannel(json("x".repeat(500)).toByteArray()))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `el limite cuenta bytes y no caracteres`() =
        limited(json("ñññ").toByteArray().size.toLong() - 1) {
            val response =
                post("/n") {
                    contentType(ContentType.Application.Json)
                    setBody(json("ñññ"))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `una ruta sin cuerpo no se ve afectada por el limite`() =
        testApplication {
            application {
                installKastApi(KastApiConfig(maxBodyBytes = 1)) { get<NoInput, BlNote>("/g") { BlNote("ok") } }
            }
            assertEquals(HttpStatusCode.OK, createClient { }.get("/g").status)
        }

    @Test
    fun `un limite no positivo es un error de configuracion`() {
        assertFailsWith<IllegalArgumentException> { KastApiConfig(maxBodyBytes = 0) }
        assertFailsWith<IllegalArgumentException> { KastApiConfig(maxBodyBytes = -5) }
        assertTrue(runCatching { KastApiConfig(maxBodyBytes = 1) }.isSuccess)
    }
}
