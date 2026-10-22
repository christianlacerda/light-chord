package com.christianlacerda.chords.chordpro

import com.christianlacerda.chords.model.Barre
import com.christianlacerda.chords.model.Voicing
import com.christianlacerda.chords.voicing.computeBaseFret

/**
 * ChordPro 6 `{define: Am base-fret 1 frets x 0 2 2 1 0 fingers 0 0 2 3 1 0}`, as Chord Desk writes it.
 * Frets run low E to high e, relative to base-fret (1 = the base fret, 0 = open, x/N = muted); fingers 0 = none.
 * Returns the chord name as written and its absolute-fret voicing, or null when the line isn't a full 6-string shape.
 */
object ChordDefine {
    fun parse(arg: String): Pair<String, Voicing>? {
        val words = arg.trim().split(Regex("\\s+"))
        val name = words.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        var baseFret = 1
        var rel: List<Int?>? = null
        var fingers: List<Int>? = null
        var i = 1
        while (i < words.size) {
            when (words[i].lowercase()) {
                "base-fret" -> { baseFret = words.getOrNull(i + 1)?.toIntOrNull() ?: return null; i += 2 }
                "frets" -> {
                    rel = words.drop(i + 1).take(6).map { w -> if (w.equals("x", true) || w.equals("n", true)) null else w.toIntOrNull() ?: return null }
                    i += 7
                }
                "fingers" -> {
                    fingers = words.drop(i + 1).take(6).map { w -> w.toIntOrNull() ?: 0 }
                    i += 7
                }
                else -> i++
            }
        }
        if (baseFret < 1 || rel == null || rel.size != 6) return null
        val frets = rel.map { r -> r?.let { if (it == 0) 0 else baseFret + it - 1 } }
        val f = fingers?.takeIf { it.size == 6 }?.mapIndexed { s, n -> if ((frets[s] ?: 0) > 0) n.coerceIn(0, 4) else 0 }
            ?: List(6) { 0 }
        return name to Voicing(frets, f, computeBaseFret(frets), barreOf(frets, f))
    }

    /**
     * One finger on two or more strings at the same fret is a barre across them (the lowest such fret wins), but
     * only when every string in between is fretted at or above it; a muted or open string in the span means the
     * finger isn't lying flat, so the dots are drawn on their own.
     */
    private fun barreOf(frets: List<Int?>, fingers: List<Int>): Barre? =
        (0 until 6).filter { fingers[it] > 0 }
            .groupBy { fingers[it] to frets[it]!! }
            .filter { (key, strings) ->
                strings.size >= 2 && (strings.min()..strings.max()).all { s -> (frets[s] ?: 0) >= key.second }
            }
            .minByOrNull { it.key.second }
            ?.let { (key, strings) -> Barre(key.second, strings.min(), strings.max(), key.first) }
}
