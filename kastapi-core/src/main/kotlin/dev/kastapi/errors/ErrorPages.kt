package dev.kastapi.errors

import dev.kastapi.HttpError
import dev.kastapi.routing.RouteDefinition
import dev.kastapi.routing.allowHeader
import dev.kastapi.routing.allowedMethods
import dev.kastapi.routing.isMethodMismatch
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.application.pluginOrNull
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Installs KastAPI's error handling (reference §8): 404, 405 (with an `Allow` header for KastAPI routes), `HttpError` and
 * unhandled exceptions.
 *
 * If the application already installed its own `StatusPages`, KastAPI's is not installed, so as not to override its
 * handlers (the "no lock-in" principle); in that case errors follow the application's configuration.
 */
internal fun Application.installErrorPages(
    json: Json,
    routes: List<RouteDefinition>,
) {
    if (pluginOrNull(StatusPages) != null) return

    install(StatusPages) {
        exception<HttpError> { call, cause ->
            call.respondError(httpError(cause.status, cause.message.orEmpty()), json)
        }
        exception<Throwable> { call, cause ->
            call.handleUnexpected(cause, json)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            // Ktor answers 404 instead of 405 when a path with parameters exists but not for this method.
            val registered = allowedMethods(routes, call.request.path())
            if (isMethodMismatch(registered, call.request.httpMethod)) {
                call.respondMethodNotAllowed(registered, json)
            } else {
                call.respondError(notFoundError(call.request.httpMethod.value, call.request.path()), json)
            }
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondMethodNotAllowed(allowedMethods(routes, call.request.path()), json)
        }
    }
}

/**
 * Responds 405. The `Allow` header lists the methods of KastAPI routes for the path; for any other route the methods
 * are unknown and the header is left out.
 */
private suspend fun ApplicationCall.respondMethodNotAllowed(
    registered: List<HttpMethod>,
    json: Json,
) {
    allowHeader(registered)?.let { response.headers.append(HttpHeaders.Allow, it) }
    respondError(methodNotAllowedError(request.httpMethod.value, request.path()), json)
}

/** Responds with [error] as JSON and the status it indicates. */
internal suspend fun ApplicationCall.respondError(
    error: ErrorResponse,
    json: Json,
) {
    respondText(
        json.encodeToString(ErrorResponse.serializer(), error),
        ContentType.Application.Json,
        HttpStatusCode.fromValue(error.status),
    )
}

private suspend fun ApplicationCall.handleUnexpected(
    cause: Throwable,
    json: Json,
) {
    // A cancellation (for example, the client closed the connection) is not a server error.
    if (cause is CancellationException) throw cause

    application.log.error(Messages.unhandledException(request.httpMethod.value, request.path()), cause)
    respondError(internalError(), json)
}
