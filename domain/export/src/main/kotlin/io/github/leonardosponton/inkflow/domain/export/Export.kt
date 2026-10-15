package io.github.leonardosponton.inkflow.domain.export

import io.github.leonardosponton.inkflow.core.common.Outcome
import io.github.leonardosponton.inkflow.core.model.ProjectId
import java.io.InputStream
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    PNG("png", "image/png", "PNG image"),
    PDF("pdf", "application/pdf", "PDF document"),
    SVG("svg", "image/svg+xml", "SVG vector image"),
    INKFLOW("inkflow", "application/zip", "Inkflow project"),
    SQL("sql", "application/sql", "SQL DDL"),
    PLANTUML("puml", "text/plain", "PlantUML"),
    MERMAID("mmd", "text/plain", "Mermaid erDiagram"),
}

enum class ExportScope { TAB, SELECTION, PROJECT }

/** User choices in the export sheet. */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PNG,
    val scope: ExportScope = ExportScope.TAB,
    val scale: Double = 2.0,
    val transparent: Boolean = false,
    val sqlDialect: String = "POSTGRESQL",
)

sealed interface ArchiveError {
    data object NotAnInkflowFile : ArchiveError

    data class UnsupportedVersion(val version: Int) : ArchiveError

    data class Corrupt(val reason: String) : ArchiveError
}

/** `.inkflow` files: a zip with a versioned JSON manifest, one JSON file per tab and the assets. */
interface ProjectArchiver {
    suspend fun export(project: ProjectId, out: OutputStream)

    suspend fun import(input: InputStream): Outcome<ProjectId, ArchiveError>

    companion object {
        const val FORMAT = "inkflow"
        const val SCHEMA_VERSION = 1
    }
}
