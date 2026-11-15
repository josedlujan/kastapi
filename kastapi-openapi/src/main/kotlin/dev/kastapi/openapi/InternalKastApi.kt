package dev.kastapi.openapi

/**
 * Marca las declaraciones que son públicas solo porque `kastapi-core` y `kastapi-openapi` son módulos distintos
 * (en Kotlin, `internal` no se ve entre módulos).
 *
 * No forman parte de la API estable de KastAPI, no se incluyen en la comprobación de compatibilidad (`apiCheck`)
 * y pueden cambiar sin aviso. Usarlas exige un opt-in explícito.
 */
@RequiresOptIn(
    message = "Esta API es interna de KastAPI y puede cambiar sin aviso.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalKastApi
