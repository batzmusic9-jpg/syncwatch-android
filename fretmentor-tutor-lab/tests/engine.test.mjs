import assert from 'node:assert/strict';
import {Composer} from '../engine/composer.mjs';
import {TUNING,chordAt,role,pc} from '../engine/harmony.mjs';
import {FingeringEngine,positions,movementCost} from '../engine/fingering.mjs';
import {Transport} from '../engine/transport.mjs';
import {backingBar} from '../engine/backing.mjs';
const composer=new Composer(),signatures=new Set();let totalNotes=0,pickups=0,bends=0,maxFretJump=0,maxPitchJump=0;
for(let i=0;i<200;i++){
 const start=4*(1+i%4),p=composer.compose(start,1234+i);assert.equal(p.endBeat-start,16);assert.deepEqual(p.phrases.map(p=>p.intent),['QUESTION','DEVELOPMENT','CLIMAX','RESOLUTION']);
 signatures.add(p.events.map(e=>`${e.midi}@${e.startBeat-start}`).join(','));
 for(const phrase of p.phrases){const duration=p.events.filter(e=>e.startBeat>=phrase.startBeat&&e.startBeat<phrase.startBeat+4).reduce((s,e)=>s+e.durationBeats,0);assert(Math.abs(duration+phrase.rhythm.rests.reduce((s,r)=>s+r.duration,0)-4)<1e-8,'Rest accounting');assert(phrase.rhythm.rests.reduce((s,r)=>s+r.duration,0)>.35);assert(chordAt(phrase.startBeat).tones.includes(pc(phrase.target)));assert.equal(phrase.motifId,p.motif.id);assert(p.events.some(e=>e.target&&e.midi===phrase.target&&e.startBeat>=phrase.startBeat&&e.startBeat<phrase.startBeat+4));}
 for(let j=0;j<p.events.length;j++){
  const e=p.events[j];totalNotes++;assert.equal(TUNING[e.string-1]+e.fret,e.bendFromMidi??e.midi);assert(e.string>=1&&e.string<=6&&e.fret>=3&&e.fret<=12);assert(e.startBeat>=start&&e.startBeat+e.durationBeats<=p.endBeat);assert(e.velocity>=.35&&e.velocity<=.95);assert(e.harmonicRole);assert(e.motifId);
  if(e.phraseRole==='PICKUP'){pickups++;assert.equal(e.midi+1,e.leadsTo.midi);}else assert.equal(e.harmonicRole,role(e.midi,chordAt(e.startBeat)));
  if(e.articulation==='bend'){bends++;assert(e.string<=3);assert.equal(e.midi-e.bendFromMidi,2);}
  if(j){const prev=p.events[j-1];assert(e.startBeat>=prev.startBeat+prev.durationBeats,'Monophonic overlap');maxFretJump=Math.max(maxFretJump,Math.abs(e.fret-prev.fret));maxPitchJump=Math.max(maxPitchJump,Math.abs(e.midi-prev.midi));}
 }
 assert(composer.memory.phrases.length<=4);
}
assert(signatures.size>=30);assert(bends>0);assert(pickups>0);assert(maxFretJump<=3);assert(maxPitchJump<=9);
// Dynamic programming should equal an exhaustive optimum, rather than a local greedy result.
const notes=[64,67,69,72].map(midi=>({midi})),hand={string:2,fret:5},result=new FingeringEngine().solve(notes,hand);let best=Infinity;
function enumerate(i,last,cost){if(i===notes.length){best=Math.min(best,cost);return;}for(const p of positions(notes[i].midi))enumerate(i+1,p,cost+movementCost(last,p)+.13*Math.abs(p.fret-7));}enumerate(0,hand,0);let actual=0,last=hand;for(const p of result){actual+=movementCost(last,p)+.13*Math.abs(p.fret-7);last=p;}assert(Math.abs(actual-best)<1e-8);
// Button boundaries include the lookahead guard even when pressed milliseconds before a bar.
const ctx={currentTime:0},renderer={play:(e,t)=>({startTime:t,endTime:t+e.durationBeats*60/82})},transport=new Transport(ctx,renderer);transport.origin=.12;
for(const offset of [0,.001,.1,1,2.9,3,30,57]){ctx.currentTime=offset;const b=transport.nextBar();assert.equal(b%4,0);assert(transport.timeAt(b)>=ctx.currentTime+.159);}
for(let bar=0;bar<32;bar++){const events=backingBar(bar);for(const e of events)assert(e.startBeat>=bar*4&&e.startBeat<(bar+1)*4);assert(events.some(e=>e.instrument==='bass'));assert(events.some(e=>e.key==='snare'));}
console.log(JSON.stringify({performances:200,totalNotes,uniqueMelodies:signatures.size,pickups,bends,maxFretJump,maxPitchJump,checks:'targets, rests, arc, memory, fingering optimum, physical bends, monophony, transport, backing'},null,2));

const one=new Composer().compose(4,777),two=new Composer().compose(4,777);assert.deepEqual(one,two,'Seed replay');
