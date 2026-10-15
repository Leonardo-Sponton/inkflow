package io.github.leonardosponton.inkflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.favorite, p.thumbnailVersion,
               (SELECT COUNT(*) FROM tabs t WHERE t.projectId = p.id AND t.deletedAt IS NULL) AS tabCount
        FROM projects p
        """,
    )
    fun observeSummaries(): Flow<List<ProjectSummaryRow>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE projects SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE projects SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE projects SET thumbnailVersion = :version WHERE id = :id")
    suspend fun setThumbnailVersion(id: String, version: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(project: ProjectEntity)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY position")
    fun observeVisible(projectId: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY position")
    suspend fun visible(projectId: String): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun get(id: String): TabEntity?

    @Insert
    suspend fun insert(tab: TabEntity)

    @Upsert
    suspend fun upsert(tab: TabEntity)

    @Query("UPDATE tabs SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE tabs SET color = :color WHERE id = :id")
    suspend fun setColor(id: String, color: String)

    @Query("UPDATE tabs SET profile = :profile WHERE id = :id")
    suspend fun setProfile(id: String, profile: String)

    @Query("UPDATE tabs SET grid = :grid WHERE id = :id")
    suspend fun setGrid(id: String, grid: String)

    @Query("UPDATE tabs SET layers = :layers WHERE id = :id")
    suspend fun setLayers(id: String, layers: String)

    @Query("UPDATE tabs SET cameraX = :x, cameraY = :y, zoom = :zoom, rotation = :rotation WHERE id = :id")
    suspend fun setCamera(id: String, x: Double, y: Double, zoom: Double, rotation: Double)

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE tabs SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Query("DELETE FROM tabs WHERE projectId = :projectId AND deletedAt IS NOT NULL")
    suspend fun purgeDeleted(projectId: String)

    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { i, id -> setPosition(id, i) }
    }
}

@Dao
interface ElementDao {
    @Query("SELECT * FROM elements WHERE tabId = :tabId")
    suspend fun forTab(tabId: String): List<ElementEntity>

    @Query("SELECT COUNT(*) FROM elements WHERE tabId = :tabId")
    suspend fun count(tabId: String): Int

    @Upsert
    suspend fun upsert(elements: List<ElementEntity>)

    @Query("DELETE FROM elements WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM elements WHERE tabId = :tabId")
    suspend fun deleteTab(tabId: String)

    @Transaction
    suspend fun apply(upserts: List<ElementEntity>, deletes: List<String>) {
        if (deletes.isNotEmpty()) deletes.chunked(SQL_CHUNK).forEach { delete(it) }
        if (upserts.isNotEmpty()) upsert(upserts)
    }

    private companion object {
        const val SQL_CHUNK = 500
    }
}

@Dao
interface UndoDao {
    @Query("SELECT * FROM undo_log WHERE tabId = :tabId ORDER BY seq")
    suspend fun forTab(tabId: String): List<UndoEntryEntity>

    @Query("DELETE FROM undo_log WHERE tabId = :tabId")
    suspend fun clear(tabId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entries: List<UndoEntryEntity>)

    @Transaction
    suspend fun replace(tabId: String, entries: List<UndoEntryEntity>) {
        clear(tabId)
        insert(entries)
    }
}

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity)

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun get(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE projectId = :projectId")
    suspend fun forProject(projectId: String): List<AssetEntity>
}
