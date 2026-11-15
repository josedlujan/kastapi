# 6. Integrating with Ktor and testing

## Using KastAPI inside a Ktor application

`kastApi(...)` starts its own server. If you already have a Ktor application, use `installKastApi`:

```kotlin
fun Application.module() {
    install(StatusPages) { /* your handlers */ }

    routing {
        get("/ping") { call.respondText("pong") }
    }

    installKastApi(KastApiConfig(docsPath = "/api/docs", openApiPath = "/api/openapi.json")) {
        get<NoInput, Health>("/health") { Health("ok") }
    }
}
```

`kastApi(...)` is just a Netty server that calls `installKastApi`, so they behave the same. You can mix Ktor and KastAPI routes, and install other plugins (CORS, authentication, etc.) directly with Ktor.

Rules:

- If you already installed `StatusPages`, KastAPI does not install its own (see [Errors](04-errors.md)). Do it before `installKastApi`.
- Inside the handler, `call` is Ktor's `ApplicationCall`.

## Testing with `kastTest`

Add the test module:

```kotlin
testImplementation("dev.kastapi:kastapi-test:0.1.0")
testImplementation(kotlin("test"))
```

`kastTest` starts your API in memory (no ports are opened) and gives you an HTTP client already configured for JSON:

```kotlin
import dev.kastapi.test.kastTest
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun `health responds ok`() =
        kastTest({
            get<NoInput, Health>("/health") { Health("ok") }
        }) {
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Health("ok"), response.bodyAs<Health>())
        }
}
```

- The first block declares the routes, just like in `kastApi { }`.
- The second one is the test. Its receiver exposes `client` and `bodyAs<T>()`.
- To test with another configuration, install your real application with `testApplication { application { module() } }`.

## What 0.1.0 does not include

Dependency injection, authentication, typed headers and cookies, forms and files, WebSockets, client generation and API versioning. They are in the [roadmap](../ROADMAP.md).
