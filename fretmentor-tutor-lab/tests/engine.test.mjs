import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {Composer} from '../engine/composer.mjs';
import {TUNING,chordAt,BEAT_SECONDS,LOOP_BEATS,pc} from '../engine/harmony.mjs';
import {FingeringEngine,positions,movementCost} from '../engine/fingering.mjs';
import {Transport} from '../engine/transport.mjs';
const composer=new Composer(),signatures=new Set(),layouts=new Set(),articulations={};let totalNotes=0,bends=0,maxFretJump=0,maxPitchJump=0,crossBarPhrases=0;
for(let i=0;i<300;i++){
 const start=4*(i%12),p=composer.compose(start,7134+i);assert.equal(p.endBeat-start,16);assert(p.phrases.length===2||p.phrases.length===3);layouts.add(p.layout);signatures.add(p.events.map(e=>`${e.midi}@${e.startBeat-start}`).join(','));
 for(const phrase of p.phrases){const a=p.events.filter(e=>e.phraseIndex===p.phrases.indexOf(phrase));const duration=a.reduce((s,e)=>s+e.durationBeats,0),rest=phrase.rhythm.rests.reduce((s,r)=>s+r.duration,0);assert(Math.abs(duration+rest-phrase.length)<1e-8);assert(rest>.4);const last=a.at(-1);assert(chordAt(last.startBeat).tones.includes(pc(last.midi)));if(Math.floor(a[0].startBeat/4)!==Math.floor(last.startBeat/4))crossBarPhrases++;}
 for(let j=0;j<p.events.length;j++){const e=p.events[j];totalNotes++;articulations[e.articulation]=(articulations[e.articulation]||0)+1;assert.equal(TUNING[e.string-1]+e.fret,e.bendFromMidi??e.midi);assert(e.fret>=3&&e.fret<=12);assert(e.startBeat>=start&&e.startBeat+e.durationBeats<=p.endBeat);assert(e.velocity>=.3&&e.velocity<=.94);assert.equal(e.chord,chordAt(e.startBeat).name);if(e.articulation==='bend'){bends++;assert(e.bendFromMidi!==null);}if(j){const prev=p.events[j-1];assert(e.startBeat>=prev.startBeat+prev.durationBeats-1e-8,'Overlap');maxFretJump=Math.max(maxFretJump,Math.abs(e.fret-prev.fret));maxPitchJump=Math.max(maxPitchJump,Math.abs(e.midi-prev.midi));}}
 assert(composer.memory.phrases.length<=4);
}
assert(maxPitchJump<=12);assert(maxFretJump<=5);assert(signatures.size>60);assert(layouts.size===5);assert(crossBarPhrases>0);assert(bends>0);assert(articulations['hammer-on']>0);
const ctx={currentTime:0},renderer={startBacking:()=>{},play:(e,t)=>({startTime:t+(e.timingOffsetSeconds||0),endTime:t+(e.timingOffsetSeconds||0)+e.durationBeats*BEAT_SECONDS})},transport=new Transport(ctx,renderer);transport.start();for(const time of [0,.001,.18,4.3,4.35,30,105,500]){ctx.currentTime=time;const b=transport.nextBar();assert.equal(b%4,0);assert(transport.timeAt(b)>=time+.179);}
const n=[63,65,67,70].map(midi=>({midi})),hand={string:2,fret:8},r=new FingeringEngine().solve(n,hand);let best=Infinity;function visit(i,last,cost){if(i===n.length){best=Math.min(best,cost);return;}for(const p of positions(n[i].midi))visit(i+1,p,cost+movementCost(last,p)+.13*Math.abs(p.fret-7));}visit(0,hand,0);let actual=0,last=hand;for(const p of r){actual+=movementCost(last,p)+.13*Math.abs(p.fret-7);last=p;}assert(Math.abs(actual-best)<1e-8);
const meta=JSON.parse(readFileSync(new URL('../tracks/backing.json',import.meta.url)));assert.equal(meta.loopBeats,LOOP_BEATS);assert(Math.abs(meta.loopDurationSeconds-LOOP_BEATS*BEAT_SECONDS)<1e-8);assert.equal(meta.license,'CC BY 3.0');assert(!meta.sourceStems.includes('leadguitar'));
assert.deepEqual(new Composer().compose(0,555),new Composer().compose(0,555));
console.log(JSON.stringify({performances:300,totalNotes,uniqueSequences:signatures.size,layouts:[...layouts],bends,crossBarPhrases,maxFretJump,maxPitchJump,articulations},null,2));
