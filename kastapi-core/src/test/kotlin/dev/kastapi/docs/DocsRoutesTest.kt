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

        // Forces the application to start so that the previous block runs.
        client.get("/")
    }
    return checkNotNull(failure)
}

class DocsRoutesTest {
    @Test
    fun `GET docs returns the Swagger UI HTML that references the schema`() =
        testApplication {
            application { installKastApi { exampleApi() } }

            val response = client.get("/docs")
            val html = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
            assertTrue(html.contains("/openapi.json"), "the HTML must reference /openapi.json")
            assertTrue(html.contains("swagger-ui-dist@5.34.1"), "the Swagger UI version must be pinned")
            assertTrue(html.contains("integrity=\"sha384-"), "the CDN resources must carry integrity")
        }

    @Test
    fun `GET openapi json is served as application json`() =
        testApplication {
            application { installKastApi { exampleApi() } }

            val response = client.get("/openapi.json")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("3.1.0", Json.parseToJsonElement(response.bodyAsText()).jsonObject.text("openapi"))
        }

    @Test
    fun `configured paths replace the default ones and the HTML points to the configured schema`() =
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
    fun `docsPath set to null disables Swagger UI and keeps the schema`() =
        testApplication {
            application { installKastApi(KastApiConfig(docsPath = null)) { exampleApi() } }

            val docs = client.get("/docs")

            assertEquals(HttpStatusCode.NotFound, docs.status)
            assertEquals("not_found", Json.parseToJsonElement(docs.bodyAsText()).jsonObject.text("error"))
            assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
        }

    @Test
    fun `openApiPath set to null disables the schema and the documentation too`() =
        testApplication {
            application { installKastApi(KastApiConfig(openApiPath = null)) { exampleApi() } }

            assertEquals(HttpStatusCode.NotFound, client.get("/openapi.json").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/docs").status)
        }

    @Test
    fun `the HTML escapes the schema path so code cannot be injected`() {
        val html = swaggerUiHtml("My API", "/a</script><script>alert(1)</script>")

        assertFalse(html.contains("<script>alert(1)"), "the path must not be able to close the script")
    }

    @Test
    fun `a user route that clashes with the documentation is a startup problem`() {
        val docs = assertStartupFails(KastApiConfig()) { get<NoInput, Health>("/docs") { Health("x") } }
        val schema = assertStartupFails(KastApiConfig()) { get<NoInput, Health>("/openapi.json") { Health("x") } }

        assertTrue(docs.message.orEmpty().contains("GET /docs"), docs.message)
        assertTrue(schema.message.orEmpty().contains("GET /openapi.json"), schema.message)
    }

    @Test
    fun `a user route can use the documentation path if it is disabled or with another method`() =
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
