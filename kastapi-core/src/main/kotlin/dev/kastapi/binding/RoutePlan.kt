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

/** De dónde sale el valor de una propiedad de la entrada. */
internal enum class ParamLocation(
    val label: String,
) {
    PATH("path"),
    QUERY("query"),
}

/** Tipos escalares que se pueden convertir desde texto (referencia §4). */
internal enum class ScalarType { STRING, INT, LONG, DOUBLE, BOOLEAN, ENUM }

/** Cómo se obtiene y convierte una propiedad de la entrada que viene de path o query. */
internal class ParamSpec(
    val name: String,
    /** Posición de la propiedad en el `SerialDescriptor` de la entrada. */
    val index: Int,
    val location: ParamLocation,
    val scalar: ScalarType,
    val enumNames: List<String>,
    val isList: Boolean,
    /** `true` si la propiedad tiene valor por defecto (es opcional). */
    val hasDefault: Boolean,
    val nullable: Boolean,
)

/** Plan de binding de una ruta junto con los problemas de configuración que se encontraron al calcularlo. */
internal class PlanResult(
    val plan: RoutePlan,
    val problems: List<Problem>,
)

private val PATH_PARAMETER = Regex("\\{([^}/]+)}")

/** Nombres de los parámetros `{nombre}` de [path]; `{id?}` y `{ids...}` se reducen a `id` e `ids`. */
internal fun pathParameterNames(path: String): List<String> =
    PATH_PARAMETER
        .findAll(path)
        .map { it.groupValues[1].removeSuffix("...").removeSuffix("?") }
        .filter { it.isNotBlank() }
        .toList()

/** [path] con los nombres de los parámetros sustituidos: dos rutas con la misma plantilla casan las mismas URLs. */
internal fun routeTemplate(path: String): String = PATH_PARAMETER.replace(path, "{}")

/**
 * Plan de binding de una ruta, calculado una vez al registrarla a partir del `SerialDescriptor` (ADR-0002).
 *
 * En `GET` y `DELETE` todas las propiedades salen de path o query. En `POST`, `PUT` y `PATCH` las que aparecen
 * como `{nombre}` en la ruta salen del path y el resto del cuerpo JSON (referencia §4).
 */
internal class RoutePlan(
    val serializer: KSerializer<*>,
    /** Propiedades que salen de path o query. En rutas con cuerpo, solo las de path. */
    val params: List<ParamSpec>,
    val noInput: Boolean,
    /** `true` si el resto de la entrada se lee del cuerpo JSON. */
    val hasBody: Boolean = false,
    /** Propiedades del cuerpo nullable y sin valor por defecto: si faltan, llegan como `null`. */
    val bodyNullables: List<String> = emptyList(),
    /** Reglas de las anotaciones de validación, compiladas al registrar la ruta. */
    val rules: RuleSet = RuleSet.EMPTY,
) {
    /** Nombres de las propiedades que se leen de la URL y no del cuerpo. */
    val pathNames: Set<String> = params.filter { it.location == ParamLocation.PATH }.mapTo(HashSet()) { it.name }

    companion object {
        private val NO_INPUT_NAME = NoInput.serializer().descriptor.serialName
        private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

        /**
         * Calcula el plan de [serializer] para la ruta [method] [path] (ya con el prefijo del grupo).
         *
         * No lanza excepciones: los problemas de configuración se devuelven en el resultado y los reporta
         * el validador de arranque. Si hay problemas fatales el plan puede estar incompleto.
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

        /** Especificación de una propiedad de path o query, o `null` (con un problema fatal) si no se soporta. */
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
