package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.layout.LineWrapper
import com.christianlacerda.chords.layout.Measurers
import com.christianlacerda.chords.layout.RenderItem
import com.christianlacerda.chords.layout.WrappedRow
import com.christianlacerda.chords.layout.buildRenderItems
import com.christianlacerda.chords.model.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineWrapperTest {
    private fun segs(line: String): List<Segment> =
        ChordProParser.parse(line, "x.cho").song.sections.single().lines.single().segments

    private fun wrap(line: String, width: Float, gap: Float = 4f): List<WrappedRow> =
        LineWrapper.wrap(segs(line), width, { it.length * 10f }, { it.length * 8f }, gap)

    private fun rowText(r: WrappedRow) = r.pieces.joinToString("") { it.text }

    @Test fun fitsOnOneRow() {
        val rows = wrap("[G]hello [C]world", 500f)
        assertEquals(1, rows.size)
        assertEquals("hello world", rowText(rows[0]))
        assertTrue(rows[0].hasChords && rows[0].hasLyrics)
        assertEquals(0f, rows[0].pieces[0].x)
        assertEquals(60f, rows[0].pieces[1].x)
    }

    @Test fun wrapsOnlyAtWordBoundaries() {
        val rows = wrap("one two three four", 100f)
        assertEquals(listOf("one two ", "three four"), rows.map { rowText(it) })
    }

    @Test fun midWordChordNeverSplits() {
        val rows = wrap("x a[G]maz[D]ing grace", 80f)
        val words = rows.map { rowText(it).trim() }
        assertTrue(words.any { it == "amazing" }, words.toString())
        assertFalse(words.any { it.contains("amaz") && it != "amazing" })
    }

    @Test fun chordStaysOnItsAtomAfterWrap() {
        val rows = wrap("aaaa bbbb [Am]cccc dddd", 100f)
        val rowWithChord = rows.first { it.hasChords }
        val piece = rowWithChord.pieces.first { it.chordText == "Am" }
        assertEquals("cccc ", piece.text)
        assertEquals("Am", piece.chord!!.raw)
    }

    @Test fun paddingWhenChordWiderThanText() {
        val rows = wrap("[Bm7b5]a b", 500f, gap = 4f)
        // chord width 5*8+4 = 44 > lyric width "a " = 20
        assertEquals(44f, rows[0].pieces[1].x)
    }

    @Test fun overlongWordForceSplit() {
        val rows = wrap("supercalifragilistic", 100f)
        assertTrue(rows.size >= 2)
        assertEquals("supercalifragilistic", rows.joinToString("") { rowText(it) })
        assertTrue(rows.all { r -> r.pieces.sumOf { it.text.length } * 10 <= 100 })
    }

    @Test fun leadingSpacesTrimmedOnContinuation() {
        val rows = wrap("aaaa bbbb", 45f)
        assertEquals(listOf("aaaa ", "bbbb"), rows.map { rowText(it) })
        val rows2 = wrap("aaaa[G] bbbb", 45f)
        assertTrue(rows2.size >= 2)
        assertFalse(rowText(rows2[1]).startsWith(" "))
    }

    @Test fun chordOnlyRowHasNoLyrics() {
        val rows = wrap("[G] [D] [Em]", 500f)
        assertEquals(1, rows.size)
        assertTrue(rows[0].hasChords); assertFalse(rows[0].hasLyrics)
    }

    @Test fun lyricOnlyRowHasNoChords() {
        val rows = wrap("just words here", 500f)
        assertFalse(rows[0].hasChords)
    }

    @Test fun buildRenderItems() {
        val song = ChordProParser.parse("{comment: V1}\n[G]a b\n\n[C]c\n{comment: V2}\n[G]d", "s.cho").song
        val items = buildRenderItems(song, 500f, Measurers({ it.length * 10f }, { it.length * 8f }, 4f))
        assertTrue(items.first() is RenderItem.Label)
        assertEquals(2, items.count { it is RenderItem.Label })
        assertEquals(3, items.count { it is RenderItem.Music })
        assertTrue(items.contains(RenderItem.Gap))
    }

    @Test fun hiddenTabsLeaveNoLabelOrGap() {
        val song = ChordProParser.parse("{sot}\ne|--0--|\n{eot}\n{comment: V1}\n[G]a", "s.cho").song
        val m = Measurers({ it.length * 10f }, { it.length * 8f }, 4f)
        val shown = buildRenderItems(song, 500f, m)
        assertTrue(shown.any { it is RenderItem.Tab })
        val hidden = buildRenderItems(song, 500f, m, showTabs = false)
        assertFalse(hidden.any { it is RenderItem.Tab })
        assertEquals(RenderItem.Label("V1", com.christianlacerda.chords.model.SectionKind.PLAIN), hidden.first())
    }
}
