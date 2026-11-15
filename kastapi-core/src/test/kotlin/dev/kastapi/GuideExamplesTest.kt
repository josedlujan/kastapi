package dev.kastapi

import dev.kastapi.test.kastTest
import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Max
import dev.kastapi.validation.Min
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.request.get as clientGet

// Examples from the guides in docs/guide: if the behavior changes, these tests force a review of them.
@Serializable
data class GdSearch(
    @Min(1) @Max(100) val limit: Int = 20,
    val search: String? = null,
    val tag: List<String> = emptyList(),
)

@Serializable
data class GdUpdate(
    val id: Long,
    @Length(min = 2, max = 30) val name: String,
)

@Serializable
data class GdUser(
    val id: Long,
    val name: String,
)

@Serializable
data class GdSignup(
    @Email val email: String,
    @Min(18) val age: Int,
)

class GuideExamplesTest {
    @Test
    fun `guide 02 - query with a default, nullable and a list`() =
        kastTest({
            get<GdSearch, GdSearch>("/users") { it }
        }) {
            val full: GdSearch = client.clientGet("/users?limit=5&search=ana&tag=a&tag=b").bodyAs()
            assertEquals(GdSearch(5, "ana", listOf("a", "b")), full)
            val defaults: GdSearch = client.clientGet("/users").bodyAs()
            assertEquals(GdSearch(), defaults)
        }

    @Test
    fun `guide 02 - path and body mixed, the path wins`() =
        kastTest({
            put<GdUpdate, GdUser>("/users/{id}") { GdUser(it.id, it.name) }
        }) {
            val response =
                client.put("/users/7") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"id": 99, "name": "Ana"}""")
                }
            assertEquals(GdUser(7, "Ana"), response.bodyAs<GdUser>())
        }

    @Test
    fun `guide 02 - group applies prefix and tags, and delete 204 has no body`() =
        kastTest({
            group("/v1", tags = listOf("users")) {
                get<NoInput, GdUser>("/me") { GdUser(1, "me") }
                delete<NoInput, Unit>("/me", status = 204) { }
            }
        }) {
            assertEquals(GdUser(1, "me"), client.clientGet("/v1/me").bodyAs<GdUser>())
            val deleted = client.delete("/v1/me")
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertEquals("", deleted.bodyAsText())
        }

    @Test
    fun `guide 02 - header from KastCall`() =
        kastTest({
            get<NoInput, GdUser>("/who") { GdUser(1, header("X-User") ?: "anonymous") }
        }) {
            assertEquals("anonymous", client.clientGet("/who").bodyAs<GdUser>().name)
            val named =
                client.clientGet("/who") {
                    header("X-User", "ana")
                }
            assertEquals("ana", named.bodyAs<GdUser>().name)
        }

    @Test
    fun `guide 03 - all validation errors at once`() =
        kastTest({
            post<GdSignup, GdUser>("/signup") { GdUser(1, it.email) }
        }) {
            val response =
                client.post("/signup") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "x", "age": 15}""")
                }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val text = response.bodyAsText()
            assertTrue("The request has 2 validation errors" in text)
            assertTrue(""""code":"email"""" in text && """"code":"min"""" in text)
        }

    @Test
    fun `guide 04 - HttpError responds with its status and error http_error`() =
        kastTest({
            get<NoInput, GdUser>("/teapot") { throw HttpError(409, "already exists") }
        }) {
            val response = client.clientGet("/teapot")
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(""""error":"http_error"""" in response.bodyAsText())
        }

    @Test
    fun `guide 06 - installKastApi coexists with Ktor routes and plugins of its own`() =
        testApplication {
            application {
                install(StatusPages) { }
                routing { get("/ping") { call.respondText("pong") } }
                installKastApi { get<NoInput, GdUser>("/me") { GdUser(1, "me") } }
            }
            assertEquals("pong", client.clientGet("/ping").bodyAsText())
            assertEquals(HttpStatusCode.OK, client.clientGet("/me").status)
            assertEquals(HttpStatusCode.OK, client.clientGet("/openapi.json").status)
        }

    @Test
    fun `guide 05 - info, docsPath and openApiPath are configurable`() =
        testApplication {
            application {
                installKastApi(KastApiConfig(docsPath = "/help", openApiPath = "/schema.json")) {
                    info(title = "My API", version = "2.0.0", description = "Demo")
                    get<NoInput, GdUser>("/me", summary = "Me", tags = listOf("users")) { GdUser(1, "me") }
                }
            }
            val schema = client.clientGet("/schema.json").bodyAsText()
            assertTrue(""""title":"My API"""" in schema && """"summary":"Me"""" in schema)
            assertEquals(HttpStatusCode.OK, client.clientGet("/help").status)
            assertEquals(HttpStatusCode.NotFound, client.clientGet("/docs").status)
        }
}
