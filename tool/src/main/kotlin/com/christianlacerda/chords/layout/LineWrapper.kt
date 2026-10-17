package com.christianlacerda.chords.layout

import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.Segment

data class Piece(val x: Float, val chordText: String?, val chord: ChordName?, val text: String)

data class WrappedRow(val pieces: List<Piece>, val hasChords: Boolean, val hasLyrics: Boolean)

/**
 * Pure word-wrapping for chord-over-lyric lines. Chords sit on the atom (word fragment) they were written
 * before; a word is never broken unless it is wider than a whole row.
 */
object LineWrapper {
    private class Atom(val text: String, val chordText: String?, val chord: ChordName?)

    fun wrap(
        segments: List<Segment>,
        maxWidth: Float,
        lyricWidth: (String) -> Float,
        chordWidth: (String) -> Float,
        chordGap: Float,
    ): List<WrappedRow> {
        if (segments.isEmpty()) return emptyList()

        fun atomWidth(a: Atom, trimTrailing: Boolean): Float {
            val t = if (trimTrailing) a.text.trimEnd() else a.text
            val lw = if (t.isEmpty()) 0f else lyricWidth(t)
            val cw = a.chordText?.let { chordWidth(it) + chordGap } ?: 0f
            return maxOf(lw, cw)
        }

        // 1. atoms
        val atoms = ArrayList<Atom>()
        for (seg in segments) {
            val parts = tokenize(seg.text)
            if (parts.isEmpty()) {
                if (seg.chordText != null) atoms.add(Atom("", seg.chordText, seg.chord))
            } else {
                parts.forEachIndexed { i, p ->
                    atoms.add(if (i == 0) Atom(p, seg.chordText, seg.chord) else Atom(p, null, null))
                }
            }
        }
        // 2. words
        val words = ArrayList<MutableList<Atom>>()
        for (a in atoms) {
            val prev = words.lastOrNull()?.last()
            if (prev != null && (prev.text.isEmpty() || !prev.text.last().isWhitespace())) {
                words.last().add(a)
            } else {
                words.add(mutableListOf(a))
            }
        }

        // 3. fill rows
        val rows = ArrayList<WrappedRow>()
        var cur = ArrayList<Piece>()
        var x = 0f

        fun flush() {
            if (cur.isNotEmpty()) {
                rows.add(
                    WrappedRow(
                        cur,
                        hasChords = cur.any { it.chordText != null },
                        hasLyrics = cur.any { it.text.isNotBlank() },
                    ),
                )
            }
            cur = ArrayList()
            x = 0f
        }

        fun place(a: Atom) {
            var text = a.text
            // leading whitespace is dropped at the start of a continuation row
            if (cur.isEmpty() && rows.isNotEmpty()) {
                text = text.trimStart()
                if (text.isEmpty() && a.chordText == null) return
            }
            val atom = Atom(text, a.chordText, a.chord)
            cur.add(Piece(x, atom.chordText, atom.chord, atom.text))
            x += atomWidth(atom, trimTrailing = false)
        }

        for (word in words) {
            val wordWidth = word.indices.sumOf { i ->
                atomWidth(word[i], trimTrailing = i == word.lastIndex).toDouble()
            }.toFloat()
            // widths of earlier atoms count in full; only the very last trailing space is free
            if (cur.isNotEmpty() && x + wordWidth > maxWidth) flush()
            if (x + wordWidth <= maxWidth) {
                word.forEach { place(it) }
                continue
            }
            // Over-long word: split at atom boundaries, then by characters.
            for (a in word) {
                val w = atomWidth(a, trimTrailing = true)
                if (cur.isNotEmpty() && x + w > maxWidth) flush()
                if (w <= maxWidth) {
                    place(a)
                } else {
                    splitAtom(a, maxWidth, lyricWidth, chordWidth, chordGap).forEach { frag ->
                        if (cur.isNotEmpty() && x + atomWidth(frag, true) > maxWidth) flush()
                        place(frag)
                    }
                }
            }
        }
        flush()
        return rows
    }

    private fun splitAtom(
        a: Atom,
        maxWidth: Float,
        lyricWidth: (String) -> Float,
        chordWidth: (String) -> Float,
        chordGap: Float,
    ): List<Atom> {
        val out = ArrayList<Atom>()
        var rest = a.text
        var first = true
        while (rest.isNotEmpty()) {
            var n = 1
            while (n < rest.length && lyricWidth(rest.substring(0, n + 1)) <= maxWidth) n++
            val frag = rest.substring(0, n)
            out.add(if (first) Atom(frag, a.chordText, a.chord) else Atom(frag, null, null))
            first = false
            rest = rest.substring(n)
        }
        return out
    }

    /** Leading whitespace as its own atom; then non-space runs with their trailing spaces. */
    private fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var i = 0
        if (text[0].isWhitespace()) {
            var j = 0
            while (j < text.length && text[j].isWhitespace()) j++
            out.add(text.substring(0, j))
            i = j
        }
        while (i < text.length) {
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            while (j < text.length && text[j].isWhitespace()) j++
            out.add(text.substring(i, j))
            i = j
        }
        return out
    }
}
