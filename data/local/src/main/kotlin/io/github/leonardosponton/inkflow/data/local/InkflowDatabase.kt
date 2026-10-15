package io.github.leonardosponton.inkflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, TabEntity::class, ElementEntity::class, AssetEntity::class, UndoEntryEntity::class],
    version = InkflowDatabase.VERSION,
    exportSchema = true,
)
abstract class InkflowDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao

    abstract fun tabs(): TabDao

    abstract fun elements(): ElementDao

    abstract fun undo(): UndoDao

    abstract fun assets(): AssetDao

    companion object {
        const val VERSION = 1
        const val NAME = "inkflow.db"

        /** Future schema migrations are registered here (and covered by MigrationTest). */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun build(context: Context): InkflowDatabase = Room.databaseBuilder(context, InkflowDatabase::class.java, NAME)
            .addMigrations(*MIGRATIONS)
            .build()
    }
}
