package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.christianlacerda.chords.R
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Progress line, then one row: a play/pause button on the left; on the right "Speed N" while playing (opens the
 * speed panel, the wheel fallback) or the settings icon while stopped.
 */
@Composable
internal fun SongBottomBar(
    progress: State<Float>,
    playState: PlayState,
    speed: Int,
    onPlayPause: () -> Unit,
    onSpeedTap: () -> Unit,
    onSettings: () -> Unit,
) {
    val playing = playState == PlayState.Playing
    Column(Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
        ProgressLine(progress)
        Row(
            modifier = Modifier.fillMaxWidth().height(3f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LightIcon(
                icon = if (playing) LightIcons.PAUSE else LightIcons.PLAY,
                size = 1.6f,
                modifier = Modifier.lightClickable { onPlayPause() },
            )
            if (playing) {
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
            } else {
                LightIcon(
                    icon = LightIcons.SETTINGS,
                    size = 1.6f,
                    contentDescription = stringResource(R.string.song_settings),
                    modifier = Modifier.lightClickable { onSettings() },
                )
            }
        }
    }
}

/**
 * Thin dim track with a solid filled part. (LightProgressBar draws its track in the content colour, which
 * reads as "complete" at 0%.) The progress State is read here so only this line recomposes per frame.
 */
@Composable
private fun ProgressLine(progress: State<Float>) {
    val colors = LightThemeTokens.colors
    Box(Modifier.fillMaxWidth().height(0.5f.gridUnitsAsDp()), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.1f.gridUnitsAsDp())
                .background(colors.contentSecondary.copy(alpha = 0.35f)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                .height(0.5f.gridUnitsAsDp())
                .background(colors.content),
        )
    }
}
