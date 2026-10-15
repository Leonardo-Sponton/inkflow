package io.github.leonardosponton.inkflow.domain.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportTest {
    @Test
    fun `formats have distinct extensions and sane defaults`() {
        assertEquals(ExportFormat.entries.size, ExportFormat.entries.map { it.extension }.toSet().size)
        assertEquals(ExportFormat.PNG, ExportOptions().format)
        assertEquals(1, ProjectArchiver.SCHEMA_VERSION)
        assertTrue(ArchiveError.UnsupportedVersion(9) != ArchiveError.NotAnInkflowFile)
        assertEquals("x", (ArchiveError.Corrupt("x")).reason)
        assertEquals(ExportScope.TAB, ExportOptions().scope)
    }
}
