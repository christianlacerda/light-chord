package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.chordpro.TextDecoding
import com.christianlacerda.chords.voicing.VoicingLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SampleSongsTest {
    private val dir = File("src/main/assets/samples")
    private val lib = VoicingLibrary.fromJson(File("src/main/assets/chords.json").readText())
    private val names = File(dir, "index.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test fun fiveSongsListed() {
        assertEquals(5, names.size)
    }

    @Test fun everySampleParsesCleanly() {
        for (n in names) {
            val f = File(dir, n)
            assertTrue(f.isFile, "missing $n")
            val r = ChordProParser.parse(TextDecoding.decode(f.readBytes()), n)
            assertNull(r.issue, "$n has issue ${r.issue}")
            assertEquals(if (n == "peixe-vivo.cho") "Cantiga popular" else "Traditional", r.song.meta.artist, n)
            assertTrue(r.song.meta.key != null, n)
            assertTrue(r.song.allChords().isNotEmpty(), n)
        }
    }

    @Test fun everyChordHasExactShape() {
        for (n in names) {
            val song = ChordProParser.parse(File(dir, n).readText(), n).song
            for (seg in song.sections.flatMap { s -> s.lines.flatMap { it.segments } }) {
                if (seg.chordText != null && seg.chord == null) throw AssertionError("$n: unparseable chord ${seg.chordText}")
            }
            for (c in song.allChords()) {
                val s = lib.shapesFor(c)
                assertTrue(!s.approximate && s.voicings.isNotEmpty(), "$n: ${c.raw} has no exact shape")
            }
        }
    }

    @Test fun featuresPresent() {
        fun parse(n: String) = ChordProParser.parse(File(dir, n).readText(), n).song
        assertEquals(2, parse("scarborough-fair.cho").meta.capo)
        assertTrue(parse("scarborough-fair.cho").sections.any { it.kind == com.christianlacerda.chords.model.SectionKind.TAB })
        assertTrue(parse("wayfaring-stranger.cho").sections.count { it.kind == com.christianlacerda.chords.model.SectionKind.CHORUS } == 2)
        assertTrue(parse("shenandoah.cho").allChords().any { it.bass != null })
        assertTrue(parse("peixe-vivo.cho").allChords().any { it.raw == "C7M" })
    }
}
