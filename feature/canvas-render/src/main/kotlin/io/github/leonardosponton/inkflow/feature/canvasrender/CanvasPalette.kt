package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Color
import io.github.leonardosponton.inkflow.core.model.ColorArgb

/**
 * Canvas colors, independent from the app theme. On a dark canvas, near-black ink and outlines
 * are shown light and white fills dark, so drawings stay readable (data is unchanged).
 */
data class CanvasPalette(
    val dark: Boolean,
    val background: Int,
    val grid: Int,
    val gridMajor: Int,
    val selection: Int,
    val selectionFill: Int,
    val guide: Int,
    val port: Int,
    val handleFill: Int,
    val warning: Int,
    val labelBackground: Int,
) {
    /** Color to draw for a stored [color]. */
    fun adapt(color: ColorArgb): Int = adapt(color.argb)

    fun adapt(argb: Int): Int {
        if (!dark) return argb
        val lum = luminance(argb)
        return when {
            lum < DARK_THRESHOLD -> invertLightness(argb)
            lum > LIGHT_THRESHOLD -> (argb and ALPHA_MASK) or (DARK_FILL and RGB_MASK)
            else -> argb
        }
    }

    private fun luminance(c: Int) = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / MAX_CHANNEL

    private fun invertLightness(c: Int): Int {
        val r = MAX_CHANNEL - Color.red(c)
        val g = MAX_CHANNEL - Color.green(c)
        val b = MAX_CHANNEL - Color.blue(c)
        return Color.argb(Color.alpha(c), r.toInt(), g.toInt(), b.toInt())
    }

    companion object {
        private const val DARK_THRESHOLD = 0.2
        private const val LIGHT_THRESHOLD = 0.92
        private const val MAX_CHANNEL = 255.0
        private const val ALPHA_MASK = 0xFF000000.toInt()
        private const val RGB_MASK = 0x00FFFFFF
        private const val DARK_FILL = 0xFF2B2D31.toInt()

        val Light = CanvasPalette(
            dark = false,
            background = 0xFFFAFAF7.toInt(),
            grid = 0xFFD9DBE0.toInt(),
            gridMajor = 0xFFC2C5CC.toInt(),
            selection = 0xFF1A73E8.toInt(),
            selectionFill = 0x141A73E8,
            guide = 0xFFE8467C.toInt(),
            port = 0xFF1A73E8.toInt(),
            handleFill = 0xFFFFFFFF.toInt(),
            warning = 0xFFE37400.toInt(),
            labelBackground = 0xF0FAFAF7.toInt(),
        )

        val Dark = Light.copy(
            dark = true,
            background = 0xFF1E1F22.toInt(),
            grid = 0xFF34373C.toInt(),
            gridMajor = 0xFF45494F.toInt(),
            selection = 0xFF8AB4F8.toInt(),
            selectionFill = 0x228AB4F8,
            port = 0xFF8AB4F8.toInt(),
            handleFill = 0xFF1E1F22.toInt(),
            labelBackground = 0xF01E1F22.toInt(),
        )
    }
}
