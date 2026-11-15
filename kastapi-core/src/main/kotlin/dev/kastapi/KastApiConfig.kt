package dev.kastapi

import kotlinx.serialization.json.Json

/**
 * KastAPI configuration. All properties have a default value.
 *
 * @property docsPath Swagger UI path; `null` disables it.
 * @property openApiPath OpenAPI schema path; `null` disables it (and the documentation too).
 * @property json serialization instance used for inputs and outputs.
 * @property validateOnStartup if `true`, validates the routes at startup.
 * @property maxBodyBytes maximum size, in bytes, of a request body; if exceeded the response is `413`. Must be positive.
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
        require(maxBodyBytes > 0) { "maxBodyBytes must be positive, but was $maxBodyBytes" }
    }
}

private const val DEFAULT_MAX_BODY_BYTES = 1_048_576L
