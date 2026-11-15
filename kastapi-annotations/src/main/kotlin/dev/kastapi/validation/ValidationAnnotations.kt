package dev.kastapi.validation

import kotlinx.serialization.SerialInfo

/**
 * El valor numérico debe ser mayor o igual a [value].
 *
 * Se aplica a propiedades numéricas. Con enteros la comparación es exacta; con decimales se compara
 * contra [value] convertido a `Double`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Min(
    public val value: Long,
)

/**
 * El valor numérico debe ser menor o igual a [value].
 *
 * Se aplica a propiedades numéricas. Con enteros la comparación es exacta; con decimales se compara
 * contra [value] convertido a `Double`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Max(
    public val value: Long,
)

/**
 * La longitud de un `String` (en caracteres Unicode) o el tamaño de una `List` debe estar entre [min] y [max],
 * ambos inclusive.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Length(
    public val min: Int = 0,
    public val max: Int = Int.MAX_VALUE,
)

/**
 * El `String` no puede estar vacío ni contener solo espacios en blanco.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class NotBlank

/**
 * El `String` debe parecer una dirección de correo electrónico (`^[^@\s]+@[^@\s]+\.[^@\s]+$`).
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Email

/**
 * El `String` debe coincidir completo con la expresión regular [regex].
 *
 * La expresión se compila una sola vez, al registrar la ruta.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Pattern(
    public val regex: String,
)
