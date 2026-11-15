# 6. Integrar con Ktor y probar

## Usar KastAPI dentro de una aplicación Ktor

`kastApi(...)` arranca su propio servidor. Si ya tienes una aplicación Ktor, usa `installKastApi`:

```kotlin
fun Application.module() {
    install(StatusPages) { /* tus manejadores */ }

    routing {
        get("/ping") { call.respondText("pong") }
    }

    installKastApi(KastApiConfig(docsPath = "/api/docs", openApiPath = "/api/openapi.json")) {
        get<NoInput, Health>("/health") { Health("ok") }
    }
}
```

`kastApi(...)` es solo un servidor Netty que llama a `installKastApi`, así que se comportan igual. Puedes mezclar rutas de Ktor y de KastAPI, e instalar otros plugins (CORS, autenticación, etc.) directamente con Ktor.

Reglas:

- Si ya instalaste `StatusPages`, KastAPI no instala el suyo (ver [Errores](04-errores.md)). Hazlo antes de `installKastApi`.
- Dentro del handler, `call` es el `ApplicationCall` de Ktor.

## Pruebas con `kastTest`

Añade el módulo de pruebas:

```kotlin
testImplementation("dev.kastapi:kastapi-test:0.1.0")
testImplementation(kotlin("test"))
```

`kastTest` levanta tu API en memoria (sin abrir puertos) y te da un cliente HTTP ya configurado con JSON:

```kotlin
import dev.kastapi.test.kastTest
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun `health responde ok`() =
        kastTest({
            get<NoInput, Health>("/health") { Health("ok") }
        }) {
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Health("ok"), response.bodyAs<Health>())
        }
}
```

- El primer bloque declara las rutas, igual que en `kastApi { }`.
- El segundo es la prueba. Su receptor expone `client` y `bodyAs<T>()`.
- Para probar con otra configuración, instala tu aplicación real con `testApplication { application { module() } }`.

## Qué no incluye 0.1.0

Inyección de dependencias, autenticación, headers y cookies tipados, formularios y archivos, WebSockets, generación de clientes y versionado de API. Están en el [roadmap](../ROADMAP.md).
