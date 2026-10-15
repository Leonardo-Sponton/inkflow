package io.github.leonardosponton.inkflow.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val thumbnailVersion: Long = 0,
)

@Entity(
    tableName = "tabs",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
@Serializable
data class TabEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val position: Int,
    val profile: String,
    val color: String,
    val cameraX: Double,
    val cameraY: Double,
    val zoom: Double,
    val rotation: Double = 0.0,
    val grid: String,
    /** JSON list of layers. */
    val layers: String,
    /** Soft delete for undo; `null` = visible. */
    val deletedAt: Long? = null,
)

/**
 * One canvas element. Bounds are denormalized for spatial queries; the element itself is the JSON
 * [payload] owned by `SceneJson`.
 */
@Entity(
    tableName = "elements",
    foreignKeys = [ForeignKey(TabEntity::class, ["id"], ["tabId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tabId")],
)
data class ElementEntity(
    @PrimaryKey val id: String,
    val tabId: String,
    val type: String,
    val zIndex: Long,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
    val payload: String,
)

@Entity(
    tableName = "assets",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val mimeType: String,
    val fileName: String,
    val createdAt: Long,
)

@Entity(
    tableName = "undo_log",
    primaryKeys = ["tabId", "seq"],
    foreignKeys = [ForeignKey(TabEntity::class, ["id"], ["tabId"], onDelete = ForeignKey.CASCADE)],
)
data class UndoEntryEntity(val tabId: String, val seq: Int, val payload: String)

/** Projection for the gallery. */
data class ProjectSummaryRow(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val thumbnailVersion: Long,
    val tabCount: Int,
)
