package com.christianlacerda.chords.library

import kotlinx.serialization.Serializable

/** Error codes stored in [SongEntry.error]. */
object SongError {
    const val NOT_CHORDPRO = "not_chordpro"
    const val EMPTY = "empty"
    const val TOO_LARGE = "too_large"
    const val UNREADABLE = "unreadable"
}

@Serializable
data class SongEntry(
    val file: String,
    val modified: Long,
    val firstSeen: Long,
    val bundled: Boolean = false,
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val capo: Int? = null,
    val error: String? = null,
)

/** Bump whenever the ChordPro/chord parsing changes so stored headers are rebuilt on the next refresh. */
const val PARSER_VERSION = 2

@Serializable
data class LibraryIndex(
    val entries: List<SongEntry> = emptyList(),
    /** The [PARSER_VERSION] that produced [entries]; 0 for indexes written before it existed. */
    val parserVersion: Int = 0,
)

@Serializable
data class SongSettings(
    val speed: Int = 4,
    val transpose: Int = 0,
    val lastProgress: Float = 0f,
)

/** [artist] null = unknown artist. */
data class ArtistSummary(val artist: String?, val songs: List<SongEntry>) {
    val count: Int get() = songs.size
}

data class FileStat(val name: String, val lastModified: Long)

/** What the indexer needs from a parsed file. */
data class SongEntryHeader(
    val title: String,
    val artist: String?,
    val key: String?,
    val capo: Int?,
    val error: String?,
)

/** Directory (under the shared files root) where Tool Manager uploads land. */
const val SONGS_DIR = "songs"
