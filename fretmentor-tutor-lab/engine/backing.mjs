import {CHORDS,chordAt} from './harmony.mjs';
export function backingBar(bar){
 const base=bar*4,ch=chordAt(base),next=CHORDS[(bar+1)%4],cycle=Math.floor(bar/4),variant=cycle%4,events=[];
 const add=(instrument,key,beat,duration,velocity,offset=0)=>events.push({instrument,key,startBeat:base+beat,durationBeats:duration,velocity,timingOffsetSeconds:offset,articulation:'pick'});
 const kicks=[[0,1.75,2.5],[0,2,3.25],[0,1.5,2.75],[0,2.5]][variant];
 kicks.forEach((b,i)=>add('drums','kick',b,.3,i===0?.85:.65));
 [1,3].forEach(b=>add('drums','snare',b,.3,.69,.011));
 if(bar%2===1)add('drums','snare',2.75,.12,.19,.016);
 for(let i=0;i<8;i++)add('drums',i===7&&bar%4===3?'openhat':'hat',i/2,i===7?.3:.13,[.4,.23,.32,.22,.37,.22,.32,.2][i],i%2?.007:0);
 if(variant===2)add('drums','rim',3.75,.16,.22,.012);
 const bass=[[0,ch.bass,.9,.67],[1.75,ch.bass+7,.5,.5],[2.5,ch.bass+12,.55,.55],[3.5,next.bass+(next.bass<ch.bass?1:-1),.32,.4]];
 if(variant===1)bass[2]=[2.5,ch.bass+(ch.tones.length===4?10:7),.55,.51];
 bass.forEach(([beat,midi,duration,vel])=>add('bass',midi,beat,duration,vel,.005));
 const strokes=variant===3?[[.5,.24,.22],[2,.45,.3],[3.5,.17,.17]]:[[.0,.48,.32],[1.5,.22,.23],[2.75,.32,.27]];
 strokes.forEach(([beat,duration,vel],j)=>{const voice=j===1?ch.voicing.slice(1):ch.voicing;const order=j%2?[...voice].reverse():voice;order.forEach((midi,k)=>add('guitar',midi,beat,duration,vel,j%2?.013+k*.012:k*.016));});
 return events;
}
