package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.christianlacerda.chords.R
import com.christianlacerda.chords.layout.RenderItem
import com.christianlacerda.chords.layout.WrappedRow
import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.SectionKind
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlin.math.roundToInt

/** Everything the body needs to draw; keeps the per-item composables' parameter lists short. */
internal class SongPalette(
    val lyric: TextStyle,
    val chord: TextStyle,
    val lyricColor: Color,
    val chordColor: Color,
    val chordLineHeightPx: Float,
    val lyricLineHeightPx: Float,
    /** Gap between two music lines, in px. */
    val lineGapPx: Float,
    /** Chords are only tappable while not playing. */
    val chordsTappable: Boolean,
)

@Composable
internal fun RenderItemView(item: RenderItem, palette: SongPalette, onChordTap: (ChordName) -> Unit) {
    val density = LocalDensity.current
    when (item) {
        is RenderItem.Gap -> Spacer(Modifier.height(with(density) { (palette.lyricLineHeightPx * 0.6f).toDp() }))

        is RenderItem.Label -> {
            val text = item.text ?: when (item.kind) {
                SectionKind.VERSE -> stringResource(R.string.section_verse)
                SectionKind.CHORUS -> stringResource(R.string.section_chorus)
                SectionKind.BRIDGE -> stringResource(R.string.section_bridge)
                SectionKind.TAB -> stringResource(R.string.section_tab)
                SectionKind.PLAIN -> ""
            }
            LightText(
                text = text.uppercase(),
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 0.3f.gridUnitsAsDp()),
            )
        }

        is RenderItem.Tab -> BasicText(
            text = item.text,
            style = palette.lyric.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = TAB_FONT_PX.designVerticalPxToSp(),
                lineHeight = (TAB_FONT_PX * 1.25f).designVerticalPxToSp(),
                color = palette.lyricColor,
            ),
            softWrap = false,
            overflow = TextOverflow.Clip,
        )

        is RenderItem.Music -> Column(
            Modifier.padding(bottom = with(density) { palette.lineGapPx.toDp() }),
        ) {
            item.rows.forEach { MusicRowView(it, palette, onChordTap) }
        }
    }
}

private const val TAB_FONT_PX = 16f

/**
 * One wrapped row: chord names on top, lyric text below, both placed at the x the wrapper computed.
 * Positions use offset() inside a fixed-height Box, so a row is exactly as tall as its content needs.
 */
@Composable
private fun MusicRowView(row: WrappedRow, palette: SongPalette, onChordTap: (ChordName) -> Unit) {
    val density = LocalDensity.current
    val chordH = if (row.hasChords) palette.chordLineHeightPx else 0f
    val lyricH = if (row.hasLyrics) palette.lyricLineHeightPx else 0f
    val slopH = with(density) { 0.4f.gridUnitsAsDp().roundToPx() }
    val underlineInset = with(density) { 3.dp.toPx() }
    val dash = with(density) { 3.dp.toPx() }
    val chordHDp = with(density) { palette.chordLineHeightPx.toDp() }

    // Chord hit areas in row coordinates, filled in as the chord boxes are placed. Taps are resolved
    // here on the full-size row rather than with a clickable on each small chord box: Compose widens
    // the touch target of any pointer-input node smaller than the minimum touch size (48dp), which made
    // a chord's tap area reach into the lyric line below it.
    val chordHits = remember(row) { mutableMapOf<Int, Pair<Rect, ChordName>>() }
    val currentOnChordTap by rememberUpdatedState(onChordTap)

    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { (chordH + lyricH).toDp() })
            .then(
                if (palette.chordsTappable && row.hasChords) {
                    Modifier.pointerInput(row) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val hit = chordHits.values.firstOrNull { it.first.contains(down.position) } ?: return@awaitEachGesture
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            if (hit.first.contains(up.position)) {
                                // consuming the up keeps the lyrics' play/pause click from firing too
                                up.consume()
                                currentOnChordTap(hit.second)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        row.pieces.forEachIndexed { index, piece ->
            val x = piece.x.roundToInt()
            val chordText = piece.chordText
            if (chordText != null) {
                val chord = piece.chord
                val tappable = palette.chordsTappable && chord != null
                val chordModifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset(x - slopH, 0) }
                    // exactly the chord row's height, so the tap target never reaches into the lyric below
                    .height(chordHDp)
                    .onPlaced { coords ->
                        if (tappable && chord != null) {
                            val pos = coords.positionInParent()
                            val size = coords.size
                            chordHits[index] = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height) to chord
                        } else {
                            chordHits.remove(index)
                        }
                    }
                    .padding(horizontal = 0.4f.gridUnitsAsDp())
                    .drawBehind {
                        if (tappable) {
                            val y = size.height - underlineInset
                            drawLine(
                                color = palette.chordColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                            )
                        }
                    }
                Box(chordModifier) {
                    BasicText(
                        text = chordText,
                        style = palette.chord.copy(color = palette.chordColor),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
            if (piece.text.isNotBlank()) {
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .offset { IntOffset(x, chordH.roundToInt()) },
                ) {
                    BasicText(
                        text = piece.text,
                        style = palette.lyric.copy(color = palette.lyricColor),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
