package com.christianlacerda.chords.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.christianlacerda.chords.R
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongError
import com.thelightphone.sdk.SealedLightContext
import java.text.Collator
import java.util.Locale

/** Builds a repository from the screen's context; a new one per ViewModel (nothing static holds it). */
internal fun libraryRepositoryFor(ctx: SealedLightContext): LibraryRepository =
    LibraryRepository(ctx.fileShare, ctx.dataStore, ctx::readAsset)

internal fun artistCollator(): Collator =
    Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

/** Horizontal padding of screen content, in grid units. */
internal const val CONTENT_PADDING_UNITS = 1.5f

/** "4" or "4.5" ("4,5" in Portuguese) for a speed in [ScrollSpeed] ticks. */
@Composable
internal fun speedLabel(tick: Int): String =
    ScrollSpeed.label(tick, stringResource(R.string.decimal_separator).first())

/** "1x", "1.05x", "0.5x" ("1,05x" in Portuguese) for a timed playback rate in percent. */
@Composable
internal fun rateLabel(percent: Int): String =
    ScrollSpeed.rateLabel(percent, stringResource(R.string.decimal_separator).first())

/** Localized text for a stored error code, or null when there is no error. */
@Composable
internal fun errorText(code: String?): String? = when (code) {
    SongError.NOT_CHORDPRO -> stringResource(R.string.error_not_chordpro)
    SongError.EMPTY -> stringResource(R.string.error_empty)
    SongError.TOO_LARGE -> stringResource(R.string.error_too_large)
    SongError.UNREADABLE -> stringResource(R.string.error_unreadable)
    else -> null
}
