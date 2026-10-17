package com.christianlacerda.chords

import com.christianlacerda.chords.ui.WheelInput
import com.christianlacerda.chords.ui.WheelKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WheelInputTest {
    private val steps = ArrayList<Int>()
    private var presses = 0
    private val w = WheelInput({ steps.add(it) }, { presses++ })

    @Test fun lpWheelCodes() {
        assertEquals(317, WheelKeys.WHEEL_A); assertEquals(318, WheelKeys.WHEEL_B); assertEquals(319, WheelKeys.WHEEL_PRESS)
        assertTrue(w.onKey(317, WheelInput.ACTION_DOWN, 0))
        assertTrue(w.onKey(318, WheelInput.ACTION_DOWN, 0))
        assertEquals(listOf(1, -1), steps)
    }

    @Test fun pressTogglesOncePerDown() {
        assertTrue(w.onKey(319, WheelInput.ACTION_DOWN, 0))
        assertTrue(w.onKey(319, WheelInput.ACTION_UP, 0))
        assertEquals(1, presses); assertTrue(steps.isEmpty())
    }

    @Test fun dpadCenterAndEnterTogglePlay() {
        assertTrue(w.onKey(23, WheelInput.ACTION_DOWN, 0))
        assertTrue(w.onKey(66, WheelInput.ACTION_DOWN, 0))
        assertTrue(w.onKey(66, WheelInput.ACTION_DOWN, 1)) // held: consumed, no extra toggle
        assertEquals(2, presses)
    }

    @Test fun dpadFallbacksAndRepeat() {
        w.onKey(20, WheelInput.ACTION_DOWN, 2)
        assertEquals(listOf(-1, -1, -1), steps)
        w.onKey(19, WheelInput.ACTION_DOWN, 0)
        assertEquals(1, steps.last())
    }

    @Test fun upIsConsumedButIgnored() {
        assertTrue(w.onKey(317, WheelInput.ACTION_UP, 0)); assertTrue(steps.isEmpty())
    }

    @Test fun unknownKeysNotConsumed() {
        assertFalse(w.onKey(29, WheelInput.ACTION_DOWN, 0)); assertTrue(steps.isEmpty())
    }

    @Test fun rotaryAccumulates() {
        w.onRotary(30f); assertTrue(steps.isEmpty())
        w.onRotary(30f); assertEquals(listOf(1), steps)
        w.onRotary(-200f); assertEquals(listOf(1, -1, -1, -1), steps)
    }
}
