package io.github.leonardosponton.inkflow.data.repository

import androidx.room.withTransaction
import io.github.leonardosponton.inkflow.core.common.IoDispatcher
import io.github.leonardosponton.inkflow.core.common.Outcome
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.data.files.FileStore
import io.github.leonardosponton.inkflow.data.local.AssetEntity
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.data.local.ProjectEntity
import io.github.leonardosponton.inkflow.data.local.TabEntity
import io.github.leonardosponton.inkflow.domain.canvas.clipboard.ElementCopier
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.export.ArchiveError
import io.github.leonardosponton.inkflow.domain.export.ProjectArchiver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Manifest of an `.inkflow` archive (schema version [ProjectArchiver.SCHEMA_VERSION]). */
@Serializable
internal data class ArchiveManifest(
    val format: String,
    val schemaVersion: Int,
    val project: ArchivedProject,
    val tabs: List<TabEntity>,
    val assets: List<ArchivedAsset> = emptyList(),
)

@Serializable
internal data class ArchivedProject(val id: String, val name: String, val createdAt: Long, val updatedAt: Long, val favorite: Boolean)

@Serializable
internal data class ArchivedAsset(val id: String, val mimeType: String)

/**
 * Zip layout: `manifest.json`, `tabs/<tabId>.json` (array of elements), `assets/<assetId>`.
 * Importing keeps all ids when the project is not on the device (exact round trip); otherwise
 * everything is re-identified so the copy lives next to the original.
 */
@Singleton
class ZipProjectArchiver
    @Inject
    constructor(
        private val db: InkflowDatabase,
        private val files: FileStore,
        private val sceneJson: SceneJson,
        private val ids: IdGenerator,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : ProjectArchiver {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        override suspend fun export(project: ProjectId, out: OutputStream) = withContext(io) {
            val p = requireNotNull(db.projects().get(project.value)) { "Unknown project $project" }
            val tabs = db.tabs().visible(project.value)
            val assets = db.assets().forProject(project.value)
            val manifest = ArchiveManifest(
                ProjectArchiver.FORMAT,
                ProjectArchiver.SCHEMA_VERSION,
                ArchivedProject(p.id, p.name, p.createdAt, p.updatedAt, p.favorite),
                tabs,
                assets.map { ArchivedAsset(it.id, it.mimeType) },
            )
            ZipOutputStream(out).use { zip ->
                zip.entry(MANIFEST, json.encodeToString(ArchiveManifest.serializer(), manifest).toByteArray())
                tabs.forEach { t ->
                    val elements = db.elements().forTab(t.id).joinToString(",", "[", "]") { it.payload }
                    zip.entry("$TABS${t.id}.json", elements.toByteArray())
                }
                assets.forEach { a ->
                    val f = files.assetFile(project.value, a.id)
                    if (f.exists()) zip.entry("$ASSETS${a.id}", f.readBytes())
                }
            }
        }

        override suspend fun import(input: InputStream): Outcome<ProjectId, ArchiveError> = withContext(io) {
            val entries = readZip(input) ?: return@withContext Outcome.Err(ArchiveError.NotAnInkflowFile)
            val manifestBytes = entries[MANIFEST] ?: return@withContext Outcome.Err(ArchiveError.NotAnInkflowFile)
            val manifest = try {
                parseManifest(manifestBytes.decodeToString())
            } catch (e: SerializationException) {
                return@withContext Outcome.Err(ArchiveError.Corrupt(e.message.orEmpty()))
            } catch (e: IllegalArgumentException) {
                return@withContext Outcome.Err(ArchiveError.Corrupt(e.message.orEmpty()))
            }
            if (manifest.format != ProjectArchiver.FORMAT) return@withContext Outcome.Err(ArchiveError.NotAnInkflowFile)
            if (manifest.schemaVersion > ProjectArchiver.SCHEMA_VERSION) {
                return@withContext Outcome.Err(ArchiveError.UnsupportedVersion(manifest.schemaVersion))
            }
            Outcome.Ok(store(manifest, entries))
        }

        private fun parseManifest(text: String): ArchiveManifest {
            val raw = json.parseToJsonElement(text).jsonObject
            val version = raw["schemaVersion"]?.jsonPrimitive?.int ?: 0
            return json.decodeFromJsonElement(ArchiveManifest.serializer(), ArchiveMigrations.migrate(raw, version))
        }

        private suspend fun store(manifest: ArchiveManifest, entries: Map<String, ByteArray>): ProjectId {
            val exists = db.projects().get(manifest.project.id) != null
            val projectId = if (exists) ids.next() else manifest.project.id
            val assetIds = manifest.assets.associate { it.id to if (exists) ids.next() else it.id }
            val tabIds = manifest.tabs.associate { it.id to if (exists) ids.next() else it.id }
            val p = manifest.project
            val name = if (exists) "${p.name} (imported)" else p.name
            db.withTransaction {
                db.projects().insert(ProjectEntity(projectId, name, p.createdAt, p.updatedAt, p.favorite))
                manifest.tabs.forEach { t -> db.tabs().insert(t.copy(id = tabIds.getValue(t.id), projectId = projectId, deletedAt = null)) }
                manifest.assets.forEach { a ->
                    val newId = assetIds.getValue(a.id)
                    entries["$ASSETS${a.id}"]?.let { files.writeAsset(projectId, newId, it) }
                    db.assets().insert(AssetEntity(newId, projectId, a.mimeType, newId, p.updatedAt))
                }
                manifest.tabs.forEach { t ->
                    val elements = decodeElements(entries["$TABS${t.id}.json"])
                    val placed = if (exists) relink(elements, assetIds) else elements
                    db.elements().upsert(placed.map { ElementMapper.toEntity(tabIds.getValue(t.id), it, sceneJson) })
                }
            }
            return ProjectId(projectId)
        }

        private fun decodeElements(bytes: ByteArray?): List<CanvasElement> {
            bytes ?: return emptyList()
            val array = json.parseToJsonElement(bytes.decodeToString()) as? JsonArray ?: return emptyList()
            return array.mapNotNull { runCatching { sceneJson.decodeElement(it.toString()) }.getOrNull() }
        }

        /** Fresh element ids (references preserved) and asset ids for a second copy of a project. */
        private fun relink(elements: List<CanvasElement>, assets: Map<String, String>): List<CanvasElement> =
            ElementCopier.copy(elements) { ElementId(ids.next()) }.map { el ->
                if (el is ImageElement) el.copy(asset = AssetId(assets[el.asset.value] ?: el.asset.value)) else el
            }

        private fun readZip(input: InputStream): Map<String, ByteArray>? = runCatching {
            val out = HashMap<String, ByteArray>()
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory && !e.name.contains("..")) out[e.name] = zip.readBytes()
                    e = zip.nextEntry
                }
            }
            out.takeIf { it.isNotEmpty() }
        }.getOrNull()

        private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }

        private companion object {
            const val MANIFEST = "manifest.json"
            const val TABS = "tabs/"
            const val ASSETS = "assets/"
        }
    }

/** Upgrades older manifests step by step to the current schema. */
internal object ArchiveMigrations {
    fun migrate(manifest: JsonObject, fromVersion: Int): JsonElement {
        var current = manifest
        var v = fromVersion
        while (v < ProjectArchiver.SCHEMA_VERSION) {
            current = step(current, v)
            v++
        }
        return current
    }

    /** v0 (pre-release) had no schemaVersion field; nothing else changed. */
    private fun step(m: JsonObject, from: Int): JsonObject = when (from) {
        0 -> JsonObject(m + ("schemaVersion" to JsonPrimitive(1)))
        else -> m
    }
}
