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

/** Logger de SLF4J que solo recuerda las llamadas a `error(mensaje, excepción)`. */
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
    fun `una ruta inexistente responde 404 con el formato estandar`() =
        testApplication {
            application { installKastApi { get<NoInput, Health>("/health") { Health("ok") } } }

            val response = client.get("/nope")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("404", body.text("status"))
            assertEquals("not_found", body.text("error"))
            assertEquals("La ruta GET /nope no existe", body.text("message"))
            assertEquals(0, body.getValue("details").jsonArray.size)
        }

    @Test
    fun `HttpError responde con su status y el error http_error`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(409, "ya existe") }
                }
            }

            val response = client.get("/users")

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals(
                """{"status":409,"error":"http_error","message":"ya existe","details":[]}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `HttpError con status 404 sigue siendo http_error y no not_found`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(404, "el usuario no existe") }
                }
            }

            val response = client.get("/users")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("http_error", body.text("error"))
            assertEquals("el usuario no existe", body.text("message"))
        }

    @Test
    fun `una excepcion generica responde 500 sin detalles internos`() =
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
            assertEquals("Error interno del servidor", body.text("message"))
            assertFalse(raw.contains("secreto-interno"), "el cuerpo no debe revelar el mensaje de la excepción")
            assertFalse(raw.contains("IllegalStateException"), "el cuerpo no debe revelar el tipo de la excepción")
            assertFalse(raw.contains("kastapi"), "el cuerpo no debe revelar paquetes ni stack trace")
        }

    @Test
    fun `una excepcion generica se registra en el log con su causa`() {
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
        assertTrue(logs.messages.single().contains("GET /boom"), "el log debe indicar método y ruta")
    }

    @Test
    fun `un HttpError no se registra como error del servidor`() {
        val logs = CapturedLogs()
        testApplication {
            environment { log = logs.logger }
            application {
                installKastApi {
                    get<NoInput, Health>("/users") { throw HttpError(409, "ya existe") }
                }
            }

            client.get("/users")
        }

        assertTrue(logs.causes.isEmpty())
        assertTrue(logs.messages.isEmpty())
    }

    @Test
    fun `una aplicacion con su propio StatusPages conserva sus manejadores`() =
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
    fun `un metodo no permitido sobre una ruta existente responde 405 con el formato estandar`() =
        testApplication {
            application { installKastApi { get<NoInput, Health>("/health") { Health("ok") } } }

            val response = client.post("/health")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("405", body.text("status"))
            assertEquals("method_not_allowed", body.text("error"))
            assertEquals("El método POST no está permitido en /health", body.text("message"))
            assertEquals(0, body.getValue("details").jsonArray.size)
        }

    @Test
    fun `HttpError rechaza un status fuera del rango 400 a 599`() {
        assertFailsWith<IllegalArgumentException> { HttpError(200, "no es un error") }
        assertFailsWith<IllegalArgumentException> { HttpError(399, "no es un error") }
        assertFailsWith<IllegalArgumentException> { HttpError(600, "no existe") }
        assertEquals(400, HttpError(400, "limite inferior").status)
        assertEquals(599, HttpError(599, "limite superior").status)
        assertEquals("ya existe", HttpError(409, "ya existe").message)
    }

    @Test
    fun `los textos de error estan centralizados en Messages`() {
        val sources = mainSources().filterNot { it.invariantSeparatorsPath.endsWith("errors/Messages.kt") }
        val constants = messageConstants()
        val accented = Regex("\"[^\"\\n]*[áéíóúñÁÉÍÓÚÑ¿¡][^\"\\n]*\"")

        assertTrue(sources.isNotEmpty(), "no se encontraron fuentes de main")
        assertTrue(constants.isNotEmpty(), "no se encontraron constantes en Messages")
        for (file in sources) {
            val code = codeLines(file)
            for (text in constants) {
                assertFalse(code.contains("\"$text\""), "${file.name} repite el texto de Messages: $text")
            }
            assertContentEquals(
                emptyList<String>(),
                accented.findAll(code).map { it.value }.toList(),
                "${file.name} tiene textos con acentos fuera de Messages",
            )
        }
    }

    private fun mainSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Código de [file] sin líneas de comentario ni KDoc. */
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
