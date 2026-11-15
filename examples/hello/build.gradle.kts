plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":kastapi-core"))
    // Logging solo para el ejemplo (la librería no impone binding de SLF4J).
    runtimeOnly(libs.logback.classic)
}
application {
    // Main.kt is the "Hello world" from docs/reference.md, verbatim (default package).
    mainClass.set("MainKt")
}
// The example reproduces the "Hello world" from docs/reference.md verbatim (wildcard imports, one-line classes), so ktlint is not applied to it.
ktlint {
    filter {
        exclude { true }
    }
}
tasks.test {
    dependsOn(tasks.classes)
    systemProperty("reference.path", rootProject.file("docs/reference.md").absolutePath)
    systemProperty("main.path", file("src/main/kotlin/Main.kt").absolutePath)
    systemProperty("hello.classpath", sourceSets.main.get().runtimeClasspath.asPath)
}
