package dev.kastapi

import kotlinx.serialization.json.Json

/**
 * Configuración de KastAPI. Todas las propiedades tienen un valor por defecto.
 *
 * @property docsPath ruta de Swagger UI; `null` la desactiva.
 * @property openApiPath ruta del esquema OpenAPI; `null` lo desactiva (y también la documentación).
 * @property json instancia de serialización usada para entradas y salidas.
 * @property validateOnStartup si es `true`, valida las rutas al arrancar.
 * @property maxBodyBytes tamaño máximo, en bytes, del cuerpo de una petición; si se supera se responde `413`. Debe ser positivo.
 */
public data class KastApiConfig(
    public val docsPath: String? = "/docs",
    public val openApiPath: String? = "/openapi.json",
    public val json: Json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
        },
    public val validateOnStartup: Boolean = true,
    public val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) {
    init {
        require(maxBodyBytes > 0) { "maxBodyBytes debe ser positivo, pero es $maxBodyBytes" }
    }
}

private const val DEFAULT_MAX_BODY_BYTES = 1_048_576L
