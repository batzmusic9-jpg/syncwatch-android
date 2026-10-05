import {BEAT_SECONDS} from './harmony.mjs';
import {backingBar} from './backing.mjs';
export class Transport{
 constructor(ctx,renderer){this.ctx=ctx;this.renderer=renderer;this.origin=0;this.scheduledBar=0;this.running=false;this.timer=null;this.solo=null;this.visualEvents=[];this.schedulingErrors=[];}
 start(){if(this.running)return;this.origin=this.ctx.currentTime+.12;this.running=true;this.tick();this.timer=setInterval(()=>this.tick(),25);}
 timeAt(beat){return this.origin+beat*BEAT_SECONDS;}
 beatAt(time=this.ctx.currentTime){return Math.max(0,(time-this.origin)/BEAT_SECONDS);}
 nextBar(){const now=this.ctx.currentTime+.16;return Math.max(4,Math.ceil(this.beatAt(now)/4)*4);}
 tick(){if(!this.running)return;
  // Recover from a background timer stall without scheduling a burst of past notes.
  const actualBar=Math.floor(this.beatAt()/4);if(this.scheduledBar<actualBar){this.schedulingErrors.push({type:'missed-bar',bar:this.scheduledBar});this.scheduledBar=actualBar;}
  while(this.timeAt(this.scheduledBar*4)<this.ctx.currentTime+.22){for(const e of backingBar(this.scheduledBar)){const time=this.timeAt(e.startBeat);if(time>=this.ctx.currentTime+.003)this.renderer.play(e,time);}this.scheduledBar++;}
 }
 schedule(performance){if(this.solo&&this.ctx.currentTime<this.timeAt(this.solo.endBeat))throw Error('Performance já agendada');this.solo=performance;this.visualEvents=performance.events.map(e=>({...e,...this.renderer.play(e,this.timeAt(e.startBeat))}));return this.visualEvents;}
 audibleTime(){const ts=this.ctx.getOutputTimestamp?.();return ts&&ts.performanceTime>0?ts.contextTime+(performance.now()-ts.performanceTime)/1000:this.ctx.currentTime-(this.ctx.outputLatency||this.ctx.baseLatency||0);}
}
