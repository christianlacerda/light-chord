# Light Chord v1: architecture and build plan

## 0. Findings that change the ERD (read these first)

1. **The ERD's keep-awake method can't be built.** The ERD says to use `LocalView.current.keepScreenOn`, but the SDK plugin blocks it. `LightSdkPlugin.BLOCKED_IMPORTS` and `BLOCKED_CODE_PATTERNS` (in `plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt`) reject `LocalView`, `LocalContext`, `getSystemService` and `as Context`. The plugin scans every `.kt` under `tool/src`, tests included. `WAKE_LOCK` is on the allowlist, but it is useless because `PowerManager` needs `getSystemService`.
   - **Substitute:** Compose's `Modifier.keepScreenOn()` (`androidx.compose.ui`, Compose UI 1.8 and later; the BOM here is 2026.03.01). Put it on the Song screen's root. Only the current screen is composed (see `LightActivity.setContent`), so leaving the song view drops the flag on its own.
   - **Fallback** if that modifier isn't in this Compose version: a zero-size `AndroidView(factory = { c -> android.view.View(c).apply { keepScreenOn = true } })` in the Song screen. This passes the scanner because it never names `LocalView` or `Context`.
2. **`onToolManagerDataUpdate()` has no Context, so it can't rebuild the index.** `LightEntryPoint` hands the tool no context. The index rebuild therefore runs in screens:
   - `ToolEntryPoint.onToolManagerDataUpdate()` only bumps a process-wide `MutableStateFlow<Long>`, the same pattern as `examples/tool-manager-demo`.
   - The open screens collect that flow and call `repository.refresh()`.
   - `LightActivity.onResume()` calls `notifyWillShow()`, which reaches `viewModel.onScreenShow()`. So coming back from the phone's Tool Manager re-indexes anyway.
   - Re-indexing is incremental and cheap: list the directory, re-parse only files whose `lastModified` changed.
3. **"The SDK draws the back bar" is wrong.** `LightActivity` only renders `screen.Content()`. Every screen draws its own `LightTopBar(leftButton = LightBarButton.LightIcon(LightIcons.BACK) { goBack() })`. The hardware back button already calls `goBack()`.
4. **There is no asset listing API.** `SealedLightContext` only has `readAsset(path)`. Bundled songs therefore need an index file, `assets/samples/index.txt`.
5. **What the wheel sends is still unknown.** `LightDeviceKeys.mapping` lives in the closed `light-keyboard` artifact, which isn't cached here. The only per-screen API is `LightKeyHandler.onKeyDown/onKeyUp/onKeyMultiple`, routed `LightActivity` → screen → `viewModel`. The design below handles key events, rotary events and pointer-scroll events, plus an on-screen fallback.
6. **`tool-manager-provider` may not exist on v582.** light-habit's `scripts/upload-to-device.sh` says Christian's LP3 Tool Manager root offers only "Photos" and "Tool Inbox", which suggests an older Tool Manager. The tool still installs either way, because the provider is a plain exported ContentProvider. LightOS just won't list it. Graceful plan is in §6.
7. **Lines on screen.** The ERD says "2–3 lyric lines on screen", but the mockups show about 5 music lines (chord row + lyric row) at the Copy size, roughly 34 characters per line. **Follow the mockups.** A font-size setting is v1.1.
8. **The launcher label may not resolve.** `label = "@string/tool_name"` passes `TOOL_LABEL_PATTERN` and is written verbatim into `android:label`, so Android resolves it. light-habit used a literal label, and Light's dashboard or builder may show the raw string. If submission shows `@string/tool_name`, fall back to the literal `"Chords"`.
9. **Tool name mismatch.** Mockup 08 says "Open Light Chord › Songs", but the launcher label is "Chords"/"Cifras". **Decision:** use the same localized name everywhere. The Tool Manager title is "Chords"/"Cifras", picked with `Locale.getDefault()` because there is no context, and the Add songs string interpolates it.
10. **Use Akkurat Bold.** It is available on the device (`systemAkkuratFonts`). Chords use `FontWeight.Bold` on `LightThemeTokens.typography.*.fontFamily`.

## 1. Tool id, package and file list

- **`tool/lighttool.toml`:** `id = "com.christianlacerda.chords"` (light-habit used `com.christianlacerda.habits`), `label = "@string/tool_name"`, `versionCode = 1`, `versionName = "1.0.0"`, `permissions = []`, `capabilities = ["tool-manager-provider"]`, and `serverPackage = "com.lightos"` with the emulator line commented out. **No `orientation`.**
- **`tool/build.gradle.kts`:** leave it unchanged. `kotlinx-serialization-json` arrives through `sdk:client` → ktor-serialization (api), DataStore is api, and `testImplementation(libs.kotlin.test)` is already there.
- **Delete** `tool/src/main/kotlin/com/thelightphone/sample/*`.

All paths below are under `tool/src/main/kotlin/com/christianlacerda/chords/`.

```
ToolEntryPoint.kt        @EntryPoint object: TM manifest + onToolManagerDataUpdate -> LibraryEvents.filesChanged()
model/
  Song.kt                data class Song(meta: SongMeta, sections: List<Section>)
                         data class SongMeta(title: String, artist: String, key: String?, capo: Int?, subtitle: String?)
                         enum SectionKind { PLAIN, VERSE, CHORUS, BRIDGE, TAB }
                         data class Section(label: String?, kind: SectionKind, lines: List<Line>)
                         data class Line(segments: List<Segment>)   // empty segments = blank line
                         data class Segment(chordText: String?, chord: ChordName?, text: String)
                             // chordText = as written ("A7M"); chord null if unparseable/annotation
  ChordName.kt           data class ChordName(root: Int, quality: Quality, bass: Int?, raw: String,
                             preferFlats: Boolean, suffix: String /*normalized, for OTHER*/)
                         enum class Quality(val intervals: Set<Int>, val canonical: String, val fallback: Quality?)
  Voicing.kt             data class Voicing(frets: List<Int?>, fingers: List<Int>, baseFret: Int, barre: Barre?)
                         data class Barre(fret: Int, fromString: Int, toString: Int, finger: Int = 1)
chordpro/
  ChordProParser.kt      object ChordProParser { fun parse(text: String, fileName: String): ParseResult }
                         data class ParseResult(song: Song, issue: ParseIssue?)  // NOT_CHORDPRO, EMPTY
  ChordParser.kt         object ChordParser { fun parse(raw: String): ChordName? }
  Transposer.kt          object Transposer { fun transpose(c: ChordName, semis: Int, preferFlats: Boolean): ChordName;
                           fun noteName(pc: Int, flats: Boolean): String; fun prefersFlats(key: String?): Boolean }
  TextDecoding.kt        object TextDecoding { fun decode(bytes: ByteArray): String }  // strict UTF-8, else windows-1252
voicing/
  VoicingLibrary.kt      class VoicingLibrary(entries: List<ShapeEntry>) {
                           fun shapesFor(chord: ChordName): ChordShapes
                           companion object { fun fromJson(json: String): VoicingLibrary } }
                         data class ChordShapes(chord: ChordName, lookedUp: ChordName, voicings: List<Voicing>, approximate: Boolean)
                         @Serializable ShapeEntry/BarreDto (JSON DTOs)
  Fretboard.kt           val STANDARD_TUNING = intArrayOf(4, 9, 2, 7, 11, 4); fun soundedPitchClasses(v): Set<Int>
layout/
  LineWrapper.kt         object LineWrapper { fun wrap(segments: List<Segment>, maxWidth: Float,
                           lyricWidth: (String) -> Float, chordWidth: (String) -> Float, chordGap: Float): List<WrappedRow> }
                         data class WrappedRow(pieces: List<Piece>, hasChords: Boolean, hasLyrics: Boolean)
                         data class Piece(x: Float, chordText: String?, chord: ChordName?, text: String)
  SongLayout.kt          sealed interface RenderItem { Label(text, kind) ; Music(rows) ; Tab(text) ; Gap }
                         fun buildRenderItems(song, maxWidth, measurers): List<RenderItem>
  ScrollSpeed.kt         object ScrollSpeed { const MIN=1; MAX=10; DEFAULT=4
                           fun secondsPerLine(level: Int): Double; fun pxPerSecond(level, linePitchPx): Float }
library/
  LibraryModels.kt       @Serializable SongEntry, LibraryIndex, SongSettings, ArtistSummary
  LibraryIndexer.kt      object LibraryIndexer { fun reindex(old: LibraryIndex, files: List<FileStat>,
                           bundled: Set<String>, now: Long, parseHeader: (String) -> SongEntryHeader): LibraryIndex }  // pure
                         fun groupByArtist(index, collator): List<ArtistSummary>
  LibraryRepository.kt   class LibraryRepository(fileShare: LightFileShare, dataStore: DataStore<Preferences>,
                           readAsset: (String) -> ByteArray)
                           suspend fun refresh(); suspend fun seedSamplesIfNeeded(); suspend fun readSong(file): ParseResult?
                           suspend fun delete(file): Boolean; suspend fun settings(file): SongSettings
                           suspend fun saveSettings(file, s); fun voicings(): VoicingLibrary
  LibraryEvents.kt       object LibraryEvents { val filesChanged = MutableStateFlow(0L); val index = MutableStateFlow<LibraryIndex?>(null)
                           @Volatile var voicingCache: VoicingLibrary? }   // data only, no Context held
ui/
  ChordsTheme.kt         @Composable fun ChordsTheme(content) = LightTheme(colors = LightThemeController.colors.collectAsState().value)
  SongStyles.kt          @Composable lyricStyle(), chordStyle(), labelStyle(); LYRIC_DESIGN_PX=30f, CHORD_DESIGN_PX=22f
  ChordGridIconPainter.kt  class ChordGridPainter(color: Color): Painter  // mini 4x5 grid, theme-tinted (LightBarButton.Icon is not tinted)
  WheelInput.kt          class WheelInput(private val onStep: (Int) -> Unit) { fun onKey(keyCode: Int, action: Int, repeat: Int): Boolean;
                           fun onRotary(deltaPx: Float): Boolean }  + object WheelKeys
                         fun Modifier.wheelInput(w: WheelInput): Modifier   // rotary + pointer-scroll + focus
  artists/ArtistsScreen.kt     @InitialScreen ArtistsScreen + ArtistsViewModel
  songs/SongsScreen.kt         SongsScreen(sa, artist: String) + SongsViewModel (edit mode)
  songs/DeleteSongScreen.kt    DeleteSongScreen(sa, title: String): SimpleLightScreen<Boolean>
  song/SongScreen.kt           SongScreen(sa, fileName: String) + content
  song/SongViewModel.kt        holds ScrollState, PlayState, speed, flash token
  song/SongBody.kt             renders RenderItems; MusicRow custom Layout; dotted underline
  song/SongBottomBar.kt        progress line + state row + speed control
  song/SpeedOverlay.kt
  chords/ChordDiagram.kt       @Composable ChordDiagram(voicing: Voicing, modifier, compact: Boolean)
  chords/ChordDiagramScreen.kt ChordDiagramScreen(sa, songTitle: String, shapes: ChordShapes)
  chords/SongChordsScreen.kt   SongChordsScreen(sa, title: String, key: String?, chords: List<ChordShapes>)
  add/AddSongsScreen.kt        AddSongsScreen + AddSongsViewModel (recent-upload count)
```

**Resources and assets**
- `res/values/strings.xml`, `res/values-pt/strings.xml`
- `assets/chords.json`
- `assets/samples/index.txt` plus the `assets/samples/*.cho` files

**How ViewModels get their dependencies.** Each screen builds its VM in `createViewModel()` from `lightContext`: `LibraryRepository(lightContext.fileShare, lightContext.dataStore, lightContext::readAsset)`. The repository objects are created per VM. Only plain data is cached statically, in `LibraryEvents`. Holding `SealedLightContext` statically would leak the Activity.

**Navigation arguments** go through the constructor: `navigateTo({ SongScreen(it, entry.file) })`. Child screens receive already-computed data (`ChordShapes`, title).

## 2. Wheel handling

**`WheelKeys`** is a single place to edit after the v0 probe:
- `UP = setOf(KEYCODE_DPAD_UP, KEYCODE_SYSTEM_NAVIGATION_UP, KEYCODE_NAVIGATE_PREVIOUS, KEYCODE_PAGE_UP)`
- `DOWN = setOf(KEYCODE_DPAD_DOWN, KEYCODE_SYSTEM_NAVIGATION_DOWN, KEYCODE_NAVIGATE_NEXT, KEYCODE_PAGE_DOWN)`
- The volume keys are deliberately **not** included. If the probe shows the wheel sends `KEYCODE_VOLUME_*`, add them here. They would then be consumed only on the screens that use the wheel.

**`WheelInput.onKey`**
- Acts on `ACTION_DOWN` only: one step, plus `repeatCount` steps for `onKeyMultiple`.
- Returns `true` (consumed) only for keys in those sets. Everything else falls through to LightOS through `LightDeviceKeys` forwarding.
- Takes plain ints so it can be JVM-tested. The `KEYCODE_*` constants are compile-time ints.

**Wiring on the key path**
- `SongViewModel`, `ChordDiagramViewModel` and `SongChordsViewModel` override `onKeyDown` and `onKeyMultiple` → `wheel.onKey(...)`.
- `onKeyUp` returns `true` for the same key codes, so a stray UP isn't forwarded either.
- Artists and Songs screens don't consume anything.

**Rotary and pointer path: `Modifier.wheelInput(w)` on the screen root**
- `focusRequester` + `focusable()`, with `LaunchedEffect(Unit) { requester.requestFocus() }`.
- `onRotaryScrollEvent { w.onRotary(it.verticalScrollPixels) }`.
- `pointerInput` reading `PointerEventType.Scroll` in `PointerEventPass.Initial`, consuming it so `verticalScroll` doesn't also move the text. This makes a mouse wheel on the emulator change speed.
- `onRotary` accumulates pixels and fires one step per `ROTARY_STEP_PX = 48f` (tune on the device). A `const val INVERT = false` flips the direction.
- Direction: up / away from the user = faster.

**Fallback if the wheel never reaches the app** (LightOS may consume it system-wide):
- The "◯ Speed N" bottom-bar item is tappable and opens the same overlay with large − / + tap zones on its left and right halves.
- This also makes the emulator testable without a wheel.

**Speed overlay.** It is drawn inside the Song screen, not through `LightModalManager`, because that manager is global and takes over the whole screen.
- The VM bumps `flashToken: StateFlow<Int>` on every speed change.
- `SpeedOverlay` = `AnimatedVisibility(fade)`, with `LaunchedEffect(flashToken) { visible = true; delay(1000); visible = false }`.
- **Box:** 12u × 11.5u, centered, background colour, 1px content-colour border, rounded corners (0.8u).
- **"SPEED":** Detail variant, lightened, uppercase.
- **Number:** `64f.designVerticalPxToSp()`, Bold.
- **Meter:** 10 bars with height `0.6u + i*0.12u`. Filled bars use the content colour; empty bars use `contentSecondary` at 0.35 alpha.
- The bottom-bar "Speed N" updates at the same time.

**Persistence.** On each step, `speed = (speed + step).coerceIn(1, 10)`. The change is saved through a conflated save job: `MutableStateFlow` + `debounce(400)` collected on IO. It is also flushed in `onScreenHide` and `onAppPause`.

## 3. Auto-scroll and rendering

**Container: `Column(Modifier.verticalScroll(vm.scrollState))`, not LazyColumn.**
- A song is about 30–100 rows, so composing all of it is cheap.
- `ScrollState` gives an exact `maxValue` for the progress bar and for end detection.
- Dragging for manual correction comes free.
- `ScrollState` lives in the **ViewModel** (`val scrollState = ScrollState(0)`). When you open the chord diagram, SongScreen leaves composition but its VM stays on the back stack, so Back returns to the same spot.

**Frame loop** (in `SongScreen.Content`):

```kotlin
LaunchedEffect(playState == Playing, pxPerSec) {
  if (playState != Playing) return@LaunchedEffect
  var last = withFrameNanos { it }
  while (isActive) withFrameNanos { now ->
    val dt = (now - last) / 1e9f; last = now
    if (!scrollState.isScrollInProgress) {           // user dragging = manual correction
      val consumed = scrollState.dispatchRawDelta(pxPerSec * dt)   // keeps sub-px remainder
      if (scrollState.value >= scrollState.maxValue) vm.onReachedEnd()
    }
  }
}
```

**Speed curve** (`ScrollSpeed`, geometric so each step feels the same size):
- `secondsPerLine(l) = 12.0 * (1.0/6.0).pow((l-1)/9.0)`, giving 12.0, 9.7, 7.8, 6.3, 5.1, 4.1, 3.3, 2.7, 2.2 and 2.0 s per music line.
- `pxPerSecond = linePitchPx / secondsPerLine`.
- `linePitchPx` = chord line height + lyric line height + row gap, computed from the actual text styles with `LocalDensity`, roughly 140 px. That gives about 12–70 px/s, around 22 px/s at the default level 4.
- Defining speed in lines per second keeps it correct under a future font-size setting.

**Play states:** `enum PlayState { Ready, Playing, Paused, Ended }`.
- A song opens in `Ready`, at the top, displayed exactly like Paused (dimmed, chords tappable).
- Tapping the lyrics (a `lightClickable` on the content Column) toggles Ready/Paused ↔ Playing.
- Tapping in `Ended` scrolls to 0 and goes to `Paused`.
- `onAppPause` → `Paused` (this also fires on screen-off).
- `onScreenShow` must **not** reset or re-parse. Only load when `song == null`, because `onResume` re-invokes it.

**End and progress**
- A bottom content padding of 0.55 × viewport height lets the last line climb into the upper reading area. End is when `value == maxValue`.
- `progress = value / maxValue.coerceAtLeast(1)`, drawn with `LightProgressBar(colors, progress)` above the state row.
- Optional polish: a top fade, using `graphicsLayer(compositingStrategy = Offscreen)` + `drawWithContent` with a `DstIn` vertical gradient over the first 1.5u.

**Keep awake:** `Modifier.keepScreenOn()` on the SongScreen root Box (see §0.1).

**Wrapping: a pure measured layout, not FlowRow.** FlowRow of chord-over-text columns would break mid-word wherever a chord sits inside a word (`a[G]maz[D]ing`), and can't split a long segment. `LineWrapper` is pure Kotlin and testable with a fake measurer:
1. Tokenise each segment's text into atoms at whitespace, with trailing spaces attached (`"grace, "`). Only the segment's first atom carries its chord.
2. Glue consecutive atoms into unbreakable *words* when the previous atom doesn't end in whitespace.
3. Atom width = `max(lyricWidth(text), chord? chordWidth(chordText) + chordGap : 0)`. The lyric is padded so chords never collide; `chordGap` = 0.4u.
4. Fill rows greedily. A word goes to a new row if `x + width(word without trailing space) > maxWidth` and the row isn't empty. A word wider than a whole row is split at atom boundaries, then by characters as a last resort. Leading whitespace on continuation rows is dropped.
5. Rows record `hasChords` and `hasLyrics`. A chord-only row (`[G] [D]`) skips the lyric line height, and a lyric-only row skips the chord line height.

**Rendering a row: `MusicRow` custom `Layout`**
- Each piece's chord `Text` sits at `(x, 0)` and its lyric `Text` at `(x, chordLineHeight)`.
- Measuring: `rememberTextMeasurer()` with `remember(song, widthPx, styles)` around `buildRenderItems`, and a `HashMap` memo inside the measure lambdas. Width comes from `BoxWithConstraints`, minus 1.5u horizontal padding on each side.
- Lyric colour: `content` while Playing, `contentSecondary` otherwise. Chords always use `content`.
- When not Playing and `chord != null`, the chord gets a dotted underline (`drawBehind` + `PathEffect.dashPathEffect(floatArrayOf(2dp, 2dp))`) and `lightClickable { openDiagram(chord) }`. It also gets ±0.4u invisible hit slop: measure it wider and place it offset left by the slop.
- Taps on a chord consume the event, so the parent's pause toggle doesn't fire.

**Styles** (built on `LightThemeTokens.typography.copy`, because `scaledForScreenHeight` is internal):
- Lyrics: `fontSize = 30f.designVerticalPxToSp()`, `lineHeight = 36f.designVerticalPxToSp()`. That is about 62 px on the LP3, about 34 characters per line.
- Chords: `22f` design px, `FontWeight.Bold`.
- Labels: `LightTextVariant.Detail`, lightened, `uppercase()`, matching "VERSE 2".
- Tab sections: `LightText(monospace = true, variant = Superfine)`, no wrapping, clipped horizontally, about 50 columns.

## 4. ChordPro parser and chord names

**`ChordProParser`** (pure Kotlin)
- **Input cleanup:** strip the BOM; normalise `\r\n` and `\r`; drop lines starting with `#`.
- **Directive regex:** `^\s*\{\s*([A-Za-z_]+)\s*(?:[:\s]\s*(.*?))?\s*\}\s*$`, case-insensitive name.
- **Meta directives:** `title`/`t`, `subtitle`/`st`, `artist`, `key`, `capo` (toIntOrNull), and `meta: artist X` / `meta: key X`.
- **Comments:** `comment`/`c`/`comment_italic`/`ci`/`comment_box`/`cb`/`highlight`. If the current section has lines, start a new section labelled with the comment; otherwise set the current section's label.
- **`start_of_verse`/`sov`, `start_of_chorus`/`soc`, `start_of_bridge`/`sob`:** open a new section of that kind. The label is the directive argument or null; the UI localizes null-label kinds ("Chorus"/"Refrão").
- **`end_of_*`/`eo*`:** close the section; the next lines go into a new PLAIN section.
- **`start_of_tab`/`sot` … `eot`:** a TAB section with raw lines kept verbatim (no chord parsing).
- **`{chorus}`:** inserts a copy of the most recent CHORUS section, or a label-only one if there was none.
- **Unknown directives** are ignored.
- **Lyric lines:** split on `\[([^\]]*)\]`. Text before the first chord becomes `Segment(null, null, text)`. `[*text]` is an annotation: `chordText = text`, `chord = null`. Otherwise `chord = ChordParser.parse(inner)`, which is null if unparseable; the text is still shown but not tappable.
- **Blank lines:** a blank lyric line becomes `Line(emptyList())`; runs of blanks collapse into one. Empty sections are dropped.
- **Fallbacks:** title → file name without extension; artist → null. The UI shows "Unknown artist"/"Artista desconhecido"; the parser stays locale-free.
- **Issues:** `NOT_CHORDPRO` if the file has no directive and no `[chord]` anywhere; `EMPTY` if it has no content. Such files are still indexed, with an error flag (§9).

**`ChordParser`** grammar, as implemented:
- Strip spaces. Root is `[A-G]`, accidental is `#|b|♯|♭`. The rest is the suffix.
- **Slash bass:** split at the last `/` only if what follows is a note (`[A-G][#b♯♭]?`). So `6/9` and `7/9` stay inside the suffix.
- **Suffix normalisation:**
  - lowercase except `M`;
  - `º°o` → `dim`; `ø` → `m7b5`; `Δ` → `maj7`; `+` alone → `aug`;
  - `-` at the start → `m`, unless it means a lowered 5th (`(5-)`, `-5` → `b5`);
  - remove parentheses, so `m7(5-)` → `m7b5`, `7(9)` → `9`, `7M(9)` → `maj9`, `(add9)` → `add9`;
  - `7M`/`M7`/`maj` → `maj7`; `m7M`/`mM7`/`m(maj7)` → `mmaj7`;
  - `sus` → `sus4`; a bare `4` → `sus4` (Brazilian usage, e.g. `D4`); `7(4)`/`74` → `7sus4`; a bare `2` → `add9` (Brazilian `A2`); `(9)` on a triad → `add9`.
- **Quality enum** (intervals from the root):

  | Quality | Intervals |
  |---|---|
  | MAJOR | {0,4,7} |
  | MINOR | {0,3,7} |
  | DIM | {0,3,6} |
  | AUG | {0,4,8} |
  | SUS2 | {0,2,7} |
  | SUS4 | {0,5,7} |
  | SIX | +9 |
  | MIN6 | |
  | DOM7 | {0,4,7,10} |
  | MAJ7 | {0,4,7,11} |
  | MIN7 | |
  | MIN_MAJ7 | |
  | HALF_DIM7 | {0,3,6,10} |
  | DIM7 | {0,3,6,9} |
  | SEVEN_SUS4 | |
  | ADD9 | {0,2,4,7} |
  | MIN_ADD9 | |
  | DOM9 | |
  | MAJ9 | |
  | MIN9 | |
  | OTHER | raw suffix |

  The parser maps `6/9` → SIX, `11` → SEVEN_SUS4, `13` → DOM7 and `7#9`/`7b9` → DOM7. These are lossy mappings, tagged so the lookup reports "closest".
  For OTHER, a minor/major guess (a leading `m` not followed by `aj`) is kept for the fallback.
- **Spelling:** `preferFlats = raw uses b/♭`.
- **Display:** v1 always shows `raw`, so the user's own notation like "A7M" survives. `ChordName.canonicalName(flats)` is used for transposed output and for the voicing key.

**`Transposer`**
- `root' = (root + semis).mod(12)`; `bass'` likewise.
- `prefersFlats(key)` is true for the keys F, Bb, Eb, Ab, Db, Gb, Dm, Gm, Cm, Fm, Bbm, Ebm.
- Note names: sharps `C C# D D# E F F# G G# A A# B`, flats `C Db D Eb E F Gb G Ab A Bb B`.
- No UI in v1. The same maths shifts movable voicings and is unit-tested.

## 5. Voicing table

**`tool/src/main/assets/chords.json`:**

```json
{ "version": 1,
  "shapes": [
    {"chord":"C",   "frets":[null,3,2,0,1,0], "fingers":[0,3,2,0,1,0]},
    {"chord":"D/F#","frets":[2,null,0,2,3,2], "fingers":[1,0,0,2,4,3]},
    {"chord":"F",   "movable":true, "frets":[1,3,3,2,1,1], "fingers":[1,3,4,2,1,1],
     "barre":{"fret":1,"from":0,"to":5}}
  ] }
```

- `chord` is parsed with the same `ChordParser`, so there is one code path. Strings are indexed 0 = low E … 5 = high e; `null` = muted, `0` = open.
- **Movable entries** are stored at their reference root with no open strings. To shift to a target: `delta = (target - refRoot).mod(12)`, add `delta` to every non-null fret and to `barre.fret`, keep the fingers.

**Starter content.** The coder must hand-check these; the test in §10 verifies the notes automatically.

*Open shapes:*

| Chord | Frets | Fingers |
|---|---|---|
| C | x32010 | 032010 |
| C7 | x32310 | 032410 |
| Cmaj7 | x32000 | 032000 |
| Cadd9 | x32030 | 021030 |
| C/E | 032010 | 032010 |
| D | xx0232 | 000132 |
| Dm | xx0231 | 000231 |
| D7 | xx0212 | 000213 |
| Dm7 | xx0211 | 000211 (barre 1, strings 4–5) |
| Dmaj7 | xx0222 | 000111 (barre 2, strings 3–5) |
| Dsus4 | xx0233 | 000123 |
| Dsus2 | xx0230 | 000130 |
| D/F# | as above | |
| E | 022100 | 023100 |
| Em | 022000 | 023000 |
| E7 | 020100 | 020100 |
| Em7 | 020000 | 020000 |
| Esus4 | 022200 | 023400 |
| Eaug | 032110 | 032110 |
| Fmaj7 | xx3210 | 003210 |
| G | 320003 | 210003 (matches mockup) |
| G7 | 320001 | 320001 (mockup) |
| Gmaj7 | 320002 | 320001 |
| G/B | x20003 | 010003 |
| A | x02220 | 001230 |
| Am | x02210 | 002310 |
| A7 | x02020 | 002030 |
| Am7 | x02010 | 002010 |
| Amaj7 | x02120 | 002130 |
| Asus4 | x02230 | 001230 |
| Asus2 | x02200 | 001200 |
| Aadd9 | x02420 | 001420 |
| B7 | x21202 | 021304 |
| Caug | x32110 | 043120 |

*Movable E-shape* (reference root F, root on the low E string, barre at fret 1 unless noted):

| Chord | Frets | Fingers | Barre |
|---|---|---|---|
| F | [1,3,3,2,1,1] | [1,3,4,2,1,1] | 0–5 |
| Fm | [1,3,3,1,1,1] | [1,3,4,1,1,1] | 0–5 |
| F7 | [1,3,1,2,1,1] | [1,3,1,2,1,1] | 0–5 |
| Fm7 | [1,3,1,1,1,1] | [1,3,1,1,1,1] | 0–5 |
| Fmaj7 | [1,x,2,2,1,x] | [1,0,3,4,2,0] | none |
| Fsus4 | [1,3,3,3,1,1] | [1,2,3,4,1,1] | 0–5 |
| F7sus4 | [1,3,1,3,1,1] | [1,3,1,4,1,1] | 0–5 |

*Movable A-shape* (reference root Bb, root on the A string):

| Chord | Frets | Fingers | Barre |
|---|---|---|---|
| Bb | [x,1,3,3,3,1] | [0,1,2,3,4,1] | 1–5 |
| Bbm | [x,1,3,3,2,1] | [0,1,3,4,2,1] | 1–5 |
| Bb7 | [x,1,3,1,3,1] | [0,1,3,1,4,1] | 1–5 |
| Bbm7 | [x,1,3,1,2,1] | [0,1,3,1,2,1] | 1–5 |
| Bbmaj7 | [x,1,3,2,3,1] | [0,1,3,2,4,1] | 1–5 |
| Bbsus4 | [x,1,3,3,4,1] | [0,1,2,3,4,1] | 1–5 |
| Bbsus2 | [x,1,3,3,1,1] | [0,1,3,4,1,1] | 1–5 |
| Bb7sus4 | [x,1,3,1,4,1] | [0,1,3,1,4,1] | 1–5 |
| Bbm7b5 | [x,1,2,1,2,x] | [0,1,3,2,4,0] | none |
| Bbdim | [x,1,2,3,2,x] | [0,1,2,4,3,0] | none |
| Bbaug | [x,1,4,3,3,x] | [0,1,4,2,3,0] | none |
| Bb6 | [x,1,3,3,3,3] | [0,1,3,3,3,3] | fret 3, strings 2–5 |

*Other movable shapes:*

| Chord | Frets | Fingers | Barre |
|---|---|---|---|
| C9 | [x,3,2,3,3,3] | [0,2,1,3,3,3] | fret 3, strings 3–5 |
| Cm9 | [x,3,1,3,3,3] | [0,2,1,3,3,3] | fret 3, strings 3–5 |
| Cmaj9 | [x,3,2,4,3,x] | [0,2,1,4,3,0] | none |
| D#dim7 | [x,x,1,2,1,2] | [0,0,1,3,2,4] | none |

**Not covered by any shape** (lookup falls back): MIN6, MIN_MAJ7, MIN_ADD9, add9 on most roots.

**Lookup: `shapesFor(chord)`**
1. **Exact.** Collect open entries with the same (root, quality, bass), in JSON order. If `bass != null` and nothing matched, remember the bass and continue without it.
2. **Movable.** Shift every movable entry of that quality to the root. Keep it only if the shifted `baseFret <= 9` (G7 then gives open + E-shape at 3, "Shape 1 of 2" as in the mockup).
3. **Order and dedupe.** Open shapes first, then movable shapes by ascending base fret. Remove duplicates by `frets`.
4. **Fallback when empty.** Walk `quality.fallback` and repeat steps 1–2 with `approximate = true`. Chains:
   - MAJ9 → MAJ7 → MAJOR
   - DOM9 → DOM7 → MAJOR
   - MIN9 → MIN7 → MINOR
   - MIN_ADD9 → MINOR; ADD9 → MAJOR
   - MIN_MAJ7 → MINOR
   - SIX → MAJOR; MIN6 → MINOR
   - SEVEN_SUS4 → SUS4 → MAJOR; SUS2 → MAJOR
   - DIM7 → DIM → MINOR
   - HALF_DIM7 → DIM
   - AUG → MAJOR
   - OTHER → MINOR or MAJOR from its minor flag
5. **Result.** `ChordShapes(chord, lookedUp, voicings, approximate)`. When `bass` was dropped, the UI shows "F# in the bass" and `approximate = false`.

**`baseFret`:** `maxFret <= 5` → 1, else the lowest fretted (>0) fret. Computed after shifting.

**Diagram Canvas** (`ChordDiagram`, unit `s` = string spacing)
- **Fit:** `s = min(W / 7.4, H / 7.3)`; grid 5s wide × 5 frets of `f = 1.2s`. Left margin 1.2s for the fret label, top margin 1.0s for markers; centre horizontally.
- **Strings:** 6 vertical lines at `left + i*s`.
- **Frets:** 6 horizontal lines at `top + j*f`. Stroke = `max(2px, 0.05s)`, colour `content`.
- **Nut:** when `baseFret == 1`, a filled rect of height `0.18s` at `top`.
- **Fret label:** when `baseFret > 1`, `"${baseFret}fr"` right-aligned at `left - 0.35s`, centred on row 0. Use `drawText` with a `rememberTextMeasurer`, about 0.5s, colour `content`.
- **Above the nut** (centre y = `top - 0.55s`): open = ring of radius 0.25s with a `content` stroke; muted = × of half-size 0.18s in `contentSecondary`.
- **Dots:** for fret `n > 0`, row `r = n - baseFret`, centre `(x_i, top + (r + 0.5) f)`, radius `0.42s`, fill `content`. The finger number is drawn centred in the `background` colour, Bold, `0.5s`. Skip strings drawn by the barre.
- **Barre:** a round rect from `x_from - 0.42s` to `x_to + 0.42s`, height `0.84s`, fill `content`. Draw finger "1" at its left end.
- **Compact mode** (the 4-up sheet) uses the same drawing at a smaller size, with dot numerals at minimum 0.45s.

## 6. Tool Manager integration

```kotlin
@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    const val SONGS_DIR = "songs"
    override fun getToolManagerManifest() = ClientToolManifest(
        title = ToolText.toolName(),                       // "Chords" / "Cifras" via Locale.getDefault()
        roots = listOf(ClientLeafNode(FileBrowserSpec(
            label = ToolText.songsLabel(),                 // "Songs" / "Músicas"
            path = SONGS_DIR,
            headerText = ToolText.songsHeader(),           // "ChordPro files (.cho, .chopro, .txt). Songs are listed by artist."
        ))))
    override suspend fun onToolManagerDataUpdate() {
        super.onToolManagerDataUpdate()
        LibraryEvents.filesChanged.value = System.currentTimeMillis()
    }
}
```

`ToolText` is a tiny Kotlin object choosing EN/PT by `Locale.getDefault().language == "pt"`. Resources aren't reachable without a context.

**Where files land.** Uploads go to `filesDir/shared/songs/` (`LightFileProvider.SHARED_DIR` + path). The tool reads them through `lightContext.fileShare.list("songs")`, `.read("songs/x.cho", ISO_8859_1) { it.readText() }` and `.delete(...)`.
- Read as ISO-8859-1 to get the bytes back losslessly, then `TextDecoding.decode(text.toByteArray(ISO_8859_1))`. This handles Latin-1 Brazilian files.
- The repository calls `fileShare.write("songs/.keep")`-free `mkdirs` implicitly: `LightFileShare.write` makes parent directories, and seeding creates the directory.

**Re-index triggers**
- `ArtistsViewModel.onScreenShow`, `SongsViewModel.onScreenShow` (app start and every resume, including back from the phone's Tool Manager).
- `LibraryEvents.filesChanged` collected in those VMs' `init`.
- After an on-phone delete.

**What `refresh()` does**
1. Seed samples if needed (§8).
2. List files; ignore directories, hidden or `._` files, and extensions other than `cho`/`chopro`/`chordpro`/`txt`.
3. Build `FileStat(name, lastModified)`.
4. Call `LibraryIndexer.reindex`, which reuses entries with the same name and mtime and parses only the rest. A file over 512 KB gets an error entry.
5. Prune settings of vanished files.
6. Persist and publish to `LibraryEvents.index`.
- Runs on `Dispatchers.IO`, serialized with a `Mutex`.

**Delete**
- **On the phone:** the Songs-by-artist top bar has a right-side `LightBarButton.Text(Edit/Editar)`. In edit mode each row shows a `LightIcons.TRASH`. Tapping it navigates to `DeleteSongScreen` (Delete/Cancel bottom bar, as in light-habit's `HabitDeleteConfirmation`), which returns a Boolean result. Then `repository.delete(file)` → `fileShare.delete("songs/$file")` → refresh. If the artist has no songs left, `goBack()`.
- **From the computer:** the Tool Manager file browser. Its delete control is unverified, but any change fires `onToolManagerDataUpdate` and the resume path covers it.
- **Deleting a bundled sample** is allowed. It is recorded in `seeded_files`, so it is never re-seeded.

**If v582 lacks `tool-manager-provider`**
- The app works unchanged with the bundled samples.
- The Add songs screen carries a line: "Don't see Chords in Tool Manager? Your LightOS may need an update."
- **Developer fallback:** put `.cho` files into `tool/src/main/assets/samples/`, add them to `index.txt`, bump `versionCode`, and reinstall with `scripts/upload-to-device.sh` (Tool Inbox). Per-file seeding copies the new ones in.
- The real fix is pulling QR import (v1.1) forward. Don't add any other transport in v1.
- There's no reliable way to detect the capability from the tool, so don't try.

## 7. Storage (DataStore `lightContext.dataStore`, JSON-in-Preferences like light-habit)

| Key | Type | Content |
|---|---|---|
| `library_index_v1` | string | `{"entries":[{"file":"amazing-grace.cho","modified":1760000000000,"firstSeen":1760000000000,"bundled":true,"title":"Amazing Grace","artist":"Traditional","key":"G","capo":null,"error":null}]}` |
| `song_settings_v1` | string | `{"amazing-grace.cho":{"speed":4,"transpose":0,"lastProgress":0.0}}` |
| `seeded_files` | stringSet | bundled file names already copied |

- **Identity:** keyed by **file name**, which is unique in the directory and stable across re-uploads, so the speed survives editing a song. `modified` is the change detector; `LightFileShare.LightFile` exposes no size and none is needed.
- **`error`:** one of `"not_chordpro"`, `"empty"`, `"too_large"`, `"unreadable"`, or null.
- **`lastProgress`:** a 0..1 fraction (font-independent, unlike pixel offsets). It is written on leaving the song but **not** used to restore, because the spec opens songs at the top. It replaces the ERD's `lastOffset`.
- **`transpose`:** stored as 0 with no UI.
- **`json`:** `Json { ignoreUnknownKeys = true }`. A corrupted blob falls back to empty plus a full re-scan.
- **"N songs added from Tool Manager today":** count of entries with `!bundled && firstSeen >= startOfToday`.

## 8. Sample songs (public domain; use traditional texts only, not later copyrighted arrangements)

Files go in `assets/samples/`, listed in `assets/samples/index.txt`, all with `{artist: Traditional}`:

| File | Song | Key | Chords | Exercises |
|---|---|---|---|---|
| `amazing-grace.cho` | Amazing Grace (Newton 1779) | G | G G7 C D Em | Matches the mockup; verses 1–3 with `{comment: Verse 2}` labels |
| `home-on-the-range.cho` | Home on the Range (Higley/Kelley 1872 text; replaced Scarborough Fair, whose common wording is a copyrighted arrangement) | G | G C D7 | `{capo: 2}`, a short `{sot}` riff block |
| `wayfaring-stranger.cho` | Wayfaring Stranger (1858 hymnal) | Dm | Dm Gm A7 F C Bb | `{soc}`/`{eoc}` and a `{chorus}` repeat |
| `shenandoah.cho` | Shenandoah (traditional, 19th c.) | D | D G Bm A A7 D/F# Em | The slash chord path |
| `peixe-vivo.cho` | Peixe Vivo (Brazilian folk) | C | C C7M Dm7 G7 A7 E7 Am | Brazilian names (`C7M`), `{comment: Refrão}` |

- **Replaces House of the Rising Sun** from the mockup. Its familiar lyrics come via Lomax / the Animals, and "Asa Branca" is under copyright.
- **Seeding:** `seedSamplesIfNeeded()` reads `index.txt`. For each name not in `seeded_files` it copies the asset with `fileShare.write("songs/$name")`, then adds the name. Entries for these files get `bundled = true` via the seeded set.
- **Why copy instead of reading straight from assets:** one code path, the files show up and can be downloaded in Tool Manager, and delete works the same way.

## 9. Screens and strings

**Screens** (horizontal content padding 1.5u everywhere; top bar is `LightTopBar`)

| Screen | Contents |
|---|---|
| **ArtistsScreen** (`@InitialScreen`) | Center "Artists"; right `LightIcons.ADD` → AddSongs. `LightScrollView` of rows: artist name (Heading, single line, ellipsis) and count right-aligned (Detail, lightened). Sorted with `Collator(Locale.getDefault()).apply { strength = PRIMARY }`; "Unknown artist" last. Empty state: "No songs yet" + hint. Before the first load, show nothing (no empty flash, a light-habit lesson). |
| **SongsScreen(artist)** | Back + artist name; right Edit/Done. Rows: title (Heading) and a detail line joined with " · ": `Key G`, `capo 2` if > 0, `Speed 4` if settings exist, or the error text. In edit mode a trash icon is on the right. |
| **SongScreen(file)** | Back, title, right `LightBarButton.Icon(ChordGridPainter(content))` → SongChordsScreen. Body as in §3. Bottom area (3.5u): `LightProgressBar` line, then a Row with PLAY/PAUSE icon (1.2u) + state text (Fine) and, on the right, `LightIcons.CIRCLE` (1u) + "Speed N" (Fine, Bold, tappable fallback). Plus SpeedOverlay. If the file has an error or is missing: centred message + Back. |
| **ChordDiagramScreen(songTitle, shapes)** | Back + song title. Chord name (`64f.designVerticalPxToSp()` Bold, centred), diagram about 12u × 13u, optional "Closest shape: X" / "F# in the bass" (Detail, lightened). Bottom: ‹ (`LightIcons.BACK`) and › (BACK mirrored with `graphicsLayer(scaleX = -1f)`), arrows at 0.3 alpha when disabled. Centre: "Shape 1 of 2" (Fine) + the fret string "3 2 0 0 0 1", muted as "x" (Detail, lightened, letter-spaced). The wheel cycles shapes. No shapes: "No diagram for this chord yet". |
| **SongChordsScreen** | Back + "Chords in this song". Subtitle "Amazing Grace · Key G · 5 chords" (Detail, lightened). 2 × 2 grid of compact diagrams with the name above (Subheading Bold). Bottom pager "1 / 2" with ‹ ›; the wheel pages. Tapping a diagram opens ChordDiagramScreen. Chords are distinct by `canonicalName` in first-use order, each in its easiest (first) shape. |
| **AddSongsScreen** | Back + "Add songs". "From your computer" (Heading). 3 numbered steps (number Bold + Copy text). Note (Detail, lightened). The "missing in Tool Manager" line. Footer above a divider, shown only if N > 0: "N songs added from Tool Manager today". |
| **DeleteSongScreen(title)** | Centred question + body; `LightBottomBar` with Cancel / Delete text items. |

**Strings** (`values/strings.xml` | `values-pt/strings.xml`)

| Name | EN | PT |
|---|---|---|
| tool_name | Chords | Cifras |
| artists_title | Artists | Artistas |
| unknown_artist | Unknown artist | Artista desconhecido |
| library_empty_title | No songs yet | Nenhuma música ainda |
| library_empty_body | Tap + to see how to add songs from your computer. | Toque em + para ver como adicionar músicas pelo computador. |
| detail_key | Key %1$s | Tom %1$s |
| detail_capo | capo %1$d | capo %1$d |
| detail_speed | Speed %1$d | Velocidade %1$d |
| edit / done | Edit / Done | Editar / OK |
| error_not_chordpro | Not a ChordPro file | Não é um arquivo ChordPro |
| error_empty | Empty file | Arquivo vazio |
| error_too_large | File too large | Arquivo grande demais |
| error_unreadable | Couldn't read this file | Não foi possível ler o arquivo |
| song_missing | This song is no longer on the phone | Esta música não está mais no telefone |
| state_ready | Tap the lyrics to start | Toque na letra para começar |
| state_scrolling | Scrolling | Rolando |
| state_paused | Paused · tap a chord to see it | Pausado · toque num acorde para vê-lo |
| state_end | End | Fim |
| speed_short | Speed %1$d | Vel. %1$d |
| speed_label | SPEED | VELOCIDADE |
| section_verse | Verse | Verso |
| section_chorus | Chorus | Refrão |
| section_bridge | Bridge | Ponte |
| section_tab | Tab | Tablatura |
| chords_in_song | Chords in this song | Acordes da música |
| plurals chord_count | %d chord / %d chords | %d acorde / %d acordes |
| page_of | %1$d / %2$d | (same) |
| shape_of | Shape %1$d of %2$d | Forma %1$d de %2$d |
| no_shape | No diagram for this chord yet | Ainda não há diagrama para este acorde |
| closest_shape | Closest shape: %1$s | Forma mais próxima: %1$s |
| bass_note | %1$s in the bass | %1$s no baixo |
| add_title | Add songs | Adicionar músicas |
| add_from_computer | From your computer | Pelo computador |
| add_step1 | Open Tool Manager on this phone | Abra o Tool Manager neste telefone |
| add_step2 | Go to the link it shows, on your computer | No computador, acesse o link que ele mostra |
| add_step3 | Open %1$s › %2$s and drop your ChordPro files | Abra %1$s › %2$s e solte seus arquivos ChordPro |
| add_note | Songs appear here by artist as soon as they finish uploading. Files: .cho, .chopro or .txt. | As músicas aparecem aqui por artista assim que o envio termina. Arquivos: .cho, .chopro ou .txt. |
| add_missing | Don't see %1$s in Tool Manager? Your LightOS may need an update. | Não encontrou %1$s no Tool Manager? Talvez seu LightOS precise de atualização. |
| plurals add_recent | %d song added from Tool Manager today / %d songs … | %d música adicionada pelo Tool Manager hoje / %d músicas adicionadas … |
| delete_title | Delete song? | Apagar música? |
| delete_body | "%1$s" will be removed from this phone. | "%1$s" será removida deste telefone. |
| delete / cancel | Delete / Cancel | Apagar / Cancelar |

- `ToolText` (Kotlin, for Tool Manager) holds Songs/Músicas and the header text.
- Use `stringResource` / `pluralStringResource` (`androidx.compose.ui.res`; it isn't blocked).

## 10. Test plan

**JVM unit tests** in `tool/src/test/kotlin/com/christianlacerda/chords/` (kotlin.test). Don't use `::class.java.getResource`, because the scanner flags `.java.`. Read files with `File("src/main/assets/...")`; the unit-test working directory is `tool/`.

- **`ChordParserTest`**
  - Plain chords: C, F#m, Bb7, Ebmaj7.
  - Brazilian names: A7M→MAJ7, Bm7(5-)→HALF_DIM7, C7M(9)→MAJ9, E7(9)→DOM9, D4→SUS4, A2→ADD9, Bº→DIM, G6/9 is SIX with no bass.
  - Slash chords: D/F#, Am/G. Junk → null. `raw` is preserved.
- **`ChordProParserTest`**
  - Meta directives, title fallback to the file name, artist null.
  - Comment → label; sov/soc/eoc; `{chorus}` copy; tab verbatim.
  - Text before the first chord; annotation `[*riff]`; chord-only line; blank-line collapsing; `#` comments; unknown directive ignored; CRLF and BOM.
  - NOT_CHORDPRO detection.
- **`TextDecodingTest`:** UTF-8 "Refrão" decodes; Latin-1 bytes decode to "Refrão".
- **`TransposeTest`:** wrap-around, bass transposed, flat/sharp spelling by key, round trip ±12.
- **`LineWrapperTest`** (measure = length × 10, chord = length × 8):
  - Fits on one row.
  - Wraps only at word boundaries; `a[G]maz[D]ing` is never split.
  - A chord stays on its atom after the wrap.
  - Padding when the chord is wider than its text.
  - An over-long word is force-split.
  - Leading spaces trimmed on continuation rows; chord-only rows have `hasLyrics = false`.
- **`ScrollSpeedTest`:** level 1 = 12 s, level 10 = 2 s, monotonic, clamps.
- **`VoicingLibraryTest`** (loads the real `chords.json`):
  - Every entry parses and has 6 frets and 6 fingers.
  - **Note check:** sounded pitch classes ⊆ quality intervals + root, and every interval except the perfect 5th (7) is present; for slash entries, the lowest sounded string = bass.
  - Shifting: E-shape for G = 3-5-5-4-3-3; G7 returns 2 shapes, open first; C returns open, then A-shape at 3.
  - Fallback: Cm6 → Cm, approximate.
  - `baseFret` rules.
- **`LibraryIndexerTest`:** a new file is parsed; an unchanged mtime is reused without calling the parser; a removed file is dropped; errors are recorded; `firstSeen` is preserved; artist grouping is case-insensitive.
- **`SampleSongsTest`:** every file in `samples/index.txt` exists and parses without an issue, and every chord in it resolves to a non-approximate shape.
- **`WheelInputTest`:** DOWN steps, UP ignored, repeatCount, unknown keys not consumed, rotary accumulation threshold.

**LightOS emulator, headless** (light-habit's `run-emulator.sh` approach)
- **Build:** swap `serverPackage`, then `./gradlew :tool:installDebug -DlightSdk.allowAltServerPackage=true`. Launch with `adb shell am start -n com.christianlacerda.chords/com.thelightphone.sdk.LightActivity`.
- **Screenshots** with `adb exec-out screencap -p > NN.png`, each compared against the matching mockup:
  - 01 Artists, with the seeded samples;
  - 02 Songs (Traditional);
  - 03 Song scrolling (tap the lyrics, wait 3 s);
  - 04 Song paused;
  - 05 Chord diagram (tap G7);
  - 06 Wheel overlay (`adb shell input keyevent KEYCODE_DPAD_DOWN`, or `input mouse scroll … --axis VSCROLL,-1`, or tap "Speed");
  - 07 Chords in this song;
  - 08 Add songs.
  - Plus: the PT locale (`adb shell cmd locale set-app-locales com.christianlacerda.chords --locales pt-BR`), the Latin-1 file, the non-ChordPro error row, and the delete flow.
- **Upload path:**
  - `adb shell run-as com.christianlacerda.chords sh -c 'cat > files/shared/songs/x.cho' < x.cho`, then resume the app. This checks re-index on resume.
  - Optionally the emulator's own ToolManagerActivity (`sdk/emulator`), which serves the Tool Manager UI, to exercise `onToolManagerDataUpdate`.
- **Keep-awake check:** `adb shell dumpsys power | grep -i "FULL_WAKE\|keep"`, or `dumpsys window | grep -i KEEP_SCREEN_ON` while the song view is showing and after Back.

**On the device (user only; there is no ADB):** the ERD's v0 checks.
- Install ui-demo and tool-manager-demo through `upload-to-device.sh` and check:
  1. the Key Events screen while turning the wheel;
  2. whether the demo appears in Tool Manager;
- Then on Light Chord itself:
  3. the screen stays awake for more than 2 minutes in the song view;
  4. scroll smoothness at speeds 1 and 10.
- Record the key codes in `WheelKeys`.

## 11. Build order

| Milestone | Scope | Done when |
|---|---|---|
| **M0 (user, in parallel)** | v0 device probe (§10). The results only touch `WheelKeys`, `ROTARY_STEP_PX` and the font constants. | — |
| **M1: pure core + data** | lighttool.toml id/label/capability; delete the sample package; strings (EN/PT skeleton). `model/`, `chordpro/`, `voicing/`, `layout/`. `chords.json`, 5 sample songs, `index.txt`. All JVM tests in §10 except the indexer and wheel tests. A placeholder `@InitialScreen ArtistsScreen` that just says the tool name. | `./gradlew :tool:testDebugUnitTest :tool:assembleDebug` green |
| **M2: library** | `LibraryModels`, `LibraryIndexer` (+ test), `LibraryRepository` (seeding, refresh, settings), `LibraryEvents`, `ToolEntryPoint` manifest. ArtistsScreen, SongsScreen (no edit yet), AddSongsScreen. | Emulator screenshots 01, 02, 08 |
| **M3: song view** | SongStyles, `buildRenderItems` + MusicRow Layout, labels/tabs, bottom bar, play states, frame-loop scroll, tap toggle, end, progress, `keepScreenOn`, ScrollState in the VM. Chord tap → a stub ChordDiagramScreen showing the name. | Screenshots 03, 04 |
| **M4: wheel + diagrams** | `WheelInput` (+ test), VM key overrides, `Modifier.wheelInput`, SpeedOverlay, tappable speed fallback, persisted speed shown in the Songs detail line. ChordDiagram Canvas, full ChordDiagramScreen with shape cycling, SongChordsScreen with paging, ChordGridPainter icon. | Screenshots 05, 06, 07 |
| **M5: management + polish** | Edit mode + DeleteSongScreen; error rows/screens; missing-file handling; PT pass (plurals, short "Vel."); top fade; hit-slop; README section in `tool/` or the repo README describing the device-check results; full screenshot pass in EN and PT. Bump `versionCode` before each device install. | Full screenshot pass |

### Critical files for implementation
- `sdk/client/src/main/kotlin/com/thelightphone/sdk/LightActivity.kt` (key routing, back stack, `SealedLightContext`: dataStore / fileShare / readAsset)
- `plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt` (blocked imports and patterns: no LocalView, LocalContext, `.java.` or `getSystemService`)
- `examples/tool-manager-demo/src/main/kotlin/com/thelightphone/toolmanagerdemo/ToolManagerDemoHomeScreen.kt` (manifest + update-flow pattern)
- `sdk/client/src/main/kotlin/com/thelightphone/sdk/LightFileShare.kt` (`shared/songs` I/O; no size field)
- `sdk/ui/src/main/kotlin/com/thelightphone/sdk/ui/LightGrid.kt` and `LightText.kt` / `LightTheme.kt` (grid units, `designVerticalPxToSp`, typography tokens)
- `light-habit: tool/src/main/kotlin/com/christianlacerda/habits/ui/HabitTrackerViewModel.kt` (DataStore JSON pattern, `onAppPause` / `onScreenShow` lessons)
