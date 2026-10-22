package com.christianlacerda.chords

import com.christianlacerda.chords.layout.RenderItem
import com.christianlacerda.chords.layout.ScrollPacing
import com.christianlacerda.chords.model.SectionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollPacingTest {
    private val spl = 4.0
    private val fallback = 30f

    private fun speed(pos: Float, heights: FloatArray, beats: FloatArray, lead: Float = 0f) =
        ScrollPacing.pxPerSecond(pos, heights, beats, lead, spl, fallback)

    @Test fun wrappedLineTakesOneLineOfTime() {
        // a one-row line (100px) then a three-row line (300px): both pass in 4 s
        val h = floatArrayOf(100f, 300f)
        val b = floatArrayOf(1f, 1f)
        assertEquals(25f, speed(50f, h, b), 1e-4f)
        assertEquals(75f, speed(250f, h, b), 1e-4f)
    }

    @Test fun labelsAndGapsRideWithTheNextLine() {
        // gap 20, label 40, line 100: the 160px span takes one line of time
        val h = floatArrayOf(20f, 40f, 100f)
        val b = floatArrayOf(0f, 0f, 1f)
        assertEquals(40f, speed(10f, h, b), 1e-4f)
        assertEquals(40f, speed(150f, h, b), 1e-4f)
    }

    @Test fun leadJoinsTheFirstLine() {
        assertEquals(30f, speed(0f, floatArrayOf(100f), floatArrayOf(1f), lead = 20f), 1e-4f)
    }

    @Test fun pastTheEndAndUnmeasuredFallBack() {
        assertEquals(fallback, speed(500f, floatArrayOf(100f), floatArrayOf(1f)), 1e-4f)
        assertEquals(fallback, speed(0f, floatArrayOf(0f), floatArrayOf(1f)), 1e-4f)
    }

    @Test fun beats() {
        assertEquals(0f, ScrollPacing.beats(RenderItem.Gap))
        assertEquals(0f, ScrollPacing.beats(RenderItem.Label("Intro", SectionKind.PLAIN)))
        assertEquals(1f, ScrollPacing.beats(RenderItem.Music(emptyList())))
        assertEquals(2f, ScrollPacing.beats(RenderItem.Tab((1..12).joinToString("\n") { "e|--" })))
    }
}
