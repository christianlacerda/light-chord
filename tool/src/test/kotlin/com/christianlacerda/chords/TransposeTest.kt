package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.Transposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransposeTest {
    private fun c(s: String) = ChordParser.parse(s)!!

    @Test fun wrapsAround() {
        assertEquals(1, Transposer.transpose(c("B"), 2, false).root)
        assertEquals(11, Transposer.transpose(c("C"), -1, false).root)
    }

    @Test fun bassTransposed() {
        val t = Transposer.transpose(c("D/F#"), 2, false)
        assertEquals(4, t.root); assertEquals(8, t.bass)
        assertEquals("E/G#", t.raw)
    }

    @Test fun spellingByKey() {
        assertEquals("Bb", Transposer.transpose(c("A"), 1, true).raw)
        assertEquals("A#", Transposer.transpose(c("A"), 1, false).raw)
        assertTrue(Transposer.prefersFlats("F")); assertTrue(Transposer.prefersFlats("Dm"))
        assertTrue(Transposer.prefersFlats("Bbm")); assertFalse(Transposer.prefersFlats("G"))
        assertFalse(Transposer.prefersFlats(null))
    }

    @Test fun keepsUserNotation() {
        assertEquals("B7M", Transposer.transpose(c("A7M"), 2, false).raw)
    }

    @Test fun roundTrip() {
        val a = c("Am7/G")
        val back = Transposer.transpose(Transposer.transpose(a, 5, false), -5, false)
        assertEquals(a.root, back.root); assertEquals(a.bass, back.bass); assertEquals(a.quality, back.quality)
        assertEquals(a.root, Transposer.transpose(a, 12, false).root)
        assertEquals(a.root, Transposer.transpose(a, -12, false).root)
    }
}
