package dev.kastapi.validation

import dev.kastapi.startup.Problem
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind

/**
 * Reglas de validación de todas las clases alcanzables desde la entrada de una ruta, calculadas una vez al
 * registrarla (ADR-0002: se leen de `SerialDescriptor.getElementAnnotations`, sin reflexión).
 */
internal class RuleSet private constructor(
    private val byClass: Map<String, List<List<Rule>>>,
) {
    /** Reglas de la propiedad número [index] de la clase descrita por [descriptor]. */
    fun rulesOf(
        descriptor: SerialDescriptor,
        index: Int,
    ): List<Rule> = byClass[descriptor.serialName]?.getOrNull(index).orEmpty()

    companion object {
        val EMPTY = RuleSet(emptyMap())

        /**
         * Recorre [root] y todas las clases anidadas (también dentro de listas y mapas).
         * Los problemas de configuración de las anotaciones se añaden a [problems].
         */
        fun build(
            root: SerialDescriptor,
            problems: MutableList<Problem>,
        ): RuleSet {
            val result = mutableMapOf<String, List<List<Rule>>>()
            visit(root, result, problems)
            return RuleSet(result)
        }

        private fun visit(
            descriptor: SerialDescriptor,
            result: MutableMap<String, List<List<Rule>>>,
            problems: MutableList<Problem>,
        ) {
            if (descriptor.isInline) {
                visit(descriptor.getElementDescriptor(0), result, problems)
                return
            }
            when (descriptor.kind) {
                StructureKind.CLASS -> visitClass(descriptor, result, problems)
                StructureKind.LIST -> visit(descriptor.getElementDescriptor(0), result, problems)
                StructureKind.MAP -> visit(descriptor.getElementDescriptor(1), result, problems)
                else -> Unit
            }
        }

        private fun visitClass(
            descriptor: SerialDescriptor,
            result: MutableMap<String, List<List<Rule>>>,
            problems: MutableList<Problem>,
        ) {
            // Evita bucles con tipos recursivos.
            if (descriptor.serialName in result) return

            val indices = 0 until descriptor.elementsCount
            result[descriptor.serialName] =
                indices.map { index ->
                    val property = "${descriptor.serialName}.${descriptor.getElementName(index)}"
                    val element = descriptor.getElementDescriptor(index)
                    rulesFor(descriptor.getElementAnnotations(index), property, element, problems)
                }
            indices.forEach { visit(descriptor.getElementDescriptor(it), result, problems) }
        }
    }
}
