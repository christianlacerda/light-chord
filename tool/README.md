# Light Chord (`tool/`)

A hands-free chord-sheet reader for the Light Phone III. Songs are ChordPro files (`.cho`, `.chopro`, `.chordpro`, `.txt`)
received from Chord Desk over local Wi-Fi into `shared/songs/`; five public-domain samples are copied in on first launch.
The design is in `docs/architecture.md`; requirements are in `erd.md`.

## Build and test

- Pure logic (model, ChordPro parser, voicing table, layout, indexer, wheel input) has no Android imports and its JVM
  tests live in `src/test`. Android build: `./gradlew :tool:testDebugUnitTest :tool:assembleDebug`.
- Emulator: set `serverPackage = "com.thelightphone.sdk.emulator"` in `lighttool.toml` and build with
  `-DlightSdk.allowAltServerPackage=true`. Bump `versionCode` before each device install.

## Wheel keys

`ui/WheelInput.kt` (`WheelKeys`) is the one place to edit after a device check. Current assumption, from the SDK's
`LightDeviceKeys` mapping: the wheel sends key codes **317** and **318** for its two directions and **319** for a press.
317 is treated as "up" (faster); which direction is which is **unverified**, so flip `WheelInput.INVERT` if the speed
goes the wrong way. A press (319) toggles play/pause in the song view. DPAD/PAGE/NAVIGATE keys are kept as fallbacks;
volume keys are deliberately not used. `ROTARY_STEP_PX` tunes rotary/mouse-wheel sensitivity.

## Device checks (record results here)

| Check | Result |
|---|---|
| Wheel 317/318 direction (turn the wheel in a song, speed goes up/down?) | |
| Wheel press (319) toggles play/pause | |
| Does Light Chord appear in Tool Manager (`tool-manager-provider`)? | |
| Screen stays awake for more than 2 minutes in a song | |
| Scroll smoothness at speeds 1 and 10 | |

## Emulator validation — 2026-10-22

Version 22 was installed on the headless LightPhone3 AVD. An invented shared fixture verifies nine distinct
chords, including separate C7 / C7(9-) / C7(9+) / C7(13) and C6 / C6(9) shapes, plus Cifra Club spelling
C7M / C9 / C7(9). Screenshots: `../docs/screenshots/validation-2026-10-22/`.
The emulator uses DPAD fallbacks for the wheel; it cannot validate physical wheel direction or LP3 display smoothness.
