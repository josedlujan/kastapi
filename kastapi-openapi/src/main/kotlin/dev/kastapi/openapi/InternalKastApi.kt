package dev.kastapi.openapi

/**
 * Marks declarations that are public only because `kastapi-core` and `kastapi-openapi` are different modules
 * (in Kotlin, `internal` is not visible across modules).
 *
 * They are not part of KastAPI's stable API, are not included in the compatibility check (`apiCheck`)
 * and may change without notice. Using them requires an explicit opt-in.
 */
@RequiresOptIn(
    message = "This API is internal to KastAPI and may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalKastApi
