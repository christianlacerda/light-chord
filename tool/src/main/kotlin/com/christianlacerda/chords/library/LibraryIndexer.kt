package com.christianlacerda.chords.library

import java.text.Collator

object LibraryIndexer {
    val SONG_EXTENSIONS = setOf("cho", "chopro", "chordpro", "txt")

    fun isSongFile(name: String): Boolean {
        if (name.startsWith(".") || name.startsWith("._")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in SONG_EXTENSIONS
    }

    /**
     * Incremental re-index: entries with the same name and mtime are reused (the parser is not called),
     * everything else is parsed through [parseHeader] (argument = file name). Vanished files are dropped.
     */
    fun reindex(
        old: LibraryIndex,
        files: List<FileStat>,
        bundled: Set<String>,
        now: Long,
        parseHeader: (String) -> SongEntryHeader,
    ): LibraryIndex {
        val byName = old.entries.associateBy { it.file }
        val entries = files.map { f ->
            val prev = byName[f.name]
            val isBundled = f.name in bundled
            if (prev != null && prev.modified == f.lastModified) {
                if (prev.bundled == isBundled) prev else prev.copy(bundled = isBundled)
            } else {
                val h = parseHeader(f.name)
                SongEntry(
                    file = f.name,
                    modified = f.lastModified,
                    firstSeen = prev?.firstSeen ?: now,
                    bundled = isBundled,
                    title = h.title,
                    artist = h.artist?.trim()?.takeIf { it.isNotEmpty() },
                    key = h.key,
                    capo = h.capo,
                    error = h.error,
                )
            }
        }
        return LibraryIndex(entries)
    }

    /** Case-insensitive grouping; unknown artist last; songs sorted by title. */
    fun groupByArtist(index: LibraryIndex, collator: Collator): List<ArtistSummary> {
        val groups = LinkedHashMap<String?, MutableList<SongEntry>>()
        val display = HashMap<String?, String?>()
        for (e in index.entries.sortedBy { it.file }) {
            val name = e.artist?.trim()?.takeIf { it.isNotEmpty() }
            val key = name?.lowercase()
            groups.getOrPut(key) { ArrayList() }.add(e)
            if (key !in display) display[key] = name
        }
        return groups.map { (k, songs) ->
            ArtistSummary(display[k], songs.sortedWith { a, b -> collator.compare(a.title, b.title) })
        }.sortedWith { a, b ->
            when {
                a.artist == null && b.artist == null -> 0
                a.artist == null -> 1
                b.artist == null -> -1
                else -> collator.compare(a.artist, b.artist)
            }
        }
    }
}

/** Songs the user added (not bundled) whose first sighting is at or after [startOfDayMillis]. */
fun LibraryIndex.countAddedSince(startOfDayMillis: Long): Int =
    entries.count { !it.bundled && it.firstSeen >= startOfDayMillis }
