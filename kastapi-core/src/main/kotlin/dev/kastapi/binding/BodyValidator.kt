package dev.kastapi.binding

import dev.kastapi.errors.ErrorCode
import dev.kastapi.errors.ErrorDetail
import dev.kastapi.errors.ErrorLocation
import dev.kastapi.errors.Messages
import dev.kastapi.validation.RuleSet
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Walks [body] against the input's [descriptor] and returns all the structural errors:
 * missing required fields, wrong types and unknown fields (ADR-0002, no reflection).
 *
 * The properties in [skip] come from the path and are not read from the body.
 */
internal fun validateBody(
    descriptor: SerialDescriptor,
    body: JsonElement,
    skip: Set<String>,
    ignoreUnknownKeys: Boolean,
    rules: RuleSet,
): List<ErrorDetail> {
    val validator = BodyValidator(ignoreUnknownKeys, rules)
    if (body is JsonObject) {
        validator.checkClass(descriptor, body, prefix = "", skip = skip)
    } else {
        validator.typeError("", body, Messages.EXPECTED_OBJECT)
    }
    return validator.errors
}

private class BodyValidator(
    private val ignoreUnknownKeys: Boolean,
    private val rules: RuleSet,
) {
    val errors = mutableListOf<ErrorDetail>()

    fun checkClass(
        descriptor: SerialDescriptor,
        obj: JsonObject,
        prefix: String,
        skip: Set<String>,
    ) {
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            if (name in skip) continue

            val element = descriptor.getElementDescriptor(index)
            val field = join(prefix, name)
            val value = obj[name]
            if (value != null) {
                val errorsBefore = errors.size
                checkValue(element, value, field)
                // Annotations are only evaluated on values that passed structural validation.
                if (errors.size == errorsBefore) applyRules(descriptor, index, value, field)
            } else if (!descriptor.isElementOptional(index) && !element.isNullable) {
                add(field, ErrorCode.REQUIRED, Messages.REQUIRED)
            }
        }
        if (!ignoreUnknownKeys) checkUnknownKeys(descriptor, obj, prefix)
    }

    fun typeError(
        field: String,
        element: JsonElement,
        message: String,
    ) {
        add(field, ErrorCode.TYPE, message, element as? JsonPrimitive)
    }

    private fun applyRules(
        descriptor: SerialDescriptor,
        index: Int,
        value: JsonElement,
        field: String,
    ) {
        if (value is JsonNull) return
        for (rule in rules.rulesOf(descriptor, index)) {
            val violation = rule.check(value) ?: continue
            add(field, violation.code, violation.message, value as? JsonPrimitive)
        }
    }

    private fun checkUnknownKeys(
        descriptor: SerialDescriptor,
        obj: JsonObject,
        prefix: String,
    ) {
        val known = (0 until descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it) }
        for (key in obj.keys) {
            if (key !in known) add(join(prefix, key), ErrorCode.UNKNOWN_FIELD, Messages.UNKNOWN_FIELD)
        }
    }

    private fun checkValue(
        descriptor: SerialDescriptor,
        element: JsonElement,
        field: String,
    ) {
        if (element is JsonNull) {
            if (!descriptor.isNullable) add(field, ErrorCode.TYPE, Messages.NOT_NULL)
            return
        }
        if (descriptor.isInline) {
            checkValue(descriptor.getElementDescriptor(0), element, field)
            return
        }

        // Polymorphic, contextual and other types (else branch): the decoder validates them.
        val kind = descriptor.kind
        when {
            kind is PrimitiveKind -> checkPrimitive(kind, element, field)
            kind == SerialKind.ENUM -> checkEnum(descriptor, element, field)
            kind == StructureKind.CLASS -> checkObject(descriptor, element, field)
            kind == StructureKind.LIST -> checkList(descriptor, element, field)
            kind == StructureKind.MAP -> checkMap(descriptor, element, field)
            else -> Unit
        }
    }

    private fun checkPrimitive(
        kind: PrimitiveKind,
        element: JsonElement,
        field: String,
    ) {
        if (!isValidPrimitive(kind, element)) typeError(field, element, expectedPrimitive(kind))
    }

    private fun checkEnum(
        descriptor: SerialDescriptor,
        element: JsonElement,
        field: String,
    ) {
        val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        val valid = element is JsonPrimitive && element.isString && element.content in names
        if (!valid) typeError(field, element, Messages.expectedOneOf(names))
    }

    private fun checkObject(
        descriptor: SerialDescriptor,
        element: JsonElement,
        field: String,
    ) {
        if (element is JsonObject) {
            checkClass(descriptor, element, field, skip = emptySet())
        } else {
            typeError(field, element, Messages.EXPECTED_OBJECT)
        }
    }

    private fun checkList(
        descriptor: SerialDescriptor,
        element: JsonElement,
        field: String,
    ) {
        if (element !is JsonArray) {
            typeError(field, element, Messages.EXPECTED_ARRAY)
            return
        }
        val itemDescriptor = descriptor.getElementDescriptor(0)
        element.forEachIndexed { index, item -> checkValue(itemDescriptor, item, "$field[$index]") }
    }

    private fun checkMap(
        descriptor: SerialDescriptor,
        element: JsonElement,
        field: String,
    ) {
        if (element !is JsonObject) {
            typeError(field, element, Messages.EXPECTED_OBJECT)
            return
        }
        val valueDescriptor = descriptor.getElementDescriptor(1)
        for ((key, value) in element) checkValue(valueDescriptor, value, join(field, key))
    }

    private fun add(
        field: String,
        code: String,
        message: String,
        value: JsonElement? = null,
    ) {
        errors += ErrorDetail(ErrorLocation.BODY, field, code, message, value)
    }

    private fun join(
        prefix: String,
        name: String,
    ): String = if (prefix.isEmpty()) name else "$prefix.$name"
}

private fun isValidPrimitive(
    kind: PrimitiveKind,
    element: JsonElement,
): Boolean {
    if (element !is JsonPrimitive) return false
    val text = element.content
    return when (kind) {
        PrimitiveKind.STRING -> element.isString
        PrimitiveKind.CHAR -> element.isString && text.length == 1
        PrimitiveKind.BOOLEAN -> !element.isString && (text == "true" || text == "false")
        PrimitiveKind.BYTE -> !element.isString && text.toByteOrNull() != null
        PrimitiveKind.SHORT -> !element.isString && text.toShortOrNull() != null
        PrimitiveKind.INT -> !element.isString && text.toIntOrNull() != null
        PrimitiveKind.LONG -> !element.isString && text.toLongOrNull() != null
        PrimitiveKind.FLOAT -> !element.isString && text.toFloatOrNull()?.isFinite() == true
        PrimitiveKind.DOUBLE -> !element.isString && text.toDoubleOrNull()?.isFinite() == true
    }
}

private fun expectedPrimitive(kind: PrimitiveKind): String =
    when (kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> Messages.EXPECTED_TEXT
        PrimitiveKind.BOOLEAN -> Messages.EXPECTED_BOOLEAN
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> Messages.EXPECTED_INTEGER
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> Messages.EXPECTED_DECIMAL
    }
