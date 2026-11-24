package dev.kastapi

import dev.kastapi.docs.mountDocumentation
import dev.kastapi.docs.reservedPaths
import dev.kastapi.errors.installErrorPages
import dev.kastapi.routing.mountRoutes
import dev.kastapi.startup.RouteDraft
import dev.kastapi.startup.planRoutes
import dev.kastapi.validation.PatternLimits
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Starts a Ktor (Netty) server with the routes declared in [block] and blocks until it stops.
 *
 * Internamente llama a [installKastApi].
 *
 * @param port listening port.
 * @param host network interface.
 * @param config KastAPI configuration.
 * @param block route declarations.
 */
public fun kastApi(
    port: Int = 8080,
    host: String = "0.0.0.0",
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
) {
    embeddedServer(Netty, port = port, host = host) {
        installKastApi(config, block)
    }.start(wait = true)
}

/**
 * Installs KastAPI into an existing Ktor application.
 *
 * @param config KastAPI configuration.
 * @param block route declarations.
 */
public fun Application.installKastApi(
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
) {
    val drafts = mutableListOf<RouteDraft>()
    val metadata = ApiMetadata()
    KastApi(drafts, metadata, prefix = "", inheritedTags = emptyList()).block()
    val routes =
        planRoutes(
            drafts,
            config.validateOnStartup,
            reservedPaths(config),
            PatternLimits(config.maxPatternInputLength, config.patternTimeoutMillis),
        )
    installErrorPages(config.json, routes)
    mountRoutes(routes, config)
    mountDocumentation(routes, metadata, config)
}

/**
 * Receiver of the route declaration block. See [get], [post], [put], [patch] and [delete].
 */
public class KastApi internal constructor(
    private val routes: MutableList<RouteDraft>,
    private val metadata: ApiMetadata,
    private val prefix: String,
    private val inheritedTags: List<String>,
) {
    /**
     * Defines the `info` block of the OpenAPI documentation. If called several times, the last call wins.
     *
     * @param title API title.
     * @param version API version.
     * @param description optional description.
     */
    public fun info(
        title: String = ApiMetadata.DEFAULT_TITLE,
        version: String = ApiMetadata.DEFAULT_VERSION,
        description: String? = null,
    ) {
        metadata.title = title
        metadata.version = version
        metadata.description = description
    }

    /**
     * Groups routes under a common prefix.
     *
     * @param prefix prefix prepended to the path of each declaration in the group.
     * @param tags tags inherited by the group's routes.
     * @param block route declarations of the group.
     */
    public fun group(
        prefix: String,
        tags: List<String> = emptyList(),
        block: KastApi.() -> Unit,
    ) {
        KastApi(routes, metadata, this.prefix.trimEnd('/') + prefix, inheritedTags + tags).block()
    }

    /** Registers a route. Only the DSL's `inline` functions use it. */
    @PublishedApi
    @Suppress("LongParameterList", "UNCHECKED_CAST")
    internal fun <I : Any, O> register(
        method: HttpMethod,
        path: String,
        summary: String?,
        description: String?,
        tags: List<String>,
        status: Int,
        inputSerializer: KSerializer<I>,
        outputSerializer: KSerializer<O>,
        handler: suspend KastCall.(I) -> O,
    ) {
        val fullPath = prefix.trimEnd('/') + path
        routes +=
            RouteDraft(
                method = method,
                path = fullPath,
                summary = summary,
                description = description,
                tags = inheritedTags + tags,
                status = status,
                inputSerializer = inputSerializer,
                outputSerializer = outputSerializer,
                emptyResponse = outputSerializer.descriptor.serialName == serializer<Unit>().descriptor.serialName,
                handler = { call, input -> call.handler(input as I) },
                encode = { json, value -> json.encodeToString(outputSerializer, value as O) },
            )
    }
}

/**
 * Declares a `GET` route.
 *
 * @param I input model; use [NoInput] if the route receives no data.
 * @param O output model; `Unit` responds without a body.
 * @param path route, relative to the group if it is inside one.
 * @param summary summary for the documentation.
 * @param description description for the documentation.
 * @param tags tags for the documentation.
 * @param status success response code.
 * @param handler suspending function that serves the request.
 */
public inline fun <reified I : Any, reified O> KastApi.get(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,
    noinline handler: suspend KastCall.(I) -> O,
) {
    register(HttpMethod.Get, path, summary, description, tags, status, serializer<I>(), serializer<O>(), handler)
}

/**
 * Declares a `POST` route. The default [status] is 201.
 *
 * See [get] for the meaning of the parameters.
 */
public inline fun <reified I : Any, reified O> KastApi.post(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 201,
    noinline handler: suspend KastCall.(I) -> O,
) {
    register(HttpMethod.Post, path, summary, description, tags, status, serializer<I>(), serializer<O>(), handler)
}

/**
 * Declares a `PUT` route.
 *
 * See [get] for the meaning of the parameters.
 */
public inline fun <reified I : Any, reified O> KastApi.put(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,
    noinline handler: suspend KastCall.(I) -> O,
) {
    register(HttpMethod.Put, path, summary, description, tags, status, serializer<I>(), serializer<O>(), handler)
}

/**
 * Declares a `PATCH` route.
 *
 * See [get] for the meaning of the parameters.
 */
public inline fun <reified I : Any, reified O> KastApi.patch(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,
    noinline handler: suspend KastCall.(I) -> O,
) {
    register(HttpMethod.Patch, path, summary, description, tags, status, serializer<I>(), serializer<O>(), handler)
}

/**
 * Declares a `DELETE` route.
 *
 * See [get] for the meaning of the parameters.
 */
public inline fun <reified I : Any, reified O> KastApi.delete(
    path: String,
    summary: String? = null,
    description: String? = null,
    tags: List<String> = emptyList(),
    status: Int = 200,
    noinline handler: suspend KastCall.(I) -> O,
) {
    register(HttpMethod.Delete, path, summary, description, tags, status, serializer<I>(), serializer<O>(), handler)
}
