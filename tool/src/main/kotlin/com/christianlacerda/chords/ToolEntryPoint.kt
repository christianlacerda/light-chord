package com.christianlacerda.chords

import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.SONGS_DIR as SONGS_DIRECTORY
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    const val SONGS_DIR = SONGS_DIRECTORY

    override fun getToolManagerManifest(): ClientToolManifest = ClientToolManifest(
        title = ToolText.toolName(),
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = ToolText.songsLabel(),
                    path = SONGS_DIR,
                    headerText = ToolText.songsHeader(),
                ),
            ),
        ),
    )

    /** No Context here, so only signal; open screens collect this and re-index. */
    override suspend fun onToolManagerDataUpdate() {
        super.onToolManagerDataUpdate()
        LibraryEvents.filesChanged.value = System.currentTimeMillis()
    }
}
