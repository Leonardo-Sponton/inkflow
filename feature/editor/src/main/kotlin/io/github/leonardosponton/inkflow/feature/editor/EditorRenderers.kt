package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.common.DefaultDispatcher
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.project.AssetStore
import io.github.leonardosponton.inkflow.feature.canvasrender.AndroidTextMetrics
import io.github.leonardosponton.inkflow.feature.canvasrender.ImageSource
import io.github.leonardosponton.inkflow.feature.canvasrender.SceneDrawer
import io.github.leonardosponton.inkflow.feature.canvasrender.SceneRasterizer
import io.github.leonardosponton.inkflow.feature.canvasrender.SyncImageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renderer instances: [metrics] and screen drawers for the main thread, plus one serialized
 * off-screen rasterizer (own caches) for thumbnails and exports on a background thread.
 */
@Singleton
class EditorRenderers
    @Inject
    constructor(
        private val catalog: NotationCatalog,
        private val assets: AssetStore,
        @param:DefaultDispatcher private val background: CoroutineDispatcher,
    ) {
        val metrics = AndroidTextMetrics()
        private val offscreenLock = Mutex()

        fun screenDrawer(images: ImageSource): SceneDrawer = SceneDrawer(catalog, metrics, images)

        fun assetFile(project: ProjectId, asset: AssetId): File? =
            assets.pathFor(project, asset)?.let(::File)

        /** Runs [block] with a rasterizer that resolves images of [project]; calls are serialized. */
        suspend fun <T> offscreen(project: ProjectId, block: (SceneRasterizer) -> T): T = offscreenLock.withLock {
            withContext(background) {
                val drawer = SceneDrawer(catalog, AndroidTextMetrics(), SyncImageSource { assetFile(project, it) })
                block(SceneRasterizer(drawer))
            }
        }
    }
