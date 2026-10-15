package io.github.leonardosponton.inkflow.domain.canvas.notation

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.BasicMarkers
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.ContentLayouter
import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.LayoutRegistry
import io.github.leonardosponton.inkflow.domain.diagram.MarkerDefinition
import io.github.leonardosponton.inkflow.domain.diagram.MarkerRegistry
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.PlainEdge
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

enum class Severity { INFO, WARNING, ERROR }

data class ValidationIssue(val elements: List<ElementId>, val severity: Severity, val message: String)

/** Notation rules surfaced as warnings; they never block the user from sketching. */
fun interface DiagramValidator {
    fun validate(scene: Scene): List<ValidationIssue>
}

/** A plug-in notation: shapes, markers, content layout, serializers and validation rules. */
interface NotationModule {
    val id: NotationId
    val displayName: String
    val shapes: List<ShapeDefinition>
    val markers: List<MarkerDefinition> get() = emptyList()
    val layouters: List<ContentLayouter> get() = emptyList()
    val serializers: SerializersModule get() = SerializersModule { }
    val validators: List<DiagramValidator> get() = emptyList()
}

object BasicNotation : NotationModule {
    override val id = NotationId.Basic
    override val displayName = "Basic"
    override val shapes = BasicShapes.all
    override val markers = BasicMarkers.all
    override val serializers = SerializersModule {
        polymorphic(NodeContent::class) {
            subclass(PlainLabel::class)
            defaultDeserializer { PlainLabel.serializer() }
        }
        polymorphic(EdgeSemantics::class) {
            subclass(PlainEdge::class)
            defaultDeserializer { PlainEdge.serializer() }
        }
    }
}

/** All installed notations merged into the registries the editor and renderer use. */
class NotationCatalog(modules: List<NotationModule>) {
    val modules: List<NotationModule> = listOf(BasicNotation) + modules.filterNot { it.id == NotationId.Basic }
    val shapes = ShapeRegistry(this.modules.flatMap { it.shapes })
    val markers = MarkerRegistry(this.modules.flatMap { it.markers })
    val layouts = LayoutRegistry(this.modules.flatMap { it.layouters })
    val validators: List<DiagramValidator> = this.modules.flatMap { it.validators }
    val serializers: SerializersModule =
        SerializersModule { this@NotationCatalog.modules.forEach { include(it.serializers) } }

    fun validate(scene: Scene): List<ValidationIssue> = validators.flatMap { it.validate(scene) }
}
