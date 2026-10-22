package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.model.Quality
import com.christianlacerda.chords.voicing.ShapesFile
import com.christianlacerda.chords.voicing.VoicingLibrary
import com.christianlacerda.chords.voicing.computeBaseFret
import com.christianlacerda.chords.voicing.lowestSoundedString
import com.christianlacerda.chords.voicing.soundedPitchClasses
import com.christianlacerda.chords.model.Voicing
import java.io.File
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VoicingLibraryTest {
    private val text = File("src/main/assets/chords.json").readText()
    private val file = Json { ignoreUnknownKeys = true }.decodeFromString(ShapesFile.serializer(), text)
    private val lib = VoicingLibrary.fromJson(text)
    private fun c(s: String) = ChordParser.parse(s)!!

    @Test fun everyEntryParsesWithSixFretsAndFingers() {
        assertTrue(file.shapes.size > 50)
        for (e in file.shapes) {
            assertNotNull(ChordParser.parse(e.chord), e.chord)
            assertEquals(6, e.frets.size, e.chord); assertEquals(6, e.fingers.size, e.chord)
            e.frets.forEachIndexed { i, f ->
                if (f == null) assertEquals(0, e.fingers[i], "${e.chord} muted string has no finger")
                else if (f > 0) assertTrue(e.fingers[i] in 1..4, "${e.chord} fretted string $i needs a finger")
                else assertEquals(0, e.fingers[i], "${e.chord} open string has no finger")
            }
            if (e.movable) assertFalse(e.frets.any { it == 0 }, "${e.chord} movable must have no open strings")
        }
    }

    @Test fun fingeringIsPlayable() {
        for (e in file.shapes) {
            // a barre is held by one finger, so every string it covers at its fret carries that finger
            e.barre?.let { b ->
                for (i in b.from..b.to) {
                    if (e.frets[i] == b.fret) assertEquals(b.finger, e.fingers[i], "${e.chord} barre finger on string $i")
                }
            }
            // one finger can't press two different frets
            val fretsByFinger = e.frets.indices.filter { e.fingers[it] > 0 }.groupBy({ e.fingers[it] }, { e.frets[it] })
            fretsByFinger.forEach { (finger, frets) ->
                assertEquals(1, frets.toSet().size, "${e.chord} finger $finger on frets $frets")
            }
        }
    }

    @Test fun notesMatchChordQuality() {
        for (e in file.shapes) {
            val chord = c(e.chord)
            val v = Voicing(e.frets, e.fingers, computeBaseFret(e.frets), null)
            val pcs = soundedPitchClasses(v)
            val expected = chord.quality.intervals.map { (chord.root + it) % 12 }.toSet()
            assertTrue(expected.containsAll(pcs), "${e.chord}: sounded $pcs not within $expected")
            for (iv in chord.quality.intervals) {
                if (iv == 7) continue
                assertTrue((chord.root + iv) % 12 in pcs, "${e.chord}: missing interval $iv")
            }
            // the root is always present in open (non-slash) shapes
            if (chord.bass != null) {
                val low = lowestSoundedString(v)!!
                val lowPc = (com.christianlacerda.chords.voicing.STANDARD_TUNING[low] + e.frets[low]!!) % 12
                assertEquals(chord.bass, lowPc, "${e.chord}: lowest string must be the bass")
            }
        }
    }

    @Test fun movableShiftingEShape() {
        val g = lib.shapesFor(c("G"))
        assertFalse(g.approximate)
        assertEquals(listOf(3, 5, 5, 4, 3, 3), g.voicings.first { it.frets[0] == 3 && it.frets[2] == 5 }.frets)
    }

    @Test fun g7OpenThenEShape() {
        val g7 = lib.shapesFor(c("G7"))
        assertEquals(2, g7.voicings.size)
        assertEquals(listOf(3, 2, 0, 0, 0, 1), g7.voicings[0].frets)
        assertEquals(listOf(3, 5, 3, 4, 3, 3), g7.voicings[1].frets)
    }

    @Test fun cOpenThenAShape() {
        val cs = lib.shapesFor(c("C"))
        assertEquals(listOf(null, 3, 2, 0, 1, 0), cs.voicings[0].frets)
        assertEquals(listOf(null, 3, 5, 5, 5, 3), cs.voicings[1].frets)
        assertEquals(3, cs.voicings[1].barre!!.fret)
    }

    @Test fun slashChords() {
        val d = lib.shapesFor(c("D/F#"))
        assertEquals(1, d.voicings.size); assertFalse(d.approximate)
        val am = lib.shapesFor(c("Am/G"))
        assertFalse(am.approximate)
        assertEquals(null, am.lookedUp.bass)
        assertTrue(am.voicings.isNotEmpty())
    }

    @Test fun fallbackCm6ToCm() {
        val s = lib.shapesFor(c("Cm6"))
        assertTrue(s.approximate)
        assertEquals(Quality.MINOR, s.lookedUp.quality)
        assertTrue(s.voicings.isNotEmpty())
    }

    @Test fun fallbackChains() {
        val x = lib.shapesFor(c("Gm(add9)"))
        assertTrue(x.approximate && x.voicings.isNotEmpty())
        assertEquals(Quality.MINOR, x.lookedUp.quality)
        val y = lib.shapesFor(c("Dm7M"))
        assertTrue(y.approximate); assertEquals(Quality.MINOR, y.lookedUp.quality)
        assertFalse(lib.shapesFor(c("Gm9")).approximate)
        val other = lib.shapesFor(c("Em11"))
        assertEquals(Quality.MINOR, other.lookedUp.quality)
    }

    @Test fun baseFretRules() {
        assertEquals(1, computeBaseFret(listOf(null, 3, 2, 0, 1, 0)))
        assertEquals(3, computeBaseFret(listOf(3, 5, 5, 4, 3, 3))) // no open strings, so a 3fr label, not a nut
        assertEquals(3, computeBaseFret(listOf(null, 3, 5, 5, 5, 3)))
        assertEquals(1, computeBaseFret(listOf(3, 2, 0, 0, 0, 1))) // open strings keep the nut
        assertEquals(1, computeBaseFret(listOf(1, 3, 3, 2, 1, 1)))
        assertEquals(2, computeBaseFret(listOf(null, 2, 2, 2, 2, 2)))
        assertEquals(6, computeBaseFret(listOf(6, 8, 8, 7, 6, 6)))
        assertEquals(1, computeBaseFret(listOf(null, null, null, null, null, null)))
    }

    @Test fun everyChordInTableResolvesExact() {
        for (e in file.shapes) {
            val s = lib.shapesFor(c(e.chord))
            assertFalse(s.approximate, e.chord)
            assertTrue(s.voicings.isNotEmpty(), e.chord)
        }
    }

    @Test fun g7BarreShapeHasBaseFretThree() {
        val v = lib.shapesFor(c("G7")).voicings[1]
        assertEquals(3, v.baseFret)
        assertEquals(3, v.barre!!.fret)
    }
}
