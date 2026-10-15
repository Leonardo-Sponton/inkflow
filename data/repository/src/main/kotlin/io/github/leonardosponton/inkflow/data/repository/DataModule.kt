package io.github.leonardosponton.inkflow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.leonardosponton.inkflow.data.files.FileStore
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.export.ProjectArchiver
import io.github.leonardosponton.inkflow.domain.project.AssetStore
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.SettingsRepository
import io.github.leonardosponton.inkflow.domain.project.TabRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun projects(impl: RoomProjectRepository): ProjectRepository

    @Binds
    abstract fun tabs(impl: RoomProjectRepository): TabRepository

    @Binds
    abstract fun scenes(impl: RoomSceneRepository): SceneRepository

    @Binds
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun assets(impl: FileAssetStore): AssetStore

    @Binds
    abstract fun archiver(impl: ZipProjectArchiver): ProjectArchiver
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): InkflowDatabase = InkflowDatabase.build(context)

    @Provides
    @Singleton
    fun fileStore(
        @ApplicationContext context: Context,
    ): FileStore = FileStore(context.filesDir)

    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
