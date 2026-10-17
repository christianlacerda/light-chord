package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.layout.LineWrapper
import com.christianlacerda.chords.library.FileStat
import com.christianlacerda.chords.library.LibraryIndex
import com.christianlacerda.chords.library.LibraryIndexer
import com.christianlacerda.chords.library.PARSER_VERSION
import com.christianlacerda.chords.library.SongEntryHeader
import com.christianlacerda.chords.model.Quality
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.model.Segment
import com.christianlacerda.chords.voicing.VoicingLibrary
import com.christianlacerda.chords.voicing.soundedPitchClasses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the review findings on the parser, voicing table, wrapper and indexer. */
class ReviewFixesTest {
    private val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
    private fun chord(s: String) = assertNotNull(ChordParser.parse(s), s)
    private fun q(s: String) = chord(s).quality

    // ---- chord parser -------------------------------------------------------------------------

    @Test fun brazilianSlashExtensions() {
        assertEquals(Quality.SEVEN_SUS4, q("A7/4"))
        assertEquals(Quality.SEVEN_SUS4, q("A4/7"))
        assertEquals(Quality.DOM9, q("E7/9"))
        assertEquals(Quality.DOM7, q("E7/9-"))
        assertEquals(Quality.DOM7, q("G7/13"))
        assertEquals(Quality.MAJ9, q("C7M/9"))
        assertEquals(Quality.MIN9, q("Cm7/9"))
        assertNull(chord("E7/9").bass)
        assertEquals(Quality.SIX, q("G6/9"))
    }

    @Test fun augAndFlatFiveSpellings() {
        assertEquals(Quality.AUG, q("C5+"))
        assertEquals(Quality.AUG, q("C(5+)"))
        assertEquals(Quality.AUG, q("C(#5)"))
        assertEquals(Quality.HALF_DIM7, q("Cm7-5"))
        assertEquals(Quality.DIM, q("Cm5-"))
    }

    @Test fun capitalMIsMajorTriadUnlessSeventh() {
        assertEquals(Quality.MAJOR, q("CM"))
        assertEquals(Quality.MAJOR, q("Cmaj"))
        assertEquals(Quality.MAJ7, q("C7M"))
        assertEquals(Quality.MAJ7, q("CM7"))
        assertEquals(Quality.MAJ7, q("Cmaj7"))
    }

    // ---- voicing table ------------------------------------------------------------------------

    @Test fun diminishedWrapsToOpenStrings() {
        val adim = lib.shapesFor(chord("Adim"))
        assertFalse(adim.approximate)
        assertEquals(listOf(null, 0, 1, 2, 1, null), adim.voicings.first().frets)
        assertEquals(0, adim.voicings.first().fingers[1])
    }

    @Test fun symmetricChordsHaveSeveralPositions() {
        val c = lib.shapesFor(chord("Cdim7"))
        assertFalse(c.approximate)
        assertTrue(c.voicings.size >= 2)
        assertFalse(lib.shapesFor(chord("Eaug")).approximate)
    }

    @Test fun addedOpenShapes() {
        for (name in listOf("A6", "A9", "E7sus4", "Dsus2")) {
            val s = lib.shapesFor(chord(name))
            assertFalse(s.approximate, name)
            val c = chord(name)
            val pcs = soundedPitchClasses(s.voicings.first())
            assertTrue(pcs.all { it in c.quality.intervals.map { iv -> (c.root + iv) % 12 } }, name)
        }
    }

    @Test fun diminishedNeverFallsBackToMinor() {
        for (root in listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")) {
            val s = lib.shapesFor(chord(root + "dim"))
            assertFalse(s.approximate, "$root dim should have an exact shape")
            assertTrue(s.voicings.isNotEmpty())
        }
        assertEquals(null, Quality.DIM.fallback)
        assertEquals(Quality.DIM, Quality.HALF_DIM7.fallback)
    }

    @Test fun coreQualitiesCoverEveryRoot() {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        for (suffix in listOf("", "m", "7", "maj7", "m7", "sus4", "dim", "aug")) {
            for (r in roots) {
                val s = lib.shapesFor(chord(r + suffix))
                assertFalse(s.approximate, "$r$suffix")
                assertTrue(s.voicings.isNotEmpty(), "$r$suffix")
            }
        }
    }

    @Test fun shiftedShapesKeepCorrectNotes() {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        for (suffix in listOf("", "m", "7", "maj7", "m7", "sus4", "dim", "aug", "dim7")) {
            for (r in roots) {
                val c = chord(r + suffix)
                val allowed = c.quality.intervals.map { (c.root + it) % 12 }.toSet()
                for (v in lib.shapesFor(c).voicings) {
                    assertTrue(allowed.containsAll(soundedPitchClasses(v)), "$r$suffix ${v.frets}")
                    assertTrue(v.frets.filterNotNull().all { it >= 0 })
                }
            }
        }
    }

    // ---- wrapper ------------------------------------------------------------------------------

    @Test fun chordOnlyPieceWiderThanRowIsKept() {
        val rows = LineWrapper.wrap(
            listOf(Segment("Repeat chorus twice then go into the bridge", null, "")),
            340f, { it.length * 10f }, { it.length * 8f }, 4f,
        )
        assertTrue(rows.isNotEmpty())
        assertEquals("Repeat chorus twice then go into the bridge", rows[0].pieces[0].chordText)
    }

    // ---- ChordPro parser ----------------------------------------------------------------------

    @Test fun chorusRepeatWhileChorusStillOpen() {
        val s = ChordProParser.parse("{soc}\n[G]la\n{chorus}\n", "x.cho").song
        val choruses = s.sections.filter { it.kind == SectionKind.CHORUS }
        assertEquals(2, choruses.size)
        assertEquals(choruses[0].lines, choruses[1].lines)
    }

    @Test fun bareDirectivesDoNotEraseValues() {
        val m = ChordProParser.parse("{title: X}\n{title}\n{key: G}\n{key:}\n{capo: 2}\n{capo: x}\n[G]a", "f.cho").song.meta
        assertEquals("X", m.title); assertEquals("G", m.key); assertEquals(2, m.capo)
    }

    @Test fun selectorSuffixIsIgnoredAndNeverShownAsLyrics() {
        val s = ChordProParser.parse("{start_of_chorus-pt}\n[C]lo\n{end_of_chorus}\n", "x.cho").song
        assertEquals(SectionKind.CHORUS, s.sections.first().kind)
        assertFalse(s.sections.flatMap { it.lines }.any { l -> l.segments.any { it.text.contains("{") } })
    }

    @Test fun commentAfterLabelledVerseKeepsItsLabel() {
        val s = ChordProParser.parse("{sov: Verse 1}\n{comment: Slowly}\n[G]a\n{eov}", "x.cho").song
        assertEquals("Verse 1", s.sections.single().label)
        val unlabeled = ChordProParser.parse("{sov}\n{comment: Slowly}\n[G]a\n{eov}", "x.cho").song
        assertEquals("Slowly", unlabeled.sections.single().label)
    }

    // ---- indexer ------------------------------------------------------------------------------

    @Test fun parserVersionMismatchForcesReparseButKeepsFirstSeen() {
        val calls = ArrayList<String>()
        val parse = { n: String -> calls.add(n); SongEntryHeader("T", "A", null, null, null) }
        val first = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.cho", 5)), emptySet(), 100, parse)
        assertEquals(PARSER_VERSION, first.parserVersion)
        calls.clear()
        val stale = first.copy(parserVersion = PARSER_VERSION - 1)
        val again = LibraryIndexer.reindex(stale, listOf(FileStat("a.cho", 5)), emptySet(), 900, parse)
        assertEquals(listOf("a.cho"), calls)
        assertEquals(100, again.entries.single().firstSeen)
        assertEquals(PARSER_VERSION, again.parserVersion)
        calls.clear()
        LibraryIndexer.reindex(again, listOf(FileStat("a.cho", 5)), emptySet(), 901, parse)
        assertTrue(calls.isEmpty())
    }
}
