plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// Los ejemplos no forman parte de la API pública (se excluyen de apiCheck/apiDump).
apiValidation {
    ignoredProjects.add("hello")
    // API pública solo por limitaciones de Kotlin entre módulos: no forma parte de la API estable.
    nonPublicMarkers.add("dev.kastapi.openapi.InternalKastApi")
}

// Configuración común a todos los módulos (`:examples` es solo una carpeta contenedora, no un módulo).
configure(subprojects.filter { it.path != ":examples" }) {
    group = "dev.kastapi"
    version = "0.1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        mavenCentral()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.engine)
    }

    dependencies {
        "testImplementation"(rootProject.libs.kotlin.test)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// `core` y `openapi` comparten clases marcadas con @InternalKastApi: ambos se acogen al opt-in.
configure(subprojects.filter { it.path == ":kastapi-core" || it.path == ":kastapi-openapi" }) {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            optIn.add("dev.kastapi.openapi.InternalKastApi")
        }
    }
}

// Módulos de librería: API explícita y warnings como errores ("sin warnings nuevos").
configure(subprojects.filter { it.path.startsWith(":kastapi-") }) {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        explicitApi()
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
}

// Publicación: por ahora solo en el repositorio Maven local (`./gradlew publishToMavenLocal`).
// Maven Central, firma y javadoc quedan para la fase 5 (docs/ROADMAP.md).
configure(subprojects.filter { it.path.startsWith(":kastapi-") }) {
    apply(plugin = "maven-publish")
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("KastAPI: APIs HTTP en Kotlin sobre Ktor, donde los tipos del handler definen validación y OpenAPI.")
                    url.set("https://github.com/josedlujan/kastapi")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        url.set("https://github.com/josedlujan/kastapi")
                        connection.set("scm:git:https://github.com/josedlujan/kastapi.git")
                    }
                }
            }
        }
    }
}
