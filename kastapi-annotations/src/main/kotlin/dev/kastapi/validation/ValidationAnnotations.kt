package dev.kastapi.validation

import kotlinx.serialization.SerialInfo

/**
 * The numeric value must be greater than or equal to [value].
 *
 * Applies to numeric properties. With integers the comparison is exact; with decimals it is compared
 * against [value] converted to `Double`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Min(
    public val value: Long,
)

/**
 * The numeric value must be less than or equal to [value].
 *
 * Applies to numeric properties. With integers the comparison is exact; with decimals it is compared
 * against [value] converted to `Double`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Max(
    public val value: Long,
)

/**
 * The length of a `String` (in Unicode characters) or the size of a `List` must be between [min] and [max],
 * both inclusive.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Length(
    public val min: Int = 0,
    public val max: Int = Int.MAX_VALUE,
)

/**
 * The `String` must not be empty or contain only whitespace.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class NotBlank

/**
 * The `String` must look like an email address (`^[^@\s]+@[^@\s]+\.[^@\s]+$`).
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Email

/**
 * The `String` must match the regular expression [regex] entirely.
 *
 * The expression is compiled only once, when the route is registered. To protect the server from expressions that
 * backtrack a lot, a text longer than `KastApiConfig.maxPatternInputLength` is not matched and a match that takes
 * longer than `KastApiConfig.patternTimeoutMillis` is stopped; both are a `pattern` error.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Pattern(
    public val regex: String,
)
