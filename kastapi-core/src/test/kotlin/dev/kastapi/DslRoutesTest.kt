package dev.kastapi

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.path
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Health(
    val status: String,
)

@Serializable
data class Created(
    val id: Long,
)

class DslRoutesTest {
    @Test
    fun `get con NoInput responde 200 y el JSON serializado`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/health") { Health("ok") }
                }
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun `post responde 201 por defecto`() =
        testApplication {
            application {
                installKastApi {
                    post<NoInput, Created>("/items") { Created(1) }
                }
            }

            val response = client.post("/items")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"id":1}""", response.bodyAsText())
        }

    @Test
    fun `post respeta status 202 cuando se indica`() =
        testApplication {
            application {
                installKastApi {
                    post<NoInput, Created>("/jobs", status = 202) { Created(7) }
                }
            }

            val response = client.post("/jobs")

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals("""{"id":7}""", response.bodyAsText())
        }

    @Test
    fun `delete con Unit y status 204 responde sin cuerpo`() =
        testApplication {
            application {
                installKastApi {
                    delete<NoInput, Unit>("/items", status = 204) { }
                }
            }

            val response = client.delete("/items")

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("", response.bodyAsText())
        }

    @Test
    fun `group monta las rutas bajo el prefijo`() =
        testApplication {
            application {
                installKastApi {
                    group("/v1") {
                        get<NoInput, Health>("/health") { Health("ok") }
                    }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/v1/health").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/health").status)
        }

    @Test
    fun `KastCall call da acceso al ApplicationCall`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/quien") { Health(call.request.path()) }
                }
            }

            assertEquals("""{"status":"/quien"}""", client.get("/quien").bodyAsText())
        }

    @Test
    fun `put y patch responden 200 por defecto`() =
        testApplication {
            application {
                installKastApi {
                    put<NoInput, Health>("/recurso") { Health("put") }
                    patch<NoInput, Health>("/recurso") { Health("patch") }
                }
            }

            val put = client.put("/recurso")
            val patch = client.patch("/recurso")

            assertEquals(HttpStatusCode.OK, put.status)
            assertEquals("""{"status":"put"}""", put.bodyAsText())
            assertEquals(HttpStatusCode.OK, patch.status)
            assertEquals("""{"status":"patch"}""", patch.bodyAsText())
        }

    @Test
    fun `header devuelve el valor o null si falta`() =
        testApplication {
            application {
                installKastApi {
                    get<NoInput, Health>("/eco") { Health(header("X-Test") ?: "ausente") }
                }
            }

            val conHeader = client.get("/eco") { header("X-Test", "hola") }
            val sinHeader = client.get("/eco")

            assertEquals("""{"status":"hola"}""", conHeader.bodyAsText())
            assertEquals("""{"status":"ausente"}""", sinHeader.bodyAsText())
        }
}
