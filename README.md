# KastAPI

**Your handler's signature is your contract.** Validation, clear errors and OpenAPI documentation for Kotlin APIs, without writing anything extra. Built on [Ktor](https://ktor.io).

> Status: v0.1.0, experimental. The API may change before 1.0.

> **A note on language.** KastAPI was designed and first written in Spanish, and then translated to English. Most of it has been translated, but some parts may still be in Spanish or read awkwardly. If you find something, please [open an issue](https://github.com/josedlujan/kastapi/issues) or send a pull request. Translation fixes are very welcome and a good way to make a first contribution. [Versión en español](README.es.md).

## Hello world

```kotlin
import dev.kastapi.*
import dev.kastapi.validation.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUser(@Email val email: String, @Min(18) val age: Int)

@Serializable
data class User(val id: Long, val email: String)

fun main() = kastApi(port = 8080) {
    post<CreateUser, User>("/users") { body -> User(1, body.email) }
}
```

Open http://localhost:8080/docs and you get Swagger UI with the endpoint documented and validated.

## Installation

KastAPI 0.1.0 is not on Maven Central yet. Install it into your local Maven repository (requires JDK 21):

```bash
git clone https://github.com/josedlujan/kastapi.git
cd kastapi
./gradlew publishToMavenLocal
```

In your project (Gradle Kotlin DSL):

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.kastapi:kastapi-core:0.1.0")
    // Optional: see the server logs (KastAPI does not ship an SLF4J implementation).
    runtimeOnly("ch.qos.logback:logback-classic:1.6.6")
    testImplementation("dev.kastapi:kastapi-test:0.1.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}
```

## Try it in 5 minutes

1. Create a project with the `build.gradle.kts` above and a `settings.gradle.kts` with `rootProject.name = "demo"`.
2. Save the "Hello world" code in `src/main/kotlin/Main.kt`.
3. Run `./gradlew run` (you need the Gradle 9 wrapper or an installed Gradle 9).
4. Open http://localhost:8080/docs, expand `POST /users`, click **Try it out** and send an invalid body, for example `{"email": "x", "age": 15}`. You get a `422` with both errors at once.

Or from the terminal:

```bash
curl -s -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"email": "x", "age": 15}'
```

```json
{
  "status": 422,
  "error": "validation_error",
  "message": "The request has 2 validation errors",
  "details": [
    { "location": "body", "field": "email", "code": "email", "message": "must be a valid email address", "value": "x" },
    { "location": "body", "field": "age", "code": "min", "message": "must be greater than or equal to 18", "value": 15 }
  ]
}
```

Swagger UI is loaded from a CDN, so it needs an internet connection. The schema at `http://localhost:8080/openapi.json` is always served.

## What you get

- Path, query and body parameters from a `data class`.
- Declarative validation: `@Min`, `@Max`, `@Length`, `@NotBlank`, `@Email`, `@Pattern`.
- `422` errors that report every problem at once, in a stable format.
- OpenAPI 3.1 at `/openapi.json` and Swagger UI at `/docs`.
- Configuration errors detected at startup, not in production.
- Integration with existing Ktor apps: `installKastApi { }`.
- Testing with `kastTest` (the `kastapi-test` module).

## Documentation

The full guide is in [`docs/guide`](docs/guide):

1. [Getting started](docs/guide/01-primeros-pasos.md)
2. [Parameters: path, query and body](docs/guide/02-parametros.md)
3. [Validation](docs/guide/03-validacion.md)
4. [Errors](docs/guide/04-errores.md)
5. [OpenAPI and Swagger UI](docs/guide/05-openapi.md)
6. [Integrating with Ktor and testing](docs/guide/06-integrar-con-ktor.md)

The project status, known limitations and what is missing for 1.0 are in [`docs/STATUS.md`](docs/STATUS.md). The API reference is in [`docs/reference.md`](docs/reference.md) and the architecture in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Look for issues labeled `good first issue`.

## License

Apache 2.0
