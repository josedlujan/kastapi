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
 * Problema de configuración de una ruta.
 *
 * Un problema [fatal] impide construir la ruta (por ejemplo, un tipo que no se puede enlazar); el resto son
 * problemas de consistencia que solo se comprueban si `validateOnStartup` está activo.
 */
internal class Problem(
    val message: String,
    val fatal: Boolean,
)

/** Ruta registrada en el DSL, todavía sin analizar: el plan de binding se calcula al terminar el bloque. */
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
    /** "METHOD /ruta", como se muestra en los mensajes. */
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
 * Analiza las rutas registradas (referencia §6) y devuelve las definiciones listas para montarse.
 *
 * Recoge **todos** los problemas antes de lanzar [KastApiConfigurationException]. Con [validate] en `false`
 * solo se reportan los problemas fatales.
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

/** Rutas `GET` de la aplicación que coinciden con las que KastAPI reserva para la documentación. */
private fun reservedRoutes(
    drafts: List<RouteDraft>,
    reservedPaths: List<String>,
): List<String> {
    val reserved = reservedPaths.map { routeTemplate(it) }.toSet()
    return drafts
        .filter { it.method == HttpMethod.Get && routeTemplate(it.path) in reserved }
        .map { "${it.label}: ${Messages.RESERVED_ROUTE}" }
}

/** Rutas con el mismo método y la misma plantilla (sin contar los nombres de los parámetros). */
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
