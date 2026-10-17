package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.christianlacerda.chords.R
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** Progress line, then "[glyph] state ... (o) Speed N". Speed is tappable (fallback for the wheel). */
@Composable
internal fun SongBottomBar(
    progress: State<Float>,
    playState: PlayState,
    speed: Int,
    onSpeedTap: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val stateText = when (playState) {
        PlayState.Ready -> stringResource(R.string.state_ready)
        PlayState.Playing -> stringResource(R.string.state_scrolling)
        PlayState.Paused -> stringResource(R.string.state_paused)
        PlayState.Ended -> stringResource(R.string.state_end)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
        LightProgressBar(colors, progress.value)
        Row(
            modifier = Modifier.fillMaxWidth().height(3f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                LightIcon(
                    icon = if (playState == PlayState.Playing) LightIcons.PLAY else LightIcons.PAUSE,
                    size = 1.2f,
                    contentDescription = null,
                )
                Spacer(Modifier.width(0.5f.gridUnitsAsDp()))
                LightText(
                    text = stateText,
                    variant = LightTextVariant.Fine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.lightClickable { onSpeedTap() }.padding(start = 0.8f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightIcon(icon = LightIcons.CIRCLE, size = 1f, contentDescription = null)
                Spacer(Modifier.width(0.4f.gridUnitsAsDp()))
                LightText(
                    text = stringResource(R.string.speed_short, speed),
                    variant = LightTextVariant.Fine,
                    maxLines = 1,
                )
            }
        }
    }
}
