package io.github.leonardosponton.inkflow.feature.palette

import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteGroupsTest {
    private val other = BasicShapes.Rectangle.copy(key = io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey("x.box"), notation = NotationId("x"), displayName = "X box")
    private val registry = ShapeRegistry(BasicShapes.all + other)

    @Test
    fun recentFirstThenPreferredNotation() {
        val groups = PaletteGroups.build(registry, "", NotationId("x"), listOf(BasicShapes.Diamond.key))
        assertTrue(groups[0].isRecent)
        assertEquals(NotationId("x"), groups[1].notation)
        assertEquals(NotationId.Basic, groups[2].notation)
    }

    @Test
    fun searchFiltersAndHidesRecent() {
        val groups = PaletteGroups.build(registry, "diam", null, listOf(BasicShapes.Diamond.key))
        assertEquals(1, groups.size)
        assertEquals(listOf(BasicShapes.Diamond), groups[0].shapes)
    }
}
