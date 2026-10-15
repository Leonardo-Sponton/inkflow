package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.domain.diagram.TextMetrics
import kotlin.math.ceil

/** [TextMetrics] and cached [StaticLayout]s; sizes are in world units (the canvas scales them). */
class AndroidTextMetrics : TextMetrics {
    private data class Key(
        val text: String,
        val width: Int,
        val size: Float,
        val bold: Boolean,
        val italic: Boolean,
        val align: TextAlign,
        val color: Int,
        val underline: Boolean,
    )

    private val layouts = object : LruCache<Key, StaticLayout>(LAYOUT_CACHE) {}
    private val measurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    fun paint(size: Float, bold: Boolean, italic: Boolean, color: Int, underline: Boolean = false) =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG or TextPaint.LINEAR_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = typeface(bold, italic)
            isUnderlineText = underline
        }

    @Suppress("LongParameterList")
    fun layout(
        text: String,
        width: Int,
        size: Float,
        bold: Boolean,
        italic: Boolean,
        align: TextAlign,
        color: Int,
        underline: Boolean,
    ): StaticLayout {
        val key = Key(text, width.coerceAtLeast(1), size, bold, italic, align, color, underline)
        layouts.get(key)?.let { return it }
        val built = StaticLayout.Builder.obtain(text, 0, text.length, paint(size, bold, italic, color, underline), key.width)
            .setAlignment(
                when (align) {
                    TextAlign.START -> Layout.Alignment.ALIGN_NORMAL
                    TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    TextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
            .setIncludePad(false)
            .build()
        layouts.put(key, built)
        return built
    }

    override fun lineHeight(fontSize: Double): Double {
        measurePaint.textSize = fontSize.toFloat()
        return measurePaint.fontSpacing.toDouble()
    }

    override fun width(text: String, fontSize: Double, bold: Boolean): Double {
        measurePaint.textSize = fontSize.toFloat()
        measurePaint.typeface = typeface(bold, false)
        return measurePaint.measureText(text).toDouble()
    }

    override fun measure(text: String, fontSize: Double, bold: Boolean, maxWidth: Double?): Size {
        if (maxWidth == null) {
            val lines = text.split('\n')
            return Size(lines.maxOf { width(it, fontSize, bold) }, lines.size * lineHeight(fontSize))
        }
        val l = layout(text, ceil(maxWidth).toInt(), fontSize.toFloat(), bold, false, TextAlign.START, 0, false)
        val w = (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f
        return Size(w.toDouble(), l.height.toDouble())
    }

    private fun typeface(bold: Boolean, italic: Boolean): Typeface = Typeface.create(
        Typeface.DEFAULT,
        when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        },
    )

    private companion object {
        const val LAYOUT_CACHE = 2048
    }
}
