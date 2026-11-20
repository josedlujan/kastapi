# KastAPI

**Your handler's signature is your contract.** Validation, clear errors and OpenAPI documentation for Kotlin APIs, without writing anything extra. Built on [Ktor](https://ktor.io).

> Status: v0.1.1, experimental. The API may change before 1.0.

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

KastAPI is published on [Maven Central](https://central.sonatype.com/namespace/dev.kastapi) under the `dev.kastapi`
group. It requires JDK 21 and Kotlin 2.3 or newer. In your project (Gradle Kotlin DSL):

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.kastapi:kastapi-core:0.1.1")
    // Optional: see the server logs (KastAPI does not ship an SLF4J implementation).
    runtimeOnly("ch.qos.logback:logback-classic:1.6.7")
    testImplementation("dev.kastapi:kastapi-test:0.1.1")
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
3. Save a minimal Logback configuration in `src/main/resources/logback.xml`. Without it, Logback prints very verbose debug output from Netty:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

4. Run `./gradlew run` (you need the Gradle 9 wrapper or an installed Gradle 9).
5. Open http://localhost:8080/docs, expand `POST /users`, click **Try it out** and send an invalid body, for example `{"email": "x", "age": 15}`. You get a `422` with both errors at once.

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

## Why KastAPI?

[Ktor](https://ktor.io) is a great foundation, and KastAPI runs on top of it: you keep your Ktor routes, plugins and
engine. You can build everything below with plain Ktor. KastAPI's point is that **you declare it once**, in the
types of the handler.

| | Plain Ktor | KastAPI |
| --- | --- | --- |
| Reading path, query and body | `call.parameters`, `receive<T>()` and manual conversion | Taken from the input `data class` |
| Validation | The `RequestValidation` plugin or your own code, rule by rule | Annotations on the model (`@Min`, `@Email`...) |
| Error format | You design it with `StatusPages` | One standard format that reports every problem at once |
| OpenAPI | Generated from your routing code with Ktor's compiler plugin (3.4+), or written by hand | Generated at runtime from the same types, including your validation rules; no extra compiler plugin |
| Configuration mistakes | Found when the endpoint is called | Found at startup |

**It is probably for you if** you come from FastAPI or Android and want a typed backend without learning Spring,
or if your team keeps validation, error responses and API docs in sync by hand today.

**It is probably not for you if** you need what 0.1 does not have yet (built-in authentication, dependency
injection, typed headers and cookies, file uploads, WebSockets), if your current Ktor setup already works well, or
if you need a stable API today: KastAPI is experimental, has one maintainer and may change before 1.0. When you need
something it lacks, you can drop down to plain Ktor at any point (`installKastApi`, `KastCall.call`).

## Documentation

The full guide is in [`docs/guide`](docs/guide):

1. [Getting started](docs/guide/01-getting-started.md)
2. [Parameters: path, query and body](docs/guide/02-parameters.md)
3. [Validation](docs/guide/03-validation.md)
4. [Errors](docs/guide/04-errors.md)
5. [OpenAPI and Swagger UI](docs/guide/05-openapi.md)
6. [Integrating with Ktor and testing](docs/guide/06-ktor-integration.md)

The project status, known limitations and what is missing for 1.0 are in [`docs/STATUS.md`](docs/STATUS.md). The API reference is in [`docs/reference.md`](docs/reference.md) and the architecture in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Look for issues labeled `good first issue`.

## License

Apache 2.0
