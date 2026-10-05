import {chordAt,pc,role,noteName,nearestPitch,makeRng,choose} from './harmony.mjs';
import {gestures,layouts} from './vocabulary.mjs';
import {FingeringEngine} from './fingering.mjs';
export class MotifMemory{
 constructor(){this.phrases=[];this.hand={string:2,fret:8};this.lastMotif=null;this.serial=0;this.lastPitch=63;this.lastLayout=null;}
 remember(p){this.phrases.push(...p.phrases);this.phrases=this.phrases.slice(-4);this.lastMotif=p.motif;const e=p.events.at(-1);this.hand={string:e.string,fret:e.fret};this.lastPitch=e.midi;this.lastLayout=p.layout;this.serial++;}
}
export class PhrasePlanner{
 plan(rng,memory,startBeat){
  const options=layouts.filter(l=>l.id!==memory.lastLayout);const layout=choose(rng,options);let original=null;
  const phrases=layout.segments.map(([offset,length,relationship],index)=>{
   const available=gestures.filter(g=>g.length===length);let gesture;
   if(index===0&&memory.lastMotif?.length===length&&rng()<.5){gesture=memory.lastMotif;relationship='VARIATE';}
   else if(['VARIATE','REPEAT','DEVELOP'].includes(relationship)&&original?.length===length)gesture=original;
   else gesture=choose(rng,available);
   if(index===0)original=gesture;
   // Displacement belongs to the gesture. It is not jitter applied independently to notes.
   const displacement=relationship==='VARIATE'&&gesture.notes.at(-1)[0]+gesture.notes.at(-1)[1]<length-.22?choose(rng,[0,1/6]):0;
   return {startBeat:startBeat+offset,endBeat:startBeat+offset+length,length,gesture,relationship,intent:relationship,motifId:gesture.id,displacement,variation:['VARIATE','DEVELOP','ANSWER'].includes(relationship)?choose(rng,['tail','rhythm','tail']):'none',intensity:index===0?.86:relationship==='ANSWER'?.82:relationship==='DEVELOP'?.96:.90,ending:relationship==='ANSWER'?'settle':'continue'};
  });return {layout:layout.id,phrases,motif:original,relationship:phrases[0].relationship};
 }
}
export class PitchEngine{
 compose(phrase,previousPitch){
  const ch=chordAt(phrase.startBeat),home=nearestPitch(ch.root,60,55,65);let last=previousPitch;
  const notes=phrase.gesture.notes.map(([beat,duration,interval,articulation,weight],i)=>{
   let desired=home+interval;
   // Keep the hook intact; vary only its tail with a diatonic neighbour.
   if(phrase.variation==='tail'&&i===phrase.gesture.notes.length-2){const minorSteps=[0,2,3,5,7,8,10,12];const idx=minorSteps.indexOf(interval);if(idx>0)desired=home+minorSteps[idx-1];}const candidates=[desired-12,desired,desired+12].filter(n=>n>=55&&n<=76);
   let midi=candidates.sort((a,b)=>(Math.abs(a-desired)+1.1*Math.abs(a-last))-(Math.abs(b-desired)+1.1*Math.abs(b-last)))[0];
   if(midi===undefined)midi=nearestPitch(pc(desired),last);
   const startBeat=phrase.startBeat+beat+phrase.displacement,current=chordAt(startBeat),target=i===phrase.gesture.notes.length-1;
   // Preserve original melody unless its destination conflicts with the chord at arrival.
   if(target&&!current.tones.includes(pc(midi))){const chordNotes=current.tones.map(t=>nearestPitch(t,midi));midi=chordNotes.sort((a,b)=>Math.abs(a-midi)-Math.abs(b-midi))[0];}
   if(target&&phrase.ending==='settle'){const roots=nearestPitch(current.root,midi);if(Math.abs(roots-midi)<=3)midi=roots;}
   if(phrase.relationship==='ANSWER'&&i===0&&Math.abs(midi-last)>7)midi=nearestPitch(pc(midi),last);
   last=midi;
   const isBlue=interval===6;
   return {midi,pitch:noteName(midi),startBeat,durationBeats:phrase.variation==='rhythm'&&i===0?duration*.8:duration,target,chord:current.name,harmonicRole:isBlue?'blue approach':role(midi,current),phraseRole:phrase.relationship,phraseIndex:0,motifId:phrase.gesture.id,requestedArticulation:articulation,dynamicWeight:weight,wantsBend:articulation==='bend',legatoCandidate:articulation==='hammer-on'};
  });return notes;
 }
}
export class ExpressionEngine{
 apply(events,phrases){return events.map((e,i)=>{
  const p=phrases[e.phraseIndex],prev=events[i-1];let articulation=e.requestedArticulation;const interval=prev?e.midi-prev.midi:0;
  if(articulation==='hammer-on'&&(!prev||prev.string!==e.string||interval<=0||interval>3))articulation='soft pick';
  const bend=e.wantsBend&&e.string<=3&&e.fret>=4;
  if(e.wantsBend&&!bend)articulation='sustain';
  const velocity=Math.min(.94,Math.max(.30,e.dynamicWeight*p.intensity));
  const long=e.durationBeats>.6;const delayedVibrato=e.durationBeats>1? .55:.32;
  // Bend onset, travel, hold and release are deliberately shaped; not an instant pitch jump.
  return {...e,id:'note-'+i,velocity,articulation,bendTarget:bend?e.midi:null,bendFromMidi:bend?e.midi-2:null,frettedMidi:bend?e.midi-2:e.midi,bend:{onsetSeconds:.07,riseSeconds:.30,releaseSeconds:e.durationBeats>1.1?.12:0},slideFromMidi:null,vibrato:{enabled:long&&['sustain','bend'].includes(articulation),rate:4.7+(e.dynamicWeight-.5)*2,depthCents:9+e.dynamicWeight*15,delaySeconds:delayedVibrato},timingOffsetSeconds:articulation==='hammer-on'?.008:articulation==='accent'?.005:.018,attackSeconds:articulation==='soft pick'?.008:.002};
 });}
}
export class Composer{
 constructor(memory=new MotifMemory()){this.memory=memory;this.planner=new PhrasePlanner();this.pitch=new PitchEngine();this.fingering=new FingeringEngine();this.expression=new ExpressionEngine();}
 compose(startBeat,seed){const plan=this.planner.plan(makeRng(seed),this.memory,startBeat);let events=[],last=this.memory.lastPitch;
  for(let i=0;i<plan.phrases.length;i++){const notes=this.pitch.compose(plan.phrases[i],last);notes.forEach(e=>e.phraseIndex=i);events.push(...notes);last=notes.at(-1).midi;}
  events=events.map(e=>({...e,frettedMidi:e.wantsBend?e.midi-2:e.midi}));events=this.fingering.solve(events,this.memory.hand);events=this.expression.apply(events,plan.phrases);
  // Re-solve after incompatible bends are downgraded; physical pitch must always agree.
  events=this.fingering.solve(events,this.memory.hand);
  for(const p of plan.phrases){const a=events.filter(e=>e.startBeat>=p.startBeat&&e.startBeat<p.endBeat);const target=a.at(-1);p.target=target.midi;p.targetRole=target.harmonicRole;p.chord=target.chord;p.rhythm={notes:a.map(e=>({beat:e.startBeat-p.startBeat,duration:e.durationBeats})),rests:[]};let cursor=p.startBeat;for(const e of a){if(e.startBeat>cursor)p.rhythm.rests.push({beat:cursor-p.startBeat,duration:e.startBeat-cursor});cursor=e.startBeat+e.durationBeats;}if(cursor<p.endBeat)p.rhythm.rests.push({beat:cursor-p.startBeat,duration:p.endBeat-cursor});}
  const performance={seed,startBeat,endBeat:startBeat+16,motif:plan.motif,layout:plan.layout,relationship:plan.relationship,phrases:plan.phrases,events};this.memory.remember(performance);return performance;
 }
}
