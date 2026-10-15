package io.github.leonardosponton.inkflow.feature.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import io.github.leonardosponton.inkflow.core.common.DefaultDispatcher
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.dbmodel.DdlGenerator
import io.github.leonardosponton.inkflow.domain.dbmodel.MermaidExporter
import io.github.leonardosponton.inkflow.domain.dbmodel.SqlDialect
import io.github.leonardosponton.inkflow.domain.export.ExportFormat
import io.github.leonardosponton.inkflow.domain.export.ExportOptions
import io.github.leonardosponton.inkflow.domain.export.ExportScope
import io.github.leonardosponton.inkflow.domain.export.ProjectArchiver
import io.github.leonardosponton.inkflow.domain.project.AssetStore
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.uml.PlantUmlExporter
import io.github.leonardosponton.inkflow.feature.canvasrender.AndroidTextMetrics
import io.github.leonardosponton.inkflow.feature.canvasrender.SceneDrawer
import io.github.leonardosponton.inkflow.feature.canvasrender.SceneRasterizer
import io.github.leonardosponton.inkflow.feature.canvasrender.SvgWriter
import io.github.leonardosponton.inkflow.feature.canvasrender.SyncImageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.min

/** What to export: the active tab (and selection) of a project. */
data class ExportTarget(val project: ProjectId, val tab: TabId, val selection: Set<ElementId>, val darkCanvas: Boolean)

sealed interface ExportResult {
    data object Done : ExportResult

    data class Failed(val message: String) : ExportResult
}

/** Renders tabs to PNG/PDF/SVG, writes `.inkflow` archives and text exports (SQL, PlantUML, Mermaid). */
@Singleton
class ExportService
@Inject
constructor(
    private val projects: ProjectRepository,
    private val scenes: SceneRepository,
    private val assets: AssetStore,
    private val archiver: ProjectArchiver,
    private val catalog: NotationCatalog,
    @param:DefaultDispatcher private val background: CoroutineDispatcher,
) {
    suspend fun export(target: ExportTarget, options: ExportOptions, out: OutputStream): ExportResult =
        withContext(background) {
            runCatching { write(target, options, out) }.getOrElse { ExportResult.Failed(it.message ?: it::class.simpleName.orEmpty()) }
        }

    private suspend fun write(target: ExportTarget, options: ExportOptions, out: OutputStream): ExportResult {
        run {
            when (options.format) {
                ExportFormat.INKFLOW -> archiver.export(target.project, out)
                ExportFormat.PNG -> png(target, options, out)
                ExportFormat.PDF -> pdf(target, options, out)
                ExportFormat.SVG -> out.write(svg(target, options).toByteArray())
                ExportFormat.SQL -> out.write(sql(target, options).toByteArray())
                ExportFormat.PLANTUML -> out.write(PlantUmlExporter.export(scene(target)).toByteArray())
                ExportFormat.MERMAID -> out.write(MermaidExporter.export(scene(target)).toByteArray())
            }
            out.flush()
        }
        return ExportResult.Done
    }

    /** Suggested file name, e.g. `My project - Board.png`. */
    suspend fun fileName(target: ExportTarget, format: ExportFormat): String {
        val p = projects.observeProject(target.project).first()
        val tab = p?.tabs?.firstOrNull { it.id == target.tab }?.title
        val base = if (format == ExportFormat.INKFLOW || tab == null) p?.name.orEmpty() else "${p?.name} - $tab"
        return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "inkflow" } + "." + format.extension
    }

    private suspend fun scene(target: ExportTarget): Scene = scenes.load(target.tab)

    private suspend fun sql(target: ExportTarget, options: ExportOptions) =
        DdlGenerator(SqlDialect.valueOf(options.sqlDialect)).generate(scene(target))

    private suspend fun elements(target: ExportTarget, scope: ExportScope): List<CanvasElement> {
        val scene = scene(target)
        if (scope != ExportScope.SELECTION || target.selection.isEmpty()) return scene.ordered
        val ids = target.selection.flatMap { id -> listOf(id) + scene.descendantsOf(id).map { it.id } }.toSet()
        return scene.ordered.filter { it.id in ids }
    }

    private fun drawer(project: ProjectId) = SceneDrawer(
        catalog,
        AndroidTextMetrics(),
        SyncImageSource { a -> assets.pathFor(project, a)?.let(::File) },
        forcePathInk = true,
    )

    private suspend fun png(target: ExportTarget, options: ExportOptions, out: OutputStream) {
        val els = elements(target, options.scope)
        require(els.isNotEmpty()) { "Nothing to export" }
        val bmp = SceneRasterizer(drawer(target.project)).render(
            Scene.of(els),
            els,
            SceneRasterizer.Options(
                scale = options.scale,
                transparent = options.transparent,
                darkCanvas = target.darkCanvas && !options.transparent,
            ),
        ) ?: error("Nothing to export")
        bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        bmp.recycle()
    }

    /** One page per tab (whole project) or a single page; 1 world unit = 1 PostScript point. */
    private suspend fun pdf(target: ExportTarget, options: ExportOptions, out: OutputStream) {
        val pages: List<List<CanvasElement>> = if (options.scope == ExportScope.PROJECT) {
            val tabs = projects.observeProject(target.project).first()?.tabs.orEmpty()
            tabs.map { scenes.load(it.id).ordered }.filter { it.isNotEmpty() }
        } else {
            listOf(elements(target, options.scope))
        }
        require(pages.isNotEmpty()) { "Nothing to export" }
        val raster = SceneRasterizer(drawer(target.project))
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, els ->
                val area = raster.contentBounds(els, SceneRasterizer.DEFAULT_PADDING) ?: return@forEachIndexed
                val scale = min(1.0, MAX_PAGE / maxOf(area.width, area.height))
                val w = ceil(area.width * scale).toInt()
                val h = ceil(area.height * scale).toInt()
                val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val page = doc.startPage(info)
                raster.draw(page.canvas, area, scale, els, SceneRasterizer.Options(transparent = options.transparent))
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private suspend fun svg(target: ExportTarget, options: ExportOptions): String {
        val els = elements(target, options.scope)
        val writer = SvgWriter(catalog, AndroidTextMetrics()) { a ->
            assets.pathFor(target.project, a)?.let { path -> File(path).readBytes().let { it to mimeOf(it) } }
        }
        return writer.write(els, options.transparent)
    }

    /** Asset files have no extension; sniff the image type from the first bytes. */
    private fun mimeOf(bytes: ByteArray) = when {
        bytes.size > 2 && bytes[0] == JPEG0 && bytes[1] == JPEG1 -> "image/jpeg"
        bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" -> "image/webp"
        bytes.size > 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF" -> "image/gif"
        else -> "image/png"
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val MAX_PAGE = 14_000.0
        const val JPEG0 = 0xFF.toByte()
        const val JPEG1 = 0xD8.toByte()
    }
}
