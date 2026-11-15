package dev.kastapi.validation

import dev.kastapi.errors.ErrorCode
import dev.kastapi.errors.Messages
import dev.kastapi.startup.Problem
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.regex.PatternSyntaxException

/** Incumplimiento de una regla: el `code` y el `message` del detalle de error. */
internal class Violation(
    val code: String,
    val message: String,
)

/**
 * Regla de validación derivada de una anotación (referencia §5).
 *
 * Si el valor no es del tipo al que se aplica la regla, [check] devuelve `null`: las anotaciones en tipos
 * incompatibles las detecta la validación al arrancar.
 */
internal sealed interface Rule {
    fun check(value: JsonElement): Violation?
}

internal class MinRule(
    private val min: Long,
) : Rule {
    override fun check(value: JsonElement): Violation? {
        val comparison = compareToBound(value, min) ?: return null
        return if (comparison < 0) Violation(ErrorCode.MIN, Messages.min(min)) else null
    }
}

internal class MaxRule(
    private val max: Long,
) : Rule {
    override fun check(value: JsonElement): Violation? {
        val comparison = compareToBound(value, max) ?: return null
        return if (comparison > 0) Violation(ErrorCode.MAX, Messages.max(max)) else null
    }
}

internal class LengthRule(
    private val min: Int,
    private val max: Int,
) : Rule {
    override fun check(value: JsonElement): Violation? {
        val (size, unit) = measure(value) ?: return null
        val outOfRange = size < min || size > max
        return if (outOfRange) Violation(ErrorCode.LENGTH, Messages.length(min, max, unit)) else null
    }
}

internal object NotBlankRule : Rule {
    override fun check(value: JsonElement): Violation? {
        val text = value.stringOrNull() ?: return null
        return if (text.isBlank()) Violation(ErrorCode.NOT_BLANK, Messages.NOT_BLANK) else null
    }
}

internal object EmailRule : Rule {
    override fun check(value: JsonElement): Violation? {
        val text = value.stringOrNull() ?: return null
        return if (isEmail(text)) null else Violation(ErrorCode.EMAIL, Messages.EMAIL)
    }

    /**
     * Equivale a `^[^@\s]+@[^@\s]+\.[^@\s]+$` pero en una sola pasada: un regex con esa forma tiene coste
     * cuadrático ante entradas hostiles (por ejemplo `a@` + muchos puntos + un espacio).
     */
    private fun isEmail(text: String): Boolean {
        val at = text.indexOf('@')
        if (at < 1 || text.indexOf('@', at + 1) != -1) return false
        if (text.any(::isRegexWhitespace)) return false
        // El dominio necesita un punto con al menos un carácter antes y otro después.
        val dot = text.indexOf('.', at + 2)
        return dot != -1 && dot < text.length - 1
    }

    /** El `\s` de Java sin `UNICODE_CHARACTER_CLASS`: espacio, `\t`, `\n`, `\u000B`, `\u000C` y `\r`. */
    private fun isRegexWhitespace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'
}

internal class PatternRule(
    private val regex: Regex,
) : Rule {
    override fun check(value: JsonElement): Violation? {
        val text = value.stringOrNull() ?: return null
        return if (regex.matches(text)) null else Violation(ErrorCode.PATTERN, Messages.PATTERN)
    }
}

/** Tipos a los que se puede aplicar cada anotación (referencia §5). */
private enum class Applies(
    val description: String,
) {
    NUMBER(Messages.APPLIES_TO_NUMBER),
    TEXT(Messages.APPLIES_TO_TEXT),
    TEXT_OR_LIST(Messages.APPLIES_TO_TEXT_OR_LIST),
    ;

    fun accepts(kind: SerialKind): Boolean =
        when (this) {
            NUMBER -> kind in NUMERIC_KINDS
            TEXT -> kind == PrimitiveKind.STRING
            TEXT_OR_LIST -> kind == PrimitiveKind.STRING || kind == StructureKind.LIST
        }
}

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
 * Convierte las anotaciones de una propiedad en reglas. Los regex de `@Pattern` se compilan aquí, una sola vez.
 *
 * Una anotación en un tipo incompatible no genera regla y se añade a [problems] como problema de consistencia;
 * un regex inválido se añade como problema fatal.
 *
 * @param property nombre `Clase.propiedad`, solo para los mensajes de configuración.
 * @param element descriptor del tipo de la propiedad.
 */
internal fun rulesFor(
    annotations: List<Annotation>,
    property: String,
    element: SerialDescriptor,
    problems: MutableList<Problem>,
): List<Rule> {
    val kind = underlyingKind(element)
    val rules = mutableListOf<Rule>()

    fun add(
        name: String,
        applies: Applies,
        create: () -> Rule?,
    ) {
        if (applies.accepts(kind)) {
            create()?.let { rules += it }
        } else {
            val message = Messages.incompatibleAnnotation(name, property, element.serialName, applies.description)
            problems += Problem(message, fatal = false)
        }
    }

    for (annotation in annotations) {
        when (annotation) {
            is Min -> add("Min", Applies.NUMBER) { MinRule(annotation.value) }
            is Max -> add("Max", Applies.NUMBER) { MaxRule(annotation.value) }
            is Length -> add("Length", Applies.TEXT_OR_LIST) { LengthRule(annotation.min, annotation.max) }
            is NotBlank -> add("NotBlank", Applies.TEXT) { NotBlankRule }
            is Email -> add("Email", Applies.TEXT) { EmailRule }
            is Pattern -> add("Pattern", Applies.TEXT) { patternRule(annotation.regex, property, problems) }
            else -> Unit
        }
    }
    return rules
}

/** Tipo de [element]; para una `value class` es el tipo que envuelve. */
private fun underlyingKind(element: SerialDescriptor): SerialKind {
    var current = element
    while (current.isInline) current = current.getElementDescriptor(0)
    return current.kind
}

private fun patternRule(
    regex: String,
    property: String,
    problems: MutableList<Problem>,
): Rule? =
    try {
        PatternRule(Regex(regex))
    } catch (_: PatternSyntaxException) {
        problems += Problem(Messages.invalidPattern(property, regex), fatal = true)
        null
    }

/** Texto de un valor numérico JSON, o `null` si no es un número. */
private fun JsonElement.numberText(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.content

/** Contenido de un valor de texto JSON, o `null` si no es un texto. */
private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Compara un número con [bound]: exacto con enteros y como `Double` con decimales.
 * Devuelve `null` si [value] no es numérico.
 */
private fun compareToBound(
    value: JsonElement,
    bound: Long,
): Int? {
    val text = value.numberText() ?: return null
    return text.toLongOrNull()?.compareTo(bound) ?: text.toDoubleOrNull()?.compareTo(bound.toDouble())
}

/** Tamaño y unidad de un texto (en caracteres Unicode) o de una lista; `null` para otros valores. */
private fun measure(value: JsonElement): Pair<Int, String>? =
    if (value is JsonArray) {
        value.size to Messages.UNIT_ITEMS
    } else {
        value.stringOrNull()?.let { it.codePointCount(0, it.length) to Messages.UNIT_CHARACTERS }
    }
