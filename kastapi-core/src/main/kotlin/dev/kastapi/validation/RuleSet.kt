package dev.kastapi.validation

import dev.kastapi.startup.Problem
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind

/**
 * Validation rules of all the classes reachable from a route's input, computed once when
 * registering it (ADR-0002: they are read from `SerialDescriptor.getElementAnnotations`, no reflection).
 */
internal class RuleSet private constructor(
    private val byClass: Map<String, List<List<Rule>>>,
) {
    /** Rules of property number [index] of the class described by [descriptor]. */
    fun rulesOf(
        descriptor: SerialDescriptor,
        index: Int,
    ): List<Rule> = byClass[descriptor.serialName]?.getOrNull(index).orEmpty()

    companion object {
        val EMPTY = RuleSet(emptyMap())

        /**
         * Walks [root] and all nested classes (also inside lists and maps).
         * Configuration problems with the annotations are added to [problems].
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
            // Avoids loops with recursive types.
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
