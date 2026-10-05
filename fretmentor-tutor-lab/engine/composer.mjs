import {chordAt,pc,role,noteName,nearestPitch,makeRng,choose} from './harmony.mjs';
import {RhythmEngine} from './rhythm.mjs';
import {FingeringEngine} from './fingering.mjs';
// Original diatonic gestures, never independent random scale notes.
const motifs=[
 {id:'rise-return',steps:[0,2,4,2],direction:1},
 {id:'lean-rise',steps:[0,-1,1,3],direction:1},
 {id:'fall-answer',steps:[0,2,1,-1],direction:-1},
 {id:'turn',steps:[0,1,-1,2],direction:1},
 {id:'echo',steps:[0,2,0,3],direction:1},
 {id:'sigh',steps:[0,-2,-1,1],direction:-1}
];
const scale=Array.from({length:23},(_,i)=>57+i).filter(n=>[0,2,4,5,7,9,11].includes(pc(n)));
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const chordNear=(chord,near)=>scale.filter(n=>n>=57&&n<=74&&chord.tones.includes(pc(n))).sort((a,b)=>Math.abs(a-near)-Math.abs(b-near))[0];
export class MotifMemory{
 constructor(){this.phrases=[];this.hand={string:2,fret:5};this.lastMotif=null;this.serial=0;}
 remember(performance){this.phrases.push(...performance.phrases);this.phrases=this.phrases.slice(-4);this.lastMotif=performance.motif;const e=performance.events.at(-1);this.hand={string:e.string,fret:e.fret};this.serial++;}
}
export class PhrasePlanner{
 plan(rng,memory,startBeat){
  const relationship=memory.lastMotif?choose(rng,['VARIATE','ANSWER','DEVELOP','CONTRAST']):'NEW';
  const motif=relationship==='CONTRAST'||!memory.lastMotif?choose(rng,motifs):memory.lastMotif;
  const register=choose(rng,[64,65,67]);const rhythmIndex=Math.floor(rng()*6);
  const intents=['QUESTION','DEVELOPMENT','CLIMAX','RESOLUTION'];let previousTarget=register;
  return {motif,relationship,rhythmIndex,pickup:rng()>.45,phrases:intents.map((intent,i)=>{
   const chord=chordAt(startBeat+i*4);const targetClass=i===0?chord.tones.at(-1):i===2?chord.tones[2]:chord.tones[1];
   const target=i===2?chordNear(chord,Math.min(75,previousTarget+5)):nearestPitch(targetClass,i===0?register:i===3?previousTarget-3:previousTarget);previousTarget=target;
   return {intent,relationship:i===0?relationship:i===1?'DEVELOP':i===2?'CONTRAST':'RELEASE',chord:chord.name,target,goal:i===0?'Abrir a ideia':i===1?'Responder com o mesmo motivo':i===2?'Subir até o clímax':'Resolver e deixar espaço',contour:i===2?'ascending':i===3?'descending':motif.direction>0?'ascending-turn':'descending-turn',articulationGoal:i===2&&['rise-return','lean-rise','turn'].includes(motif.id)?'bend':'sustain',density:i===2?'high':i===3?'low':'medium',intensity:[.56,.64,.86,.56][i],startBeat:startBeat+i*4,motifId:motif.id};
  })};
 }
}
export class PitchEngine{
 compose(phrase,rhythm,motif,previous){
  const chord=chordAt(phrase.startBeat);const count=rhythm.notes.length;
  let anchor=chordNear(chord,phrase.intent==='CLIMAX'?phrase.target-5:phrase.intent==='RESOLUTION'?phrase.target+4:phrase.target-3);
  if(previous)anchor=chordNear(chord,phrase.intent==='CLIMAX'?Math.min(phrase.target-3,previous.at(-1).midi+2):phrase.intent==='RESOLUTION'?previous.at(-1).midi-2:(previous.at(-1).midi+phrase.target-3)/2);
  const idx=scale.indexOf(anchor);let pitches=rhythm.notes.map((n,i)=>{
   if(i===count-1)return phrase.target;
   if(phrase.intent==='RESOLUTION')return chordNear(chord,anchor-i*2);
   if(phrase.intent==='CLIMAX')return scale[clamp(idx+i,0,scale.length-1)];
   const step=motif.steps[i%motif.steps.length];const ceiling=scale.indexOf(phrase.target)+2;return scale[clamp(idx+step,0,Math.max(idx,ceiling))];
  });
  // The destination is chosen first; a stepwise approach connects it to the gesture.
  if(count>3){const ti=scale.indexOf(phrase.target);pitches[count-2]=scale[clamp(ti+(phrase.contour.startsWith('ascending')?-1:1),0,scale.length-1)];}
  pitches=pitches.map(n=>clamp(n,57,76));
  return rhythm.notes.map((n,i)=>({midi:pitches[i],pitch:noteName(pitches[i]),startBeat:phrase.startBeat+n.beat,durationBeats:n.duration,target:i===count-1,chord:chord.name,harmonicRole:role(pitches[i],chord),phraseRole:phrase.intent,phraseIndex:Math.round((phrase.startBeat-rhythm.baseBeat)/4)||0,motifId:motif.id,wantsBend:phrase.articulationGoal==='bend'&&i===count-1,legatoCandidate:i>0&&Math.abs(pitches[i]-pitches[i-1])<=2}));
 }
}
export class ExpressionEngine{
 apply(events,phrases){return events.map((e,i)=>{
  const p=phrases[e.phraseIndex],prev=events[i-1],sameString=prev&&prev.string===e.string,interval=prev?e.midi-prev.midi:0;
  let articulation=e.target?'sustain':e.durationBeats<.25?'staccato':'pick';
  if(sameString&&Math.abs(interval)<=2&&Math.abs(interval)>0&&e.durationBeats<.5)articulation=interval>0?'hammer-on':'pull-off';
  if(sameString&&interval>2&&interval<=5&&e.phraseRole==='DEVELOPMENT'&&!e.target)articulation='slide';
  const bend=e.wantsBend&&e.string<=3&&e.fret>=5&&e.durationBeats>.6;
  if(bend)articulation='bend';
  const local=(e.startBeat-p.startBeat)/4;const velocity=clamp(p.intensity+.07*Math.sin(local*Math.PI)+(e.target?.06:0)-(articulation==='hammer-on'?.09:0),.35,.95);
  return {...e,id:'note-'+i,velocity,articulation,bendTarget:bend?e.midi:null,bendFromMidi:bend?e.frettedMidi:null,slideFromMidi:articulation==='slide'?prev.midi:null,vibrato:{enabled:e.target&&e.durationBeats>.6,rate:5.1,depthCents:e.phraseRole==='CLIMAX'?17:11,delaySeconds:.3},timingOffsetSeconds:local===0?0:e.target?.008:.006,attackSeconds:articulation==='hammer-on'||articulation==='pull-off'?.015:.003};
 });}
}
export class Composer{
 constructor(memory=new MotifMemory()){this.memory=memory;this.planner=new PhrasePlanner();this.rhythm=new RhythmEngine();this.pitch=new PitchEngine();this.fingering=new FingeringEngine();this.expression=new ExpressionEngine();}
 compose(startBeat,seed){const plan=this.planner.plan(makeRng(seed),this.memory,startBeat);let events=[],previous=null,previousRhythm=null;
  for(let i=0;i<4;i++){const phrase=plan.phrases[i],rhythm=this.rhythm.plan(plan.rhythmIndex,phrase.intent,previousRhythm);if(i===1&&plan.pickup){rhythm.notes[0]={beat:0,duration:.6};rhythm.rests=[];let cursor=0;for(const n of rhythm.notes){if(n.beat>cursor)rhythm.rests.push({beat:cursor,duration:n.beat-cursor});cursor=n.beat+n.duration;}if(cursor<4)rhythm.rests.push({beat:cursor,duration:4-cursor});}rhythm.baseBeat=startBeat;phrase.rhythm=rhythm;phrase.targetRole=role(phrase.target,chordAt(phrase.startBeat));const notes=this.pitch.compose(phrase,rhythm,plan.motif,previous);notes.forEach(e=>e.phraseIndex=i);events.push(...notes);previous=notes;previousRhythm=rhythm;}
  if(plan.pickup){const destination=plan.phrases[1].target;const pickup={...events[0],midi:destination-1,pitch:noteName(destination-1),startBeat:startBeat+3.65,durationBeats:.25,target:false,harmonicRole:'chromatic approach',phraseRole:'PICKUP',phraseIndex:0,wantsBend:false,legatoCandidate:false,leadsTo:{midi:destination,chord:plan.phrases[1].chord,role:plan.phrases[1].targetRole}};events.push(pickup);events.sort((a,b)=>a.startBeat-b.startBeat);const first=events.find(e=>e.phraseIndex===1);first.midi=destination;first.pitch=noteName(destination);first.harmonicRole=plan.phrases[1].targetRole;plan.phrases[0].rhythm.rests=plan.phrases[0].rhythm.rests.filter(r=>r.beat+r.duration<=3.65);plan.phrases[0].rhythm.rests.push({beat:3.9,duration:.1});}
  events=events.map(e=>({...e,frettedMidi:e.wantsBend?e.midi-2:e.midi}));events=this.fingering.solve(events,this.memory.hand);events=this.expression.apply(events,plan.phrases);
  for(const phrase of plan.phrases){const phraseEvents=events.filter(e=>e.startBeat>=phrase.startBeat&&e.startBeat<phrase.startBeat+4);phrase.rhythm.rests=[];let cursor=0;for(const e of phraseEvents){const local=e.startBeat-phrase.startBeat;if(local>cursor)phrase.rhythm.rests.push({beat:cursor,duration:local-cursor});cursor=local+e.durationBeats;}if(cursor<4)phrase.rhythm.rests.push({beat:cursor,duration:4-cursor});}
  const performance={seed,startBeat,endBeat:startBeat+16,motif:plan.motif,relationship:plan.relationship,phrases:plan.phrases,events};this.memory.remember(performance);return performance;
 }
}
