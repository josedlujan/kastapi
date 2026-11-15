plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// The examples are not part of the public API (they are excluded from apiCheck/apiDump).
apiValidation {
    ignoredProjects.add("hello")
    // Public only because of Kotlin limitations across modules: not part of the stable API.
    nonPublicMarkers.add("dev.kastapi.openapi.InternalKastApi")
}

// Configuration common to all modules (`:examples` is only a container folder, not a module).
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

// `core` and `openapi` share classes marked with @InternalKastApi: both opt in.
configure(subprojects.filter { it.path == ":kastapi-core" || it.path == ":kastapi-openapi" }) {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            optIn.add("dev.kastapi.openapi.InternalKastApi")
        }
    }
}

// Library modules: explicit API and warnings as errors ("no new warnings").
configure(subprojects.filter { it.path.startsWith(":kastapi-") }) {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        explicitApi()
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
}

// Publishing: for now only to the local Maven repository (`./gradlew publishToMavenLocal`).
// Maven Central, signing and javadoc are left for phase 5 (docs/ROADMAP.md).
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
                    description.set("KastAPI: HTTP APIs in Kotlin on top of Ktor, where the handler's types define validation and OpenAPI.")
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
