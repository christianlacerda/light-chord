package com.christianlacerda.chords.ui.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.ArtistSummary
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.LibraryIndexer
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.add.AddSongsScreen
import com.christianlacerda.chords.ui.artistCollator
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.songs.SongsScreen
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButtonDefaults
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
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

class ArtistsViewModel(private val repository: LibraryRepository) : LightViewModel<Unit>() {
    private val _artists = MutableStateFlow<List<ArtistSummary>?>(null)

    /** null until the first load, so the screen can show nothing instead of flashing "empty". */
    val artists: StateFlow<List<ArtistSummary>?> = _artists.asStateFlow()

    init {
        viewModelScope.launch {
            LibraryEvents.index.filterNotNull().collect { _artists.value = LibraryIndexer.groupByArtist(it, artistCollator()) }
        }
        viewModelScope.launch {
            LibraryEvents.filesChanged.drop(1).collect { repository.refreshSafely() }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch { repository.refreshSafely() }
    }
}

@InitialScreen
class ArtistsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ArtistsViewModel>(sealedActivity) {

    override val viewModelClass: Class<ArtistsViewModel>
        get() = ArtistsViewModel::class.java

    override fun createViewModel() = ArtistsViewModel(libraryRepositoryFor(lightContext))

    @Composable
    override fun Content() {
        val artists by viewModel.artists.collectAsState()
        ChordsTheme {
            ArtistsContent(
                artists = artists,
                onAdd = { navigateTo({ AddSongsScreen(it) }) },
                onOpenArtist = { a -> navigateTo({ SongsScreen(it, a.artist) }) },
            )
        }
    }
}

@Composable
private fun ArtistsContent(
    artists: List<ArtistSummary>?,
    onAdd: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
) {
    val colors = LightThemeTokens.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text(stringResource(R.string.artists_title)),
            )
            when {
                artists == null -> Unit
                artists.isEmpty() -> EmptyLibrary()
                else -> LightScrollView(
                    Modifier.weight(1f).fillMaxWidth(),
                    scrollBarPosition = LightScrollBarPosition.Inside,
                ) {
                    Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                        artists.forEach { ArtistRow(it, onOpenArtist) }
                        // keep the last row clear of the corner + button
                        Spacer(Modifier.height(ADD_BUTTON_CLEARANCE_UNITS.gridUnitsAsDp()))
                    }
                }
            }
        }
        // Add songs: bottom-right corner, 1u from the right and bottom edges, top-bar icon size.
        LightIcon(
            icon = LightIcons.ADD,
            size = LightBarButtonDefaults.ICON_SIZE_UNITS,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
                .lightClickable { onAdd() },
        )
    }
}

/** Icon (2u) + its 1u margin + a little air. */
private const val ADD_BUTTON_CLEARANCE_UNITS = 3.5f

@Composable
private fun ArtistRow(summary: ArtistSummary, onOpen: (ArtistSummary) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable { onOpen(summary) }
            .padding(vertical = 0.9f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = summary.artist ?: stringResource(R.string.unknown_artist),
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightText(
            text = summary.count.toString(),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(start = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 2f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(
            text = stringResource(R.string.library_empty_title),
            variant = LightTextVariant.Heading,
            align = TextAlign.Center,
        )
        LightText(
            text = stringResource(R.string.library_empty_body),
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
        )
    }
}
