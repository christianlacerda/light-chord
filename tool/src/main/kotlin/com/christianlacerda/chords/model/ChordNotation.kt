package com.christianlacerda.chords.model

import com.christianlacerda.chords.chordpro.ChordSpelling

/** How chord names are shown. Brazilian is the default; files are not changed either way. */
enum class ChordNotation {
    /** Cifragem popular: G#7M, Bm7(b5), D4, A7(4/9), C(9), B°. */
    BRAZILIAN,

    /** G#maj7, Bm7b5, Dsus4, A9sus4, Cadd9, Bdim. */
    INTERNATIONAL,
}

/**
 * The chord as shown in [notation]: the root and bass keep the spelling they were written with, the quality is
 * re-spelled. A quality the speller can't read is shown as written.
 */
fun ChordName.displayName(notation: ChordNotation): String {
    val quality = ChordSpelling.spell(rawSuffix, notation) ?: rawSuffix
    val written = raw.filterNot { it.isWhitespace() }
    val rootLength = if (written.length > 1 && written[1] in "#b♯♭") 2 else 1
    if (written.regionMatches(rootLength, rawSuffix, 0, rawSuffix.length)) {
        return written.substring(0, rootLength) + quality + written.substring(rootLength + rawSuffix.length)
    }
    val bassName = bass?.let { "/" + NoteNames.noteName(it, preferFlats) } ?: ""
    return NoteNames.noteName(root, preferFlats) + quality + bassName
}
