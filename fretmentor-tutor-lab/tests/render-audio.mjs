// Optional QA dependency: npm install --no-save node-web-audio-api
const {OfflineAudioContext}=await import(process.env.WEB_AUDIO_QA_MODULE||'node-web-audio-api');
import fs from 'node:fs/promises';
import {AudioRenderer} from '../engine/audio.mjs';
import {Composer} from '../engine/composer.mjs';
import {BEAT_SECONDS} from '../engine/harmony.mjs';
const root=new URL('../',import.meta.url);
globalThis.fetch=async path=>new Response(await fs.readFile(new URL(path,root)));
const sampleRate=44100,composer=new Composer(),loader=new AudioRenderer(new OfflineAudioContext(2,44100,sampleRate));
await loader.load();console.log('decoded',loader.buffers.lead.length,loader.backingBuffer.duration);
const results=[];
for(let seed=1;seed<=20;seed++){
 const performance=composer.compose((seed%12)*4,seed), duration=(performance.endBeat+1)*BEAT_SECONDS,ctx=new OfflineAudioContext(2,Math.ceil(duration*sampleRate),sampleRate),renderer=new AudioRenderer(ctx);
 renderer.buffers=loader.buffers;renderer.backingBuffer=loader.backingBuffer;renderer.startBacking(0);
 for(const e of performance.events)renderer.play(e,e.startBeat*BEAT_SECONDS);
 const output=await ctx.startRendering();let peak=0,sum=0,count=0,nonfinite=0;
 for(let c=0;c<2;c++){let data=new Float32Array(output.length);output.copyFromChannel(data,c);for(const v of data){peak=Math.max(peak,Math.abs(v));sum+=v*v;count++;if(!Number.isFinite(v))nonfinite++;}}
 results.push({seed,layout:performance.layout,notes:performance.events.length,peak,rms:Math.sqrt(sum/count),nonfinite});
 console.log(JSON.stringify(results.at(-1)));
}
if(results.some(r=>r.nonfinite||r.peak>=1))throw Error('Invalid or clipped audio');
console.log(JSON.stringify({takes:results.length,maxPeak:Math.max(...results.map(r=>r.peak)),minRms:Math.min(...results.map(r=>r.rms)),backingDuration:loader.backingBuffer.duration,samplesDecoded:loader.buffers.lead.length,perceptualApproval:false},null,2));
