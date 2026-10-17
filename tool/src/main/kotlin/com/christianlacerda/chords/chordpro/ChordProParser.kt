package com.christianlacerda.chords.chordpro

import com.christianlacerda.chords.model.Line
import com.christianlacerda.chords.model.Section
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.model.Segment
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.model.SongMeta

enum class ParseIssue { NOT_CHORDPRO, EMPTY }

data class ParseResult(val song: Song, val issue: ParseIssue?)

object ChordProParser {
    private val DIRECTIVE = Regex("""^\s*\{\s*([A-Za-z_]+)\s*(?:[:\s]\s*(.*?))?\s*\}\s*$""")
    private val CHORD = Regex("""\[([^\]]*)\]""")

    private class Builder(var label: String?, val kind: SectionKind, val keepEmpty: Boolean = false) {
        val lines = ArrayList<Line>()
    }

    fun parse(text: String, fileName: String): ParseResult {
        val cleaned = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        var title: String? = null
        var subtitle: String? = null
        var artist: String? = null
        var key: String? = null
        var capo: Int? = null

        val sections = ArrayList<Section>()
        var current = Builder(null, SectionKind.PLAIN)
        var inEnv = false
        var inTab = false
        var sawDirective = false
        var sawChord = false

        fun finish() {
            val ls = current.lines
            while (ls.isNotEmpty() && ls.last().segments.isEmpty()) ls.removeAt(ls.size - 1)
            while (ls.isNotEmpty() && ls.first().segments.isEmpty()) ls.removeAt(0)
            if (ls.isNotEmpty() || current.keepEmpty) {
                sections.add(Section(current.label, current.kind, ls.toList()))
            }
        }

        fun open(label: String?, kind: SectionKind, keepEmpty: Boolean = false) {
            finish()
            current = Builder(label, kind, keepEmpty)
        }

        for (line in cleaned.split('\n')) {
            val trimmedStart = line.trimStart()
            if (!inTab && trimmedStart.startsWith("#")) continue
            if (inTab && trimmedStart.startsWith("#")) continue
            val m = DIRECTIVE.matchEntire(line)
            if (m != null) {
                sawDirective = true
                val name = m.groupValues[1].lowercase()
                val arg = m.groups[2]?.value?.trim()?.takeIf { it.isNotEmpty() }
                when (name) {
                    "title", "t" -> title = arg
                    "subtitle", "st" -> subtitle = arg
                    "artist" -> artist = arg
                    "key" -> key = arg
                    "capo" -> capo = arg?.toIntOrNull()
                    "meta" -> {
                        val parts = arg?.split(Regex("\\s+"), limit = 2)
                        if (parts != null && parts.size == 2) {
                            val v = parts[1].trim().takeIf { it.isNotEmpty() }
                            when (parts[0].lowercase()) {
                                "title" -> title = v
                                "artist" -> artist = v
                                "key" -> key = v
                                "capo" -> capo = v?.toIntOrNull()
                                "subtitle" -> subtitle = v
                            }
                        }
                    }
                    "comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight" -> {
                        if (current.lines.isEmpty()) {
                            current.label = arg ?: current.label
                        } else {
                            val kind = if (inEnv) current.kind else SectionKind.PLAIN
                            open(arg, kind)
                        }
                    }
                    "start_of_verse", "sov" -> { open(arg, SectionKind.VERSE); inEnv = true; inTab = false }
                    "start_of_chorus", "soc" -> { open(arg, SectionKind.CHORUS); inEnv = true; inTab = false }
                    "start_of_bridge", "sob" -> { open(arg, SectionKind.BRIDGE); inEnv = true; inTab = false }
                    "start_of_tab", "sot" -> { open(arg, SectionKind.TAB); inEnv = true; inTab = true }
                    "end_of_verse", "eov", "end_of_chorus", "eoc", "end_of_bridge", "eob",
                    "end_of_tab", "eot" -> { open(null, SectionKind.PLAIN); inEnv = false; inTab = false }
                    "chorus" -> {
                        val last = sections.lastOrNull { it.kind == SectionKind.CHORUS }
                        finish()
                        if (last != null) {
                            sections.add(if (arg != null) last.copy(label = arg) else last)
                        } else {
                            sections.add(Section(arg, SectionKind.CHORUS, emptyList()))
                        }
                        current = Builder(null, SectionKind.PLAIN)
                        inEnv = false
                    }
                    else -> Unit // unknown directives are ignored
                }
                continue
            }

            if (inTab) {
                current.lines.add(Line(listOf(Segment(null, null, line.trimEnd()))))
                continue
            }

            val body = line.trimEnd()
            if (body.isBlank()) {
                if (current.lines.isNotEmpty() && current.lines.last().segments.isNotEmpty()) {
                    current.lines.add(Line(emptyList()))
                }
                continue
            }
            val segs = parseLyricLine(body)
            if (segs.any { it.chordText != null }) sawChord = true
            current.lines.add(Line(segs))
        }
        finish()

        val fallbackTitle = fileName.substringBeforeLast('.', fileName)
        val song = Song(
            SongMeta(title ?: fallbackTitle, artist, key, capo, subtitle),
            sections,
        )
        val issue = when {
            sections.isEmpty() -> ParseIssue.EMPTY
            !sawDirective && !sawChord -> ParseIssue.NOT_CHORDPRO
            else -> null
        }
        return ParseResult(song, issue)
    }

    private fun parseLyricLine(line: String): List<Segment> {
        val out = ArrayList<Segment>()
        var pos = 0
        var pendingChordText: String? = null
        var pendingChord: com.christianlacerda.chords.model.ChordName? = null
        var havePending = false
        for (m in CHORD.findAll(line)) {
            val before = line.substring(pos, m.range.first)
            if (havePending) {
                out.add(Segment(pendingChordText, pendingChord, before))
            } else if (before.isNotEmpty()) {
                out.add(Segment(null, null, before))
            }
            val inner = m.groupValues[1].trim()
            if (inner.startsWith("*")) {
                pendingChordText = inner.removePrefix("*").trim()
                pendingChord = null
            } else {
                pendingChordText = inner
                pendingChord = ChordParser.parse(inner)
            }
            havePending = true
            pos = m.range.last + 1
        }
        val tail = line.substring(pos)
        if (havePending) out.add(Segment(pendingChordText, pendingChord, tail))
        else if (tail.isNotEmpty()) out.add(Segment(null, null, tail))
        return out
    }
}
