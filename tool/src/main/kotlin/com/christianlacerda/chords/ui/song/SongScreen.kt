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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
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
import com.christianlacerda.chords.layout.RenderItem
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.layout.buildRenderItems
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.timing.RehearsalTiming
import com.christianlacerda.chords.ui.ChordsIconPainter
import com.christianlacerda.chords.ui.ChordsTheme
import com.christianlacerda.chords.ui.KeepScreenOn
import com.christianlacerda.chords.ui.wheelInput
import com.christianlacerda.chords.ui.chords.SongChordsScreen
import com.christianlacerda.chords.ui.chordStyle
import com.christianlacerda.chords.ui.chords.ChordDiagramScreen
import com.christianlacerda.chords.ui.errorText
import com.christianlacerda.chords.ui.libraryRepositoryFor
import com.christianlacerda.chords.ui.lyricStyle
import com.christianlacerda.chords.ui.rateLabel
import com.christianlacerda.chords.ui.speedLabel
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
                onOpenChords = { title ->
                    val key = viewModel.songKey()
                    val capo = viewModel.songCapo()
                    viewModel.withSongChords { chords ->
                        navigateTo({ SongChordsScreen(it, title, key, chords, capo) })
                    }
                },
                onChordTap = { chord, title ->
                    val capo = viewModel.songCapo()
                    viewModel.withShapes(chord) { shapes ->
                        navigateTo({ ChordDiagramScreen(it, title, shapes, capo) })
                    }
                },
                onOpenSettings = { navigateTo({ SongSettingsScreen(it, fileName) }) },
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
    onOpenChords: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val load by vm.load.collectAsState()
    val showTabs by vm.showTabs.collectAsState()
    val playState by vm.playState.collectAsState()
    val speed by vm.speed.collectAsState()
    val timed by vm.timed.collectAsState()
    val rate by vm.rate.collectAsState()
    // a timed song shows its playback rate where the speed would be: 1x = as rehearsed
    val speedText = if (timed != null) rateLabel(rate) else speedLabel(speed)
    val speedTitle = stringResource(R.string.speed_label)
    val flashToken by vm.flashToken.collectAsState()
    val speedPanel by vm.speedPanel.collectAsState()
    val countdown by vm.countdown.collectAsState()
    // Held as State (not delegated) so only the bottom bar re-reads it every frame, not the whole screen.
    val progress = remember(vm) {
        derivedStateOf {
            val max = vm.scrollState.maxValue
            if (max > 0 && max < Int.MAX_VALUE) (vm.scrollState.value.toFloat() / max).coerceIn(0f, 1f) else 0f
        }
    }
    val loaded = load as? SongLoad.Ready
    val title = loaded?.song?.meta?.title ?: fileName.substringBeforeLast('.')

    Box(Modifier.fillMaxSize().background(colors.background).wheelInput(vm.wheel)) {
        KeepScreenOn()
        Column(Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack),
                center = LightTopBarCenter.Text(title),
                rightButton = if (loaded != null) {
                    LightBarButton.Icon(
                        painter = remember(colors.content) { ChordsIconPainter(colors.content) },
                        onClick = { onOpenChords(title) },
                        contentDescription = stringResource(R.string.chords_in_song),
                    )
                } else {
                    null
                },
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
                        timed = timed,
                        showTabs = showTabs,
                        onChordTap = { onChordTap(it, title) },
                    )
                    SongBottomBar(
                        progress = progress,
                        // pause (cancel) and the speed show during the countdown, as while playing
                        playState = if (countdown != null) PlayState.Playing else playState,
                        // uppercase the label only, so "1.05x" keeps its small x
                        speedText = stringResource(R.string.speed_short, "§").uppercase().replace("§", speedText),
                        onPlayPause = { vm.togglePlay() },
                        onSpeedTap = { vm.openSpeedPanel() },
                        onSettings = onOpenSettings,
                    )
                }
            }
        }
        if (loaded != null) {
            SpeedOverlay(
                title = speedTitle,
                value = speedText,
                level = if (timed != null) null else (speed + ScrollSpeed.TICKS_PER_LEVEL).toFloat() / ScrollSpeed.TICKS_PER_LEVEL,
                flashToken = flashToken,
                interactive = speedPanel,
                onMinus = { vm.changeSpeed(-1) },
                onPlus = { vm.changeSpeed(1) },
                onHidden = { vm.closeSpeedPanel() },
            )
            countdown?.let { CountdownOverlay(it) }
        }
    }
}

@Composable
private fun SongBodyScroll(
    modifier: Modifier,
    vm: SongViewModel,
    song: Song,
    playState: PlayState,
    speed: Int,
    timed: List<RehearsalTiming.Cue>?,
    showTabs: Boolean,
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
        val fadePx = with(density) { 1.5f.gridUnitsAsDp().toPx() }

        val notation by LibraryEvents.notation.collectAsState()
        val items = remember(song, widthPx, lyric, chord, chordGapPx, showTabs, notation) {
            val lyricCache = HashMap<String, Float>()
            val chordCache = HashMap<String, Float>()
            buildRenderItems(
                song, widthPx,
                Measurers(
                    lyricWidth = { t -> lyricCache.getOrPut(t) { measurer.textWidth(t, lyric) } },
                    chordWidth = { t -> chordCache.getOrPut(t) { measurer.textWidth(t, chord) } },
                    chordGap = chordGapPx,
                ),
                showTabs = showTabs,
                notation = notation,
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

        // Rehearsed timing: where each line sits in the content, measured as it is placed.
        val geometry = remember(items) { TimingGeometry() }
        val scroll = vm.scrollState
        fun positions(): DoubleArray = RehearsalTiming.resolvePositions(
            song.terminal, geometry.tops, geometry.end, readingY = viewportPx / 3f,
            maxScroll = if (scroll.maxValue == Int.MAX_VALUE) 0f else scroll.maxValue.toFloat(),
        )

        // Speed is defined in music lines per second, so it stays right if the font size changes.
        val pxPerSec = ScrollSpeed.pxPerSecond(speed, chordLineH + lyricLineH + gapPx)
        LaunchedEffect(playState, pxPerSec, timed, geometry, viewportPx) {
            if (playState != PlayState.Playing) return@LaunchedEffect
            var last = withFrameNanos { it }
            // Sub-pixel position already dispatched. Deltas against the Int ScrollState.value would round each
            // frame and jitter by up to a pixel; resync when the value moved for another reason (drag, relayout).
            var shown = scroll.value.toDouble()
            while (isActive) {
                withFrameNanos { now ->
                    val dt = (now - last) / 1_000_000_000.0
                    last = now
                    if (scroll.isScrollInProgress) return@withFrameNanos // a finger on the text = manual correction
                    if (timed == null) {
                        scroll.dispatchRawDelta(pxPerSec * dt.toFloat())
                        if (scroll.maxValue < Int.MAX_VALUE && scroll.value >= scroll.maxValue) vm.onReachedEnd()
                    } else {
                        // a long gap is time the screen wasn't drawn (e.g. in the background): not song time
                        vm.advance(dt.coerceAtMost(0.25))
                        val ms = vm.timelineMs.toLong()
                        val y = RehearsalTiming.positionAt(ms, timed, positions())
                        if (kotlin.math.abs(scroll.value - shown) > 1.5) shown = scroll.value.toDouble()
                        scroll.dispatchRawDelta((y - shown).toFloat())
                        shown = y
                        if (ms >= timed.last().ms) vm.onReachedEnd()
                    }
                }
            }
        }
        // After a drag (playing or paused), continue from the time that matches where the text was left.
        LaunchedEffect(timed, geometry, viewportPx) {
            val cues = timed ?: return@LaunchedEffect
            var dragging = false
            snapshotFlow { scroll.isScrollInProgress }.collect { inProgress ->
                if (inProgress) {
                    dragging = true
                } else if (dragging) {
                    dragging = false
                    vm.seek(RehearsalTiming.timeAt(scroll.value.toDouble(), cues, positions(), vm.timelineMs.toLong()))
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // soft top edge: lines fade out as they leave the reading area
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fadePx),
                        size = Size(size.width, fadePx),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .lightClickable(hapticsEnabled = false) { vm.togglePlay() }
                .verticalScroll(vm.scrollState)
                .padding(horizontal = sidePad),
        ) {
            // room for the top fade, so the first line is fully visible before scrolling starts
            Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
            items.forEach { item ->
                Box(Modifier.onPlaced { geometry.place(item, it.positionInParent().y, it.size.height.toFloat()) }) {
                    RenderItemView(item, palette, onChordTap)
                }
            }
            // Lets the last line climb into the upper reading area before the song ends.
            Spacer(Modifier.height(with(density) { (viewportPx * 0.55f).toDp() }))
        }
    }
}

/** Content y of each placed line's boundary, and the bottom of the last line (the terminal boundary). */
private class TimingGeometry {
    val tops = HashMap<Int, Float>()
    var end = 0f

    fun place(item: RenderItem, y: Float, height: Float) {
        when (item) {
            is RenderItem.Music -> item.boundary?.let { tops[it] = y }
            // tab rows have a fixed pitch inside the block
            is RenderItem.Tab -> {
                val pitch = height / item.rowBoundaries.size.coerceAtLeast(1)
                item.rowBoundaries.forEachIndexed { row, b -> if (b != null) tops[b] = y + row * pitch }
            }
            else -> return
        }
        end = maxOf(end, y + height)
    }
}

/** Width in px; trailing spaces are counted (measuring a sentinel avoids engines that trim them). */
private fun TextMeasurer.textWidth(text: String, style: TextStyle): Float {
    if (text.isEmpty()) return 0f
    fun raw(t: String): Float = measure(AnnotatedString(t), style, softWrap = false, maxLines = 1).size.width.toFloat()
    return if (text.last().isWhitespace()) raw(text + "x") - raw("x") else raw(text)
}
