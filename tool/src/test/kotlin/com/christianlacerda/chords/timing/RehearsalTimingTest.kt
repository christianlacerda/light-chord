package com.christianlacerda.chords.timing

import com.christianlacerda.chords.timing.RehearsalTiming.Cue
import com.christianlacerda.chords.timing.RehearsalTiming.Kind
import com.christianlacerda.chords.timing.RehearsalTiming.Result
import com.christianlacerda.chords.timing.RehearsalTiming.TimingError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the contract fixture shared with Chord Desk's `src/rehearsal.rs` tests. */
class RehearsalTimingTest {
    private val fixture = Json.parseToJsonElement(
        File("src/test/resources/contracts/rehearsal-timing.json").readText(),
    ).jsonObject

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun song(name: String): String =
        fixture.getValue("canonical").jsonArray.map { it.jsonObject }.first { it.str("name") == name }.str("source")

    private fun cues(a: JsonArray) = a.map { Cue(it.jsonObject.getValue("ms").jsonPrimitive.long, it.jsonObject.getValue("boundary").jsonPrimitive.long) }

    @Test fun canonicalRecordsHashesAndTagsMatchFixture() {
        for (case in fixture.getValue("canonical").jsonArray.map { it.jsonObject }) {
            val name = case.str("name")
            val c = RehearsalTiming.canonicalize(case.str("source"))
            assertEquals(
                case.getValue("records").jsonArray.map { it.jsonPrimitive.content },
                c.records.map { (if (it.kind == Kind.TAB) "T" else "M") + "\t" + it.text },
                name,
            )
            assertEquals(
                case.getValue("line_boundaries").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.int } },
                c.records.mapIndexed { b, r -> listOf(r.line, b) },
                name,
            )
            assertEquals(case.str("sha256"), c.sha256, name)
            assertEquals(case.getValue("repeat").jsonPrimitive.boolean, c.repeat, name)
        }
    }

    @Test fun timingValidationMatchesFixture() {
        for (case in fixture.getValue("timing").jsonArray.map { it.jsonObject }) {
            val name = case.str("name")
            val result = RehearsalTiming.parse(case.str("value"), RehearsalTiming.canonicalize(song(case.str("song"))))
            val expected = case.getValue("error").takeIf { it != JsonNull }?.jsonPrimitive?.content
            assertEquals(expected, (result as? Result.Invalid)?.error?.code, name)
        }
    }

    @Test fun interpolationMatchesFixture() {
        val interp = fixture.getValue("interpolation").jsonObject
        val cues = cues(interp.getValue("cues").jsonArray)
        for (g in interp.getValue("geometries").jsonArray.map { it.jsonObject }) {
            val positions = g.getValue("positions").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            for (s in g.getValue("samples").jsonArray.map { it.jsonArray }) {
                val ms = s[0].jsonPrimitive.long
                assertEquals(s[1].jsonPrimitive.double, RehearsalTiming.positionAt(ms, cues, positions), 1e-9, "${g.str("geometry")} at $ms")
            }
        }
    }

    @Test fun directiveLineRoundTripsAndIsFoundInTheSong() {
        val d = fixture.getValue("directive").jsonObject
        val base = song(d.str("song"))
        val line = d.str("line")
        val timing = (RehearsalTiming.parse(d.str("value"), RehearsalTiming.canonicalize(base)) as Result.Ok).timing
        assertEquals(line, RehearsalTiming.directiveLine(timing))
        assertEquals(Result.Ok(timing), RehearsalTiming.read("$line\n$base"))
        assertNull(RehearsalTiming.read(base))
        assertEquals(Result.Invalid(TimingError.MALFORMED), RehearsalTiming.read("$line\n$line\n$base"))
        assertEquals(1, RehearsalTiming.timingValues(line.replaceFirst("meta:", "meta")).size)
        assertTrue(RehearsalTiming.timingValues("{meta: lightchord_timing_v2 {}}").isEmpty())
    }

    @Test fun directiveForms() {
        assertEquals("start_of_tab" to "", RehearsalTiming.directive("  {Start_Of_Tab-guitar}  "))
        assertEquals("c" to "Riff", RehearsalTiming.directive("{c Riff}"))
        assertEquals("title" to "A", RehearsalTiming.directive("{title :  A  }"))
        assertNull(RehearsalTiming.directive("{title(x)}"))
        assertNull(RehearsalTiming.directive("{}"))
        assertNull(RehearsalTiming.directive("[G]{x}"))
    }

    @Test fun seekingFindsTheTimeOfADraggedPosition() {
        // intro hold at 0..4000, then 0 → 100 px over 4000..8000, hold at 100 until 9000, then 100 → 300 by 11000
        val cues = listOf(Cue(0, 0), Cue(4000, 0), Cue(8000, 1), Cue(9000, 1), Cue(11000, 2))
        val positions = doubleArrayOf(0.0, 100.0, 300.0)
        assertEquals(6000, RehearsalTiming.timeAt(50.0, cues, positions, preferMs = 1000))
        assertEquals(2500, RehearsalTiming.timeAt(0.0, cues, positions, preferMs = 2500), "current time already maps there")
        assertEquals(4000, RehearsalTiming.timeAt(0.0, cues, positions, preferMs = 7000), "closest end of the hold")
        assertEquals(8500, RehearsalTiming.timeAt(100.0, cues, positions, preferMs = 8500))
        assertEquals(9000, RehearsalTiming.timeAt(100.0, cues, positions, preferMs = 10000))
        assertEquals(8000, RehearsalTiming.timeAt(100.0, cues, positions, preferMs = 2000))
        assertEquals(11000, RehearsalTiming.timeAt(500.0, cues, positions, preferMs = 0))
        assertEquals(10000, RehearsalTiming.timeAt(200.0, cues, positions, preferMs = 0))
    }

    @Test fun hiddenBoundariesTakeTheNextVisiblePositionAndOffsetsClamp() {
        // boundaries 1 and 2 are hidden tab rows
        val p = RehearsalTiming.resolvePositions(4, mapOf(0 to 50f, 3 to 400f), terminalTop = 900f, readingY = 100f, maxScroll = 700f)
        assertEquals(listOf(0.0, 300.0, 300.0, 300.0, 700.0), p.toList())
    }

    @Test fun parserTagsLinesWithTheCanonicalBoundaries() {
        for (case in fixture.getValue("canonical").jsonArray.map { it.jsonObject }) {
            if (case.getValue("repeat").jsonPrimitive.boolean) continue // {chorus} copies lines; timing unsupported
            val song = com.christianlacerda.chords.chordpro.ChordProParser.parse(case.str("source"), "x.cho").song
            val tagged = song.sections.flatMap { it.lines }.mapNotNull { it.boundary }
            val expected = case.getValue("line_boundaries").jsonArray.map { it.jsonArray[1].jsonPrimitive.int }
            assertEquals(expected, tagged, case.str("name"))
            assertEquals(expected.size, song.terminal, case.str("name"))
        }
    }
}
