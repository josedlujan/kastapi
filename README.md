# KastAPI

**La firma de tu handler es tu contrato.** Validación, errores claros y documentación OpenAPI para APIs en Kotlin, sin escribir nada extra. Construido sobre [Ktor](https://ktor.io).

> Estado: v0.1.0, experimental. La API puede cambiar antes de 1.0.

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

KastAPI 0.1.0 todavía no está en Maven Central. Se instala en tu repositorio Maven local (requiere JDK 21):

```bash
git clone https://github.com/josedlujan/kastapi.git
cd kastapi
./gradlew publishToMavenLocal
```

En tu proyecto (Gradle Kotlin DSL):

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
    // Opcional: ver los logs del servidor (KastAPI no incluye una implementación de SLF4J).
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

## Pruébalo en 5 minutos

1. Crea un proyecto con el `build.gradle.kts` anterior y `settings.gradle.kts` con `rootProject.name = "demo"`.
2. Guarda el código de "Hola mundo" en `src/main/kotlin/Main.kt`.
3. Ejecuta `./gradlew run` (necesitas el wrapper de Gradle 9 o un Gradle 9 instalado).
4. Abre http://localhost:8080/docs, despliega `POST /users`, pulsa **Try it out** y envía un cuerpo inválido, por ejemplo `{"email": "x", "age": 15}`. Recibirás un `422` con los dos errores a la vez.

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

## Documentación

Guía completa en [`docs/guide`](docs/guide):

1. [Primeros pasos](docs/guide/01-primeros-pasos.md)
2. [Parámetros: path, query y body](docs/guide/02-parametros.md)
3. [Validación](docs/guide/03-validacion.md)
4. [Errores](docs/guide/04-errores.md)
5. [OpenAPI y Swagger UI](docs/guide/05-openapi.md)
6. [Integrar con Ktor y probar](docs/guide/06-integrar-con-ktor.md)

El estado del proyecto, las limitaciones conocidas y lo que falta para 1.0 están en [`docs/STATUS.md`](docs/STATUS.md). La referencia de la API está en [`docs/reference.md`](docs/reference.md) y la arquitectura en [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Contribuir

Lee [CONTRIBUTING.md](CONTRIBUTING.md). Busca issues con la etiqueta `good first issue`.

## Licencia

Apache 2.0
