# FretMentor — Musical Tutor Lab

Isolated 82 BPM / 4/4 browser music laboratory. No microphone, lesson framework, account, generative AI or speech. The original prototype remains in `../fretmentor-test`; its speech helper and speech calls were removed to honor the visual-only tutor rule.

## Run

Serve the repository with `python -m http.server 8000` and open `/fretmentor-tutor-lab/`. HTTPS is required for installation/offline caching outside localhost. First tap unlocks AudioContext and loads recorded instruments, starts the backing, then schedules four solo bars at a guarded next-bar boundary. Later taps reuse the uninterrupted backing transport. The first tap is necessary because mobile browser autoplay policies prohibit unattended audio. SILENCIAR mutes output; it does not stop transport.

The manifest supplies installable PNG icons. Service worker preloads all shell and sample files; offline playback works after successful initial caching. No CDN/runtime package dependencies. Audio is not guaranteed with screen locked or browser backgrounded.

## Architecture

| Module | Responsibility |
|---|---|
| `engine/transport.mjs` | AudioContext clock, beat/time conversion, guarded nextBar, 25 ms / 220 ms lookahead, backing scheduler, output-latency-aligned visualization |
| `engine/harmony.mjs` | Am7 / Fmaj7 / C / G, chord tones and roles, tuning |
| `PhrasePlanner` in `engine/composer.mjs` | Four-bar question / development / climax / resolution, targets, contour, density and intensity before note creation |
| `engine/rhythm.mjs` | Six original rhythm cells, explicit rests, motif rhythm variation, sparse release and short climax burst |
| `PitchEngine` | Diatonic motif transformations, chord-aware register, target-first approach, optional chromatic pickup to next chord's third |
| `MotifMemory` | Last four phrase plans and the last motif / hand location; repeat vocabulary through variation, answer, develop or contrast |
| `engine/fingering.mjs` | Dynamic programming across string/fret candidates; fret movement, string changes, position shifts and articulation constraints |
| `ExpressionEngine` | Authored phrase intensity curve, articulated accent/legato/slide/bend, delayed vibrato and deliberate small late timing |
| `engine/audio.mjs` | Local recorded-sample playback, true attack alternates, recorded velocity layers where available, gain envelopes, rate bends/slides, high/low pass EQ, light compression and 270 ms room |
| `engine/fretboard.mjs` | Single physical position from the scheduled PerformanceEvent; fret 12 left / open string right, string 6 above / string 1 below |

Both renderers share PerformanceEvent objects; the scheduled copy adds startTime/endTime from the same AudioContext origin. Visual sampling uses getOutputTimestamp where available. Bend events store sounding destination `midi`, source `bendFromMidi`, `frettedMidi`, string and fret. The marker stays on the physical source fret; no equivalent-pitch positions are lit. Target events use purple; other current notes use turquoise. No old-note trail to clutter the neck.

## Samples and licensing

See [CREDITS.md](./CREDITS.md), bundled license notice, [manifest](./samples/manifest.json) and exact per-file [provenance / hashes](./samples/provenance.json). All runtime assets are shipped locally (~1.64 MB compressed samples).

- FreePats clean guitar CC0: upstream commit `192cf0d9bf2c4ba6ead8e3524ba3f78818e4fe91`. 8 sampled pitch centers; 2 attack takes each; recorded soft/hard layers through E4. G4/B4 have one recorded velocity layer and use envelope gain for intensity. Converted/truncated from original FLAC to MP3. Rhythm and lead use the same recorded guitar, with separate processing buses.
- FluidR3 bass and percussion CC BY 3.0: retrieved fork commit `8b09fddc99bc98004d4440699cc95d9de8393fb6`. Attribution preserved. The repository's code MIT license does not override the sound bank license.

## Verification

`node fretmentor-tutor-lab/tests/engine.test.mjs` from repository root generates 200 sequential performances across all four possible entry chords. It verifies targets, motif identity, rests, phrase arc, memory bound, event order / monophony, pitch-to-physical-position equality (including bends), range, plausible hand travel, exact fingering optimum against exhaustive search, next-bar guard and backing patterns. These are structural checks, not a perceptual listening score.

## Honest limitations / gate

This is a musicality candidate, not proof that 15 out of 20 solos sound human. That gate requires actual listening to twenty complete performances, ideally blind, on the target phone and headphones. Do not extend the product until that listening test passes.

Algorithmic fingerprints remain: always the same four-bar macro arc, only six motif cells and six opening rhythms, constrained diatonic contour, motif-dependent culminating articulation, fixed vibrato rate, limited pickup type, recurring accompaniment patterns. Phrase memory informs motif reuse and physical hand position; it is not a large learned stylistic model.

Slides/bends are continuous playback-rate changes of picked samples. Hammer/pull articulation attenuates and softens a fresh attack; it is not a recording of genuine legato transitions. No per-string timbre sample mapping, dedicated mute samples, bend releases, pre-bends, amp model or measured cabinet IR. High-register velocity changes rely on gain rather than independent recorded layers. These are the largest remaining realism limits.

A visual note is painted at the first animation frame after its audio timestamp; this cannot guarantee zero latency. Bluetooth and browser output timestamp behavior need physical Android validation. Background timer throttling may interrupt the backing; after a stall it drops past events rather than playing a burst. App pause/resume follows AudioContext time, not wall time.

Next steps stay inside this laboratory: blind 20-take listening audit, repair the weakest contours/rhythms, compare lead samples/legato transitions, confirm Android/Bluetooth timing. No user scoring, microphone, classes or gamification.

Validation environment: engine and source/asset checks passed. Local Chromium installation failed; the available cloud browser reached the published access screen but was not signed in. Browser playback, offline behavior, mobile rendering and perceptual musicality remain unverified on a physical device. Sample amplitudes were inspected numerically; quiet FluidR3 drums are calibrated at load time against the guitar, and a final compressor limits peaks. This is not a measurement of the final rendered mix.
