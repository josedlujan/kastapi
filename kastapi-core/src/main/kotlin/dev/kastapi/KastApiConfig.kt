package dev.kastapi

import kotlinx.serialization.json.Json

/**
 * KastAPI configuration. All properties have a default value.
 *
 * Create it with the builder; every property you do not set keeps its default:
 *
 * ```kotlin
 * val config = KastApiConfig {
 *     docsPath = "/help"
 *     maxBodyBytes = 64 * 1024
 * }
 * ```
 *
 * @property docsPath Swagger UI path; `null` disables it.
 * @property openApiPath OpenAPI schema path; `null` disables it (and the documentation too).
 * @property json serialization instance used for inputs and outputs.
 * @property validateOnStartup if `true`, validates the routes at startup.
 * @property maxBodyBytes maximum size, in bytes, of a request body; if exceeded the response is `413`. Must be positive.
 */
public class KastApiConfig internal constructor(
    builder: Builder,
) {
    public val docsPath: String? = builder.docsPath
    public val openApiPath: String? = builder.openApiPath
    public val json: Json = builder.json
    public val validateOnStartup: Boolean = builder.validateOnStartup
    public val maxBodyBytes: Long = builder.maxBodyBytes

    init {
        require(maxBodyBytes > 0) { "maxBodyBytes must be positive, but was $maxBodyBytes" }
    }

    /**
     * Creates a configuration from named arguments.
     *
     * Kept so code written for 0.1 keeps compiling and linking. Use `KastApiConfig { ... }` instead.
     */
    @Deprecated(
        message = "Use the builder: KastApiConfig { docsPath = \"/help\" }",
        replaceWith = ReplaceWith("KastApiConfig { }"),
    )
    public constructor(
        docsPath: String? = DEFAULT_DOCS_PATH,
        openApiPath: String? = DEFAULT_OPENAPI_PATH,
        json: Json = defaultJson(),
        validateOnStartup: Boolean = true,
        maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    ) : this(
        Builder().also {
            it.docsPath = docsPath
            it.openApiPath = openApiPath
            it.json = json
            it.validateOnStartup = validateOnStartup
            it.maxBodyBytes = maxBodyBytes
        },
    )

    /** Keeps the no-argument constructor of 0.1 in the compiled class. Not callable from source. */
    @Deprecated(message = "Use KastApiConfig()", level = DeprecationLevel.HIDDEN)
    public constructor() : this(Builder())

    /** Mutable holder of the settings, used inside `KastApiConfig { ... }`. */
    public class Builder internal constructor() {
        /** Swagger UI path; `null` disables it. Default `/docs`. */
        public var docsPath: String? = DEFAULT_DOCS_PATH

        /** OpenAPI schema path; `null` disables it (and the documentation too). Default `/openapi.json`. */
        public var openApiPath: String? = DEFAULT_OPENAPI_PATH

        /** Serialization instance used for inputs and outputs. */
        public var json: Json = defaultJson()

        /** If `true` (the default), validates the routes at startup. */
        public var validateOnStartup: Boolean = true

        /** Maximum size, in bytes, of a request body. Must be positive. Default 1 MiB. */
        public var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is KastApiConfig &&
                    docsPath == other.docsPath &&
                    openApiPath == other.openApiPath &&
                    json == other.json &&
                    validateOnStartup == other.validateOnStartup &&
                    maxBodyBytes == other.maxBodyBytes
            )

    override fun hashCode(): Int {
        var result = docsPath?.hashCode() ?: 0
        result = 31 * result + (openApiPath?.hashCode() ?: 0)
        result = 31 * result + json.hashCode()
        result = 31 * result + validateOnStartup.hashCode()
        result = 31 * result + maxBodyBytes.hashCode()
        return result
    }

    override fun toString(): String =
        "KastApiConfig(docsPath=$docsPath, openApiPath=$openApiPath, json=$json, " +
            "validateOnStartup=$validateOnStartup, maxBodyBytes=$maxBodyBytes)"
}

/** Creates a configuration with every default. */
public fun KastApiConfig(): KastApiConfig = KastApiConfig(KastApiConfig.Builder())

/** Creates a configuration; the properties you do not set in [block] keep their defaults. */
public fun KastApiConfig(block: KastApiConfig.Builder.() -> Unit): KastApiConfig =
    KastApiConfig(KastApiConfig.Builder().apply(block))

private const val DEFAULT_DOCS_PATH = "/docs"
private const val DEFAULT_OPENAPI_PATH = "/openapi.json"
private const val DEFAULT_MAX_BODY_BYTES = 1_048_576L

// One shared instance: `Json` compares by identity, so this keeps two default configurations equal.
private val DEFAULT_JSON: Json =
    Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

private fun defaultJson(): Json = DEFAULT_JSON
