package com.christianlacerda.chords.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongEntry
import com.christianlacerda.chords.library.SongSettings
import com.christianlacerda.chords.library.songsForArtist
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.artistCollator
import com.christianlacerda.chords.ui.errorText
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.song.SongScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SongsState(val songs: List<SongEntry>, val settings: Map<String, SongSettings>)

/** [artist] null means the "Unknown artist" group. */
class SongsViewModel(
    private val repository: LibraryRepository,
    private val artist: String?,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<SongsState?>(null)
    val state: StateFlow<SongsState?> = _state.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun toggleEdit() {
        _editMode.value = !_editMode.value
    }

    /** Removes the file from the phone, then re-indexes (the screen goes back if the artist is now empty). */
    fun delete(entry: SongEntry) {
        viewModelScope.launch {
            try {
                repository.delete(entry.file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // file vanished or path rejected: the refresh below shows the truth
            }
            repository.refreshSafely()
        }
    }

    init {
        viewModelScope.launch {
            LibraryEvents.index.filterNotNull().collect { index ->
                val songs = songsForArtist(index, artist, artistCollator())
                val settings = repository.allSettings()
                _state.value = SongsState(songs, settings)
            }
        }
        viewModelScope.launch {
            LibraryEvents.filesChanged.drop(1).collect { repository.refreshSafely() }
        }
        // A song's speed is saved when leaving it; show the new "Speed N" as soon as the write lands.
        viewModelScope.launch {
            LibraryEvents.settingsChanged.drop(1).collect { reloadSettings() }
        }
    }

    private suspend fun reloadSettings() {
        val settings = repository.allSettings()
        // Atomic on the latest state: the old read-then-write could put back a song list from before a
        // delete that landed while the settings were loading (the "deleted song reappears" bug).
        _state.update { it?.copy(settings = settings) }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch {
            repository.refreshSafely()
            reloadSettings() // an unchanged index doesn't re-emit, so settings are re-read explicitly
        }
    }
}

class SongsScreen(sealedActivity: SealedLightActivity, private val artist: String?) :
    LightScreen<Unit, SongsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongsViewModel>
        get() = SongsViewModel::class.java

    override fun createViewModel() = SongsViewModel(libraryRepositoryFor(lightContext), artist)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val editMode by viewModel.editMode.collectAsState()
        val title = artist ?: stringResource(R.string.unknown_artist)
        // The artist's last song was removed (e.g. from the computer): nothing left to show.
        LaunchedEffect(state?.songs?.isEmpty()) {
            if (state?.songs?.isEmpty() == true) goBack()
        }
        ChordsTheme {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(title),
                )
                val s = state
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (s != null) {
                        LightScrollView(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                                s.songs.forEach { entry ->
                                    SongRow(
                                        entry = entry,
                                        settings = s.settings[entry.file],
                                        editMode = editMode,
                                        onOpen = { navigateTo({ SongScreen(it, entry.file) }) },
                                        onDelete = {
                                            navigateTo(screenFactory = { DeleteSongScreen(it, entry.title) }) { confirmed ->
                                                if (confirmed) viewModel.delete(entry)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                // The SDK's bar, as on Artists: pencil bottom-right, DONE in the centre while editing.
                if (!s?.songs.isNullOrEmpty()) {
                    LightBottomBar(
                        items = if (editMode) {
                            listOf(null, LightBarButton.Text(stringResource(R.string.done).uppercase(), onClick = { viewModel.toggleEdit() }), null)
                        } else {
                            listOf(
                                null,
                                null,
                                LightBarButton.LightIcon(LightIcons.PENCIL, onClick = { viewModel.toggleEdit() }, contentDescription = stringResource(R.string.edit)),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    entry: SongEntry,
    settings: SongSettings?,
    editMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val detail = songDetail(entry, settings)
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(enabled = !editMode) { onOpen() }
            .padding(vertical = 0.9f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LightText(
                text = entry.title,
                variant = LightTextVariant.Heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                LightText(
                    text = detail,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (editMode) {
            LightIcon(
                icon = LightIcons.TRASH,
                modifier = Modifier.padding(horizontal = 0.8f.gridUnitsAsDp()).lightClickable { onDelete() },
            )
        }
    }
}

/** "Key G · capo 2 · Speed 4", or the error text for a broken file. */
@Composable
private fun songDetail(entry: SongEntry, settings: SongSettings?): String {
    errorText(entry.error)?.let { return it }
    val parts = ArrayList<String>()
    entry.key?.takeIf { it.isNotBlank() }?.let { parts.add(stringResource(R.string.detail_key, it)) }
    entry.capo?.takeIf { it > 0 }?.let { parts.add(stringResource(R.string.detail_capo, it)) }
    settings?.let { parts.add(stringResource(R.string.detail_speed, it.speed)) }
    return parts.joinToString(" · ")
}
