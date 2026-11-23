package dev.kastapi.routing

import dev.kastapi.KastApiConfig
import dev.kastapi.KastCall
import dev.kastapi.binding.BindingResult
import dev.kastapi.binding.bind
import dev.kastapi.errors.malformedJsonError
import dev.kastapi.errors.payloadTooLargeError
import dev.kastapi.errors.respondError
import dev.kastapi.errors.unsupportedMediaTypeError
import dev.kastapi.errors.validationError
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Mounts the registered routes in Ktor's `routing { }`.
 *
 * Every `GET` route is also mounted for `HEAD`, with the same binding, validation and handler: the status and headers
 * are those of the `GET`, and the server drops the body.
 */
internal fun Application.mountRoutes(
    routes: List<RouteDefinition>,
    config: KastApiConfig,
) {
    routing {
        for (definition in routes) {
            route(definition.path, definition.method) {
                handle { call.serve(definition, config) }
            }
            if (definition.method == HttpMethod.Get) {
                route(definition.path, HttpMethod.Head) {
                    handle { call.serve(definition, config) }
                }
            }
        }
    }
}

private suspend fun RoutingCall.serve(
    definition: RouteDefinition,
    config: KastApiConfig,
) {
    when (val bound = definition.plan.bind(this, config.json, config.maxBodyBytes)) {
        is BindingResult.Failure -> {
            respondError(validationError(bound.errors), config.json)
        }

        BindingResult.MalformedJson -> {
            respondError(malformedJsonError(), config.json)
        }

        BindingResult.PayloadTooLarge -> {
            respondError(payloadTooLargeError(config.maxBodyBytes), config.json)
        }

        is BindingResult.UnsupportedMediaType -> {
            respondError(unsupportedMediaTypeError(bound.message), config.json)
        }

        is BindingResult.Success -> {
            val result = definition.handler(KastCall(this), bound.input)
            respondResult(definition, result, config)
        }
    }
}

private suspend fun ApplicationCall.respondResult(
    definition: RouteDefinition,
    result: Any?,
    config: KastApiConfig,
) {
    val status = HttpStatusCode.fromValue(definition.status)
    if (definition.emptyResponse) {
        respond(status)
    } else {
        respondText(definition.encode(config.json, result), ContentType.Application.Json, status)
    }
}
