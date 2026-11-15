package dev.kastapi

/** Datos del bloque `info` de la documentación, compartidos por el bloque de rutas y sus grupos. */
internal class ApiMetadata {
    var title: String = DEFAULT_TITLE
    var version: String = DEFAULT_VERSION
    var description: String? = null

    companion object {
        const val DEFAULT_TITLE = "KastAPI"
        const val DEFAULT_VERSION = "0.0.0"
    }
}
