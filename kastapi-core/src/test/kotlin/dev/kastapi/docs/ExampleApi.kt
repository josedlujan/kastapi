package dev.kastapi.docs

import dev.kastapi.KastApi
import dev.kastapi.delete
import dev.kastapi.get
import dev.kastapi.post
import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Max
import dev.kastapi.validation.Min
import kotlinx.serialization.Serializable

// Models of the reference example (§1).

@Serializable
data class CreateUser(
    @Email val email: String,
    @Min(18) val age: Int,
    @Length(min = 2, max = 30) val nickname: String? = null,
)

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String? = null,
)

@Serializable
data class UserPath(
    val id: Long,
)

@Serializable
data class ListQuery(
    @Min(1) @Max(100) val limit: Int = 20,
    val search: String? = null,
)

/** The routes of the reference example (§1), verbatim. */
fun KastApi.exampleApi() {
    info(title = "Users API", version = "1.0.0", description = "KastAPI example")

    post<CreateUser, UserResponse>("/users", summary = "Creates a user", status = 201) { body ->
        UserResponse(id = 1, email = body.email, nickname = body.nickname)
    }

    get<UserPath, UserResponse>("/users/{id}", summary = "Gets a user") { path ->
        UserResponse(id = path.id, email = "demo@kastapi.dev")
    }

    get<ListQuery, List<UserResponse>>("/users", tags = listOf("users")) { _ ->
        emptyList()
    }

    delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
}
