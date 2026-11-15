package dev.kastapi

import dev.kastapi.errors.Messages

/**
 * HTTP error that a handler can throw to respond with a specific [status].
 *
 * The response uses the standard error format with `error = "http_error"` and [message] as the message:
 *
 * ```kotlin
 * get<UserPath, UserResponse>("/users/{id}") { path ->
 *     users[path.id] ?: throw HttpError(404, "the user does not exist")
 * }
 * ```
 *
 * @property status HTTP code of the response; must be between 400 and 599.
 * @param message description of the error that the client will see.
 * @throws IllegalArgumentException if [status] is not between 400 and 599.
 */
public class HttpError(
    public val status: Int,
    message: String,
) : RuntimeException(message) {
    init {
        require(status in MIN_STATUS..MAX_STATUS) { Messages.invalidHttpErrorStatus(status) }
    }
}

// File-level constants: in a private companion they would be published as public fields of the class.
private const val MIN_STATUS = 400
private const val MAX_STATUS = 599
