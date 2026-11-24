import dev.kastapi.KastApiConfig
import dev.kastapi.NoInput
import dev.kastapi.get
import dev.kastapi.installKastApi
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Pong(
    val ok: Boolean,
)

/** Written the way a 0.1 user wrote it; compiled against 0.1.1. */
class Compiled01Test {
    @Test
    fun `the no-argument constructor of 0_1 still links`() {
        val config = KastApiConfig()
        assertEquals("/docs", config.docsPath)
        assertEquals(1_048_576L, config.maxBodyBytes)
    }

    @Test
    fun `the named-argument constructor of 0_1 still links`() {
        val config = KastApiConfig(docsPath = "/help", maxBodyBytes = 10)
        assertEquals("/help", config.docsPath)
        assertEquals("/openapi.json", config.openApiPath)
        assertEquals(10L, config.maxBodyBytes)
    }

    @Test
    fun `the full constructor of 0_1 still links`() {
        val json = Json { ignoreUnknownKeys = true }
        val config = KastApiConfig("/a", "/b", json, false, 7L)
        assertEquals("/a", config.docsPath)
        assertEquals("/b", config.openApiPath)
        assertEquals(json, config.json)
        assertEquals(false, config.validateOnStartup)
        assertEquals(7L, config.maxBodyBytes)
    }

    @Test
    fun `a configuration built the 0_1 way works in an application`() =
        testApplication {
            application {
                installKastApi(KastApiConfig(docsPath = "/help")) {
                    get<NoInput, Pong>("/ping") { Pong(true) }
                }
            }
            assertEquals(HttpStatusCode.OK, client.get("/ping").status)
            assertEquals(HttpStatusCode.OK, client.get("/help").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/docs").status)
        }
}
