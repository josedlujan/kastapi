package dev.kastapi

import io.ktor.server.application.ApplicationCall

/**
 * Receiver of the handlers. Gives access to the request and lets you drop down to plain Ktor.
 *
 * @property call the current Ktor call (`ApplicationCall`).
 */
public class KastCall internal constructor(
    public val call: ApplicationCall,
) {
    /**
     * Returns the value of the [name] header, or `null` if the request does not carry it.
     */
    public fun header(name: String): String? = call.request.headers[name]
}
