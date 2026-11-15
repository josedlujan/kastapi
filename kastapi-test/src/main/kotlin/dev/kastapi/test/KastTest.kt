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
 * Test client that the [kastTest] block receives.
 *
 * @property client Ktor HTTP client, already configured for JSON (the same [KastApiConfig.json] the server uses).
 */
public class KastTestClient internal constructor(
    public val client: HttpClient,
) {
    /** Deserializes the response body into type [T]. */
    public suspend inline fun <reified T> HttpResponse.bodyAs(): T = body()
}

/**
 * Starts a KastAPI application in memory (`testApplication` + `installKastApi`) and runs [test].
 *
 * ```kotlin
 * kastTest({
 *     get<UserPath, UserResponse>("/users/{id}") { path -> UserResponse(path.id) }
 * }) {
 *     val user: UserResponse = client.get("/users/1").bodyAs()
 * }
 * ```
 *
 * @param block route declarations, just like in `kastApi { ... }`.
 * @param test test that runs with a [KastTestClient].
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
