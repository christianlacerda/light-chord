package com.christianlacerda.chords.model

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

/** Empty [segments] = blank line. */
data class Line(val segments: List<Segment>)

data class Section(val label: String?, val kind: SectionKind, val lines: List<Line>)

/** [defines]: shapes given in the file with {define}, keyed by [ChordName.canonicalName] (sharps). */
data class Song(val meta: SongMeta, val sections: List<Section>, val defines: Map<String, Voicing> = emptyMap()) {
    /** Every parsed chord in first-use order (duplicates included). */
    fun allChords(): List<ChordName> =
        sections.filter { it.kind != SectionKind.TAB }
            .flatMap { s -> s.lines.flatMap { l -> l.segments.mapNotNull { it.chord } } }
}
