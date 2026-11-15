package dev.kastapi.openapi

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val prettyJson = Json { prettyPrint = true }

/**
 * Genera los esquemas de [roots] con un solo generador y los compara, como estructura JSON, con el snapshot
 * `src/test/resources/schemas/[name].json`. Si el snapshot no existe la prueba falla e imprime lo obtenido.
 */
private fun assertSnapshot(
    name: String,
    vararg roots: SerialDescriptor,
) {
    val generator = SchemaGenerator()
    val rootSchemas = roots.map { generator.schemaOf(it) }
    val actual =
        buildJsonObject {
            put("roots", JsonArray(rootSchemas))
            put("components", JsonObject(generator.components))
        }
    val pretty = prettyJson.encodeToString(JsonObject.serializer(), actual)

    val resource = SchemaGeneratorTest::class.java.getResource("/schemas/$name.json")
    if (resource == null) fail("Falta el snapshot schemas/$name.json. Esto es lo que se obtuvo:\n$pretty")

    val expected = Json.parseToJsonElement(resource.readText())
    assertEquals(expected, actual, "El esquema no coincide con el snapshot schemas/$name.json. Obtenido:\n$pretty")
}

class SchemaGeneratorTest {
    @Test
    fun `genera el esquema de todos los tipos primitivos`() =
        assertSnapshot("primitives", AllPrimitives.serializer().descriptor)

    @Test
    fun `genera nullable y opcional y solo exige lo que no tiene default ni es nullable`() =
        assertSnapshot("nullables", Nullables.serializer().descriptor)

    @Test
    fun `genera listas conjuntos listas de listas y mapas`() =
        assertSnapshot("collections", Collected.serializer().descriptor)

    @Test
    fun `genera enums con los nombres serializados`() = assertSnapshot("enums", EnumHolder.serializer().descriptor)

    @Test
    fun `refleja las anotaciones de validacion`() = assertSnapshot("annotations", Annotated.serializer().descriptor)

    @Test
    fun `las clases anidadas son referencias a componentes`() = assertSnapshot("nested", Person.serializer().descriptor)

    @Test
    fun `dos clases con el mismo nombre simple se distinguen usando el nombre completo`() =
        assertSnapshot("collision", Catalog.serializer().descriptor)

    @Test
    fun `los tipos recursivos no entran en bucle`() =
        assertSnapshot(
            "recursive",
            Node.serializer().descriptor,
            TreeNode.serializer().descriptor,
            Even.serializer().descriptor,
        )

    @Test
    fun `el esquema raiz es en linea para primitivos listas mapas y enums`() =
        assertSnapshot(
            "roots",
            String.serializer().descriptor,
            ListSerializer(Person.serializer()).descriptor,
            MapSerializer(String.serializer(), Address.serializer()).descriptor,
            Level.serializer().descriptor,
        )

    @Test
    fun `los genericos con distintos argumentos generan componentes distintos`() =
        assertSnapshot("generics", Boxes.serializer().descriptor)

    @Test
    fun `una value class se documenta como el tipo que envuelve`() =
        assertSnapshot("inline", Account.serializer().descriptor)

    @Test
    fun `los tipos que no se pueden describir generan un esquema abierto`() =
        assertSnapshot("unsupported", Drawing.serializer().descriptor)

    @Test
    fun `una clase usada varias veces genera un solo componente`() {
        val generator = SchemaGenerator()

        val person = generator.schemaOf(Person.serializer().descriptor)
        val address = generator.schemaOf(Address.serializer().descriptor)
        val addressAgain = generator.schemaOf(Address.serializer().descriptor)

        assertEquals(setOf("Person", "Address"), generator.components.keys)
        assertEquals(address, addressAgain)
        assertEquals(buildJsonObject { put("\$ref", "#/components/schemas/Person") }, person)
    }
}
