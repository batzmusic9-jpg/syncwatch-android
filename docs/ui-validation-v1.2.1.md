# SyncWatch 1.2.1 — UI and build validation

Java/View UI, official LibVLC 3.7.7 and the continuously attached video texture are retained. PlaybackEngine, PlaybackIntent, SyncProtocol, SyncConnection and SyncPolicy are unchanged. Only engine track-label resources were localized. English presentation adapters translate the old protocol's participant/error labels without changing its messages or state.

The player uses vector transport icons, 56/64dp circular transport targets, 48dp audio/subtitle/fullscreen chips, a compact timeline, original video aspect ratio and a mint/dark palette. Its 250ms refresh updates state/time/progress/visibility. Dragging seeks only on release. Controls hide after 3 seconds while playing and a video tap toggles them.

## Builds

Default builds contain only `arm64-v8a`; `-Puniversal=true` enables armeabi-v7a, arm64-v8a, x86 and x86_64 for broader device support and emulator tests. This filters native architectures without removing LibVLC codecs.

```sh
gradle --no-daemon -Puniversal=true testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
gradle --no-daemon assembleDebug
```

The workflow first copies the universal APK, then builds/copies ARM64. Both APKs are signed with the same job's debug key and verified with apksigner. Python checks their actual native ABIs and LibVLC libraries, measures bytes, calculates ARM64 size reduction against universal, and records SHA-256 hashes.

## Visual evidence

Existing four native playback/sync/subtitle tests are retained. One UI instrumentation test checks English labels, 48dp targets/content descriptions, real fullscreen rotation, texture identity, auto-hide, tap-to-show, and Back. It captures `portrait.png`, `fullscreen-visible.png` and `fullscreen-hidden.png` using real emulator screenshots while synthetic MP4 is loaded. Images survive test-app uninstall under the emulator's Download directory, then are copied into Validation-reports. Chunked CI preview logs provide the same PNGs for review without requiring an artifact download.

The fixture's Alex/Movie night identity is test data. No real movie or private participant data appears in previews. UI screenshots must be visually reviewed before delivery.

## Device acceptance

ARM64 decoding is not executed by the x86_64 emulator. The actual movie and physical phones still require verification, including unusual HEVC profiles, Main10/E-AC3/DTS and performance. Test build signatures can vary between runners.

1. Install the ARM64 APK on an ARM64 phone (use universal for other supported architectures). Open the real MKV and check picture, sound, play/pause and seek.
2. Check Audio, internal subtitles, external SRT/VTT, No subtitles and +/-500ms subtitle delay.
3. Join the same room on two phones; check Ready, participants, local/remote play/pause/seek and absence of feedback loops.
4. Review the English UI and controls, rotate/fullscreen/Back, tap to toggle the overlay, its 3-second timeout, and saved position after background pause.
