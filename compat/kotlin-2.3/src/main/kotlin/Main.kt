import dev.kastapi.*
import dev.kastapi.validation.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUser(@Email val email: String, @Min(18) val age: Int)

@Serializable
data class User(val id: Long, val email: String)

fun main() = kastApi(port = 8080) {
    post<CreateUser, User>("/users") { body -> User(1, body.email) }
}
