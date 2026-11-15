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

/** Arranca una aplicación con [block] y devuelve la excepción de configuración que lanza `installKastApi`. */
private fun assertStartupFails(
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
): KastApiConfigurationException {
    var failure: KastApiConfigurationException? = null
    testApplication {
        application {
            failure = assertFailsWith<KastApiConfigurationException> { installKastApi(config, block) }
        }

        // Fuerza el arranque de la aplicación para que se ejecute el bloque anterior.
        client.get("/")
    }
    return checkNotNull(failure)
}

/** Arranca una aplicación con [block]; la prueba falla si `installKastApi` lanza una excepción. */
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
        assertTrue(text.contains(fragment), "el mensaje debe contener '$fragment' pero fue:\n$text")
    }
}

class StartupValidationTest {
    @Test
    fun `un parametro de la ruta sin propiedad en la entrada falla y nombra la ruta y el parametro`() {
        val error = assertStartupFails { get<UserPath, Health>("/users/{userId}") { Health("x") } }

        error.assertMentions("GET /users/{userId}", "userId", "UserPath")
    }

    @Test
    fun `un parametro de la ruta con NoInput falla`() {
        val error = assertStartupFails { get<NoInput, Health>("/users/{id}") { Health("x") } }

        error.assertMentions("GET /users/{id}", "id", "NoInput")
    }

    @Test
    fun `una propiedad de path nullable falla`() {
        val error = assertStartupFails { get<NullablePath, Health>("/n/{id}") { Health("x") } }

        error.assertMentions("GET /n/{id}", "id", "nullable")
    }

    @Test
    fun `una propiedad de path con valor por defecto falla`() {
        val error = assertStartupFails { get<DefaultPath, Health>("/d/{id}") { Health("x") } }

        error.assertMentions("GET /d/{id}", "id", "default value")
    }

    @Test
    fun `un objeto anidado en query falla y menciona la ruta y la propiedad`() {
        val error = assertStartupFails { get<WithNested, Health>("/x") { Health("x") } }

        error.assertMentions("GET /x", "inner")
    }

    @Test
    fun `una lista en el path falla y menciona la propiedad`() {
        val error = assertStartupFails { get<ListInPath, Health>("/l/{ids}") { Health("x") } }

        error.assertMentions("GET /l/{ids}", "ids")
    }

    @Test
    fun `dos rutas con el mismo metodo y la misma ruta fallan`() {
        val error =
            assertStartupFails {
                get<NoInput, Health>("/a") { Health("uno") }
                get<NoInput, Health>("/a") { Health("dos") }
            }

        error.assertMentions("GET /a", "duplicate")
    }

    @Test
    fun `dos rutas con el mismo patron y distinto nombre de parametro cuentan como duplicadas`() {
        val error =
            assertStartupFails {
                get<UserPath, Health>("/u/{id}") { Health("uno") }
                get<UserKey, Health>("/u/{key}") { Health("dos") }
            }

        error.assertMentions("GET /u/{key}", "duplicate")
    }

    @Test
    fun `un grupo con el mismo prefijo que otra ruta cuenta como duplicado`() {
        val error =
            assertStartupFails {
                group("/v1") { get<NoInput, Health>("/a") { Health("uno") } }
                get<NoInput, Health>("/v1/a") { Health("dos") }
            }

        error.assertMentions("GET /v1/a", "duplicate")
    }

    @Test
    fun `la misma ruta con distinto metodo no es un duplicado`() {
        assertStartupSucceeds {
            get<NoInput, Health>("/a") { Health("get") }
            post<NoInput, Health>("/a") { Health("post") }
        }
    }

    @Test
    fun `una anotacion en un tipo incompatible falla y nombra la anotacion y la propiedad`() {
        val error = assertStartupFails { post<VIncompatible, VIncompatible>("/inc") { it } }

        error.assertMentions("POST /inc", "@Email", "VIncompatible.n")
    }

    @Test
    fun `Min en un String NotBlank en un Int Length en un Int y Pattern en un Int fallan`() {
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
    fun `una anotacion incompatible dentro de una clase anidada falla`() {
        val error = assertStartupFails { post<NestedBad, NestedBad>("/nested") { it } }

        error.assertMentions("POST /nested", "@Email", "VIncompatible.n")
    }

    @Test
    fun `una configuracion valida no lanza ninguna excepcion`() {
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
    fun `varios problemas se reportan juntos en un solo mensaje`() {
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
    fun `con validateOnStartup en false no se comprueba la consistencia de las rutas`() {
        val config = KastApiConfig(validateOnStartup = false)

        assertStartupSucceeds(config) {
            get<NoInput, Health>("/a") { Health("uno") }
            get<NoInput, Health>("/a") { Health("dos") }
            get<UserPath, Health>("/users/{userId}") { Health("x") }
            post<VIncompatible, VIncompatible>("/inc") { it }
        }
    }

    @Test
    fun `con validateOnStartup en false un tipo no soportado sigue fallando`() {
        val config = KastApiConfig(validateOnStartup = false)

        val error = assertStartupFails(config) { get<WithNested, Health>("/x") { Health("x") } }

        error.assertMentions("GET /x", "inner")
    }

    @Test
    fun `un regex invalido en Pattern se reporta como problema con la propiedad`() {
        val error = assertStartupFails { post<BadRegex, BadRegex>("/re") { it } }

        error.assertMentions("POST /re", "@Pattern", "BadRegex.code", "[abc")
    }
}
