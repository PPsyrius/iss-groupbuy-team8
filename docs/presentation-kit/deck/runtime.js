// Presenter runtime inlined into the HTML deck: arrows/space/n/p step through .frag[data-frag] fragments (and
// .cover[data-frag] covers) and slides; digits + Enter jump to a main slide; S opens the speaker companion (this deck
// with ?notes, notes-runtime.js), T starts or resets its talk timer, O overview, F fullscreen, B/X backup index (slide
// id BX), . blank screen, Home/End, #<id> deep links and hashchange; a click advances (left third of the window: back).
// window.__PRINT__ (set by render.js) skips presenter mode so every slide prints as its own page.
module.exports = ({ total }) => `
(function(){
if(new URLSearchParams(location.search).has('notes'))return;
const slides=[...document.querySelectorAll('.slide')];
let i=0, frag=0, notesWin=null, jump='', shown=-1;
const body=document.body;
const byId=id=>slides.findIndex(s=>s.dataset.n===String(id));
function fit(){const k=Math.min(innerWidth/1280,innerHeight/720);slides.forEach(s=>s.style.transform='translate(-50%,-50%) scale('+k+')');}
function steps(s){return s.querySelectorAll('.frag,.cover[data-frag]');}
function frags(){return [...steps(slides[i])].map(f=>+f.dataset.frag).filter((v,j,a)=>a.indexOf(v)===j).sort((a,b)=>a-b);}
function applyFrags(){steps(slides[i]).forEach(f=>{const n=+f.dataset.frag;if(f.classList.contains('cover'))f.classList.toggle('on',n<=frag);else f.classList.toggle('hidden',n>frag);});}
// On a slide change the fragment state is set while the slide is still inactive and styles are flushed, so
// entrance transitions start from the right state; toggling .active then replays every entrance.
function paint(){applyFrags();if(shown!==i){void slides[i].offsetWidth;shown=i;}
  slides.forEach((s,j)=>s.classList.toggle('active',j===i));
  history.replaceState(null,'','#'+slides[i].dataset.n); notify();}
function go(j,f){i=Math.max(0,Math.min(slides.length-1,j));frag=f===undefined?0:f;paint();}
function next(){const fs=frags();if(frag<(fs.length?fs[fs.length-1]:0)){frag++;paint();}else go(i+1);}
function prev(){if(frag>0){frag--;paint();}else if(i>0){i--;const fs=frags();go(i,fs.length?fs[fs.length-1]:0);}}
// Companion messages: see notes-runtime.js. A file:// page has an opaque origin that postMessage cannot target (Chrome
// still reports location.origin as 'file://'), so '*' is used there; the companion accepts messages only from this
// window, and this window only from the companion.
const ORIGIN=location.protocol==='file:'||location.origin==='null'?'*':location.origin;
// The talk timer lives here, so T works before the companion opens; the tab's sessionStorage keeps it through a reload.
// T starts it and, once running, restarts it from zero; it never stops it, so a stray T mid-talk cannot lose the pace.
const TKEY='blocbuy-talk-start';
let talkStart=(()=>{try{const v=Number(sessionStorage.getItem(TKEY));return v>0?v:null;}catch{return null;}})();
function startTimer(){talkStart=Date.now();
  try{sessionStorage.setItem(TKEY,String(talkStart));}catch{/* storage off: the timer still runs */}
  notify();}
// The companion's Home, End and B/X keys, by name: the backup hand-over tells the presenter to press them.
const JUMPS={home:()=>go(0),end:()=>go(${total - 1}),backups:()=>backups()};
function notify(){if(!notesWin||notesWin.closed)return;const fs=frags();
  notesWin.postMessage({type:'state',id:slides[i].dataset.n,frag,frags:fs.length?fs[fs.length-1]:0,talkStart},ORIGIN);}
function openNotes(){const u=new URL(location.href);u.hash='';u.search='?notes';
  notesWin=window.open(u.href,'blocbuy-notes','width=1180,height=860');}
// After a reload of this window, notesWin is gone but the companion still has this window as its opener: adopt it when
// it says hello (opener is readable across origins).
const ownsCompanion=w=>{try{return !!w&&w.opener===window;}catch{return false;}};
addEventListener('message',e=>{const m=e.data||{};
  if((!notesWin||notesWin.closed)&&m.type==='hello'&&ownsCompanion(e.source))notesWin=e.source;
  if(!notesWin||e.source!==notesWin)return;
  if(m.type==='hello')notify();else if(m.type==='go')(m.delta>0?next:prev)();else if(m.type==='timer')startTimer();
  else if(m.type==='jump'&&Object.hasOwn(JUMPS,m.to))JUMPS[m.to]();});
// Entering presenter mode applies the hidden start states under .still (no transitions), so they take effect at
// once instead of running their delayed hide transitions into the entrance that follows.
function enter(){body.classList.add('still');body.classList.remove('overview');body.classList.add('present');fit();void body.offsetWidth;body.classList.remove('still');}
function present(j){enter();if(j!==undefined){i=j;frag=0;}shown=-1;requestAnimationFrame(paint);}
function backups(){const k=byId('BX');if(k>=0)go(k);}
document.addEventListener('keydown',e=>{
  if(e.metaKey||e.ctrlKey||e.altKey)return;
  const k=e.key;
  if(body.classList.contains('overview')){if(k==='o'||k==='O'||k==='Escape')present();return;}
  if(/^[0-9]$/.test(k)){jump+=k;return;}
  if(k==='Enter'&&jump){const j=byId(+jump);jump='';if(j>=0)go(j);return;}
  jump='';
  if(['ArrowRight','ArrowDown','PageDown',' ','Enter','n'].includes(k)){e.preventDefault();next();}
  else if(['ArrowLeft','ArrowUp','PageUp','Backspace','p'].includes(k)){e.preventDefault();prev();}
  else if(k==='Home')go(0);else if(k==='End')go(${total - 1});
  else if(k==='f'||k==='F'){document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();}
  // the overview clears the blank screen: overview mode swallows '.', so a blank screen would otherwise stick
  else if(k==='o'||k==='O'){document.querySelector('.blank').classList.remove('on');body.classList.remove('present');body.classList.add('overview');slides.forEach(s=>s.style.transform='');slides[i].scrollIntoView({block:'center'});}
  else if(k==='s'||k==='S')openNotes();
  else if(k==='t'||k==='T')startTimer();
  else if(k==='b'||k==='B'||k==='x'||k==='X')backups();
  else if(k==='.')document.querySelector('.blank').classList.toggle('on');
});
document.addEventListener('click',ev=>{
  if(body.classList.contains('overview')){const s=ev.target.closest('.slide');if(s)present(slides.indexOf(s));return;}
  if(!body.classList.contains('present'))return;
  const a=ev.target.closest('[data-go]');if(a){const k=byId(a.dataset.go);if(k>=0)go(k);return;}
  // a click inside a full-size scrolling diagram (data-scroll) scrolls or drags it, never steps the deck
  if(ev.target.closest('a,button,[data-scroll]'))return;
  ev.clientX<innerWidth/3?prev():next();
});
addEventListener('resize',()=>{if(body.classList.contains('present'))fit();});
// a malformed hash (stray '%') must not abort the runtime
const hashId=()=>{try{return decodeURIComponent(location.hash.slice(1));}catch{return '';}};
addEventListener('hashchange',()=>{const k=byId(hashId());if(k>=0&&k!==i&&body.classList.contains('present'))go(k);});
const k=byId(hashId());
if(!window.__PRINT__){enter();
  // first paint on the next frame so the opening slide plays its entrance too
  requestAnimationFrame(()=>go(k>=0?k:0));
  const h=document.querySelector('.hint');if(h){h.classList.add('show');setTimeout(()=>h.classList.remove('show'),4000);}}
})();`;
