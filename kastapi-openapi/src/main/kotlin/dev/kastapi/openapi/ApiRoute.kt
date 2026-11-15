package dev.kastapi.openapi

import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Neutral model of a route: what `kastapi-core` hands to `kastapi-openapi` to generate the document.
 *
 * This way `openapi` does not depend on `core` or repeat the logic that decides what comes from
 * the path, the query or the body.
 */
@InternalKastApi
public class ApiRoute(
    /** HTTP method in uppercase (`GET`, `POST`...). */
    public val method: String,
    /** Full path, with the group prefixes and the parameters as `{name}`. */
    public val path: String,
    public val summary: String?,
    public val description: String?,
    public val tags: List<String>,
    /** Success response code. */
    public val status: Int,
    /** Standard phrase of the [status] (`OK`, `Created`...). */
    public val statusDescription: String,
    public val parameters: List<ApiParameter>,
    public val body: ApiBody?,
    /** Output descriptor, or `null` if the response has no body. */
    public val output: SerialDescriptor?,
    /** `true` if the route has input and can therefore respond 422. */
    public val documentsValidationError: Boolean,
)

/** Input property that is read from the URL. */
@InternalKastApi
public class ApiParameter(
    public val name: String,
    /** `path` o `query`. */
    public val location: String,
    public val required: Boolean,
    /** Descriptor of the input class. */
    public val owner: SerialDescriptor,
    /** Position of the property in [owner]. */
    public val index: Int,
)

/** JSON body of the input. */
@InternalKastApi
public class ApiBody(
    public val descriptor: SerialDescriptor,
    /** Properties of [descriptor] that do not come from the body (the path ones). */
    public val excluded: Set<String>,
)

/** Data of the document's `info` block. */
@InternalKastApi
public class ApiInfo(
    public val title: String,
    public val version: String,
    public val description: String?,
)
