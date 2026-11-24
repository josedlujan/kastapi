package dev.kastapi.validation

import dev.kastapi.errors.ErrorCode
import dev.kastapi.errors.Messages
import dev.kastapi.startup.Problem
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.regex.PatternSyntaxException

/** A rule violation: the `code` and `message` of the error detail. */
internal class Violation(
    val code: String,
    val message: String,
)

/**
 * Validation rule derived from an annotation (reference §5).
 *
 * If the value is not of the type the rule applies to, [check] returns `null`: annotations on
 * incompatible types are detected by startup validation.
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
     * Equivalent to `^[^@\s]+@[^@\s]+\.[^@\s]+$` but in a single pass: a regex of that form has quadratic cost
     * on hostile inputs (for example `a@` + many dots + a space).
     */
    private fun isEmail(text: String): Boolean {
        val at = text.indexOf('@')
        if (at < 1 || text.indexOf('@', at + 1) != -1) return false
        if (text.any(::isRegexWhitespace)) return false
        // The domain needs a dot with at least one character before it and one after it.
        val dot = text.indexOf('.', at + 2)
        return dot != -1 && dot < text.length - 1
    }

    /** Java's `\s` without `UNICODE_CHARACTER_CLASS`: space, `\t`, `\n`, `\u000B`, `\u000C` and `\r`. */
    private fun isRegexWhitespace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'
}

/**
 * Limits that keep `@Pattern` from being a way to burn CPU: a text longer than [maxLength] is not matched, and a
 * match that takes longer than [timeoutMillis] is stopped. Both cases are a `pattern` violation.
 */
internal class PatternLimits(
    val maxLength: Int,
    val timeoutMillis: Long,
)

internal class PatternRule(
    private val regex: Regex,
    private val limits: PatternLimits,
    private val property: String,
) : Rule {
    override fun check(value: JsonElement): Violation? {
        val text = value.stringOrNull() ?: return null
        if (text.length > limits.maxLength) return violation()
        val deadline = System.nanoTime() + limits.timeoutMillis * NANOS_PER_MILLI
        return try {
            if (regex.matches(DeadlineSequence(text, deadline))) null else violation()
        } catch (_: MatchTimeout) {
            // Never log the value: it comes from the client.
            logger.warn(Messages.patternTimeout(property, limits.timeoutMillis))
            violation()
        }
    }

    private fun violation() = Violation(ErrorCode.PATTERN, Messages.PATTERN)
}

private val logger = KtorSimpleLogger("dev.kastapi.validation.Pattern")

private const val NANOS_PER_MILLI = 1_000_000L

/** Reads are counted so the clock is checked once every [CHECK_EVERY] characters, not on each one. */
private const val CHECK_EVERY = 1024

private class MatchTimeout : RuntimeException(null, null, false, false)

/** The matcher reads the text through this view, which gives up once the deadline has passed. */
private class DeadlineSequence(
    private val text: CharSequence,
    private val deadline: Long,
    private var reads: Int = 0,
) : CharSequence {
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        if (++reads % CHECK_EVERY == 0 && System.nanoTime() - deadline > 0) throw MatchTimeout()
        return text[index]
    }

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = text.subSequence(startIndex, endIndex)

    override fun toString(): String = text.toString()
}

/** Types each annotation can be applied to (reference §5). */
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
 * Turns a property's annotations into rules. `@Pattern` regexes are compiled here, only once.
 *
 * An annotation on an incompatible type produces no rule and is added to [problems] as a consistency problem;
 * an invalid regex is added as a fatal problem.
 *
 * @param property `Class.property` name, only for configuration messages.
 * @param element descriptor of the property's type.
 */
internal fun rulesFor(
    annotations: List<Annotation>,
    property: String,
    element: SerialDescriptor,
    problems: MutableList<Problem>,
    limits: PatternLimits,
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
            is Pattern -> add("Pattern", Applies.TEXT) { patternRule(annotation.regex, property, problems, limits) }
            else -> Unit
        }
    }
    return rules
}

/** Type of [element]; for a `value class` it is the type it wraps. */
private fun underlyingKind(element: SerialDescriptor): SerialKind {
    var current = element
    while (current.isInline) current = current.getElementDescriptor(0)
    return current.kind
}

private fun patternRule(
    regex: String,
    property: String,
    problems: MutableList<Problem>,
    limits: PatternLimits,
): Rule? =
    try {
        PatternRule(Regex(regex), limits, property)
    } catch (_: PatternSyntaxException) {
        problems += Problem(Messages.invalidPattern(property, regex), fatal = true)
        null
    }

/** Text of a JSON numeric value, or `null` if it is not a number. */
private fun JsonElement.numberText(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.content

/** Content of a JSON text value, or `null` if it is not text. */
private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Compares a number with [bound]: exactly for integers and as `Double` for decimals.
 * Returns `null` if [value] is not numeric.
 */
private fun compareToBound(
    value: JsonElement,
    bound: Long,
): Int? {
    val text = value.numberText() ?: return null
    return text.toLongOrNull()?.compareTo(bound) ?: text.toDoubleOrNull()?.compareTo(bound.toDouble())
}

/** Size and unit of a text (in Unicode characters) or of a list; `null` for other values. */
private fun measure(value: JsonElement): Pair<Int, String>? =
    if (value is JsonArray) {
        value.size to Messages.UNIT_ITEMS
    } else {
        value.stringOrNull()?.let { it.codePointCount(0, it.length) to Messages.UNIT_CHARACTERS }
    }
