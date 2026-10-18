package com.christianlacerda.chords.ui.song

import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.lifecycle.viewModelScope
import com.christianlacerda.chords.chordpro.ParseIssue
import com.christianlacerda.chords.layout.ScrollSpeed
import com.christianlacerda.chords.library.LibraryEvents
import com.christianlacerda.chords.library.LibraryRepository
import com.christianlacerda.chords.library.SongError
import com.christianlacerda.chords.library.SongSettings
import com.christianlacerda.chords.model.ChordName
import com.christianlacerda.chords.model.SectionKind
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.ui.WheelInput
import com.christianlacerda.chords.ui.WheelKeys
import com.christianlacerda.chords.voicing.ChordShapes
import com.christianlacerda.chords.voicing.VoicingLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen

enum class PlayState { Ready, Playing, Paused, Ended }

sealed interface SongLoad {
    data object Loading : SongLoad

    /** [code] is a [SongError] code, or null when the file is simply gone. */
    data class Failed(val code: String?) : SongLoad

    data class Ready(val song: Song) : SongLoad
}

class SongViewModel(
    private val repository: LibraryRepository,
    private val fileName: String,
) : LightViewModel<Unit>() {
    /** Lives here (not in the composition) so Back from a chord diagram returns to the same spot. */
    val scrollState = ScrollState(0)

    private val _load = MutableStateFlow<SongLoad>(SongLoad.Loading)
    val load: StateFlow<SongLoad> = _load.asStateFlow()

    private val _playState = MutableStateFlow(PlayState.Ready)
    val playState: StateFlow<PlayState> = _playState.asStateFlow()

    private val _speed = MutableStateFlow(ScrollSpeed.DEFAULT)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    /** Bumped on every speed change so the overlay can flash. */
    private val _flashToken = MutableStateFlow(0)
    val flashToken: StateFlow<Int> = _flashToken.asStateFlow()

    /** True while the speed overlay was opened by tapping "Speed N" (it then has - / + tap zones). */
    private val _speedPanel = MutableStateFlow(false)
    val speedPanel: StateFlow<Boolean> = _speedPanel.asStateFlow()

    /** Wheel steps change the speed; a wheel press (319) toggles play/pause. */
    val wheel = WheelInput(onStep = { changeSpeed(it) }, onPress = { togglePlay() })

    private var loadStarted = false

    /** Show TAB sections? From the song's saved settings. */
    private val _showTabs = MutableStateFlow(true)
    val showTabs: StateFlow<Boolean> = _showTabs.asStateFlow()

    /** The saved (default) speed last applied, so a return from Settings only resets speed when it changed. */
    private var appliedSavedSpeed: Int? = null

    init {
        // The settings screen saves asynchronously; apply its result when the write lands, even if the song
        // screen was shown again before it finished.
        viewModelScope.launch {
            LibraryEvents.settingsChanged.drop(1).collect {
                if (_load.value is SongLoad.Ready) applySettings(repository.settings(fileName))
            }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        // onResume re-invokes this: only ever load once, never reset the play position.
        if (loadStarted) {
            // Back from the settings screen (or app resume): pick up a new default speed / tab visibility.
            viewModelScope.launch { applySettings(repository.settings(fileName)) }
            return
        }
        loadStarted = true
        viewModelScope.launch {
            try {
                loadSong()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _load.value = SongLoad.Failed(SongError.UNREADABLE)
            }
        }
    }

    private suspend fun loadSong() {
        applySettings(repository.settings(fileName))
        val result = repository.readSong(fileName)
        _load.value = when {
            result == null -> SongLoad.Failed(
                LibraryEvents.index.value?.entries?.firstOrNull { it.file == fileName }?.error
                    ?.takeIf { it == SongError.TOO_LARGE || it == SongError.UNREADABLE },
            )
            result.issue == ParseIssue.NOT_CHORDPRO -> SongLoad.Failed(SongError.NOT_CHORDPRO)
            result.issue == ParseIssue.EMPTY -> SongLoad.Failed(SongError.EMPTY)
            else -> SongLoad.Ready(result.song)
        }
        repository.voicings() // warm the cache (loaded on IO) so a chord tap is instant
    }

    /**
     * The saved speed is the song's default: what it opens with. Wheel / speed-panel changes only last for this
     * session. A changed default is applied when not playing; tab visibility always applies.
     */
    private fun applySettings(settings: SongSettings) {
        _showTabs.value = settings.showTabs
        val saved = ScrollSpeed.clamp(settings.speed)
        if (saved != appliedSavedSpeed && _playState.value != PlayState.Playing) {
            _speed.value = saved
            appliedSavedSpeed = saved
        }
    }

    fun hasTabs(): Boolean =
        (_load.value as? SongLoad.Ready)?.song?.sections?.any { it.kind == SectionKind.TAB } == true

    fun songCapo(): Int? = (_load.value as? SongLoad.Ready)?.song?.meta?.capo?.takeIf { it > 0 }

    /** True while a chord lookup that will navigate is in flight: further taps are ignored until it lands. */
    private var pendingNav = false

    /** Bumped by [onScreenHide]; a lookup that finishes after the screen was hidden drops its result. */
    private var hideCount = 0

    /**
     * Runs [lookup] against the voicing library and hands the result to [onReady] (which navigates) on the main
     * thread. When the library is already cached this is synchronous. Otherwise taps are ignored while chords.json
     * loads, and the result is dropped if the screen was hidden meanwhile (so a late result can't navigate twice
     * or from another screen).
     */
    private fun <T> navigateWithLibrary(lookup: (VoicingLibrary) -> T, onReady: (T) -> Unit) {
        if (pendingNav) return
        LibraryEvents.voicingCache?.let { onReady(lookup(it)); return }
        pendingNav = true
        val startedAt = hideCount
        viewModelScope.launch {
            try {
                val result = lookup(repository.voicings())
                if (hideCount == startedAt) onReady(result)
            } finally {
                pendingNav = false
            }
        }
    }

    /** Looks the shapes up (chords.json is read on IO the first time) and hands them to [onReady] on the main thread. */
    fun withShapes(chord: ChordName, onReady: (ChordShapes) -> Unit) {
        navigateWithLibrary({ it.shapesFor(chord) }, onReady)
    }

    /** Distinct chords of the song in order of first use, each with its shapes. */
    fun withSongChords(onReady: (List<ChordShapes>) -> Unit) {
        val song = (_load.value as? SongLoad.Ready)?.song ?: return
        val chords = song.allChords().distinctBy { it.canonicalName(false) }
        navigateWithLibrary({ lib -> chords.map { lib.shapesFor(it) } }, onReady)
    }

    fun songKey(): String? = (_load.value as? SongLoad.Ready)?.song?.meta?.key

    // ---- hardware keys ----------------------------------------------------------------------

    // A held wheel press must toggle once, so its repeat count is passed through; steps repeat per event.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        wheel.onKey(keyCode, WheelInput.ACTION_DOWN, if (keyCode in WheelKeys.PRESS) event.repeatCount else 0)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean =
        wheel.onKey(keyCode, WheelInput.ACTION_DOWN, repeatCount)

    // Swallow the matching UP so a stray UP isn't forwarded to LightOS.
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = wheel.onKey(keyCode, WheelInput.ACTION_UP, 0)

    // ---- play state -------------------------------------------------------------------------

    fun togglePlay() {
        when (_playState.value) {
            PlayState.Ready, PlayState.Paused -> _playState.value = PlayState.Playing
            PlayState.Playing -> _playState.value = PlayState.Paused
            PlayState.Ended -> viewModelScope.launch {
                // back to the top first, so the end check doesn't fire again straight away
                scrollState.scrollTo(0)
                _playState.value = PlayState.Playing
            }
        }
    }

    fun pause() {
        if (_playState.value == PlayState.Playing) _playState.value = PlayState.Paused
    }

    fun onReachedEnd() {
        if (_playState.value == PlayState.Playing) _playState.value = PlayState.Ended
    }

    // ---- speed ------------------------------------------------------------------------------

    fun changeSpeed(delta: Int) {
        val next = ScrollSpeed.clamp(_speed.value + delta)
        _speed.value = next
        _flashToken.value = _flashToken.value + 1
        // session only: the song's default speed is changed on its settings screen
    }

    fun openSpeedPanel() {
        _speedPanel.value = true
        _flashToken.value = _flashToken.value + 1
    }

    fun closeSpeedPanel() {
        _speedPanel.value = false
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        // Leaving for the chord sheet must not leave the song scrolling or resume on its own when we come back.
        hideCount++
        closeSpeedPanel()
        pause()
    }

    override fun onAppPause() {
        pause()
    }
}
