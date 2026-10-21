package com.christianlacerda.chords.ui.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.christianlacerda.chords.ui.artistCollator
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.receive.ReceiveScreen
import com.christianlacerda.chords.ui.receive.ScanChordDeskScreen
import com.christianlacerda.chords.ui.songs.DeleteArtistScreen
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
import kotlinx.coroutines.CancellationException
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

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun toggleEdit() {
        _editMode.value = !_editMode.value
    }

    /** Removes every song file of the artist, then re-indexes (settings of removed songs are pruned there). */
    fun delete(summary: ArtistSummary) {
        viewModelScope.launch {
            try {
                repository.deleteAll(summary.songs.map { it.file })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the refresh below shows what is left
            }
            repository.refreshSafely()
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
        val editMode by viewModel.editMode.collectAsState()
        ChordsTheme {
            ArtistsContent(
                artists = artists,
                editMode = editMode,
                onToggleEdit = { viewModel.toggleEdit() },
                onAdd = {
                    // + goes straight to the scanner; it pops itself with the text, then Receive is pushed
                    // (see LightQrCodeScanner KDoc). Done on Receive lands back here.
                    navigateTo(screenFactory = { ScanChordDeskScreen(it) }) { text ->
                        navigateTo(screenFactory = { ReceiveScreen(it, text) })
                    }
                },
                onOpenArtist = { a -> navigateTo({ SongsScreen(it, a.artist) }) },
                onDeleteArtist = { a, name ->
                    navigateTo(screenFactory = { DeleteArtistScreen(it, name, a.count) }) { confirmed ->
                        if (confirmed) viewModel.delete(a)
                    }
                },
            )
        }
    }
}

@Composable
private fun ArtistsContent(
    artists: List<ArtistSummary>?,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    onAdd: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onDeleteArtist: (ArtistSummary, String) -> Unit,
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
                // Outside: the scrollbar gets its own 2u gutter, so it never draws over the counts. The bottom
                // padding ends the list (and the scrollbar track) above the corner + button.
                else -> LightScrollView(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = ADD_BUTTON_CLEARANCE_UNITS.gridUnitsAsDp()),
                    scrollBarPosition = LightScrollBarPosition.Outside,
                ) {
                    Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                        artists.forEach { ArtistRow(it, editMode, onOpenArtist, onDeleteArtist) }
                    }
                }
            }
        }
        // Add songs: an ADD text button centred on the bottom bar; hidden while editing.
        if (!editMode) {
            LightText(
                text = stringResource(R.string.add).uppercase(),
                variant = LightTextVariant.Heading,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1f.gridUnitsAsDp())
                    .lightClickable { onAdd() },
            )
        }
        // Edit: bottom-right corner; DONE while editing, like the song list. Only offered when there is something to delete.
        if (!artists.isNullOrEmpty()) {
            val corner = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
                .lightClickable { onToggleEdit() }
            if (editMode) {
                LightText(stringResource(R.string.done).uppercase(), LightTextVariant.Heading, modifier = corner)
            } else {
                LightIcon(
                    icon = LightIcons.PENCIL,
                    size = LightBarButtonDefaults.ICON_SIZE_UNITS,
                    contentDescription = stringResource(R.string.edit),
                    modifier = corner,
                )
            }
        }
    }
}

/** Bottom bar (2u) + its 1u margin + 2u of air between the list's end and the bar. */
private const val ADD_BUTTON_CLEARANCE_UNITS = 5f

@Composable
private fun ArtistRow(
    summary: ArtistSummary,
    editMode: Boolean,
    onOpen: (ArtistSummary) -> Unit,
    onDelete: (ArtistSummary, String) -> Unit,
) {
    val name = summary.artist ?: stringResource(R.string.unknown_artist)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = !editMode) { onOpen(summary) }
            .padding(vertical = 0.9f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = name,
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightText(
            text = summary.count.toString(),
            variant = LightTextVariant.Detail,
            lighten = true,
            // the 2u scrollbar gutter already sits to the right, so counts end 2u from the edge
            modifier = Modifier.padding(start = 1f.gridUnitsAsDp()),
        )
        if (editMode) {
            LightIcon(
                icon = LightIcons.TRASH,
                modifier = Modifier.padding(horizontal = 0.8f.gridUnitsAsDp()).lightClickable { onDelete(summary, name) },
            )
        }
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
