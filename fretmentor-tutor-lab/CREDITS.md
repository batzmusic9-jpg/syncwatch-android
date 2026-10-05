# Sample credits and licenses

## Electric guitar — FreePats FSBS Clean #1
- Author / project: FreePats (FSBS electric guitar sound bank).
- Source: https://github.com/freepats/electric-guitar-FSBS-clean
- Authoritative instrument page: https://freepats.zenvoid.org/ElectricGuitar/clean-electric-guitar.html
- Version: 2026-08-07. Recorded Fender electric guitar, bridge pickup.
- License: CC0 1.0 public domain dedication, confirmed in the upstream README and LICENSE.txt. Full dedication in `licenses/FreePats-CC0.txt`.
- Adaptations: selected 8 pitch centers, 2 recorded velocity layers and 2 independent takes; trimmed to 4.5 seconds, initial silence removed, converted to mono 44.1 kHz / 96 kbps MP3. Used for rhythm and lead; pitch interpolation, envelopes, bend/slide/vibrato and subtle room added at runtime.

## Fingered electric bass and GM drums — FluidR3
- Sound bank: FluidR3 GM, by Frank Wen and the Fluid soundfont contributors.
- Pre-rendering: Benjamin Gleitzman / `gleitz/midi-js-soundfonts`; retrieved via the `dave4mpls/midi-js-soundfonts-with-drums` fork, FluidR3_GM directory.
- Source: https://github.com/gleitz/midi-js-soundfonts
- Retrieved source: https://github.com/dave4mpls/midi-js-soundfonts-with-drums/tree/gh-pages/FluidR3_GM
- License of the **audio**: Creative Commons Attribution 3.0 US, as specified in the upstream sound bank section. https://creativecommons.org/licenses/by/3.0/us/
- Attribution: “FluidR3 by Frank Wen and contributors; browser sample rendering by Benjamin Gleitzman. Licensed CC BY 3.0.”
- Adaptations: extracted fingered bass E1, A1, C2, E2, G2; drums C2 (kick / MIDI 36), D2 (snare / 38), Gb2 (closed hat / 42), Bb2 (open hat / 46), Db2 (side stick / 37). Audio bytes unchanged; gain, filtering, envelope and pitch changes at runtime. The top-level MIT code license is NOT substituted for the audio license.

`./samples/provenance.json` maps each shipped file to its upstream sample and SHA-256. Upstream commit SHAs are recorded in README.md. No sounds scraped from commercial recordings; no generative music; no speech.
