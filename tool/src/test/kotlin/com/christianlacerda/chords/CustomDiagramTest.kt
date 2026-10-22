package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.ChordDefine
import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.model.Barre
import com.christianlacerda.chords.model.Voicing
import com.christianlacerda.chords.voicing.bassString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomDiagramTest {
    @Test fun desktopCustomShapesKeepFingerNumbersBarresAndBass() {
        val song = ChordProParser.parse(File("src/test/resources/contracts/custom-shapes.cho").readText(), "custom.cho").song
        val slash = song.defines.getValue("Am7/G")
        assertEquals(0, bassString(slash))
        assertEquals(listOf(3, 0, 2, 0, 1, 0), slash.fingers)
        assertEquals(Barre(3, 0, 5, 2), song.defines.getValue("Gm").barre)
        assertEquals(List(6) { 0 }, song.defines.getValue("C").fingers)
        assertNull(song.defines.getValue("C7#9").barre)
    }

    @Test fun bassUsesActualPitchAndHandlesAllMutedOrOpenShapes() {
        fun v(frets: List<Int?>) = Voicing(frets, List(6) { 0 }, 1, null)
        assertEquals(1, bassString(v(listOf(12, 0, null, null, null, null))))
        assertEquals(0, bassString(v(List(6) { 0 })))
        assertNull(bassString(v(List(6) { null })))
    }

    @Test fun oneFingerOnDifferentFretsIsNotABarre() {
        val shape = ChordDefine.parse("C frets x 3 4 3 x x fingers 0 2 2 2 0 0")!!.second
        assertNull(shape.barre)
    }
}
