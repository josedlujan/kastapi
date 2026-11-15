package dev.kastapi.test

import dev.kastapi.KastApi
import dev.kastapi.KastApiConfig
import dev.kastapi.installKastApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication

/**
 * Cliente de pruebas que recibe el bloque de [kastTest].
 *
 * @property client cliente HTTP de Ktor, ya configurado con JSON (el mismo [KastApiConfig.json] que usa el servidor).
 */
public class KastTestClient internal constructor(
    public val client: HttpClient,
) {
    /** Deserializa el cuerpo de la respuesta al tipo [T]. */
    public suspend inline fun <reified T> HttpResponse.bodyAs(): T = body()
}

/**
 * Levanta una aplicación KastAPI en memoria (`testApplication` + `installKastApi`) y ejecuta [test].
 *
 * ```kotlin
 * kastTest({
 *     get<UserPath, UserResponse>("/users/{id}") { path -> UserResponse(path.id) }
 * }) {
 *     val user: UserResponse = client.get("/users/1").bodyAs()
 * }
 * ```
 *
 * @param block declaración de rutas, igual que en `kastApi { ... }`.
 * @param test prueba que se ejecuta con un [KastTestClient].
 */
public fun kastTest(
    block: KastApi.() -> Unit,
    test: suspend KastTestClient.() -> Unit,
) {
    val config = KastApiConfig()
    testApplication {
        application { installKastApi(config, block) }
        val client =
            createClient {
                install(ContentNegotiation) { json(config.json) }
            }
        KastTestClient(client).test()
    }
}
