package com.christianlacerda.chords.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * Minimal chord glyph for the song's top bar: three strings, a nut and one fret line (same weight), and a
 * single finger dot on the middle string. Thin strokes, to sit with the SDK's own icons.
 * LightBarButton.Icon does not tint, so the colour is passed in.
 */
class ChordGridPainter(private val color: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.045f).coerceAtLeast(1.5f)
        val left = w * 0.28f
        val right = w * 0.72f
        val mid = (left + right) / 2f
        val nut = h * 0.22f
        val fret = h * 0.52f
        val bottom = h * 0.82f
        for (x in listOf(left, mid, right)) {
            drawLine(color, Offset(x, nut), Offset(x, bottom), strokeWidth = stroke)
        }
        drawLine(color, Offset(left, nut), Offset(right, nut), strokeWidth = stroke)
        drawLine(color, Offset(left, fret), Offset(right, fret), strokeWidth = stroke)
        drawCircle(color, radius = w * 0.085f, center = Offset(mid, (nut + fret) / 2f))
    }
}
