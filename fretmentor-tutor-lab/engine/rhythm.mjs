// Rests are explicit, including the breaths at the end of every phrase.
const cells=[
 [[0,.65],[1,.35],[1.5,.35],[2.5,.85]],
 [[.5,.35],[1,.7],[2,.35],[2.5,.9]],
 [[0,.9],[1.5,.3],[2,.3],[2.5,.85]],
 [[0,.35],[.5,.65],[1.5,.65],[2.5,.9]],
 [[.5,.7],[1.5,.35],[2,.35],[2.5,.85]],
 [[0,.65],[1,.35],[1.75,.4],[2.5,.9]]
];
export class RhythmEngine{
 plan(index,intent,previous){
  let notes=cells[index%cells.length].map(([beat,duration])=>({beat,duration}));
  if(intent==='DEVELOPMENT'&&previous){notes=previous.notes.map(n=>({...n}));notes[1].beat=Math.min(notes[2].beat-.35,notes[1].beat+.25);notes[1].duration=Math.min(notes[1].duration,.3);}
  if(intent==='CLIMAX')notes=[{beat:0,duration:.7},{beat:1,duration:.3},{beat:1.5,duration:.18},{beat:1.75,duration:.18},{beat:2,duration:.3},{beat:2.5,duration:.9}];
  if(intent==='RESOLUTION')notes=[{beat:.25,duration:.55},{beat:1.25,duration:.55},{beat:2.25,duration:1.3}];
  const rests=[];let cursor=0;for(const n of notes){if(n.beat>cursor)rests.push({beat:cursor,duration:n.beat-cursor});cursor=n.beat+n.duration;}if(cursor<4)rests.push({beat:cursor,duration:4-cursor});
  return {notes,rests};
 }
}
export const rhythmVocabulary=cells;
