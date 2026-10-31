# Rehearsal Mode implementation plan

Status: proposed implementation handoff, 2026-10-31. Christian requested a manual desktop rehearsal workflow and finer phone speed control. No feature code or visual design has been approved/implemented by this document.

## Intended experience

Open a song in Chord Desk, enter Rehearsal, start a countdown and automatic scrolling, and tap a lyric/chord phrase when it should reach the reading position. Each tap records a musical timing anchor and corrects the preview position. Preview the resulting timing, fix mistakes, Apply, then Save/Send normally. Light Chord plays the same timing at its own text size and wrapping.

A rehearsal may be done while listening to an external recording. This release does not require audio import, transcription, BPM detection, microphone access, or a recording service. The timing model should also accommodate future automatic alignment.

Explicit user timings are different from the rejected v14 equal-time-per-source-line pacing. Untimed playback remains constant pixels/second. Do not silently infer equal musical durations for source lines.

## Decisions and visual checkpoint

Before GUI changes, present two concrete sketches to Christian, as required by both AGENTS.md files:

- Recommended: use the existing viewer, add a Rehearsal toolbar, and make phrases tappable during recording. Controls: Start/Pause, speed, Undo last point, Finish, Preview, Apply, Cancel. A reading guide makes the meaning of a tap visible. Space marks the selected/next phrase for repeated tapping without aiming.
- Alternative: a dedicated rehearsal view with a larger next-phrase target and fewer surrounding editor controls.

The recommended interaction is **tap the phrase being reached now**, not tap anywhere to mean an unspecified speed change. A speed adjustment can help keep upcoming text reachable but does not define a reliable portable anchor on its own. Dragging changes the viewport; it does not write a timing point. Keep diagrams and chord navigation from intercepting rehearsal taps.

The user must be able to reach a phrase that has scrolled off screen: pause, reposition, resume, then mark it. Undo and re-record from a selected cue remove/replace the subsequent draft suffix; do not permit ambiguous out-of-order timelines. All rehearsal changes are drafts until Apply. Cancel restores the original document and timings.

## Playback model

Keep a pure Rust timing module and a matching pure Kotlin module, with a shared fixture and sampled expected outputs. GUI layers supply measured positions, clocks, and input.

- A cue is an elapsed active time in integer milliseconds plus a source-text boundary. Source identities survive layout changes; never export pixels, viewport percentages, font metrics, or wrapped-row indices.
- Boundaries identify the start of an original nonblank lyric/chord-only line, or a tab row. The terminal boundary identifies the bottom of the final content. Blank lines and headings affect measured geometry but are not automatically assigned durations.
- Times strictly increase. Targets are nondecreasing; repeated targets allow holds. Reject negative values, overflow, backward targets, unknown versions, duplicate timestamps and out-of-range references. Bound input by the existing 256 KiB song limit and a documented cue-count cap.
- Start with a cue at time zero for the first phrase. A later cue for that same phrase represents an intro hold. Finish records an explicit terminal cue and end time; never infer song completion from reaching the scroll limit.
- Between cues, interpolate measured positions linearly. This is smooth motion with changes of velocity at cues; acceleration smoothing is optional later. Sparse cues are an approximation, not evidence of musical beats.
- Use a reading position about one third down the usable viewer. Provide leading/trailing layout space where necessary so first/last phrases can reach it. Resolve target coordinates after layout and clamp to valid bounds. Multiple logical cues can map to one clamped position; their elapsed durations still count.
- Use a monotonic active-time clock. Countdown, pause, background time, and navigation to diagrams do not advance it. Calculate position from timeline time, not accumulated frame deltas, to avoid drift.
- Playback rate multiplies timeline time. Changing rate preserves the current timeline time and position. Pause/resume preserves time and repeats the existing phone countdown. Replay resets time. Window resize/font/notation changes remeasure targets at the same timeline time.
- In timed playback, manual dragging pauses playback and seeks to a documented timeline position; for flat/hold intervals choose the earliest matching time. Resume starts there. Do not snap back to the pre-drag position. Unit-test this inverse mapping.
- Timed songs with hidden tabs still spend the recorded time on them: map a hidden target to the following visible boundary, or the terminal boundary when none follows. Hiding tabs never compresses musical time.
- Initial release requires a linear arrangement. Explicitly expand shorthand `{chorus}` repeats before rehearsal (or block recording with an actionable explanation); a printed “2x” is not an executable repeat. Desktop currently does not mirror the phone's `{chorus}` expansion, so do not assume the parsers already agree.

## Shared song contract (proposal to freeze in task 1)

Embed one versioned, compact JSON value in a custom metadata directive:

    {meta: lightchord_timing {"version":1,"body_sha256":"…","cues":[{"ms":0,"boundary":0},{"ms":8000,"boundary":1},{"ms":20000,"boundary":2}]}}

For a two-line song, boundary 2 is terminal. The final cue is mandatory. This is a private extension, not a standard ChordPro timing directive. Existing phone versions ignore it and retain ordinary scrolling. No transfer endpoints, file extensions, or sidecars change.

Task 1 must specify canonicalization byte-for-byte before implementing either language:

1. Normalize BOM/CRLF/CR to UTF-8 text with LF endings.
2. Exclude timing and document metadata (`title/t`, `subtitle/st`, `artist`, `key`, `capo`, `define`, and their supported `meta` equivalents) from the body fingerprint. Keep section/comment/other body directives so structure edits invalidate timings. Use an explicit shared allowlist, not language-specific parser assumptions.
3. Canonical body records preserve leading spaces and strip trailing ASCII spaces/tabs. Join records with LF and one terminal LF; define blank-line handling and comment handling identically in both languages. SHA-256 covers exactly these UTF-8 bytes.
4. Assign boundary ordinals to nonblank body content lines in source order, excluding directives and `#` comment lines; include individual tab rows. Metadata/define insertion must not shift identity. Carry these source references through parser output and render items rather than reconstructing them from rendered text.

Freeze examples for whitespace, Portuguese accents, tabs, aliases, comments and repeats in the shared fixture. Export serialization must preserve valid timing through `tidy_chordpro`, custom-diagram edits, automatic define enrichment, Save, Send current, and batch sending. Recompute/validate against the final serialized body, not an earlier draft. Any conversion that changes the body must invalidate/re-record timings rather than silently attach them to different text.

Invalid/stale/unsupported timing never makes a readable song fail to open. Fall back to untimed playback with a concise warning. Desktop Apply requires a valid complete timeline; drafts stay in memory and are not exported. On edits that invalidate an applied timeline, show that rehearsal must be redone; exporting without valid timing remains possible and strips stale timing. Do not silently repair references by fuzzy text matching.

## Finer Light Chord scrolling

Current curve spans levels 1–10, 12 to 1.5 seconds per nominal music-line pitch. One whole level changes speed by about 26%. Keep the same endpoints and exact integer-level speeds, introduce quarter-level increments (about 6% per wheel step), and display values such as 4, 4.25, 4.5, 4.75. This display is a proposal subject to the visual checkpoint.

Use integer ticks internally: 0..36, legacy level L maps to tick 4*(L-1). Evaluate the existing geometric curve at the fractional level. Keep legacy `SongSettings.speed` readable; add an optional tick field whose absence maps to the legacy integer. Persist the tick only when the user changes the default in Settings; wheel and speed-panel changes remain session-only. Store a nearest valid legacy integer for downgrade fallback. Define precedence explicitly and test old stored settings.

Timed playback opens at 100% rate independently of the legacy scroll level. Its wheel changes rate in 5 percentage-point increments, initially bounded to 50–150%; show a percentage so its meaning is clear. Rate is session-only in v1. Offer a per-session way to use ordinary scrolling for a timed song. These controls also need the visual checkpoint. Unknown wheel keys must continue falling through to LightOS.

## Delegation packages and dependency order

Each package is one bounded coding assignment. Give the implementing model this document, both AGENTS.md files, and the listed files. Start with one implementer at a time; packages touching shared files are not independent. Use a stronger reviewer for the contract and lifecycle integration; routine pure logic/tests and approved UI wiring can go to a cheaper coding model. No particular current model pricing is assumed.

### 1. Freeze contract and build fixtures (cross-repo reviewer)

Deliver a final normative `docs/rehearsal-timing.md` in both repos with identical contents, invented shared `.cho` fixtures and invalid cases. Resolve fingerprint rules, source boundaries, terminal/hold semantics, repeats, stale timings, and validation bounds. Include expected timestamp-to-position samples using different desktop/phone geometry. Implement no GUI yet. Review this before packages 2–4.

Acceptance: another implementer can produce byte-identical hashes and equivalent playback results without guessing. Fixture explains what each cue means and includes unequal phrase durations and an intro hold.

### 2. Fine speed increments (Kotlin implementer; independent of timing)

Files: `layout/ScrollSpeed.kt`, `library/LibraryModels.kt`, repository settings serialization, `ui/song/SongViewModel.kt`, `SongSettingsScreen.kt`, `SpeedOverlay.kt`, `SongBottomBar.kt`, speed tests, both localized string resources as needed.

Deliver the migration and quarter steps above, after visual approval. Check all references to MIN/MAX/DEFAULT and the ten-bar overlay so fractional values do not break rendering. Preserve play/pause/countdown and saved-default/session behavior.

Acceptance: integer speeds unchanged; every adjacent tick is about 6% faster, endpoints clamp, legacy level 4 opens at exactly the former speed, fine defaults round-trip, wheel changes do not overwrite defaults.

### 3. Desktop rehearsal core and export (Rust implementer)

Files: new `src/rehearsal.rs`, `src/lib.rs`, `src/preview.rs`, `src/document.rs`, shared fixtures; avoid bloating `main.rs` with timing math.

Deliver pure cue validation, draft operations, clock/rate transitions, interpolation/seeking, source references, fingerprinting, metadata reading/writing, and export integration. Keep `cargo test --no-default-features` working. Preserve exact custom diagrams and existing mandatory diagram coverage.

Acceptance: Undo/Cancel/re-record deterministic; pauses excluded; holds/terminal tested; malformed metadata falls back; repeated export is idempotent; current and batch exports preserve identical valid timing and diagrams; body edits invalidate while title/define edits do not.

### 4. Phone timing engine and player (Kotlin implementer)

Files: new pure timing package, `model/Song.kt`, `chordpro/ChordProParser.kt`, `layout/SongLayout.kt`, `ui/song/SongBody.kt`, `SongScreen.kt`, `SongViewModel.kt`, settings/controls/strings as needed.

Deliver parsing/validation, source-to-layout mapping, timed player state and rate control; untimed path retains constant pixels/second. Keep Android imports out of pure logic. Use measured geometry, including wrapped lines and tab rows; tab rendering may need individual row measurement. Check transposition/copy operations retain timing references.

Acceptance: shared fixture matches Rust; rate changes do not jump; background/diagram navigation pauses; countdown excluded; holds survive reaching max scroll; terminal cue controls completion; hiding tabs/rotating/resizing preserves timeline time; fallback works for old or invalid songs. Integrate package 2 before changing speed controls again.

### 5. Desktop rehearsal UI (Rust GUI implementer)

Depends on package 3 and approved visual option. Files: `src/main.rs`, new `src/rehearsal_view.rs` as appropriate; existing viewer lives in `main.rs` and uses a COSMIC scrollable.

First spike: prove the pinned libcosmic revision supports reading actual scroll offsets, programmatic scroll updates, measuring phrase bounds, and frame/timer subscription without feedback loops. Do not upgrade libcosmic casually. Then wire the approved controls, active-time clock, auto-scroll, phrase taps/Space, Undo, Finish, Preview, Apply/Cancel and unsaved draft handling when opening/new/editing/sending.

During initial recording use untimed speed between taps. Recorded points determine replay; speed changes alone are not exported. Smoothly correct the recording viewport toward a newly tapped phrase without changing its recorded time. Editing cue timestamps after finishing may be deferred if Undo/re-record is usable.

Acceptance: complete an invented song using only Start, taps, Finish, Preview, Apply; preview has visibly unequal phrase timing; Cancel leaves original bytes/timing unchanged; resizing does not alter cue identity. Capture screenshots at unchanged window size and inspect them. GUI build must pass as well as headless tests.

### 6. Integration and physical rehearsal (reviewer + Christian)

Run both test suites, phone `./gradlew check`, and desktop GUI build/tests. Check byte-identical shared fixtures. Update user READMEs and durable AGENTS guidance; remove superseded implementation notes. Commit and push to main under each repo's workflow; no PRs. Do not commit copyrighted rehearsal material or local scripts.

Emulator: transfer timed fixture, play/pause/resume/replay, change rate, open diagrams, hide tabs, compare timed and untimed fallback, test legacy settings. Bump `tool/lighttool.toml` versionCode before every install and restore com.lightos for device builds.

Christian: try a formerly troublesome song with a long held phrase, fast verse and instrumental passage. Confirm next text stays readable, rate correction feels useful, and quarter steps reduce overshoot for untimed songs. Record actual checks in `tool/README.md`; emulator success does not substitute for LP3 wheel/display validation. Request the current Tool Inbox link only when ready to install.

## Release boundary

First usable release includes manual cue recording/replay on desktop, portable saved timings, timed phone playback, fallback, and finer untimed speed. Audio alignment, audio playback/import, automatic beat counting, live guitar following, repeat-aware arrangement editing, and continuous acceleration curves are later work. Successful implementation is a rehearsable end-to-end flow, not only a new file format or desktop animation.
