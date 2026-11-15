package dev.kastapi

import dev.kastapi.test.kastTest
import dev.kastapi.validation.Min
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class KtItemPath(
    val id: Long,
)

@Serializable
data class KtItemIn(
    @Min(1) val qty: Int,
)

@Serializable
data class KtItemOut(
    val id: Long,
    val qty: Int = 0,
)

/** Uso de `kastTest` (referencia §9) sobre el comportamiento del core. */
class KastTestUsageTest {
    @Test
    fun `kastTest enlaza el parametro de ruta y devuelve el cuerpo tipado`() =
        kastTest({
            get<KtItemPath, KtItemOut>("/items/{id}") { path -> KtItemOut(path.id) }
        }) {
            val item: KtItemOut = client.get("/items/42").bodyAs()
            assertEquals(KtItemOut(42), item)
        }

    @Test
    fun `kastTest responde 201 con cuerpo valido y 422 con cuerpo invalido`() =
        kastTest({
            post<KtItemIn, KtItemOut>("/items", status = 201) { body -> KtItemOut(1, body.qty) }
        }) {
            val created =
                client.post("/items") {
                    contentType(ContentType.Application.Json)
                    setBody(KtItemIn(5))
                }
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(KtItemOut(1, 5), created.bodyAs<KtItemOut>())

            val invalid =
                client.post("/items") {
                    contentType(ContentType.Application.Json)
                    setBody(KtItemIn(0))
                }
            assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        }

    @Test
    fun `kastTest sirve delete 204 y el 404 uniforme para rutas inexistentes`() =
        kastTest({
            delete<KtItemPath, Unit>("/items/{id}", status = 204) { _ -> }
        }) {
            assertEquals(HttpStatusCode.NoContent, client.delete("/items/1").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/nope").status)
        }
}
