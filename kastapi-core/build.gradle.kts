plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // ADR-0004: core exposes the annotations with api(...) so users do not have to declare them separately.
    api(project(":kastapi-annotations"))
    implementation(project(":kastapi-openapi"))

    api(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(project(":kastapi-test"))
}
