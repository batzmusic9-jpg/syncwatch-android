import {TUNING} from './harmony.mjs';
export function positions(midi){return TUNING.flatMap((open,i)=>{const fret=midi-open;return fret>=3&&fret<=12?[{string:i+1,fret}]:[];});}
export function movementCost(a,b){return .85*Math.abs(a.fret-b.fret)+1.2*Math.abs(a.string-b.string)+Math.max(0,Math.abs(a.fret-b.fret)-3)*3;}
export class FingeringEngine{
 solve(events,hand={string:2,fret:5}){
  const layers=[];
  for(let i=0;i<events.length;i++){
   const candidates=positions(events[i].frettedMidi??events[i].midi);if(!candidates.length)throw Error('Unplayable pitch '+events[i].midi);
   const prev=layers[i-1];
   layers.push(candidates.map(p=>{
    const positionCost=.13*Math.abs(p.fret-7)+(events[i].wantsBend&&(p.string>3||p.fret<5)?7:0);
    if(!prev)return {...p,cost:movementCost(hand,p)+positionCost,parent:-1};
    let best=Infinity,parent=-1;prev.forEach((q,j)=>{const legatoPenalty=events[i].legatoCandidate&&q.string!==p.string?2:0;const c=q.cost+movementCost(q,p)+positionCost+legatoPenalty;if(c<best){best=c;parent=j;}});
    return {...p,cost:best,parent};
   }));
  }
  let j=layers.at(-1).reduce((best,p,i,a)=>p.cost<a[best].cost?i:best,0);const output=events.map(e=>({...e}));
  for(let i=layers.length-1;i>=0;i--){const p=layers[i][j];output[i].string=p.string;output[i].fret=p.fret;j=p.parent;}
  return output;
 }
}
