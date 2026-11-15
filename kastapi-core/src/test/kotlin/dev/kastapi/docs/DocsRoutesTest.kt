package dev.kastapi.docs

import dev.kastapi.Health
import dev.kastapi.KastApi
import dev.kastapi.KastApiConfig
import dev.kastapi.KastApiConfigurationException
import dev.kastapi.NoInput
import dev.kastapi.get
import dev.kastapi.installKastApi
import dev.kastapi.openapi.swaggerUiHtml
import dev.kastapi.post
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun assertStartupFails(
    config: KastApiConfig,
    block: KastApi.() -> Unit,
): KastApiConfigurationException {
    var failure: KastApiConfigurationException? = null
    testApplication {
        application {
            failure = assertFailsWith<KastApiConfigurationException> { installKastApi(config, block) }
        }

        // Fuerza el arranque de la aplicación para que se ejecute el bloque anterior.
        client.get("/")
    }
    return checkNotNull(failure)
}

class DocsRoutesTest {
    @Test
    fun `GET docs devuelve el HTML de Swagger UI que referencia el esquema`() =
        testApplication {
            application { installKastApi { exampleApi() } }

            val response = client.get("/docs")
            val html = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
            assertTrue(html.contains("/openapi.json"), "el HTML debe referenciar /openapi.json")
            assertTrue(html.contains("swagger-ui-dist@5.34.1"), "la versión de Swagger UI debe estar fijada")
            assertTrue(html.contains("integrity=\"sha384-"), "los recursos del CDN deben llevar integrity")
        }

    @Test
    fun `GET openapi json se sirve como application json`() =
        testApplication {
            application { installKastApi { exampleApi() } }

            val response = client.get("/openapi.json")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("3.1.0", Json.parseToJsonElement(response.bodyAsText()).jsonObject.text("openapi"))
        }

    @Test
    fun `las rutas configuradas sustituyen a las de por defecto y el HTML apunta al esquema configurado`() =
        testApplication {
            val config = KastApiConfig(docsPath = "/api/docs", openApiPath = "/api/schema.json")
            application { installKastApi(config) { exampleApi() } }

            val html = client.get("/api/docs").bodyAsText()

            assertEquals(HttpStatusCode.OK, client.get("/api/schema.json").status)
            assertTrue(html.contains("/api/schema.json"))
            assertEquals(HttpStatusCode.NotFound, client.get("/docs").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/openapi.json").status)
        }

    @Test
    fun `docsPath en null desactiva Swagger UI y deja el esquema`() =
        testApplication {
            application { installKastApi(KastApiConfig(docsPath = null)) { exampleApi() } }

            val docs = client.get("/docs")

            assertEquals(HttpStatusCode.NotFound, docs.status)
            assertEquals("not_found", Json.parseToJsonElement(docs.bodyAsText()).jsonObject.text("error"))
            assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
        }

    @Test
    fun `openApiPath en null desactiva el esquema y tambien la documentacion`() =
        testApplication {
            application { installKastApi(KastApiConfig(openApiPath = null)) { exampleApi() } }

            assertEquals(HttpStatusCode.NotFound, client.get("/openapi.json").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/docs").status)
        }

    @Test
    fun `el HTML escapa la ruta del esquema para que no se pueda inyectar codigo`() {
        val html = swaggerUiHtml("Mi API", "/a</script><script>alert(1)</script>")

        assertFalse(html.contains("<script>alert(1)"), "la ruta no debe poder cerrar el script")
    }

    @Test
    fun `una ruta de usuario que choca con la documentacion es un problema de arranque`() {
        val docs = assertStartupFails(KastApiConfig()) { get<NoInput, Health>("/docs") { Health("x") } }
        val schema = assertStartupFails(KastApiConfig()) { get<NoInput, Health>("/openapi.json") { Health("x") } }

        assertTrue(docs.message.orEmpty().contains("GET /docs"), docs.message)
        assertTrue(schema.message.orEmpty().contains("GET /openapi.json"), schema.message)
    }

    @Test
    fun `una ruta de usuario puede usar el path de la documentacion si esta desactivada o con otro metodo`() =
        testApplication {
            val config = KastApiConfig(docsPath = null)
            application {
                installKastApi(config) {
                    get<NoInput, Health>("/docs") { Health("mia") }
                    post<NoInput, Health>("/openapi.json") { Health("mia") }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/docs").status)
        }
}
