# KastAPI

**La firma de tu handler es tu contrato.** Validación, errores claros y documentación OpenAPI para APIs en Kotlin, sin escribir nada extra. Construido sobre [Ktor](https://ktor.io).

> Estado: v0.2.1, experimental. La API puede cambiar antes de 1.0.

> [English version](README.md). Este proyecto se diseñó y escribió primero en español y después se tradujo al inglés. La mayor parte de la documentación está ya en inglés y puede que quede algo sin traducir: si lo encuentras, abre un [issue](https://github.com/josedlujan/kastapi/issues) o envía un pull request. Los mensajes de error de la API están en inglés.

## Hola mundo

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

Abre http://localhost:8080/docs y ya tienes Swagger UI con el endpoint documentado y validado.

## Instalación

KastAPI está publicado en [Maven Central](https://central.sonatype.com/namespace/dev.kastapi) con el grupo
`dev.kastapi`. Requiere JDK 21 y Kotlin 2.3 o superior. En tu proyecto (Gradle Kotlin DSL):

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
    implementation("dev.kastapi:kastapi-core:0.2.1")
    // Opcional: ver los logs del servidor (KastAPI no incluye una implementación de SLF4J).
    runtimeOnly("ch.qos.logback:logback-classic:1.6.8")
    testImplementation("dev.kastapi:kastapi-test:0.2.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}
```

## Pruébalo en 5 minutos

1. Crea un proyecto con el `build.gradle.kts` anterior y `settings.gradle.kts` con `rootProject.name = "demo"`.
2. Guarda el código de "Hola mundo" en `src/main/kotlin/Main.kt`.
3. Guarda una configuración mínima de Logback en `src/main/resources/logback.xml`. Sin ella, Logback imprime muchísima salida de depuración de Netty:

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

4. Ejecuta `./gradlew run` (necesitas el wrapper de Gradle 9 o un Gradle 9 instalado).
5. Abre http://localhost:8080/docs, despliega `POST /users`, pulsa **Try it out** y envía un cuerpo inválido, por ejemplo `{"email": "x", "age": 15}`. Recibirás un `422` con los dos errores a la vez.

O desde la terminal:

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

Swagger UI se carga desde un CDN, así que necesita conexión a internet. El esquema `http://localhost:8080/openapi.json` se sirve siempre.

## Qué obtienes

- Path, query y body desde una `data class`.
- Validación declarativa: `@Min`, `@Max`, `@Length`, `@NotBlank`, `@Email`, `@Pattern`.
- Errores `422` con todos los problemas, en un formato estable.
- OpenAPI 3.1 en `/openapi.json` y Swagger UI en `/docs`.
- Errores de configuración detectados al arrancar, no en producción.
- Integración con apps Ktor existentes: `installKastApi { }`.
- Pruebas con `kastTest` (módulo `kastapi-test`).

## ¿Por qué KastAPI?

[Ktor](https://ktor.io) es una gran base, y KastAPI corre encima de él: conservas tus rutas, plugins y motor de Ktor.
Todo lo que sigue se puede hacer con Ktor solo. La idea de KastAPI es que **lo declaras una vez**, en los tipos del
handler.

| | Ktor solo | KastAPI |
| --- | --- | --- |
| Leer path, query y body | `call.parameters`, `receive<T>()` y conversión a mano | Sale de la `data class` de entrada |
| Validación | El plugin `RequestValidation` o código propio, regla por regla | Anotaciones en el modelo (`@Min`, `@Email`...) |
| Formato de errores | Lo diseñas tú con `StatusPages` | Un formato estándar que reporta todos los problemas a la vez |
| OpenAPI | Se genera desde tu código de rutas con el plugin de compilador de Ktor (3.4+), o se escribe a mano | Se genera en ejecución desde los mismos tipos, incluidas tus reglas de validación; sin plugin de compilador adicional |
| Errores de configuración | Se descubren al llamar al endpoint | Se descubren al arrancar |

**Probablemente es para ti si** vienes de FastAPI o de Android y quieres un backend tipado sin aprender Spring, o si
tu equipo mantiene hoy a mano la validación, las respuestas de error y la documentación de la API.

**Probablemente no es para ti si** necesitas lo que la 0.1 todavía no tiene (autenticación integrada, inyección de
dependencias, headers y cookies tipados, subida de archivos, WebSockets), si tu configuración actual de Ktor ya
funciona bien, o si necesitas una API estable hoy: KastAPI es experimental, tiene un solo mantenedor y puede cambiar
antes de la 1.0. Cuando te falte algo, puedes bajar a Ktor puro en cualquier momento (`installKastApi`,
`KastCall.call`).

## Documentación

Guía completa en [`docs/guide`](docs/guide) (en inglés):

1. [Primeros pasos](docs/guide/01-getting-started.md)
2. [Parámetros: path, query y body](docs/guide/02-parameters.md)
3. [Validación](docs/guide/03-validation.md)
4. [Errores](docs/guide/04-errors.md)
5. [OpenAPI y Swagger UI](docs/guide/05-openapi.md)
6. [Integrar con Ktor y probar](docs/guide/06-ktor-integration.md)
7. [Logs y trazado de peticiones](docs/guide/07-logging.md)

El estado del proyecto, las limitaciones conocidas y lo que falta para 1.0 están en [`docs/STATUS.md`](docs/STATUS.md). La referencia de la API está en [`docs/reference.md`](docs/reference.md) y la arquitectura en [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Contribuir

Lee [CONTRIBUTING.md](CONTRIBUTING.md). Busca issues con la etiqueta `good first issue`.

## Licencia

Apache 2.0
