package io.github.leonardosponton.inkflow.data.repository

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Layer
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.data.local.TabEntity
import io.github.leonardosponton.inkflow.domain.project.Tab
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object Mappers {
    private val json = Json { ignoreUnknownKeys = true }
    private val layersSerializer = ListSerializer(Layer.serializer())

    fun encodeLayers(layers: List<Layer>): String = json.encodeToString(layersSerializer, layers)

    fun decodeLayers(s: String): List<Layer> = runCatching { json.decodeFromString(layersSerializer, s) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(Layer.Default)

    fun TabEntity.toDomain() = Tab(
        id = TabId(id),
        projectId = ProjectId(projectId),
        title = title,
        position = position,
        profile = enumOr(profile, DiagramProfile.FREEFORM),
        color = enumOr(color, TabColor.NONE),
        camera = Camera(Vec2(cameraX, cameraY), Camera.clampZoom(zoom), rotation),
        layers = decodeLayers(layers),
        grid = enumOr(grid, GridStyle.DOTS),
    )

    fun Tab.toEntity(deletedAt: Long? = null) = TabEntity(
        id = id.value,
        projectId = projectId.value,
        title = title,
        position = position,
        profile = profile.name,
        color = color.name,
        cameraX = camera.center.x,
        cameraY = camera.center.y,
        zoom = camera.zoom,
        rotation = camera.rotation,
        grid = grid.name,
        layers = encodeLayers(layers),
        deletedAt = deletedAt,
    )

    inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
