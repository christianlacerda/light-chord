# Rehearsal Mode implementation plan

Status: proposed implementation handoff, revised after Claude review on 2026-10-31. Christian requested a manual desktop rehearsal workflow and finer phone speed control. No feature code or visual design has been approved/implemented by this document.

## Intended experience

Open a song in Chord Desk, enter Rehearsal, start a countdown and automatic scrolling, and tap a lyric/chord phrase when it should reach the reading position. Each tap records a musical timing anchor and corrects the preview position. Preview the resulting timing, fix mistakes, Apply, then Save/Send normally. Light Chord plays the same timing at its own text size and wrapping.

A rehearsal may be done while listening to an external recording. This release does not require audio import, transcription, BPM detection, microphone access, or a recording service. The timing model should also accommodate future automatic alignment.

Explicit user timings are different from the rejected v14 equal-time-per-source-line pacing. Untimed playback remains constant pixels/second. Do not silently infer equal musical durations for source lines.

## Decisions and visual checkpoint

Before GUI changes, present two concrete sketches to Christian, as required by both AGENTS.md files:

- Recommended: use the existing viewer, add a Rehearsal toolbar, and make phrases tappable during recording. Controls: Start/Pause, speed, Undo last point, Finish, Preview, Apply, Cancel. A reading guide makes the meaning of a tap visible. Space marks the selected/next phrase for repeated tapping without aiming.
- Alternative: a dedicated rehearsal view with a larger next-phrase target and fewer surrounding editor controls.

Run the geometry spike below before committing to either layout. If existing widgets cannot expose reliable phrase geometry, use a canvas-drawn dedicated rehearsal view with owned text measurement, wrapping and hit testing; present that design for approval before product UI changes.

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
- Proposed phone behavior: dragging during timed playback suspends automatic viewport updates for the gesture, seeks the timeline on release, and continues playing without a countdown if it was playing before. A paused player stays paused. For flat/hold intervals, keep the current time if it already maps to the released position; otherwise choose the closest matching time to the pre-gesture time (earliest on ties). Do not snap back to the pre-drag position. Unit-test this inverse mapping. Confirm the interaction during LP3 testing.
- Timed songs with hidden tabs still spend the recorded time on them: map a hidden target to the following visible boundary, or the terminal boundary when none follows. Hiding tabs never compresses musical time. Resolve against effective tab visibility: the current tab-only-song fallback still shows tabs even when the setting is off, so those targets retain their measured row positions. Tab rows have fixed height; derive their positions from the measured block origin and actual row pitch.
- Initial release requires a linear arrangement. Explicitly expand shorthand `{chorus}` repeats before rehearsal (or block recording with an actionable explanation); a printed “2x” is not an executable repeat. Desktop currently does not mirror the phone's `{chorus}` expansion, so do not assume the parsers already agree.

## Shared song contract (proposal to freeze in assignment 2)

Embed one versioned, compact JSON value in a custom metadata directive:

    {meta: lightchord_timing {"version":1,"body_sha256":"…","cues":[{"ms":0,"boundary":0},{"ms":8000,"boundary":1},{"ms":20000,"boundary":2}]}}

For a two-line song, boundary 2 is terminal. The final cue is mandatory. This is a private extension, not a standard ChordPro timing directive. Existing phone versions ignore it and retain ordinary scrolling. No transfer endpoints, file extensions, or sidecars change.

Use a small dedicated canonicalizer in each language to define source boundaries independently of the existing parsers. Parsers consume its source-line-to-boundary map and carry those tags into render items; they must not invent their own ordinals. The shared fixture and parser-tagging tests are the contract. Keep the normative rules here, refining them in the first rehearsal task; do not create another duplicated specification document.

Canonicalization must be frozen byte-for-byte before playback implementation:

1. Normalize BOM/CRLF/CR to UTF-8 text with LF endings. Recognize directive aliases, selector suffixes and both colon/whitespace argument forms identically in Rust and Kotlin. Skip `#` comment lines consistently. Align the desktop renderer with this classification so it cannot display extra untagged lyric lines.
2. Assign ordinals to nonblank content lines, including chord-only lines and individual tab rows; exclude directives, headings and comments. A chord-only line stays a boundary even after its bracketed tokens are removed. Preserve a record for it rather than dropping an empty result. Include line kind (music versus tab) in fingerprint records.
3. Fingerprint the ordered content records, with every `[...]` token removed from music lines. Both parsers treat any bracketed text as a chord (including annotations such as `[*Riff]` or `[x2]`); do not make removal depend on chord recognition, which would couple the hash to each language's chord grammar. Preserve tab rows literally apart from trailing ASCII spaces/tabs; brackets inside tabs are not chord tokens. For lyric records trim/collapse ASCII spaces/tabs after chord removal so chord respelling or moving a chord within the same lyric line does not invalidate timing. Preserve lyric characters, case and punctuation. Specify record encoding and separators unambiguously in fixture expectations, then SHA-256 those UTF-8 bytes.
4. Exclude metadata, definitions, headings, blank lines and section labels from the fingerprint. Their layout positions are measured at playback. Lyric/tab edits, added/removed/reordered content lines, or music/tab kind changes invalidate the whole timeline in v1. Chord corrections, international respelling, title edits and diagram changes preserve it. Identical repeated content cannot be distinguished by this hash; this is acceptable because boundaries refer to occurrences in document order.
5. Reject rehearsal recording for unexpanded `{chorus}` references with an actionable explanation, and treat imported timing on such files as unsupported, unless a shared expansion is implemented and tested first. Do not let parser-specific expansion change boundary numbering. The geometry spike need not solve repeats.

Use one whole-timeline fingerprint for v1, not per-line automatic cue salvage: retaining most cues after structural edits adds mapping ambiguity and is deferred. Keep the proposed `body_sha256` field name, but define it as the canonical timing-content hash above.

`tidy_chordpro` converts bracket-only section lines such as `[Intro]` into `{comment: Intro}`, which removes a content record. Desktop Apply must fingerprint already-tidied text, so timed exports never contain such lines; an older timed file that still has them is stale after opening, by design.

Fixture cases must cover chord corrections/respelling, bracket-only section lines before and after `tidy_chordpro`, chord-only lines, Portuguese accents, tabs, `#` comments, directive aliases/selectors/whitespace forms, headings, blank lines and rejected shorthand repeats. Both parser tagging tests must agree with the canonicalizer, not merely produce matching hashes. Export must preserve valid timing through `tidy_chordpro`, custom diagrams, define enrichment, Save, Send current and batch sending. Validate against final serialized content. Canonical content changes invalidate rather than silently remap cues.

### Frozen v1 rules (assignment 2, 2026-10-31)

Implemented by Chord Desk `src/rehearsal.rs` and Light Chord `timing/RehearsalTiming.kt`; shared fixture `rehearsal-timing.json` (Chord Desk `tests/fixtures/`, Light Chord `tool/src/test/resources/contracts/`, byte-identical).

- Whitespace means ASCII space and tab only. `#` lines (after leading whitespace) are skipped, inside tabs too.
- A directive is `{name}`, `{name: arg}` or `{name arg}`; name `[A-Za-z_][A-Za-z0-9_-]*`, lowercased, `-selector` dropped. Anything else is content. `start_of_tab`/`sot` enter tab mode; any other start/end of verse, chorus, bridge or tab leaves it. `{chorus}` marks the song as a repeat (unsupported).
- Blank lines and blank tab rows get no boundary. Music records strip every `[...]` (an unclosed `[` stays text) and collapse whitespace runs to one space, trimmed; chord-only lines give an empty record. Tab records keep the row, trailing whitespace removed.
- Fingerprint: SHA-256, lowercase hex, of the concatenated UTF-8 records `M` or `T`, TAB, text, LF.
- Timing value: exactly one `{meta: lightchord_timing JSON}` line (two or more is malformed); written compactly as `{"version":1,"body_sha256":"…","cues":[{"ms":0,"boundary":0},…]}`. Unknown JSON fields are ignored; numbers must be JSON integers.
- Errors, checked in this order: `malformed` (bad JSON/types/missing fields), `unsupported_version`, `repeat_unsupported`, `empty` (no content records), `stale` (hash mismatch), `bad_cues`.
- Cues: 2 to 4096; first is exactly {0 ms, boundary 0}; ms in 0..21 600 000 (6 h) and strictly increasing; boundaries in 0..N, nondecreasing; last is terminal N.
- Interpolation is linear between cues, clamped to the first/last cue's position outside them.

Invalid/stale/unsupported timing never makes a readable song fail to open. Fall back to untimed playback with a concise warning. Desktop Apply requires a valid complete timeline; drafts stay in memory and are not exported. On edits that invalidate an applied timeline, show that rehearsal must be redone; exporting without valid timing remains possible and strips stale timing. Do not silently repair references by fuzzy text matching.

## Finer Light Chord scrolling

Current curve spans levels 1–10, 12 to 1.5 seconds per nominal music-line pitch. One whole level changes speed by about 26%. Keep the same endpoints and exact integer-level speeds, introduce half-level increments (about 12.2% per wheel step), and display values such as 4, 4.5, 5. This takes 18 wheel steps across the range instead of 36 quarter steps. This display is a proposal subject to the visual checkpoint.

Use integer ticks internally: 0..18, legacy level L maps to tick 2*(L-1). Evaluate the existing geometric curve at the fractional level. Keep legacy `SongSettings.speed` readable; add an optional tick field whose absence maps to the legacy integer. Persist the tick only when the user changes the default in Settings; wheel and speed-panel changes remain session-only. Store a nearest valid legacy integer for downgrade fallback. Define precedence explicitly and test old stored settings.

Timed playback opens at 100% rate independently of the legacy scroll level. Its wheel changes rate in 5 percentage-point increments, initially bounded to 50–150%; show a percentage so its meaning is clear. Rate is session-only in v1. Offer a per-session way to use ordinary scrolling for a timed song. These controls also need the visual checkpoint. Unknown wheel keys must continue falling through to LightOS.

## Implementation order and delegation

Four coding assignments are enough. Give each implementer this plan, both AGENTS.md files and the listed files. Use a cheaper coding model for the bounded implementation work; use a stronger reviewer for shared semantics and lifecycle integration. No current model pricing is assumed. Keep assignments sequential where files overlap. This plan does not launch agents or coding work.

### 1. Independent phone speed release (Kotlin implementer)

Files: `layout/ScrollSpeed.kt`, `library/LibraryModels.kt`, repository settings serialization, `ui/song/SongViewModel.kt`, `SongSettingsScreen.kt`, `SpeedOverlay.kt`, `SongBottomBar.kt`, speed tests, both localized string resources as needed.

Deliver the migration and half steps above, after visual approval. Check all references to MIN/MAX/DEFAULT and the ten-bar overlay so fractional values do not break rendering. Preserve play/pause/countdown and saved-default/session behavior.

Acceptance: integer speeds unchanged; every adjacent tick is about 12.2% faster, endpoints clamp, legacy level 4 opens at exactly the former speed, fine defaults round-trip, wheel changes do not overwrite defaults.

### 2. Desktop feasibility spike and shared fixture (Rust implementer with cross-repo review)

Do this first for rehearsal, before committing to a renderer or building either playback integration. It may proceed independently of the phone speed release. Under the pinned libcosmic revision, use an invented song with wrapped phrases to prove: actual scroll offset, programmatic scrolling, phrase bounds/hit tests, resize/reflow updates, and timer subscriptions without feedback loops. Do not upgrade libcosmic casually. A throwaway spike can run before visual approval; product UI changes still require it.

If existing wrapped widgets cannot reliably expose geometry, prototype a dedicated canvas view owning measurement, wrapping and hit tests. Acceptance is that clicking a phrase resolves its canonical boundary and programmatic scrolling places it at the reading guide, before/after resize. Report which approach works and capture a preview for design approval. Stop speculative core/UI integration until this is proven.

Alongside the spike, freeze canonicalizer rules above and create identical invented fixtures in both repos, with expected hashes, source tags, validation failures and sampled time-to-position results. A short test comment can explain the fixture; no separate spec package/document is needed. Include unequal phrase durations and intro holds. Review semantics before the next assignments.

#### Spike result (2026-10-31)

Existing widgets suffice; no canvas view is needed. `examples/rehearsal-spike.rs` in Chord Desk (`cargo run --example rehearsal-spike -- /tmp/rehearsal-spike`) wraps each canonical phrase in a container with `Id` `phrase-<boundary>` inside a `mouse_area` (click gives the boundary). A custom widget `Operation` (run with `iced::runtime::task::widget`) collects every phrase container's unclipped bounds plus the scrollable's bounds and current translation, giving content-space phrase tops and the real scroll offset. `scrollable::scroll_to` placed the chosen phrase exactly on a guide at one third of the viewport, before and after a reflow that wrapped lines from one or two rows to three. Findings: `on_scroll` is not emitted for programmatic scrolls (no feedback loop; read the offset through the operation instead); re-measure after every layout-affecting change (padding, width, font, notation) because stale tops misplace the guide; `window::frames()` ran at about 5 fps while the window was unfocused, while `time::every(16 ms)` gave about 60 fps; COSMIC tiling ignores `window::resize`, so the reflow was tested by narrowing the text column; a screenshot can be taken before a just-issued scroll is drawn. The click itself was a synthesized message, so real pointer hit testing still needs a manual check.

### 3. Desktop rehearsal end to end (Rust implementer)

Files: new `src/rehearsal.rs`, `src/lib.rs`, `src/preview.rs`, `src/document.rs`, shared fixtures; avoid bloating `main.rs` with timing math.

Deliver pure cue validation, draft operations, clock/rate transitions, interpolation/seeking, source references, fingerprinting, metadata reading/writing, and export integration. Keep `cargo test --no-default-features` working. Preserve exact custom diagrams and existing mandatory diagram coverage.

Acceptance: Undo/Cancel/re-record deterministic; pauses excluded; holds/terminal tested; malformed metadata falls back; repeated export is idempotent; current and batch exports preserve identical valid timing and diagrams; lyric/tab or structural content edits invalidate while chord/title/define edits do not.

#### Desktop UI portion of assignment 3

Depends on the pure core above, successful assignment 2 spike, and approved visual option. Files: `src/main.rs`, new `src/rehearsal_view.rs` as appropriate; existing viewer lives in `main.rs` and uses a COSMIC scrollable.

Use the renderer proven in assignment 2. Wire the approved controls, active-time clock, auto-scroll, phrase taps/Space, Undo, Finish, Preview, Apply/Cancel and unsaved draft handling when opening/new/editing/sending.

During initial recording use untimed speed between taps. Recorded points determine replay; speed changes alone are not exported. Smoothly correct the recording viewport toward a newly tapped phrase without changing its recorded time. Editing cue timestamps after finishing may be deferred if Undo/re-record is usable.

Acceptance: complete an invented song using only Start, taps, Finish, Preview, Apply; preview has visibly unequal phrase timing; Cancel leaves original bytes/timing unchanged; resizing does not alter cue identity. Capture screenshots at unchanged window size and inspect them. GUI build must pass as well as headless tests.

### 4. Phone timing engine and player (Kotlin implementer)

Files: new pure timing package, `model/Song.kt`, `chordpro/ChordProParser.kt`, `layout/SongLayout.kt`, `ui/song/SongBody.kt`, `SongScreen.kt`, `SongViewModel.kt`, settings/controls/strings as needed.

Deliver parsing/validation, source-to-layout mapping, timed player state and rate control; untimed path retains constant pixels/second. Keep Android imports out of pure logic. Use measured geometry, including wrapped lines and tab rows; derive fixed-height tab row positions from the measured block origin and actual row pitch, using the row's index within the rendered tab block (blank tab rows occupy height but have no boundary ordinal), not its boundary ordinal, with a test for the tab-only visibility fallback. Check transposition/copy operations retain timing references.

Acceptance: shared fixture matches Rust; rate changes do not jump; background/diagram navigation pauses; countdown excluded; holds survive reaching max scroll; terminal cue controls completion; hiding tabs/rotating/resizing preserves timeline time; fallback works for old or invalid songs. Build on the independent speed release before changing speed controls again.

## Final integration checklist (assignment 4 reviewer + Christian)

Run both test suites, phone `./gradlew check`, and desktop GUI build/tests. Check byte-identical shared fixtures. Update user READMEs and durable AGENTS guidance; remove superseded implementation notes. Commit and push to main under each repo's workflow; no PRs. Do not commit copyrighted rehearsal material or local scripts.

Emulator: transfer timed fixture, play/pause/resume/replay, change rate, open diagrams, hide tabs, compare timed and untimed fallback, test legacy settings. Bump `tool/lighttool.toml` versionCode before every install and restore com.lightos for device builds.

Christian: try a formerly troublesome song with a long held phrase, fast verse and instrumental passage. Confirm next text stays readable, rate correction feels useful, and half steps reduce overshoot for untimed songs. Record actual checks in `tool/README.md`; emulator success does not substitute for LP3 wheel/display validation. Request the current Tool Inbox link only when ready to install.

## Release boundary

Release A ships finer untimed speed alone, then Christian tests it on the LP3. Release B ships manual cue recording/replay on desktop, portable saved timings, timed phone playback and fallback, after the geometry spike and approved design. The speed release does not wait for rehearsal. Audio alignment, audio playback/import, automatic beat counting, live guitar following, repeat-aware arrangement editing, and continuous acceleration curves are later work. Successful implementation is a rehearsable end-to-end flow, not only a new file format or desktop animation.
