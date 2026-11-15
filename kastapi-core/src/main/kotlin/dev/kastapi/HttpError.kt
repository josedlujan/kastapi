package dev.kastapi

import dev.kastapi.errors.Messages

/**
 * Error HTTP que un handler puede lanzar para responder con un [status] concreto.
 *
 * La respuesta usa el formato de error estándar con `error = "http_error"` y [message] como mensaje:
 *
 * ```kotlin
 * get<UserPath, UserResponse>("/users/{id}") { path ->
 *     users[path.id] ?: throw HttpError(404, "el usuario no existe")
 * }
 * ```
 *
 * @property status código HTTP de la respuesta; debe estar entre 400 y 599.
 * @param message descripción del error que verá el cliente.
 * @throws IllegalArgumentException si [status] no está entre 400 y 599.
 */
public class HttpError(
    public val status: Int,
    message: String,
) : RuntimeException(message) {
    init {
        require(status in MIN_STATUS..MAX_STATUS) { Messages.invalidHttpErrorStatus(status) }
    }
}

// Constantes de archivo: en un companion privado se publicarían como campos públicos de la clase.
private const val MIN_STATUS = 400
private const val MAX_STATUS = 599
