// A minimal application that consumes KastAPI with the oldest Kotlin version it supports.
// CI publishes the modules to the local Maven repository and then compiles this project against them.
// Run it by hand with: ./gradlew publishToMavenLocal && ./gradlew -p compat/kotlin-2.3 compileKotlin
plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

val kastapiVersion: String = providers.gradleProperty("kastapiVersion").orElse("0.2.1").get()

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.kastapi:kastapi-core:$kastapiVersion")
    testImplementation("dev.kastapi:kastapi-test:$kastapiVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
