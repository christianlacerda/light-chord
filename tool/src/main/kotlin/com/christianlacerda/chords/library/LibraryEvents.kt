package com.christianlacerda.chords.library

import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.voicing.VoicingLibrary
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide, plain-data signals. Nothing here holds a Context or the Activity.
 * - [filesChanged]: bumped by ToolEntryPoint.onToolManagerDataUpdate(); open screens re-index when it changes.
 * - [index]: the latest published library index (null before the first load).
 * - [settingsChanged]: bumped after a song's settings are written.
 * - [notation]: the app-wide chord notation, loaded by LibraryRepository.loadNotation().
 */
object LibraryEvents {
    val filesChanged = MutableStateFlow(0L)
    val index = MutableStateFlow<LibraryIndex?>(null)

    /** Bumped after per-song settings (e.g. speed) are saved, so lists showing "Speed N" can reload them. */
    val settingsChanged = MutableStateFlow(0L)

    /** App-wide chord notation; screens collect it so a change shows straight away. */
    val notation = MutableStateFlow(ChordNotation.BRAZILIAN)

    @Volatile
    var voicingCache: VoicingLibrary? = null
}
