# AGENTS.md — Light Chord

Guidance for coding agents (Codex and others) working in this repo. Read this first, then `tool/README.md`,
`docs/architecture.md` (design and build plan) and `erd.md` (requirements, screenshots in `docs/screenshots/`).

## What this is

Light Chord ("Chords" / "Cifras" in the launcher) is a hands-free chord-sheet reader for the **Light Phone III**:
an artists → songs library, chords above lyrics in big type, smooth auto-scroll whose speed is set by the LP3
wheel, screen kept awake only in the song view, and guitar chord diagrams.

- Tool id and package: `com.christianlacerda.chords`. The tool lives in `tool/`.
- Everything else (`sdk/`, `plugin/`, `builder/`, `signer/`, `lint-rules/`, `examples/`, most of `docs/`) is the
  upstream **Light SDK** (lightphone/light-sdk), copied with full history (not a GitHub fork). Don't edit SDK modules
  for tool features. To update the SDK: add remote `upstream` = `https://github.com/lightphone/light-sdk` and merge it.
- Companion desktop app: **Chord Desk** (`christianlacerda/chord-desk`, Rust/COSMIC). It turns pasted chord sheets
  into ChordPro files and sends them to the phone. See "Chord Desk contract" below.
- The upstream `CONTRIBUTING.md` / `TOOL_GUIDELINES.md` are Light's rules for the SDK; the AI policy there is about
  PRs to lightphone/light-sdk, not this repo.

## Workflow rules

- Keep this `AGENTS.md` up to date as work changes architecture, workflows, constraints, shared contracts, or
  known issues, so Christian can switch agents and models without losing context. Update Chord Desk's
  `AGENTS.md` too when a change affects both repos. Record durable guidance and unresolved handoff details;
  remove stale information rather than accumulating a session log.
- **No pull requests. Commit and push straight to `main`.** Christian is the only developer.
- CI (`.github/workflows/pr-check.yml`, `./gradlew check`) only runs on PRs, so it will **not** run for pushes to
  main. Run `./gradlew check` locally before or right after pushing and fix anything it reports.
- **Bump `versionCode` in `tool/lighttool.toml` before every device install** (currently 24).
  `versionName` stays `1.0.0` unless told otherwise.
- Never commit real copyrighted song lyrics. Bundled samples are public domain only (Amazing Grace, Home on the
  Range, Wayfaring Stranger, Shenandoah, Peixe Vivo). Test fixtures use invented or public-domain text.
- Show Christian design options before changing visuals (icons, layouts). Don't rename the app; no numbers in names.
- Don't reintroduce "sung-line" scroll pacing (each source line = equal time). It was tried in v14 and reverted in
  v16; scrolling is constant px/s derived from the speed level. Ask first.

## Build, test, install

Requires JDK 17 and the Android SDK (Christian's Pop!_OS laptop: repo at `~/Develop/Github/light-chord`, SDK at
`~/Android/Sdk`). Sandboxes without access to Google Maven / dl.google.com can't build Android at all; in that case
only the pure-Kotlin logic can be tested (copy it into a throwaway Kotlin/JVM Gradle project).

```sh
./gradlew :tool:testDebugUnitTest          # JVM unit tests (tool/src/test)
./gradlew :tool:assembleDebug              # debug APK
./gradlew :tool:assembleRelease            # ~27 MB release APK (same LightSDK Dev signer) — preferred for the phone
./gradlew check                            # everything CI runs, including lint and the SDK's source scanner
```

- **Emulator:** AVD `LightPhone3` with the LightOS emulator system app (KVM, headless works; see light-habit's
  `scripts/run-emulator.sh`). Set `serverPackage = "com.thelightphone.sdk.emulator"` in `tool/lighttool.toml` and
  build with `-DlightSdk.allowAltServerPackage=true`. Switch back to `com.lightos` before a device build. The stock
  API-34 emulator drops keycodes 317–319, so emulate the wheel with DPAD_UP/DOWN and DPAD_CENTER.
- **Device install:** `scripts/upload-to-device.sh` on Christian's laptop (local, uncommitted; copied from
  light-habit) builds the debug APK with `serverPackage = com.lightos` and uploads it to the phone's Tool Manager
  › Tool Inbox using the URL + `#token` shown on the phone (token changes on every Tool Manager restart; same Wi-Fi).
  There is no status endpoint: the inbox emptying means the phone accepted it. The script always sends the debug
  APK (~83 MB, which can crash File Manager on weak Wi-Fi); for release, run a copy with the APK path changed and
  `--no-build`. Christian handles the phone side; ask him for the link.

## Architecture (tool/src/main/kotlin/com/christianlacerda/chords/)

| Package | What it does |
|---|---|
| `ToolEntryPoint.kt` | `@EntryPoint`; Tool Manager manifest; `onToolManagerDataUpdate` just bumps `LibraryEvents.filesChanged`. |
| `model/` | `Song`, `ChordName`, `Voicing`, `ChordNotation` (display notation + `displayName`). |
| `chordpro/` | `ChordProParser`, `ChordParser` (chord names), `ChordDefine` (`{define}` lines), `ChordSpelling` (Brazilian/International spelling), `Transposer`, `TextDecoding` (strict UTF-8, else windows-1252). |
| `voicing/` | `VoicingLibrary` (shapes for a chord; `{define}` first, then `assets/chords.json`), `Fretboard`. |
| `layout/` | `LineWrapper`, `SongLayout` (render items), `ScrollSpeed` (levels 1–10: 12 s … 1.5 s per line). |
| `library/` | `LibraryRepository` (files in `shared/songs/`, DataStore settings, sample seeding), pure `LibraryIndexer`, `LibraryEvents` (process-wide flows; data only, never a Context). |
| `transfer/` | Receive from Chord Desk: QR URL parsing, HTTP client, protocol models. |
| `ui/` | Screens: artists, songs (+ delete), song (player, bottom bar, speed overlay, settings), chords (diagram, song chords grid), receive, settings. `WheelInput`/`WheelModifier`, `KeepScreenOn`, `ChordsTheme`, `SongStyles`. |

Assets: `assets/chords.json` (fallback voicing table), `assets/samples/` (+ `index.txt`, since there is no asset
listing API). Strings: `res/values/strings.xml` and `res/values-pt/strings.xml`; keep both in sync.

Pure logic (model, parsers, layout, indexer, wheel input, transfer protocol) has **no Android imports** so it stays
JVM-testable. Keep it that way and add tests in `tool/src/test/kotlin/...` next to the existing ones.

Song writes use `library/SongWriter.kt`: persist a pending write in DataStore before the SDK text writer runs,
then clear it after success. Refresh, reads, hashes and deletion recover pending writes under `refreshMutex`.
This is replayable recovery, not filesystem atomic replacement; keep it tool-local (SDK has no rename API).

## Light SDK constraints (the build enforces these)

The SDK plugin (`plugin/.../LightSdkPlugin.kt`, `BLOCKED_IMPORTS` / `BLOCKED_CODE_PATTERNS`) scans **every** `.kt`
under `tool/src`, tests included, and fails the build on: `LocalContext`, `LocalView`, `LocalActivity`,
`LocalLifecycleOwner`, `android.app.*`, `android.content.Context`/`Intent`/…, `getSystemService(`, `contentResolver`,
`startActivity(`, casts to Context/Activity, and reflection (`.javaClass`, `.java.`). Matches in comments are skipped, but avoid the words anyway.

- Keep-awake is a 1 dp `AndroidView` whose View has `keepScreenOn = true` (`ui/KeepScreenOn.kt`).
- `onToolManagerDataUpdate()` has no Context; screens collect `LibraryEvents.filesChanged` and refresh.
- Each screen draws its own `LightTopBar` with a back button; the SDK does not.
- Navigation arguments go through screen constructors; ViewModels build their `LibraryRepository` from
  `lightContext` in `createViewModel()`.
- Lint fails on a literal BOM character in source: write `"﻿"`.
- Android unit tests can't use `com.sun.net.httpserver`.

## UI conventions

- **Use Light SDK components first** (`LightTopBar`, `LightBottomBar`, `LightBarButton`, `LightIcons`, theme
  typography/tokens) wherever one exists. Hand-built bars drifted from the LightOS look (font, spacing). Compare
  against native LightOS apps and christianlacerda/light-habit. The one exception: the big bold chord names on the
  chord diagram and chords-grid screens are custom `BasicText` for readability.
- Fonts: Akkurat via `LightThemeTokens.typography`; chords use `FontWeight.Bold`.
- No orientation lock (it letterboxes on the LP3's near-square display).
- Player: `LightBottomBar` with two slots, speed on the right; 3‑2‑1 countdown on every play; bracketed chord-only
  lines render inline.

## Wheel

`ui/WheelInput.kt` (`WheelKeys`) is the single place for key mapping. Per the SDK's `LightDeviceKeys`
(light-keyboard 0.0.19): keycode **317** = RotaryTurnUp, **318** = RotaryTurnDown, **319** = press. 317 is treated
as "faster"; which physical direction that is remains unverified on the LP3 (`WheelInput.INVERT` flips it). Press
toggles play/pause. Volume keys are deliberately not used. Unknown keys must fall through to LightOS.

## Chord notation

- App-wide setting: **Brazilian (Cifra Club) is the default**, International is the option. It is display only;
  song files always use international spelling.
- Brazilian display follows Cifra Club's standard (suporte.cifraclub.com.br, article 64000308284): first number right
  after the name, the rest in parentheses with `/` (`7(4/9)`, `C(9)`, `m7(b5)`), `7M` for maj7, `4` for sus4, `°`
  for dim, `+`/`-` alterations, e.g. `G#7M`, `D4`.
- `chordpro/ChordSpelling.kt` is a port of Chord Desk's `src/chord.rs` grammar. **Keep the two in sync**: a spelling
  change in one needs the same change (and test) in the other. `ChordSpellingTest.kt` covers the Kotlin side.
- `ChordName.identitySuffix` / `canonicalName` preserve the full normalized suffix independently of the coarse
  `Quality` used for voicing fallback. Never collapse C7b9/C7#9/C13 to C7 or C69 to C6 in definition/grid keys.
  Inexact library shapes are labelled approximate. Every supplied `{define}` is authoritative: show that
  shape alone, without appending local-library alternatives. Shared fixture: `tool/src/test/resources/contracts/chord-identities.cho`, copied in Chord Desk's
  `tests/fixtures/`; keep the two copies identical.

## Chord Desk contract

- **Files:** ChordPro (`.cho`, `.chopro`, `.chordpro`, `.crd`, `.txt`) in `shared/songs/`, named `Artist - Title.cho`, with
  `{title}`, `{artist}`, optional `{key}`/`{capo}`, `{start_of_chorus}`, `{comment: Label}`, `{start_of_tab}`.
- **Chord shapes:** songs may carry `{define: Am base-fret 1 frets x 0 2 2 1 0 fingers 0 0 2 3 1 0}` lines. Frets run
  low E → high e and are **relative to base-fret** (1 = base fret, 0 = open, x/N = muted); fingers 0 = none. Chord
  Desk picks these from **chords-db** (MIT). Light Chord shows the song's `{define}` shape first and falls back to
  `assets/chords.json` (`VoicingLibrary.shapesFor(chord, defined)`). A barre is drawn only when no muted/open string
  sits inside its span (`ChordDefine.barreOf`). Don't change the `{define}` format without changing Chord Desk too.
- **Transfer:** LightOS v582's Tool Manager only exposes Photos + Tool Inbox, so `tool-manager-provider` folders
  aren't reachable. Songs come over Wi-Fi instead: Chord Desk serves files and shows a QR; the phone opens
  **Chords › + › Receive from Chord Desk** and scans it. Spec: `docs/chord-desk-transfer.md` (keep in sync with
  Chord Desk's `src/transfer.rs`). This is why the tool has INTERNET, CAMERA and `cleartext-http`.
- **Import is paste-based, on the desktop.** The phone never parses Cifra Club pages and nothing fetches song URLs;
  no typing on the LP3. Don't add scraping of Cifra Club or other sites.

## Open device questions

- Review and fixes: `docs/codebase-review.md` (2026-10-22). Validation screenshots live in
  `docs/screenshots/validation-2026-10-22/`; headless LightPhone3 emulator verified Brazilian spelling and nine
  distinct chord-grid entries. Physical-device checks below remain open.
- Physical direction of wheel keycodes 317/318.
- Record device check results in the table in `tool/README.md`.

## Custom diagrams and Cifra Club string markers

Implemented 2026-10-22. Chord Desk's larger editor lets users place dots, enter fingers 1–4, choose open/muted
strings and a starting fret, and Apply/Cancel a song-specific shape. No phone editor was requested. Custom
shapes travel in existing `{define}` lines; preserve explicit zero/unknown finger numbers.

Both apps display Cifra Club markers BELOW every string: filled circle for bass, hollow circle for another
played string, × for muted. Hollow here means played, NOT necessarily open. `voicing/bassString` uses standard
tuning's absolute pitches, including high-position and slash-chord shapes; don't assume the leftmost string
or root is always bass. Phone diagrams stay monochrome and retain capo indicators.

ChordDefine infers a barre for any finger 1–4 at one fret, only if every crossed string is fretted at or above
it and the same finger isn't also used at another fret. The current model draws one inferred barre (lowest
valid fret). Keep this aligned with Chord Desk's `diagram::barre_of`.

Shared regression fixture: `tool/src/test/resources/contracts/custom-shapes.cho`, identical to Chord Desk's
`tests/fixtures/` copy. Version 23 was checked in the headless LightPhone3 emulator: Am7/G, a finger-2 barre,
unnumbered dots and a muted-string span. Screenshots: `docs/screenshots/custom-diagrams-2026-10-22/`.

Chord Desk's final exports embed automatic and custom definitions for every recognized chord; missing shapes
must be authored on the desktop before export. Save, Send current and batch sends share that validation.
SongViewModel bypasses local voicing-table loading/lookups when definitions cover the requested chords.
Keep the local table as compatibility fallback only for older/imported songs with missing definitions.
