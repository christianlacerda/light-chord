package com.christianlacerda.chords.library

import com.christianlacerda.chords.voicing.VoicingLibrary
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide, plain-data signals. Nothing here holds a Context or the Activity.
 * - [filesChanged]: bumped by ToolEntryPoint.onToolManagerDataUpdate(); open screens re-index when it changes.
 * - [index]: the latest published library index (null before the first load).
 * - [settingsChanged]: bumped after a song's settings are written.
 */
object LibraryEvents {
    val filesChanged = MutableStateFlow(0L)
    val index = MutableStateFlow<LibraryIndex?>(null)

    /** Bumped after per-song settings (e.g. speed) are saved, so lists showing "Speed N" can reload them. */
    val settingsChanged = MutableStateFlow(0L)

    @Volatile
    var voicingCache: VoicingLibrary? = null
}
