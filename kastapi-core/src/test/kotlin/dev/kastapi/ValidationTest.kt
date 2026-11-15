package dev.kastapi

import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Max
import dev.kastapi.validation.Min
import dev.kastapi.validation.NotBlank
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class VMin(
    @Min(18) val age: Int,
)

@Serializable
data class VMax(
    @Max(100) val score: Int,
)

@Serializable
data class VLength(
    @Length(min = 2, max = 5) val name: String,
)

@Serializable
data class VTags(
    @Length(max = 2) val tags: List<String>,
)

@Serializable
data class VNotBlank(
    @NotBlank val name: String,
)

@Serializable
data class VEmail(
    @Email val email: String,
)

@Serializable
data class VPattern(
    @Pattern("^[A-Z]{3}$") val code: String,
)

@Serializable
data class VLimitQuery(
    @Min(1) @Max(100) val limit: Int = 20,
)

@Serializable
data class VPathId(
    @Min(1) val id: Long,
)

@Serializable
data class VRatio(
    @Min(1) val ratio: Double,
)

@Serializable
data class VMulti(
    @Min(18) val age: Int,
    @NotBlank val name: String,
    @Email val email: String,
)

@Serializable
data class VZip(
    @Pattern("\\d{5}") val zip: String,
)

@Serializable
data class VNested(
    @NotBlank val name: String,
    val address: VZip,
)

@Serializable
data class VProduct(
    @NotBlank val name: String,
)

@Serializable
data class VBasket(
    val items: List<VProduct>,
)

@Serializable
data class VOptional(
    @Email val backup: String? = null,
)

@Serializable
data class VIncompatible(
    @Email val n: Int,
)

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.detailText(
    key: String,
    index: Int = 0,
): String {
    val detail = getValue("details").jsonArray[index].jsonObject
    return detail.text(key)
}

private fun JsonObject.detailCount(): Int = getValue("details").jsonArray.size

private suspend fun HttpResponse.errorBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

private suspend fun HttpClient.postJson(
    path: String,
    body: String,
): HttpResponse =
    post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

class ValidationTest {
    @Test
    fun `Min accepts a value equal to the minimum`() =
        testApplication {
            application { installKastApi { post<VMin, VMin>("/min") { it } } }

            val response = client.postJson("/min", """{"age":18}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"age":18}""", response.bodyAsText())
        }

    @Test
    fun `Min rejects a smaller value with code min and the received value`() =
        testApplication {
            application { installKastApi { post<VMin, VMin>("/min") { it } } }

            val response = client.postJson("/min", """{"age":17}""")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("body", body.detailText("location"))
            assertEquals("age", body.detailText("field"))
            assertEquals("min", body.detailText("code"))
            assertEquals("must be greater than or equal to 18", body.detailText("message"))
            assertEquals("17", body.detailText("value"))
        }

    @Test
    fun `Max accepts a value equal to the maximum`() =
        testApplication {
            application { installKastApi { post<VMax, VMax>("/max") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/max", """{"score":100}""").status)
        }

    @Test
    fun `Max rejects a larger value with code max`() =
        testApplication {
            application { installKastApi { post<VMax, VMax>("/max") { it } } }

            val body = client.postJson("/max", """{"score":101}""").errorBody()

            assertEquals("score", body.detailText("field"))
            assertEquals("max", body.detailText("code"))
            assertEquals("must be less than or equal to 100", body.detailText("message"))
        }

    @Test
    fun `Length accepts a text within the range`() =
        testApplication {
            application { installKastApi { post<VLength, VLength>("/len") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/len", """{"name":"ab"}""").status)
            assertEquals(HttpStatusCode.Created, client.postJson("/len", """{"name":"abcde"}""").status)
        }

    @Test
    fun `Length rejects a text that is too short or too long`() =
        testApplication {
            application { installKastApi { post<VLength, VLength>("/len") { it } } }

            val corto = client.postJson("/len", """{"name":"a"}""").errorBody()
            val largo = client.postJson("/len", """{"name":"abcdef"}""").errorBody()

            assertEquals("length", corto.detailText("code"))
            assertEquals("must have between 2 and 5 characters", corto.detailText("message"))
            assertEquals("length", largo.detailText("code"))
        }

    @Test
    fun `Length counts an emoji as a single character`() =
        testApplication {
            application { installKastApi { post<VLength, VLength>("/len") { it } } }

            val response = client.postJson("/len", "{\"name\":\"\uD83D\uDE00\uD83D\uDE00\"}")

            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `Length validates the size of a list`() =
        testApplication {
            application { installKastApi { post<VTags, VTags>("/tags") { it } } }

            val valido = client.postJson("/tags", """{"tags":["a","b"]}""")
            val invalido = client.postJson("/tags", """{"tags":["a","b","c"]}""").errorBody()

            assertEquals(HttpStatusCode.Created, valido.status)
            assertEquals("tags", invalido.detailText("field"))
            assertEquals("length", invalido.detailText("code"))
            assertEquals("must have at most 2 items", invalido.detailText("message"))
        }

    @Test
    fun `NotBlank accepts a text with content`() =
        testApplication {
            application { installKastApi { post<VNotBlank, VNotBlank>("/nb") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/nb", """{"name":"ana"}""").status)
        }

    @Test
    fun `NotBlank rejects an empty or whitespace-only text`() =
        testApplication {
            application { installKastApi { post<VNotBlank, VNotBlank>("/nb") { it } } }

            val vacio = client.postJson("/nb", """{"name":""}""").errorBody()
            val espacios = client.postJson("/nb", """{"name":"   "}""").errorBody()

            assertEquals("not_blank", vacio.detailText("code"))
            assertEquals("not_blank", espacios.detailText("code"))
            assertEquals("must not be blank", espacios.detailText("message"))
        }

    @Test
    fun `Email accepts a valid address`() =
        testApplication {
            application { installKastApi { post<VEmail, VEmail>("/em") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/em", """{"email":"ana@kastapi.dev"}""").status)
        }

    @Test
    fun `Email rejects an invalid address with the received value`() =
        testApplication {
            application { installKastApi { post<VEmail, VEmail>("/em") { it } } }

            val body = client.postJson("/em", """{"email":"ana@kastapi"}""").errorBody()

            assertEquals("email", body.detailText("code"))
            assertEquals("must be a valid email address", body.detailText("message"))
            assertEquals("ana@kastapi", body.detailText("value"))
        }

    @Test
    fun `Pattern accepts a text that matches entirely`() =
        testApplication {
            application { installKastApi { post<VPattern, VPattern>("/pt") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/pt", """{"code":"ABC"}""").status)
        }

    @Test
    fun `Pattern rejects a text that does not match`() =
        testApplication {
            application { installKastApi { post<VPattern, VPattern>("/pt") { it } } }

            val body = client.postJson("/pt", """{"code":"abc"}""").errorBody()

            assertEquals("pattern", body.detailText("code"))
            assertEquals("does not match the required format", body.detailText("message"))
        }

    @Test
    fun `annotations are also validated in query with location query`() =
        testApplication {
            application { installKastApi { get<VLimitQuery, VLimitQuery>("/items") { it } } }

            val bajo = client.get("/items?limit=0")
            val alto = client.get("/items?limit=101").errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, bajo.status)
            assertEquals("query", bajo.errorBody().detailText("location"))
            assertEquals("limit", bajo.errorBody().detailText("field"))
            assertEquals("min", bajo.errorBody().detailText("code"))
            assertEquals("max", alto.detailText("code"))
            assertEquals(HttpStatusCode.OK, client.get("/items?limit=50").status)
        }

    @Test
    fun `the default value of an annotated property is not validated`() =
        testApplication {
            application { installKastApi { get<VLimitQuery, VLimitQuery>("/items") { it } } }

            val response = client.get("/items")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"limit":20}""", response.bodyAsText())
        }

    @Test
    fun `annotations are also validated in path with location path`() =
        testApplication {
            application { installKastApi { get<VPathId, VPathId>("/p/{id}") { it } } }

            val response = client.get("/p/0")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("path", body.detailText("location"))
            assertEquals("id", body.detailText("field"))
            assertEquals("min", body.detailText("code"))
            assertEquals(HttpStatusCode.OK, client.get("/p/1").status)
        }

    @Test
    fun `Min compares a Double with its decimal value`() =
        testApplication {
            application { installKastApi { post<VRatio, VRatio>("/ratio") { it } } }

            val bajo = client.postJson("/ratio", """{"ratio":0.5}""")
            val igual = client.postJson("/ratio", """{"ratio":1.0}""")

            assertEquals(HttpStatusCode.UnprocessableEntity, bajo.status)
            assertEquals("min", bajo.errorBody().detailText("code"))
            assertEquals(HttpStatusCode.Created, igual.status)
        }

    @Test
    fun `several annotation errors are returned together`() =
        testApplication {
            application { installKastApi { post<VMulti, VMulti>("/multi") { it } } }

            val body = client.postJson("/multi", """{"age":10,"name":" ","email":"x"}""").errorBody()

            assertEquals(3, body.detailCount())
            assertEquals("min", body.detailText("code", index = 0))
            assertEquals("not_blank", body.detailText("code", index = 1))
            assertEquals("email", body.detailText("code", index = 2))
            assertEquals("The request has 3 validation errors", body.text("message"))
        }

    @Test
    fun `a type error and an annotation error are returned together`() =
        testApplication {
            application { installKastApi { post<VMulti, VMulti>("/multi") { it } } }

            val body = client.postJson("/multi", """{"age":"x","name":" ","email":"a@b.c"}""").errorBody()

            assertEquals(2, body.detailCount())
            assertEquals("type", body.detailText("code", index = 0))
            assertEquals("age", body.detailText("field", index = 0))
            assertEquals("not_blank", body.detailText("code", index = 1))
        }

    @Test
    fun `annotations are validated inside nested objects`() =
        testApplication {
            application { installKastApi { post<VNested, VNested>("/nested") { it } } }

            val body = client.postJson("/nested", """{"name":"ana","address":{"zip":"abc"}}""").errorBody()

            assertEquals(1, body.detailCount())
            assertEquals("address.zip", body.detailText("field"))
            assertEquals("pattern", body.detailText("code"))
        }

    @Test
    fun `annotations are validated on each element of a list of objects`() =
        testApplication {
            application { installKastApi { post<VBasket, VBasket>("/basket") { it } } }

            val body = client.postJson("/basket", """{"items":[{"name":"a"},{"name":" "}]}""").errorBody()

            assertEquals(1, body.detailCount())
            assertEquals("items[1].name", body.detailText("field"))
            assertEquals("not_blank", body.detailText("code"))
        }

    @Test
    fun `null or absence in an annotated optional property is not validated`() =
        testApplication {
            application { installKastApi { post<VOptional, VOptional>("/opt") { it } } }

            assertEquals(HttpStatusCode.Created, client.postJson("/opt", """{"backup":null}""").status)
            assertEquals(HttpStatusCode.Created, client.postJson("/opt", "{}").status)
            assertEquals(HttpStatusCode.UnprocessableEntity, client.postJson("/opt", """{"backup":"x"}""").status)
        }
}
