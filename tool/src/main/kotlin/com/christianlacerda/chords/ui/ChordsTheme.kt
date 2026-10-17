package com.christianlacerda.chords.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController

@Composable
fun ChordsTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors, content = content)
}
