// Speaker companion runtime, inlined into the HTML deck next to runtime.js. It runs only when the deck is opened with
// ?notes (the S key in the deck does that): it removes the slides, shows #notes and follows the deck window that opened
// it. Deck → companion: { type: 'state', id, frag, frags }; companion → deck: { type: 'hello' | 'go', delta? }.
// The deck can also send { type: 'timer' } (T pressed there). Text goes in through textContent; the HTML fields of
// #notes-data (script, cues, handover, demo) are built by notes-data.js from escaped content.
module.exports = () => `
(function(){
if(!new URLSearchParams(location.search).has('notes'))return;
const data=JSON.parse(document.getElementById('notes-data').textContent);
const byId=new Map(data.slides.map(s=>[s.id,s]));
const $=id=>document.getElementById(id);
// A file:// page has an opaque origin that postMessage cannot target (Chrome still reports location.origin as
// 'file://'), so '*' is the only option there. Both windows accept messages only from each other (event.source), so
// no other window can drive them.
const ORIGIN=location.protocol==='file:'||location.origin==='null'?'*':location.origin;
const deck=window.opener;
const post=m=>{if(deck&&!deck.closed)deck.postMessage(m,ORIGIN);};
document.title='Speaker notes · '+document.title;
document.querySelectorAll('.deck,.hint,.blank').forEach(e=>e.remove());
document.body.classList.add('notes');
const root=$('notes');root.hidden=false;
// The talk timer survives a reload of this window (pressing S again in the deck reloads it).
const KEY='blocbuy-talk-start';
const load=()=>{try{const v=Number(sessionStorage.getItem(KEY));return v>0?v:null;}catch{return null;}};
const save=v=>{try{v?sessionStorage.setItem(KEY,String(v)):sessionStorage.removeItem(KEY);}catch{/* storage off: the timer still runs */}};
let cur=data.slides[0],frag=0,frags=0,slideAt=Date.now(),startedAt=load(),heardAt=0;
const fmt=sec=>{const neg=sec<0;sec=Math.abs(Math.round(sec));return (neg?'−':'')+String(Math.floor(sec/60)).padStart(2,'0')+':'+String(sec%60).padStart(2,'0');};
function render(){
  const s=cur;
  $('n-label').textContent=s.label;
  $('n-title').textContent=s.title;
  $('n-meta').textContent=s.meta;
  $('n-slot').textContent=s.slot?' · '+s.slot:'';
  $('n-script').innerHTML=s.script;
  $('n-cues').innerHTML=s.cues.map(c=>'<li>'+c+'</li>').join('');
  $('n-cues-box').hidden=!s.cues.length;
  $('n-hand').innerHTML=s.handover;
  $('n-frag').textContent=frags?'Reveal '+frag+' / '+frags+(frag<frags?' — next click reveals more':''):'';
  $('n-next').textContent=s.next;
  $('n-demo').innerHTML=s.demo;$('n-demo').hidden=!s.demo;
}
function tick(){
  $('n-clock').textContent=new Date().toLocaleTimeString('en-GB');
  const inSlide=(Date.now()-slideAt)/1000;
  $('n-inslide').textContent=fmt(inSlide);
  $('n-inslide').className=cur.secs&&inSlide>cur.secs?'over':'';
  const delta=$('n-delta');
  if(!startedAt){$('n-elapsed').textContent='press T';delta.textContent='—';delta.className='';return;}
  const el=(Date.now()-startedAt)/1000;
  $('n-elapsed').textContent=fmt(el);
  if(cur.due==null){delta.textContent='—';delta.className='';return;}
  const d=el-cur.due;
  delta.textContent=(d>0?'behind ':'ahead ')+fmt(Math.abs(d));
  delta.className=d>20?'over':d<-20?'ahead':'';
}
function toggleTimer(){startedAt=startedAt?null:Date.now();save(startedAt);tick();}
// The companion says hello every second and the deck answers with its state, so a reloaded deck picks the companion
// up again; silence for 3 s means the deck is gone or no longer listening.
function status(){
  const msg=!deck||deck.closed?'The deck window is closed or was not found: open the deck and press S there.'
    :Date.now()-heardAt>3000?'The deck is not answering: press S in the deck window.':'';
  $('n-status').textContent=msg;
}
addEventListener('message',e=>{
  if(!deck||e.source!==deck)return;
  const m=e.data||{};
  if(m.type==='state'&&byId.has(String(m.id))){heardAt=Date.now();const s=byId.get(String(m.id));if(s!==cur)slideAt=Date.now();cur=s;frag=+m.frag||0;frags=+m.frags||0;render();tick();}
  else if(m.type==='timer')toggleTimer();
});
addEventListener('keydown',e=>{
  if(e.metaKey||e.ctrlKey||e.altKey)return;
  const k=e.key;
  if(['ArrowRight','ArrowDown','PageDown',' ','n'].includes(k)){e.preventDefault();post({type:'go',delta:1});}
  else if(['ArrowLeft','ArrowUp','PageUp','p'].includes(k)){e.preventDefault();post({type:'go',delta:-1});}
  else if(k==='t'||k==='T')toggleTimer();
  else if(k==='+'||k==='='||k==='-'){const px=parseFloat(getComputedStyle(root).getPropertyValue('--n-size'))||20;root.style.setProperty('--n-size',Math.max(12,Math.min(40,px+(k==='-'?-2:2)))+'px');}
});
$('n-prev').onclick=()=>post({type:'go',delta:-1});
$('n-nextb').onclick=()=>post({type:'go',delta:1});
$('n-timer').onclick=toggleTimer;
render();tick();status();
setInterval(()=>{tick();status();},500);
post({type:'hello'});setInterval(()=>post({type:'hello'}),1000);
})();`;
