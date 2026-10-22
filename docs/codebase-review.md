# Codebase review — 2026-10-22

Scope: first-party Light Chord (`tool/`) and companion Chord Desk (`src/`), with SDK interfaces inspected where relevant. This is a source review plus local build/test validation, not a physical-device or exhaustive security audit. Original review: Light Chord 5a9ea42 and Chord Desk bc16186. The findings below describe that baseline; the following section records the subsequent fixes.

## Fixes and validation — 2026-10-22

- P1 fixed: full normalized chord identities are independent of fallback voicing families. Definitions/grid entries no longer collide. Simplified library shapes are marked approximate and are not appended to an extended chord's exact definition.
- Save/Send fixed: both call desktop `document::serialize`, retaining selected shapes and adding default definitions. `document::save_path` retains an opened file's original destination.
- Server fixed: the former tiny_http adapter was replaced by a bounded single-request-per-connection HTTP/1 adapter over std TCP. Header/body reads share a five-second deadline capped by session lifetime; Cancel shuts down the active socket. POSTs require bounded Content-Length; unauthenticated bodies are not read. Chunked and Expect requests are unsupported. Five loopback tests exercise actual transport, stalled headers/body, authentication and cancellation without skipping.
- Desktop overwrite fixed: stage beside the target, sync, then rename. Failure-injection tests preserve the previous contents. Phone overwrite mitigated within the existing SDK: commit a pending song in DataStore before writing; replay before indexing, reading, hashing or deleting. This provides recoverability after interruption, not atomic filesystem replacement. No SDK modules were changed.
- Contract gaps fixed: `.chordpro` transfer support, byte-limited generated filenames, consistent filename validation, explicit invalid-batch rejection, UTF-8 normalization of legacy Windows-1252 transfer files, and manifest byte-count verification.
- Regression corpus: identical `chord-identities.cho` fixtures in both repos cover nine distinct chords and the definitions that must remain separate. Cifra Club display spelling remains intact.
- Final production-configuration `./gradlew :tool:testDebugUnitTest check --offline`: passed, 138 tool tests per debug/release variant, no failures/errors/skips. Desktop `cargo test --offline`: 46 library tests plus five real loopback server tests passed.
- Headless LightPhone3 AVD booted with its existing LightOS emulator app. Version 22 was installed with the emulator server package; the source TOML was restored to `com.lightos` afterward. Inspected Brazilian song names and all three grid pages: nine chords, including separate C7/C7(9-)/C7(9+)/C7(13), C6/C6(9), and C9/C7(9), with distinct defined shapes. Screenshots: `screenshots/validation-2026-10-22/`.
- Still unverified: physical LP3 wheel direction, device scrolling/keep-awake checks, and a camera-scanned desktop-to-phone transfer. CI workflow alignment and broader historical design-document cleanup remain follow-up maintenance work.

## Assessment

The architecture fits the product well. Chord Desk owns paste conversion and shape selection; Light Chord owns library browsing and hands-free reading. ChordPro is a useful portable boundary. Both repos separate substantial pure logic from their UI, and transfer orchestration is abstracted from HTTP and storage on the phone. The phone keeps SDK dependencies out of its parsers and layout logic. Its library refresh uses a shared mutex and incremental indexing; lifecycle hooks pause playback and preserve reading position when visiting diagrams.

The largest correctness risk is the boundary between precise musical identity and approximate voicing lookup. The largest maintenance risk is duplicated contracts without automated cross-repo fixtures. A rewrite is not warranted; focused fixes and integration tests are.

## Findings, in priority order

### P1: distinct chords collide in the phone's definition map

`model/ChordName.kt:78` builds canonical names from the coarse `Quality` enum rather than retaining the full suffix. `chordpro/ChordParser.kt:103` deliberately classifies extended/altered chords into simpler voicing families. That approximation then becomes identity: C7, C7b9, C7#9 and C13 all canonicalize to C7; C6 and C6/9 both become C6.

`ChordProParser` stores `{define}` entries under that key, and `SongViewModel.withSongChords` deduplicates by it. Consequently, later definitions overwrite earlier ones and distinct chords can disappear from the song's chord grid or receive each other's diagrams. Reproduced by invoking the compiled classes: parsing separate C7 and C7b9 definitions yields just `[C7]` as map keys.

Recommendation: preserve a fully normalized musical identity separately from the family used for fallback lookup. Add tests containing multiple related but distinct chords and definitions in one song.

### P2: Send current omits the definitions added by Save

Chord Desk `src/main.rs:666` adds a selected/default `{define}` for each chord before saving. `Message::SendCurrent` at line 728 sends the raw editor source, adding only a trailing newline. A freshly converted song sent directly can therefore use the phone's fallback shapes instead of the shapes previewed on the desktop. Explicit definitions already in the source do survive.

Recommendation: use one serialization function for both Save and Send, and test equality of their output.

### P2: saving an opened file ignores its original path

Chord Desk `src/main.rs:218` always computes the destination from the default folder and metadata. `Message::OpenFile` retains the original path in `opened`, but Save uses it only to decide whether to ask about overwriting. Opening a file outside the default folder, or changing its metadata, saves a different file instead of updating the opened one. This conflicts with README's stated overwrite behavior.

Recommendation: use the opened path for Save, with a separate explicit Save As action if desired; cover an external path and edited metadata.

### P2: a stalled HTTP request can outlive cancellation and expiry

Chord Desk `src/send.rs:38` synchronously reads a request body before authentication. The 64 KB limit bounds bytes, not elapsed time. The server's sole handler cannot recheck its stop flag or ten-minute expiry while blocked reading a client that stops sending. Closing the screen sets the flag but does not interrupt that read.

Recommendation: bound request-read duration, avoid reading bodies for GET requests, and make shutdown interrupt active I/O. Validate with a deliberately incomplete request and cancellation.

### P2: failed overwrites can damage existing songs

Desktop Save uses `std::fs::write` directly. Phone `LibraryRepository.writeSong` delegates to SDK `LightFileShare.write`, whose writer also truncates the destination before writing. A storage failure or process interruption can leave a previously valid song truncated. Download hashes protect network integrity but do not make the subsequent overwrite atomic.

Recommendation: stage writes and atomically replace after a successful close, within the SDK's supported filesystem surface. Test failure during overwrite and preservation of the prior file.

## Contract and maintenance gaps

- Phone library indexing and the desktop Open dialog accept `.chordpro`, but the transfer extension lists omit it. A file that can be opened is absent from the send picker.
- Desktop filename truncation counts 120 Unicode characters; the phone's transfer validator limits filenames to 255 UTF-8 bytes. Long non-ASCII metadata can therefore produce a rejected transfer name, or exceed filesystem limits.
- The phone decodes received bytes and writes UTF-8 text, but duplicate classification compares hashes of raw files. Windows-1252 files change bytes after receipt and can prompt for replacement on every send.
- `start_sending` silently truncates the file list to 200. Report excluded selections explicitly.
- Chord grammar exists in Rust, Kotlin spelling, and a separate Kotlin parser/classifier. Share an invented/public-domain fixture corpus covering spelling, identity, definitions, and transfer acceptance across both implementations.
- Chord Desk's `main.rs` combines event handling, synchronous filesystem work, serialization, and preview preparation. Extract document operations first; this would make the Save/Send/path behavior testable without the GUI.
- CI runs only on PRs despite a direct-to-main workflow; Chord Desk has no CI. Push-triggered checks would match actual development practice.
- Root Light Chord README is upstream SDK documentation. `tool/README.md`, `erd.md`, and parts of the architecture plan still describe Tool Manager import or historical implementation assumptions. AGENTS.md is currently the more accurate overview.
- Physical wheel direction, keep-awake, and real-device scrolling checks remain undocumented in the device-check table. Local unit tests cannot settle these.

## Validation

- `./gradlew :tool:testDebugUnitTest check --offline`: passed. Tool XML reports contain 130 tests per debug/release variant, zero failures/errors/skips. Some tasks used cached/up-to-date outputs.
- `cargo test --no-default-features --offline`: 41 library tests passed.
- `cargo test --offline`: GUI compiled; 41 library tests and one server test reported passed. The server test returns early if `lan_ip()` is unavailable, so this result does not establish successful real-network transfer.
- A temporary Java harness against the compiled Kotlin classes reproduced canonical-name collisions and overwriting of distinct definitions. No harness or copyrighted lyrics were added to the repos.
- No physical phone, visual UX session, or desktop-to-phone end-to-end transfer was exercised.

Suggested order: fix chord identity; unify Save/Send serialization and original-path saving; harden overwrite and server cancellation; then add shared contract fixtures and align CI/documentation.
