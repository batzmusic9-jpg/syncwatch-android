import {BEAT_SECONDS} from './harmony.mjs';
export class AudioRenderer{
 constructor(ctx){this.ctx=ctx;this.buffers={guitar:[],bass:[],drums:[]};this.roundRobin=0;this.active=new Set();
  this.master=ctx.createGain();this.master.gain.value=.78;
  const compressor=ctx.createDynamicsCompressor();compressor.threshold.value=-16;compressor.knee.value=12;compressor.ratio.value=2.2;compressor.attack.value=.015;compressor.release.value=.18;
  this.master.connect(compressor);const limiter=ctx.createDynamicsCompressor();limiter.threshold.value=-3;limiter.knee.value=0;limiter.ratio.value=20;limiter.attack.value=.001;limiter.release.value=.08;compressor.connect(limiter);limiter.connect(ctx.destination);this.buses={};
  for(const [name,gain,hp,lp] of [['drums',.62,35,12500],['bass',.65,35,1800],['guitar',.65,180,4800],['lead',1.05,130,6200]]){
   const bus=ctx.createGain();bus.gain.value=gain;const high=ctx.createBiquadFilter();high.type='highpass';high.frequency.value=hp;const low=ctx.createBiquadFilter();low.type='lowpass';low.frequency.value=lp;bus.connect(high);high.connect(low);low.connect(this.master);this.buses[name]=bus;
   if(name==='lead'||name==='guitar'){
    const room=ctx.createConvolver(),wet=ctx.createGain();wet.gain.value=name==='lead'?.09:.05;
    const impulse=ctx.createBuffer(2,Math.floor(ctx.sampleRate*.27),ctx.sampleRate);
    // Procedural room impulse only; all instrument sources are recorded samples.
    for(let c=0;c<2;c++){const a=impulse.getChannelData(c);let s=170+c;for(let i=0;i<a.length;i++){s=(s*16807)%2147483647;a[i]=(s/2147483647*2-1)*Math.exp(-i/(ctx.sampleRate*.045))*.16;}}
    room.buffer=impulse;low.connect(room);room.connect(wet);wet.connect(this.master);
   }
  }
 }
 async load(progress=()=>{}){
  const response=await fetch('./samples/manifest.json');if(!response.ok)throw Error('Manifesto indisponível');const manifest=await response.json(),list=Object.entries(manifest).flatMap(([type,items])=>items.map(item=>({type,item})));let complete=0,index=0;
  const worker=async()=>{while(index<list.length){const {type,item}=list[index++];const r=await fetch('./'+item.url);if(!r.ok)throw Error('Sample indisponível: '+item.url);const buffer=await this.ctx.decodeAudioData(await r.arrayBuffer());let peak=0;for(let c=0;c<buffer.numberOfChannels;c++)for(const v of buffer.getChannelData(c))peak=Math.max(peak,Math.abs(v));this.buffers[type].push({...item,buffer,peak});progress(++complete/list.length);}};
  await Promise.all(Array.from({length:4},worker));
  // Calibrate quiet rendered percussion against recorded guitar before mixing.
  for(const type of ['guitar','bass','drums']){const bankPeak=Math.max(...this.buffers[type].map(s=>s.peak));for(const sample of this.buffers[type])sample.trimGain=(type==='guitar'?.45:type==='bass'?.5:.7)/Math.max(.001,type==='drums'?sample.peak:bankPeak);}
 }
 play(e,startTime){
  const ctx=this.ctx,type=e.instrument||'lead',bank=type==='lead'?'guitar':type;let sample;
  if(bank==='drums')sample=this.buffers.drums.find(s=>s.name===e.key);
  else{const midi=e.midi??e.key;let candidates=this.buffers[bank];if(bank==='guitar'){const layer=e.velocity>.72?'hard':'soft';candidates=candidates.filter(s=>s.layer===layer||s.layer==='single');}const distance=Math.min(...candidates.map(s=>Math.abs(s.midi-midi)));candidates=candidates.filter(s=>Math.abs(s.midi-midi)===distance);sample=candidates[(this.roundRobin++)%candidates.length];}
  if(!sample)throw Error('Sample ausente: '+type);
  const source=ctx.createBufferSource(),gain=ctx.createGain();source.buffer=sample.buffer;source.connect(gain);gain.connect(this.buses[type]);
  const midi=e.midi??e.key,initialMidi=e.bendFromMidi??e.slideFromMidi??midi;
  if(bank!=='drums')source.playbackRate.value=Math.pow(2,(initialMidi-sample.midi)/12);
  let duration=e.durationBeats*BEAT_SECONDS;const t=startTime+(e.timingOffsetSeconds||0),end=t+duration;
  // Never loop the attack. Long lead notes use the recorded natural decay.
  const peak=sample.trimGain*Math.max(.001,e.velocity)*(e.articulation==='hammer-on'||e.articulation==='pull-off'?.78:1);
  gain.gain.setValueAtTime(0,t);gain.gain.linearRampToValueAtTime(peak,t+(e.attackSeconds||.003));gain.gain.setValueAtTime(peak,Math.max(t+.02,end-.03));gain.gain.linearRampToValueAtTime(0,end);
  if(e.bendFromMidi!==null&&e.bendFromMidi!==undefined){const rate=Math.pow(2,(midi-sample.midi)/12);source.playbackRate.setValueAtTime(source.playbackRate.value,t);source.playbackRate.exponentialRampToValueAtTime(rate,t+.22);}
  if(e.slideFromMidi!==null&&e.slideFromMidi!==undefined){source.playbackRate.setValueAtTime(source.playbackRate.value,t);source.playbackRate.exponentialRampToValueAtTime(Math.pow(2,(midi-sample.midi)/12),t+.085);}
  if(e.vibrato?.enabled){const v=e.vibrato,from=t+v.delaySeconds;for(let time=from;time<end-.04;time+=.012)source.detune.setValueAtTime(Math.sin((time-from)*2*Math.PI*v.rate)*v.depthCents*Math.min(1,(time-from)/.18),time);}
  source.start(t);source.stop(end+.005);this.active.add(source);source.onended=()=>{source.disconnect();gain.disconnect();this.active.delete(source);};return {startTime:t,endTime:end};
 }
}
