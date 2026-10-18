package com.christianlacerda.chords.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/**
 * "Chords in this song" glyph for the song's top bar: an empty guitar pick outline. Drawn on a
 * 40-unit box like the SDK's own icons, at a similar stroke.
 * LightBarButton.Icon does not tint, so the colour is passed in.
 */
class ChordsIconPainter(private val color: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val u = minOf(size.width, size.height) / 40f
        val ox = (size.width - 40f * u) / 2f
        val oy = (size.height - 40f * u) / 2f
        fun x(v: Float) = ox + v * u
        fun y(v: Float) = oy + v * u
        val pick = Path().apply {
            moveTo(x(18.2f), y(33.2f))
            cubicTo(x(12f), y(26.5f), x(6.5f), y(19f), x(6.5f), y(12f))
            cubicTo(x(6.5f), y(7.5f), x(10.5f), y(5f), x(20f), y(5f))
            cubicTo(x(29.5f), y(5f), x(33.5f), y(7.5f), x(33.5f), y(12f))
            cubicTo(x(33.5f), y(19f), x(28f), y(26.5f), x(21.8f), y(33.2f))
            // softly rounded tip
            quadraticTo(x(20f), y(35.2f), x(18.2f), y(33.2f))
            close()
        }
        drawPath(pick, color, style = Stroke(width = (3.4f * u).coerceAtLeast(2f), join = StrokeJoin.Round))
    }
}
