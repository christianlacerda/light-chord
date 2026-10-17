package com.christianlacerda.chords.chordpro

import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.Quality

/** Parses chord symbols, including Brazilian notation (A7M, Bm7(5-), D4, A2, Bº). */
object ChordParser {
    private val NATURALS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val BASS_REGEX = Regex("^[A-G][#b♯♭]?$")

    private val VALID_SUFFIX = Regex("^(?:maj|min|dim|aug|sus|add|omit|no|m|[0-9#b+\\-/])*$")

    private val TABLE: Map<String, Quality> = mapOf(
        "" to Quality.MAJOR,
        "m" to Quality.MINOR,
        "dim" to Quality.DIM,
        "dim7" to Quality.DIM7,
        "aug" to Quality.AUG,
        "sus2" to Quality.SUS2,
        "sus4" to Quality.SUS4,
        "sus" to Quality.SUS4,
        "4" to Quality.SUS4,
        "6" to Quality.SIX,
        "69" to Quality.SIX,
        "6/9" to Quality.SIX,
        "6add9" to Quality.SIX,
        "m6" to Quality.MIN6,
        "7" to Quality.DOM7,
        "maj7" to Quality.MAJ7,
        "m7" to Quality.MIN7,
        "mmaj7" to Quality.MIN_MAJ7,
        "m7b5" to Quality.HALF_DIM7,
        "7sus4" to Quality.SEVEN_SUS4,
        "7sus" to Quality.SEVEN_SUS4,
        "74" to Quality.SEVEN_SUS4,
        "11" to Quality.SEVEN_SUS4,
        "13" to Quality.DOM7,
        "2" to Quality.ADD9,
        "add2" to Quality.ADD9,
        "add9" to Quality.ADD9,
        "madd9" to Quality.MIN_ADD9,
        "9" to Quality.DOM9,
        "maj9" to Quality.MAJ9,
        "m9" to Quality.MIN9,
    )

    fun parse(raw: String): ChordName? {
        val original = raw.trim()
        val s = original.filterNot { it.isWhitespace() }
        if (s.isEmpty()) return null
        val letter = s[0]
        val natural = NATURALS[letter] ?: return null
        var i = 1
        var root = natural
        var flats = false
        if (i < s.length) {
            when (s[i]) {
                '#', '♯' -> { root += 1; i++ }
                'b', '♭' -> { root -= 1; i++; flats = true }
            }
        }
        root = root.mod(12)
        var rest = s.substring(i)
        var bass: Int? = null
        val slash = rest.lastIndexOf('/')
        if (slash >= 0) {
            val b = rest.substring(slash + 1)
            if (BASS_REGEX.matches(b)) {
                var pc = NATURALS.getValue(b[0])
                if (b.length > 1) {
                    if (b[1] == '#' || b[1] == '♯') pc += 1 else { pc -= 1; flats = true }
                }
                bass = pc.mod(12)
                rest = rest.substring(0, slash)
            }
        }
        val rawSuffix = rest
        val normalized = normalizeSuffix(rest) ?: return null
        if (!VALID_SUFFIX.matches(normalized)) return null
        val quality = classify(normalized)
        return ChordName(root, quality, bass, original, flats, normalized, rawSuffix)
    }

    private fun classify(n: String): Quality {
        TABLE[n]?.let { return it }
        if (n.startsWith("aug")) return Quality.AUG
        if (Regex("^7[#b+]?\\d*$").matches(n) || Regex("^7[#b]\\d+.*$").matches(n)) return Quality.DOM7
        if (Regex("^13.*$").matches(n)) return Quality.DOM7
        return Quality.OTHER
    }

    /** Returns the normalised suffix, or null for text that cannot be a chord suffix at all. */
    internal fun normalizeSuffix(input: String): String? {
        var t = input
        if (t.any { it == ',' || it == ';' || it == '*' || it == '!' || it == '?' }) return null
        t = t.replace("ø7", "m7b5").replace("ø", "m7b5")
            .replace("Δ7", "maj7").replace("Δ", "maj7")
            .replace("º", "dim").replace("°", "dim")
        if (t.startsWith("o")) t = "dim" + t.substring(1)
        // m + major seventh spellings
        t = Regex("^m(?:7M|M7|M|\\(maj7?\\)|\\(M7?\\)|maj7?)").replace(t) { "mmaj7" }
        t = t.replace(Regex("(?i)maj"), "maj").replace(Regex("(?i)min"), "m")
        t = t.replace("7M9", "maj9").replace("M9", "maj9")
            .replace("7M", "maj7").replace("M7", "maj7")
        t = t.replace('M', '\u0000').let { s0 ->
            // remaining standalone M means major seventh
            s0.replace("\u0000", "maj7")
        }
        t = t.lowercase()
        t = t.replace("maj7maj7", "maj7")
        if (t == "maj") t = "maj7"
        if (t.startsWith("-5")) t = "b5" + t.substring(2) else if (t.startsWith("-")) t = "m" + t.substring(1)
        if (t.startsWith("+")) t = "aug" + t.substring(1)
        t = t.replace("5-", "b5").replace("5+", "#5").replace("9-", "b9").replace("9+", "#9")
        if (t.endsWith("+")) t = t.dropLast(1) + "#5"
        // parenthesised extensions
        t = Regex("^(m?)\\(9\\)$").replace(t) { it.groupValues[1] + "add9" }
        t = t.replace("maj7(9)", "maj9").replace("m7(9)", "m9").replace("7(9)", "9")
        t = t.replace("7(4)", "7sus4")
        t = t.replace("(", "").replace(")", "")
        if (t == "m7maj7") t = "mmaj7"
        return t
    }
}
