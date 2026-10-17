package com.christianlacerda.chords.library

import com.christianlacerda.chords.voicing.VoicingLibrary
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide, plain-data signals. Nothing here holds a Context or the Activity.
 * - [filesChanged]: bumped by ToolEntryPoint.onToolManagerDataUpdate(); open screens re-index when it changes.
 * - [index]: the latest published library index (null before the first load).
 */
object LibraryEvents {
    val filesChanged = MutableStateFlow(0L)
    val index = MutableStateFlow<LibraryIndex?>(null)

    @Volatile
    var voicingCache: VoicingLibrary? = null
}
