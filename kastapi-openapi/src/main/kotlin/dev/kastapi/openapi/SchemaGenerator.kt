package dev.kastapi.openapi

import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Max
import dev.kastapi.validation.Min
import dev.kastapi.validation.NotBlank
import dev.kastapi.validation.Pattern
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val COMPONENTS_PREFIX = "#/components/schemas/"
private const val REF = "\$ref"

private val NULL_TYPE: JsonObject = schema("type" to JsonPrimitive("null"))

private val NUMERIC_KINDS: Set<SerialKind> =
    setOf(
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE,
    )

/**
 * Converts `SerialDescriptor` into JSON Schema schemas (OpenAPI 3.1), without reflection (ADR-0002).
 *
 * An instance accumulates the schemas of the classes (components) it finds: a class's schema
 * is a `$ref` reference and its definition is kept in [components]. One instance is used per document.
 */
internal class SchemaGenerator {
    private val componentSchemas = LinkedHashMap<String, JsonObject>()
    private val namesByKey = HashMap<ComponentKey, String>()

    /** Definitions of the classes found so far, in the order they were registered. */
    val components: Map<String, JsonObject> get() = componentSchemas

    /** Schema of [descriptor]: inline for primitives, lists, maps and enums; `$ref` for classes. */
    fun schemaOf(descriptor: SerialDescriptor): JsonObject {
        val base = baseSchema(descriptor)
        return if (descriptor.isNullable) base.nullable() else base
    }

    private fun baseSchema(descriptor: SerialDescriptor): JsonObject {
        if (descriptor.isInline) return schemaOf(descriptor.getElementDescriptor(0))
        return when (descriptor.kind) {
            PrimitiveKind.STRING -> schema("type" to JsonPrimitive("string"))
            PrimitiveKind.CHAR -> charSchema()
            PrimitiveKind.BOOLEAN -> schema("type" to JsonPrimitive("boolean"))
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT -> numberSchema("integer", "int32")
            PrimitiveKind.LONG -> numberSchema("integer", "int64")
            PrimitiveKind.FLOAT -> numberSchema("number", "float")
            PrimitiveKind.DOUBLE -> numberSchema("number", "double")
            SerialKind.ENUM -> enumSchema(descriptor)
            StructureKind.LIST -> arraySchema(schemaOf(descriptor.getElementDescriptor(0)))
            StructureKind.MAP -> mapSchema(schemaOf(descriptor.getElementDescriptor(1)))
            StructureKind.CLASS -> componentRef(descriptor)
            StructureKind.OBJECT -> schema("type" to JsonPrimitive("object"))
            else -> JsonObject(emptyMap())
        }
    }

    /**
     * Schema of property [index] of class [owner], with the constraints of its annotations.
     *
     * It does not admit `null`: it is used for path and query parameters, where an absent value already equals `null`.
     */
    fun schemaOfProperty(
        owner: SerialDescriptor,
        index: Int,
    ): JsonObject {
        val element = owner.getElementDescriptor(index)
        val constraints = constraintsOf(owner.getElementAnnotations(index), element)
        return baseSchema(element).withConstraints(constraints)
    }

    /**
     * Schema of a route's body. If [excluded] has no properties of [descriptor] it is the class's own; otherwise
     * it is a derived `<Name>Body` component without those properties (the ones that come from the URL).
     */
    fun bodySchemaOf(
        descriptor: SerialDescriptor,
        excluded: Set<String>,
    ): JsonObject {
        val names = (0 until descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it) }
        val present = excluded.filterTo(HashSet()) { it in names }
        if (present.isEmpty()) return schemaOf(descriptor)

        val base = ComponentKey.of(descriptor)
        val key = base.copy(serialName = "${base.serialName}#${present.sorted().joinToString(",")}")
        val name =
            namesByKey[key]
                ?: register(key, "${base.serialName}Body") { classSchema(descriptor, present) }
        return schema(REF to JsonPrimitive("$COMPONENTS_PREFIX$name"))
    }

    private fun componentRef(descriptor: SerialDescriptor): JsonObject {
        val key = ComponentKey.of(descriptor)
        val name = namesByKey[key] ?: register(key, key.serialName) { classSchema(descriptor) }
        return schema(REF to JsonPrimitive("$COMPONENTS_PREFIX$name"))
    }

    private fun register(
        key: ComponentKey,
        serialName: String,
        build: () -> JsonObject,
    ): String {
        val name = uniqueName(serialName)
        namesByKey[key] = name
        // Reserving the name before generating the properties avoids loops with recursive types.
        componentSchemas[name] = JsonObject(emptyMap())
        componentSchemas[name] = build()
        return name
    }

    /** Simple name; if it already exists, the full name; if that also exists, the full one with a suffix `_2`, `_3`... */
    private fun uniqueName(serialName: String): String {
        val full = serialName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val simple = full.substringAfterLast('.')
        val free = listOf(simple, full).firstOrNull { it !in componentSchemas }
        return free ?: generateSequence(2) { it + 1 }.map { "${full}_$it" }.first { it !in componentSchemas }
    }

    private fun classSchema(
        descriptor: SerialDescriptor,
        excluded: Set<String> = emptySet(),
    ): JsonObject {
        val properties = LinkedHashMap<String, JsonElement>()
        val required = mutableListOf<JsonElement>()
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            if (name in excluded) continue
            val element = descriptor.getElementDescriptor(index)
            val constraints = constraintsOf(descriptor.getElementAnnotations(index), element)
            properties[name] = schemaOf(element).withConstraints(constraints)
            if (!descriptor.isElementOptional(index) && !element.isNullable) required += JsonPrimitive(name)
        }
        val entries = linkedMapOf<String, JsonElement>()
        entries["type"] = JsonPrimitive("object")
        entries["properties"] = JsonObject(properties)
        if (required.isNotEmpty()) entries["required"] = JsonArray(required)
        return JsonObject(entries)
    }
}

/** Identifies a class by its name and the shape of its properties, to tell generics apart. */
private data class ComponentKey(
    val serialName: String,
    val elements: List<Pair<String, String>>,
) {
    companion object {
        fun of(descriptor: SerialDescriptor): ComponentKey =
            ComponentKey(
                descriptor.serialName.removeSuffix("?"),
                (0 until descriptor.elementsCount).map {
                    descriptor.getElementName(it) to descriptor.getElementDescriptor(it).serialName
                },
            )
    }
}

private fun schema(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*entries))

private fun numberSchema(
    type: String,
    format: String,
): JsonObject = schema("type" to JsonPrimitive(type), "format" to JsonPrimitive(format))

private fun charSchema(): JsonObject =
    schema("type" to JsonPrimitive("string"), "minLength" to JsonPrimitive(1), "maxLength" to JsonPrimitive(1))

private fun enumSchema(descriptor: SerialDescriptor): JsonObject {
    val names = (0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }
    return schema("type" to JsonPrimitive("string"), "enum" to JsonArray(names))
}

private fun arraySchema(items: JsonObject): JsonObject = schema("type" to JsonPrimitive("array"), "items" to items)

private fun mapSchema(values: JsonObject): JsonObject =
    schema("type" to JsonPrimitive("object"), "additionalProperties" to values)

/** Version that also admits `null`: `type: [X, "null"]` or `oneOf` if it is a reference. */
private fun JsonObject.nullable(): JsonObject =
    when {
        isEmpty() -> this
        containsKey(REF) -> schema("oneOf" to JsonArray(listOf(this, NULL_TYPE)))
        else -> withNullableType()
    }

private fun JsonObject.withNullableType(): JsonObject {
    val type = this["type"] as? JsonPrimitive ?: return this
    val entries = LinkedHashMap<String, JsonElement>()
    for ((key, value) in this) {
        entries[key] =
            when (key) {
                "type" -> JsonArray(listOf(type, JsonPrimitive("null")))
                "enum" -> JsonArray((value as JsonArray) + JsonNull)
                else -> value
            }
    }
    return JsonObject(entries)
}

private fun JsonObject.withConstraints(constraints: Map<String, JsonElement>): JsonObject =
    if (constraints.isEmpty()) this else JsonObject(toMutableMap().apply { putAll(constraints) })

/** Type of [element]; for a `value class` it is the type it wraps. */
private fun underlyingKind(element: SerialDescriptor): SerialKind {
    var current = element
    while (current.isInline) current = current.getElementDescriptor(0)
    return current.kind
}

/** Constraints from the validation annotations that apply to the type of [element]; the rest are ignored. */
private fun constraintsOf(
    annotations: List<Annotation>,
    element: SerialDescriptor,
): Map<String, JsonElement> {
    val kind = underlyingKind(element)
    return when {
        kind in NUMERIC_KINDS -> numberConstraints(annotations)
        kind == PrimitiveKind.STRING -> textConstraints(annotations)
        kind == StructureKind.LIST -> listConstraints(annotations)
        else -> emptyMap()
    }
}

private fun numberConstraints(annotations: List<Annotation>): Map<String, JsonElement> {
    val result = LinkedHashMap<String, JsonElement>()
    annotations.filterIsInstance<Min>().firstOrNull()?.let { result["minimum"] = JsonPrimitive(it.value) }
    annotations.filterIsInstance<Max>().firstOrNull()?.let { result["maximum"] = JsonPrimitive(it.value) }
    return result
}

private fun textConstraints(annotations: List<Annotation>): Map<String, JsonElement> {
    val result = LinkedHashMap<String, JsonElement>()
    val length = annotations.filterIsInstance<Length>().firstOrNull()
    val notBlankMin = if (annotations.any { it is NotBlank }) 1 else 0
    val minLength = maxOf(length?.min ?: 0, notBlankMin)
    if (minLength > 0) result["minLength"] = JsonPrimitive(minLength)
    if (length != null && length.max < Int.MAX_VALUE) result["maxLength"] = JsonPrimitive(length.max)
    annotations.filterIsInstance<Pattern>().firstOrNull()?.let { result["pattern"] = JsonPrimitive(it.regex) }
    if (annotations.any { it is Email }) result["format"] = JsonPrimitive("email")
    return result
}

private fun listConstraints(annotations: List<Annotation>): Map<String, JsonElement> {
    val result = LinkedHashMap<String, JsonElement>()
    val length = annotations.filterIsInstance<Length>().firstOrNull() ?: return result
    if (length.min > 0) result["minItems"] = JsonPrimitive(length.min)
    if (length.max < Int.MAX_VALUE) result["maxItems"] = JsonPrimitive(length.max)
    return result
}
