package com.christianlacerda.chords.chordpro

import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.NoteNames

object Transposer {
    private val FLAT_KEYS = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm")

    fun noteName(pc: Int, flats: Boolean): String = NoteNames.noteName(pc, flats)

    fun prefersFlats(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val k = key.trim().replace('♭', 'b').replace('♯', '#')
        val normalized = k.substring(0, 1).uppercase() + k.substring(1)
        return normalized in FLAT_KEYS
    }

    fun transpose(c: ChordName, semis: Int, preferFlats: Boolean): ChordName {
        val root = (c.root + semis).mod(12)
        val bass = c.bass?.let { (it + semis).mod(12) }
        val raw = noteName(root, preferFlats) + c.rawSuffix + (bass?.let { "/" + noteName(it, preferFlats) } ?: "")
        return c.copy(root = root, bass = bass, raw = raw, preferFlats = preferFlats)
    }
}
