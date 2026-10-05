import {Composer} from './engine/composer.mjs';
import {AudioRenderer} from './engine/audio.mjs';
import {Transport} from './engine/transport.mjs';
import {FretboardRenderer} from './engine/fretboard.mjs';
import {chordAt,noteName} from './engine/harmony.mjs';
const $=s=>document.querySelector(s),button=$('#improvise'),status=$('#status'),composer=new Composer(),fretboard=new FretboardRenderer($('#fretboard'));
let ctx,renderer,transport,initializing=false,currentPhrase=-1,finished=false,muted=false,seed=Date.now()>>>0,lastStatus='';
function setStatus(s){if(s!==lastStatus){status.textContent=s;lastStatus=s;}}
async function initialize(){
 ctx=new AudioContext({latencyHint:'interactive'});await ctx.resume();renderer=new AudioRenderer(ctx);await renderer.load(p=>setStatus('Carregando instrumentos · '+Math.round(p*100)+'%'));transport=new Transport(ctx,renderer);transport.start();$('#mute').disabled=false;
 ctx.onstatechange=()=>{if(ctx.state==='suspended'||ctx.state==='interrupted'){button.disabled=false;button.textContent='RETOMAR ÁUDIO';setStatus('Áudio pausado pelo dispositivo.');}};
}
button.onclick=async()=>{
 if(initializing)return;button.disabled=true;initializing=true;
 try{
  if(!ctx)await initialize();else if(!transport){await ctx.close();await initialize();}else if(ctx.state!=='running'){await ctx.resume();if(transport.solo&&ctx.currentTime<transport.timeAt(transport.solo.endBeat)){initializing=false;return;}}
  const start=transport.nextBar();const performance=composer.compose(start,++seed);transport.schedule(performance);window.lab.lastPerformance=performance;finished=false;currentPhrase=-1;$('#summary').hidden=true;button.textContent='ENTRANDO NO PRÓXIMO COMPASSO…';setStatus('PRÓXIMO COMPASSO');
 }catch(e){console.error(e);button.disabled=false;button.textContent='TENTAR NOVAMENTE';setStatus('Falha ao carregar áudio. '+e.message);}finally{initializing=false;}
};
$('#mute').onclick=()=>{muted=!muted;renderer.master.gain.setTargetAtTime(muted?0:.78,ctx.currentTime,.025);$('#mute').textContent=muted?'ATIVAR SOM':'SILENCIAR';$('#mute').setAttribute('aria-pressed',String(muted));};
function draw(){requestAnimationFrame(draw);if(!transport||ctx.state!=='running')return;const time=transport.audibleTime(),beat=transport.beatAt(time),bar=Math.floor(beat/4),local=beat%4;
 document.querySelectorAll('#chords span').forEach((el,i)=>el.classList.toggle('active',i===bar%4));document.querySelectorAll('#beats i').forEach((el,i)=>el.classList.toggle('active',i===Math.floor(local)));
 const solo=transport.solo;if(!solo)return;
 if(beat<solo.startBeat){button.textContent='ENTRANDO NO PRÓXIMO COMPASSO…';setStatus('PRÓXIMO COMPASSO · '+Math.ceil(solo.startBeat-beat));return;}
 if(beat>=solo.endBeat){if(!finished){finished=true;button.disabled=false;button.textContent='IMPROVISAR';fretboard.render(null);$('#position').textContent='—';$('#note').textContent='—';$('#phrase').textContent='IDEIA COMPLETA';$('#target').textContent='A jam continua. Ouça a próxima variação.';setStatus('QUESTION → DEVELOPMENT → CLIMAX → RESOLUTION');$('#summary').hidden=false;$('#summary').replaceChildren(...solo.phrases.map((p,i)=>{const line=document.createElement('div');line.textContent=`${i+1} · ${p.intent} — ${noteName(p.target)} · ${p.targetRole} de ${p.chord}`;return line;}));}return;}
 button.textContent='TUTOR TOCANDO';setStatus('OUÇA A IDEIA');const phraseIndex=Math.min(3,Math.floor((beat-solo.startBeat)/4)),phrase=solo.phrases[phraseIndex];
 if(currentPhrase!==phraseIndex){currentPhrase=phraseIndex;$('#phrase').textContent=phrase.intent;$('#target').textContent=`Target: ${noteName(phrase.target)} · ${phrase.targetRole} de ${phrase.chord}`;}
 const event=transport.visualEvents.find(e=>time>=e.startTime&&time<e.endTime);fretboard.render(event);$('#note').textContent=event?noteName(event.midi):'—';$('#position').textContent=event?`${event.string}ª corda · casa ${event.fret}${event.articulation==='bend'?' ↑ bend':''}`:'—';
 if($('#debug').open)$('#debug-data').textContent=JSON.stringify({seed:solo.seed,chord:chordAt(beat).name,bar,beat:+local.toFixed(2),midi:event?.midi,string:event?.string,fret:event?.fret,harmonicRole:event?.harmonicRole,phraseRole:phrase.intent,velocity:event?.velocity,articulation:event?.articulation,target:event?.target,motifId:solo.motif.id,relationship:solo.relationship,schedulingErrors:transport.schedulingErrors},null,2);
}
window.lab={composer,get transport(){return transport;},get renderer(){return renderer;},get ctx(){return ctx;},lastPerformance:null};
requestAnimationFrame(draw);
if('serviceWorker' in navigator)navigator.serviceWorker.register('./sw.js').catch(console.warn);
