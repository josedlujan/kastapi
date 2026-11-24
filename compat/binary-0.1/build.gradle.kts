// Binary compatibility check against the 0.1 line.
//
// The tests in this project are compiled against the released 0.1.1 from Maven Central, so their bytecode
// contains the exact constructor and method signatures that 0.1 consumers have. At run time the 0.1.1 KastAPI
// jars are swapped for the ones built from this checkout. If a signature that 0.1 had is gone, the tests fail
// with NoSuchMethodError.
//
// Run it by hand with: ./gradlew assemble && ./gradlew -p compat/binary-0.1 test -PkastapiVersion=<current version>
plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

val baselineVersion = "0.1.1"
val currentVersion: String = providers.gradleProperty("kastapiVersion").get()

repositories {
    // Central only: the baseline must never resolve to a locally published build.
    mavenCentral()
}

dependencies {
    testImplementation("dev.kastapi:kastapi-core:$baselineVersion")
    testImplementation("io.ktor:ktor-server-test-host:3.6.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

val currentJars =
    listOf("kastapi-core", "kastapi-annotations", "kastapi-openapi").map {
        rootDir.resolve("../../$it/build/libs/$it-$currentVersion.jar").canonicalFile
    }

tasks.test {
    doFirst {
        currentJars.forEach { check(it.isFile) { "Missing $it. Run ./gradlew assemble in the repository root first." } }
    }
    // Replace the baseline KastAPI jars with the ones built from this checkout.
    classpath = classpath.filter { !it.name.startsWith("kastapi-") } + files(currentJars)
    useJUnitPlatform()
}
