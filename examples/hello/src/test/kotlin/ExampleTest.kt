import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ExampleTest {
    private fun referenceExample(): String {
        val reference = File(System.getProperty("reference.path")).readText()
        val section = reference.substringAfter("## 1. Hola mundo").substringBefore("\n## 2.")
        return section.substringAfter("```kotlin\n").substringBefore("\n```")
    }

    @Test
    fun `Main kt es exactamente el Hola mundo de la referencia`() {
        val main = File(System.getProperty("main.path")).readText()
        assertEquals(referenceExample().trim(), main.trim())
    }

    @Test
    fun `humo - docs y openapi json responden en el ejemplo arrancado`() {
        try {
            ServerSocket(8080).close()
        } catch (e: java.io.IOException) {
            fail("El puerto 8080 está ocupado; libéralo para ejecutar la prueba de humo: ${e.message}")
        }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process =
            ProcessBuilder(java, "-cp", System.getProperty("hello.classpath"), "MainKt")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        try {
            val http = HttpClient.newHttpClient()

            fun get(path: String): HttpResponse<String>? =
                try {
                    http.send(
                        HttpRequest.newBuilder(URI("http://localhost:8080$path")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                } catch (_: java.io.IOException) {
                    null
                }

            val deadline = System.currentTimeMillis() + 30_000
            var docs = get("/docs")
            while (docs == null && System.currentTimeMillis() < deadline) {
                assertTrue(process.isAlive, "El ejemplo terminó antes de arrancar")
                Thread.sleep(250)
                docs = get("/docs")
            }
            assertEquals(200, docs?.statusCode(), "/docs no respondió 200")
            assertTrue(docs!!.body().contains("swagger-ui"), "/docs no sirve Swagger UI")

            val openapi = get("/openapi.json")
            assertEquals(200, openapi?.statusCode(), "/openapi.json no respondió 200")
            assertTrue(openapi!!.body().contains("Users API"))
            assertTrue(openapi.body().contains("/users/{id}"))
        } finally {
            process.destroy()
            process.waitFor()
        }
    }
}
