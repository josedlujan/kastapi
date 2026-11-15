package dev.kastapi

import dev.kastapi.errors.Messages
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private suspend fun HttpResponse.errorBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

/** SLF4J logger that only remembers the calls to `error(message, exception)`. */
private class CapturedLogs {
    val messages = mutableListOf<String>()
    val causes = mutableListOf<Throwable>()

    val logger: Logger =
        Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
            if (method.name == "error" && args != null) {
                (args.firstOrNull() as? String)?.let { messages += it }
                (args.lastOrNull() as? Throwable)?.let { causes += it }
            }
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as Logger
}

class ErrorHandlingTest {
    @Test
    fun `an unknown route responds 404 with the standard format`() =
        testApplication {
            application { installKastApi { get<NoInput, Health>("/health") { Health("ok") } } }

            val response = client.get("/nope")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("404", body.text("status"))
            assertEquals("not_found", body.text("error"))
            assertEquals("Route GET /nope does not exist", body.text("message"))
            assertEquals(0, body.getValue("details").jsonArray.size)
        }

    @Test
    fun `HttpError responds with its status and the http_error error`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(409, "already exists") }
                }
            }

            val response = client.get("/users")

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals(
                """{"status":409,"error":"http_error","message":"already exists","details":[]}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `HttpError with status 404 is still http_error and not not_found`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(404, "the user does not exist") }
                }
            }

            val response = client.get("/users")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("http_error", body.text("error"))
            assertEquals("the user does not exist", body.text("message"))
        }

    @Test
    fun `a generic exception responds 500 without internal details`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/boom") { throw IllegalStateException("secreto-interno") }
                }
            }

            val response = client.get("/boom")
            val raw = response.bodyAsText()
            val body = response.errorBody()

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("internal_error", body.text("error"))
            assertEquals("Internal server error", body.text("message"))
            assertFalse(raw.contains("secreto-interno"), "the body must not reveal the exception message")
            assertFalse(raw.contains("IllegalStateException"), "the body must not reveal the exception type")
            assertFalse(raw.contains("kastapi"), "the body must not reveal packages or a stack trace")
        }

    @Test
    fun `a generic exception is logged with its cause`() {
        val logs = CapturedLogs()
        testApplication {
            environment { log = logs.logger }
            application {
                installKastApi {
                    get<NoInput, Health>("/boom") { throw IllegalStateException("secreto-interno") }
                }
            }

            client.get("/boom")
        }

        assertEquals(1, logs.causes.size)
        assertEquals("secreto-interno", logs.causes.single().message)
        assertTrue(logs.messages.single().contains("GET /boom"), "the log must indicate method and path")
    }

    @Test
    fun `an HttpError is not logged as a server error`() {
        val logs = CapturedLogs()
        testApplication {
            environment { log = logs.logger }
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(409, "already exists") }
                }
            }

            client.get("/users")
        }

        assertTrue(logs.causes.isEmpty())
        assertTrue(logs.messages.isEmpty())
    }

    @Test
    fun `an application with its own StatusPages keeps its handlers`() =
        testApplication {
            application {
                install(StatusPages) {
                    exception<IllegalStateException> { call, _ ->
                        call.respondText("propio", status = HttpStatusCode.fromValue(418))
                    }
                }
                installKastApi {
                    get<NoInput, Health>("/boom") { throw IllegalStateException("x") }
                }
            }

            val response = client.get("/boom")

            assertEquals(418, response.status.value)
            assertEquals("propio", response.bodyAsText())
        }

    @Test
    fun `a method not allowed on an existing route responds 405 with the standard format`() =
        testApplication {
            application { installKastApi { get<NoInput, Health>("/health") { Health("ok") } } }

            val response = client.post("/health")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("405", body.text("status"))
            assertEquals("method_not_allowed", body.text("error"))
            assertEquals("Method POST is not allowed on /health", body.text("message"))
            assertEquals(0, body.getValue("details").jsonArray.size)
        }

    @Test
    fun `HttpError rejects a status outside the 400 to 599 range`() {
        assertFailsWith<IllegalArgumentException> { HttpError(200, "is not an error") }
        assertFailsWith<IllegalArgumentException> { HttpError(399, "is not an error") }
        assertFailsWith<IllegalArgumentException> { HttpError(600, "no existe") }
        assertEquals(400, HttpError(400, "limite inferior").status)
        assertEquals(599, HttpError(599, "limite superior").status)
        assertEquals("already exists", HttpError(409, "already exists").message)
    }

    @Test
    fun `error texts are centralized in Messages`() {
        val sources = mainSources().filterNot { it.invariantSeparatorsPath.endsWith("errors/Messages.kt") }
        val constants = messageConstants()
        val accented = Regex("\"[^\"\\n]*[áéíóúñÁÉÍÓÚÑ¿¡][^\"\\n]*\"")

        assertTrue(sources.isNotEmpty(), "no main sources were found")
        assertTrue(constants.isNotEmpty(), "no constants were found in Messages")
        for (file in sources) {
            val code = codeLines(file)
            for (text in constants) {
                assertFalse(code.contains("\"$text\""), "${file.name} repeats a Messages text: $text")
            }
            assertContentEquals(
                emptyList<String>(),
                accented.findAll(code).map { it.value }.toList(),
                "${file.name} has accented texts outside Messages",
            )
        }
    }

    private fun mainSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Code of [file] without comment or KDoc lines. */
    private fun codeLines(file: File): String =
        file
            .readLines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")
            }.joinToString("\n")

    private fun messageConstants(): List<String> =
        Messages::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(null) as? String
            }.filter { it.length >= 6 }
}
