package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.chordpro.ParseIssue
import com.christianlacerda.chords.model.Quality
import com.christianlacerda.chords.model.SectionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProParserTest {
    private fun parse(s: String, name: String = "file.cho") = ChordProParser.parse(s, name)

    @Test fun metaDirectives() {
        val r = parse("{title: T}\n{subtitle: S}\n{artist: A}\n{key: G}\n{capo: 2}\n[G]hi")
        assertNull(r.issue)
        with(r.song.meta) { assertEquals("T", title); assertEquals("S", subtitle); assertEquals("A", artist); assertEquals("G", key); assertEquals(2, capo) }
        val s = parse("{t:Short}\n{st:Sub}\n{meta: artist Some One}\n{meta: key D}\n[D]x").song.meta
        assertEquals("Short", s.title); assertEquals("Some One", s.artist); assertEquals("D", s.key)
    }

    @Test fun titleFallbackAndNullArtist() {
        val m = parse("[C]hello", "my-song.cho").song.meta
        assertEquals("my-song", m.title)
        assertNull(m.artist)
        assertNull(m.capo)
    }

    @Test fun commentBecomesLabel() {
        val song = parse("{comment: Verse 1}\n[G]one\n\n{c: Verse 2}\n[G]two").song
        assertEquals(listOf("Verse 1", "Verse 2"), song.sections.map { it.label })
        assertEquals(2, song.sections.size)
        assertEquals(1, song.sections[0].lines.size)
    }

    @Test fun environmentsAndChorusCopy() {
        val song = parse(
            "{sov}\n[G]v1\n{eov}\n{soc}\n[C]chorus line\n{eoc}\n{start_of_bridge: Bridge A}\nb\n{end_of_bridge}\n{chorus}",
        ).song
        assertEquals(listOf(SectionKind.VERSE, SectionKind.CHORUS, SectionKind.BRIDGE, SectionKind.CHORUS), song.sections.map { it.kind })
        assertNull(song.sections[0].label)
        assertEquals("Bridge A", song.sections[2].label)
        assertEquals(song.sections[1].lines, song.sections[3].lines)
    }

    @Test fun chorusWithoutPriorChorusIsLabelOnly() {
        val song = parse("{chorus}\n[C]x").song
        assertEquals(SectionKind.CHORUS, song.sections[0].kind)
        assertTrue(song.sections[0].lines.isEmpty())
    }

    @Test fun tabIsVerbatim() {
        val song = parse("{sot}\ne|--[0]--|\nB|-----|\n{eot}\n[G]after").song
        val tab = song.sections[0]
        assertEquals(SectionKind.TAB, tab.kind)
        assertEquals("e|--[0]--|", tab.lines[0].segments.single().text)
        assertNull(tab.lines[0].segments.single().chordText)
        assertEquals(SectionKind.PLAIN, song.sections[1].kind)
    }

    @Test fun inlineChords() {
        val line = parse("Before [G]one [*riff]two[Am7]").song.sections[0].lines[0].segments
        assertEquals(null, line[0].chordText); assertEquals("Before ", line[0].text)
        assertEquals("G", line[1].chordText); assertEquals(Quality.MAJOR, line[1].chord!!.quality); assertEquals("one ", line[1].text)
        assertEquals("riff", line[2].chordText); assertNull(line[2].chord); assertEquals("two", line[2].text)
        assertEquals("Am7", line[3].chordText); assertEquals("", line[3].text)
    }

    @Test fun unparseableChordKeepsText() {
        val seg = parse("[Foo]bar").song.sections[0].lines[0].segments.single()
        assertEquals("Foo", seg.chordText); assertNull(seg.chord); assertEquals("bar", seg.text)
    }

    @Test fun chordOnlyLine() {
        val segs = parse("[G] [D]").song.sections[0].lines[0].segments
        assertEquals(2, segs.size); assertEquals(" ", segs[0].text); assertEquals("", segs[1].text)
    }

    @Test fun blankLinesCollapse() {
        val lines = parse("\n\n[G]a\n\n\n\n[G]b\n\n").song.sections.single().lines
        assertEquals(3, lines.size)
        assertTrue(lines[1].segments.isEmpty())
    }

    @Test fun hashCommentsUnknownDirectivesCrlfBom() {
        val r = parse("﻿# a comment\r\n{title: X}\r\n{define: G base-fret 1}\r\n{weird}\r\n[G]hi\r\n")
        assertNull(r.issue)
        assertEquals("X", r.song.meta.title)
        assertEquals(1, r.song.sections.single().lines.size)
        assertEquals("hi", r.song.sections[0].lines[0].segments[0].text)
    }

    @Test fun issues() {
        assertEquals(ParseIssue.NOT_CHORDPRO, parse("just some words\nand more").issue)
        assertEquals(ParseIssue.EMPTY, parse("").issue)
        assertEquals(ParseIssue.EMPTY, parse("  \n\n").issue)
        assertEquals(ParseIssue.EMPTY, parse("{title: Only}").issue)
        assertNull(parse("{title: x}\nplain text").issue)
        assertNotNull(parse("").song)
    }

    @Test fun allChords() {
        val chords = parse("[G]a [C]b [G]c").song.allChords().map { it.raw }
        assertEquals(listOf("G", "C", "G"), chords)
    }
}
