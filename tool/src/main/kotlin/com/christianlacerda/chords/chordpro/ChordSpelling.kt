package com.christianlacerda.chords.chordpro

import com.christianlacerda.chords.model.ChordNotation

/**
 * Reads a chord suffix (the part between root and bass) piece by piece, in Brazilian (Cifra) or international
 * notation, and spells it back in either one. Same grammar as Chord Desk's src/chord.rs, so mixed spellings like
 * `m7(b5)` or `7M(9)` need no table entry.
 *
 * Brazilian (Cifra Club's "padrão de cifragem"): 7M, m7(5-), 7(4/9), 4, 2, 4+, 9, 6(9), m5-, º, 5+, 7(11+).
 * International: maj7, m7b5, 9sus4, sus4, sus2, sus#4, add9, 69, dim, dim7, aug, 7#11.
 */
object ChordSpelling {
    private enum class Triad { MAJOR, MINOR, DIM, AUG, SUS2, SUS4, POWER }
    private enum class Seventh { NONE, DOM, MAJ, DIM }

    /** An altered tone: [sign] is 'b' or '#'. */
    private data class Alt(val sign: Char, val degree: Int)

    private class Quality {
        var triad = Triad.MAJOR
        var seventh = Seventh.NONE
        var sixth = false

        /** Highest stacked extension over the seventh: 9, 11 or 13 (0 = just the 7th). */
        var ext = 0

        /** Tones added without a seventh (9, 11, 13). */
        val adds = ArrayList<Int>()
        val alts = ArrayList<Alt>()

        /** Natural tensions from parentheses or Cifra slashes, resolved at the end. */
        val tensions = ArrayList<Int>()

        /** An altered suspension: '#' for sus#4 (Cifra 4+), 'b' for susb2 (Cifra 2-). */
        var susAlt: Char? = null

        /**
         * Reading Cifra Club notation, where a bare 9, 11 or 13 is an added note, 2 is a suspended 2nd and º is the
         * diminished 7th.
         */
        var cifra = false
    }

    /**
     * Spells [suffix] in [notation], or null when it can't be read as a chord quality. The suffix is read the
     * international way (it comes from a file, which Chord Desk writes in international names).
     */
    fun spell(suffix: String, notation: ChordNotation): String? {
        val q = parse(suffix, ChordNotation.INTERNATIONAL) ?: return null
        return when (notation) {
            ChordNotation.BRAZILIAN -> brazilian(q)
            ChordNotation.INTERNATIONAL -> international(q)
        }
    }

    /** Reads a suffix written in [from] notation and spells it the international way (Chord Desk's read_suffix). */
    fun read(suffix: String, from: ChordNotation): String? = parse(suffix, from)?.let { international(it) }

    /** A tension like `9`, `b9`, `9-`, `#11`, `11+`, `4+`, as (sign, degree) with sign 'b', '#' or ' '. */
    private fun tension(item: String): Alt? {
        if (item.isEmpty()) return null
        var sign = ' '
        var rest = item
        when (item[0]) {
            'b', '#' -> { sign = item[0]; rest = item.substring(1) }
            '-' -> { sign = 'b'; rest = item.substring(1) }
            '+' -> { sign = '#'; rest = item.substring(1) }
        }
        if (rest.endsWith('+') || rest.endsWith('-')) {
            if (sign != ' ') return null
            sign = if (rest.endsWith('+')) '#' else 'b'
            rest = rest.dropLast(1)
        }
        if (rest.isEmpty() || !rest.all { it in '0'..'9' }) return null
        val n = rest.toIntOrNull() ?: return null
        if (n !in setOf(2, 4, 5, 6, 7, 9, 11, 13)) return null
        // an augmented 4th is a sharp 11th, a minor 6th a flat 13th
        return when {
            sign == '#' && n == 4 -> Alt('#', 11)
            sign == 'b' && n == 6 -> Alt('b', 13)
            sign == '#' && n == 2 -> Alt('#', 9)
            sign == 'b' && n == 2 -> Alt('b', 9)
            else -> Alt(sign, n)
        }
    }

    private fun Quality.applyTension(t: Alt): Boolean {
        if (t.sign != ' ') {
            if (t.degree in setOf(4, 6, 7)) return false
            alts.add(t)
            return true
        }
        when (t.degree) {
            7 -> seventh = Seventh.DOM
            6 -> sixth = true
            // Cifra's (4) is a suspended 4th on major chords; on minor ones it colours like an 11th
            4 -> if (triad == Triad.MINOR) tensions.add(11) else triad = Triad.SUS4
            // a 2nd with no 3rd or 4th is suspended: 7(2) = 7sus2
            2 -> if (triad == Triad.MAJOR) triad = Triad.SUS2 else tensions.add(9)
            5 -> {}
            else -> tensions.add(t.degree)
        }
        return true
    }

    /** Cifra's 4+ and 2- on a chord with no 3rd are suspensions: C4+ = Csus#4, C7(2-) = C7susb2. */
    private fun Quality.alteredSus(item: String): Boolean {
        when {
            (item == "4+" || item == "#4") && (triad == Triad.MAJOR || triad == Triad.SUS4) -> {
                triad = Triad.SUS4
                susAlt = '#'
            }
            (item == "2-" || item == "b2") && (triad == Triad.MAJOR || triad == Triad.SUS2) -> {
                triad = Triad.SUS2
                susAlt = 'b'
            }
            else -> return false
        }
        return true
    }

    private fun Quality.bareNumber(n: Int): Boolean {
        when {
            n == 7 && triad == Triad.DIM && seventh == Seventh.NONE -> seventh = Seventh.DIM
            n == 7 && seventh == Seventh.NONE -> seventh = Seventh.DOM
            n in setOf(9, 11, 13) && ext == 0 && sixth -> tensions.add(n) // 69
            // Cifra writes the dominant 9th as 7(9); a bare 9 is an added note
            n in setOf(9, 11, 13) && cifra && ext == 0 && seventh == Seventh.NONE -> tensions.add(n)
            n in setOf(9, 11, 13) && ext == 0 -> {
                if (seventh == Seventh.NONE) seventh = Seventh.DOM
                ext = n
            }
            n == 6 && !sixth && seventh == Seventh.NONE && triad != Triad.POWER -> sixth = true
            n == 5 && triad == Triad.AUG -> {}
            n == 5 && triad == Triad.MAJOR && seventh == Seventh.NONE && !sixth -> triad = Triad.POWER
            n == 4 && triad == Triad.MAJOR -> triad = Triad.SUS4
            n == 2 && cifra && triad == Triad.MAJOR -> triad = Triad.SUS2
            n == 2 && triad == Triad.MAJOR && seventh == Seventh.NONE -> tensions.add(9)
            else -> return false
        }
        return true
    }

    private fun digits(t: String): Int = t.takeWhile { it in '0'..'9' }.length

    private fun parse(written: String, from: ChordNotation): Quality? {
        val cifra = from == ChordNotation.BRAZILIAN
        // Cifra's º is the diminished 7th (the triad is m5-); elsewhere ° is the triad
        val input = if (cifra) {
            written.replace("º7", "º").replace("°7", "º").replace("º", "dim7").replace("°", "dim7")
        } else {
            written
        }
        val s = input
            .replace('♯', '#').replace('♭', 'b')
            .replace("ø7", "m7b5").replace("ø", "m7b5")
            .replace("Δ7", "maj7").replace("Δ", "maj7")
            .replace("º", "dim").replace("°", "dim")
            .replace("(maj7)", "maj7").replace("(M7)", "M7")
        val q = Quality()
        q.cifra = cifra
        var r = s

        // triad prefix
        when {
            r.startsWith("min") -> { q.triad = Triad.MINOR; r = r.substring(3) }
            r.startsWith("dim") -> { q.triad = Triad.DIM; r = r.substring(3) }
            r.startsWith("aug") -> { q.triad = Triad.AUG; r = r.substring(3) }
            r.startsWith("m") && !r.startsWith("maj") -> { q.triad = Triad.MINOR; r = r.substring(1) }
            r.startsWith("-") && !r.startsWith("-5") -> { q.triad = Triad.MINOR; r = r.substring(1) }
            r.startsWith("o") -> { q.triad = Triad.DIM; r = r.substring(1) }
            r.startsWith("+") && !(r.length > 1 && r[1] in '0'..'9' && r[1] != '5' && r[1] != '7') -> {
                q.triad = Triad.AUG
                r = r.substring(1)
            }
        }

        while (r.isNotEmpty()) {
            when {
                r.startsWith("(") -> {
                    val end = r.indexOf(')')
                    if (end < 0) return null
                    val items = r.substring(1, end).split('/', ',').map { it.trim() }.filter { it.isNotEmpty() }
                    // with a 4th in the chord, a 2nd is the 9th: 7(4/2) = 9sus4
                    val hasFourth = "4" in items
                    for (item in items) {
                        if (q.alteredSus(item)) continue
                        if (item == "2" && hasFourth) {
                            q.tensions.add(9)
                            continue
                        }
                        if (!q.applyTension(tension(item) ?: return null)) return null
                    }
                    r = r.substring(end + 1)
                }
                r.startsWith("/") -> {
                    // Cifra slash extension: 7/9, 6/9, 7/4, 7/9-
                    val t = r.substring(1)
                    val n = t.indexOfFirst { it == '/' || it == '(' }.let { if (it < 0) t.length else it }
                    if (!q.applyTension(tension(t.substring(0, n)) ?: return null)) return null
                    r = t.substring(n)
                }
                r.startsWith("maj") || r.startsWith("M") -> {
                    val t = r.substring(if (r.startsWith("maj")) 3 else 1)
                    val d = digits(t)
                    q.seventh = Seventh.MAJ
                    if (d > 0) {
                        when (val n = t.substring(0, d).toIntOrNull()) {
                            7 -> {}
                            9, 11, 13 -> q.ext = n
                            else -> return null
                        }
                    } else if (r.startsWith("M") && '7' !in input && q.triad == Triad.MAJOR) {
                        // a bare M with no 7 anywhere ("CM") is just C major
                        q.seventh = Seventh.NONE
                    }
                    r = t.substring(d)
                }
                r.startsWith("sus") -> {
                    var t = r.substring(3)
                    // sus#4 and susb2 are how Cifra's 4+ and 2- read internationally
                    var sign: Char? = null
                    if (t.length > 1 && (t[0] == '#' || t[0] == 'b') && (t[1] == '2' || t[1] == '4')) {
                        sign = t[0]
                        t = t.substring(1)
                    }
                    val d = digits(t)
                    q.triad = when (t.substring(0, d)) {
                        "", "4" -> if (sign == null || sign == '#') Triad.SUS4 else return null
                        "2" -> if (sign == null || sign == 'b') Triad.SUS2 else return null
                        else -> return null
                    }
                    q.susAlt = sign
                    r = t.substring(d)
                }
                r.startsWith("add") -> {
                    val t = r.substring(3)
                    val sign = if (t.startsWith("#") || t.startsWith("b")) 1 else 0
                    val n = listOf("13", "11", "9", "6", "4", "2").firstOrNull { t.startsWith(it, sign) }?.length
                        ?: return null
                    val (sg, deg) = tension(t.substring(0, sign + n)) ?: return null
                    when {
                        sg != ' ' -> q.alts.add(Alt(sg, deg))
                        deg == 4 -> q.tensions.add(11)
                        deg == 6 -> q.sixth = true
                        else -> q.tensions.add(if (deg == 2) 9 else deg)
                    }
                    r = t.substring(sign + n)
                }
                r.startsWith("omit") || r.startsWith("no") -> {
                    val t = r.substring(if (r.startsWith("omit")) 4 else 2)
                    val d = digits(t)
                    if (d == 0) return null
                    r = t.substring(d)
                }
                r[0] in "#b+-" -> {
                    val d = digits(r.substring(1))
                    if (!q.applyTension(tension(r.substring(0, 1 + d)) ?: return null)) return null
                    r = r.substring(1 + d)
                }
                r[0] in '0'..'9' -> {
                    val d = digits(r)
                    // a trailing + or - alters the number itself: 5-, 9+, 4+
                    if (d < r.length && (r[d] == '+' || r[d] == '-')) {
                        if (q.seventh == Seventh.NONE && q.alteredSus(r.substring(0, d + 1))) {
                            r = r.substring(d + 1)
                            continue
                        }
                        if (!q.applyTension(tension(r.substring(0, d + 1)) ?: return null)) return null
                        r = r.substring(d + 1)
                        continue
                    }
                    // "79" style runs are not a thing; read 1-2 digits as one degree
                    val take = if (d >= 2 && r.substring(0, 2) in setOf("11", "13")) 2 else 1
                    val n = r.substring(0, take).toInt()
                    // a 9 with more tones in parentheses and no 7th is an added note in either notation:
                    // D9(11) = Dadd9add11 (as Chord Desk's chord.rs)
                    if (n in setOf(9, 11, 13) && r.substring(take).startsWith("(") &&
                        q.seventh == Seventh.NONE && !q.sixth && q.ext == 0
                    ) {
                        q.tensions.add(n)
                    } else if (!q.bareNumber(n)) {
                        return null
                    }
                    r = r.substring(take)
                }
                else -> return null
            }
        }
        return resolve(q)
    }

    private fun resolve(q: Quality): Quality? {
        val tensions = q.tensions.toList()
        q.tensions.clear()
        when {
            q.seventh != Seventh.NONE -> tensions.maxOrNull()?.let { q.ext = maxOf(q.ext, it) }
            q.sixth && tensions == listOf(9) -> q.adds.add(9) // 69
            else -> q.adds.addAll(tensions)
        }
        val adds = q.adds.distinct().sorted()
        q.adds.clear(); q.adds.addAll(adds)
        val alts = q.alts.distinct().sortedWith(compareBy({ it.degree }, { it.sign }))
        q.alts.clear(); q.alts.addAll(alts)
        // altered fifths that are really a triad quality
        if (q.seventh == Seventh.NONE && !q.sixth) {
            if (q.triad == Triad.MAJOR && Alt('#', 5) in q.alts) {
                q.triad = Triad.AUG
                q.alts.remove(Alt('#', 5))
            } else if (q.triad == Triad.MINOR && Alt('b', 5) in q.alts) {
                q.triad = Triad.DIM
                q.alts.remove(Alt('b', 5))
            }
        }
        if (q.triad == Triad.POWER && (q.seventh != Seventh.NONE || q.sixth || q.alts.isNotEmpty())) return null
        return q
    }

    private fun num(q: Quality): String = if (q.ext > 0) q.ext.toString() else "7"

    private fun international(q: Quality): String {
        val alts = q.alts.toMutableList()
        val sb = StringBuilder(
            when {
                q.triad == Triad.POWER && q.adds == listOf(9) -> return "sus2" // same notes as sus2
                q.triad == Triad.POWER -> "5"
                q.triad == Triad.DIM && q.seventh == Seventh.DIM -> "dim7"
                q.triad == Triad.DIM && q.seventh == Seventh.DOM -> "m${num(q)}b5"
                q.triad == Triad.DIM -> "dim"
                q.triad == Triad.MINOR && q.seventh == Seventh.DOM && Alt('b', 5) in alts -> {
                    alts.remove(Alt('b', 5))
                    "m${num(q)}b5"
                }
                q.triad == Triad.MINOR && q.seventh == Seventh.MAJ -> "mmaj${num(q)}"
                q.triad == Triad.MINOR && q.seventh == Seventh.DOM -> "m${num(q)}"
                q.triad == Triad.MINOR -> "m"
                q.triad == Triad.AUG && q.seventh == Seventh.NONE -> "aug"
                q.triad == Triad.AUG -> {
                    alts.add(0, Alt('#', 5))
                    if (q.seventh == Seventh.MAJ) "maj${num(q)}" else num(q)
                }
                q.seventh == Seventh.MAJ -> "maj${num(q)}"
                q.seventh == Seventh.DOM || q.seventh == Seventh.DIM -> num(q)
                else -> ""
            },
        )
        var adds = q.adds.toList()
        if (q.sixth) {
            sb.append('6')
            if (adds == listOf(9)) {
                sb.append('9')
                adds = emptyList()
            }
        }
        when (q.triad) {
            Triad.SUS4 -> sb.append(if (q.susAlt != null) "sus#4" else "sus4")
            Triad.SUS2 -> sb.append(if (q.susAlt != null) "susb2" else "sus2")
            else -> {}
        }
        for (a in adds) sb.append("add").append(a)
        alts.forEachIndexed { i, a ->
            // on a triad, an altered 9th/11th/13th is an added tone: Eadd#11
            if (i == 0 && adds.isEmpty() && q.seventh == Seventh.NONE && a.degree >= 9) sb.append("add")
            sb.append(a.sign).append(a.degree)
        }
        return sb.toString()
    }

    /**
     * Cifra Club's standard (suporte.cifraclub.com.br, "padrão de cifragem"): the chord's body (m, 7, 7M, 5+, m5-, º,
     * 2, 4), then the numbers in ascending order, the first right after the name and the rest in parentheses
     * separated by "/". Alterations take + or - after the number: C7(9-), Cm7(5-), C4+.
     */
    private fun brazilian(q: Quality): String {
        fun alt(sign: Char, n: Int) = "$n" + if (sign == '#') '+' else '-'
        fun sus(n: Int) = q.susAlt?.let { alt(it, n) } ?: n.toString()
        val seventh = when (q.seventh) {
            Seventh.MAJ -> "7M"
            Seventh.DOM -> "7"
            Seventh.DIM, Seventh.NONE -> ""
        }
        // (degree, text), sorted together at the end
        val nums = ArrayList<Pair<Int, String>>()
        val body = when (q.triad) {
            Triad.POWER -> "5"
            Triad.DIM -> when (q.seventh) {
                Seventh.DIM -> "º"
                Seventh.NONE -> "m5-"
                else -> {
                    nums.add(5 to "5-")
                    "m$seventh"
                }
            }
            Triad.MINOR -> "m$seventh"
            Triad.AUG -> if (q.seventh == Seventh.NONE) "5+" else {
                nums.add(5 to "5+")
                seventh
            }
            Triad.SUS4 -> if (q.seventh == Seventh.NONE && !q.sixth) sus(4) else {
                nums.add(4 to sus(4))
                seventh
            }
            Triad.SUS2 -> if (q.seventh == Seventh.NONE && !q.sixth) sus(2) else {
                nums.add(2 to sus(2))
                seventh
            }
            Triad.MAJOR -> seventh
        }
        if (q.sixth) nums.add(6 to "6")
        if (q.ext > 0) nums.add(q.ext to q.ext.toString())
        q.adds.forEach { nums.add(it to it.toString()) }
        q.alts.forEach { nums.add(it.degree to alt(it.sign, it.degree)) }
        val rest = nums.sortedBy { it.first }.distinct().map { it.second }.toMutableList()
        val sb = StringBuilder(body)
        // C9, Cm6(9): the first number joins a body that has none
        if ((body.isEmpty() || body == "m") && rest.isNotEmpty()) sb.append(rest.removeAt(0))
        if (rest.isNotEmpty()) sb.append(rest.joinToString("/", "(", ")"))
        return sb.toString()
    }
}
