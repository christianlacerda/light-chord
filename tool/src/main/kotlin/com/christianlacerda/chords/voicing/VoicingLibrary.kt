package com.christianlacerda.chords.voicing

import com.christianlacerda.chords.chordpro.ChordParser
import com.christianlacerda.chords.chordpro.Transposer
import com.christianlacerda.chords.model.Barre
import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.Quality
import com.christianlacerda.chords.model.Voicing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BarreDto(val fret: Int, val from: Int, val to: Int, val finger: Int = 1)

@Serializable
data class ShapeEntry(
    val chord: String,
    val movable: Boolean = false,
    val frets: List<Int?>,
    val fingers: List<Int>,
    val barre: BarreDto? = null,
)

@Serializable
data class ShapesFile(val version: Int = 1, val shapes: List<ShapeEntry>)

/** [lookedUp] is the chord actually searched (differs from [chord] on a fallback or dropped bass). */
data class ChordShapes(
    val chord: ChordName,
    val lookedUp: ChordName,
    val voicings: List<Voicing>,
    val approximate: Boolean,
)

class VoicingLibrary(entries: List<ShapeEntry>) {
    private class Parsed(val chord: ChordName, val entry: ShapeEntry)

    private val parsed: List<Parsed> = entries.map {
        val c = ChordParser.parse(it.chord)
            ?: throw IllegalArgumentException("Unparseable chord in voicing table: ${it.chord}")
        require(it.frets.size == 6 && it.fingers.size == 6) { "Shape ${it.chord} needs 6 frets and 6 fingers" }
        Parsed(c, it)
    }

    private val open = parsed.filter { !it.entry.movable }
    private val movable = parsed.filter { it.entry.movable }

    fun shapesFor(chord: ChordName): ChordShapes {
        // 1. Exact (root, quality, bass).
        if (chord.quality != Quality.OTHER) {
            val exact = open.filter {
                it.chord.root == chord.root && it.chord.quality == chord.quality && it.chord.bass == chord.bass
            }
            if (exact.isNotEmpty() && chord.bass != null) {
                // A slash chord with a dedicated shape: root-position movables would have the wrong bass.
                return ChordShapes(chord, chord, dedupe(exact.map { build(it, 0) }), false)
            }
            val found = lookup(chord.root, chord.quality)
            if (found.isNotEmpty()) {
                return ChordShapes(chord, withoutBass(chord, chord.quality), found, false)
            }
        }
        // 4. Fallback chain.
        var q: Quality? = fallbackOf(chord, chord.quality)
        while (q != null) {
            val found = lookup(chord.root, q)
            if (found.isNotEmpty()) return ChordShapes(chord, withoutBass(chord, q), found, true)
            q = fallbackOf(chord, q)
        }
        return ChordShapes(chord, withoutBass(chord, chord.quality), emptyList(), true)
    }

    private fun fallbackOf(chord: ChordName, q: Quality): Quality? =
        if (q == Quality.OTHER) (if (chord.minorGuess) Quality.MINOR else Quality.MAJOR) else q.fallback

    private fun withoutBass(chord: ChordName, q: Quality): ChordName {
        if (q == chord.quality && chord.bass == null) return chord
        val suffix = if (q == Quality.OTHER) chord.suffix else q.canonical
        return ChordName(
            chord.root, q, null,
            Transposer.noteName(chord.root, chord.preferFlats) + suffix,
            chord.preferFlats, suffix, suffix,
        )
    }

    /** Open shapes for the (root, quality), then movable shapes shifted to [root]. */
    private fun lookup(root: Int, quality: Quality): List<Voicing> {
        val out = ArrayList<Voicing>()
        open.filter { it.chord.root == root && it.chord.quality == quality && it.chord.bass == null }
            .forEach { out.add(build(it, 0)) }
        val shifted = movable.filter { it.chord.quality == quality }
            .map { build(it, (root - it.chord.root).mod(12)) }
            .filter { it.baseFret <= MAX_MOVABLE_BASE_FRET }
            .sortedBy { it.baseFret }
        out.addAll(shifted)
        return dedupe(out)
    }

    private fun dedupe(list: List<Voicing>): List<Voicing> = list.distinctBy { it.frets }

    private fun build(p: Parsed, delta: Int): Voicing {
        val frets = p.entry.frets.map { f -> f?.let { it + delta } }
        val barre = p.entry.barre?.let { Barre(it.fret + delta, it.from, it.to, it.finger) }
        return Voicing(frets, p.entry.fingers, computeBaseFret(frets), barre)
    }

    companion object {
        const val MAX_MOVABLE_BASE_FRET = 9
        private val JSON = Json { ignoreUnknownKeys = true }

        fun fromJson(json: String): VoicingLibrary =
            VoicingLibrary(JSON.decodeFromString(ShapesFile.serializer(), json).shapes)
    }
}
