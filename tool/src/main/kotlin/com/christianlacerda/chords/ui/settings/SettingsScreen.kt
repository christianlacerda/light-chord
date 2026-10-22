package com.christianlacerda.chords.ui.settings

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repository: LibraryRepository) : LightViewModel<Unit>() {
    /** Null until the saved value is read. */
    private val _notation = MutableStateFlow<ChordNotation?>(null)
    val notation: StateFlow<ChordNotation?> = _notation.asStateFlow()

    init {
        viewModelScope.launch { _notation.value = repository.loadNotation() }
    }

    /** Switches every song between Brazilian and international chord names. */
    fun toggleNotation() {
        val current = _notation.value ?: return
        val next = if (current == ChordNotation.BRAZILIAN) ChordNotation.INTERNATIONAL else ChordNotation.BRAZILIAN
        _notation.value = next
        viewModelScope.launch {
            // NonCancellable: a tap followed straight away by Back must still be saved.
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
}

/** Settings for the whole tool (every song), opened from the gear on the Artists screen. Room for more rows. */
class SettingsScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(libraryRepositoryFor(lightContext))

    @Composable
    override fun Content() {
        val notation by viewModel.notation.collectAsState()
        ChordsTheme {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(stringResource(R.string.settings_title)),
                )
                val n = notation ?: return@Column
                val brazilian = n == ChordNotation.BRAZILIAN
                LightScrollView(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = CONTENT_PADDING_UNITS.gridUnitsAsDp())) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { viewModel.toggleNotation() }
                                .padding(end = 1f.gridUnitsAsDp(), top = 0.6f.gridUnitsAsDp(), bottom = 0.6f.gridUnitsAsDp()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LightText(
                                stringResource(R.string.settings_chord_names),
                                LightTextVariant.Copy,
                                modifier = Modifier.weight(1f),
                            )
                            LightText(
                                stringResource(if (brazilian) R.string.notation_brazilian else R.string.notation_international),
                                LightTextVariant.Copy,
                            )
                        }
                        LightText(
                            text = stringResource(
                                if (brazilian) R.string.notation_brazilian_hint else R.string.notation_international_hint,
                            ),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }
            }
        }
    }
}
