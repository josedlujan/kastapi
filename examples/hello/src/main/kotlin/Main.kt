import dev.kastapi.*
import dev.kastapi.validation.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUser(
    @Email val email: String,
    @Min(18) val age: Int,
    @Length(min = 2, max = 30) val nickname: String? = null,
)

@Serializable
data class UserResponse(val id: Long, val email: String, val nickname: String? = null)

@Serializable
data class UserPath(val id: Long)

@Serializable
data class ListQuery(@Min(1) @Max(100) val limit: Int = 20, val search: String? = null)

fun main() = kastApi(port = 8080) {
    info(title = "Users API", version = "1.0.0", description = "Ejemplo de KastAPI")

    post<CreateUser, UserResponse>("/users", summary = "Crea un usuario", status = 201) { body ->
        UserResponse(id = 1, email = body.email, nickname = body.nickname)
    }

    get<UserPath, UserResponse>("/users/{id}", summary = "Obtiene un usuario") { path ->
        UserResponse(id = path.id, email = "demo@kastapi.dev")
    }

    get<ListQuery, List<UserResponse>>("/users", tags = listOf("users")) { query ->
        emptyList()
    }

    delete<UserPath, Unit>("/users/{id}", status = 204) { _ -> }
}
// Swagger UI en http://localhost:8080/docs
// Esquema en http://localhost:8080/openapi.json
