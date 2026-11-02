package com.christianlacerda.chords.model

import com.christianlacerda.chords.timing.RehearsalTiming

data class SongMeta(
    val title: String,
    val artist: String?,
    val key: String?,
    val capo: Int?,
    val subtitle: String?,
)

enum class SectionKind { PLAIN, VERSE, CHORUS, BRIDGE, TAB }

/** [chordText] is the chord as written ("A7M"); [chord] is null if unparseable or an annotation. */
data class Segment(val chordText: String?, val chord: ChordName?, val text: String)

/** Empty [segments] = blank line. [boundary]: the line's rehearsal timing boundary, null for blank lines. */
data class Line(val segments: List<Segment>, val boundary: Int? = null)

data class Section(val label: String?, val kind: SectionKind, val lines: List<Line>)

/**
 * [defines]: shapes given in the file with {define}, keyed by [ChordName.canonicalName] (sharps).
 * [timing]: the song's rehearsal timing, null when it has none.
 */
data class Song(
    val meta: SongMeta,
    val sections: List<Section>,
    val defines: Map<String, Voicing> = emptyMap(),
    val timing: RehearsalTiming.Result? = null,
    /** Boundary count; boundary [terminal] is the end of the last line. */
    val terminal: Int = 0,
) {
    /** Every parsed chord in first-use order (duplicates included). */
    fun allChords(): List<ChordName> =
        sections.filter { it.kind != SectionKind.TAB }
            .flatMap { s -> s.lines.flatMap { l -> l.segments.mapNotNull { it.chord } } }
}
