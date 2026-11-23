package dev.kastapi

import dev.kastapi.routing.allowHeader
import dev.kastapi.routing.isMethodMismatch
import dev.kastapi.routing.matchesTemplate
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.options
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.client.request.get as clientGet
import io.ktor.server.routing.get as routingGet

@Serializable
data class AhItem(
    val id: Long = 1,
)

@Serializable
data class AhPath(
    val id: Long,
)

@Serializable
data class AhBody(
    val name: String = "x",
)

@Serializable
data class AhQuery(
    val limit: Int = 10,
)

/** `Allow` on 405 and automatic `HEAD` for the `GET` routes of KastAPI (reference §8). */
class AllowAndHeadTest {
    private val handlerCalls = AtomicInteger()

    private fun Application.sampleApi() {
        routing {
            post("/native") { call.respondText("native") }
        }
        installKastApi {
            get<NoInput, AhItem>("/one") {
                handlerCalls.incrementAndGet()
                AhItem()
            }
            get<NoInput, List<AhItem>>("/items") { emptyList() }
            post<AhBody, AhItem>("/items") { AhItem() }
            get<AhPath, AhItem>("/items/{id}") { AhItem(it.id) }
            delete<AhPath, Unit>("/items/{id}", status = 204) { _ -> }
            post<AhBody, AhItem>("/only-post") { AhItem() }
            get<AhQuery, AhItem>("/search") { AhItem() }
            // Declared in the "wrong" order: the Allow header must still be in the fixed order.
            delete<NoInput, Unit>("/mixed", status = 204) { _ -> }
            put<AhBody, AhItem>("/mixed") { AhItem() }
            post<AhBody, AhItem>("/mixed") { AhItem() }
            get<NoInput, Unit>("/gone", status = 404) { _ -> }
            group("/v1") {
                get<NoInput, AhItem>("/g") { AhItem() }
                patch<AhBody, AhItem>("/g") { AhItem() }
            }
        }
    }

    private fun sample(test: suspend io.ktor.client.HttpClient.() -> Unit) =
        testApplication {
            application { sampleApi() }
            client.test()
        }

    // ---- Allow on 405

    @Test
    fun `a 405 lists the methods registered for the path with HEAD right after GET`() =
        sample {
            val cases =
                mapOf(
                    delete("/one") to "GET, HEAD",
                    put("/items") to "GET, HEAD, POST",
                    put("/items/5") to "GET, HEAD, DELETE",
                    put("/v1/g") to "GET, HEAD, PATCH",
                    post("/search") to "GET, HEAD",
                    patch("/mixed") to "POST, PUT, DELETE",
                )
            for ((response, expected) in cases) {
                assertEquals(HttpStatusCode.MethodNotAllowed, response.status, expected)
                assertEquals(expected, response.headers[HttpHeaders.Allow], expected)
                val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals("method_not_allowed", error.getValue("error").toString().trim('"'))
            }
        }

    @Test
    fun `a path with parameters answers 405 and not 404 when only the method is wrong`() =
        sample {
            // Plain Ktor answers 404 here; KastAPI knows the path exists.
            for (response in listOf(put("/items/5"), options("/items/5"), put("/v1/g"))) {
                assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
                assertNotNull(response.headers[HttpHeaders.Allow])
            }
        }

    @Test
    fun `a 404 sent by a matching route stays a 404`() =
        sample {
            val response = clientGet("/gone")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertNull(response.headers[HttpHeaders.Allow])
            // The same path with a method it does not have is a 405.
            val wrongMethod = post("/gone")
            assertEquals(HttpStatusCode.MethodNotAllowed, wrongMethod.status)
            assertEquals("GET, HEAD", wrongMethod.headers[HttpHeaders.Allow])
            // HEAD is implied by GET, so it is not a mismatch either.
            assertEquals(HttpStatusCode.NotFound, head("/gone").status)
        }

    @Test
    fun `OPTIONS is still a 405 but now says what is allowed`() =
        sample {
            val response = options("/one")
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals("GET, HEAD", response.headers[HttpHeaders.Allow])
        }

    @Test
    fun `a 405 coming from a plain Ktor route carries no Allow header`() =
        sample {
            val response = clientGet("/native")
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertNull(response.headers[HttpHeaders.Allow], "KastAPI cannot know the methods of routes it does not own")
        }

    @Test
    fun `a 404 carries no Allow header`() =
        sample {
            val response = delete("/nope")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertNull(response.headers[HttpHeaders.Allow])
        }

    // ---- automatic HEAD

    @Test
    fun `HEAD on a GET route answers with the status and headers of GET`() =
        sample {
            val get = clientGet("/one")
            val head = head("/one")
            assertEquals(HttpStatusCode.OK, head.status)
            assertEquals(get.headers[HttpHeaders.ContentType], head.headers[HttpHeaders.ContentType])
            assertEquals(get.headers[HttpHeaders.ContentLength], head.headers[HttpHeaders.ContentLength])
            assertNotNull(head.headers[HttpHeaders.ContentLength])
        }

    @Test
    fun `HEAD runs the same handler as GET`() =
        sample {
            clientGet("/one")
            assertEquals(1, handlerCalls.get())
            head("/one")
            assertEquals(2, handlerCalls.get())
        }

    @Test
    fun `HEAD binds path parameters like GET`() =
        sample {
            assertEquals(HttpStatusCode.OK, head("/items/7").status)
            assertEquals(HttpStatusCode.OK, head("/v1/g").status)
        }

    @Test
    fun `HEAD keeps the validation status of GET`() =
        sample {
            for (path in listOf("/items/abc", "/search?limit=x")) {
                val response = head(path)
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status, path)
            }
        }

    @Test
    fun `HEAD on a path without a GET route is a 405 that lists what is allowed`() =
        sample {
            val response = head("/only-post")
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals("POST", response.headers[HttpHeaders.Allow])
        }

    @Test
    fun `HEAD on an unknown path is a 404`() =
        sample {
            assertEquals(HttpStatusCode.NotFound, head("/nope").status)
        }

    @Test
    fun `HEAD does not appear in the OpenAPI document`() =
        sample {
            val document = Json.parseToJsonElement(clientGet("/openapi.json").bodyAsText()).jsonObject
            val paths = document.getValue("paths").jsonObject
            for ((path, operations) in paths) {
                assertFalse("head" in operations.jsonObject, "$path documents a HEAD operation")
            }
            assertEquals(setOf("get"), paths.getValue("/one").jsonObject.keys)
        }

    // ---- real Netty server: the body really is dropped

    @Test
    fun `on a real server HEAD sends the headers of GET and no body bytes`() {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(Netty, port = port) { sampleApi() }
        server.start(wait = false)
        try {
            val base = "http://localhost:$port"
            awaitReady("$base/one")
            val get = call(base, "/one", "GET")
            val head = call(base, "/one", "HEAD")
            assertEquals(200, head.status)
            assertEquals(get.contentLength, head.contentLength, "HEAD must report the same Content-Length as GET")
            assertTrue((head.contentLength ?: 0) > 0)
            assertEquals(0, head.bodyBytes, "HEAD must not send a body")
            assertTrue(get.bodyBytes > 0)

            val invalid = call(base, "/items/abc", "HEAD")
            assertEquals(422, invalid.status)
            assertEquals(0, invalid.bodyBytes, "a HEAD error must not send a body either")

            val notAllowed = call(base, "/one", "DELETE")
            assertEquals(405, notAllowed.status)
            assertEquals("GET, HEAD", notAllowed.allow)
        } finally {
            server.stop(0, 0)
        }
    }

    private class Reply(
        val status: Int,
        val contentLength: Long?,
        val bodyBytes: Int,
        val allow: String?,
    )

    private fun call(
        base: String,
        path: String,
        method: String,
    ): Reply {
        val connection = URI("$base$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val bytes = stream?.readBytes()?.size ?: 0
        return Reply(
            status,
            connection.getHeaderField("Content-Length")?.toLong(),
            bytes,
            connection.getHeaderField("Allow"),
        )
    }

    private fun awaitReady(url: String) {
        repeat(50) {
            try {
                val c = URI(url).toURL().openConnection() as HttpURLConnection
                if (c.responseCode == 200) return
            } catch (_: java.io.IOException) {
                Thread.sleep(200)
            }
        }
        error("the server did not start: $url")
    }

    // ---- pure helpers

    @Test
    fun `a path template matches the paths Ktor would route to it`() {
        val matches =
            mapOf(
                "/users/{id}" to listOf("/users/5", "/users/abc"),
                "/a/{id?}" to listOf("/a", "/a/1"),
                "/files/{path...}" to listOf("/files", "/files/a", "/files/a/b/c"),
                "/x" to listOf("/x"),
                "/" to listOf("/"),
                "/v1/users/{id}/posts" to listOf("/v1/users/5/posts"),
            )
        for ((template, paths) in matches) {
            for (path in paths) {
                assertTrue(
                    matchesTemplate(template, path),
                    "$template should match $path",
                )
            }
        }
        val misses =
            mapOf(
                "/users/{id}" to listOf("/users", "/users/5/x", "/other/5"),
                "/x" to listOf("/X", "/x/y", "/"),
                "/a/{id?}" to listOf("/a/1/2"),
                "/v1/users/{id}/posts" to listOf("/v1/users/5", "/v1/users/5/comments"),
            )
        for ((template, paths) in misses) {
            for (path in paths) {
                assertFalse(
                    matchesTemplate(template, path),
                    "$template should not match $path",
                )
            }
        }
    }

    @Test
    fun `a method mismatch needs registered methods and a request method outside them and outside the implied HEAD`() {
        val get = listOf(HttpMethod.Get)
        assertTrue(isMethodMismatch(get, HttpMethod.Post))
        assertTrue(isMethodMismatch(get, HttpMethod.Options))
        assertFalse(isMethodMismatch(get, HttpMethod.Get))
        assertFalse(isMethodMismatch(get, HttpMethod.Head), "HEAD is implied by GET")
        assertTrue(isMethodMismatch(listOf(HttpMethod.Post), HttpMethod.Head), "HEAD is only implied by GET")
        assertFalse(isMethodMismatch(emptyList(), HttpMethod.Post), "an unknown path is a plain 404")
    }

    @Test
    fun `the Allow header lists methods in a fixed order and adds HEAD after GET`() {
        assertEquals("GET, HEAD", allowHeader(listOf(HttpMethod.Get)))
        assertEquals(
            "GET, HEAD, POST, PUT, PATCH, DELETE",
            allowHeader(listOf(HttpMethod.Delete, HttpMethod.Patch, HttpMethod.Put, HttpMethod.Post, HttpMethod.Get)),
        )
        assertEquals("POST", allowHeader(listOf(HttpMethod.Post)))
        assertEquals("POST, DELETE", allowHeader(listOf(HttpMethod.Delete, HttpMethod.Post, HttpMethod.Post)))
        assertNull(allowHeader(emptyList()))
    }
}
