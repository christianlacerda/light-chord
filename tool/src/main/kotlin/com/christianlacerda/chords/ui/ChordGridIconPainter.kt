package com.christianlacerda.chords.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/**
 * "Chords in this song" glyph for the song's top bar: four rounded tiles in a 2x2 grid, echoing the
 * 4-per-page sheet it opens. Drawn on a 40-unit box like the SDK's own icons, at a similar stroke.
 * LightBarButton.Icon does not tint, so the colour is passed in.
 */
class ChordGridPainter(private val color: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val u = minOf(size.width, size.height) / 40f
        val ox = (size.width - 40f * u) / 2f
        val oy = (size.height - 40f * u) / 2f
        val tile = Size(12f * u, 12f * u)
        val style = Stroke(width = (2.4f * u).coerceAtLeast(1.5f))
        for (x in listOf(5f, 23f)) {
            for (y in listOf(5f, 23f)) {
                drawRoundRect(color, Offset(ox + x * u, oy + y * u), tile, CornerRadius(2f * u), style = style)
            }
        }
    }
}
