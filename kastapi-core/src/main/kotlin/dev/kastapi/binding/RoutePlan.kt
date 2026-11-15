package dev.kastapi.binding

import dev.kastapi.NoInput
import dev.kastapi.errors.Messages
import dev.kastapi.startup.Problem
import dev.kastapi.validation.RuleSet
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/** Where the value of an input property comes from. */
internal enum class ParamLocation(
    val label: String,
) {
    PATH("path"),
    QUERY("query"),
}

/** Scalar types that can be converted from text (reference §4). */
internal enum class ScalarType { STRING, INT, LONG, DOUBLE, BOOLEAN, ENUM }

/** How an input property that comes from path or query is obtained and converted. */
internal class ParamSpec(
    val name: String,
    /** Position of the property in the input's `SerialDescriptor`. */
    val index: Int,
    val location: ParamLocation,
    val scalar: ScalarType,
    val enumNames: List<String>,
    val isList: Boolean,
    /** `true` if the property has a default value (it is optional). */
    val hasDefault: Boolean,
    val nullable: Boolean,
)

/** A route's binding plan together with the configuration problems found while computing it. */
internal class PlanResult(
    val plan: RoutePlan,
    val problems: List<Problem>,
)

private val PATH_PARAMETER = Regex("\\{([^}/]+)}")

/** Names of the `{name}` parameters of [path]; `{id?}` and `{ids...}` are reduced to `id` and `ids`. */
internal fun pathParameterNames(path: String): List<String> =
    PATH_PARAMETER
        .findAll(path)
        .map { it.groupValues[1].removeSuffix("...").removeSuffix("?") }
        .filter { it.isNotBlank() }
        .toList()

/** [path] with the parameter names replaced: two routes with the same template match the same URLs. */
internal fun routeTemplate(path: String): String = PATH_PARAMETER.replace(path, "{}")

/**
 * A route's binding plan, computed once when it is registered from the `SerialDescriptor` (ADR-0002).
 *
 * In `GET` and `DELETE` all properties come from path or query. In `POST`, `PUT` and `PATCH` those that appear
 * as `{name}` in the route come from the path and the rest from the JSON body (reference §4).
 */
internal class RoutePlan(
    val serializer: KSerializer<*>,
    /** Properties that come from path or query. In routes with a body, only the path ones. */
    val params: List<ParamSpec>,
    val noInput: Boolean,
    /** `true` if the rest of the input is read from the JSON body. */
    val hasBody: Boolean = false,
    /** Body properties that are nullable and have no default value: if missing, they arrive as `null`. */
    val bodyNullables: List<String> = emptyList(),
    /** Validation annotation rules, compiled when the route is registered. */
    val rules: RuleSet = RuleSet.EMPTY,
) {
    /** Names of the properties read from the URL rather than from the body. */
    val pathNames: Set<String> = params.filter { it.location == ParamLocation.PATH }.mapTo(HashSet()) { it.name }

    companion object {
        private val NO_INPUT_NAME = NoInput.serializer().descriptor.serialName
        private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

        /**
         * Computes the plan of [serializer] for the route [method] [path] (already with the group prefix).
         *
         * It does not throw: configuration problems are returned in the result and reported by
         * the startup validator. If there are fatal problems the plan may be incomplete.
         */
        fun build(
            method: HttpMethod,
            path: String,
            serializer: KSerializer<*>,
        ): PlanResult {
            val descriptor = serializer.descriptor
            val problems = mutableListOf<Problem>()
            val pathNames = pathParameterNames(path).toSet()

            if (descriptor.serialName == NO_INPUT_NAME) {
                pathNames.forEach { problems += consistency(Messages.pathParameterWithoutProperty(it, NO_INPUT_NAME)) }
                return PlanResult(RoutePlan(serializer, emptyList(), noInput = true), problems)
            }
            if (descriptor.kind != StructureKind.CLASS) {
                problems += Problem(Messages.inputMustBeClass(descriptor.serialName), fatal = true)
                return PlanResult(RoutePlan(serializer, emptyList(), noInput = false), problems)
            }

            val hasBody = method in BODY_METHODS
            val indices = 0 until descriptor.elementsCount
            val propertyNames = indices.mapTo(HashSet()) { descriptor.getElementName(it) }
            pathNames
                .filter { it !in propertyNames }
                .forEach { problems += consistency(Messages.pathParameterWithoutProperty(it, descriptor.serialName)) }

            val fromUrl = indices.filter { !hasBody || descriptor.getElementName(it) in pathNames }
            val plan =
                RoutePlan(
                    serializer = serializer,
                    params = fromUrl.mapNotNull { paramSpec(descriptor, it, pathNames, problems) },
                    noInput = false,
                    hasBody = hasBody,
                    bodyNullables = if (hasBody) nullablesWithoutDefault(descriptor, pathNames) else emptyList(),
                    rules = RuleSet.build(descriptor, problems),
                )
            return PlanResult(plan, problems)
        }

        private fun consistency(message: String): Problem = Problem(message, fatal = false)

        private fun nullablesWithoutDefault(
            descriptor: SerialDescriptor,
            pathNames: Set<String>,
        ): List<String> =
            (0 until descriptor.elementsCount)
                .filter { descriptor.getElementName(it) !in pathNames }
                .filter { descriptor.getElementDescriptor(it).isNullable && !descriptor.isElementOptional(it) }
                .map { descriptor.getElementName(it) }

        /** Specification of a path or query property, or `null` (with a fatal problem) if it is not supported. */
        private fun paramSpec(
            descriptor: SerialDescriptor,
            index: Int,
            pathNames: Set<String>,
            problems: MutableList<Problem>,
        ): ParamSpec? {
            val name = descriptor.getElementName(index)
            val element = descriptor.getElementDescriptor(index)
            val location = if (name in pathNames) ParamLocation.PATH else ParamLocation.QUERY
            val isList = element.kind == StructureKind.LIST
            val scalarDescriptor = if (isList) element.getElementDescriptor(0) else element
            val scalar = scalarTypeOf(scalarDescriptor)

            if (scalar == null || (isList && location == ParamLocation.PATH)) {
                problems += Problem(Messages.unsupportedParameter(name, element.serialName), fatal = true)
                return null
            }
            val hasDefault = descriptor.isElementOptional(index)
            if (location == ParamLocation.PATH) checkPathProperty(name, element, hasDefault, problems)

            return ParamSpec(
                name = name,
                index = index,
                location = location,
                scalar = scalar,
                enumNames = enumNamesOf(scalarDescriptor),
                isList = isList,
                hasDefault = hasDefault,
                nullable = element.isNullable,
            )
        }

        private fun checkPathProperty(
            name: String,
            element: SerialDescriptor,
            hasDefault: Boolean,
            problems: MutableList<Problem>,
        ) {
            if (element.isNullable) problems += consistency(Messages.pathPropertyNullable(name))
            if (hasDefault) problems += consistency(Messages.pathPropertyHasDefault(name))
        }

        private fun scalarTypeOf(descriptor: SerialDescriptor): ScalarType? =
            when (descriptor.kind) {
                PrimitiveKind.STRING -> ScalarType.STRING
                PrimitiveKind.INT -> ScalarType.INT
                PrimitiveKind.LONG -> ScalarType.LONG
                PrimitiveKind.DOUBLE -> ScalarType.DOUBLE
                PrimitiveKind.BOOLEAN -> ScalarType.BOOLEAN
                SerialKind.ENUM -> ScalarType.ENUM
                else -> null
            }

        private fun enumNamesOf(descriptor: SerialDescriptor): List<String> =
            if (descriptor.kind == SerialKind.ENUM) {
                (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
            } else {
                emptyList()
            }
    }
}
