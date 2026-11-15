package dev.kastapi

import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Min
import dev.kastapi.validation.NotBlank
import dev.kastapi.validation.Pattern
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
data class NullablePath(
    val id: Long?,
)

@Serializable
data class DefaultPath(
    val id: Long = 1,
)

@Serializable
data class ListInPath(
    val ids: List<String>,
)

@Serializable
data class UserKey(
    val key: Long,
)

@Serializable
data class BadMin(
    @Min(1) val name: String,
)

@Serializable
data class BadNotBlank(
    @NotBlank val count: Int,
)

@Serializable
data class BadLength(
    @Length(max = 3) val n: Int,
)

@Serializable
data class BadPattern(
    @Pattern("a") val n: Int,
)

@Serializable
data class NestedBad(
    val inner: VIncompatible,
)

@Serializable
data class BadRegex(
    @Pattern("[abc") val code: String,
)

@Serializable
data class GoodModel(
    @Min(1) val id: Long,
    @Email val email: String,
    @NotBlank @Length(max = 20) val name: String,
    @Pattern("\\d+") val digits: String,
    val tags: List<String> = emptyList(),
)

/** Starts an application with [block] and returns the configuration exception that `installKastApi` throws. */
private fun assertStartupFails(
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
): KastApiConfigurationException {
    var failure: KastApiConfigurationException? = null
    testApplication {
        application {
            failure = assertFailsWith<KastApiConfigurationException> { installKastApi(config, block) }
        }

        // Forces the application to start so that the previous block runs.
        client.get("/")
    }
    return checkNotNull(failure)
}

/** Starts an application with [block]; the test fails if `installKastApi` throws an exception. */
private fun assertStartupSucceeds(
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
) {
    testApplication {
        application { installKastApi(config, block) }

        client.get("/")
    }
}

private fun KastApiConfigurationException.assertMentions(vararg fragments: String) {
    val text = message.orEmpty()
    for (fragment in fragments) {
        assertTrue(text.contains(fragment), "the message must contain '$fragment' but was:\n$text")
    }
}

class StartupValidationTest {
    @Test
    fun `a route parameter without a property in the input fails and names the route and the parameter`() {
        val error = assertStartupFails { get<UserPath, Health>("/users/{userId}") { Health("x") } }

        error.assertMentions("GET /users/{userId}", "userId", "UserPath")
    }

    @Test
    fun `a route parameter with NoInput fails`() {
        val error = assertStartupFails { get<NoInput, Health>("/users/{id}") { Health("x") } }

        error.assertMentions("GET /users/{id}", "id", "NoInput")
    }

    @Test
    fun `a nullable path property fails`() {
        val error = assertStartupFails { get<NullablePath, Health>("/n/{id}") { Health("x") } }

        error.assertMentions("GET /n/{id}", "id", "nullable")
    }

    @Test
    fun `a path property with a default value fails`() {
        val error = assertStartupFails { get<DefaultPath, Health>("/d/{id}") { Health("x") } }

        error.assertMentions("GET /d/{id}", "id", "default value")
    }

    @Test
    fun `a nested object in query fails and mentions the route and the property`() {
        val error = assertStartupFails { get<WithNested, Health>("/x") { Health("x") } }

        error.assertMentions("GET /x", "inner")
    }

    @Test
    fun `a list in the path fails and mentions the property`() {
        val error = assertStartupFails { get<ListInPath, Health>("/l/{ids}") { Health("x") } }

        error.assertMentions("GET /l/{ids}", "ids")
    }

    @Test
    fun `two routes with the same method and the same path fail`() {
        val error =
            assertStartupFails {
                get<NoInput, Health>("/a") { Health("uno") }
                get<NoInput, Health>("/a") { Health("dos") }
            }

        error.assertMentions("GET /a", "duplicate")
    }

    @Test
    fun `two routes with the same pattern and a different parameter name count as duplicates`() {
        val error =
            assertStartupFails {
                get<UserPath, Health>("/u/{id}") { Health("uno") }
                get<UserKey, Health>("/u/{key}") { Health("dos") }
            }

        error.assertMentions("GET /u/{key}", "duplicate")
    }

    @Test
    fun `a group with the same prefix as another route counts as a duplicate`() {
        val error =
            assertStartupFails {
                group("/v1") { get<NoInput, Health>("/a") { Health("uno") } }
                get<NoInput, Health>("/v1/a") { Health("dos") }
            }

        error.assertMentions("GET /v1/a", "duplicate")
    }

    @Test
    fun `the same route with a different method is not a duplicate`() {
        assertStartupSucceeds {
            get<NoInput, Health>("/a") { Health("get") }
            post<NoInput, Health>("/a") { Health("post") }
        }
    }

    @Test
    fun `an annotation on an incompatible type fails and names the annotation and the property`() {
        val error = assertStartupFails { post<VIncompatible, VIncompatible>("/inc") { it } }

        error.assertMentions("POST /inc", "@Email", "VIncompatible.n")
    }

    @Test
    fun `Min on a String and NotBlank on an Int and Length on an Int and Pattern on an Int fail`() {
        val error =
            assertStartupFails {
                post<BadMin, BadMin>("/min") { it }
                post<BadNotBlank, BadNotBlank>("/nb") { it }
                post<BadLength, BadLength>("/len") { it }
                post<BadPattern, BadPattern>("/pt") { it }
            }

        error.assertMentions("@Min", "@NotBlank", "@Length", "@Pattern", "4 configuration problems")
    }

    @Test
    fun `an incompatible annotation inside a nested class fails`() {
        val error = assertStartupFails { post<NestedBad, NestedBad>("/nested") { it } }

        error.assertMentions("POST /nested", "@Email", "VIncompatible.n")
    }

    @Test
    fun `a valid configuration throws no exception`() {
        assertStartupSucceeds {
            get<NoInput, Health>("/health") { Health("ok") }
            get<UserPath, Health>("/users/{id}") { Health("x") }
            get<VLimitQuery, VLimitQuery>("/items") { it }
            post<GoodModel, GoodModel>("/models") { it }
            put<UpdateUser, UpdateUser>("/users/{id}") { it }
            group("/v1") {
                delete<UserPath, Unit>("/users/{id}", status = 204) { }
            }
        }
    }

    @Test
    fun `several problems are reported together in a single message`() {
        val error =
            assertStartupFails {
                get<UserPath, Health>("/users/{userId}") { Health("x") }
                get<WithNested, Health>("/x") { Health("x") }
                post<VIncompatible, VIncompatible>("/inc") { it }
            }

        error.assertMentions(
            "3 configuration problems",
            "GET /users/{userId}",
            "GET /x",
            "POST /inc",
        )
    }

    @Test
    fun `with validateOnStartup set to false route consistency is not checked`() {
        val config = KastApiConfig(validateOnStartup = false)

        assertStartupSucceeds(config) {
            get<NoInput, Health>("/a") { Health("uno") }
            get<NoInput, Health>("/a") { Health("dos") }
            get<UserPath, Health>("/users/{userId}") { Health("x") }
            post<VIncompatible, VIncompatible>("/inc") { it }
        }
    }

    @Test
    fun `with validateOnStartup set to false an unsupported type still fails`() {
        val config = KastApiConfig(validateOnStartup = false)

        val error = assertStartupFails(config) { get<WithNested, Health>("/x") { Health("x") } }

        error.assertMentions("GET /x", "inner")
    }

    @Test
    fun `an invalid regex in Pattern is reported as a problem with the property`() {
        val error = assertStartupFails { post<BadRegex, BadRegex>("/re") { it } }

        error.assertMentions("POST /re", "@Pattern", "BadRegex.code", "[abc")
    }
}
