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
import com.christianlacerda.chords.model.Song
import com.christianlacerda.chords.ui.WheelInput
import com.christianlacerda.chords.ui.WheelKeys
import com.christianlacerda.chords.voicing.ChordShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(FlowPreview::class)
    private val saver = viewModelScope.launch(Dispatchers.IO) {
        saveRequests.debounce(400).collect { saveNow() }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        // onResume re-invokes this: only ever load once, never reset the play position.
        if (loadStarted) return
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
        val settings = repository.settings(fileName)
        _speed.value = ScrollSpeed.clamp(settings.speed)
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

    /** Looks the shapes up (chords.json is read on IO the first time) and hands them to [onReady] on the main thread. */
    fun withShapes(chord: ChordName, onReady: (ChordShapes) -> Unit) {
        viewModelScope.launch {
            val lib = repository.voicings()
            onReady(lib.shapesFor(chord))
        }
    }

    /** Distinct chords of the song in order of first use, each with its shapes. */
    fun withSongChords(onReady: (List<ChordShapes>) -> Unit) {
        val song = (_load.value as? SongLoad.Ready)?.song ?: return
        viewModelScope.launch {
            val lib = repository.voicings()
            onReady(song.allChords().distinctBy { it.canonicalName(false) }.map { lib.shapesFor(it) })
        }
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
            PlayState.Ended -> {
                viewModelScope.launch { scrollState.scrollTo(0) }
                _playState.value = PlayState.Paused
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
        saveRequests.tryEmit(Unit)
    }

    fun openSpeedPanel() {
        _speedPanel.value = true
        _flashToken.value = _flashToken.value + 1
    }

    fun closeSpeedPanel() {
        _speedPanel.value = false
    }

    private fun progress(): Float {
        val max = scrollState.maxValue
        return if (max > 0) (scrollState.value.toFloat() / max).coerceIn(0f, 1f) else 0f
    }

    private suspend fun saveNow() {
        try {
            saveSettingsNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // a failed settings write must never crash the song view
        }
    }

    private suspend fun saveSettingsNow() {
        repository.saveSettings(fileName, SongSettings(speed = _speed.value, transpose = 0, lastProgress = progress()))
    }

    private fun flush() {
        if (_load.value !is SongLoad.Ready) return
        // NonCancellable: the screen's scope may be cleared right after onScreenHide.
        viewModelScope.launch { withContext(NonCancellable) { saveNow() } }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        flush()
    }

    override fun onAppPause() {
        pause()
        flush()
    }
}
