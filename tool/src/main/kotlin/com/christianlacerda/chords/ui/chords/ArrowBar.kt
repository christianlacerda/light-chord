package com.christianlacerda.chords.ui.chords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.ui.unit.dp

/** Bottom bar: ‹ [center] ›. The arrows dim to 0.3 alpha when there is nowhere to go. */
@Composable
internal fun ArrowBar(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    center: @Composable () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Column(Modifier.fillMaxWidth()) {
        // Dim like the song's progress track, with a little air above it (between the diagram and the line).
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 1f.gridUnitsAsDp())
                .height(1.dp)
                .background(colors.content.copy(alpha = 0.25f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(4f.gridUnitsAsDp()).padding(horizontal = 1f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightIcon(
                icon = LightIcons.BACK,
                contentDescription = null,
                modifier = Modifier
                    .graphicsLayer(alpha = if (canPrev) 1f else 0.3f)
                    .lightClickable(enabled = canPrev) { onPrev() },
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
            LightIcon(
                icon = LightIcons.BACK,
                contentDescription = null,
                modifier = Modifier
                    .graphicsLayer(scaleX = -1f, alpha = if (canNext) 1f else 0.3f)
                    .lightClickable(enabled = canNext) { onNext() },
            )
        }
    }
}
