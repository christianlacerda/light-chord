package com.christianlacerda.chords.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Keeps the screen awake while this composable is in the composition. Modifier.keepScreenOn() would be
 * neater, but this works on any Compose version and never names a blocked Context/LocalView symbol.
 */
@Composable
fun KeepScreenOn() {
    AndroidView(
        factory = { viewContext -> android.view.View(viewContext).apply { keepScreenOn = true } },
        modifier = Modifier.size(1.dp),
    )
}
