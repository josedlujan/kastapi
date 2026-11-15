package dev.kastapi

import io.ktor.server.application.ApplicationCall

/**
 * Receptor de los handlers. Da acceso a la petición y permite bajar a Ktor puro.
 *
 * @property call la llamada de Ktor (`ApplicationCall`) en curso.
 */
public class KastCall internal constructor(
    public val call: ApplicationCall,
) {
    /**
     * Devuelve el valor del header [name], o `null` si la petición no lo trae.
     */
    public fun header(name: String): String? = call.request.headers[name]
}
