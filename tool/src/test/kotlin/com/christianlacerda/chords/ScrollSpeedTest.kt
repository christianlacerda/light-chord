package com.christianlacerda.chords

import com.christianlacerda.chords.layout.ScrollSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollSpeedTest {
    @Test fun endpoints() {
        assertEquals(12.0, ScrollSpeed.secondsPerLine(1), 1e-9)
        assertEquals(2.0, ScrollSpeed.secondsPerLine(10), 1e-9)
    }

    @Test fun monotonicAndClamped() {
        for (l in 1 until 10) assertTrue(ScrollSpeed.secondsPerLine(l) > ScrollSpeed.secondsPerLine(l + 1))
        assertEquals(ScrollSpeed.secondsPerLine(1), ScrollSpeed.secondsPerLine(-5), 1e-9)
        assertEquals(ScrollSpeed.secondsPerLine(10), ScrollSpeed.secondsPerLine(99), 1e-9)
        assertEquals(4, ScrollSpeed.DEFAULT)
    }

    @Test fun pixels() {
        assertEquals(10f, ScrollSpeed.pxPerSecond(1, 120f), 1e-4f)
        assertEquals(60f, ScrollSpeed.pxPerSecond(10, 120f), 1e-3f)
    }
}
