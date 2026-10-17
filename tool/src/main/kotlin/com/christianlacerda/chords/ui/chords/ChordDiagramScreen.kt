package com.christianlacerda.chords.ui.chords

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.christianlacerda.chords.R
import com.christianlacerda.chords.chordpro.Transposer
import com.christianlacerda.chords.ui.ChordsTheme
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cycles through the shapes of one chord; the wheel does the same as the arrows. */
class ChordDiagramViewModel(private val shapes: ChordShapes) : LightViewModel<Unit>() {
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    val count: Int get() = shapes.voicings.size

    fun step(delta: Int) {
        if (count == 0) return
        _index.value = (_index.value + delta).coerceIn(0, count - 1)
    }

    val wheel = WheelInput(onStep = { step(it) })

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = wheel.onKey(keyCode, WheelInput.ACTION_DOWN, 0)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean =
        wheel.onKey(keyCode, WheelInput.ACTION_DOWN, repeatCount)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = wheel.onKey(keyCode, WheelInput.ACTION_UP, 0)
}

class ChordDiagramScreen(
    sealedActivity: SealedLightActivity,
    private val songTitle: String,
    private val shapes: ChordShapes,
) : LightScreen<Unit, ChordDiagramViewModel>(sealedActivity) {

    override val viewModelClass: Class<ChordDiagramViewModel>
        get() = ChordDiagramViewModel::class.java

    override fun createViewModel() = ChordDiagramViewModel(shapes)

    @Composable
    override fun Content() {
        val index by viewModel.index.collectAsState()
        ChordsTheme {
            val colors = LightThemeTokens.colors
            Column(Modifier.fillMaxSize().background(colors.background).wheelInput(viewModel.wheel)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(songTitle),
                )
                BasicText(
                    text = shapes.chord.raw,
                    modifier = Modifier.fillMaxWidth(),
                    style = LightThemeTokens.typography.copy.copy(
                        fontSize = 64f.designVerticalPxToSp(),
                        lineHeight = 70f.designVerticalPxToSp(),
                        fontWeight = FontWeight.Bold,
                        color = colors.content,
                        textAlign = TextAlign.Center,
                    ),
                )
                val voicing = shapes.voicings.getOrNull(index)
                if (voicing == null) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
                        LightText(stringResource(R.string.no_shape), LightTextVariant.Copy, align = TextAlign.Center)
                    }
                } else {
                    val bass = shapes.chord.bass
                    val note = when {
                        shapes.approximate -> stringResource(R.string.closest_shape, shapes.lookedUp.raw)
                        bass != null && shapes.lookedUp.bass == null ->
                            stringResource(R.string.bass_note, Transposer.noteName(bass, shapes.chord.preferFlats))
                        else -> null
                    }
                    if (note != null) {
                        LightText(note, LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    ChordDiagram(
                        voicing = voicing,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()),
                    )
                }
                ArrowBar(
                    canPrev = index > 0,
                    canNext = index < shapes.voicings.size - 1,
                    onPrev = { viewModel.step(-1) },
                    onNext = { viewModel.step(1) },
                ) {
                    if (voicing != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LightText(stringResource(R.string.shape_of, index + 1, shapes.voicings.size), LightTextVariant.Fine)
                            LightText(
                                text = voicing.frets.joinToString(" ") { it?.toString() ?: "x" },
                                variant = LightTextVariant.Detail,
                                lighten = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
