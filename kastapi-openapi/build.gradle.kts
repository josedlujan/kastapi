plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":kastapi-annotations"))
    implementation(libs.kotlinx.serialization.json)
}
