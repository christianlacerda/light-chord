package com.christianlacerda.chords.model

/** Chord qualities the voicing table understands. [fallback] is the "closest" quality to try next. */
enum class Quality(val intervals: Set<Int>, val canonical: String) {
    MAJOR(setOf(0, 4, 7), ""),
    MINOR(setOf(0, 3, 7), "m"),
    DIM(setOf(0, 3, 6), "dim"),
    AUG(setOf(0, 4, 8), "aug"),
    SUS2(setOf(0, 2, 7), "sus2"),
    SUS4(setOf(0, 5, 7), "sus4"),
    SIX(setOf(0, 4, 7, 9), "6"),
    MIN6(setOf(0, 3, 7, 9), "m6"),
    DOM7(setOf(0, 4, 7, 10), "7"),
    MAJ7(setOf(0, 4, 7, 11), "maj7"),
    MIN7(setOf(0, 3, 7, 10), "m7"),
    MIN_MAJ7(setOf(0, 3, 7, 11), "mmaj7"),
    HALF_DIM7(setOf(0, 3, 6, 10), "m7b5"),
    DIM7(setOf(0, 3, 6, 9), "dim7"),
    SEVEN_SUS4(setOf(0, 5, 7, 10), "7sus4"),
    ADD9(setOf(0, 2, 4, 7), "add9"),
    MIN_ADD9(setOf(0, 2, 3, 7), "madd9"),
    DOM9(setOf(0, 2, 4, 7, 10), "9"),
    MAJ9(setOf(0, 2, 4, 7, 11), "maj9"),
    MIN9(setOf(0, 2, 3, 7, 10), "m9"),
    OTHER(emptySet(), "");

    /** Next-closest quality when no shape exists. For [OTHER] the caller decides (minor guess). */
    val fallback: Quality?
        get() = when (this) {
            MAJ9 -> MAJ7
            MAJ7 -> MAJOR
            DOM9 -> DOM7
            DOM7 -> MAJOR
            MIN9 -> MIN7
            MIN7 -> MINOR
            MIN_ADD9 -> MINOR
            ADD9 -> MAJOR
            MIN_MAJ7 -> MINOR
            SIX -> MAJOR
            MIN6 -> MINOR
            SEVEN_SUS4 -> SUS4
            SUS4 -> MAJOR
            SUS2 -> MAJOR
            DIM7 -> DIM
            DIM -> null // a diminished triad is not a minor chord: show "no shape" instead
            HALF_DIM7 -> DIM
            AUG -> MAJOR
            MAJOR, MINOR, OTHER -> null
        }
}

/**
 * A parsed chord. [raw] is exactly what the user wrote ("A7M") and is what v1 displays.
 * [rawSuffix] is the user's own quality text (between root and bass) so transposed output keeps their notation.
 * [suffix] is the normalised quality text (used for [Quality.OTHER]).
 */
data class ChordName(
    val root: Int,
    val quality: Quality,
    val bass: Int?,
    val raw: String,
    val preferFlats: Boolean,
    val suffix: String,
    val rawSuffix: String = "",
) {
    /** For [Quality.OTHER]: does the suffix look minor (leading m, not maj)? */
    val minorGuess: Boolean
        get() = suffix.startsWith("m") && !suffix.startsWith("maj")

    fun canonicalName(flats: Boolean = preferFlats): String {
        val q = if (quality == Quality.OTHER) suffix else quality.canonical
        val b = bass?.let { "/" + NoteNames.noteName(it, flats) } ?: ""
        return NoteNames.noteName(root, flats) + q + b
    }
}

/** Shared pitch-class spelling (also used by Transposer). */
object NoteNames {
    private val SHARPS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLATS = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    fun noteName(pc: Int, flats: Boolean): String = (if (flats) FLATS else SHARPS)[((pc % 12) + 12) % 12]
}
