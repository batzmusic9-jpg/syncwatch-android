import {BEAT_SECONDS} from './harmony.mjs';
export class Transport{
 constructor(ctx,renderer){this.ctx=ctx;this.renderer=renderer;this.origin=0;this.running=false;this.solo=null;this.visualEvents=[];this.schedulingErrors=[];}
 start(){if(this.running)return;this.origin=this.ctx.currentTime+.16;this.renderer.startBacking(this.origin);this.running=true;}
 timeAt(beat){return this.origin+beat*BEAT_SECONDS;}
 beatAt(time=this.ctx.currentTime){return Math.max(0,(time-this.origin)/BEAT_SECONDS);}
 nextBar(){const now=this.ctx.currentTime+.18;return Math.max(4,Math.ceil(this.beatAt(now)/4)*4);}
 schedule(performance){if(this.solo&&this.ctx.currentTime<this.timeAt(this.solo.endBeat))throw Error('Performance já agendada');this.solo=performance;this.visualEvents=performance.events.map(e=>({...e,...this.renderer.play(e,this.timeAt(e.startBeat))}));return this.visualEvents;}
 audibleTime(){const ts=this.ctx.getOutputTimestamp?.();return ts&&ts.performanceTime>0?ts.contextTime+(performance.now()-ts.performanceTime)/1000:this.ctx.currentTime-(this.ctx.outputLatency||this.ctx.baseLatency||0);}
}
