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
 * Generates the schemas of [roots] with a single generator and compares them, as a JSON structure, with the snapshot
 * `src/test/resources/schemas/[name].json`. If the snapshot does not exist the test fails and prints what was obtained.
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
    if (resource == null) fail("Missing snapshot schemas/$name.json. This is what was obtained:\n$pretty")

    val expected = Json.parseToJsonElement(resource.readText())
    assertEquals(expected, actual, "The schema does not match the snapshot schemas/$name.json. Got:\n$pretty")
}

class SchemaGeneratorTest {
    @Test
    fun `generates the schema of all primitive types`() =
        assertSnapshot("primitives", AllPrimitives.serializer().descriptor)

    @Test
    fun `generates nullable and optional and only requires what has no default and is not nullable`() =
        assertSnapshot("nullables", Nullables.serializer().descriptor)

    @Test
    fun `generates lists and sets and lists of lists and maps`() =
        assertSnapshot("collections", Collected.serializer().descriptor)

    @Test
    fun `generates enums with the serialized names`() = assertSnapshot("enums", EnumHolder.serializer().descriptor)

    @Test
    fun `reflects the validation annotations`() = assertSnapshot("annotations", Annotated.serializer().descriptor)

    @Test
    fun `nested classes are references to components`() = assertSnapshot("nested", Person.serializer().descriptor)

    @Test
    fun `two classes with the same simple name are told apart using the full name`() =
        assertSnapshot("collision", Catalog.serializer().descriptor)

    @Test
    fun `recursive types do not loop`() =
        assertSnapshot(
            "recursive",
            Node.serializer().descriptor,
            TreeNode.serializer().descriptor,
            Even.serializer().descriptor,
        )

    @Test
    fun `the root schema is inline for primitives and lists and maps and enums`() =
        assertSnapshot(
            "roots",
            String.serializer().descriptor,
            ListSerializer(Person.serializer()).descriptor,
            MapSerializer(String.serializer(), Address.serializer()).descriptor,
            Level.serializer().descriptor,
        )

    @Test
    fun `generics with different arguments generate different components`() =
        assertSnapshot("generics", Boxes.serializer().descriptor)

    @Test
    fun `a value class is documented as the type it wraps`() = assertSnapshot("inline", Account.serializer().descriptor)

    @Test
    fun `types that cannot be described generate an open schema`() =
        assertSnapshot("unsupported", Drawing.serializer().descriptor)

    @Test
    fun `a class used several times generates a single component`() {
        val generator = SchemaGenerator()

        val person = generator.schemaOf(Person.serializer().descriptor)
        val address = generator.schemaOf(Address.serializer().descriptor)
        val addressAgain = generator.schemaOf(Address.serializer().descriptor)

        assertEquals(setOf("Person", "Address"), generator.components.keys)
        assertEquals(address, addressAgain)
        assertEquals(buildJsonObject { put("\$ref", "#/components/schemas/Person") }, person)
    }
}
