package dev.kastapi.openapi

import dev.kastapi.validation.Email
import dev.kastapi.validation.Length
import dev.kastapi.validation.Max
import dev.kastapi.validation.Min
import dev.kastapi.validation.NotBlank
import dev.kastapi.validation.Pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.kastapi.openapi.first.Product as FirstProduct
import dev.kastapi.openapi.second.Product as SecondProduct

@Serializable
data class AllPrimitives(
    val textValue: String,
    val flagValue: Boolean,
    val byteValue: Byte,
    val shortValue: Short,
    val intValue: Int,
    val longValue: Long,
    val floatValue: Float,
    val doubleValue: Double,
    val charValue: Char,
)

@Serializable
enum class Level { LOW, HIGH }

@Serializable
enum class Status {
    @SerialName("in_progress")
    IN_PROGRESS,
    DONE,
}

@Serializable
data class EnumHolder(
    val level: Level,
    val status: Status,
    val levels: List<Level>,
)

@Serializable
data class Inner(
    val id: Long,
)

@Serializable
data class Nullables(
    val nullableText: String?,
    val nullableInt: Int? = null,
    val nullableList: List<String>?,
    val nullableEnum: Level?,
    val nullableObject: Inner?,
    val withDefault: String = "x",
    val requiredText: String,
)

@Serializable
data class Entry(
    val name: String,
)

@Serializable
data class Collected(
    val tags: List<String>,
    val numbers: Set<Int>,
    val matrix: List<List<Int>>,
    val entries: List<Entry>,
    val scores: Map<String, Int>,
    val byName: Map<String, Entry>,
)

@Serializable
data class Annotated(
    @Min(18) val age: Int,
    @Max(100) val score: Int,
    @Min(1) @Max(10) val rating: Double,
    @Length(min = 2, max = 30) val nickname: String,
    @Length(min = 1) val minOnly: String,
    @Length(max = 5) val maxOnly: String,
    @Length(min = 1, max = 3) val tags: List<String>,
    @Pattern("^[A-Z]{3}$") val code: String,
    @Email val email: String,
    @NotBlank val title: String,
    @NotBlank @Length(min = 3, max = 8) val label: String,
    @Email val backupEmail: String? = null,
    @Length(max = 2) val optionalTags: List<String>? = null,
)

@Serializable
data class Address(
    val street: String,
    val zip: String,
)

@Serializable
data class Person(
    val name: String,
    val address: Address,
    val previous: List<Address>,
)

@Serializable
data class Catalog(
    val first: FirstProduct,
    val second: SecondProduct,
)

@Serializable
data class Node(
    val value: Int,
    val children: List<Node>,
)

@Serializable
data class TreeNode(
    val left: TreeNode? = null,
    val right: TreeNode? = null,
)

@Serializable
data class Even(
    val next: Odd?,
)

@Serializable
data class Odd(
    val next: Even?,
)

@Serializable
data class Box<T>(
    val value: T,
)

@Serializable
data class Boxes(
    val ints: Box<Int>,
    val texts: Box<String>,
)

@JvmInline
@Serializable
value class UserId(
    val value: Long,
)

@Serializable
data class Account(
    val id: UserId,
    val owner: UserId?,
)

@Serializable
sealed class Shape {
    @Serializable
    data class Circle(
        val radius: Double,
    ) : Shape()
}

@Serializable
data class Drawing(
    val shape: Shape,
    val nothing: Unit,
)
