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
    fun `post decodifica el JSON del cuerpo`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val response = client.postJson("/users", """{"email":"a@b.c","age":20,"nickname":"ana"}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"email":"a@b.c","age":20,"nickname":"ana"}""", response.bodyAsText())
        }

    @Test
    fun `put toma el id de la URL y el name del cuerpo`() =
        testApplication {
            application {
                installKastApi { put<UpdateUser, UpdateUser>("/users/{id}") { it } }
            }

            val response = client.putJson("/users/5", """{"name":"Ana"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":5,"name":"Ana"}""", response.bodyAsText())
        }

    @Test
    fun `el path gana si el cuerpo trae la misma propiedad`() =
        testApplication {
            application {
                installKastApi { put<UpdateUser, UpdateUser>("/users/{id}") { it } }
            }

            val response = client.putJson("/users/5", """{"id":99,"name":"Ana"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":5,"name":"Ana"}""", response.bodyAsText())
        }

    @Test
    fun `JSON mal formado responde 400 con malformed_json`() =
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
    fun `campo requerido ausente responde 422 con code required y su field`() =
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
    fun `campo desconocido responde 422 con code unknown_field`() =
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
    fun `un campo ausente dentro de un objeto anidado reporta address punto zip`() =
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
    fun `un tipo incorrecto dentro de un objeto anidado reporta address punto zip`() =
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
    fun `tipo incorrecto responde 422 con code type y el valor recibido`() =
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
    fun `un entero fuera del rango de Int responde 422 con code type`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"email":"a@b.c","age":3000000000}""").errorBody()

            assertEquals("age", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
        }

    @Test
    fun `varios errores del cuerpo se devuelven juntos`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"age":"x","extra":1}""").errorBody()

            assertEquals(3, body.detailCount())
            assertEquals("La petición tiene 3 errores de validación", body.text("message"))
        }

    @Test
    fun `el error en una lista de objetos reporta items corchete 1 punto name`() =
        testApplication {
            application {
                installKastApi { post<Order, Order>("/orders") { it } }
            }

            val body = client.postJson("/orders", """{"items":[{"name":"a"},{}]}""").errorBody()

            assertEquals("items[1].name", body.detailText("field"))
            assertEquals("required", body.detailText("code"))
        }

    @Test
    fun `null en una propiedad no nullable responde 422 con code type`() =
        testApplication {
            application {
                installKastApi { post<CreateUser, CreateUser>("/users") { it } }
            }

            val body = client.postJson("/users", """{"email":null,"age":1}""").errorBody()

            assertEquals("email", body.detailText("field"))
            assertEquals("type", body.detailText("code"))
            assertEquals("no puede ser nulo", body.detailText("message"))
        }

    @Test
    fun `un cuerpo que no es un objeto responde 422 con code type y field vacio`() =
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
    fun `un cuerpo vacio se trata como objeto vacio y reporta los requeridos`() =
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
    fun `un cuerpo vacio funciona si todas las propiedades son opcionales`() =
        testApplication {
            application {
                installKastApi { post<AllOptional, AllOptional>("/opts") { it } }
            }

            val response = client.post("/opts")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"a":1,"b":"x"}""", response.bodyAsText())
        }

    @Test
    fun `patch tambien lee el cuerpo`() =
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
    fun `con ignoreUnknownKeys los campos desconocidos se ignoran`() =
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
    fun `enum y mapa validos se decodifican`() =
        testApplication {
            application {
                installKastApi { post<Profile, Profile>("/profiles") { it } }
            }

            val response = client.postJson("/profiles", """{"color":"RED","scores":{"a":1,"b":2}}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("""{"color":"RED","scores":{"a":1,"b":2}}""", response.bodyAsText())
        }

    @Test
    fun `enum desconocido y valor de mapa incorrecto reportan su field`() =
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
    fun `propiedad nullable sin default ausente del cuerpo llega como null`() =
        testApplication {
            application {
                installKastApi { post<NullableBody, NullableBody>("/notes") { it } }
            }

            val response = client.postJson("/notes", "{}")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("{}", response.bodyAsText())
        }

    @Test
    fun `errores de path y de cuerpo se devuelven juntos`() =
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
