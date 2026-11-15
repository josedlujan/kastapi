package dev.kastapi.openapi

import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Modelo neutral de una ruta: lo que `kastapi-core` entrega a `kastapi-openapi` para generar el documento.
 *
 * Así `openapi` no depende de `core` ni repite la lógica que decide qué viene del path, del query o del cuerpo.
 */
@InternalKastApi
public class ApiRoute(
    /** Método HTTP en mayúsculas (`GET`, `POST`...). */
    public val method: String,
    /** Ruta completa, con el prefijo de los grupos y los parámetros como `{nombre}`. */
    public val path: String,
    public val summary: String?,
    public val description: String?,
    public val tags: List<String>,
    /** Código de la respuesta exitosa. */
    public val status: Int,
    /** Frase estándar del [status] (`OK`, `Created`...). */
    public val statusDescription: String,
    public val parameters: List<ApiParameter>,
    public val body: ApiBody?,
    /** Descriptor de la salida, o `null` si la respuesta no tiene cuerpo. */
    public val output: SerialDescriptor?,
    /** `true` si la ruta tiene entrada y por tanto puede responder 422. */
    public val documentsValidationError: Boolean,
)

/** Propiedad de la entrada que se lee de la URL. */
@InternalKastApi
public class ApiParameter(
    public val name: String,
    /** `path` o `query`. */
    public val location: String,
    public val required: Boolean,
    /** Descriptor de la clase de entrada. */
    public val owner: SerialDescriptor,
    /** Posición de la propiedad en [owner]. */
    public val index: Int,
)

/** Cuerpo JSON de la entrada. */
@InternalKastApi
public class ApiBody(
    public val descriptor: SerialDescriptor,
    /** Propiedades de [descriptor] que no salen del cuerpo (las de path). */
    public val excluded: Set<String>,
)

/** Datos del bloque `info` del documento. */
@InternalKastApi
public class ApiInfo(
    public val title: String,
    public val version: String,
    public val description: String?,
)
