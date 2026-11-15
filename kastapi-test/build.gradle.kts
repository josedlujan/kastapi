plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":kastapi-core"))
    api(libs.ktor.server.test.host)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
