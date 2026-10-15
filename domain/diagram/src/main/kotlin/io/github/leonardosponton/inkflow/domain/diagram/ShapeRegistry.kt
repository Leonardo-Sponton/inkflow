package io.github.leonardosponton.inkflow.domain.diagram

/** Every shape definition the app knows, grouped by notation. */
class ShapeRegistry(definitions: Iterable<ShapeDefinition>) {
    private val byKey: Map<ShapeTypeKey, ShapeDefinition> = definitions.associateBy { it.key }

    val all: List<ShapeDefinition> = byKey.values.toList()

    val notations: List<NotationId> = all.map { it.notation }.distinct()

    operator fun get(key: ShapeTypeKey): ShapeDefinition? = byKey[key]

    /** Definition for [key], or the basic rectangle so unknown (future) shapes still render. */
    fun resolve(key: ShapeTypeKey): ShapeDefinition = byKey[key] ?: BasicShapes.Rectangle

    fun inNotation(id: NotationId): List<ShapeDefinition> = all.filter { it.notation == id }

    fun search(query: String): List<ShapeDefinition> = all.filter { it.matches(query) }
}

/** Picks the layouter for a content type; the plain label layouter is the fallback. */
class LayoutRegistry(private val layouters: List<ContentLayouter>) {
    fun layouterFor(content: NodeContent): ContentLayouter = layouters.firstOrNull { it.supports(content) } ?: PlainLabelLayouter
}
