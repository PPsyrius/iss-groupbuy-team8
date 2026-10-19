// node src/deck.js -> dist/swe5001-presentation.html
const fs = require('fs');
const path = require('path');
const { fontCSS, md, esc, words } = require('./lib');
const C = require('./content');
const CSS = require('./deck-css');
const MAIN = require('./slides-main');
let BACK = {};
try { BACK = require('./slides-backup'); } catch (e) { if (!/Cannot find module/.test(e.message)) throw e; }

const TOTAL = C.SLIDES.length;
const SEGMIN = C.SEGMENTS.map(s => s.talk + s.qa);

function footer(seg, label) {
  const bar = C.SEGMENTS.map((s, i) => {
    const cls = s.n === seg ? 'on' : (s.n < seg ? 'done' : '');
    return `<span class="${cls}" style="flex:${SEGMIN[i]}"></span>`;
  }).join('');
  const sg = C.SEGMENTS[seg - 1];
  return `<div class="foot"><div class="brand"><b>BlocBuy</b> Team 8 · SWE5001</div><div class="seg">${seg} · ${sg.short}</div><div class="bar">${bar}</div><div class="pg">${label}</div></div>`;
}

function mainSlide(s) {
  const seg = C.SEGMENTS[s.seg - 1];
  const sp = C.SPEAKERS[s.speaker];
  const body = MAIN[s.n]();
  if (s.n === 1 || s.n === 24) return `<section class="slide dark" id="s${s.n}" data-n="${s.n}">${body}</section>`;
  return `<section class="slide" id="s${s.n}" data-n="${s.n}">
  <div class="hdr">${seg.n} · ${esc(seg.head)} <span class="it">${s.item}</span></div>
  <div class="who"><span class="av">${sp.init}</span>${esc(s.speaker)}</div>
  <div class="ttl" style="${s.title.length > 58 ? 'font-size:30.5px;top:62px' : ''}">${esc(s.title)}</div>
  <div class="body">${body}</div>
  ${footer(s.seg, `${s.n}<small>/${TOTAL}</small>`)}
</section>`;
}

function backupSlide(b) {
  return `<section class="slide backup" id="${b.id}" data-n="${b.id}">
  <div class="hdr">Backup · Q&amp;A only <span class="it">${b.id}</span></div>
  ${b.speaker ? `<div class="who"><span class="av">${C.SPEAKERS[b.speaker].init}</span>${esc(b.speaker)}</div>` : ''}
  <div class="ttl">${esc(b.title)}</div>
  <div class="body">${b.body}</div>
  <div class="foot"><div class="brand"><b>BlocBuy</b> Team 8 · SWE5001</div><div class="seg" style="width:auto;color:var(--foreground)">Backup · not presented unless asked</div><div class="bar"><span style="flex:1"></span></div><div class="pg">${b.id}</div></div>
</section>`;
}

const notes = {};
for (const s of C.SLIDES) notes[s.n] = { title: s.title, speaker: s.speaker, slot: s.slot, say: md(s.say).replace(/\[click\]/g, '<b class="clk">[click]</b>'), cues: (s.cues || []).map(md), handover: s.handover ? md(s.handover) : '', words: words(s.say) };

const sections = C.SLIDES.map(mainSlide);
if (BACK.list) sections.push(...BACK.list().map(backupSlide));

const RUNTIME = `
(function(){
const slides=[...document.querySelectorAll('.slide')];
const NOTES=${JSON.stringify(notes)};
let i=0, frag=0, t0=null, notesWin=null;
const body=document.body;
function fit(){const k=Math.min(innerWidth/1280,innerHeight/720);slides.forEach(s=>s.style.transform='translate(-50%,-50%) scale('+k+')');}
function frags(){return [...slides[i].querySelectorAll('.frag')].map(f=>+f.dataset.frag).filter((v,j,a)=>a.indexOf(v)===j).sort((a,b)=>a-b);}
function paint(){slides.forEach((s,j)=>s.classList.toggle('active',j===i));
  slides[i].querySelectorAll('.frag').forEach(f=>f.classList.toggle('hidden',+f.dataset.frag>frag));
  history.replaceState(null,'','#'+slides[i].dataset.n); updNotes();}
function go(j,f){i=Math.max(0,Math.min(slides.length-1,j));frag=f===undefined?0:f;paint();}
function next(){const fs=frags();if(frag<(fs.length?fs[fs.length-1]:0)){frag++;paint();}else go(i+1);}
function prev(){if(frag>0){frag--;paint();}else{go(i-1);const fs=frags();frag=fs.length?fs[fs.length-1]:0;paint();}}
function updNotes(){if(!notesWin||notesWin.closed)return;const n=NOTES[slides[i].dataset.n];const d=notesWin.document;
  d.getElementById('h').textContent=n?('Slide '+slides[i].dataset.n+' · '+n.title):('Backup '+slides[i].dataset.n);
  d.getElementById('m').textContent=n?(n.speaker+' · slot '+(n.slot?n.slot+' s':'Q&A')+' · ≈ '+Math.round(n.words/130*60)+' s at 130 wpm'):'Q&A only';
  d.getElementById('say').innerHTML=n?n.say:'';d.getElementById('cues').innerHTML=n?n.cues.map(c=>'<li>'+c+'</li>').join(''):'';
  d.getElementById('ho').innerHTML=n&&n.handover?'Hand-over: '+n.handover:'';}
function openNotes(){notesWin=window.open('','blocbuy-notes','width=760,height=720');if(!notesWin)return;
  notesWin.document.write('<title>Notes</title><style>body{font:17px/1.45 system-ui;margin:20px;background:#1B1A18;color:#EEF1FC}#h{font-weight:700;font-size:20px}#m{color:#A9B7EE;margin:4px 0 12px}#say{font-size:19px}#cues{color:#bcd;font-size:15px}#ho{color:#FFB01F;margin-top:10px}#clock{font:700 28px monospace;color:#FFB01F;float:right}.mk{font:700 13px monospace;border:1px dashed #f9a;border-radius:4px;padding:1px 4px;color:#fbc}.clk{color:#FFB01F}</style><div id="clock">--:--</div><div id="h"></div><div id="m"></div><div id="say"></div><ul id="cues"></ul><div id="ho"></div><p style="color:#789;font-size:13px">T starts/resets the timer (in the deck window).</p>');
  updNotes();}
setInterval(()=>{if(notesWin&&!notesWin.closed&&t0){const s=Math.floor((Date.now()-t0)/1000);notesWin.document.getElementById('clock').textContent=String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');}},500);
document.addEventListener('keydown',e=>{
  if(body.classList.contains('overview')){if(e.key==='o'||e.key==='O'||e.key==='Escape'){body.classList.remove('overview');body.classList.add('present');fit();paint();}return;}
  if(['ArrowRight','PageDown',' ','Enter'].includes(e.key)){e.preventDefault();next();}
  else if(['ArrowLeft','PageUp','Backspace'].includes(e.key)){e.preventDefault();prev();}
  else if(e.key==='Home')go(0);else if(e.key==='End')go(${TOTAL - 1});
  else if(e.key==='f'||e.key==='F'){document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();}
  else if(e.key==='o'||e.key==='O'){body.classList.remove('present');body.classList.add('overview');slides.forEach(s=>s.style.transform='');slides[i].scrollIntoView({block:'center'});}
  else if(e.key==='s'||e.key==='S')openNotes();
  else if(e.key==='t'||e.key==='T')t0=Date.now();
  else if(e.key==='b'||e.key==='B'){const k=slides.findIndex(s=>s.dataset.n==='BX');if(k>=0)go(k);}
});
slides.forEach((s,j)=>s.addEventListener('click',ev=>{if(body.classList.contains('overview')){i=j;frag=0;body.classList.remove('overview');body.classList.add('present');fit();paint();}
  else{const a=ev.target.closest('[data-go]');if(a){const k=slides.findIndex(x=>x.dataset.n===a.dataset.go);if(k>=0)go(k);}}}));
addEventListener('resize',fit);
const h=decodeURIComponent(location.hash.slice(1));const k=slides.findIndex(s=>s.dataset.n===h);
if(!window.__PRINT__){body.classList.add('present');fit();go(k>=0?k:0);}
})();`;

const html = `<!doctype html><html lang="en-SG"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BlocBuy · SWE5001 Team 8</title>
<style>${fontCSS()}\n${CSS}</style></head>
<body><div class="deck">
${sections.join('\n')}
</div>
<script>${RUNTIME}</script></body></html>`;

fs.mkdirSync(path.join(__dirname, '..', 'dist'), { recursive: true });
fs.writeFileSync(path.join(__dirname, '..', 'dist', 'swe5001-presentation.html'), html);
console.log('slides:', sections.length, 'bytes:', html.length);
