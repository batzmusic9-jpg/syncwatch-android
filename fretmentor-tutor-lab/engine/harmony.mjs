export const BPM=55, BEAT_SECONDS=60/BPM, LOOP_BEATS=96;
export const TUNING=[64,59,55,50,45,40];
export const CHORDS=[
 {name:'Cm7',root:0,tones:[0,3,7,10],roles:['root','♭3rd','5th','♭7th']},
 {name:'Fm7',root:5,tones:[5,8,0,3],roles:['root','♭3rd','5th','♭7th']},
 {name:'Gm7',root:7,tones:[7,10,2,5],roles:['root','♭3rd','5th','♭7th']}
];
export const harmonicMap=[[0,0],[16,1],[24,0],[32,2],[36,1],[40,0],[46,2]];
export const pc=n=>((n%12)+12)%12;
export const names=['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'];
export const noteName=n=>names[pc(n)]+(Math.floor(n/12)-1);
export function chordAt(beat){const local=((beat%48)+48)%48;let chord=0;for(const [b,i] of harmonicMap)if(local>=b)chord=i;return CHORDS[chord];}
export function role(midi,chord){const i=chord.tones.indexOf(pc(midi));return i>=0?chord.roles[i]:({2:'9th',5:'11th',6:'blue approach',9:'13th'}[pc(midi-chord.root)]||'approach / color');}
export const nearestPitch=(pitchClass,near,min=55,max=76)=>Array.from({length:max-min+1},(_,i)=>min+i).filter(n=>pc(n)===pc(pitchClass)).sort((a,b)=>Math.abs(a-near)-Math.abs(b-near))[0];
export function makeRng(seed){let s=seed>>>0;return ()=>{s+=0x6D2B79F5;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return ((t^t>>>14)>>>0)/4294967296;};}
export const choose=(rng,a)=>a[Math.floor(rng()*a.length)];
