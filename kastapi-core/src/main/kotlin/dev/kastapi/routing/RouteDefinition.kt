package dev.kastapi.routing

import dev.kastapi.KastCall
import dev.kastapi.binding.RoutePlan
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Ruta registrada en el DSL, ya sin tipos genéricos, lista para montarse en Ktor.
 *
 * Los metadatos ([summary], [description], [tags]) los usa la generación de OpenAPI.
 */
internal class RouteDefinition(
    val method: HttpMethod,
    val path: String,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val status: Int,
    /** Serializadores de entrada y salida; la documentación OpenAPI lee sus descriptores. */
    val inputSerializer: KSerializer<*>,
    val outputSerializer: KSerializer<*>,
    /** Cómo construir la entrada desde path, query y cuerpo. */
    val plan: RoutePlan,
    /** `true` cuando la salida es `Unit`: se responde sin cuerpo. */
    val emptyResponse: Boolean,
    /** Ejecuta el handler del usuario con la entrada ya construida. */
    val handler: suspend (KastCall, Any?) -> Any?,
    /** Serializa el resultado del handler a JSON. */
    val encode: (Json, Any?) -> String,
)
