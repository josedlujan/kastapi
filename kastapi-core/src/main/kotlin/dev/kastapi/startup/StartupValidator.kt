package dev.kastapi.startup

import dev.kastapi.KastApiConfigurationException
import dev.kastapi.KastCall
import dev.kastapi.binding.RoutePlan
import dev.kastapi.binding.routeTemplate
import dev.kastapi.errors.Messages
import dev.kastapi.routing.RouteDefinition
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Configuration problem of a route.
 *
 * A [fatal] problem prevents building the route (for example, a type that cannot be bound); the rest are
 * consistency problems that are only checked if `validateOnStartup` is enabled.
 */
internal class Problem(
    val message: String,
    val fatal: Boolean,
)

/** Route registered in the DSL, not yet analyzed: the binding plan is computed when the block ends. */
internal class RouteDraft(
    val method: HttpMethod,
    val path: String,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val status: Int,
    val inputSerializer: KSerializer<*>,
    val outputSerializer: KSerializer<*>,
    val emptyResponse: Boolean,
    val handler: suspend (KastCall, Any?) -> Any?,
    val encode: (Json, Any?) -> String,
) {
    /** "METHOD /path", as shown in the messages. */
    val label: String get() = "${method.value} $path"

    fun toDefinition(plan: RoutePlan): RouteDefinition =
        RouteDefinition(
            method = method,
            path = path,
            summary = summary,
            description = description,
            tags = tags,
            status = status,
            inputSerializer = inputSerializer,
            outputSerializer = outputSerializer,
            plan = plan,
            emptyResponse = emptyResponse,
            handler = handler,
            encode = encode,
        )
}

/**
 * Analyzes the registered routes (reference §6) and returns the definitions ready to be mounted.
 *
 * Collects **all** the problems before throwing [KastApiConfigurationException]. With [validate] set to `false`
 * only the fatal problems are reported.
 */
internal fun planRoutes(
    drafts: List<RouteDraft>,
    validate: Boolean,
    reservedPaths: List<String>,
): List<RouteDefinition> {
    val results = drafts.map { RoutePlan.build(it.method, it.path, it.inputSerializer) }
    val problems = mutableListOf<String>()

    drafts.zip(results).forEach { (draft, result) ->
        result.problems
            .filter { validate || it.fatal }
            .forEach { problems += "${draft.label}: ${it.message}" }
    }
    if (validate) {
        problems += duplicateRoutes(drafts)
        problems += reservedRoutes(drafts, reservedPaths)
    }

    if (problems.isNotEmpty()) throw KastApiConfigurationException(Messages.configurationProblems(problems))
    return drafts.zip(results).map { (draft, result) -> draft.toDefinition(result.plan) }
}

/** `GET` routes of the application that match the ones KastAPI reserves for the documentation. */
private fun reservedRoutes(
    drafts: List<RouteDraft>,
    reservedPaths: List<String>,
): List<String> {
    val reserved = reservedPaths.map { routeTemplate(it) }.toSet()
    return drafts
        .filter { it.method == HttpMethod.Get && routeTemplate(it.path) in reserved }
        .map { "${it.label}: ${Messages.RESERVED_ROUTE}" }
}

/** Routes with the same method and the same template (ignoring parameter names). */
private fun duplicateRoutes(drafts: List<RouteDraft>): List<String> {
    val first = mutableMapOf<String, RouteDraft>()
    val duplicates = mutableListOf<String>()
    for (draft in drafts) {
        val key = "${draft.method.value} ${routeTemplate(draft.path)}"
        val original = first.putIfAbsent(key, draft)
        if (original != null) duplicates += "${draft.label}: ${Messages.duplicateRoute(original.label)}"
    }
    return duplicates
}
