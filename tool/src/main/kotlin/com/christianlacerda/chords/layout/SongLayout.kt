package com.christianlacerda.chords.layout

import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.model.displayName

sealed interface RenderItem {
    /** [text] null means "use the localized name of [kind]" (Verse, Chorus, ...). */
    data class Label(val text: String?, val kind: SectionKind) : RenderItem
    data class Music(val rows: List<WrappedRow>) : RenderItem
    data class Tab(val text: String) : RenderItem
    data object Gap : RenderItem
}

/** Text measurement callbacks (pixels), supplied by the UI. */
class Measurers(
    val lyricWidth: (String) -> Float,
    val chordWidth: (String) -> Float,
    val chordGap: Float,
)

/**
 * [showTabs] false leaves TAB sections (and their labels) out entirely. Parsed chords are shown in [notation];
 * annotations and unreadable chords as written.
 */
fun buildRenderItems(
    song: Song,
    maxWidth: Float,
    measurers: Measurers,
    showTabs: Boolean = true,
    notation: ChordNotation = ChordNotation.BRAZILIAN,
): List<RenderItem> {
    val items = ArrayList<RenderItem>()
    // Hiding tabs must never leave a tab-only song blank: then the tabs are shown anyway.
    val sections = if (showTabs) song.sections
    else song.sections.filter { it.kind != SectionKind.TAB }.ifEmpty { song.sections }
    sections.forEachIndexed { idx, section ->
        if (idx > 0) items.add(RenderItem.Gap)
        val showLabel = section.label != null || section.kind == SectionKind.VERSE ||
            section.kind == SectionKind.CHORUS || section.kind == SectionKind.BRIDGE ||
            section.kind == SectionKind.TAB
        if (showLabel) items.add(RenderItem.Label(section.label, section.kind))
        if (section.kind == SectionKind.TAB) {
            items.add(RenderItem.Tab(section.lines.joinToString("\n") { l -> l.segments.joinToString("") { it.text } }))
            return@forEachIndexed
        }
        for (line in section.lines) {
            if (line.segments.isEmpty()) {
                items.add(RenderItem.Gap)
            } else {
                val segments = line.segments.map { seg ->
                    seg.chord?.let { seg.copy(chordText = it.displayName(notation)) } ?: seg
                }
                val rows = LineWrapper.wrap(
                    segments, maxWidth, measurers.lyricWidth, measurers.chordWidth, measurers.chordGap,
                )
                if (rows.isNotEmpty()) items.add(RenderItem.Music(rows))
            }
        }
    }
    return items
}
