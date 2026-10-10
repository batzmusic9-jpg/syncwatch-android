# SyncWatch Android 1.2.1

Local videos synchronized with Syncplay. Android 8.0 or later. English UI, official LibVLC 3.7.7, cinematic controls and ARM64/universal downloads.

## Install and watch together

1. Download **SyncWatch-1.2.1-arm64** from **Actions → Android APK** for an ARM64 phone. The **SyncWatch-1.2.1-universal** artifact supports ARM32, ARM64, x86 and x86_64. Extract and install the APK.
2. Choose your local copy of the video, enter your name and use the same room name as the other participants.
3. Tap **Join room** and **Ready**. Play, pause and seeks synchronize through TLS at `syncplay.pl:8997`. Each person needs a local copy of the same duration.
4. Rotate to landscape or tap **Fullscreen**. Tap the video to show/hide controls; they hide after 3 seconds while playing. Back exits fullscreen first. The original video aspect ratio is preserved.

The fullscreen overlay has circular vector Play/Pause and 10-second transport controls, a compact timeline, Audio/Subtitles chips and an Exit control. Touch targets are at least 48dp. The native VLC texture stays attached while its bounds change. Portrait keeps the video card, Choose video, Subtitles/Fullscreen and a separate Room card with status, participants, Ready and Invite. There are no server/port fields or chat.

## Audio and subtitles

**Audio** selects a native track with language/codec/channel metadata when available. **Subtitles** offers **No subtitles**, internal tracks, external UTF-8 `.srt`/`.vtt` up to 2 MB and native subtitle delay. +500ms delays by half a second; -500ms advances it. Changing delay does not reopen the video or change other participants' subtitles. External subtitles must be selected again in a new session.

SAF access is limited to the selected file and its persisted URI grant. Generic provider MIME types are accepted without broad storage permission. Playback and media stay local; there is no upload, conversion, streaming, background playback or Play Store publication. Playback pauses in the background and saves its position. Room reconnection remains manual.

## Build and validation

JDK 17, Gradle 8.11.1, AGP 8.9.2, compileSdk 36, targetSdk 35, minSdk 26, versionCode 4 / versionName 1.2.1.

```sh
gradle --no-daemon -Puniversal=true testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
gradle --no-daemon assembleDebug
```

ARM64 is the default ABI filter. Universal is used for x86_64 instrumentation and is packaged before the ARM64 build. Both APKs retain LibVLC and its codecs. Actions verifies signatures, native libraries, measured APK sizes/reduction and SHA256SUMS; artifacts expire after 30 days. Debug signatures may differ between runners, requiring uninstall before installing an update.

The 15 existing unit tests remain. Four existing native tests cover MP4 H.264/AAC, synthetic MKV HEVC 1920x804 at 24000/1001 fps with AC3 5.1/48kHz, tracks, internal/external subtitles, delay, seek, fullscreen frames, socket synchronization, echo prevention and lifecycle pause. A fifth test checks accessible cinematic controls, English labels, rotation with retained texture, auto-hide, video tap and Back, and captures portrait/fullscreen-visible/fullscreen-hidden screenshots. See [UI validation and phone checklist](docs/ui-validation-v1.2.1.md) and the original [playback audit](docs/playback-audit-v1.2.md).

Synthetic emulator tests do not validate the real movie on physical phones or ARM64 decoding. Main10, E-AC3, DTS, other subtitle formats and device performance still require device tests. Selected native demux/decoder and cause details are in VLC logcat; the Java API does not expose all of them. Reports, logs and UI screenshots are in **Validation-reports**.

LibVLC is the official [Maven Central 3.7.7 dependency](https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/), distributed under LGPL with sources and license in the [official project](https://code.videolan.org/videolan/libvlc-android) and its sources JAR. The Syncplay protocol implementation and its feedback-loop protection are preserved.
