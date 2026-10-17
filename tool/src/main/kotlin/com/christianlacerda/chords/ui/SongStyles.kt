package com.christianlacerda.chords.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp

/** Design-pixel sizes (the SDK scales these by screen height; about 62 px / 34 characters per line on the LP3). */
const val LYRIC_DESIGN_PX = 30f
const val LYRIC_LINE_DESIGN_PX = 36f
const val CHORD_DESIGN_PX = 22f
const val CHORD_LINE_DESIGN_PX = 28f
const val TAB_DESIGN_PX = 16f

// `scaledForScreenHeight` is internal to the SDK, so the styles are built on the raw tokens.
@Composable
fun lyricStyle(): TextStyle = LightThemeTokens.typography.copy.copy(
    fontSize = LYRIC_DESIGN_PX.designVerticalPxToSp(),
    lineHeight = LYRIC_LINE_DESIGN_PX.designVerticalPxToSp(),
    letterSpacing = 0f.designVerticalPxToSp(),
)

@Composable
fun chordStyle(): TextStyle = LightThemeTokens.typography.copy.copy(
    fontSize = CHORD_DESIGN_PX.designVerticalPxToSp(),
    lineHeight = CHORD_LINE_DESIGN_PX.designVerticalPxToSp(),
    letterSpacing = 0f.designVerticalPxToSp(),
    fontWeight = FontWeight.Bold,
)
