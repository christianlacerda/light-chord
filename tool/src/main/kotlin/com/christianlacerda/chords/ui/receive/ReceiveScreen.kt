package com.christianlacerda.chords.ui.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.R
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.transfer.TransferClient
import com.christianlacerda.chords.transfer.TransferException
import com.christianlacerda.chords.transfer.TransferHost
import com.christianlacerda.chords.transfer.TransferProgress
import com.christianlacerda.chords.transfer.TransferProtocol
import com.christianlacerda.chords.transfer.TransferSummary
import com.christianlacerda.chords.transfer.TransferTarget
import com.christianlacerda.chords.transfer.UrlConnectionHttp
import com.christianlacerda.chords.ui.CONTENT_PADDING_UNITS
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.KeepScreenOn
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReceiveError { NOT_CODE, UNREACHABLE, EXPIRED, PROTOCOL }

sealed interface ReceiveState {
    data object Connecting : ReceiveState
    data class Receiving(val done: Int, val total: Int) : ReceiveState
    data class AskReplace(val conflicts: Int) : ReceiveState
    data class Done(val summary: TransferSummary) : ReceiveState
    data class Failed(val error: ReceiveError) : ReceiveState
}

class ReceiveViewModel(
    private val qrText: String,
    private val repository: LibraryRepository,
) : LightViewModel<Boolean>() {
    private val _state = MutableStateFlow<ReceiveState>(ReceiveState.Connecting)
    val state: StateFlow<ReceiveState> = _state.asStateFlow()

    private var pendingReplace: CompletableDeferred<Boolean>? = null

    init {
        val target = TransferProtocol.parseQr(qrText)
        if (target == null) {
            _state.value = ReceiveState.Failed(ReceiveError.NOT_CODE)
        } else {
            viewModelScope.launch { run(target) }
        }
    }

    private suspend fun run(target: TransferTarget) {
        val host = object : TransferHost {
            override suspend fun existingHash(name: String) = repository.songHash(name)

            override suspend fun write(name: String, bytes: ByteArray) = repository.writeSong(name, bytes)

            override suspend fun confirmReplace(conflicts: Int): Boolean {
                val answer = CompletableDeferred<Boolean>()
                pendingReplace = answer
                _state.value = ReceiveState.AskReplace(conflicts)
                return answer.await()
            }

            override fun onProgress(progress: TransferProgress) {
                _state.value = when (progress) {
                    TransferProgress.Connecting -> ReceiveState.Connecting
                    is TransferProgress.Receiving -> ReceiveState.Receiving(progress.done, progress.total)
                }
            }
        }
        try {
            val summary = TransferClient(UrlConnectionHttp(), target).run(host)
            // same re-index as on app start, so the new songs show up in Artists right away
            repository.refreshSafely()
            _state.value = ReceiveState.Done(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransferException.Expired) {
            _state.value = ReceiveState.Failed(ReceiveError.EXPIRED)
        } catch (e: TransferException.Unreachable) {
            _state.value = ReceiveState.Failed(ReceiveError.UNREACHABLE)
        } catch (e: Exception) {
            _state.value = ReceiveState.Failed(ReceiveError.PROTOCOL)
        }
    }

    fun answerReplace(replace: Boolean) {
        _state.value = ReceiveState.Receiving(0, 0)
        pendingReplace?.complete(replace)
        pendingReplace = null
    }
}

/** Downloads the songs from Chord Desk; returns true when the user finished (Done) so the caller can pop to Artists. */
class ReceiveScreen(sealedActivity: SealedLightActivity, private val qrText: String) :
    LightScreen<Boolean, ReceiveViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiveViewModel>
        get() = ReceiveViewModel::class.java

    override fun createViewModel() = ReceiveViewModel(qrText, libraryRepositoryFor(lightContext))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        ChordsTheme {
            ReceiveContent(
                state = state,
                onBack = { goBack() },
                onFinish = { goBack(true) },
                onReplace = viewModel::answerReplace,
            )
        }
    }
}

/** Camera screen; hands the decoded text to the caller. Pops itself first (see LightQrCodeScanner KDoc). */
class ScanChordDeskScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<String>(sealedActivity) {
    @Composable
    override fun Content() {
        var scanned by remember { mutableStateOf<String?>(null) }
        ChordsTheme {
            LightQrCodeScanner(
                title = stringResource(R.string.receive_scan_title),
                onScanned = { scanned = it },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
        LaunchedEffect(scanned) {
            val value = scanned ?: return@LaunchedEffect
            scanned = null
            goBack(value)
        }
    }
}

@Composable
private fun ReceiveContent(
    state: ReceiveState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onReplace: (Boolean) -> Unit,
) {
    val colors = LightThemeTokens.colors
    val pad = CONTENT_PADDING_UNITS.gridUnitsAsDp()
    val busy = state is ReceiveState.Connecting || state is ReceiveState.Receiving
    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(stringResource(R.string.receive_title)),
        )
        if (busy) KeepScreenOn()
        when (state) {
            ReceiveState.Connecting -> Centered {
                LightText(stringResource(R.string.receive_connecting), LightTextVariant.Heading, align = TextAlign.Center)
            }
            is ReceiveState.Receiving -> Centered {
                LightText(
                    text = stringResource(R.string.receive_progress, state.done, state.total),
                    variant = LightTextVariant.Heading,
                    align = TextAlign.Center,
                )
                Box(Modifier.fillMaxWidth().padding(top = 1f.gridUnitsAsDp())) {
                    LightProgressBar(
                        colors,
                        if (state.total == 0) 0f else state.done.toFloat() / state.total,
                    )
                }
            }
            is ReceiveState.AskReplace -> {
                Centered {
                    LightText(
                        text = pluralStringResource(R.plurals.receive_conflict, state.conflicts, state.conflicts),
                        variant = LightTextVariant.Heading,
                        align = TextAlign.Center,
                    )
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = stringResource(R.string.receive_skip).uppercase(), onClick = { onReplace(false) }),
                        LightBarButton.Text(text = stringResource(R.string.receive_replace).uppercase(), onClick = { onReplace(true) }),
                    ),
                )
            }
            is ReceiveState.Done -> {
                val s = state.summary
                val count = s.songsReceived
                Column(Modifier.weight(1f).fillMaxWidth().padding(start = pad, top = 0.5f.gridUnitsAsDp())) {
                    LightText(
                        text = pluralStringResource(R.plurals.receive_done, count, count),
                        variant = LightTextVariant.Heading,
                    )
                    if (s.skipped.isNotEmpty() || s.failed.isNotEmpty()) {
                        val parts = buildList {
                            if (s.skipped.isNotEmpty()) add(stringResource(R.string.receive_skipped_count, s.skipped.size))
                            if (s.failed.isNotEmpty()) add(stringResource(R.string.receive_failed_count, s.failed.size))
                        }
                        LightText(
                            text = parts.joinToString(" · "),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
                        )
                    }
                    LightScrollView(Modifier.weight(1f).fillMaxWidth().padding(top = 0.6f.gridUnitsAsDp())) {
                        Column(Modifier.fillMaxWidth().padding(end = pad)) {
                            (s.received + s.replaced).forEach { name ->
                                LightText(
                                    text = name.substringBeforeLast('.', name),
                                    variant = LightTextVariant.Paragraph,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 0.4f.gridUnitsAsDp()),
                                )
                            }
                        }
                    }
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = stringResource(R.string.done).uppercase(), onClick = onFinish),
                    ),
                )
            }
            is ReceiveState.Failed -> Centered {
                LightText(
                    text = stringResource(
                        when (state.error) {
                            ReceiveError.NOT_CODE -> R.string.receive_error_not_code
                            ReceiveError.UNREACHABLE -> R.string.receive_error_unreachable
                            ReceiveError.EXPIRED -> R.string.receive_error_expired
                            ReceiveError.PROTOCOL -> R.string.receive_error_protocol
                        },
                    ),
                    variant = LightTextVariant.Heading,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
