package com.christianlacerda.chords.ui.chords

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.christianlacerda.chords.model.Voicing
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.max
import kotlin.math.min

/**
 * Fretboard diagram: 6 strings x 5 frets. All geometry derives from the string spacing `s`.
 * [compact] is for the 4-up sheet (bigger numerals relative to the dots so they stay readable).
 * With a [capo] (> 0) the top edge becomes a bar labelled CAPO with the capo fret beside it, as on
 * Cifra Club; the shape is read relative to the capo.
 */
@Composable
fun ChordDiagram(voicing: Voicing, modifier: Modifier = Modifier, compact: Boolean = false, capo: Int? = null) {
    val colors = LightThemeTokens.colors
    val measurer = rememberTextMeasurer()
    val content = colors.content
    val secondary = colors.contentSecondary
    val background = colors.background
    Canvas(modifier) {
        drawDiagram(voicing, measurer, content, secondary, background, compact, capo?.takeIf { it > 0 })
    }
}

private fun DrawScope.drawDiagram(
    v: Voicing,
    measurer: TextMeasurer,
    content: Color,
    secondary: Color,
    background: Color,
    compact: Boolean,
    capo: Int?,
) {
    val s = min(size.width / 7.4f, size.height / 7.3f)
    if (s <= 0f) return
    val f = 1.2f * s
    val left = (size.width - 5f * s) / 2f
    val top = (size.height - 7f * s) / 2f + 1.0f * s
    val stroke = max(2f, 0.05f * s)
    fun sx(i: Int) = left + i * s
    fun rowCenter(r: Int) = top + (r + 0.5f) * f

    // frets and strings
    for (j in 0..5) {
        val y = top + j * f
        drawLine(content, Offset(left, y), Offset(sx(5), y), strokeWidth = stroke)
    }
    for (i in 0..5) {
        drawLine(content, Offset(sx(i), top), Offset(sx(i), top + 5 * f), strokeWidth = stroke)
    }
    // capo bar in place of the nut, with the capo fret in the left gutter
    if (capo != null) {
        val barH = 0.5f * s
        val x0 = left - 0.3f * s
        val x1 = sx(5) + 0.3f * s
        drawRoundRect(content, Offset(x0, top - barH / 2f), Size(x1 - x0, barH), CornerRadius(barH / 2f))
        val capoStyle = TextStyle(color = background, fontSize = (0.32f * s).toSp(), fontWeight = FontWeight.Bold)
        // one letter on each inner string, so the label spreads evenly across the bar
        "CAPO".forEachIndexed { k, ch ->
            val layout = measurer.measure(ch.toString(), capoStyle)
            val cx = sx(k + 1)
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, top - layout.size.height / 2f))
        }
        val numStyle = TextStyle(color = content, fontSize = (0.5f * s).toSp(), fontWeight = FontWeight.Bold)
        val num = measurer.measure(capo.toString(), numStyle)
        drawText(num, topLeft = Offset(x0 - 0.25f * s - num.size.width, top - num.size.height / 2f))
    }
    // nut
    if (v.baseFret == 1) {
        if (capo == null) {
            drawRect(content, Offset(left - stroke / 2f, top - 0.09f * s), Size(5f * s + stroke, 0.18f * s))
        }
    } else {
        val style = TextStyle(color = content, fontSize = (0.45f * s).toSp(), fontWeight = FontWeight.Normal)
        val layout = measurer.measure("${v.baseFret}fr", style)
        drawText(
            textLayoutResult = layout,
            // clear of the barre and first-string dots, which reach 0.42s left of the low E string
            topLeft = Offset(left - 0.55f * s - layout.size.width, rowCenter(0) - layout.size.height / 2f),
        )
    }
    // open / muted markers above the nut
    val markerY = top - (if (capo != null) 0.62f else 0.55f) * s
    v.frets.forEachIndexed { i, fret ->
        when (fret) {
            null -> {
                val h = 0.18f * s
                drawLine(secondary, Offset(sx(i) - h, markerY - h), Offset(sx(i) + h, markerY + h), strokeWidth = stroke)
                drawLine(secondary, Offset(sx(i) - h, markerY + h), Offset(sx(i) + h, markerY - h), strokeWidth = stroke)
            }
            0 -> drawCircle(content, radius = 0.25f * s, center = Offset(sx(i), markerY), style = Stroke(width = stroke))
            else -> Unit
        }
    }

    val numStyle = TextStyle(
        color = background,
        fontSize = ((if (compact) 0.55f else 0.5f) * s).toSp(),
        fontWeight = FontWeight.Bold,
    )
    fun drawNumber(n: Int, cx: Float, cy: Float) {
        val layout = measurer.measure(n.toString(), numStyle)
        drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
    }

    // barre
    val barre = v.barre
    if (barre != null) {
        val r = barre.fret - v.baseFret
        if (r in 0..4) {
            val cy = rowCenter(r)
            val x0 = sx(barre.fromString) - 0.42f * s
            val x1 = sx(barre.toString) + 0.42f * s
            drawRoundRect(content, Offset(x0, cy - 0.42f * s), Size(x1 - x0, 0.84f * s), CornerRadius(0.42f * s))
            drawNumber(barre.finger, sx(barre.fromString), cy)
        }
    }
    // finger dots
    v.frets.forEachIndexed { i, fret ->
        if (fret == null || fret <= 0) return@forEachIndexed
        if (barre != null && fret == barre.fret && i in barre.fromString..barre.toString) return@forEachIndexed
        val r = fret - v.baseFret
        if (r !in 0..4) return@forEachIndexed
        val cy = rowCenter(r)
        drawCircle(content, radius = 0.42f * s, center = Offset(sx(i), cy))
        val finger = v.fingers.getOrElse(i) { 0 }
        if (finger > 0) drawNumber(finger, sx(i), cy)
    }
}
