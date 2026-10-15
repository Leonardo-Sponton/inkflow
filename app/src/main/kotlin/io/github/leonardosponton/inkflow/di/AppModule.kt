package io.github.leonardosponton.inkflow.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import io.github.leonardosponton.inkflow.core.common.ApplicationScope
import io.github.leonardosponton.inkflow.core.common.DefaultDispatcher
import io.github.leonardosponton.inkflow.core.common.IoDispatcher
import io.github.leonardosponton.inkflow.core.common.Logger
import io.github.leonardosponton.inkflow.core.model.Clock
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.UuidIdGenerator
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationModule
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun ids(): IdGenerator = UuidIdGenerator

    @Provides
    @Singleton
    fun logger(): Logger = AndroidLogger

    @Provides
    @Singleton
    fun catalog(modules: Set<@JvmSuppressWildcards NotationModule>): NotationCatalog =
        NotationCatalog(modules.sortedBy { it.id.value })

    @Provides
    @Singleton
    fun sceneJson(catalog: NotationCatalog): SceneJson = SceneJson(catalog.serializers)
}

/** Notation plug-ins; each notation module adds itself here. */
@Module
@InstallIn(SingletonComponent::class)
object NotationsModule {
    @Provides
    @ElementsIntoSet
    fun notations(): Set<NotationModule> = InstalledNotations.all.toSet()
}

private object AndroidLogger : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }
}
