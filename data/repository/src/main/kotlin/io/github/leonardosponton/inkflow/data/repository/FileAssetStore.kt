package io.github.leonardosponton.inkflow.data.repository

import io.github.leonardosponton.inkflow.core.common.IoDispatcher
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.Clock
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.data.files.FileStore
import io.github.leonardosponton.inkflow.data.local.AssetEntity
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.domain.project.AssetInfo
import io.github.leonardosponton.inkflow.domain.project.AssetStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileAssetStore
    @Inject
    constructor(
        private val db: InkflowDatabase,
        private val files: FileStore,
        private val ids: IdGenerator,
        private val clock: Clock,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : AssetStore {
        override fun pathFor(project: ProjectId, asset: AssetId): String? =
            files.assetFile(project.value, asset.value).takeIf { it.exists() }?.absolutePath

        override suspend fun save(project: ProjectId, bytes: ByteArray, mimeType: String): AssetId = withContext(io) {
            val id = AssetId(ids.next())
            files.writeAsset(project.value, id.value, bytes)
            db.assets().insert(AssetEntity(id.value, project.value, mimeType, id.value, clock.nowMillis()))
            id
        }

        override suspend fun copy(from: ProjectId, to: ProjectId, asset: AssetId): AssetId? = withContext(io) {
            if (from == to) return@withContext asset
            val source = files.assetFile(from.value, asset.value)
            if (!source.exists()) return@withContext null
            val mime = db.assets().get(asset.value)?.mimeType ?: DEFAULT_MIME
            save(to, source.readBytes(), mime)
        }

        override suspend fun list(project: ProjectId): List<AssetInfo> = withContext(io) {
            db.assets().forProject(project.value).map { AssetInfo(AssetId(it.id), it.mimeType) }
        }

        override suspend fun read(project: ProjectId, asset: AssetId): ByteArray? = withContext(io) {
            files.assetFile(project.value, asset.value).takeIf { it.exists() }?.readBytes()
        }

        private companion object {
            const val DEFAULT_MIME = "image/png"
        }
    }
