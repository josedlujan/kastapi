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
    // Main.kt es el "Hola mundo" de docs/reference.md tal cual (paquete por defecto).
    mainClass.set("MainKt")
}
// El ejemplo reproduce el "Hola mundo" de docs/reference.md literalmente (imports con comodín, clases en una línea): no se le aplica ktlint.
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
