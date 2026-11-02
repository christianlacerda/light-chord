package com.christianlacerda.chords.library

import com.christianlacerda.chords.layout.ScrollSpeed
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

/**
 * Per-song settings, written only from the song's settings screen. The song opens at [speedTick]; when absent
 * (data saved before half steps) at legacy level [speed]. [speed] is still written, rounded down, for older
 * versions. [lastProgress] is kept for compatibility with existing saved data but is no longer written.
 */
@Serializable
data class SongSettings(
    val speed: Int = 4,
    val transpose: Int = 0,
    val lastProgress: Float = 0f,
    val showTabs: Boolean = true,
    val speedTick: Int? = null,
    /** Scroll along the song's rehearsed timing when it has valid timing; off uses the speed. */
    val useTiming: Boolean = true,
) {
    val tick: Int get() = speedTick?.let(ScrollSpeed::clamp) ?: ScrollSpeed.tickOf(speed)

    fun withTick(tick: Int): SongSettings =
        ScrollSpeed.clamp(tick).let { copy(speedTick = it, speed = ScrollSpeed.legacyLevel(it)) }
}

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
