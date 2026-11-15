plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Las anotaciones de validación son @SerialInfo: se exponen para que el usuario no las declare aparte.
    api(libs.kotlinx.serialization.json)
}
