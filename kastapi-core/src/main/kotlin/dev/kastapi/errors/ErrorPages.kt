package dev.kastapi.errors

import dev.kastapi.HttpError
import io.ktor.http.ContentType
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
 * Instala el manejo de errores de KastAPI (referencia §8): 404, `HttpError` y excepciones no controladas.
 *
 * Si la aplicación ya instaló su propio `StatusPages` no se instala el de KastAPI, para no pisar sus
 * manejadores (principio "sin encierro"); en ese caso los errores siguen la configuración de la aplicación.
 */
internal fun Application.installErrorPages(json: Json) {
    if (pluginOrNull(StatusPages) != null) return

    install(StatusPages) {
        exception<HttpError> { call, cause ->
            call.respondError(httpError(cause.status, cause.message.orEmpty()), json)
        }
        exception<Throwable> { call, cause ->
            call.handleUnexpected(cause, json)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(notFoundError(call.request.httpMethod.value, call.request.path()), json)
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondError(methodNotAllowedError(call.request.httpMethod.value, call.request.path()), json)
        }
    }
}

/** Responde con [error] en JSON y el status que indica. */
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
    // Una cancelación (por ejemplo, el cliente cerró la conexión) no es un error del servidor.
    if (cause is CancellationException) throw cause

    application.log.error(Messages.unhandledException(request.httpMethod.value, request.path()), cause)
    respondError(internalError(), json)
}
