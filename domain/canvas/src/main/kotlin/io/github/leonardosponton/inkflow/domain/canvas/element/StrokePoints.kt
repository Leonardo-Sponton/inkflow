package io.github.leonardosponton.inkflow.domain.canvas.element

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Raw stylus samples of an ink stroke, packed for memory: [STRIDE] floats per point
 * (x, y relative to the stroke origin, elapsed ms, pressure 0..1, tilt rad, orientation rad).
 * Serialized as Base64 of little-endian floats (≈ 4× smaller than JSON numbers).
 */
@Serializable(with = StrokePointsSerializer::class)
class StrokePoints(val data: FloatArray) {
    init {
        require(data.size % STRIDE == 0) { "Stroke data must be a multiple of $STRIDE" }
    }

    val size: Int get() = data.size / STRIDE

    fun x(i: Int) = data[i * STRIDE]

    fun y(i: Int) = data[i * STRIDE + 1]

    fun time(i: Int) = data[i * STRIDE + 2]

    fun pressure(i: Int) = data[i * STRIDE + 3]

    fun tilt(i: Int) = data[i * STRIDE + 4]

    fun orientation(i: Int) = data[i * STRIDE + 5]

    fun localPoint(i: Int) = Vec2(x(i).toDouble(), y(i).toDouble())

    /** Bounds of the sample positions relative to the origin. */
    fun localBounds(): Rect? {
        if (size == 0) return null
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (i in 0 until size) {
            l = minOf(l, x(i))
            t = minOf(t, y(i))
            r = maxOf(r, x(i))
            b = maxOf(b, y(i))
        }
        return Rect(l.toDouble(), t.toDouble(), r.toDouble(), b.toDouble())
    }

    /** New points with positions mapped through [f]; other channels are kept. */
    fun mapPositions(f: (Vec2) -> Vec2): StrokePoints {
        val out = data.copyOf()
        for (i in 0 until size) {
            val p = f(localPoint(i))
            out[i * STRIDE] = p.x.toFloat()
            out[i * STRIDE + 1] = p.y.toFloat()
        }
        return StrokePoints(out)
    }

    override fun equals(other: Any?) = other is StrokePoints && data.contentEquals(other.data)

    override fun hashCode() = data.contentHashCode()

    override fun toString() = "StrokePoints($size points)"

    class Builder {
        private var buf = FloatArray(INITIAL_CAPACITY * STRIDE)
        private var count = 0

        @Suppress("LongParameterList")
        fun add(x: Float, y: Float, timeMs: Float, pressure: Float, tilt: Float = 0f, orientation: Float = 0f) {
            if ((count + 1) * STRIDE > buf.size) buf = buf.copyOf(buf.size * 2)
            val o = count * STRIDE
            buf[o] = x
            buf[o + 1] = y
            buf[o + 2] = timeMs
            buf[o + 3] = pressure
            buf[o + 4] = tilt
            buf[o + 5] = orientation
            count++
        }

        val size: Int get() = count

        fun build() = StrokePoints(buf.copyOf(count * STRIDE))
    }

    companion object {
        const val STRIDE = 6
        private const val INITIAL_CAPACITY = 64
        val Empty = StrokePoints(FloatArray(0))
    }
}

object StrokePointsSerializer : KSerializer<StrokePoints> {
    override val descriptor = PrimitiveSerialDescriptor("StrokePoints", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StrokePoints) {
        val bytes = ByteBuffer.allocate(value.data.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        value.data.forEach(bytes::putFloat)
        encoder.encodeString(Base64.getEncoder().encodeToString(bytes.array()))
    }

    override fun deserialize(decoder: Decoder): StrokePoints {
        val bytes = ByteBuffer.wrap(Base64.getDecoder().decode(decoder.decodeString())).order(ByteOrder.LITTLE_ENDIAN)
        return StrokePoints(FloatArray(bytes.remaining() / Float.SIZE_BYTES) { bytes.getFloat() })
    }
}
