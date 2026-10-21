package com.christianlacerda.chords.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.christianlacerda.chords.R
import com.christianlacerda.chords.ui.ChordsTheme
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Asks before removing all of an artist's songs from the phone; returns true when the user confirmed. */
class DeleteArtistScreen(
    sealedActivity: SealedLightActivity,
    private val artist: String,
    private val songCount: Int,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        ChordsTheme {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LightText(stringResource(R.string.delete_artist_title), LightTextVariant.Heading, align = TextAlign.Center)
                    LightText(
                        text = pluralStringResource(R.plurals.delete_artist_body, songCount, artist, songCount),
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp()),
                    )
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = stringResource(R.string.cancel).uppercase(), onClick = { goBack(false) }),
                        LightBarButton.Text(text = stringResource(R.string.delete).uppercase(), onClick = { goBack(true) }),
                    ),
                )
            }
        }
    }
}
