package com.christianlacerda.chords.ui.chords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Bottom bar: ‹ [label] › on the SDK's bar. An arrow is hidden when there is nowhere to go (the bar has no dimmed state). */
@Composable
internal fun ArrowBar(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    label: String?,
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
        LightBottomBar(
            items = listOf(
                if (canPrev) LightBarButton.LightIcon(LightIcons.BACK, onClick = onPrev, contentDescription = null) else null,
                label?.let { LightBarButton.Text(it.uppercase(), onClick = null) },
                if (canNext) LightBarButton.LightIcon(LightIcons.ARROW_RIGHT, onClick = onNext, contentDescription = null) else null,
            ),
        )
    }
}
