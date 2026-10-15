package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable

/** Changes only the default palette a tab shows; any element can live on any tab. */
enum class DiagramProfile { FREEFORM, UML, DATABASE, FLOWCHART }

enum class GridStyle { PLAIN, DOTS, SQUARES, ISOMETRIC }

@Serializable
data class Layer(
    val id: LayerId,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
) {
    companion object {
        val Default = Layer(LayerId.Default, "Layer 1")
    }
}

/** Tab color tags shown on the tab strip. */
enum class TabColor { NONE, RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE }
