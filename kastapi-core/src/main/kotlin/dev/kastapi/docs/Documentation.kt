package dev.kastapi.docs

import dev.kastapi.ApiMetadata
import dev.kastapi.KastApiConfig
import dev.kastapi.binding.ParamLocation
import dev.kastapi.errors.ErrorResponse
import dev.kastapi.openapi.ApiBody
import dev.kastapi.openapi.ApiInfo
import dev.kastapi.openapi.ApiParameter
import dev.kastapi.openapi.ApiRoute
import dev.kastapi.openapi.OpenApiGenerator
import dev.kastapi.openapi.swaggerUiHtml
import dev.kastapi.routing.RouteDefinition
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.JsonObject

/** Rutas que KastAPI reserva para la documentación; vacío si el esquema está desactivado. */
internal fun reservedPaths(config: KastApiConfig): List<String> =
    if (config.openApiPath == null) emptyList() else listOfNotNull(config.openApiPath, config.docsPath)

/**
 * Genera el documento OpenAPI **una sola vez** y monta las rutas que lo sirven (`openApiPath`) y la que sirve
 * Swagger UI (`docsPath`). Con `openApiPath = null` no se monta nada; con `docsPath = null` solo se sirve el esquema.
 */
internal fun Application.mountDocumentation(
    routes: List<RouteDefinition>,
    metadata: ApiMetadata,
    config: KastApiConfig,
) {
    val openApiPath = config.openApiPath ?: return
    val info = ApiInfo(metadata.title, metadata.version, metadata.description)
    val document =
        OpenApiGenerator.generate(
            info,
            routes.map { it.toApiRoute() },
            ErrorResponse.serializer().descriptor,
        )
    val body = config.json.encodeToString(JsonObject.serializer(), document)

    routing {
        get(openApiPath) { call.respondText(body, ContentType.Application.Json) }
        config.docsPath?.let { docsPath ->
            val html = swaggerUiHtml(metadata.title, openApiPath)
            get(docsPath) { call.respondText(html, ContentType.Text.Html) }
        }
    }
}

/** Traduce una ruta registrada al modelo neutral que consume `kastapi-openapi`. */
private fun RouteDefinition.toApiRoute(): ApiRoute {
    val descriptor = inputSerializer.descriptor
    val parameters =
        plan.params.map { param ->
            val isPath = param.location == ParamLocation.PATH
            val required = isPath || (!param.hasDefault && !param.nullable)
            ApiParameter(param.name, param.location.label, required, descriptor, param.index)
        }
    return ApiRoute(
        method = method.value,
        path = path,
        summary = summary,
        description = description,
        tags = tags,
        status = status,
        statusDescription = HttpStatusCode.fromValue(status).description,
        parameters = parameters,
        body = if (plan.hasBody) ApiBody(descriptor, plan.pathNames) else null,
        output = if (emptyResponse) null else outputSerializer.descriptor,
        documentsValidationError = !plan.noInput,
    )
}
