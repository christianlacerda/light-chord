package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordDefine
import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.model.Barre
import com.christianlacerda.chords.voicing.VoicingLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChordDefineTest {
    @Test fun openShape() {
        val (name, v) = ChordDefine.parse("Am base-fret 1 frets x 0 2 2 1 0 fingers 0 0 2 3 1 0")!!
        assertEquals("Am", name)
        assertEquals(listOf(null, 0, 2, 2, 1, 0), v.frets)
        assertEquals(listOf(0, 0, 2, 3, 1, 0), v.fingers)
        assertNull(v.barre)
    }

    @Test fun baseFretIsRelativeAndBarreIsInferred() {
        val (_, v) = ChordDefine.parse("Cm base-fret 3 frets x 1 3 3 2 1 fingers 0 1 3 4 2 1")!!
        assertEquals(listOf(null, 3, 5, 5, 4, 3), v.frets)
        assertEquals(3, v.baseFret)
        assertEquals(Barre(3, 1, 5, 1), v.barre)
    }

    @Test fun incompleteDefineIsIgnored() {
        assertNull(ChordDefine.parse("Am frets x 0 2"))
        assertNull(ChordDefine.parse("Am"))
    }

    @Test fun songDefineWinsOverTheLibrary() {
        val text = "{title: T}\n{define: F7(9)/A base-fret 5 frets x 1 x 1 2 1 fingers 0 1 0 1 2 1}\n[F7(9)/A]la\n"
        val song = ChordProParser.parse(text, "t.cho").song
        val chord = ChordParser.parse("F7(9)/A")!!
        val defined = song.defines[chord.canonicalName(false)]
        assertEquals(listOf(null, 5, null, 5, 6, 5), defined!!.frets)
        val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
        val shapes = lib.shapesFor(chord, defined)
        assertFalse(shapes.approximate)
        assertEquals(chord, shapes.lookedUp) // no "play X in the bass" note
        assertEquals(defined, shapes.voicings.first())
    }
}
