package dev.kastapi.routing

import dev.kastapi.KastApiConfig
import dev.kastapi.KastCall
import dev.kastapi.binding.BindingResult
import dev.kastapi.binding.bind
import dev.kastapi.errors.malformedJsonError
import dev.kastapi.errors.payloadTooLargeError
import dev.kastapi.errors.respondError
import dev.kastapi.errors.validationError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Monta las rutas registradas en el `routing { }` de Ktor. */
internal fun Application.mountRoutes(
    routes: List<RouteDefinition>,
    config: KastApiConfig,
) {
    routing {
        for (definition in routes) {
            route(definition.path, definition.method) {
                handle {
                    when (val bound = definition.plan.bind(call, config.json, config.maxBodyBytes)) {
                        is BindingResult.Failure -> {
                            call.respondError(validationError(bound.errors), config.json)
                        }

                        BindingResult.MalformedJson -> {
                            call.respondError(malformedJsonError(), config.json)
                        }

                        BindingResult.PayloadTooLarge -> {
                            call.respondError(payloadTooLargeError(config.maxBodyBytes), config.json)
                        }

                        is BindingResult.Success -> {
                            val result = definition.handler(KastCall(call), bound.input)
                            call.respondResult(definition, result, config)
                        }
                    }
                }
            }
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
