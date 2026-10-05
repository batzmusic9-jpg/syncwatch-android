// Original gestures, authored here. Columns: beat, duration, minor/blues interval,
// attack character, dynamic weight. These are whole musical gestures, not random notes.
export const gestures=[
 {id:'breath',length:6,notes:[[2/3,.25,3,'soft pick',.50],[1,1.15,7,'bend',.72],[8/3,.25,5,'pick',.46],[3,.6,3,'hammer-on',.51],[14/3,1.05,0,'sustain',.57]]},
 {id:'echo-hook',length:6,notes:[[0,.5,3,'pick',.59],[2/3,.28,7,'pick',.66],[1,1.2,10,'sustain',.74],[3,.42,3,'soft pick',.49],[11/3,.25,7,'pick',.58],[4,1.35,10,'bend',.69]]},
 {id:'falling-song',length:6,notes:[[2/3,.65,10,'sustain',.66],[5/3,.27,7,'pick',.50],[2,.7,3,'sustain',.56],[10/3,.24,5,'hammer-on',.46],[11/3,.42,3,'pick',.43],[14/3,1.0,0,'sustain',.55]]},
 {id:'blue-turn',length:6,notes:[[0,.55,3,'soft pick',.53],[2/3,.23,5,'pick',.59],[1,.23,6,'hammer-on',.45],[4/3,.8,7,'sustain',.66],[8/3,.3,10,'pick',.67],[10/3,.55,7,'pick',.52],[4,.4,5,'pick',.43],[14/3,1.0,3,'sustain',.54]]},
 {id:'one-note-conversation',length:4,notes:[[0,.3,7,'staccato',.54],[2/3,.27,7,'pick',.60],[1,.7,7,'accent',.74],[8/3,.23,5,'pick',.46],[3,.75,3,'sustain',.54]]},
 {id:'held-answer',length:4,notes:[[2/3,1.45,7,'bend',.68],[8/3,.3,5,'pick',.46],[3,.7,3,'sustain',.52]]},
 {id:'soft-turn',length:4,notes:[[1/3,.48,0,'soft pick',.48],[1,.25,3,'hammer-on',.42],[4/3,.8,5,'sustain',.53],[3,.65,3,'sustain',.48]]},
 {id:'lift-and-fall',length:8,notes:[[2/3,.28,3,'pick',.53],[1,.45,7,'pick',.59],[5/3,.25,10,'pick',.66],[2,1.25,12,'sustain',.72],[11/3,.25,10,'pick',.60],[4,.65,7,'sustain',.55],[16/3,.25,5,'pick',.47],[17/3,.5,3,'pick',.44],[7,.7,0,'sustain',.52]]},
 {id:'slow-river',length:8,notes:[[1/3,1.0,0,'soft pick',.45],[5/3,.26,3,'pick',.50],[2,1.35,7,'bend',.66],[14/3,.24,5,'pick',.45],[5,.65,3,'sustain',.47],[20/3,.9,0,'sustain',.50]]},
 {id:'triplet-release',length:4,notes:[[0,.68,7,'sustain',.58],[5/3,.23,10,'pick',.67],[2,.17,7,'pick',.54],[7/3,.17,5,'hammer-on',.43],[8/3,.17,3,'pick',.45],[3,.75,0,'sustain',.54]]}
];
export const layouts=[
 {id:'idea-echo-tail',segments:[[0,6,'NEW'],[6,6,'VARIATE'],[12,4,'ANSWER']]},
 {id:'long-line-answer',segments:[[0,8,'NEW'],[10,6,'ANSWER']]},
 {id:'short-long-echo',segments:[[0,4,'NEW'],[5,6,'CONTRAST'],[12,4,'REPEAT']]},
 {id:'two-long-lines',segments:[[0,8,'NEW'],[8,8,'DEVELOP']]},
 {id:'space-and-return',segments:[[0,6,'NEW'],[8,4,'CONTRAST'],[12,4,'ANSWER']]}
];
