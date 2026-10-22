package com.christianlacerda.chords.ui.chords

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.model.displayName
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.KeepScreenOn
import com.christianlacerda.chords.ui.WheelInput
import com.christianlacerda.chords.ui.wheelInput
import com.christianlacerda.chords.voicing.ChordShapes
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PER_PAGE = 4

/** Space under each row of the grid, between a diagram and the next row's chord names. */
private const val ROW_GAP_UNITS = 1f

class SongChordsViewModel(private val chordCount: Int) : LightViewModel<Unit>() {
    val pageCount: Int get() = ((chordCount + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    fun step(delta: Int) {
        _page.value = (_page.value + delta).coerceIn(0, pageCount - 1)
    }

    val wheel = WheelInput(onStep = { step(it) })

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = wheel.onKey(keyCode, WheelInput.ACTION_DOWN, 0)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean =
        wheel.onKey(keyCode, WheelInput.ACTION_DOWN, repeatCount)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = wheel.onKey(keyCode, WheelInput.ACTION_UP, 0)
}

/** Every distinct chord of the song, in order of first use, 4 per page in its easiest (first) shape. */
class SongChordsScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val key: String?,
    private val chords: List<ChordShapes>,
    /** The song's capo (> 0) if any; drawn as a CAPO bar on each diagram. */
    private val capo: Int? = null,
) : LightScreen<Unit, SongChordsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongChordsViewModel>
        get() = SongChordsViewModel::class.java

    override fun createViewModel() = SongChordsViewModel(chords.size)

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Content() {
        val page by viewModel.page.collectAsState()
        ChordsTheme {
            val colors = LightThemeTokens.colors
            Box(Modifier.fillMaxSize().background(colors.background).wheelInput(viewModel.wheel)) {
            KeepScreenOn()
            Column(Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(stringResource(R.string.chords_in_song)),
                )
                val subtitle = buildList {
                    add(title)
                    key?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.detail_key, it)) }
                    add(pluralStringResource(R.plurals.chord_count, chords.size, chords.size))
                }.joinToString(" · ")
                LightText(
                    text = subtitle,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 1.5f.gridUnitsAsDp()),
                )
                val pageItems = chords.drop(page * PER_PAGE).take(PER_PAGE)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (rowItems in pageItems.chunked(2)) {
                        // a clear gap under each row, so the next row's names read as belonging to the diagrams below
                        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = ROW_GAP_UNITS.gridUnitsAsDp())) {
                            for (shapes in rowItems) {
                                ChordCell(shapes, capo, Modifier.weight(1f)) {
                                    navigateTo({ ChordDiagramScreen(it, title, shapes, capo) })
                                }
                            }
                            if (rowItems.size == 1) Column(Modifier.weight(1f)) {}
                        }
                    }
                    // keep the grid 2 rows tall so a short last page doesn't stretch its cells
                    if (pageItems.size <= 2) Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = ROW_GAP_UNITS.gridUnitsAsDp())) {}
                }
                ArrowBar(
                    canPrev = page > 0,
                    canNext = page < viewModel.pageCount - 1,
                    onPrev = { viewModel.step(-1) },
                    onNext = { viewModel.step(1) },
                    label = stringResource(R.string.page_of, page + 1, viewModel.pageCount),
                )
            }
            }
        }
    }
}

@Composable
private fun ChordCell(shapes: ChordShapes, capo: Int?, modifier: Modifier, onClick: () -> Unit) {
    val colors = LightThemeTokens.colors
    val notation by LibraryEvents.notation.collectAsState()
    Column(
        modifier = modifier.fillMaxSize().lightClickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = shapes.chord.displayName(notation),
            style = LightThemeTokens.typography.copy.copy(
                fontSize = 30f.designVerticalPxToSp(),
                lineHeight = 34f.designVerticalPxToSp(),
                fontWeight = FontWeight.Bold,
                color = colors.content,
            ),
        )
        val voicing = shapes.voicings.firstOrNull()
        if (voicing != null) {
            ChordDiagram(voicing, Modifier.weight(1f).fillMaxWidth(), compact = true, capo = capo)
        }
    }
}
