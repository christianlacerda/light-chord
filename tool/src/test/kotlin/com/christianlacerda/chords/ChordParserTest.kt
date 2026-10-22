package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordParserTest {
    private fun p(s: String) = assertNotNull(ChordParser.parse(s), "should parse: $s")

    @Test fun plainChords() {
        p("C").let { assertEquals(0, it.root); assertEquals(Quality.MAJOR, it.quality) }
        p("F#m").let { assertEquals(6, it.root); assertEquals(Quality.MINOR, it.quality); assertTrue(!it.preferFlats) }
        p("Bb7").let { assertEquals(10, it.root); assertEquals(Quality.DOM7, it.quality); assertTrue(it.preferFlats) }
        p("Ebmaj7").let { assertEquals(3, it.root); assertEquals(Quality.MAJ7, it.quality) }
        p("Bdim").let { assertEquals(11, it.root); assertEquals(Quality.DIM, it.quality) }
        p("Csus4").let { assertEquals(Quality.SUS4, it.quality) }
        p("Gsus2").let { assertEquals(Quality.SUS2, it.quality) }
        p("Cadd9").let { assertEquals(Quality.ADD9, it.quality) }
        p("Em7").let { assertEquals(Quality.MIN7, it.quality) }
        p("Cm7b5").let { assertEquals(Quality.HALF_DIM7, it.quality) }
        p("C9").let { assertEquals(Quality.DOM9, it.quality) }
        p("Caug").let { assertEquals(Quality.AUG, it.quality) }
        p("C+").let { assertEquals(Quality.AUG, it.quality) }
        p("Cm6").let { assertEquals(Quality.MIN6, it.quality) }
        p("Cm(maj7)").let { assertEquals(Quality.MIN_MAJ7, it.quality) }
        p("C7sus4").let { assertEquals(Quality.SEVEN_SUS4, it.quality) }
        p("C-").let { assertEquals(Quality.MINOR, it.quality) }
    }

    @Test fun brazilianNames() {
        assertEquals(Quality.MAJ7, p("A7M").quality)
        assertEquals(Quality.HALF_DIM7, p("Bm7(5-)").quality)
        assertEquals(Quality.MAJ9, p("C7M(9)").quality)
        assertEquals(Quality.DOM9, p("E7(9)").quality)
        assertEquals(Quality.SUS4, p("D4").quality)
        assertEquals(Quality.ADD9, p("A2").quality)
        assertEquals(Quality.DIM, p("Bº").quality)
        assertEquals(Quality.SEVEN_SUS4, p("G7(4)").quality)
        assertEquals(Quality.MINOR, p("Cm").quality)
        assertEquals(Quality.ADD9, p("C(9)").quality)
        p("G6/9").let { assertEquals(Quality.SIX, it.quality); assertNull(it.bass) }
    }

    /** The international names Chord Desk writes for Cifra Club symbols (C9, C2, C4+, C2-, Cº, C5+(9), Cm5-(9)). */
    @Test fun cifraClubInternationalNames() {
        assertEquals(Quality.ADD9, p("Cadd9").quality)
        assertEquals(Quality.SUS2, p("Csus2").quality)
        assertEquals(Quality.SUS_SHARP4, p("Csus#4").quality)
        assertEquals(Quality.SUS_FLAT2, p("Csusb2").quality)
        assertEquals(Quality.DIM7, p("Cdim7").quality)
        assertEquals(Quality.AUG, p("Caugadd9").quality)
        assertEquals(Quality.DIM, p("Cdimadd9").quality)
        assertEquals(Quality.SUS4, p("Csus4add9").quality)
        assertEquals(Quality.SUS2, p("Csus2add11").quality)
        assertEquals(Quality.SUS_SHARP4, p("Csus#4add9").quality)
        assertEquals(Quality.SEVEN_SUS4, p("C9sus4").quality)
        p("Csus#4/E").let { assertEquals(Quality.SUS_SHARP4, it.quality); assertEquals(4, it.bass) }
        p("Cmaddb9").let { assertEquals(Quality.OTHER, it.quality); assertTrue(it.minorGuess) }
        // an altered suspension falls back to the plain one, then to the major triad
        assertEquals(Quality.SUS4, Quality.SUS_SHARP4.fallback)
        assertEquals(Quality.SUS2, Quality.SUS_FLAT2.fallback)
    }

    @Test fun lossyMappings() {
        assertEquals(Quality.SEVEN_SUS4, p("C11").quality)
        assertEquals(Quality.DOM7, p("C13").quality)
        assertEquals(Quality.DOM7, p("C7#9").quality)
        assertEquals(Quality.DOM7, p("C7b9").quality)
        p("Cm11").let { assertEquals(Quality.OTHER, it.quality); assertTrue(it.minorGuess) }
    }

    @Test fun slashChords() {
        p("D/F#").let { assertEquals(2, it.root); assertEquals(6, it.bass); assertEquals(Quality.MAJOR, it.quality) }
        p("Am/G").let { assertEquals(9, it.root); assertEquals(7, it.bass); assertEquals(Quality.MINOR, it.quality) }
        p("C/Bb").let { assertEquals(10, it.bass); assertTrue(it.preferFlats) }
    }

    @Test fun junkAndRaw() {
        assertNull(ChordParser.parse(""))
        assertNull(ChordParser.parse("H7"))
        assertNull(ChordParser.parse("xyz"))
        assertNull(ChordParser.parse("Chorus"))
        assertNull(ChordParser.parse("Verse 1"))
        assertEquals("A7M", p("A7M").raw)
        assertEquals("Bm7(5-)", p("Bm7(5-)").raw)
    }

    @Test fun canonicalNames() {
        assertEquals("Amaj7", p("A7M").canonicalName())
        assertEquals("Bbm", p("A#m").canonicalName(true))
        assertEquals("D/F#", p("D/F#").canonicalName(false))
    }
}
