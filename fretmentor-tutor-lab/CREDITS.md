# Audio credits

## Recorded backing: Slow Blues Backing Track in Cm

- Artist: **Admiral Bob**.
- Original publication and license declaration: https://ccmixter.org/files/admiralbob77/28495
- License: **Creative Commons Attribution 3.0** — https://creativecommons.org/licenses/by/3.0/
- Original stems: https://ccmixter.org/content/admiralbob77/admiralbob77_-_Slow_Blues_Backing_Track_in_Cm.zip
- Adaptation: two complete choruses from 8.7272727 seconds; original drums, bass, rhythm guitar and Rhodes mixed with one shared gain adjustment. Original lead guitar excluded so the lab can supply its own solo. 5 ms boundary fades suppress loop clicks. Converted to Vorbis. No time stretching or resynthesis.
- Attribution appears on the performance page. Do not register the original recording or this derivative in Content ID.
- BPM: 55; triplet subdivision. Two-chorus loop: 96 beats / 24 bars / 104.7272727 seconds.
- Source hash, adaptation hash and harmonic map: `tracks/backing.json`.

## Sampled lead: Karoryfer Black and Green Guitars

- Library: **Black and Green Guitars**, Karoryfer Samples; recordings by **Brian Wood**.
- Publisher: https://shop.karoryfer.com/pages/free-samples
- Source: https://github.com/sfzinstruments/karoryfer.black-and-green-guitars
- Source commit: `b3b3249d37dc977a1a297bd2dc053e6d9b6b805c`.
- License: **CC0 1.0**, verified in the library license; complete text in `licenses/Karoryfer-CC0.txt`.
- Instrument selected: green Gretsch Anniversary guitar. 80 recordings: soft/medium/hard picked layers, alternate recorded takes, actual recorded hammer-on and staccato attacks. Nine pitch centers, MIDI 55–75.
- Adaptations: selected original Git binary blobs (avoiding upstream checkout line-ending conversion), removed leading silence below −65 dB, converted to mono 44.1 kHz / 96 kbps MP3. Original source paths and SHA-256 hashes in `samples/lead-provenance.json`; playback mapping in `samples/lead-manifest.json`.
- Pitch centers come from the original SFZ mappings. Filename octave labels are not used to infer MIDI pitch.
- Browser processing: modest drive, filtering, dynamics, quiet tempo delay, shaped playback-rate bends and delayed vibrato. Bend transitions remain modeled; they are not recorded bends. No voice or generated oscillator instrument.

Previously used FreePats/FluidR3 lab assets are retired from this version. The old product prototype is separate.
