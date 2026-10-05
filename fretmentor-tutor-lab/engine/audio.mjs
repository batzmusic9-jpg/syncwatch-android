import {BEAT_SECONDS,LOOP_BEATS} from './harmony.mjs';
export class AudioRenderer{
 constructor(ctx){this.ctx=ctx;this.buffers={lead:[]};this.roundRobin={};this.active=new Set();this.master=ctx.createGain();this.master.gain.value=.78;
  const limiter=ctx.createDynamicsCompressor();limiter.threshold.value=-4;limiter.knee.value=5;limiter.ratio.value=6;limiter.attack.value=.003;limiter.release.value=.12;this.master.connect(limiter);limiter.connect(ctx.destination);
  this.backingBus=ctx.createGain();this.backingBus.gain.value=.76;this.backingBus.connect(this.master);
  this.leadBus=ctx.createGain();this.leadBus.gain.value=1.0;
  const high=ctx.createBiquadFilter();high.type='highpass';high.frequency.value=140;
  const drive=ctx.createWaveShaper();const curve=new Float32Array(4096);for(let i=0;i<curve.length;i++){const x=i*2/(curve.length-1)-1;curve[i]=Math.tanh(x*1.8)/Math.tanh(1.8);}drive.curve=curve;drive.oversample='2x';
  const low=ctx.createBiquadFilter();low.type='lowpass';low.frequency.value=4900;low.Q.value=.55;
  const body=ctx.createBiquadFilter();body.type='peaking';body.frequency.value=1150;body.Q.value=.8;body.gain.value=1.5;
  this.leadBus.connect(high);high.connect(drive);drive.connect(low);low.connect(body);body.connect(this.master);
  const delay=ctx.createDelay(1);delay.delayTime.value=BEAT_SECONDS/2;const feedback=ctx.createGain();feedback.gain.value=.13;const damp=ctx.createBiquadFilter();damp.frequency.value=2800;const wet=ctx.createGain();wet.gain.value=.09;body.connect(delay);delay.connect(damp);damp.connect(wet);wet.connect(this.master);damp.connect(feedback);feedback.connect(delay);
 }
 async load(progress=()=>{}){
  const response=await fetch('./samples/lead-manifest.json');if(!response.ok)throw Error('Manifesto da guitarra indisponível');const manifest=await response.json();let done=0,index=0;
  const backing=async()=>{const r=await fetch('./tracks/admiral-bob-cm.ogg');if(!r.ok)throw Error('Backing indisponível');this.backingBuffer=await this.ctx.decodeAudioData(await r.arrayBuffer());const expected=LOOP_BEATS*BEAT_SECONDS;if(Math.abs(this.backingBuffer.duration-expected)>.01)throw Error('Duração do backing não corresponde à grade');progress(++done/(manifest.length+1));};
  const worker=async()=>{while(index<manifest.length){const item=manifest[index++];const r=await fetch('./'+item.url);if(!r.ok)throw Error('Sample ausente: '+item.url);const buffer=await this.ctx.decodeAudioData(await r.arrayBuffer());let peak=0;for(let c=0;c<buffer.numberOfChannels;c++)for(const v of buffer.getChannelData(c))peak=Math.max(peak,Math.abs(v));this.buffers.lead.push({...item,buffer,peak});progress(++done/(manifest.length+1));}};
  await Promise.all([backing(),...Array.from({length:4},worker)]);
  const peak=Math.max(...this.buffers.lead.map(s=>s.peak));for(const sample of this.buffers.lead)sample.trimGain=.37/Math.max(.001,peak);
 }
 startBacking(time){if(this.backingSource)return;const source=this.ctx.createBufferSource();source.buffer=this.backingBuffer;source.loop=true;source.loopStart=0;source.loopEnd=LOOP_BEATS*BEAT_SECONDS;source.connect(this.backingBus);source.start(time);this.backingSource=source;}
 play(e,startTime){
  const ctx=this.ctx,kind=e.articulation==='hammer-on'?'hammer-on':e.articulation==='staccato'?'staccato':'pick';const layer=e.velocity<.46?'soft':e.velocity>.68?'hard':'medium';let candidates=this.buffers.lead.filter(s=>s.articulation===kind&&(s.layer===layer||s.layer==='any'));
  if(!candidates.length)candidates=this.buffers.lead.filter(s=>s.articulation==='pick');
  const physicalMidi=e.bendFromMidi??e.midi;const distance=Math.min(...candidates.map(s=>Math.abs(s.midi-physicalMidi)));candidates=candidates.filter(s=>Math.abs(s.midi-physicalMidi)===distance).sort((a,b)=>a.rr-b.rr);
  const key=kind+'-'+physicalMidi+'-'+layer,rr=this.roundRobin[key]||0;this.roundRobin[key]=rr+1;const sample=candidates[rr%candidates.length];if(!sample)throw Error('Sample ausente');
  const source=ctx.createBufferSource(),gain=ctx.createGain();source.buffer=sample.buffer;source.connect(gain);gain.connect(this.leadBus);source.playbackRate.value=2**((physicalMidi-sample.midi)/12);
  const t=startTime+(e.timingOffsetSeconds||0),end=t+e.durationBeats*BEAT_SECONDS,attack=e.attackSeconds||.002;const peak=sample.trimGain*(.58+e.velocity*.55);
  gain.gain.setValueAtTime(0,t);gain.gain.linearRampToValueAtTime(peak,t+attack);gain.gain.setValueAtTime(peak,Math.max(t+attack,end-.04));gain.gain.linearRampToValueAtTime(0,end);
  if(e.bendFromMidi!==null&&e.bendFromMidi!==undefined){const b=e.bend||{onsetSeconds:.07,riseSeconds:.3,releaseSeconds:0};source.playbackRate.setValueAtTime(source.playbackRate.value,t+b.onsetSeconds);source.playbackRate.exponentialRampToValueAtTime(2**((e.midi-sample.midi)/12),t+b.onsetSeconds+b.riseSeconds);if(b.releaseSeconds){source.playbackRate.setValueAtTime(2**((e.midi-sample.midi)/12),end-b.releaseSeconds);source.playbackRate.exponentialRampToValueAtTime(2**((physicalMidi-sample.midi)/12),end-.02);}}
  if(e.vibrato?.enabled){const v=e.vibrato,from=t+v.delaySeconds;for(let time=from;time<end-.05;time+=.012){const phase=time-from;source.detune.setValueAtTime(Math.sin(phase*2*Math.PI*v.rate)*v.depthCents*Math.min(1,phase/.35),time);}}
  source.start(t);source.stop(end+.005);this.active.add(source);source.onended=()=>{source.disconnect();gain.disconnect();this.active.delete(source);};return {startTime:t,endTime:end};
 }
}
