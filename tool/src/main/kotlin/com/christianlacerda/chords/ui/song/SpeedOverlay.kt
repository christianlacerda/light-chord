package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.christianlacerda.chords.R
import com.christianlacerda.chords.layout.ScrollSpeed
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay

/**
 * Drawn inside the Song screen (not through LightModalManager, which is global and takes over the screen).
 * Shows for about a second after each speed change. When opened from the "Speed N" item ([interactive]) the
 * left half is a minus and the right half a plus, which also makes the emulator usable without a wheel.
 */
@Composable
internal fun SpeedOverlay(
    speed: Int,
    flashToken: Int,
    interactive: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onHidden: () -> Unit,
) {
    val initialToken = remember { flashToken }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(flashToken) {
        if (flashToken == initialToken) return@LaunchedEffect // don't flash when the screen is merely re-shown
        visible = true
        delay(if (interactive) 3000L else 1000L)
        visible = false
        onHidden()
    }
    if (!visible) return

    val colors = LightThemeTokens.colors
    val shape = RoundedCornerShape(0.8f.gridUnitsAsDp())
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(12f.gridUnitsAsDp())
                .height(11.5f.gridUnitsAsDp())
                .background(colors.background, shape)
                .border(1.dp, colors.content, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LightText(stringResource(R.string.speed_label), LightTextVariant.Detail, lighten = true, align = TextAlign.Center)
                BasicText(
                    text = speed.toString(),
                    style = LightThemeTokens.typography.copy.copy(
                        fontSize = 64f.designVerticalPxToSp(),
                        lineHeight = 70f.designVerticalPxToSp(),
                        fontWeight = FontWeight.Bold,
                        color = colors.content,
                    ),
                )
                Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
                Row(horizontalArrangement = Arrangement.spacedBy(0.25f.gridUnitsAsDp()), verticalAlignment = Alignment.Bottom) {
                    for (i in 0 until ScrollSpeed.MAX) {
                        Box(
                            Modifier
                                .width(0.5f.gridUnitsAsDp())
                                .height((0.6f + i * 0.12f).gridUnitsAsDp())
                                .background(
                                    if (i < speed) colors.content else colors.contentSecondary.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }
            if (interactive) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().lightClickable { onMinus() }, contentAlignment = Alignment.CenterStart) {
                        LightText("−", LightTextVariant.Heading, modifier = Modifier.padding(horizontal = 0.6f.gridUnitsAsDp()))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().lightClickable { onPlus() }, contentAlignment = Alignment.CenterEnd) {
                        LightText("+", LightTextVariant.Heading, modifier = Modifier.padding(horizontal = 0.6f.gridUnitsAsDp()))
                    }
                }
            }
        }
    }
}
