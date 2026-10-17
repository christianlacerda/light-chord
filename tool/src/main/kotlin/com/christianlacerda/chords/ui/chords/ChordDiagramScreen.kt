package com.christianlacerda.chords.ui.chords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.voicing.ChordShapes
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

/** Milestone 3 stub: just the chord name. The real diagram arrives in milestone 4. */
class ChordDiagramScreen(
    sealedActivity: SealedLightActivity,
    private val songTitle: String,
    private val shapes: ChordShapes,
) : SimpleLightScreen<Unit>(sealedActivity) {
    @Composable
    override fun Content() {
        ChordsTheme {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(songTitle),
                )
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    LightText(shapes.chord.raw, LightTextVariant.Title)
                }
            }
        }
    }
}
