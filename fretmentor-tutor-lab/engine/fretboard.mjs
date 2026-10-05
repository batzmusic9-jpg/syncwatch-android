import {noteName} from './harmony.mjs';
export class FretboardRenderer{
 constructor(element){this.element=element;this.id=null;const svg='http://www.w3.org/2000/svg';this.svg=document.createElementNS(svg,'svg');this.svg.setAttribute('viewBox','0 0 650 220');this.svg.setAttribute('role','img');this.svg.setAttribute('aria-label','Braço: casa 12 à esquerda, casa 0 à direita; E grave em cima, e agudo embaixo');element.append(this.svg);
  const add=(name,attributes)=>{const el=document.createElementNS(svg,name);Object.entries(attributes).forEach(([k,v])=>el.setAttribute(k,v));this.svg.append(el);return el;};
  add('rect',{x:32,y:35,width:585,height:158,rx:3,fill:'#1b262b'});
  for(let fret=0;fret<=12;fret++){const x=this.x(fret);add('line',{x1:x+22.5,y1:35,x2:x+22.5,y2:193,stroke:fret===0?'#c2cbd0':'#46565e','stroke-width':fret===0?4:1});const t=add('text',{x,y:22,fill:'#8498a3','font-size':13,'text-anchor':'middle'});t.textContent=fret;}
  for(const fret of [3,5,7,9,12]){for(const y of fret===12?[99,130]:[114])add('circle',{cx:this.x(fret),cy:y,r:3,fill:'#5d6d74'});}
  for(let string=6;string>=1;string--){const y=this.y(string);add('line',{x1:33,y1:y,x2:639,y2:y,stroke:'#91a1a9','stroke-width':.65+(string-1)*.26});const label=add('text',{x:15,y:y+4,fill:'#b0c1ca','font-size':14,'text-anchor':'middle'});label.textContent=['e','B','G','D','A','E'][string-1];}
  this.marker=add('circle',{cx:0,cy:0,r:11,fill:'#4ee2c2',opacity:0});this.label=add('text',{x:0,y:0,'text-anchor':'middle','font-size':11,'font-weight':700,fill:'#08261f',opacity:0});
 }
 x(fret){return 54+(12-fret)*45;}
 y(string){return 49+(6-string)*26;}
 render(event){if(event?.id===this.id)return;this.id=event?.id??null;if(!event){this.marker.setAttribute('opacity',0);this.label.setAttribute('opacity',0);return;}
  const x=this.x(event.fret),y=this.y(event.string);this.marker.setAttribute('cx',x);this.marker.setAttribute('cy',y);this.marker.setAttribute('fill',event.target?'#c19cff':'#4ee2c2');this.marker.setAttribute('opacity',1);this.label.setAttribute('x',x);this.label.setAttribute('y',y+4);this.label.setAttribute('opacity',1);this.label.textContent=event.fret;
  this.svg.setAttribute('aria-label',`${noteName(event.midi)}, corda ${event.string}, casa ${event.fret}${event.articulation==='bend'?', bend de um tom':''}`);
 }
}
