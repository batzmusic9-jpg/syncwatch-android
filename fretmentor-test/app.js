(() => {
'use strict';

const NOTES=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
const TUNING=[40,45,50,55,59,64];
const CHORDS=[
  {name:'Am',root:9,tones:[9,0,4],bass:45,voicing:[57,60,64]},
  {name:'F',root:5,tones:[5,9,0],bass:41,voicing:[53,57,60]},
  {name:'C',root:0,tones:[0,4,7],bass:48,voicing:[55,60,64]},
  {name:'G',root:7,tones:[7,11,2],bass:43,voicing:[55,59,62]}
];
const SKILL_DEFAULTS={
  'Ouvido → braço':42,'Fretboard':36,'Ritmo':58,'Construção de frase':40,
  'Finalização':36,'Chord targeting':30,'Pentatônica consciente':44,'Vocabulário':26
};
const $=s=>document.querySelector(s), $$=s=>Array.from(document.querySelectorAll(s));
const pc=m=>((m%12)+12)%12, clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const noteName=m=>NOTES[pc(m)]+(Math.floor(m/12)-1);
const now=()=>performance.now()/1000;

let state=loadState();
let session=null,toastTimer=null;
let audio={ctx:null,stream:null,source:null,gain:null,an:null,buf:null,running:false,noiseFloor:.001,candidate:null,candidateCount:0,lastAccepted:null,lastAcceptedAt:0,lastDetectedAt:0,lastRms:0,calibrating:false};
let jam={on:true,master:null,noiseBuffer:null,timer:null,nextTime:0,origin:0,step:0,bpm:82,currentChord:0};

function loadState(){
  try{
    const s=JSON.parse(localStorage.getItem('fretmentor_tutor4')||'null');
    return s||{skills:Object.assign({},SKILL_DEFAULTS),history:[],settings:{gain:4,gate:.0015,backing:.28},tested:false};
  }catch(e){return{skills:Object.assign({},SKILL_DEFAULTS),history:[],settings:{gain:4,gate:.0015,backing:.28},tested:false};}
}
function saveState(){localStorage.setItem('fretmentor_tutor4',JSON.stringify(state));}
function toast(msg){const e=$('#toast');e.textContent=msg;e.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove('show'),1500);}
function showScreen(id){
  $$('.screen').forEach(x=>x.classList.toggle('active',x.id===id));
  $$('.bottom-nav button').forEach(x=>x.classList.toggle('active',x.dataset.screen===id));
  if(id==='progressScreen')renderProgress();
}
$$('.bottom-nav button').forEach(b=>b.onclick=()=>showScreen(b.dataset.screen));

function speak(text){
  if(!('speechSynthesis' in window))return;
  speechSynthesis.cancel();
  const u=new SpeechSynthesisUtterance(text.replace(/Am/g,'Lá menor').replace(/F/g,'Fá').replace(/C/g,'Dó').replace(/G/g,'Sol'));
  u.lang='pt-BR';u.rate=1.04;u.pitch=1;
  const voices=speechSynthesis.getVoices();
  const v=voices.find(x=>x.lang&&x.lang.toLowerCase().startsWith('pt-br'))||voices.find(x=>x.lang&&x.lang.toLowerCase().startsWith('pt'));
  if(v)u.voice=v;
  speechSynthesis.speak(u);
}
function openModal(html){$('#modal').innerHTML=html;$('#modalOverlay').classList.add('show');}
function closeModal(){$('#modalOverlay').classList.remove('show');}
$('#modalOverlay').onclick=e=>{if(e.target===$('#modalOverlay'))closeModal();};

function renderHome(){
  const entries=Object.entries(state.skills).sort((a,b)=>a[1]-b[1]);
  if(!state.history.length){
    $('#coachHeadline').textContent='Faça sua primeira sessão.';
    $('#coachBody').textContent='Depois o app decide em qual habilidade insistir.';
  }else{
    $('#coachHeadline').textContent='Próximo foco: '+entries[0][0];
    $('#coachBody').textContent='Depois disso, '+entries[1][0]+'. O Guided Jam já ajusta a aula para esses pontos.';
  }
}
function renderProgress(){
  $('#skills').innerHTML=Object.entries(state.skills).map(([k,v])=>
    '<div class="skill"><span>'+k+'</span><div class="bar"><i style="width:'+Math.round(v)+'%"></i></div><b>'+Math.round(v)+'</b></div>'
  ).join('');
  const h=state.history.slice(-10).reverse();
  $('#history').innerHTML=h.length?h.map(x=>
    '<div class="history-item"><div><b>'+x.label+'</b><span>'+new Date(x.date).toLocaleString('pt-BR')+'</span></div><b>'+Math.round(x.score)+'%</b></div>'
  ).join(''):'';
}
function renderSettings(){
  $('#gainSlider').value=state.settings.gain;$('#gateSlider').value=state.settings.gate;$('#backingSlider').value=state.settings.backing;
  $('#gainSlider').oninput=e=>{state.settings.gain=+e.target.value;if(audio.gain)audio.gain.gain.value=state.settings.gain;saveState();};
  $('#gateSlider').oninput=e=>{state.settings.gate=+e.target.value;saveState();};
  $('#backingSlider').oninput=e=>{state.settings.backing=+e.target.value;if(jam.master)jam.master.gain.value=jam.on?state.settings.backing:0;saveState();};
  $('#resetData').onclick=()=>{if(confirm('Zerar todo o progresso deste aparelho?')){localStorage.removeItem('fretmentor_tutor4');state=loadState();renderHome();renderProgress();renderSettings();toast('Progresso zerado');}};
}

async function ensureMic(){
  if(audio.running)return true;
  try{
    audio.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:true,channelCount:1}});
    audio.ctx=new (window.AudioContext||window.webkitAudioContext)();
    await audio.ctx.resume();
    audio.source=audio.ctx.createMediaStreamSource(audio.stream);
    audio.gain=audio.ctx.createGain();audio.gain.gain.value=state.settings.gain;
    audio.an=audio.ctx.createAnalyser();audio.an.fftSize=2048;
    audio.source.connect(audio.gain);audio.gain.connect(audio.an);
    audio.buf=new Float32Array(audio.an.fftSize);
    audio.running=true;audio.calibrating=true;
    $('#micBtn').textContent='CALIBRANDO';$('#micBtn').classList.add('on');
    let samples=[],start=performance.now();
    const calibrate=()=>{
      if(!audio.running)return;
      audio.an.getFloatTimeDomainData(audio.buf);
      let rms=0;for(let i=0;i<audio.buf.length;i++)rms+=audio.buf[i]*audio.buf[i];
      rms=Math.sqrt(rms/audio.buf.length);samples.push(rms);
      if(performance.now()-start<1100){requestAnimationFrame(calibrate);return;}
      samples.sort((a,b)=>a-b);
      audio.noiseFloor=samples[Math.floor(samples.length*.55)]||.001;
      audio.calibrating=false;$('#micBtn').textContent='MIC ON';
      detectLoop();
    };
    openModal('<h2>Calibrando o ambiente</h2><p>Fique em silêncio por um segundo. Isso evita que o app invente notas quando você não está tocando.</p>');
    setTimeout(closeModal,1250);calibrate();
    return true;
  }catch(e){
    openModal('<h2>Microfone bloqueado</h2><p>'+String(e.message||e)+'</p>');
    return false;
  }
}
$('#micBtn').onclick=ensureMic;

function detectPitch(buf,sr){
  const n=buf.length;let mean=0;for(let i=0;i<n;i++)mean+=buf[i];mean/=n;
  let rms=0;for(let i=0;i<n;i++){const v=buf[i]-mean;rms+=v*v;}rms=Math.sqrt(rms/n);
  const gate=Math.max(state.settings.gate,audio.noiseFloor*2.8);
  if(rms<gate)return null;
  const minLag=Math.floor(sr/1000),maxLag=Math.min(n-4,Math.ceil(sr/75));
  let bestLag=-1,bestCorr=0;
  for(let lag=minLag;lag<=maxLag;lag++){
    let sum=0,e1=0,e2=0,stop=n-lag;
    for(let i=0;i<stop;i++){const a=buf[i]-mean,b=buf[i+lag]-mean;sum+=a*b;e1+=a*a;e2+=b*b;}
    const c=sum/Math.sqrt(e1*e2||1);
    if(c>bestCorr){bestCorr=c;bestLag=lag;}
  }
  if(bestLag<0||bestCorr<.62)return null;
  const f=sr/bestLag;
  if(f<75||f>1000)return null;
  return{f,rms,confidence:bestCorr,gate};
}
function cents(f,m){const exact=440*Math.pow(2,(m-69)/12);return Math.round(1200*Math.log2(f/exact));}
function detectLoop(){
  if(!audio.running||audio.calibrating)return;
  audio.an.getFloatTimeDomainData(audio.buf);
  const r=detectPitch(audio.buf,audio.ctx.sampleRate),t=now();
  if(r){
    const midi=Math.round(69+12*Math.log2(r.f/440));
    if(audio.candidate===midi)audio.candidateCount++;else{audio.candidate=midi;audio.candidateCount=1;}
    const onset=r.rms>Math.max(r.gate*1.35,audio.lastRms*1.45);
    if(audio.candidateCount>=2){
      const changed=midi!==audio.lastAccepted;
      const repeated=onset&&t-audio.lastAcceptedAt>.11;
      if(changed||repeated||t-audio.lastAcceptedAt>.5){
        audio.lastAccepted=midi;audio.lastAcceptedAt=t;onAcceptedNote(midi,r,t,changed,repeated);
      }
      audio.lastDetectedAt=t;
    }
    audio.lastRms=audio.lastRms*.7+r.rms*.3;
  }else{
    audio.candidate=null;audio.candidateCount=0;audio.lastRms*=.85;
    if(t-audio.lastDetectedAt>.28){$('#noteOrb').textContent='—';$('#noteText').textContent='Aguardando você tocar';$('#noteFunction').textContent='—';}
    if(session&&t-session.lastSound>.52)endPhrase();
    if(!session&&t-audio.lastDetectedAt>1.2)audio.noiseFloor=Math.max(.0004,audio.noiseFloor*.998);
  }
  requestAnimationFrame(detectLoop);
}

function currentChord(){return CHORDS[jam.currentChord]||CHORDS[0];}
function degreeLabel(p,ch){
  const rel=(p-ch.root+12)%12;
  const labels={0:'1',1:'♭2',2:'2',3:'♭3',4:'3',5:'4',6:'♭5',7:'5',8:'♭6',9:'6',10:'♭7',11:'7'};
  return labels[rel]+' de '+ch.name+(ch.tones.includes(p)?' · chord tone':'');
}
function onAcceptedNote(midi,r,t,changed,repeated){
  $('#noteOrb').textContent=noteName(midi);
  $('#noteText').textContent=NOTES[pc(midi)]+' · '+r.f.toFixed(0)+' Hz';
  $('#noteFunction').textContent=degreeLabel(pc(midi),currentChord());
  renderFretboard(pc(midi));
  if(!session)return;
  const ev={midi,pc:pc(midi),t,chord:jam.currentChord,onset:repeated||changed};
  session.events.push(ev);session.lastSound=t;
  if(session.phraseClosed){session.phraseStartIndex=session.events.length-1;session.phraseClosed=false;}
  evaluateNote(ev);
}
function endPhrase(){
  if(!session||session.phraseClosed||!session.events.length)return;
  const last=session.events[session.events.length-1];
  session.phraseClosed=true;session.phraseEnds++;
  evaluatePhrase(last);
}

function renderFretboard(activePc=null){
  const step=session?session.steps[session.index]:null;
  const allowed=step&&step.allowed?step.allowed:[];
  let target=null;
  if(step){
    if(step.targetPc!=null)target=step.targetPc;
    if(step.type==='find'&&step.targets)target=pc(step.targets[Math.min(session.progress,step.targets.length-1)]);
    if(step.type==='targetThird'){const ch=currentChord();target=pc(ch.root+(ch.name==='Am'?3:4));}
  }
  let h='<div class="fb">';
  for(let s=0;s<TUNING.length;s++){
    h+='<div class="string">';
    for(let fret=12;fret>=0;fret--){
      const p=pc(TUNING[s]+fret);let cls='f';
      if(p===activePc)cls+=' active';else if(target===p)cls+=' target';else if(allowed.includes(p))cls+=' allowed';
      let txt='';
      if(p===activePc||p===target||allowed.includes(p))txt=NOTES[p];
      h+='<div class="'+cls+'"><span>'+txt+'</span></div>';
    }
    h+='</div>';
  }
  h+='</div><div class="labels">';
  for(let f=12;f>=0;f--)h+='<span>'+f+'</span>';
  h+='</div><div class="edge"><span>CORPO</span><span>HEADSTOCK</span></div>';
  $('#fretboard').innerHTML=h;
}
function renderChords(){
  $('#chordStrip').innerHTML=CHORDS.map((c,i)=>'<div class="chord '+(i===jam.currentChord?'on':'')+'">'+c.name+'</div>').join('');
}

function makeNoiseBuffer(){
  const b=audio.ctx.createBuffer(1,audio.ctx.sampleRate*.3,audio.ctx.sampleRate),d=b.getChannelData(0);
  for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1;
  return b;
}
function scheduleKick(t,g=.17){
  const o=audio.ctx.createOscillator(),a=audio.ctx.createGain();
  o.type='sine';o.frequency.setValueAtTime(120,t);o.frequency.exponentialRampToValueAtTime(48,t+.12);
  a.gain.setValueAtTime(g,t);a.gain.exponentialRampToValueAtTime(.0001,t+.16);
  o.connect(a);a.connect(jam.master);o.start(t);o.stop(t+.18);
}
function scheduleSnare(t,g=.09){
  const src=audio.ctx.createBufferSource(),f=audio.ctx.createBiquadFilter(),a=audio.ctx.createGain();
  src.buffer=jam.noiseBuffer;f.type='highpass';f.frequency.value=1300;a.gain.setValueAtTime(g,t);a.gain.exponentialRampToValueAtTime(.0001,t+.13);
  src.connect(f);f.connect(a);a.connect(jam.master);src.start(t);src.stop(t+.15);
}
function scheduleHat(t,g=.026){
  const src=audio.ctx.createBufferSource(),f=audio.ctx.createBiquadFilter(),a=audio.ctx.createGain();
  src.buffer=jam.noiseBuffer;f.type='highpass';f.frequency.value=6000;a.gain.setValueAtTime(g,t);a.gain.exponentialRampToValueAtTime(.0001,t+.045);
  src.connect(f);f.connect(a);a.connect(jam.master);src.start(t);src.stop(t+.05);
}
function scheduleBass(midi,t,dur=.28,g=.08){
  const o=audio.ctx.createOscillator(),f=audio.ctx.createBiquadFilter(),a=audio.ctx.createGain();
  o.type='triangle';o.frequency.value=440*Math.pow(2,(midi-69)/12);f.type='lowpass';f.frequency.value=420;
  a.gain.setValueAtTime(.0001,t);a.gain.exponentialRampToValueAtTime(g,t+.015);a.gain.exponentialRampToValueAtTime(.0001,t+dur);
  o.connect(f);f.connect(a);a.connect(jam.master);o.start(t);o.stop(t+dur+.03);
}
function scheduleRhythmChord(ch,t,g=.025){
  ch.voicing.forEach((m,i)=>{
    const o=audio.ctx.createOscillator(),f=audio.ctx.createBiquadFilter(),a=audio.ctx.createGain();
    o.type=i===0?'triangle':'sine';o.frequency.value=440*Math.pow(2,(m-69)/12);f.type='lowpass';f.frequency.value=1500;
    a.gain.setValueAtTime(.0001,t);a.gain.exponentialRampToValueAtTime(g,t+.012);a.gain.exponentialRampToValueAtTime(.0001,t+.22);
    o.connect(f);f.connect(a);a.connect(jam.master);o.start(t);o.stop(t+.24);
  });
}
function startJam(){
  if(!audio.ctx)return;
  stopJam();jam.master=audio.ctx.createGain();jam.master.gain.value=jam.on?state.settings.backing:0;jam.master.connect(audio.ctx.destination);
  jam.noiseBuffer=makeNoiseBuffer();jam.origin=audio.ctx.currentTime+.08;jam.nextTime=jam.origin;jam.step=0;
  const secondsPerHalfBeat=60/jam.bpm/2;
  const scheduler=()=>{
    if(!session||!audio.ctx)return;
    while(jam.nextTime<audio.ctx.currentTime+.12){
      const sub=jam.step%8,bar=Math.floor(jam.step/8),ci=bar%4,ch=CHORDS[ci];jam.currentChord=ci;
      scheduleHat(jam.nextTime,sub%2===0?.022:.015);
      if(sub===0||sub===4)scheduleKick(jam.nextTime,sub===0?.18:.13);
      if(sub===2||sub===6)scheduleSnare(jam.nextTime,.085);
      if(sub===0)scheduleBass(ch.bass,jam.nextTime,.34,.075);
      if(sub===4)scheduleBass(ch.bass+7,jam.nextTime,.28,.06);
      if(sub===7){const next=CHORDS[(ci+1)%4];scheduleBass(next.bass-1,jam.nextTime,.15,.04);}
      if(sub===0||sub===3||sub===6)scheduleRhythmChord(ch,jam.nextTime,sub===0?.026:.018);
      jam.step++;jam.nextTime+=secondsPerHalfBeat;
    }
    renderChords();jam.timer=setTimeout(scheduler,25);
  };
  scheduler();
}
function stopJam(){if(jam.timer)clearTimeout(jam.timer);jam.timer=null;if(jam.master){try{jam.master.disconnect();}catch(e){}jam.master=null;}}
$('#backingBtn').onclick=()=>{jam.on=!jam.on;if(jam.master)jam.master.gain.value=jam.on?state.settings.backing:0;$('#backingBtn').textContent='♫ BACKING '+(jam.on?'ON':'OFF');};

function playTone(midi,t,dur=.3,g=.13,type='triangle'){
  const o=audio.ctx.createOscillator(),a=audio.ctx.createGain(),f=audio.ctx.createBiquadFilter();
  o.type=type;o.frequency.value=440*Math.pow(2,(midi-69)/12);f.type='lowpass';f.frequency.value=2400;
  a.gain.setValueAtTime(.0001,t);a.gain.exponentialRampToValueAtTime(g,t+.015);a.gain.exponentialRampToValueAtTime(.0001,t+dur);
  o.connect(f);f.connect(a);a.connect(audio.ctx.destination);o.start(t);o.stop(t+dur+.03);
}
function playSequence(seq,durs){
  if(!audio.ctx)return;
  let t=audio.ctx.currentTime+.08,beat=60/jam.bpm;
  seq.forEach((m,i)=>{const d=beat*(durs&&durs[i]?durs[i]:.5);playTone(m,t,Math.max(.14,d*.78));t+=d;});
}
function playRhythm(pattern){
  if(!audio.ctx)return;const beat=60/jam.bpm,t0=audio.ctx.currentTime+.1;
  pattern.forEach(x=>playTone(69,t0+x*beat,.12,.14,'sine'));
}

function buildGuided(){
  return[
    {type:'listen',instruction:'Primeiro, só ouça a jam.',hint:'Não toque ainda. Sinta o pulso e a troca Am → F → C → G.',seconds:8,demo:[57,60,64,60]},
    {type:'hits',instruction:'Agora toque A quatro vezes.',hint:'Uma nota só. Entre junto com o groove.',targetPc:9,required:4,demo:[57,57,57,57]},
    {type:'phrase',instruction:'Use A e C. Termine em A.',hint:'Faça uma frase curta, pare, e deixe o final respirar.',allowed:[9,0],endPc:9,required:2,demo:[57,60,57]},
    {type:'phrase',instruction:'Agora use A, C e E.',hint:'Comece em E e termine em A.',allowed:[9,0,4],startPc:4,endPc:9,required:2,demo:[64,60,57]},
    {type:'targetThird',instruction:'Siga os acordes.',hint:'Termine sua frase na terça do acorde que estiver soando.',required:3,demo:[60,57,64,59]},
    {type:'rhythm',instruction:'Copie este ritmo.',hint:'As notas podem ser quaisquer. Acerte os ataques.',pattern:[0,1.5,3],required:5,demoRhythm:true},
    {type:'find',instruction:'Encontre as notas sem procurar ao acaso.',hint:'O tutor muda o alvo depois de cada acerto.',targets:[60,64,69,72],required:4},
    {type:'imitate',instruction:'Copie esta frase bonita.',hint:'Ouça quantas vezes precisar. Depois toque a mesma sequência.',sequence:[64,67,69,72,69,67,64,60,57],required:1},
    {type:'freeGuided',instruction:'Agora misture tudo.',hint:'O tutor ainda vai te dar pequenas missões.',seconds:45},
    {type:'free',instruction:'Agora é com você.',hint:'Improvisa por um minuto usando o que acabou de praticar.',seconds:60}
  ];
}
function buildFocused(mode){
  if(mode==='phrase')return[
    {type:'phrase',instruction:'Use A e C. Termine em A.',hint:'Duas notas já bastam para construir uma frase.',allowed:[9,0],endPc:9,required:2,demo:[57,60,57]},
    {type:'phrase',instruction:'Adicione E.',hint:'Comece em E, passe por C e resolva em A.',allowed:[9,0,4],startPc:4,endPc:9,required:3,demo:[64,60,57]},
    {type:'targetThird',instruction:'Agora faça o acorde aparecer na frase.',hint:'Termine na terça do acorde atual.',required:3,demo:[60,57,64,59]},
    {type:'free',instruction:'Agora improvise livremente.',hint:'Não abandone a ideia de destino.',seconds:45}
  ];
  if(mode==='fretboard')return[
    {type:'find',instruction:'Encontre C4.',hint:'Sem shape. Procure a nota.',targets:[60],required:1},
    {type:'find',instruction:'Agora encontre E4, A4 e C5.',hint:'O braço precisa virar mapa, não desenho.',targets:[64,69,72],required:3},
    {type:'findPc',instruction:'Encontre A em três oitavas.',hint:'Mude de região do braço.',targetPc:9,required:3},
    {type:'free',instruction:'Improvisa mudando de região.',hint:'Evite ficar preso na mesma caixa.',seconds:45}
  ];
  if(mode==='rhythm')return[
    {type:'rhythm',instruction:'Copie o primeiro groove.',hint:'Qualquer nota. Só o ritmo importa.',pattern:[0,1.5,3],required:5,demoRhythm:true},
    {type:'rhythm',instruction:'Agora copie este segundo groove.',hint:'Mais espaço, menos notas.',pattern:[0,.5,2.5],required:5,demoRhythm:true},
    {type:'freeGuided',instruction:'Use ritmo para construir frases.',hint:'O tutor vai pedir variações.',seconds:40}
  ];
  return[
    {type:'imitate',instruction:'Copie esta frase.',hint:'Não memorize só os dedos. Ouça o contorno.',sequence:[57,60,62,64,62,60,57],required:1},
    {type:'imitate',instruction:'Agora uma frase mais aberta.',hint:'Preste atenção no salto e no final.',sequence:[64,67,69,72,69,67,64,60,57],required:1},
    {type:'variation',instruction:'Use o começo do lick e mude o final.',hint:'O objetivo é transformar vocabulário em linguagem.',seed:[64,67,69],endPc:9,required:2,demo:[64,67,69,67,64,57]},
    {type:'free',instruction:'Coloque fragmentos dos licks na jam.',hint:'Não force o lick inteiro. Use pedaços.',seconds:50}
  ];
}
function buildDiagnostic(){
  return[
    {type:'free',instruction:'Toque livremente.',hint:'Um minuto. Não tente impressionar o app.',seconds:55},
    {type:'phrase',instruction:'Use só A, C e E.',hint:'Frases curtas.',allowed:[9,0,4],required:2,maxSeconds:35,demo:[57,60,64,57]},
    {type:'targetThird',instruction:'Termine na terça do acorde.',hint:'O tutor mede se você acompanha a harmonia.',required:3,maxSeconds:35,demo:[60,57,64,59]},
    {type:'rhythm',instruction:'Copie este ritmo.',hint:'Qualquer nota.',pattern:[0,1.5,3],required:5,maxSeconds:35,demoRhythm:true},
    {type:'find',instruction:'Encontre as notas pedidas.',hint:'Sem olhar shape.',targets:[60,64,69,72],required:4,maxSeconds:35},
    {type:'imitate',instruction:'Copie esta frase.',hint:'Ouça e reproduza.',sequence:[57,60,62,64,62,60,57],required:1,maxSeconds:35},
    {type:'free',instruction:'Toque livremente outra vez.',hint:'Use o que acabou de perceber.',seconds:70}
  ];
}

async function startLesson(label,steps,type){
  if(!(await ensureMic()))return;
  if(session)finishLesson(true);
  session={label,steps,type,index:0,progress:0,events:[],lastSound:0,phraseClosed:true,phraseStartIndex:0,phraseEnds:0,stepStarted:now(),hintAt:0,rhythmHits:0,seqIndex:0,freeCue:0,ticker:null,metrics:{hits:0,attempts:0,chord:0,chordN:0}};
  $('#lessonTitle').textContent=label;$('#lessonLabel').textContent=type==='diagnostic'?'DIAGNÓSTICO':'AULA GUIADA';
  showScreen('lessonScreen');startJam();applyStep(0);
  session.ticker=setInterval(stepClock,150);
}
function applyStep(i){
  if(!session)return;
  if(i>=session.steps.length){finishLesson(false);return;}
  session.index=i;session.progress=0;session.seqIndex=0;session.rhythmHits=0;session.stepStarted=now();session.hintAt=0;session.phraseClosed=true;
  const st=session.steps[i];
  $('#stepCount').textContent='PASSO '+(i+1)+' DE '+session.steps.length;
  $('#instruction').textContent=st.instruction;$('#hint').textContent=st.hint||'';
  $('#feedback').className='feedback';$('#feedback').textContent='Faça quando estiver pronto.';
  $('#demoBtn').textContent='▶ OUVIR EXEMPLO';
  speak(st.instruction);
  renderFretboard(audio.lastAccepted==null?null:pc(audio.lastAccepted));
  if(st.type==='listen')setTimeout(()=>{if(session&&session.index===i)advanceStep('Agora sim.');},st.seconds*1000);
}
function advanceStep(msg){
  if(!session)return;
  $('#feedback').className='feedback good';$('#feedback').textContent='✓ '+msg;
  const next=session.index+1;
  setTimeout(()=>{if(session)applyStep(next);},700);
}
function fail(msg){$('#feedback').className='feedback warn';$('#feedback').textContent=msg;}
function success(msg){$('#feedback').className='feedback good';$('#feedback').textContent='✓ '+msg;}

function stepClock(){
  if(!session)return;
  const st=session.steps[session.index],elapsed=now()-session.stepStarted;
  if((st.type==='free'||st.type==='freeGuided')&&elapsed>=st.seconds){advanceStep('Boa. Vamos seguir.');return;}
  if(st.maxSeconds&&elapsed>=st.maxSeconds){advanceStep('Vamos para o próximo teste.');return;}
  if(st.type==='freeGuided'){
    const cues=['Faça uma frase curta e pare.','Agora deixe dois tempos de espaço.','Na próxima frase, procure uma chord tone.','Use um pedaço do lick, não o lick inteiro.'];
    const idx=Math.floor(elapsed/10);
    if(idx!==session.freeCue&&idx<cues.length){session.freeCue=idx;$('#hint').textContent=cues[idx];speak(cues[idx]);}
  }
  if(elapsed>14&&!session.hintAt&& !['free','freeGuided','listen'].includes(st.type)){
    session.hintAt=elapsed;$('#feedback').className='feedback warn';$('#feedback').textContent='Travou? Ouça o exemplo novamente.';
  }
}

function targetThirdForChord(ci){const ch=CHORDS[ci];return pc(ch.root+(ch.name==='Am'?3:4));}
function evaluateNote(ev){
  if(!session)return;const st=session.steps[session.index];session.metrics.chordN++;if(CHORDS[ev.chord].tones.includes(ev.pc))session.metrics.chord++;
  if(st.type==='hits'){
    session.metrics.attempts++;if(ev.pc===st.targetPc){session.metrics.hits++;session.progress++;success(session.progress+'/'+st.required);}else fail('Procure '+NOTES[st.targetPc]+'.');
    if(session.progress>=st.required)advanceStep('Isso. Uma nota já pode ter groove.');
  }else if(st.type==='phrase'||st.type==='variation'){
    if(st.allowed&& !st.allowed.includes(ev.pc))fail(NOTES[ev.pc]+' está fora da limitação deste passo.');
  }else if(st.type==='find'){
    const target=st.targets[Math.min(session.progress,st.targets.length-1)];session.metrics.attempts++;
    if(ev.midi===target){session.metrics.hits++;session.progress++;success('Encontrou '+noteName(target)+'.');if(session.progress>=st.required)advanceStep('Agora você está encontrando a nota, não o shape.');else{const n=st.targets[session.progress];$('#instruction').textContent='Agora encontre '+noteName(n)+'.';speak('Agora encontre '+noteName(n));renderFretboard(ev.pc);}}else fail('Você tocou '+noteName(ev.midi)+'. O alvo é '+noteName(target)+'.');
  }else if(st.type==='findPc'){
    session.metrics.attempts++;if(ev.pc===st.targetPc){const used=session.findOctaves||(session.findOctaves=new Set());if(!used.has(ev.midi)){used.add(ev.midi);session.metrics.hits++;session.progress++;success('Boa. Agora outra oitava.');if(session.progress>=st.required)advanceStep('Você saiu da mesma região.');}}else fail('Procure '+NOTES[st.targetPc]+'.');
  }else if(st.type==='imitate'){
    const target=st.sequence[session.seqIndex];session.metrics.attempts++;
    if(ev.pc===pc(target)){session.metrics.hits++;session.seqIndex++;success(session.seqIndex+'/'+st.sequence.length);if(session.seqIndex>=st.sequence.length)advanceStep('Frase copiada. Agora ela entra no seu vocabulário.');}
    else{session.seqIndex=0;fail('A sequência quebrou. Ouça de novo e recomece.');}
  }else if(st.type==='rhythm'&&ev.onset){
    const beat=60/jam.bpm,bar=((audio.ctx.currentTime-jam.origin)%(beat*4)+beat*4)%(beat*4),targets=st.pattern.map(x=>x*beat);
    const near=Math.min.apply(null,targets.map(x=>Math.abs(bar-x)));
    session.metrics.attempts++;if(near<.17){session.metrics.hits++;session.progress++;success('No groove.');if(session.progress>=st.required)advanceStep('O ritmo já está carregando a frase.');}else fail('Ataque fora do desenho. Ouça o exemplo.');
  }
}
function evaluatePhrase(last){
  if(!session)return;const st=session.steps[session.index];
  if(st.type==='phrase'){
    const start=session.events[session.phraseStartIndex]||last;let ok=true;session.metrics.attempts++;
    if(st.startPc!=null&&start.pc!==st.startPc){ok=false;fail('Comece em '+NOTES[st.startPc]+'.');}
    if(st.endPc!=null&&last.pc!==st.endPc){ok=false;fail('Termine em '+NOTES[st.endPc]+'.');}
    if(ok){session.metrics.hits++;session.progress++;success('Frase resolvida.');if(session.progress>=st.required)advanceStep('Agora a frase tem direção.');}
    session.phraseStartIndex=session.events.length;
  }else if(st.type==='targetThird'){
    const target=targetThirdForChord(last.chord);session.metrics.attempts++;
    if(last.pc===target){session.metrics.hits++;session.progress++;success('Você fez '+CHORDS[last.chord].name+' aparecer.');if(session.progress>=st.required)advanceStep('Isso é tocar as mudanças.');}
    else fail('Essa frase terminou em '+NOTES[last.pc]+'. Tente pousar em '+NOTES[target]+'.');
    session.phraseStartIndex=session.events.length;
  }else if(st.type==='variation'){
    const start=session.events[session.phraseStartIndex]||last;session.metrics.attempts++;
    const seedOk=start.pc===pc(st.seed[0]),endOk=last.pc===st.endPc;
    if(seedOk&&endOk){session.metrics.hits++;session.progress++;success('Você preservou a ideia e mudou o caminho.');if(session.progress>=st.required)advanceStep('Agora o lick começou a virar linguagem.');}
    else fail('Use o começo do exemplo e resolva em '+NOTES[st.endPc]+'.');
    session.phraseStartIndex=session.events.length;
  }
}

function playDemo(){
  if(!session||!audio.ctx)return;const st=session.steps[session.index];
  if(st.demoRhythm){playRhythm(st.pattern);return;}
  if(st.type==='find'){const t=st.targets[Math.min(session.progress,st.targets.length-1)];playSequence([t],[1]);return;}
  if(st.type==='findPc'){playSequence([57,69,81],[.6,.6,.8]);return;}
  if(st.type==='targetThird'){playSequence([57,60,57,57,60,57,53,57,53,55,64,60,55,59,55],[.4,.4,.8,.4,.4,.8,.4,.4,.8,.4,.4,.8,.4,.4,.8]);return;}
  if(st.type==='imitate'){playSequence(st.sequence,[.5,.5,.5,1,.5,.5,.5,.5,1]);return;}
  if(st.type==='variation'){playSequence(st.demo||[64,67,69,67,64,57],[.5,.5,.5,.5,.5,1]);return;}
  if(st.demo){playSequence(st.demo,st.demo.map(()=>.65));return;}
  playSequence([57,60,64,62,60,57],[.4,.4,.5,.4,.4,.8]);
}
$('#demoBtn').onclick=playDemo;

function analyzeSession(s){
  const hitRate=s.metrics.attempts?s.metrics.hits/s.metrics.attempts:.55;
  const chordRate=s.metrics.chordN?s.metrics.chord/s.metrics.chordN:.45;
  const unique=new Set(s.events.map(e=>e.pc)).size;
  const updates={
    'Fretboard':s.type==='fretboard'?hitRate*100:state.skills['Fretboard'],
    'Ouvido → braço':(s.type==='fretboard'||s.type==='diagnostic')?hitRate*95:state.skills['Ouvido → braço'],
    'Finalização':hitRate*100,
    'Chord targeting':chordRate*100,
    'Vocabulário':clamp(20+unique*8,20,92),
    'Construção de frase':clamp(45+s.phraseEnds*5,30,90)
  };
  Object.entries(updates).forEach(([k,v])=>state.skills[k]=clamp(state.skills[k]*.75+v*.25,5,98));
  const score=(hitRate*55+chordRate*25+Math.min(unique/7,1)*20)*100/100;
  return{score:clamp(score,0,100),hitRate,chordRate,unique};
}
function finishLesson(manual){
  if(!session)return;
  const s=session;if(s.ticker)clearInterval(s.ticker);stopJam();const a=analyzeSession(s);
  state.history.push({date:new Date().toISOString(),label:s.label,score:a.score});state.history=state.history.slice(-30);state.tested=state.tested||s.type==='diagnostic';saveState();
  session=null;renderHome();renderProgress();renderFretboard(null);
  openModal('<h2>'+(manual?'Sessão encerrada':'Aula concluída')+'</h2><p>O treino acabou. O tutor atualizou seu próximo foco sem jogar estatísticas na sua cara durante a prática.</p><div class="actions"><button id="closeSummary" class="secondary">INÍCIO</button><button id="nextSummary" class="primary">OUTRA AULA</button></div>');
  setTimeout(()=>{$('#closeSummary').onclick=()=>{closeModal();showScreen('homeScreen');};$('#nextSummary').onclick=()=>{closeModal();startLesson('Guided Jam',buildGuided(),'guided');};},0);
}
$('#endLesson').onclick=()=>finishLesson(true);

$('#startGuided').onclick=()=>startLesson('Guided Jam',buildGuided(),'guided');
$('#startDiagnostic').onclick=()=>startLesson('Teste inicial',buildDiagnostic(),'diagnostic');
$$('.mode-card').forEach(b=>b.onclick=()=>startLesson(b.querySelector('b').textContent,buildFocused(b.dataset.mode),b.dataset.mode));

renderChords();renderFretboard(null);renderHome();renderProgress();renderSettings();
})();