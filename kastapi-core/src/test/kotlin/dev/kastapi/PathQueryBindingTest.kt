package dev.kastapi

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
data class UserPath(
    val id: Long,
)

@Serializable
data class ListQuery(
    val limit: Int = 20,
    val search: String? = null,
)

@Serializable
data class RequiredQuery(
    val name: String,
)

@Serializable
data class IntQuery(
    val n: Int,
)

@Serializable
data class LongQuery(
    val n: Long,
)

@Serializable
data class DoubleQuery(
    val n: Double,
)

@Serializable
data class BooleanQuery(
    val flag: Boolean,
)

@Serializable
enum class Color { RED, GREEN }

@Serializable
data class EnumQuery(
    val color: Color,
)

@Serializable
data class TagsQuery(
    val tag: List<String> = emptyList(),
)

@Serializable
data class TwoInts(
    val a: Int,
    val b: Int,
)

@Serializable
data class UserWithQuery(
    val id: Long,
    val verbose: Boolean = false,
)

@Serializable
data class NullableWithoutDefault(
    val q: String?,
)

@Serializable
data class WithNested(
    val inner: Health,
)

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.detailText(
    key: String,
    index: Int = 0,
): String {
    val detail = getValue("details").jsonArray[index].jsonObject
    return detail.text(key)
}

private suspend fun HttpResponse.errorBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

class PathQueryBindingTest {
    @Test
    fun `path receives id as Long`() =
        testApplication {
            application {
                installKastApi {
                    get<UserPath, UserPath>("/users/{id}") { it }
                }
            }

            val response = client.get("/users/42")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":42}""", response.bodyAsText())
        }

    @Test
    fun `query uses the default values when they are not sent`() =
        testApplication {
            application {
                installKastApi {
                    get<ListQuery, ListQuery>("/users") { it }
                }
            }

            val response = client.get("/users")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"limit":20}""", response.bodyAsText())
        }

    @Test
    fun `query takes the given values`() =
        testApplication {
            application {
                installKastApi {
                    get<ListQuery, ListQuery>("/users") { it }
                }
            }

            val response = client.get("/users?limit=5&search=ana")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"limit":5,"search":"ana"}""", response.bodyAsText())
        }

    @Test
    fun `query converts Int`() =
        testApplication {
            application { installKastApi { get<IntQuery, IntQuery>("/n") { it } } }

            assertEquals("""{"n":-7}""", client.get("/n?n=-7").bodyAsText())
        }

    @Test
    fun `query converts Long`() =
        testApplication {
            application { installKastApi { get<LongQuery, LongQuery>("/n") { it } } }

            assertEquals("""{"n":9999999999}""", client.get("/n?n=9999999999").bodyAsText())
        }

    @Test
    fun `query converts Double`() =
        testApplication {
            application { installKastApi { get<DoubleQuery, DoubleQuery>("/n") { it } } }

            assertEquals("""{"n":1.5}""", client.get("/n?n=1.5").bodyAsText())
        }

    @Test
    fun `query converts Boolean`() =
        testApplication {
            application { installKastApi { get<BooleanQuery, BooleanQuery>("/b") { it } } }

            assertEquals("""{"flag":true}""", client.get("/b?flag=true").bodyAsText())
            assertEquals("""{"flag":false}""", client.get("/b?flag=false").bodyAsText())
        }

    @Test
    fun `query converts an enum by name`() =
        testApplication {
            application { installKastApi { get<EnumQuery, EnumQuery>("/c") { it } } }

            assertEquals("""{"color":"GREEN"}""", client.get("/c?color=GREEN").bodyAsText())
        }

    @Test
    fun `query converts a List of String with repeated parameters`() =
        testApplication {
            application { installKastApi { get<TagsQuery, TagsQuery>("/t") { it } } }

            assertEquals("""{"tag":["a","b"]}""", client.get("/t?tag=a&tag=b").bodyAsText())
            assertEquals("""{"tag":["a"]}""", client.get("/t?tag=a").bodyAsText())
            assertEquals("""{"tag":[]}""", client.get("/t").bodyAsText())
        }

    @Test
    fun `a missing required query parameter responds 422 with code required`() =
        testApplication {
            application { installKastApi { get<RequiredQuery, RequiredQuery>("/s") { it } } }

            val response = client.get("/s")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(422, body.text("status").toInt())
            assertEquals("validation_error", body.text("error"))
            assertEquals("query", body.detailText("location"))
            assertEquals("name", body.detailText("field"))
            assertEquals("required", body.detailText("code"))
        }

    @Test
    fun `limit with text responds 422 with code type`() =
        testApplication {
            application { installKastApi { get<ListQuery, ListQuery>("/users") { it } } }

            val response = client.get("/users?limit=abc")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("query", body.detailText("location"))
            assertEquals("limit", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
            assertEquals("abc", body.detailText("value"))
        }

    @Test
    fun `a non-numeric path responds 422 with location path`() =
        testApplication {
            application { installKastApi { get<UserPath, UserPath>("/users/{id}") { it } } }

            val response = client.get("/users/abc")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("path", body.detailText("location"))
            assertEquals("id", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
        }

    @Test
    fun `several binding errors are returned together`() =
        testApplication {
            application { installKastApi { get<TwoInts, TwoInts>("/two") { it } } }

            val body = client.get("/two?a=x&b=y").errorBody()

            assertEquals(2, body.getValue("details").jsonArray.size)
            assertEquals("a", body.detailText("field", index = 0))
            assertEquals("b", body.detailText("field", index = 1))
            assertEquals("The request has 2 validation errors", body.text("message"))
        }

    @Test
    fun `an enum with an unknown value responds 422 with code type`() =
        testApplication {
            application { installKastApi { get<EnumQuery, EnumQuery>("/c") { it } } }

            val response = client.get("/c?color=BLUE")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("type", response.errorBody().detailText("code"))
        }

    @Test
    fun `delete also binds path and query`() =
        testApplication {
            application {
                installKastApi {
                    delete<UserWithQuery, UserWithQuery>("/users/{id}") { it }
                    delete<UserPath, Unit>("/legacy/{id}", status = 204) { }
                }
            }

            assertEquals("""{"id":7,"verbose":true}""", client.delete("/users/7?verbose=true").bodyAsText())
            assertEquals(HttpStatusCode.NoContent, client.delete("/legacy/7").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, client.delete("/legacy/x").status)
        }

    @Test
    fun `path and query are combined in the same input`() =
        testApplication {
            application { installKastApi { get<UserWithQuery, UserWithQuery>("/users/{id}") { it } } }

            assertEquals("""{"id":3,"verbose":false}""", client.get("/users/3").bodyAsText())
            assertEquals("""{"id":3,"verbose":true}""", client.get("/users/3?verbose=true").bodyAsText())
        }

    @Test
    fun `a nullable property without a default that is absent arrives as null`() =
        testApplication {
            application {
                installKastApi {
                    get<NullableWithoutDefault, NullableWithoutDefault>("/q") { it }
                }
            }

            val response = client.get("/q")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("{}", response.bodyAsText())
        }

    @Test
    fun `undeclared query parameters are ignored`() =
        testApplication {
            application { installKastApi { get<ListQuery, ListQuery>("/users") { it } } }

            val response = client.get("/users?limit=5&other=1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"limit":5}""", response.bodyAsText())
        }

    @Test
    fun `a nested object in query fails at registration and mentions the property`() =
        testApplication {
            application {
                val error =
                    assertFailsWith<KastApiConfigurationException> {
                        installKastApi {
                            get<WithNested, Health>("/x") { Health("x") }
                        }
                    }
                assertTrue(error.message.orEmpty().contains("inner"), "the message must mention the property")
            }

            // Forces the application to start so that the previous block runs.
            client.get("/")
        }
}
