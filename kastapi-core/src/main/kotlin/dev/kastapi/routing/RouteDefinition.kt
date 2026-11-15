package dev.kastapi.routing

import dev.kastapi.KastCall
import dev.kastapi.binding.RoutePlan
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Route registered in the DSL, now without generic types, ready to be mounted in Ktor.
 *
 * The metadata ([summary], [description], [tags]) is used by the OpenAPI generation.
 */
internal class RouteDefinition(
    val method: HttpMethod,
    val path: String,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val status: Int,
    /** Input and output serializers; the OpenAPI documentation reads their descriptors. */
    val inputSerializer: KSerializer<*>,
    val outputSerializer: KSerializer<*>,
    /** How to build the input from path, query and body. */
    val plan: RoutePlan,
    /** `true` when the output is `Unit`: the response has no body. */
    val emptyResponse: Boolean,
    /** Runs the user's handler with the input already built. */
    val handler: suspend (KastCall, Any?) -> Any?,
    /** Serializes the handler's result to JSON. */
    val encode: (Json, Any?) -> String,
)
