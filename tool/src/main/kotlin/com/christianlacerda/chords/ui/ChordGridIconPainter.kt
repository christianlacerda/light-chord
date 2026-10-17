package com.christianlacerda.chords.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Mini chord-grid glyph for the top bar (LightBarButton.Icon is not theme-tinted, so the colour is passed in). */
class ChordGridPainter(private val color: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.07f).coerceAtLeast(1.5f)
        val left = w * 0.2f
        val right = w * 0.8f
        val top = h * 0.12f
        val bottom = h * 0.88f
        // three strings, five fret rows, a nut
        for (i in 0..2) {
            val x = left + (right - left) * i / 2f
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
        }
        for (j in 0..4) {
            val y = top + (bottom - top) * j / 4f
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = if (j == 0) stroke * 2.2f else stroke)
        }
    }
}
