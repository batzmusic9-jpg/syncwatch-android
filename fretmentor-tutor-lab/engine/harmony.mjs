export const BPM=82, BEAT_SECONDS=60/BPM;
export const TUNING=[64,59,55,50,45,40]; // indexed by string number minus one
export const CHORDS=[
 {name:'Am7',root:9,tones:[9,0,4,7],roles:['root','♭3rd','5th','♭7th'],bass:33,voicing:[57,60,64,67]},
 {name:'Fmaj7',root:5,tones:[5,9,0,4],roles:['root','3rd','5th','7th'],bass:29,voicing:[53,57,60,64]},
 {name:'C',root:0,tones:[0,4,7],roles:['root','3rd','5th'],bass:36,voicing:[55,60,64,67]},
 {name:'G',root:7,tones:[7,11,2],roles:['root','3rd','5th'],bass:31,voicing:[55,59,62,67]}
];
export const pc=n=>((n%12)+12)%12;
export const names=['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'];
export const noteName=n=>names[pc(n)]+(Math.floor(n/12)-1);
export const chordAt=beat=>CHORDS[((Math.floor(beat/4)%4)+4)%4];
export function role(midi,chord){const i=chord.tones.indexOf(pc(midi));return i>=0?chord.roles[i]:({2:'9th',5:'11th',9:'13th'}[pc(midi-chord.root)]||'passing tone');}
export const nearestPitch=(pitchClass,near,min=57,max=76)=>Array.from({length:max-min+1},(_,i)=>min+i).filter(n=>pc(n)===pitchClass).sort((a,b)=>Math.abs(a-near)-Math.abs(b-near))[0];
export function makeRng(seed){let s=seed>>>0;return ()=>{s+=0x6D2B79F5;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return ((t^t>>>14)>>>0)/4294967296;};}
export const choose=(rng,a)=>a[Math.floor(rng()*a.length)];
