package com.christianlacerda.chords.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.LibraryIndexer
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongEntry
import com.christianlacerda.chords.library.SongSettings
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
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

data class SongsState(val songs: List<SongEntry>, val settings: Map<String, SongSettings>)

/** [artist] null means the "Unknown artist" group. */
class SongsViewModel(
    private val repository: LibraryRepository,
    private val artist: String?,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<SongsState?>(null)
    val state: StateFlow<SongsState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            LibraryEvents.index.filterNotNull().collect { index ->
                val key = artist?.trim()?.lowercase()
                val songs = LibraryIndexer.groupByArtist(index, artistCollator())
                    .firstOrNull { it.artist?.trim()?.lowercase() == key }
                    ?.songs.orEmpty()
                _state.value = SongsState(songs, repository.allSettings())
            }
        }
        viewModelScope.launch {
            LibraryEvents.filesChanged.drop(1).collect { repository.refresh() }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch { repository.refresh() }
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
                if (s != null) {
                    LightScrollView(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                            s.songs.forEach { entry ->
                                SongRow(entry, s.settings[entry.file], onOpen = {
                                    navigateTo({ SongScreen(it, entry.file) })
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(entry: SongEntry, settings: SongSettings?, onOpen: () -> Unit) {
    val detail = songDetail(entry, settings)
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable { onOpen() }
            .padding(vertical = 0.9f.gridUnitsAsDp()),
    ) {
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
