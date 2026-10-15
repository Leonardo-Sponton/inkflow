package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable

/** 32-bit ARGB color. */
@Serializable
@JvmInline
value class ColorArgb(val argb: Int) {
    val alpha: Int get() = (argb ushr ALPHA_SHIFT) and MAX_CHANNEL

    fun withAlpha(a: Int) = ColorArgb((argb and RGB_MASK) or (a.coerceIn(0, MAX_CHANNEL) shl ALPHA_SHIFT))

    companion object {
        private const val RGB_MASK = 0x00FFFFFF
        private const val MAX_CHANNEL = 0xFF
        private const val ALPHA_SHIFT = 24

        val Black = ColorArgb(0xFF1C1B1F.toInt())
        val White = ColorArgb(0xFFFFFFFF.toInt())
        val Transparent = ColorArgb(0)
        val StickyYellow = ColorArgb(0xFFFFF59D.toInt())

        /** Pen/shape swatches offered in the UI. */
        val Swatches = listOf(
            Black,
            ColorArgb(0xFF5F6368.toInt()),
            ColorArgb(0xFFD93025.toInt()),
            ColorArgb(0xFFF29900.toInt()),
            ColorArgb(0xFF188038.toInt()),
            ColorArgb(0xFF1A73E8.toInt()),
            ColorArgb(0xFF9334E6.toInt()),
            ColorArgb(0xFFE52592.toInt()),
            White,
        )
    }
}

enum class DashPattern { SOLID, DASHED, DOTTED }

enum class TextAlign { START, CENTER, END }

/** Visual style shared by shapes, text boxes and connectors. */
@Serializable
data class ShapeStyle(
    val fill: ColorArgb? = ColorArgb.White,
    val stroke: ColorArgb = ColorArgb.Black,
    val strokeWidth: Double = 2.0,
    val dash: DashPattern = DashPattern.SOLID,
    val textColor: ColorArgb = ColorArgb.Black,
    val fontSize: Double = 16.0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textAlign: TextAlign = TextAlign.CENTER,
    val opacity: Double = 1.0,
) {
    companion object {
        val Default = ShapeStyle()
        val Line = ShapeStyle(fill = null)
    }
}

/** Partial style update; `null` fields keep the current value. */
@Serializable
data class StylePatch(
    val fill: ColorArgb? = null,
    val clearFill: Boolean = false,
    val stroke: ColorArgb? = null,
    val strokeWidth: Double? = null,
    val dash: DashPattern? = null,
    val textColor: ColorArgb? = null,
    val fontSize: Double? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val textAlign: TextAlign? = null,
    val opacity: Double? = null,
) {
    fun applyTo(s: ShapeStyle) = s.copy(
        fill = if (clearFill) null else fill ?: s.fill,
        stroke = stroke ?: s.stroke,
        strokeWidth = strokeWidth ?: s.strokeWidth,
        dash = dash ?: s.dash,
        textColor = textColor ?: s.textColor,
        fontSize = fontSize ?: s.fontSize,
        bold = bold ?: s.bold,
        italic = italic ?: s.italic,
        textAlign = textAlign ?: s.textAlign,
        opacity = opacity ?: s.opacity,
    )
}
