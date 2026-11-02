package com.christianlacerda.chords

import com.christianlacerda.chords.layout.ScrollSpeed
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollSpeedTest {
    /** The pre-half-step curve, by whole level. */
    private fun legacySeconds(level: Int) = 12.0 * (1.0 / 8.0).pow((level - 1) / 9.0)

    @Test fun endpoints() {
        assertEquals(12.0, ScrollSpeed.secondsPerLine(ScrollSpeed.MIN), 1e-9)
        assertEquals(1.5, ScrollSpeed.secondsPerLine(ScrollSpeed.MAX), 1e-9)
        assertEquals(18, ScrollSpeed.MAX)
    }

    @Test fun wholeLevelsKeepTheirFormerSpeeds() {
        for (level in 1..10) {
            assertEquals(legacySeconds(level), ScrollSpeed.secondsPerLine(ScrollSpeed.tickOf(level)), 1e-9)
        }
        assertEquals(ScrollSpeed.tickOf(4), ScrollSpeed.DEFAULT)
    }

    @Test fun everyTickIsAbout12PercentFaster() {
        for (t in ScrollSpeed.MIN until ScrollSpeed.MAX) {
            val ratio = ScrollSpeed.secondsPerLine(t) / ScrollSpeed.secondsPerLine(t + 1)
            assertEquals(8.0.pow(1.0 / 18), ratio, 1e-9)
            assertTrue(ratio in 1.12..1.13)
        }
    }

    @Test fun clamped() {
        assertEquals(ScrollSpeed.secondsPerLine(ScrollSpeed.MIN), ScrollSpeed.secondsPerLine(-5), 1e-9)
        assertEquals(ScrollSpeed.secondsPerLine(ScrollSpeed.MAX), ScrollSpeed.secondsPerLine(99), 1e-9)
        assertEquals(0, ScrollSpeed.tickOf(-3))
        assertEquals(18, ScrollSpeed.tickOf(42))
    }

    @Test fun legacyLevelRoundsHalfStepsDown() {
        assertEquals(4, ScrollSpeed.legacyLevel(6))
        assertEquals(4, ScrollSpeed.legacyLevel(7))
        assertEquals(10, ScrollSpeed.legacyLevel(18))
        assertEquals(1, ScrollSpeed.legacyLevel(-1))
    }

    @Test fun labels() {
        assertEquals("1", ScrollSpeed.label(0))
        assertEquals("4", ScrollSpeed.label(6))
        assertEquals("4.5", ScrollSpeed.label(7))
        assertEquals("4,5", ScrollSpeed.label(7, ','))
        assertEquals("10", ScrollSpeed.label(18))
    }

    @Test fun rateLabels() {
        assertEquals("1x", ScrollSpeed.rateLabel(100))
        assertEquals("1.05x", ScrollSpeed.rateLabel(105))
        assertEquals("1.5x", ScrollSpeed.rateLabel(150))
        assertEquals("0.5x", ScrollSpeed.rateLabel(50))
        assertEquals("0,95x", ScrollSpeed.rateLabel(95, ','))
    }

    @Test fun pixels() {
        assertEquals(10f, ScrollSpeed.pxPerSecond(ScrollSpeed.tickOf(1), 120f), 1e-4f)
        assertEquals(80f, ScrollSpeed.pxPerSecond(ScrollSpeed.tickOf(10), 120f), 1e-3f)
    }
}
