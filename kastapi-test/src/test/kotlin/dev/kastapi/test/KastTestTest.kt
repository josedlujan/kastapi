package dev.kastapi.test

import dev.kastapi.NoInput
import dev.kastapi.get
import dev.kastapi.post
import dev.kastapi.validation.Min
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get as clientGet
import io.ktor.client.request.post as clientPost

@Serializable
data class Echo(
    @Min(1) val n: Int,
)

class KastTestTest {
    @Test
    fun `kastTest levanta la aplicacion y responde a una ruta declarada`() =
        kastTest({
            get<NoInput, Echo>("/echo") { Echo(7) }
        }) {
            val response = client.clientGet("/echo")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `bodyAs deserializa la respuesta JSON al tipo pedido`() =
        kastTest({
            get<NoInput, Echo>("/echo") { Echo(7) }
        }) {
            val echo: Echo = client.clientGet("/echo").bodyAs()
            assertEquals(Echo(7), echo)
        }

    @Test
    fun `el cliente envia JSON y el servidor valida el cuerpo`() =
        kastTest({
            post<Echo, Echo>("/echo") { body -> body }
        }) {
            val ok =
                client.clientPost("/echo") {
                    contentType(ContentType.Application.Json)
                    setBody(Echo(3))
                }
            assertEquals(Echo(3), ok.bodyAs<Echo>())

            val invalid =
                client.clientPost("/echo") {
                    contentType(ContentType.Application.Json)
                    setBody(Echo(0))
                }
            assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        }
}
