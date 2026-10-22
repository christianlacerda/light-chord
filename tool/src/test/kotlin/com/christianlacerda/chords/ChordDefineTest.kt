package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordDefine
import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.model.Barre
import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.model.displayName
import com.christianlacerda.chords.voicing.VoicingLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChordDefineTest {
    @Test fun sharedContractFixturePreservesCifraNamesAndDefinitions() {
        val source = File("src/test/resources/contracts/chord-identities.cho").readText()
        val song = ChordProParser.parse(source, "test.cho").song
        val chords = song.allChords().distinctBy { it.canonicalName(false) }
        assertEquals(9, chords.size)
        assertEquals(6, song.defines.size)
        assertEquals(
            listOf("C7", "C7(9-)", "C7(9+)", "C7(13)", "C6", "C6(9)", "C7M", "C9", "C7(9)"),
            chords.map { it.displayName(ChordNotation.BRAZILIAN) },
        )
        assertEquals(3, song.defines.getValue("C7").frets[1])
        assertEquals(2, song.defines.getValue("C7b9").frets[4])
        assertEquals(4, song.defines.getValue("C7#9").frets[4])
    }

    @Test fun alteredChordsKeepTheirOwnDefinitionsAndGridIdentities() {
        val names = listOf("C7", "C7b9", "C7#9", "C13", "C6", "C6/9")
        val text = names.mapIndexed { i, name ->
            "{define: $name base-fret 1 frets x ${i + 1} 2 3 1 0}"
        }.joinToString("\n") + "\n" + names.joinToString(" ") { "[$it]" }
        val song = ChordProParser.parse(text, "test.cho").song
        val chords = song.allChords().distinctBy { it.canonicalName(false) }
        assertEquals(names.size, chords.size)
        assertEquals(names.size, song.defines.size)
        val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
        chords.forEachIndexed { i, chord ->
            val defined = song.defines.getValue(chord.canonicalName(false))
            val shapes = lib.shapesFor(chord, defined)
            assertEquals(i + 1, shapes.voicings.first().frets[1])
            assertFalse(shapes.approximate)
            if (!chord.hasExactQuality) assertEquals(listOf(defined), shapes.voicings)
        }
    }

    @Test fun equivalentSpellingsStillShareAnIdentity() {
        for ((a, b) in listOf("C7(9-)" to "C7b9", "C7M" to "Cmaj7", "C6/9" to "C69")) {
            assertEquals(ChordParser.parse(a)!!.canonicalName(false), ChordParser.parse(b)!!.canonicalName(false))
        }
    }

    @Test fun simplifiedShapesAreLabelledAsApproximations() {
        val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
        val shapes = lib.shapesFor(ChordParser.parse("C7b9")!!)
        assertEquals(true, shapes.approximate)
        assertEquals("C7", shapes.lookedUp.canonicalName(false))
    }

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

    @Test fun noBarreAcrossAMutedString() {
        val (_, v) = ChordDefine.parse("X base-fret 5 frets x 1 x 1 2 1 fingers 0 1 0 1 2 1")!!
        assertNull(v.barre)
    }

    @Test fun incompleteDefineIsIgnored() {
        assertNull(ChordDefine.parse("Am frets x 0 2"))
        assertNull(ChordDefine.parse("Am"))
    }

    @Test fun songDefineWinsOverTheLibrary() {
        val text = "{title: T}\n{define: F7(9)/A base-fret 1 frets x 0 1 0 1 1 fingers 0 0 1 0 2 3}\n[F7(9)/A]la\n"
        val song = ChordProParser.parse(text, "t.cho").song
        val chord = ChordParser.parse("F7(9)/A")!!
        val defined = song.defines[chord.canonicalName(false)]
        assertEquals(listOf(null, 0, 1, 0, 1, 1), defined!!.frets)
        val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
        val shapes = lib.shapesFor(chord, defined)
        assertFalse(shapes.approximate)
        assertEquals(chord, shapes.lookedUp) // no "play X in the bass" note
        assertEquals(defined, shapes.voicings.first())
    }
}
