package dev.kastapi

import dev.kastapi.validation.Pattern
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.measureTime

@Serializable
data class PlLetters(
    @Pattern("[a-z]*") val text: String,
)

/** A pattern that backtracks a lot: on `aaaa...b` it takes seconds with 30 characters. */
@Serializable
data class PlCatastrophic(
    @Pattern("(.*a){20}") val text: String,
)

@Serializable
data class PlQuery(
    @Pattern("[a-z]*") val q: String,
)

private suspend fun HttpClient.postJson(
    path: String,
    body: String,
): HttpResponse =
    post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private suspend fun HttpResponse.firstDetail() =
    Json
        .parseToJsonElement(bodyAsText())
        .jsonObject
        .getValue("details")
        .jsonArray
        .first()
        .jsonObject

class PatternLimitsTest {
    @Test
    fun `the defaults are 10000 characters and 100 milliseconds`() {
        val config = KastApiConfig()
        assertEquals(10_000, config.maxPatternInputLength)
        assertEquals(100L, config.patternTimeoutMillis)
    }

    @Test
    fun `both limits can be set and must be positive`() {
        val config =
            KastApiConfig {
                maxPatternInputLength = 5
                patternTimeoutMillis = 7
            }
        assertEquals(5, config.maxPatternInputLength)
        assertEquals(7L, config.patternTimeoutMillis)
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxPatternInputLength = 0 } }
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxPatternInputLength = -1 } }
        assertFailsWith<IllegalArgumentException> { KastApiConfig { patternTimeoutMillis = 0 } }
        assertFailsWith<IllegalArgumentException> { KastApiConfig { patternTimeoutMillis = -1 } }
    }

    @Test
    fun `equals and toString include the pattern limits`() {
        assertTrue(KastApiConfig { maxPatternInputLength = 5 } != KastApiConfig())
        assertTrue(KastApiConfig { patternTimeoutMillis = 5 } != KastApiConfig())
        assertTrue("maxPatternInputLength=10000" in KastApiConfig().toString())
        assertTrue("patternTimeoutMillis=100" in KastApiConfig().toString())
    }

    @Test
    fun `a text of exactly the maximum length is checked normally`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { maxPatternInputLength = 5 }) { post<PlLetters, PlLetters>("/p") { it } }
            }
            assertEquals(HttpStatusCode.Created, client.postJson("/p", """{"text":"abcde"}""").status)
        }

    @Test
    fun `a text longer than the maximum is a pattern error even if it would match`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { maxPatternInputLength = 5 }) { post<PlLetters, PlLetters>("/p") { it } }
            }
            val response = client.postJson("/p", """{"text":"abcdef"}""")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val detail = response.firstDetail()
            assertEquals("pattern", detail.getValue("code").jsonPrimitive.content)
            assertEquals("does not match the required format", detail.getValue("message").jsonPrimitive.content)
        }

    @Test
    fun `the length limit also applies to query parameters`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { maxPatternInputLength = 3 }) { get<PlQuery, PlQuery>("/q") { it } }
            }
            assertEquals(HttpStatusCode.OK, client.get("/q?q=abc").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/q?q=abcd").status)
        }

    @Test
    fun `with the default limit a text of 10000 characters is checked and 10001 is rejected`() =
        testApplication {
            application { installKastApi { post<PlLetters, PlLetters>("/p") { it } } }
            val ok = "a".repeat(10_000)
            val tooLong = "a".repeat(10_001)
            assertEquals(HttpStatusCode.Created, client.postJson("/p", """{"text":"$ok"}""").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, client.postJson("/p", """{"text":"$tooLong"}""").status)
        }

    @Test
    fun `a catastrophic pattern is cut by the time limit and answers 422 pattern`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { patternTimeoutMillis = 50 }) {
                    post<PlCatastrophic, PlCatastrophic>("/c") { it }
                    post<PlLetters, PlLetters>("/p") { it }
                }
            }
            val hostile = "a".repeat(34) + "b"
            lateinit var response: HttpResponse
            val elapsed = measureTime { response = client.postJson("/c", """{"text":"$hostile"}""") }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                "pattern",
                response
                    .firstDetail()
                    .getValue("code")
                    .jsonPrimitive.content,
            )
            assertTrue(elapsed.inWholeMilliseconds < 1500, "took $elapsed")
            // The server keeps working after a timeout.
            assertEquals(HttpStatusCode.Created, client.postJson("/p", """{"text":"abc"}""").status)
        }

    @Test
    fun `a catastrophic pattern in a query parameter is cut by the time limit too`() =
        testApplication {
            application {
                installKastApi(KastApiConfig { patternTimeoutMillis = 50 }) { get<PlQueryCat, PlQueryCat>("/q") { it } }
            }
            lateinit var response: HttpResponse
            val elapsed = measureTime { response = client.get("/q?q=${"a".repeat(34)}b") }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue(elapsed.inWholeMilliseconds < 1500, "took $elapsed")
        }

    @Test
    fun `a text that matches within the time limit is accepted`() =
        testApplication {
            application { installKastApi { post<PlLetters, PlLetters>("/p") { it } } }
            assertEquals(HttpStatusCode.Created, client.postJson("/p", """{"text":"abc"}""").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, client.postJson("/p", """{"text":"ab1"}""").status)
        }
}

@Serializable
data class PlQueryCat(
    @Pattern("(.*a){20}") val q: String,
)
