package com.christianlacerda.chords.ui.add

import androidx.compose.foundation.background
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.ToolText
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.countAddedSince
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.libraryRepositoryFor
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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class AddSongsViewModel(private val repository: LibraryRepository) : LightViewModel<Unit>() {
    private val _recent = MutableStateFlow(0)

    /** Songs added from Tool Manager today (not bundled, first seen since midnight). */
    val recent: StateFlow<Int> = _recent.asStateFlow()

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    init {
        viewModelScope.launch {
            LibraryEvents.index.filterNotNull().collect { _recent.value = it.countAddedSince(startOfToday()) }
        }
        viewModelScope.launch {
            LibraryEvents.filesChanged.drop(1).collect { repository.refresh() }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch { repository.refresh() }
    }
}

class AddSongsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AddSongsViewModel>(sealedActivity) {

    override val viewModelClass: Class<AddSongsViewModel>
        get() = AddSongsViewModel::class.java

    override fun createViewModel() = AddSongsViewModel(libraryRepositoryFor(lightContext))

    @Composable
    override fun Content() {
        val recent by viewModel.recent.collectAsState()
        ChordsTheme {
            AddSongsContent(recent = recent, onBack = { goBack() })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddSongsContent(recent: Int, onBack: () -> Unit) {
    val colors = LightThemeTokens.colors
    val pad = CONTENT_PADDING_UNITS.gridUnitsAsDp()
    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(stringResource(R.string.add_title)),
        )
        LightScrollView(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(start = pad, top = 0.5f.gridUnitsAsDp())) {
                LightText(stringResource(R.string.add_from_computer), LightTextVariant.Heading)
                Spacer(Modifier.height(0.8f.gridUnitsAsDp()))
                Step(1, stringResource(R.string.add_step1))
                Step(2, stringResource(R.string.add_step2))
                Step(3, stringResource(R.string.add_step3, ToolText.toolName(), ToolText.songsLabel()))
                Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
                LightText(
                    text = stringResource(R.string.add_note),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(end = pad),
                )
                Spacer(Modifier.height(0.8f.gridUnitsAsDp()))
                LightText(
                    text = stringResource(R.string.add_missing, ToolText.toolName()),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(end = pad),
                )
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
            }
        }
        if (recent > 0) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.content))
                LightText(
                    text = pluralStringResource(R.plurals.add_recent, recent, recent),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = pad, vertical = 0.8f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(end = CONTENT_PADDING_UNITS.gridUnitsAsDp(), bottom = 0.6f.gridUnitsAsDp())) {
        LightText(
            text = number.toString(),
            variant = LightTextVariant.Button,
            modifier = Modifier.width(1.6f.gridUnitsAsDp()),
        )
        LightText(text = text, variant = LightTextVariant.Copy, modifier = Modifier.weight(1f))
    }
}
