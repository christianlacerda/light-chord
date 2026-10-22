package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.ChordSpelling
import com.christianlacerda.chords.chordpro.Transposer
import com.christianlacerda.chords.layout.Measurers
import com.christianlacerda.chords.layout.RenderItem
import com.christianlacerda.chords.layout.buildRenderItems
import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.model.ChordNotation.BRAZILIAN
import com.christianlacerda.chords.model.ChordNotation.INTERNATIONAL
import com.christianlacerda.chords.model.Line
import com.christianlacerda.chords.model.Section
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.model.Segment
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.model.SongMeta
import com.christianlacerda.chords.model.displayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChordSpellingTest {
    /** (written, international, Brazilian). Inputs mirror Chord Desk's chord.rs tests. */
    private val table = listOf(
        Triple("7M", "maj7", "7M"), Triple("maj7", "maj7", "7M"), Triple("M7", "maj7", "7M"), Triple("Δ", "maj7", "7M"),
        Triple("m7(5-)", "m7b5", "m7(b5)"), Triple("m7(b5)", "m7b5", "m7(b5)"), Triple("m7b5", "m7b5", "m7(b5)"),
        Triple("ø", "m7b5", "m7(b5)"), Triple("ø7", "m7b5", "m7(b5)"),
        Triple("7(9)", "9", "7(9)"), Triple("7/9", "9", "7(9)"), Triple("9", "9", "7(9)"),
        Triple("7(4/9)", "9sus4", "7(4/9)"), Triple("7(9/4)", "9sus4", "7(4/9)"), Triple("9sus4", "9sus4", "7(4/9)"),
        Triple("7(4)", "7sus4", "7(4)"), Triple("7/4", "7sus4", "7(4)"), Triple("4/7", "7sus4", "7(4)"),
        Triple("7sus4", "7sus4", "7(4)"), Triple("7sus", "7sus4", "7(4)"),
        Triple("4", "sus4", "4"), Triple("sus4", "sus4", "4"), Triple("sus", "sus4", "4"),
        Triple("sus2", "sus2", "5(9)"), Triple("5(9)", "sus2", "5(9)"),
        Triple("(9)", "add9", "(9)"), Triple("add9", "add9", "(9)"), Triple("2", "add9", "(9)"), Triple("add2", "add9", "(9)"),
        Triple("m(9)", "madd9", "m(9)"), Triple("madd9", "madd9", "m(9)"),
        Triple("6(9)", "69", "6(9)"), Triple("6/9", "69", "6(9)"), Triple("69", "69", "6(9)"),
        Triple("m6(9)", "m69", "m6(9)"),
        Triple("º", "dim", "°"), Triple("°", "dim", "°"), Triple("dim", "dim", "°"),
        Triple("º7", "dim7", "°7"), Triple("dim7", "dim7", "°7"),
        Triple("+", "aug", "+"), Triple("aug", "aug", "+"), Triple("5+", "aug", "+"), Triple("(#5)", "aug", "+"),
        Triple("+5", "aug", "+"),
        Triple("m7M", "mmaj7", "m7M"), Triple("mM7", "mmaj7", "m7M"), Triple("m(maj7)", "mmaj7", "m7M"),
        Triple("mmaj7", "mmaj7", "m7M"),
        Triple("7(9-)", "7b9", "7(b9)"), Triple("7(b9)", "7b9", "7(b9)"), Triple("7b9", "7b9", "7(b9)"),
        Triple("7/9-", "7b9", "7(b9)"),
        Triple("7(9+)", "7#9", "7(#9)"), Triple("7(#9)", "7#9", "7(#9)"), Triple("7#9", "7#9", "7(#9)"),
        Triple("7(11+)", "7#11", "7(#11)"), Triple("7(#11)", "7#11", "7(#11)"), Triple("7(4+)", "7#11", "7(#11)"),
        Triple("4+", "add#11", "(#11)"), Triple("(4+)", "add#11", "(#11)"), Triple("add#11", "add#11", "(#11)"),
        Triple("(4+/9)", "add9#11", "(9/#11)"), Triple("(9/11+)", "add9#11", "(9/#11)"),
        Triple("7M(9)", "maj9", "7M(9)"), Triple("maj9", "maj9", "7M(9)"), Triple("7M/9", "maj9", "7M(9)"),
        Triple("7M(#11)", "maj7#11", "7M(#11)"),
        Triple("m7(9)", "m9", "m7(9)"), Triple("m7/9", "m9", "m7(9)"), Triple("m9", "m9", "m7(9)"),
        Triple("m7(11)", "m11", "m7(11)"), Triple("m11", "m11", "m7(11)"), Triple("m7(4)", "m11", "m7(11)"),
        Triple("7(13)", "13", "7(13)"), Triple("13", "13", "7(13)"), Triple("7(9/13)", "13", "7(13)"),
        Triple("7(13-)", "7b13", "7(b13)"), Triple("7(b13)", "7b13", "7(b13)"), Triple("7(6-)", "7b13", "7(b13)"),
        Triple("7(5+)", "7#5", "7(#5)"), Triple("7#5", "7#5", "7(#5)"), Triple("aug7", "7#5", "7(#5)"),
        Triple("+7", "7#5", "7(#5)"),
        Triple("7(5-)", "7b5", "7(b5)"), Triple("7b5", "7b5", "7(b5)"),
        Triple("7(b9/b13)", "7b9b13", "7(b9/b13)"),
        Triple("5", "5", "5"),
        Triple("6", "6", "6"), Triple("m6", "m6", "m6"), Triple("6(11)", "6add11", "6(11)"),
        Triple("m(11)", "madd11", "m(11)"), Triple("madd11", "madd11", "m(11)"),
        Triple("-", "m", "m"), Triple("-7", "m7", "m7"), Triple("min7", "m7", "m7"), Triple("m", "m", "m"),
        Triple("7", "7", "7"), Triple("", "", ""), Triple("M", "", ""),
    )

    @Test fun bothNotationsSpellEveryForm() {
        for ((input, intl, br) in table) {
            assertEquals(intl, ChordSpelling.spell(input, INTERNATIONAL), "international of $input")
            assertEquals(br, ChordSpelling.spell(input, BRAZILIAN), "Brazilian of $input")
        }
    }

    /** Whatever one notation writes, the other reads back to the same chord. */
    @Test fun spellingsRoundTrip() {
        for ((input, intl, _) in table) {
            val br = assertNotNull(ChordSpelling.spell(input, BRAZILIAN))
            assertEquals(intl, ChordSpelling.spell(br, INTERNATIONAL), "$input -> $br")
            assertEquals(intl, ChordSpelling.spell(intl, INTERNATIONAL), "$input -> $intl")
        }
    }

    @Test fun nonsenseIsRejected() {
        for (bad in listOf("5(9)6", "7x", "m8", "(2x)", "add", "sus3", "7(9", "b4")) {
            assertNull(ChordSpelling.spell(bad, BRAZILIAN), bad)
        }
    }

    private fun show(raw: String, n: ChordNotation) = assertNotNull(ChordParser.parse(raw), raw).displayName(n)

    @Test fun displayKeepsRootAndBassAsWritten() {
        assertEquals("G#7M", show("G#maj7", BRAZILIAN))
        assertEquals("G#maj7", show("G#7M", INTERNATIONAL))
        assertEquals("Bbm7(b5)", show("Bbm7b5", BRAZILIAN))
        assertEquals("F#7(4/9)/E", show("F#9sus4/E", BRAZILIAN))
        assertEquals("D4", show("Dsus4", BRAZILIAN))
        assertEquals("Ab7M(9)/C", show("Ab7M/9/C", BRAZILIAN))
        assertEquals("C°", show("Cdim", BRAZILIAN))
        assertEquals("Bdim7", show("Bº7", INTERNATIONAL))
    }

    @Test fun unreadableQualityShownAsWritten() {
        val chord = assertNotNull(ChordParser.parse("Csus3"))
        assertEquals("Csus3", chord.displayName(BRAZILIAN))
    }

    @Test fun transposedChordsUseTheNotation() {
        val c = Transposer.transpose(assertNotNull(ChordParser.parse("Cmaj7/E")), 2, preferFlats = false)
        assertEquals("D7M/F#", c.displayName(BRAZILIAN))
    }

    @Test fun songLayoutUsesTheNotation() {
        val seg = Segment("G#maj7", ChordParser.parse("G#maj7"), "Hello")
        val annotation = Segment("N.C.", null, " world")
        val song = Song(
            SongMeta("t", null, null, null, null),
            listOf(Section(null, SectionKind.PLAIN, listOf(Line(listOf(seg, annotation))))),
        )
        val m = Measurers({ it.length.toFloat() }, { it.length.toFloat() }, 1f)
        fun chords(n: ChordNotation) = buildRenderItems(song, 1000f, m, notation = n)
            .filterIsInstance<RenderItem.Music>().flatMap { it.rows }.flatMap { it.pieces }.mapNotNull { it.chordText }
        assertEquals(listOf("G#7M", "N.C."), chords(BRAZILIAN))
        assertEquals(listOf("G#maj7", "N.C."), chords(INTERNATIONAL))
    }
}
