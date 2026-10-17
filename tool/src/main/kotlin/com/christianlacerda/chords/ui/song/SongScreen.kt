package com.christianlacerda.chords.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.christianlacerda.chords.R
import com.christianlacerda.chords.layout.Measurers
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.layout.buildRenderItems
import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.chordStyle
import com.christianlacerda.chords.ui.chords.ChordDiagramScreen
import com.christianlacerda.chords.ui.errorText
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.lyricStyle
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.isActive

class SongScreen(sealedActivity: SealedLightActivity, private val fileName: String) :
    LightScreen<Unit, SongViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongViewModel>
        get() = SongViewModel::class.java

    override fun createViewModel() = SongViewModel(libraryRepositoryFor(lightContext), fileName)

    @Composable
    override fun Content() {
        ChordsTheme {
            SongContent(
                vm = viewModel,
                fileName = fileName,
                onBack = { goBack() },
                onChordTap = { chord, title ->
                    val shapes = viewModel.shapesFor(chord)
                    navigateTo({ ChordDiagramScreen(it, title, shapes) })
                },
            )
        }
    }
}

@Composable
private fun SongContent(
    vm: SongViewModel,
    fileName: String,
    onBack: () -> Unit,
    onChordTap: (ChordName, String) -> Unit,
) {
    val colors = LightThemeTokens.colors
    val load by vm.load.collectAsState()
    val playState by vm.playState.collectAsState()
    val speed by vm.speed.collectAsState()
    val progress by remember(vm) {
        derivedStateOf {
            val max = vm.scrollState.maxValue
            if (max > 0 && max < Int.MAX_VALUE) (vm.scrollState.value.toFloat() / max).coerceIn(0f, 1f) else 0f
        }
    }
    val loaded = load as? SongLoad.Ready
    val title = loaded?.song?.meta?.title ?: fileName.substringBeforeLast('.')

    Box(Modifier.fillMaxSize().background(colors.background)) {
        KeepScreenOn()
        Column(Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack),
                center = LightTopBarCenter.Text(title),
            )
            when (val l = load) {
                is SongLoad.Loading -> Spacer(Modifier.weight(1f))
                is SongLoad.Failed -> Box(
                    Modifier.weight(1f).fillMaxSize().padding(horizontal = 2f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = errorText(l.code) ?: stringResource(R.string.song_missing),
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )
                }
                is SongLoad.Ready -> {
                    SongBodyScroll(
                        modifier = Modifier.weight(1f),
                        vm = vm,
                        song = l.song,
                        playState = playState,
                        speed = speed,
                        onChordTap = { onChordTap(it, title) },
                    )
                    SongBottomBar(progress, playState, speed, onSpeedTap = { })
                }
            }
        }
    }
}

/**
 * Keeps the screen awake while this composable is in the composition. Modifier.keepScreenOn() would be
 * neater, but this works on any Compose version and never names a blocked Context/LocalView symbol.
 */
@Composable
private fun KeepScreenOn() {
    AndroidView(
        factory = { viewContext -> android.view.View(viewContext).apply { keepScreenOn = true } },
        modifier = Modifier.size(1.dp),
    )
}

@Composable
private fun SongBodyScroll(
    modifier: Modifier,
    vm: SongViewModel,
    song: Song,
    playState: PlayState,
    speed: Int,
    onChordTap: (ChordName) -> Unit,
) {
    val colors = LightThemeTokens.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val lyric = lyricStyle()
    val chord = chordStyle()
    val sidePad = 1.5f.gridUnitsAsDp()
    val chordGapPx = with(density) { 0.4f.gridUnitsAsDp().toPx() }

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { (maxWidth - sidePad * 2).toPx() }
        val viewportPx = with(density) { maxHeight.toPx() }
        val chordLineH = with(density) { chord.lineHeight.toPx() }
        val lyricLineH = with(density) { lyric.lineHeight.toPx() }
        val gapPx = lyricLineH * 0.35f

        val items = remember(song, widthPx, lyric, chord, chordGapPx) {
            val lyricCache = HashMap<String, Float>()
            val chordCache = HashMap<String, Float>()
            buildRenderItems(
                song, widthPx,
                Measurers(
                    lyricWidth = { t -> lyricCache.getOrPut(t) { measurer.textWidth(t, lyric) } },
                    chordWidth = { t -> chordCache.getOrPut(t) { measurer.textWidth(t, chord) } },
                    chordGap = chordGapPx,
                ),
            )
        }

        val palette = SongPalette(
            lyric = lyric,
            chord = chord,
            lyricColor = if (playState == PlayState.Playing) colors.content else colors.contentSecondary,
            chordColor = colors.content,
            chordLineHeightPx = chordLineH,
            lyricLineHeightPx = lyricLineH,
            lineGapPx = gapPx,
            chordsTappable = playState != PlayState.Playing,
        )

        // Speed is defined in music lines per second, so it stays right if the font size changes.
        val pxPerSec = ScrollSpeed.pxPerSecond(speed, chordLineH + lyricLineH + gapPx)
        LaunchedEffect(playState, pxPerSec) {
            if (playState != PlayState.Playing) return@LaunchedEffect
            val scroll = vm.scrollState
            var last = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val dt = (now - last) / 1_000_000_000f
                    last = now
                    if (!scroll.isScrollInProgress) { // a finger on the text = manual correction
                        scroll.dispatchRawDelta(pxPerSec * dt)
                        if (scroll.maxValue < Int.MAX_VALUE && scroll.value >= scroll.maxValue) vm.onReachedEnd()
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .lightClickable { vm.togglePlay() }
                .verticalScroll(vm.scrollState)
                .padding(horizontal = sidePad),
        ) {
            Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
            items.forEach { RenderItemView(it, palette, onChordTap) }
            // Lets the last line climb into the upper reading area before the song ends.
            Spacer(Modifier.height(with(density) { (viewportPx * 0.55f).toDp() }))
        }
    }
}

/** Width in px; trailing spaces are counted (measuring a sentinel avoids engines that trim them). */
private fun TextMeasurer.textWidth(text: String, style: TextStyle): Float {
    if (text.isEmpty()) return 0f
    fun raw(t: String): Float = measure(AnnotatedString(t), style, softWrap = false, maxLines = 1).size.width.toFloat()
    return if (text.last().isWhitespace()) raw(text + "x") - raw("x") else raw(text)
}
