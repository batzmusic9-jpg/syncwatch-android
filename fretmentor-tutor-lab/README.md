# FretMentor — Musical Performance Lab

This revision focuses exclusively on music. It replaces the generated band and the fixed question/development/climax/resolution scaffold. There is no user evaluation, microphone, lesson system, speech or generative AI. The original product in `../fretmentor-test/` remains separate.

## Listen

Serve the repository and open `/fretmentor-tutor-lab/`. Tap **OUVIR BANDA** to audition the recording alone, then **IMPROVISAR**. Every solo enters on a guarded next-bar boundary and lasts four bars. The band continues. The browser requires the initial tap to enable audio. SILENCIAR changes output gain without stopping the clock.

This test deliberately changes the original Am/F/C/G proposal: it uses Admiral Bob's recorded C-minor slow blues, 55 BPM, triplet subdivision. The twelve-bar score is Cm × 4, Fm × 2, Cm × 2, Gm, Fm, Cm, Cm→Gm turnaround. Chord sevenths in the engine are compatible solo colors, not a claim that every seventh is present in every recorded voicing.

HTTPS enables PWA installation. All runtime audio is local; no streaming service, runtime CDN or protected commercial recording. Successful first caching enables offline use. Locked-screen/background audio depends on the device/browser and is not guaranteed.

## Musical decisions

Original complete melodic gestures replace note-by-note random scale selection. Ten gesture families use triplet feel, upbeat entries, repeated notes, blue-note approaches, held notes, unequal phrase lengths, rhythmic hooks and deliberate rests. Five arrangements combine four-, six- and eight-beat phrases; six-beat phrases cross bar lines. The same hook can return with displaced rhythm, a shortened opening, a changed melodic tail or a new harmonic destination. Memory retains four phrases and avoids repeating the same arrangement consecutively.

Pitch selection preserves gesture contour, maintains octave continuity and adjusts destinations to chord tones at arrival. Answers can settle to the root. Motif development changes a limited part of the idea instead of replacing every note. Dynamics follow authored note weight and phrase character, rather than independent random velocities. There is no obligatory climax in bar three.

Research references:
- Ed Saindon, Berklee, “Got Rhythm?”: https://www.berklee.edu/berklee-today/winter-2008/the-woodshed/got-rhythm — accents, space, subdivision and phrase groupings beyond bar boundaries.
- Bob Reynolds, Berklee, “Focus on the Framework”: https://www.berklee.edu/berklee-today/spring-2016/focus-on-the-framework — rhythmic delivery, chord-tone framework, voice leading, repetition and motif development.

## Architecture

| Part | Responsibility |
|---|---|
| `transport.mjs` | One AudioContext origin, time/beat conversion and guarded next-bar scheduling |
| `tracks/backing.json` | Recording origin, duration, provenance, tempo and chord timeline |
| `harmony.mjs` | Twelve-bar minor blues, chord colors and harmonic roles |
| `vocabulary.mjs` | Original rhythmic/melodic gestures and flexible phrase arrangements |
| `PhrasePlanner` | Gesture, relationship, displacement and phrase intensity |
| `PitchEngine` | Gesture register, octave continuity, tail variation and harmonic destination |
| `MotifMemory` | Recent phrases, motif, layout, last pitch and hand position |
| `fingering.mjs` | Dynamic programming over physical string/fret positions with movement and bend costs |
| `ExpressionEngine` | Recorded attack selection, deliberate dynamics, late placement, bend travel/release and delayed vibrato |
| `audio.mjs` | Locally decoded band recording and layered/alternate guitar samples |
| `fretboard.mjs` | Existing exact-position renderer, retained but hidden during this music-only test |

The backing is one looping AudioBufferSource with an exact 96-beat loop endpoint. It does not depend on recurring JavaScript timer ticks. Solos are scheduled in advance on the same audio clock. Audio and optional fretboard debug receive the same PerformanceEvents. No independent HTML media clock is allowed to drift against the solo.

Synchronization uses an authored, checked score map. The artist specifies 55 BPM; percussion onset analysis matched the triplet grid, and bass autocorrelation/Rhodes spectral analysis identified minor roots/thirds and the turnaround. This is **not** live identification of every polyphonic note. The recording's human timing is preserved. A different track would need its own beat/chord map; a free-time performance would require tempo-map support.

## Verification and limitations

Run `node fretmentor-tutor-lab/tests/engine.test.mjs` from repository root. Three hundred performances cover all twelve entry bars, verify explicit rests, chord-tone destinations, monophony, physical fingering/bend pitch equality, bounded memory, deterministic replay, exact fingering optimality against exhaustive enumeration and next-bar guards. Current result: 257 distinct pitch/rhythm sequences, five layouts, maximum within-performance fret movement four frets and melodic leap eleven semitones. These numbers do not measure beauty.

Twenty complete mixes were also rendered through the production AudioRenderer using native OfflineAudioContext. All 80 lead recordings and the backing decoded; renders had finite samples and no clipping. This verifies the actual audio graph and scheduled playback, not an Android/browser listening score.

The musicality gate is still open. No claim that 15 of 20 takes sound like a skilled guitarist. That requires listening to complete takes on the target phone and headphones. The prior rejected implementation is not treated as a success.

Remaining algorithmic fingerprints: ten authored gesture families, recurring endings, conservative harmony, mostly fixed tonal register and a relatively small set of transformations. Four-bar audition duration is still fixed, although internal phrase lengths differ. Some register choices can fold a melodic contour; bends are rate-shifted samples and change the instrument's spectrum. There are no recorded slide, pull-off or pre-bend transitions, no per-string sample mapping and no continuous physical string model. Hammer-on recordings are used only for compatible ascending same-string moves. Seven-chord labels are engine colors over the verified basic minor harmony.

Native render tests cannot establish groove, emotional development, amp realism or acceptable Bluetooth visual latency. Mobile playback, offline cache behavior and perceptual quality still require device validation. Keep further work within this music lab: listen to twenty takes, identify the weakest repeated gestures/attacks, then refine those specific musical failures. Do not add product features before the sound is convincing.

## License

See `CREDITS.md`: backing **CC BY 3.0**, Karoryfer lead **CC0**. Runtime audio totals approximately 5.5 MB. Exact provenance and source/adaptation hashes are included. Old synthetic accompaniment modules and the previous lab sample set have been removed from this revision.
