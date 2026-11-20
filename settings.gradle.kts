pluginManagement {
    repositories {
        gradlePluginPortal()
        // Fallback: the plugin portal occasionally fails to proxy artifacts that Maven Central does have.
        mavenCentral()
    }
}

rootProject.name = "kastapi"

include(
    "kastapi-annotations",
    "kastapi-core",
    "kastapi-openapi",
    "kastapi-test",
    "examples:hello",
)
