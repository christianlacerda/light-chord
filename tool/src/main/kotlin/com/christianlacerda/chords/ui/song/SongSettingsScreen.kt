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
import com.christianlacerda.chords.timing.RehearsalTiming
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongSettings
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.speedLabel
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
    /** null: no rehearsed timing; false: present but unusable (stale or invalid). */
    val timingValid: Boolean? = null,
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
                timingValid = song?.timing?.let { it is RehearsalTiming.Result.Ok },
            )
        }
    }

    /** The song's default speed: what it opens with. */
    fun changeSpeed(delta: Int) {
        val s = _state.value?.settings ?: return
        val next = ScrollSpeed.clamp(s.tick + delta)
        if (next != s.tick || s.speedTick == null) update(s.withTick(next))
    }

    fun toggleTiming() {
        val s = _state.value?.settings ?: return
        update(s.copy(useTiming = !s.useTiming))
    }

    fun toggleTabs() {
        val s = _state.value?.settings ?: return
        update(s.copy(showTabs = !s.showTabs))
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

/** Per-song settings: default speed, tab visibility, and read-only song info. Room for more rows later. */
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
                                value = speedLabel(s.settings.tick),
                                canDecrease = s.settings.tick > ScrollSpeed.MIN,
                                canIncrease = s.settings.tick < ScrollSpeed.MAX,
                                onDecrease = { viewModel.changeSpeed(-1) },
                                onIncrease = { viewModel.changeSpeed(1) },
                            )
                        }
                        LightText(
                            text = stringResource(R.string.settings_speed_hint),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        when (s.timingValid) {
                            true -> {
                                Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                                SettingRow(stringResource(R.string.settings_timing), onClick = { viewModel.toggleTiming() }) {
                                    LightIcon(icon = if (s.settings.useTiming) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF)
                                }
                                LightText(stringResource(R.string.settings_timing_hint), LightTextVariant.Detail, lighten = true)
                            }
                            false -> {
                                Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                                LightText(stringResource(R.string.settings_timing_invalid), LightTextVariant.Detail, lighten = true)
                            }
                            null -> Unit
                        }
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
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", canDecrease, onDecrease)
        LightText(
            text = value,
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
