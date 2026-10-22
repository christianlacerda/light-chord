package com.christianlacerda.chords.layout

import kotlin.math.ceil

/**
 * Paces auto-scroll by sung lines rather than pixels: a song line takes the same time however many rows it wraps
 * to, and section labels, gaps and blank lines take none (the screen glides past them with the next line).
 */
object ScrollPacing {
    /** A tab block is played, not skipped: one beat per [TAB_LINES_PER_BEAT] text lines (a six-string staff). */
    const val TAB_LINES_PER_BEAT = 6

    fun beats(item: RenderItem): Float = when (item) {
        is RenderItem.Music -> 1f
        is RenderItem.Tab -> maxOf(1f, ceil(item.text.lines().size / TAB_LINES_PER_BEAT.toFloat()))
        is RenderItem.Label, RenderItem.Gap -> 0f
    }

    /**
     * Scroll speed at [position] px. [heights] are the items' measured heights, [leadPx] the space above the first
     * item. Each item with beats spans its own height plus the zero-beat items (and lead) before it, and takes
     * beats × [secondsPerLine] to pass. Past the last line, or for an unmeasured span, [fallbackPxPerSecond].
     */
    fun pxPerSecond(
        position: Float,
        heights: FloatArray,
        beats: FloatArray,
        leadPx: Float,
        secondsPerLine: Double,
        fallbackPxPerSecond: Float,
    ): Float {
        var start = 0f
        var span = leadPx
        for (i in heights.indices) {
            span += heights[i]
            val b = beats[i]
            if (b <= 0f) continue
            val end = start + span
            if (position < end) {
                return if (span > 0f) (span / (b * secondsPerLine)).toFloat() else fallbackPxPerSecond
            }
            start = end
            span = 0f
        }
        return fallbackPxPerSecond
    }
}
