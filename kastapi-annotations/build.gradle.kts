plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // The validation annotations are @SerialInfo: they are exposed so users do not have to declare them separately.
    api(libs.kotlinx.serialization.json)
}
