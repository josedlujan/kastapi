package dev.kastapi

import io.ktor.client.HttpClient
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
data class CreateUser(
    val email: String,
    val age: Int,
    val nickname: String? = null,
)

@Serializable
data class UpdateUser(
    val id: Long,
    val name: String,
)

@Serializable
data class Address(
    val street: String,
    val zip: String,
)

@Serializable
data class Person(
    val name: String,
    val address: Address,
)

@Serializable
data class Item(
    val name: String,
)

@Serializable
data class Order(
    val items: List<Item>,
)

@Serializable
data class Profile(
    val color: Color,
    val scores: Map<String, Int>,
)

@Serializable
data class NullableBody(
    val note: String?,
)

@Serializable
data class AllOptional(
    val a: Int = 1,
    val b: String = "x",
)

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.detailAt(index: Int): JsonObject = getValue("details").jsonArray[index].jsonObject

private fun JsonObject.detailText(
    key: String,
    index: Int = 0,
): String = detailAt(index).text(key)

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

private suspend fun HttpClient.putJson(
    path: String,
    body: String,
): HttpResponse =
    put(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

class BodyBindingTest {
    @Test
    fun `post decodes the JSON body`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"email":"a@b.c","age":20,"nickname":"ana"}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"email":"a@b.c","age":20,"nickname":"ana"}""", response.bodyAsText())
        }

    @Test
    fun `put takes the id from the URL and the name from the body`() =
        testApplication {
            application {
                installKastApi { put<UpdateUser, UpdateUser>("/users/{id}") { it } }
            }

            val response = client.putJson("/users/5", """{"name":"Ana"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":5,"name":"Ana"}""", response.bodyAsText())
        }

    @Test
    fun `the path wins if the body carries the same property`() =
        testApplication {
            application {
                installKastApi { put<UpdateUser, UpdateUser>("/users/{id}") { it } }
            }

            val response = client.putJson("/users/5", """{"id":99,"name":"Ana"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":5,"name":"Ana"}""", response.bodyAsText())
        }

    @Test
    fun `malformed JSON responds 400 with malformed_json`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"email":""")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("malformed_json", body.text("error"))
            assertEquals(400, body.text("status").toInt())
        }

    @Test
    fun `a missing required field responds 422 with code required and its field`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"age":20}""")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("validation_error", body.text("error"))
            assertEquals("body", body.detailText("location"))
            assertEquals("email", body.detailText("field"))
            assertEquals("required", body.detailText("code"))
        }

    @Test
    fun `an unknown field responds 422 with code unknown_field`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"email":"a@b.c","age":20,"extra":1}""")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("extra", body.detailText("field"))
            assertEquals("unknown_field", body.detailText("code"))
        }

    @Test
    fun `a missing field inside a nested object reports address dot zip`() =
        testApplication {
            application {
                installKastApi { post<Person, Person>("/people") { it } }
            }

            val response = client.postJson("/people", """{"name":"A","address":{"street":"s"}}""")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(1, body.detailCount())
            assertEquals("address.zip", body.detailText("field"))
            assertEquals("required", body.detailText("code"))
        }

    @Test
    fun `a wrong type inside a nested object reports address dot zip`() =
        testApplication {
            application {
                installKastApi { post<Person, Person>("/people") { it } }
            }

            val response = client.postJson("/people", """{"name":"A","address":{"street":"s","zip":5}}""")
            val body = response.errorBody()

            assertEquals("address.zip", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
            assertEquals("5", body.detailText("value"))
        }

    @Test
    fun `a wrong type responds 422 with code type and the received value`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"email":"a@b.c","age":"veinte"}""").errorBody()

            assertEquals("age", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
            assertEquals("veinte", body.detailText("value"))
        }

    @Test
    fun `an integer outside the Int range responds 422 with code type`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"email":"a@b.c","age":3000000000}""").errorBody()

            assertEquals("age", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
        }

    @Test
    fun `several body errors are returned together`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"age":"x","extra":1}""").errorBody()

            assertEquals(3, body.detailCount())
            assertEquals("The request has 3 validation errors", body.text("message"))
        }

    @Test
    fun `an error in a list of objects reports items bracket 1 dot name`() =
        testApplication {
            application {
                installKastApi { post<Order, Order>("/orders") { it } }
            }

            val body = client.postJson("/orders", """{"items":[{"name":"a"},{}]}""").errorBody()

            assertEquals("items[1].name", body.detailText("field"))
            assertEquals("required", body.detailText("code"))
        }

    @Test
    fun `null in a non-nullable property responds 422 with code type`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"email":null,"age":1}""").errorBody()

            assertEquals("email", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
            assertEquals("must not be null", body.detailText("message"))
        }

    @Test
    fun `a body that is not an object responds 422 with code type and an empty field`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", "[]")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
        }

    @Test
    fun `an empty body is treated as an empty object and reports the required fields`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.post("/users")
            val body = response.errorBody()

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(2, body.detailCount())
            assertEquals("required", body.detailText("code", index = 0))
            assertEquals("required", body.detailText("code", index = 1))
        }

    @Test
    fun `an empty body works if all properties are optional`() =
        testApplication {
            application {
                installKastApi { post<AllOptional, AllOptional>("/opts") { it } }
            }

            val response = client.post("/opts")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"a":1,"b":"x"}""", response.bodyAsText())
        }

    @Test
    fun `patch also reads the body`() =
        testApplication {
            application {
                installKastApi { patch<AllOptional, AllOptional>("/opts") { it } }
            }

            val response =
                client.patch("/opts") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"a":5}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"a":5,"b":"x"}""", response.bodyAsText())
        }

    @Test
    fun `with ignoreUnknownKeys unknown fields are ignored`() =
        testApplication {
            application {
                val config =
                    KastApiConfig(
                        json =
                            Json {
                                ignoreUnknownKeys = true
                                explicitNulls = false
                                encodeDefaults = true
                            },
                    )
                installKastApi(config) { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"email":"a@b.c","age":20,"extra":1}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"email":"a@b.c","age":20}""", response.bodyAsText())
        }

    @Test
    fun `a valid enum and map are decoded`() =
        testApplication {
            application {
                installKastApi { post<Profile, Profile>("/profiles") { it } }
            }

            val response = client.postJson("/profiles", """{"color":"RED","scores":{"a":1,"b":2}}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"color":"RED","scores":{"a":1,"b":2}}""", response.bodyAsText())
        }

    @Test
    fun `an unknown enum and a wrong map value report their field`() =
        testApplication {
            application {
                installKastApi { post<Profile, Profile>("/profiles") { it } }
            }

            val body = client.postJson("/profiles", """{"color":"BLUE","scores":{"a":"x"}}""").errorBody()

            assertEquals(2, body.detailCount())
            assertEquals("color", body.detailText("field", index = 0))
            assertEquals("scores.a", body.detailText("field", index = 1))
        }

    @Test
    fun `a nullable property without a default that is absent from the body arrives as null`() =
        testApplication {
            application {
                installKastApi { post<NullableBody, NullableBody>("/notes") { it } }
            }

            val response = client.postJson("/notes", "{}")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("{}", response.bodyAsText())
        }

    @Test
    fun `path and body errors are returned together`() =
        testApplication {
            application {
                installKastApi { put<UpdateUser, UpdateUser>("/users/{id}") { it } }
            }

            val body = client.putJson("/users/x", "{}").errorBody()

            assertEquals(2, body.detailCount())
            assertEquals("path", body.detailText("location", index = 0))
            assertEquals("id", body.detailText("field", index = 0))
            assertEquals("body", body.detailText("location", index = 1))
            assertEquals("name", body.detailText("field", index = 1))
        }
}
