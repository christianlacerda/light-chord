package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongSettings
import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SongSettingsState(
    val title: String,
    val key: String?,
    val capo: Int?,
    val hasTabs: Boolean,
    val settings: SongSettings,
    /** App-wide, not per song. */
    val notation: ChordNotation,
)

class SongSettingsViewModel(
    private val repository: LibraryRepository,
    private val fileName: String,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow<SongSettingsState?>(null)
    val state: StateFlow<SongSettingsState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.settings(fileName)
            val song = repository.readSong(fileName)?.song
            _state.value = SongSettingsState(
                title = song?.meta?.title ?: fileName.substringBeforeLast('.'),
                key = song?.meta?.key?.takeIf { it.isNotBlank() },
                capo = song?.meta?.capo?.takeIf { it > 0 },
                hasTabs = song?.sections?.any { it.kind == SectionKind.TAB } == true,
                settings = settings,
                notation = repository.loadNotation(),
            )
        }
    }

    /** The song's default speed: what it opens with. */
    fun changeSpeed(delta: Int) {
        val s = _state.value?.settings ?: return
        val next = ScrollSpeed.clamp(s.speed + delta)
        if (next != s.speed) update(s.copy(speed = next))
    }

    fun toggleTabs() {
        val s = _state.value?.settings ?: return
        update(s.copy(showTabs = !s.showTabs))
    }

    /** Switches every song between Brazilian and international chord names. */
    fun toggleNotation() {
        val s = _state.value ?: return
        val next = if (s.notation == ChordNotation.BRAZILIAN) ChordNotation.INTERNATIONAL else ChordNotation.BRAZILIAN
        _state.value = s.copy(notation = next)
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repository.saveNotation(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // keep the on-screen value; the next change tries again
                }
            }
        }
    }

    private fun update(next: SongSettings) {
        _state.value = _state.value?.copy(settings = next)
        viewModelScope.launch {
            // NonCancellable: a tap followed straight away by Back must still be saved.
            withContext(NonCancellable) {
                try {
                    repository.saveSettings(fileName, next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // keep the on-screen value; the next change tries again
                }
            }
        }
    }
}

/** Per-song settings (default speed, tab visibility), the app-wide chord notation, and read-only song info. */
class SongSettingsScreen(sealedActivity: SealedLightActivity, private val fileName: String) :
    LightScreen<Unit, SongSettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongSettingsViewModel>
        get() = SongSettingsViewModel::class.java

    override fun createViewModel() = SongSettingsViewModel(libraryRepositoryFor(lightContext), fileName)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        ChordsTheme {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(state?.title ?: fileName.substringBeforeLast('.')),
                )
                val s = state ?: return@Column
                LightScrollView(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                        SettingRow(stringResource(R.string.settings_speed)) {
                            Stepper(
                                value = s.settings.speed,
                                canDecrease = s.settings.speed > ScrollSpeed.MIN,
                                canIncrease = s.settings.speed < ScrollSpeed.MAX,
                                onDecrease = { viewModel.changeSpeed(-1) },
                                onIncrease = { viewModel.changeSpeed(1) },
                            )
                        }
                        LightText(
                            text = stringResource(R.string.settings_speed_hint),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        if (s.hasTabs) {
                            Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                            SettingRow(
                                label = stringResource(R.string.settings_show_tabs),
                                onClick = { viewModel.toggleTabs() },
                            ) {
                                LightIcon(
                                    icon = if (s.settings.showTabs) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                                )
                            }
                        }
                        Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                        SettingRow(
                            label = stringResource(R.string.settings_chord_names),
                            onClick = { viewModel.toggleNotation() },
                        ) {
                            LightText(
                                text = stringResource(
                                    if (s.notation == ChordNotation.BRAZILIAN) R.string.notation_brazilian
                                    else R.string.notation_international,
                                ),
                                variant = LightTextVariant.Copy,
                            )
                        }
                        LightText(
                            text = stringResource(
                                if (s.notation == ChordNotation.BRAZILIAN) R.string.notation_brazilian_hint
                                else R.string.notation_international_hint,
                            ),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        Spacer(Modifier.height(1.2f.gridUnitsAsDp()))
                        s.key?.let {
                            LightText(stringResource(R.string.detail_key, it), LightTextVariant.Detail)
                        }
                        s.capo?.let {
                            LightText(stringResource(R.string.info_capo, it), LightTextVariant.Detail)
                        }
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: (() -> Unit)? = null, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> if (onClick != null) m.lightClickable { onClick() } else m }
            .padding(end = 1f.gridUnitsAsDp(), top = 0.6f.gridUnitsAsDp(), bottom = 0.6f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(label, LightTextVariant.Copy, modifier = Modifier.weight(1f))
        control()
    }
}

/** − N + with large tap targets; an arrow at its limit dims to 0.3. */
@Composable
private fun Stepper(
    value: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", canDecrease, onDecrease)
        LightText(
            text = value.toString(),
            variant = LightTextVariant.Heading,
            align = TextAlign.Center,
            modifier = Modifier.width(2.4f.gridUnitsAsDp()),
        )
        StepButton("+", canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .graphicsLayer(alpha = if (enabled) 1f else 0.3f)
            .lightClickable(enabled = enabled) { onClick() }
            .padding(horizontal = 0.6f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(symbol, LightTextVariant.Heading)
    }
}
