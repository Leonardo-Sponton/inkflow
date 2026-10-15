package io.github.leonardosponton.inkflow.domain.canvas.serialization

import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus

/**
 * JSON codec for elements and changes. Notation content types come from [notationSerializers];
 * unknown fields are ignored so older app versions can open newer files.
 */
class SceneJson(notationSerializers: SerializersModule) {
    val json: Json = Json {
        serializersModule = notationSerializers + SerializersModule { }
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
    }

    fun encodeElement(e: CanvasElement): String = json.encodeToString(CanvasElement.serializer(), e)

    fun decodeElement(s: String): CanvasElement = json.decodeFromString(CanvasElement.serializer(), s)

    fun encodeChange(c: SceneChange): String = json.encodeToString(SceneChange.serializer(), c)

    fun decodeChange(s: String): SceneChange = json.decodeFromString(SceneChange.serializer(), s)
}
