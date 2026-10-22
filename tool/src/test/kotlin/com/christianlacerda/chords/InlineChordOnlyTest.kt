package com.christianlacerda.chords

import com.christianlacerda.chords.layout.inlineChordOnly
import com.christianlacerda.chords.model.Segment
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineChordOnlyTest {
    @Test fun bracketsJoinTheChordRow() {
        // ( [Em7] [G] )
        val line = listOf(Segment(null, null, "( "), Segment("Em7", null, "  "), Segment("G", null, " )"))
        assertEquals(
            listOf(Segment("(", null, ""), Segment("Em7", null, ""), Segment("G )", null, "")),
            inlineChordOnly(line),
        )
    }

    @Test fun lyricsAndPlainTextAreLeftAlone() {
        val lyric = listOf(Segment("C", null, "Hello "), Segment("G", null, "there"))
        assertEquals(lyric, inlineChordOnly(lyric))
        val noChords = listOf(Segment(null, null, "( x2 )"))
        assertEquals(noChords, inlineChordOnly(noChords))
    }
}
