package dev.kastapi

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
data class EdPayload(
    val n: Int,
)

/** The `details` field only travels in validation errors, whatever the application's Json settings are. */
class ErrorDetailsFieldTest {
    private val customJsons =
        mapOf(
            "default" to KastApiConfig().json,
            "encodeDefaults and explicitNulls" to
                Json {
                    encodeDefaults = true
                    explicitNulls = true
                },
        )

    private fun run(
        json: Json,
        test: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        application {
            installKastApi(
                KastApiConfig {
                    this.json = json
                    maxBodyBytes = 50
                },
            ) {
                post<EdPayload, EdPayload>("/p") { it }
                get<NoInput, EdPayload>("/boom") { error("boom") }
                get<NoInput, EdPayload>("/teapot") { throw HttpError(409, "conflict") }
            }
        }
        createClient { }.test()
    }

    private suspend fun HttpResponse.body(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    @Test
    fun `non-validation errors carry no details field`() {
        for ((label, json) in customJsons) {
            run(json) {
                val cases =
                    mapOf(
                        "404" to get("/nope"),
                        "405" to post("/boom"),
                        "409" to get("/teapot"),
                        "500" to get("/boom"),
                        "malformed" to
                            post("/p") {
                                contentType(ContentType.Application.Json)
                                setBody("{")
                            },
                        "413" to
                            post("/p") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"n":${"1".repeat(100)}}""")
                            },
                    )
                for ((name, response) in cases) {
                    assertFalse(response.body().containsKey("details"), "$name ($label) must not carry details")
                }
            }
        }
    }

    @Test
    fun `validation errors still carry the details list`() {
        for ((label, json) in customJsons) {
            run(json) {
                val response =
                    post("/p") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"n":"x"}""")
                    }
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status, label)
                val details = response.body().getValue("details").jsonArray
                assertTrue(details.isNotEmpty(), "details must be present and non-empty ($label)")
            }
        }
    }
}
