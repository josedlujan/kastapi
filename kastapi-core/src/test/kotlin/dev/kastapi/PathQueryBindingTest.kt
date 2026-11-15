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
    fun `path recibe id como Long`() =
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
    fun `query usa los valores por defecto cuando no se envian`() =
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
    fun `query toma los valores indicados`() =
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
    fun `query convierte Int`() =
        testApplication {
            application { installKastApi { get<IntQuery, IntQuery>("/n") { it } } }

            assertEquals("""{"n":-7}""", client.get("/n?n=-7").bodyAsText())
        }

    @Test
    fun `query convierte Long`() =
        testApplication {
            application { installKastApi { get<LongQuery, LongQuery>("/n") { it } } }

            assertEquals("""{"n":9999999999}""", client.get("/n?n=9999999999").bodyAsText())
        }

    @Test
    fun `query convierte Double`() =
        testApplication {
            application { installKastApi { get<DoubleQuery, DoubleQuery>("/n") { it } } }

            assertEquals("""{"n":1.5}""", client.get("/n?n=1.5").bodyAsText())
        }

    @Test
    fun `query convierte Boolean`() =
        testApplication {
            application { installKastApi { get<BooleanQuery, BooleanQuery>("/b") { it } } }

            assertEquals("""{"flag":true}""", client.get("/b?flag=true").bodyAsText())
            assertEquals("""{"flag":false}""", client.get("/b?flag=false").bodyAsText())
        }

    @Test
    fun `query convierte enum por nombre`() =
        testApplication {
            application { installKastApi { get<EnumQuery, EnumQuery>("/c") { it } } }

            assertEquals("""{"color":"GREEN"}""", client.get("/c?color=GREEN").bodyAsText())
        }

    @Test
    fun `query convierte List de String con parametros repetidos`() =
        testApplication {
            application { installKastApi { get<TagsQuery, TagsQuery>("/t") { it } } }

            assertEquals("""{"tag":["a","b"]}""", client.get("/t?tag=a&tag=b").bodyAsText())
            assertEquals("""{"tag":["a"]}""", client.get("/t?tag=a").bodyAsText())
            assertEquals("""{"tag":[]}""", client.get("/t").bodyAsText())
        }

    @Test
    fun `query requerido ausente responde 422 con code required`() =
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
    fun `limit con texto responde 422 con code type`() =
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
    fun `path no numerico responde 422 con location path`() =
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
    fun `varios errores de binding se devuelven juntos`() =
        testApplication {
            application { installKastApi { get<TwoInts, TwoInts>("/two") { it } } }

            val body = client.get("/two?a=x&b=y").errorBody()

            assertEquals(2, body.getValue("details").jsonArray.size)
            assertEquals("a", body.detailText("field", index = 0))
            assertEquals("b", body.detailText("field", index = 1))
            assertEquals("La petición tiene 2 errores de validación", body.text("message"))
        }

    @Test
    fun `enum con un valor desconocido responde 422 con code type`() =
        testApplication {
            application { installKastApi { get<EnumQuery, EnumQuery>("/c") { it } } }

            val response = client.get("/c?color=BLUE")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("type", response.errorBody().detailText("code"))
        }

    @Test
    fun `delete tambien enlaza path y query`() =
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
    fun `path y query se combinan en la misma entrada`() =
        testApplication {
            application { installKastApi { get<UserWithQuery, UserWithQuery>("/users/{id}") { it } } }

            assertEquals("""{"id":3,"verbose":false}""", client.get("/users/3").bodyAsText())
            assertEquals("""{"id":3,"verbose":true}""", client.get("/users/3?verbose=true").bodyAsText())
        }

    @Test
    fun `propiedad nullable sin default ausente llega como null`() =
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
    fun `parametros de query no declarados se ignoran`() =
        testApplication {
            application { installKastApi { get<ListQuery, ListQuery>("/users") { it } } }

            val response = client.get("/users?limit=5&otro=1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"limit":5}""", response.bodyAsText())
        }

    @Test
    fun `un objeto anidado en query falla al registrar y menciona la propiedad`() =
        testApplication {
            application {
                val error =
                    assertFailsWith<KastApiConfigurationException> {
                        installKastApi {
                            get<WithNested, Health>("/x") { Health("x") }
                        }
                    }
                assertTrue(error.message.orEmpty().contains("inner"), "el mensaje debe mencionar la propiedad")
            }

            // Fuerza el arranque de la aplicación para que se ejecute el bloque anterior.
            client.get("/")
        }
}
