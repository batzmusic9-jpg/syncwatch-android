
(() => {
'use strict';
const NOTES=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
const TUNING=[40,45,50,55,59,64];
const AM_PENTA=[9,0,2,4,7];
const CHORDS=[
  {name:'Am',root:9,tones:[9,0,4]},
  {name:'F',root:5,tones:[5,9,0]},
  {name:'C',root:0,tones:[0,4,7]},
  {name:'G',root:7,tones:[7,11,2]}
];
const DEFAULT_SKILLS={
  'Ouvido → braço':42,'Fretboard':36,'Ritmo':62,'Construção de frase':42,'Finalização':38,'Chord targeting':32,'Pentatônica consciente':45,'Motivos':36,'Vocabulário':28,'Dinâmica':60
};
const EXERCISES=[
  {id:'phrase',tag:'FRASE',title:'Phrase Builder',desc:'Comece e termine em notas definidas usando poucas notas.',cta:'TREINAR DIREÇÃO'},
  {id:'target',tag:'HARMONIA',title:'Target Notes',desc:'Aprenda a pousar conscientemente nas notas importantes.',cta:'TREINAR RESOLUÇÃO'},
  {id:'rhythm',tag:'RITMO',title:'Rhythm Lab',desc:'Separe ritmo de pitch e copie padrões de ataque.',cta:'TREINAR RITMO'},
  {id:'fretboard',tag:'MAPA',title:'Fretboard Radar',desc:'Encontre rapidamente notas e graus em regiões diferentes.',cta:'MAPEAR BRAÇO'},
  {id:'lick',tag:'VOCABULÁRIO',title:'Lick Lab',desc:'Imite, entenda e modifique frases bonitas.',cta:'APRENDER LICKS'},
  {id:'stretch',tag:'STRETCH',title:'Advanced Stretch',desc:'Material acima do seu nível: erre, ajuste e internalize.',cta:'TENTAR ALGO DIFÍCIL'},
  {id:'free',tag:'JAM',title:'Free Jam',desc:'Toque livremente e deixe o app observar seus hábitos.',cta:'IMPROVISAR'}
];
const LICKS=[
  {name:'Resolve to root',notes:[64,67,69,67,64,57],durs:[1,0.5,1,0.5,0.5,1.5]},
  {name:'Minor pentatonic answer',notes:[57,60,62,64,62,60,57],durs:[0.5,0.5,1,0.5,0.5,0.5,1.5]},
  {name:'Gilmour-like climb',notes:[64,67,69,72,69,67,64],durs:[1,0.5,0.5,1,1,0.5,1.5]},
  {name:'Chord-tone landing',notes:[60,64,67,64,60,57],durs:[0.5,0.5,1,0.5,0.5,2]},
  {name:'Stretch chromatic color',notes:[60,62,63,64,67,64,57],durs:[0.5,0.5,0.5,1,0.5,0.5,2]},
  {name:'Wide interval phrase',notes:[57,64,60,67,64,60,57],durs:[1,0.5,0.5,1,0.5,0.5,2]}
];

const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const pc=m=>((m%12)+12)%12;
const noteName=m=>NOTES[pc(m)]+(Math.floor(m/12)-1);
const pct=n=>Math.round(clamp(n,0,1)*100);
const fmtTime=s=>`${String(Math.floor(s/60)).padStart(2,'0')}:${String(Math.floor(s%60)).padStart(2,'0')}`;
const distPc=(a,b)=>Math.min((a-b+12)%12,(b-a+12)%12);
const now=()=>performance.now()/1000;

let state=loadState();
let audio={ctx:null,an:null,gain:null,stream:null,buf:null,running:false,lastMidi:null,lastNoteAt:0,lastDetectedAt:0,noiseFloor:0.0008};
let backing={master:null,timer:null,startAt:0,on:true,currentChord:0,bpm:80};
let session=null;
let toastTimer=null;

function loadState(){
  const saved=JSON.parse(localStorage.getItem('fretmentor_state_v2')||'null');
  return saved||{skills:{...DEFAULT_SKILLS},history:[],settings:{gain:4,gate:0.0012,backing:0.22,fog:70},tested:false};
}
function saveState(){localStorage.setItem('fretmentor_state_v2',JSON.stringify(state));}

function renderExercises(){
  $('#exerciseGrid').innerHTML=EXERCISES.map(e=>`<div class="card exercise" data-ex="${e.id}"><span class="tag">${e.tag}</span><h3>${e.title}</h3><p>${e.desc}</p><b>${e.cta} →</b></div>`).join('');
  $$('.exercise').forEach(el=>el.onclick=()=>startExercise(el.dataset.ex));
}
function renderSkills(target='#skills'){
  const rows=Object.entries(state.skills).map(([k,v])=>`<div class="skillrow"><span>${k}</span><div class="skillbar"><i style="width:${Math.round(v)}%"></i></div><strong>${Math.round(v)}</strong></div>`).join('');
  $(target).innerHTML=rows;
}
function renderHistory(){
  const h=state.history.slice(-8).reverse();
  $('#historyList').innerHTML=h.length?h.map(x=>`<div class="history-item"><div><b>${x.label}</b><span>${new Date(x.date).toLocaleString('pt-BR')}</span></div><div style="text-align:right"><b>${Math.round(x.score)}%</b><span>${x.notes} notas · ${x.phrases} frases</span></div></div>`).join(''):`<div class="card"><p>Nenhuma sessão registrada ainda.</p></div>`;
}
function renderCoach(){
  const entries=Object.entries(state.skills).sort((a,b)=>a[1]-b[1]);
  if(!state.history.length){$('#coachTitle').textContent='Ainda sem dados suficientes';$('#coachText').textContent='Faça o teste inicial ou uma sessão para o app começar a detectar seus gargalos.';return;}
  const [weak,weak2]=entries;
  const best=entries[entries.length-1];
  $('#coachTitle').textContent=`Prioridade: ${weak[0]}`;
  $('#coachText').textContent=`Seu ponto mais forte hoje é ${best[0]} (${Math.round(best[1])}). Os gargalos são ${weak[0]} (${Math.round(weak[1])}) e ${weak2[0]} (${Math.round(weak2[1])}). O Guided Jam vai aumentar automaticamente o peso desses dois treinos.`;
}
function showScreen(id){$$('.screen').forEach(x=>x.classList.toggle('active',x.id===id));$$('.nav button').forEach(x=>x.classList.toggle('active',x.dataset.screen===id));if(id==='progressScreen'){renderSkills('#progressSkills');renderHistory();}}
$$('.nav button').forEach(b=>b.onclick=()=>showScreen(b.dataset.screen));

function renderSettings(){
  $('#gainSlider').value=state.settings.gain;$('#gateSlider').value=state.settings.gate;$('#backingSlider').value=state.settings.backing;$('#fogSlider').value=state.settings.fog;
  $('#gainSlider').oninput=e=>{state.settings.gain=+e.target.value;if(audio.gain)audio.gain.gain.value=state.settings.gain;saveState();};
  $('#gateSlider').oninput=e=>{state.settings.gate=+e.target.value;saveState();};
  $('#backingSlider').oninput=e=>{state.settings.backing=+e.target.value;if(backing.master)backing.master.gain.value=state.settings.backing;saveState();};
  $('#fogSlider').oninput=e=>{state.settings.fog=+e.target.value;saveState();renderFretboard(audio.lastMidi?pc(audio.lastMidi):null);};
  $('#resetData').onclick=()=>openModal(`<h2>Zerar progresso?</h2><p>Isso apaga scores e histórico deste aparelho.</p><div class="actions"><button class="btn alt" id="cancelReset">Cancelar</button><button class="btn warn" id="confirmReset">Zerar</button></div>`,()=>{$('#cancelReset').onclick=closeModal;$('#confirmReset').onclick=()=>{localStorage.removeItem('fretmentor_state_v2');state=loadState();renderAll();closeModal();toast('Progresso zerado','good');};});
}
function renderAll(){renderExercises();renderSkills();renderSkills('#progressSkills');renderHistory();renderCoach();renderSettings();renderFretboard(null);}
async function ensureMic(){
  if(audio.running)return true;
  try{
    if(!navigator.mediaDevices?.getUserMedia)throw new Error('Microfone indisponível neste navegador');
    audio.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:true,channelCount:1}});
    audio.ctx=new (window.AudioContext||window.webkitAudioContext)();await audio.ctx.resume();
    const src=audio.ctx.createMediaStreamSource(audio.stream);audio.gain=audio.ctx.createGain();audio.gain.gain.value=state.settings.gain;audio.an=audio.ctx.createAnalyser();audio.an.fftSize=2048;src.connect(audio.gain);audio.gain.connect(audio.an);audio.buf=new Float32Array(audio.an.fftSize);audio.running=true;$('#micBtn').textContent='MIC ON';$('#micBtn').classList.add('on');detectLoop();return true;
  }catch(e){toast('Não consegui abrir o microfone','bad');openModal(`<h2>Microfone bloqueado</h2><p>${String(e.message||e)}</p><button class="btn" id="closeErr">OK</button>`,()=>$('#closeErr').onclick=closeModal);return false;}
}
$('#micBtn').onclick=ensureMic;

function detectPitch(b,sr){
  const n=b.length;let mean=0;for(let i=0;i<n;i++)mean+=b[i];mean/=n;let rms=0;for(let i=0;i<n;i++){const v=b[i]-mean;rms+=v*v;}rms=Math.sqrt(rms/n);
  audio.noiseFloor=audio.noiseFloor*0.995+Math.min(rms,audio.noiseFloor*2.2)*0.005;
  const gate=Math.max(state.settings.gate,audio.noiseFloor*1.55);if(rms<gate)return null;
  const minLag=Math.max(2,Math.floor(sr/1400)),maxLag=Math.min(n-4,Math.ceil(sr/75));let bestLag=-1,bestCorr=0;
  for(let lag=minLag;lag<=maxLag;lag++){let sum=0,e1=0,e2=0,stop=n-lag;for(let i=0;i<stop;i++){const a=b[i]-mean,d=b[i+lag]-mean;sum+=a*d;e1+=a*a;e2+=d*d;}const corr=sum/Math.sqrt((e1*e2)||1);if(corr>bestCorr){bestCorr=corr;bestLag=lag;}}
  if(bestLag<0||bestCorr<0.56)return null;
  const corrAt=lag=>{if(lag<minLag||lag>maxLag)return 0;let sum=0,e1=0,e2=0,stop=n-lag;for(let i=0;i<stop;i++){const a=b[i]-mean,d=b[i+lag]-mean;sum+=a*d;e1+=a*a;e2+=d*d;}return sum/Math.sqrt((e1*e2)||1);};
  const y1=corrAt(bestLag-1),y2=bestCorr,y3=corrAt(bestLag+1),den=(y1-2*y2+y3),shift=Math.abs(den)>1e-6?0.5*(y1-y3)/den:0,lag=bestLag+clamp(shift,-1,1),f=sr/lag;if(f<75||f>1400)return null;return{f,rms,confidence:bestCorr};
}
function cents(f,m){const e=440*Math.pow(2,(m-69)/12);return Math.round(1200*Math.log2(f/e));}
function detectLoop(){
  if(!audio.running)return;audio.an.getFloatTimeDomainData(audio.buf);const r=detectPitch(audio.buf,audio.ctx.sampleRate);const t=now();
  if(r){const midi=Math.round(69+12*Math.log2(r.f/440));audio.lastDetectedAt=t;if(midi!==audio.lastMidi||t-audio.lastNoteAt>.055){audio.lastMidi=midi;audio.lastNoteAt=t;onNote(midi,r,t);}$('#levelBar').style.width=Math.min(100,r.rms*900)+'%';}
  else if(session&&t-audio.lastDetectedAt>.55) onSilence(t);
  requestAnimationFrame(detectLoop);
}
function onNote(midi,r,t){
  $('#noteOrb').textContent=noteName(midi);$('#noteName').textContent=`${r.f.toFixed(1)} Hz`;$('#noteDetail').textContent=`${cents(r.f,midi)>=0?'+':''}${cents(r.f,midi)} cents · ${degreeLabel(pc(midi),currentChord())}`;renderFretboard(pc(midi));
  if(!session)return;
  const p=pc(midi),prev=session.events[session.events.length-1];if(prev&&prev.midi===midi&&t-prev.t<.09)return;
  // During slides we keep pitch movement but avoid counting every intermediate semitone as a new intentional note if too fast.
  const legatoTransient=prev&&t-prev.t<.065&&Math.abs(midi-prev.midi)<=2;
  const ev={t,midi,pc:p,rms:r.rms,chord:backing.currentChord,phase:session.phaseIndex,legatoTransient};session.events.push(ev);session.lastSound=t;if(!legatoTransient)session.noteCount++;
  if(session.lastPhraseEnd && t-session.lastPhraseEnd>0.6){session.phraseStarts.push(session.events.length-1);session.lastPhraseEnd=null;}
  evaluateEvent(ev);updateLiveStats();
}
function onSilence(t){if(!session)return;if(session.events.length&&!session.lastPhraseEnd&&t-session.lastSound>.6){session.lastPhraseEnd=t;session.phraseCount++;session.phraseEnds.push(session.events.length-1);evaluatePhraseEnd();updateLiveStats();}}
function degreeLabel(notePc,chord){const rel=(notePc-chord.root+12)%12;const labels={0:'1',1:'♭2',2:'2',3:'♭3',4:'3',5:'4',6:'♭5',7:'5',8:'♭6',9:'6',10:'♭7',11:'7'};return `${labels[rel]} de ${chord.name}${chord.tones.includes(notePc)?' · chord tone':''}`;}
function currentChord(){return CHORDS[backing.currentChord]||CHORDS[0];}
function renderFretboard(activePc=null){
  const allowed=session?.rule?.allowed||[];const target=session?currentTargetPc():null;const fog=state.settings.fog;let h='<div class="fb">';
  for(let s=TUNING.length-1;s>=0;s--){h+='<div class="string">';for(let f=0;f<=12;f++){const p=pc(TUNING[s]+f),showName=fog>=80||(fog>=50&&CHORDS.some(c=>c.root===p))||(fog>=20&&allowed.includes(p));let cls='f';if(p===activePc)cls+=' on';else if(target===p)cls+=' target';else if(allowed.includes(p))cls+=' allowed';h+=`<div class="${cls}"><span>${showName?NOTES[p]:''}</span></div>`;}h+='</div>';}
  h+='</div><div class="labels">'+Array.from({length:13},(_,i)=>`<span>${i}</span>`).join('')+'</div>';$('#fretboard').innerHTML=h;
}

function initBacking(){
  if(!audio.ctx)return;stopBacking();const ctx=audio.ctx;backing.master=ctx.createGain();backing.master.gain.value=backing.on?state.settings.backing:0;backing.master.connect(ctx.destination);backing.startAt=ctx.currentTime+.08;backing.currentChord=0;let beat=0;
  const tick=()=>{if(!session||!audio.ctx)return;const t=audio.ctx.currentTime+.04;const beatDur=60/backing.bpm;const bar=Math.floor(beat/4),chordIndex=bar%4;backing.currentChord=chordIndex;renderChordLine();if(backing.on){playClick(t,beat%4===0?880:520,beat%4===0?.035:.022);if(beat%4===0)playChordPad(CHORDS[chordIndex],t,beatDur*3.85);}beat++;backing.timer=setTimeout(tick,beatDur*1000);};tick();
}
function playClick(t,freq,g){const o=audio.ctx.createOscillator(),gain=audio.ctx.createGain();o.type='triangle';o.frequency.value=freq;gain.gain.setValueAtTime(g,t);gain.gain.exponentialRampToValueAtTime(.0001,t+.06);o.connect(gain);gain.connect(backing.master);o.start(t);o.stop(t+.07);}
function playChordPad(ch,t,d){const roots={Am:45,F:41,C:48,G:43},base=roots[ch.name];ch.tones.forEach((tone)=>{let midi=base+((tone-ch.root+12)%12);while(midi>60)midi-=12;const f=440*Math.pow(2,(midi-69)/12),o=audio.ctx.createOscillator(),g=audio.ctx.createGain();o.type='sine';o.frequency.value=f;g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.025,t+.05);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(backing.master);o.start(t);o.stop(t+d+.05);});}
function stopBacking(){if(backing.timer)clearTimeout(backing.timer);backing.timer=null;if(backing.master){try{backing.master.disconnect();}catch{}backing.master=null;}}
function renderChordLine(){$('#chordLine').innerHTML=CHORDS.map((c,i)=>`<div class="chord ${i===backing.currentChord?'on':''}">${c.name}</div>`).join('');}
$('#toggleBacking').onclick=()=>{backing.on=!backing.on;if(backing.master)backing.master.gain.value=backing.on?state.settings.backing:0;$('#toggleBacking').textContent=`BACKING: ${backing.on?'ON':'OFF'}`;};

function newSession(label,type,duration,phases){return{label,type,duration,phases,start:now(),phaseIndex:0,events:[],noteCount:0,phraseCount:0,phraseStarts:[],phraseEnds:[],lastSound:0,lastPhraseEnd:null,hits:0,attempts:0,rule:{},metrics:{},phaseData:{},ticker:null};}
async function beginSession(label,type,duration,phases){if(!(await ensureMic()))return;stopSession(false);session=newSession(label,type,duration,phases);showScreen('liveScreen');$('#modeTitle').textContent=label;$('#modeSub').textContent=type==='diagnostic'?'avaliação inicial':type==='guided'?'sessão adaptativa':'treino focado';$('#feedback').textContent='Comece quando quiser.';renderChordLine();applyPhase(0);initBacking();session.ticker=setInterval(updateSessionClock,100);}
function updateSessionClock(){if(!session)return;const elapsed=now()-session.start,remaining=Math.max(0,session.duration-elapsed);$('#timer').textContent=fmtTime(remaining);$('#phaseProgress').style.width=(elapsed/session.duration*100)+'%';let acc=0,next=session.phaseIndex;for(let i=0;i<session.phases.length;i++){acc+=session.phases[i].duration;if(elapsed<acc){next=i;break;}}if(next!==session.phaseIndex)applyPhase(next);if(elapsed>=session.duration)finishSession();}
function applyPhase(i){if(!session)return;session.phaseIndex=i;const ph=session.phases[i];session.rule=typeof ph.rule==='function'?ph.rule():structuredClone(ph.rule||{});$('#promptLabel').textContent=ph.label||'INSTRUÇÃO';$('#promptMain').textContent=typeof ph.main==='function'?ph.main():ph.main;$('#promptSub').textContent=ph.sub||'';if(ph.onStart)ph.onStart();renderFretboard(audio.lastMidi?pc(audio.lastMidi):null);}
function stopSession(record=true){if(!session)return;if(session.ticker)clearInterval(session.ticker);if(record)finishSession();else{stopBacking();session=null;}}
$('#stopSession').onclick=()=>{if(session)finishSession(true);else showScreen('homeScreen');};

function evaluateEvent(ev){const r=session.rule||{},p=ev.pc;if(ev.legatoTransient)return;
  if(r.allowed?.length){session.attempts++;if(r.allowed.includes(p)){session.hits++;flashFeedback('Dentro da restrição','good');}else flashFeedback(`${NOTES[p]} está fora das notas permitidas`,'bad');}
  if(r.findPc!=null){session.attempts++;if(p===r.findPc){session.hits++;flashFeedback(`Perfeito — ${NOTES[p]} encontrado`,'good');advanceMicroChallenge();}else flashFeedback(`Você tocou ${NOTES[p]}. Procure ${NOTES[r.findPc]}.`,'warn');}
  if(r.imitate){const idx=r.imitateIndex||0;if(idx<r.imitate.length){session.attempts++;if(p===pc(r.imitate[idx])){session.hits++;r.imitateIndex=idx+1;flashFeedback(`${idx+1}/${r.imitate.length} ✓`,'good');if(r.imitateIndex>=r.imitate.length){flashFeedback('Frase reproduzida','good');setTimeout(()=>loadNextLick(),700);}}else flashFeedback(`Esperado: ${NOTES[pc(r.imitate[idx])]}`,'warn');}}
  if(r.rhythm){const beatDur=60/backing.bpm,barPos=((audio.ctx.currentTime-backing.startAt)% (beatDur*4)+beatDur*4)%(beatDur*4),targetBeats=r.rhythm;const nearest=Math.min(...targetBeats.map(b=>Math.abs(barPos-b*beatDur)));session.attempts++;if(nearest<.14){session.hits++;flashFeedback('Ataque no tempo ✓','good');}else flashFeedback('Ataque fora do padrão','warn');}
  if(session.type==='free'||session.type==='guided'||session.type==='diagnostic')evaluateHarmony(ev);
}
function evaluateHarmony(ev){const ch=CHORDS[ev.chord];if(ch.tones.includes(ev.pc)){session.metrics.chordToneHits=(session.metrics.chordToneHits||0)+1;}session.metrics.harmonicAttempts=(session.metrics.harmonicAttempts||0)+1;if(AM_PENTA.includes(ev.pc))session.metrics.pentaHits=(session.metrics.pentaHits||0)+1;session.metrics.pentaAttempts=(session.metrics.pentaAttempts||0)+1;}
function evaluatePhraseEnd(){if(!session)return;const r=session.rule||{},idx=session.phraseEnds.at(-1),ev=session.events[idx];if(!ev)return;if(r.targetPc!=null){session.attempts++;if(ev.pc===r.targetPc){session.hits++;flashFeedback(`Resolvido em ${NOTES[ev.pc]} ✓`,'good');}else flashFeedback(`Terminou em ${NOTES[ev.pc]}. Alvo: ${NOTES[r.targetPc]}`,'warn');}if(r.startPc!=null){const startIdx=session.phraseStarts.at(-1)??0,st=session.events[startIdx];session.attempts++;if(st&&st.pc===r.startPc)session.hits++;}}
function updateLiveStats(){$('#statNotes').textContent=session?.noteCount||0;$('#statHits').textContent=session?.attempts?pct(session.hits/session.attempts)+'%':'—';$('#statPhrases').textContent=session?.phraseCount||0;}
function flashFeedback(msg,kind=''){$('#feedback').className='feedback '+kind;$('#feedback').textContent=msg;}

let microIndex=0;
function advanceMicroChallenge(){if(!session)return;microIndex++;const r=session.rule;if(r.sequence?.length){const next=r.sequence[microIndex%r.sequence.length];r.findPc=next;$('#promptMain').textContent=`Encontre ${NOTES[next]}`;renderFretboard(audio.lastMidi?pc(audio.lastMidi):null);}}
function randomFrom(arr){return arr[Math.floor(Math.random()*arr.length)];}
function makeExercisePhases(id){
  if(id==='phrase')return[{duration:180,label:'PHRASE BUILDER',main:'Comece em E e termine em A',sub:'Use somente A, C e E. Frases curtas, com pausas.',rule:{allowed:[9,0,4],startPc:4,targetPc:9}}];
  if(id==='target')return[{duration:180,label:'TARGET NOTES',main:'Termine cada frase na terça do acorde atual',sub:'Am→C · F→A · C→E · G→B',rule:{targetMode:'third'}}];
  if(id==='rhythm')return[{duration:160,label:'RHYTHM LAB',main:'Ataques em 1, “&” de 2 e 4',sub:'Qualquer nota. Copie apenas o ritmo.',rule:{rhythm:[0,1.5,3]}}];
  if(id==='fretboard')return[{duration:180,label:'FRETBOARD RADAR',main:'Encontre A',sub:'O alvo muda quando você acerta.',rule:()=>{microIndex=0;return{findPc:9,sequence:[9,0,4,7,2,5,11,9]};}}];
  if(id==='lick')return[{duration:210,label:'LICK LAB',main:'Ouça e reproduza a frase',sub:'Primeiro copie. Depois altere o final.',rule:{},onStart:()=>setTimeout(()=>loadNextLick(false),600)}];
  if(id==='stretch')return[{duration:240,label:'ADVANCED STRETCH',main:'Frase acima do seu nível',sub:'Não busque perfeição imediata. Tente, erre, ajuste.',rule:{},onStart:()=>setTimeout(()=>loadNextLick(true),600)}];
  return[{duration:300,label:'FREE JAM',main:'Improvisação livre',sub:'O app vai observar ritmo, finais, notas e harmonia.',rule:{}}];
}
async function startExercise(id){const phases=makeExercisePhases(id),d=phases.reduce((a,p)=>a+p.duration,0);await beginSession(EXERCISES.find(e=>e.id===id)?.title||'Treino',id,d,phases);}

function diagnosticPhases(){return[
  {duration:60,label:'1/7 · FREE',main:'Toque livremente',sub:'Não tente impressionar o app. Toque como você normalmente improvisa.',rule:{}},
  {duration:40,label:'2/7 · RESTRIÇÃO',main:'Use somente A, C e E',sub:'Faça frases musicais com apenas três notas.',rule:{allowed:[9,0,4]}},
  {duration:40,label:'3/7 · TARGET',main:'Termine cada frase em A',sub:'O caminho é livre. Só o destino importa.',rule:{targetPc:9}},
  {duration:40,label:'4/7 · RITMO',main:'Ataques em 1, “&” de 2 e 4',sub:'Qualquer nota. Foque no ritmo.',rule:{rhythm:[0,1.5,3]}},
  {duration:40,label:'5/7 · FRETBOARD',main:'Encontre as notas pedidas',sub:'O alvo muda quando você acerta.',rule:()=>{microIndex=0;return{findPc:9,sequence:[9,0,4,7,2,5,11,9]};}},
  {duration:40,label:'6/7 · IMITAÇÃO',main:'Ouça e reproduza',sub:'A frase será curta. Não precisa cantar.',rule:{},onStart:()=>setTimeout(()=>loadNextLick(false,true),500)},
  {duration:40,label:'7/7 · FREE AGAIN',main:'Agora improvise livremente',sub:'Use o que acabou de perceber sem pensar demais.',rule:{}}
];}
$('#startDiagnostic').onclick=()=>beginSession('Teste inicial · 5 min','diagnostic',300,diagnosticPhases());

function guidedPhases(){
  const weak=Object.entries(state.skills).sort((a,b)=>a[1]-b[1]).slice(0,2).map(x=>x[0]);const p=[];
  if(weak.includes('Fretboard')||weak.includes('Ouvido → braço'))p.push({duration:90,label:'FRETBOARD',main:'Encontre A',sub:'O alvo muda a cada acerto.',rule:()=>{microIndex=0;return{findPc:9,sequence:[9,0,4,7,2,5,11]};}});
  p.push({duration:120,label:'PHRASE BUILDER',main:'Use A, C e E. Termine em A.',sub:'Poucas notas; intenção alta.',rule:{allowed:[9,0,4],targetPc:9}});
  if(weak.includes('Ritmo')||weak.includes('Construção de frase'))p.push({duration:90,label:'RHYTHM',main:'Ataques em 1, “&” de 2 e 4',sub:'Qualquer nota.',rule:{rhythm:[0,1.5,3]}});
  p.push({duration:120,label:'TARGETING',main:'Termine na terça do acorde atual',sub:'Am:C · F:A · C:E · G:B',rule:{targetMode:'third'}});
  p.push({duration:120,label:'STRETCH',main:'Imite uma frase acima do seu nível',sub:'Erros são esperados; corrija por repetição.',rule:{},onStart:()=>setTimeout(()=>loadNextLick(true),500)});
  p.push({duration:120,label:'FREE JAM',main:'Agora solte tudo',sub:'Tente usar conscientemente uma ideia do treino.',rule:{}});return p;
}
$('#startGuided').onclick=()=>{const p=guidedPhases(),d=p.reduce((a,x)=>a+x.duration,0);beginSession('Guided Jam','guided',d,p);};

function currentTargetPc(){if(!session)return null;const r=session.rule;if(r.targetMode==='third'){const ch=currentChord();return pc(ch.root+(ch.name==='Am'?3:4));}return r.targetPc;}
const oldEvalPhraseEnd=evaluatePhraseEnd;
evaluatePhraseEnd=function(){if(!session)return;const r=session.rule||{},idx=session.phraseEnds.at(-1),ev=session.events[idx];if(!ev)return;let target=null;if(r.targetMode==='third'){const ch=CHORDS[ev.chord];target=pc(ch.root+(ch.name==='Am'?3:4));}else target=r.targetPc;if(target!=null){session.attempts++;if(ev.pc===target){session.hits++;flashFeedback(`Resolvido em ${NOTES[ev.pc]} ✓`,'good');}else flashFeedback(`Terminou em ${NOTES[ev.pc]}. Alvo: ${NOTES[target]}`,'warn');}if(r.startPc!=null){const startIdx=session.phraseStarts.at(-1)??0,st=session.events[startIdx];session.attempts++;if(st&&st.pc===r.startPc)session.hits++;}};

let lickCursor=0;
function loadNextLick(stretch=false,diagnostic=false){if(!session)return;const lick=stretch?LICKS[4+(lickCursor%2)]:LICKS[lickCursor%4];lickCursor++;session.rule.imitate=lick.notes.slice();session.rule.imitateIndex=0;$('#promptMain').textContent=lick.name;$('#promptSub').textContent='Ouça. Depois reproduza exatamente as alturas.';playLick(session.rule.imitate,lick.durs).then(()=>{$('#feedback').textContent='Sua vez.';});if(diagnostic)session.rule.diagnosticLick=true;}
async function playLick(midis,durs){if(!audio.ctx)return;let t=audio.ctx.currentTime+.15;const beat=60/backing.bpm;midis.forEach((m,i)=>{const f=440*Math.pow(2,(m-69)/12),o=audio.ctx.createOscillator(),g=audio.ctx.createGain();o.type='triangle';o.frequency.value=f;g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.12,t+.015);g.gain.exponentialRampToValueAtTime(.0001,t+beat*durs[i]*.8);o.connect(g);g.connect(audio.ctx.destination);o.start(t);o.stop(t+beat*durs[i]);t+=beat*durs[i];});return new Promise(res=>setTimeout(res,(t-audio.ctx.currentTime)*1000+100));}

function analyzeSession(s){
  const nonTransient=s.events.filter(e=>!e.legatoTransient),notes=nonTransient.map(e=>e.pc),unique=new Set(notes).size,phrases=Math.max(1,s.phraseCount),hitRate=s.attempts?s.hits/s.attempts:null;
  const chordRate=s.metrics.harmonicAttempts?(s.metrics.chordToneHits||0)/s.metrics.harmonicAttempts:.45,pentaRate=s.metrics.pentaAttempts?(s.metrics.pentaHits||0)/s.metrics.pentaAttempts:.6;
  const amps=nonTransient.map(e=>e.rms),mean=amps.reduce((a,b)=>a+b,0)/(amps.length||1),variance=amps.reduce((a,b)=>a+(b-mean)**2,0)/(amps.length||1),dyn=clamp(Math.sqrt(variance)/(mean||.01)*90,20,95);
  const phraseLens=[];let last=0;for(const end of s.phraseEnds){phraseLens.push(Math.max(1,end-last+1));last=end+1;}const avgLen=phraseLens.length?phraseLens.reduce((a,b)=>a+b,0)/phraseLens.length:nonTransient.length;
  let motif=35;if(s.phraseEnds.length>=2){const seqs=[];let st=0;for(const e of s.phraseEnds){seqs.push(notes.slice(st,e+1));st=e+1;}let matches=0,total=0;for(let i=1;i<seqs.length;i++){const a=seqs[i-1].slice(0,3).join(','),b=seqs[i].slice(0,3).join(',');if(a&&b){total++;if(a===b)matches++;}}motif=clamp(30+(total?matches/total*70:0),25,95);}
  const updates={};
  updates['Pentatônica consciente']=pentaRate*100;updates['Chord targeting']=chordRate*100;updates['Dinâmica']=dyn;updates['Motivos']=motif;updates['Vocabulário']=clamp(20+unique*7,20,90);updates['Construção de frase']=clamp(85-Math.abs(avgLen-5)*8,25,90);if(hitRate!=null){updates['Finalização']=hitRate*100;updates['Fretboard']=Math.max(state.skills['Fretboard'],hitRate*100);updates['Ouvido → braço']=Math.max(state.skills['Ouvido → braço'],hitRate*92);}if(s.type==='rhythm'||s.rule?.rhythm)updates['Ritmo']=hitRate!=null?hitRate*100:state.skills['Ritmo'];
  for(const [k,v] of Object.entries(updates))state.skills[k]=clamp(state.skills[k]*.72+v*.28,5,98);
  const overall=Object.values(updates).length?Object.values(updates).reduce((a,b)=>a+b,0)/Object.values(updates).length:50;
  return{score:overall,notes:nonTransient.length,phrases:s.phraseCount,hitRate,chordRate,pentaRate,unique,avgLen,updates};
}
function finishSession(manual=false){if(!session)return;const s=session;if(s.ticker)clearInterval(s.ticker);stopBacking();const a=analyzeSession(s);state.history.push({date:new Date().toISOString(),label:s.label,score:a.score,notes:a.notes,phrases:a.phrases});state.history=state.history.slice(-30);state.tested=state.tested||s.type==='diagnostic';saveState();session=null;renderAll();const weak=Object.entries(state.skills).sort((x,y)=>x[1]-y[1]).slice(0,2);openModal(`<div class="eyebrow">ANÁLISE</div><div class="bigscore">${Math.round(a.score)}</div><h2>${manual?'Sessão encerrada':'Sessão concluída'}</h2><p>${buildSessionSummary(a,weak)}</p><div class="actions"><button class="btn alt" id="seeProgress">Ver progresso</button><button class="btn violet" id="again">Guided Jam</button></div>`,()=>{$('#seeProgress').onclick=()=>{closeModal();showScreen('progressScreen');};$('#again').onclick=()=>{closeModal();$('#startGuided').click();};});}
function buildSessionSummary(a,weak){const parts=[];if(a.hitRate!=null)parts.push(`Acerto nas tarefas objetivas: <b>${pct(a.hitRate)}%</b>.`);parts.push(`Chord tones: <b>${pct(a.chordRate)}%</b>. Pentatônica: <b>${pct(a.pentaRate)}%</b>.`);parts.push(`Gargalos atuais: <b>${weak[0][0]}</b> e <b>${weak[1][0]}</b>.`);if(a.unique<4)parts.push('Seu vocabulário de alturas ficou estreito nesta sessão; o próximo treino deve forçar mais destinos.');else if(a.avgLen>9)parts.push('Suas frases ficaram longas; mais pausas e finais claros devem melhorar a sensação de direção.');else parts.push('A duração média das frases ficou utilizável; continue priorizando começo e destino, não quantidade de notas.');return parts.join(' ');}

function openModal(html,onReady){$('#modal').innerHTML=html;$('#modalOverlay').classList.add('show');if(onReady)setTimeout(onReady,0);}function closeModal(){$('#modalOverlay').classList.remove('show');}$('#modalOverlay').onclick=e=>{if(e.target===$('#modalOverlay'))closeModal();};
function toast(msg,kind=''){const el=$('#toast');el.textContent=msg;el.className='toast show '+kind;clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.className='toast',1600);}

renderAll();renderChordLine();$('#profileHint').textContent=state.tested?'perfil calibrado':'faça o teste de 5 min';
})();
