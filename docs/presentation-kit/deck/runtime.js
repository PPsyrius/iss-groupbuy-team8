// Presenter runtime inlined into the HTML deck: arrows/space/n/p step through .frag[data-frag] fragments (and
// .cover[data-frag] covers) and slides; digits + Enter jump to a main slide; S notes window with timer, T start timer,
// O overview, F fullscreen, B/X backup index (slide id BX), . blank screen, Home/End, #<id> deep links and hashchange;
// a click advances (left third of the window: back). window.__PRINT__ (set by render.js) skips presenter mode so every
// slide prints as its own page.
module.exports = ({ notes, total }) => `
(function(){
const slides=[...document.querySelectorAll('.slide')];
const NOTES=${JSON.stringify(notes)};
let i=0, frag=0, t0=null, notesWin=null, jump='', shown=-1;
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
  history.replaceState(null,'','#'+slides[i].dataset.n); updNotes();}
function go(j,f){i=Math.max(0,Math.min(slides.length-1,j));frag=f===undefined?0:f;paint();}
function next(){const fs=frags();if(frag<(fs.length?fs[fs.length-1]:0)){frag++;paint();}else go(i+1);}
function prev(){if(frag>0){frag--;paint();}else if(i>0){i--;const fs=frags();go(i,fs.length?fs[fs.length-1]:0);}}
function updNotes(){if(!notesWin||notesWin.closed)return;const n=NOTES[slides[i].dataset.n];const d=notesWin.document;
  d.getElementById('h').textContent=n?('Slide '+slides[i].dataset.n+' · '+n.title):('Backup '+slides[i].dataset.n);
  d.getElementById('m').textContent=n?(n.speaker+' · slot '+(n.slot?n.slot+' s':'Q&A')+' · ≈ '+Math.round(n.words/130*60)+' s at 130 wpm'):'Q&A only';
  d.getElementById('say').innerHTML=n?n.say:'';d.getElementById('cues').innerHTML=n?n.cues.map(c=>'<li>'+c+'</li>').join(''):'';
  d.getElementById('ho').innerHTML=n&&n.handover?'Hand-over: '+n.handover:'';}
function openNotes(){notesWin=window.open('','blocbuy-notes','width=760,height=720');if(!notesWin)return;
  notesWin.document.write('<title>Notes</title><style>body{font:17px/1.45 system-ui;margin:20px;background:#1B1A18;color:#EEF1FC}#h{font-weight:700;font-size:20px}#m{color:#A9B7EE;margin:4px 0 12px}#say{font-size:19px}#cues{color:#bcd;font-size:15px}#ho{color:#FFB01F;margin-top:10px}#clock{font:700 28px monospace;color:#FFB01F;float:right}.mk{font:700 13px monospace;border:1px dashed #f9a;border-radius:4px;padding:1px 4px;color:#fbc}.clk{color:#FFB01F}</style><div id="clock">--:--</div><div id="h"></div><div id="m"></div><div id="say"></div><ul id="cues"></ul><div id="ho"></div><p style="color:#789;font-size:13px">T starts/resets the timer (in the deck window).</p>');
  updNotes();}
setInterval(()=>{if(notesWin&&!notesWin.closed&&t0){const s=Math.floor((Date.now()-t0)/1000);notesWin.document.getElementById('clock').textContent=String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');}},500);
function present(j){body.classList.remove('overview');body.classList.add('present');fit();if(j!==undefined){i=j;frag=0;}shown=-1;paint();}
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
  else if(k==='o'||k==='O'){body.classList.remove('present');body.classList.add('overview');slides.forEach(s=>s.style.transform='');slides[i].scrollIntoView({block:'center'});}
  else if(k==='s'||k==='S')openNotes();
  else if(k==='t'||k==='T')t0=Date.now();
  else if(k==='b'||k==='B'||k==='x'||k==='X')backups();
  else if(k==='.')document.querySelector('.blank').classList.toggle('on');
});
document.addEventListener('click',ev=>{
  if(body.classList.contains('overview')){const s=ev.target.closest('.slide');if(s)present(slides.indexOf(s));return;}
  if(!body.classList.contains('present'))return;
  const a=ev.target.closest('[data-go]');if(a){const k=byId(a.dataset.go);if(k>=0)go(k);return;}
  if(ev.target.closest('a,button'))return;
  ev.clientX<innerWidth/3?prev():next();
});
addEventListener('resize',()=>{if(body.classList.contains('present'))fit();});
addEventListener('hashchange',()=>{const k=byId(decodeURIComponent(location.hash.slice(1)));if(k>=0&&k!==i&&body.classList.contains('present'))go(k);});
const k=byId(decodeURIComponent(location.hash.slice(1)));
if(!window.__PRINT__){body.classList.add('present');fit();void body.offsetWidth;
  // first paint on the next frame so the opening slide plays its entrance too
  requestAnimationFrame(()=>go(k>=0?k:0));
  const h=document.querySelector('.hint');if(h){h.classList.add('show');setTimeout(()=>h.classList.remove('show'),4000);}}
})();`;
