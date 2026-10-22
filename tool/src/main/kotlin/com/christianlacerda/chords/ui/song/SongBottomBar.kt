package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.christianlacerda.chords.R
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * Progress line, then the SDK's bar: play/pause on the left; "SPEED N" in the centre while playing (opens the
 * speed panel, the wheel fallback) or the settings icon on the right while stopped.
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
    Column(Modifier.fillMaxWidth()) {
        ProgressLine(progress)
        // Playing: pause · SPEED N in the centre, where the bar gives text its full width (a side slot is a third
        // of the bar and truncated it to "SPEED..."). Stopped: play · gear.
        LightBottomBar(
            items = if (playing) {
                listOf(
                    LightBarButton.LightIcon(LightIcons.PAUSE, onClick = onPlayPause),
                    LightBarButton.Text(stringResource(R.string.speed_short, speed).uppercase(), onClick = onSpeedTap),
                    null,
                )
            } else {
                listOf(
                    LightBarButton.LightIcon(LightIcons.PLAY, onClick = onPlayPause),
                    null,
                    LightBarButton.LightIcon(LightIcons.SETTINGS, onClick = onSettings, contentDescription = stringResource(R.string.song_settings)),
                )
            },
        )
    }
}

/**
 * Thin dim track with a solid filled part. (LightProgressBar draws its track in the content colour, which
 * reads as "complete" at 0%.) The progress State is read here so only this line recomposes per frame.
 */
@Composable
private fun ProgressLine(progress: State<Float>) {
    val colors = LightThemeTokens.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()).height(0.5f.gridUnitsAsDp()), contentAlignment = Alignment.CenterStart) {
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
