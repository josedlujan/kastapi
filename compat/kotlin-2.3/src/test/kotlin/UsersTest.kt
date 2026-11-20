import dev.kastapi.*
import dev.kastapi.test.kastTest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals

class UsersTest {
    @Test
    fun `an invalid body is rejected and a valid one is created`() =
        kastTest({
            post<CreateUser, User>("/users") { body -> User(1, body.email) }
        }) {
            val invalid =
                client.post("/users") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"x","age":15}""")
                }
            assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)

            val valid =
                client.post("/users") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"a@b.co","age":20}""")
                }
            assertEquals(HttpStatusCode.Created, valid.status)
            assertEquals(User(1, "a@b.co"), valid.bodyAs<User>())
        }
}
