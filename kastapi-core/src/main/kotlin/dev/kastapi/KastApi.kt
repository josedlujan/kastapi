package dev.kastapi

import dev.kastapi.docs.mountDocumentation
import dev.kastapi.docs.reservedPaths
import dev.kastapi.errors.installErrorPages
import dev.kastapi.routing.mountRoutes
import dev.kastapi.startup.RouteDraft
import dev.kastapi.startup.planRoutes
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Arranca un servidor Ktor (Netty) con las rutas declaradas en [block] y bloquea hasta que se detenga.
 *
 * Internamente llama a [installKastApi].
 *
 * @param port puerto de escucha.
 * @param host interfaz de red.
 * @param config configuración de KastAPI.
 * @param block declaración de rutas.
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
 * Instala KastAPI en una aplicación Ktor existente.
 *
 * @param config configuración de KastAPI.
 * @param block declaración de rutas.
 */
public fun Application.installKastApi(
    config: KastApiConfig = KastApiConfig(),
    block: KastApi.() -> Unit,
) {
    val drafts = mutableListOf<RouteDraft>()
    val metadata = ApiMetadata()
    KastApi(drafts, metadata, prefix = "", inheritedTags = emptyList()).block()
    val routes = planRoutes(drafts, config.validateOnStartup, reservedPaths(config))
    installErrorPages(config.json)
    mountRoutes(routes, config)
    mountDocumentation(routes, metadata, config)
}

/**
 * Receptor del bloque de declaración de rutas. Ver [get], [post], [put], [patch] y [delete].
 */
public class KastApi internal constructor(
    private val routes: MutableList<RouteDraft>,
    private val metadata: ApiMetadata,
    private val prefix: String,
    private val inheritedTags: List<String>,
) {
    /**
     * Define el bloque `info` de la documentación OpenAPI. Si se llama varias veces, gana la última llamada.
     *
     * @param title título de la API.
     * @param version versión de la API.
     * @param description descripción opcional.
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
     * Agrupa rutas bajo un prefijo común.
     *
     * @param prefix prefijo que se antepone a la ruta de cada declaración del grupo.
     * @param tags etiquetas que heredan las rutas del grupo.
     * @param block declaración de rutas del grupo.
     */
    public fun group(
        prefix: String,
        tags: List<String> = emptyList(),
        block: KastApi.() -> Unit,
    ) {
        KastApi(routes, metadata, this.prefix.trimEnd('/') + prefix, inheritedTags + tags).block()
    }

    /** Registra una ruta. Solo lo usan las funciones `inline` del DSL. */
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
 * Declara una ruta `GET`.
 *
 * @param I modelo de entrada; usa [NoInput] si la ruta no recibe datos.
 * @param O modelo de salida; `Unit` responde sin cuerpo.
 * @param path ruta, relativa al grupo si está dentro de uno.
 * @param summary resumen para la documentación.
 * @param description descripción para la documentación.
 * @param tags etiquetas para la documentación.
 * @param status código de respuesta exitosa.
 * @param handler función suspendida que atiende la petición.
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
 * Declara una ruta `POST`. El [status] por defecto es 201.
 *
 * Ver [get] para el significado de los parámetros.
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
 * Declara una ruta `PUT`.
 *
 * Ver [get] para el significado de los parámetros.
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
 * Declara una ruta `PATCH`.
 *
 * Ver [get] para el significado de los parámetros.
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
 * Declara una ruta `DELETE`.
 *
 * Ver [get] para el significado de los parámetros.
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
