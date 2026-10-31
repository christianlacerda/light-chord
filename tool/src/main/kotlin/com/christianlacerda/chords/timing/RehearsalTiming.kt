package com.christianlacerda.chords.timing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

/**
 * Rehearsal timing contract shared with Chord Desk (docs/rehearsal-mode-plan.md): canonical timing boundaries,
 * the content fingerprint, `{meta: lightchord_timing …}` validation and cue interpolation. Port of Chord Desk's
 * `src/rehearsal.rs`; keep the two in sync. Shared fixture: `contracts/rehearsal-timing.json`.
 */
object RehearsalTiming {
    const val TIMING_KEY = "lightchord_timing"
    const val VERSION = 1L
    /** Six hours of active time. */
    const val MAX_MS = 21_600_000L
    const val MAX_CUES = 4096

    enum class Kind { MUSIC, TAB }

    /** [line] is the source line index after newline normalization. */
    data class Record(val kind: Kind, val text: String, val line: Int)

    /** One record per boundary; boundary `records.size` is terminal. [repeat]: uses `{chorus}`, unsupported in v1. */
    data class Canonical(val records: List<Record>, val sha256: String, val repeat: Boolean) {
        val terminal: Long get() = records.size.toLong()
        fun boundaryOfLine(line: Int): Int? = records.indexOfFirst { it.line == line }.takeIf { it >= 0 }
    }

    data class Cue(val ms: Long, val boundary: Long)

    data class Timing(val sha256: String, val cues: List<Cue>)

    enum class TimingError(val code: String) {
        MALFORMED("malformed"),
        UNSUPPORTED_VERSION("unsupported_version"),
        REPEAT_UNSUPPORTED("repeat_unsupported"),
        EMPTY("empty"),
        STALE("stale"),
        BAD_CUES("bad_cues"),
    }

    sealed interface Result {
        data class Ok(val timing: Timing) : Result
        data class Invalid(val error: TimingError) : Result
    }

    private val DIRECTIVE = Regex("""^[ \t]*\{[ \t]*([A-Za-z_][A-Za-z0-9_-]*)[ \t]*(?:[: \t][ \t]*(.*?))?[ \t]*\}[ \t]*$""")
    private val CHORD = Regex("""\[[^\]]*\]""")
    private val WS = Regex("[ \t]+")
    private val TAB_ON = setOf("start_of_tab", "sot")
    private val TAB_OFF = setOf(
        "start_of_verse", "sov", "start_of_chorus", "soc", "start_of_bridge", "sob",
        "end_of_verse", "eov", "end_of_chorus", "eoc", "end_of_bridge", "eob", "end_of_tab", "eot",
    )

    private fun isWs(c: Char) = c == ' ' || c == '\t'

    /** `{name}`, `{name: arg}` or `{name arg}`; the name is lowercased without its `-selector`. */
    fun directive(line: String): Pair<String, String>? {
        val m = DIRECTIVE.matchEntire(line) ?: return null
        val name = m.groupValues[1].lowercase().substringBefore('-')
        return name to (m.groups[2]?.value ?: "").trim(::isWs)
    }

    fun normalize(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    fun canonicalize(text: String): Canonical {
        val records = ArrayList<Record>()
        var inTab = false
        var repeat = false
        normalize(text).split('\n').forEachIndexed { i, line ->
            if (line.trimStart(::isWs).startsWith("#")) return@forEachIndexed
            val d = directive(line)
            if (d != null) {
                when (d.first) {
                    in TAB_ON -> inTab = true
                    in TAB_OFF -> inTab = false
                    "chorus" -> repeat = true
                }
                return@forEachIndexed
            }
            if (line.trim(::isWs).isEmpty()) return@forEachIndexed
            records += if (inTab) {
                Record(Kind.TAB, line.trimEnd(::isWs), i)
            } else {
                Record(Kind.MUSIC, line.replace(CHORD, "").split(WS).filter { it.isNotEmpty() }.joinToString(" "), i)
            }
        }
        val bytes = records.joinToString("") { (if (it.kind == Kind.TAB) "T\t" else "M\t") + it.text + "\n" }
            .toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Canonical(records, sha, repeat)
    }

    /** The JSON value of every `{meta: lightchord_timing …}` line in [text]. */
    fun timingValues(text: String): List<String> =
        normalize(text).split('\n').mapNotNull { line ->
            val (name, arg) = directive(line) ?: return@mapNotNull null
            if (name != "meta" || !arg.startsWith(TIMING_KEY)) return@mapNotNull null
            val rest = arg.removePrefix(TIMING_KEY)
            if (rest.isEmpty() || isWs(rest[0])) rest.trim(::isWs) else null
        }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    /** Validates a timing value against the song it sits in. */
    fun parse(value: String, song: Canonical): Result {
        val malformed = Result.Invalid(TimingError.MALFORMED)
        val obj = try {
            Json.parseToJsonElement(value) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return malformed
        val version = obj.long("version") ?: return malformed
        if (version != VERSION) return Result.Invalid(TimingError.UNSUPPORTED_VERSION)
        val sha = (obj["body_sha256"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return malformed
        val cues = (obj["cues"] as? JsonArray ?: return malformed).map { c ->
            val o = c as? JsonObject ?: return malformed
            Cue(o.long("ms") ?: return malformed, o.long("boundary") ?: return malformed)
        }
        return when {
            song.repeat -> Result.Invalid(TimingError.REPEAT_UNSUPPORTED)
            song.records.isEmpty() -> Result.Invalid(TimingError.EMPTY)
            sha != song.sha256 -> Result.Invalid(TimingError.STALE)
            !cuesValid(cues, song.terminal) -> Result.Invalid(TimingError.BAD_CUES)
            else -> Result.Ok(Timing(sha, cues))
        }
    }

    /** Starts at {0 ms, boundary 0}, times strictly increase, targets never go back, ends on the terminal boundary. */
    fun cuesValid(cues: List<Cue>, terminal: Long): Boolean =
        cues.size in 2..MAX_CUES &&
            cues[0] == Cue(0, 0) &&
            cues.last().boundary == terminal &&
            cues.all { it.ms in 0..MAX_MS && it.boundary in 0..terminal } &&
            cues.zipWithNext().all { (a, b) -> b.ms > a.ms && b.boundary >= a.boundary }

    /** The song's timing; null when it has no timing line. */
    fun read(text: String): Result? {
        val values = timingValues(text)
        return when (values.size) {
            0 -> null
            1 -> parse(values[0], canonicalize(text))
            else -> Result.Invalid(TimingError.MALFORMED)
        }
    }

    fun toJson(t: Timing): String =
        """{"version":$VERSION,"body_sha256":"${t.sha256}","cues":[""" +
            t.cues.joinToString(",") { """{"ms":${it.ms},"boundary":${it.boundary}}""" } + "]}"

    fun directiveLine(t: Timing): String = "{meta: $TIMING_KEY ${toJson(t)}}"

    /**
     * Position at active time [ms], interpolating linearly between cues. `positions[b]` is boundary b's resolved
     * scroll position for the current layout.
     */
    fun positionAt(ms: Long, cues: List<Cue>, positions: DoubleArray): Double {
        fun pos(c: Cue) = positions[c.boundary.toInt()]
        val first = cues.firstOrNull() ?: return 0.0
        if (ms <= first.ms) return pos(first)
        for ((a, b) in cues.zipWithNext()) {
            if (ms < b.ms) return pos(a) + (pos(b) - pos(a)) * (ms - a.ms).toDouble() / (b.ms - a.ms)
        }
        return pos(cues.last())
    }
}
