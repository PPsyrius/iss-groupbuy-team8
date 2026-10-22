// Speaker companion markup and styles (layout from the v3 deck's notes window). The markup ships hidden in every deck
// and is shown only when the deck is opened with ?notes; notes-runtime.js fills the ids below.
const { esc } = require('../lib/text');

const CSS = `
#notes{--n-size:20px;font-family:Inter,"Helvetica Neue",Helvetica,Arial,sans-serif;color:#EDE8E0;background:#1B1A18;min-height:100vh;padding:22px 28px;font-size:var(--n-size);box-sizing:border-box}
#notes header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;border-bottom:1px solid #3a3733;padding-bottom:14px}
#notes .t1{font:600 15px 'JetBrains Mono',monospace;color:#FFB01F;letter-spacing:.05em}
#notes h1{font-weight:700;font-size:30px;margin:4px 0 2px;color:#fff}
#notes .meta{color:#b5afa6;font-size:16px}
#notes .timers{display:grid;grid-template-columns:repeat(4,auto);gap:4px 18px;text-align:right;font:600 15px Inter;color:#9a948b}
#notes .timers b{display:block;font:600 26px 'JetBrains Mono',monospace;color:#fff}
#notes .timers b.over{color:#ff8a73}
#notes .timers b.ahead{color:#9fb2ff}
#notes .script{font-size:1.45em;line-height:1.5;margin:20px 0;color:#fff;max-width:1100px}
#notes .clk{color:#FFB01F}
#notes .box{background:#262522;border:1px solid #3a3733;border-radius:14px;padding:12px 18px;margin:12px 0}
#notes .box h3{margin:0 0 6px;font-size:14px;letter-spacing:.12em;text-transform:uppercase;color:#FFB01F}
#notes ul{margin:0;padding-left:20px}
#notes li{margin:4px 0}
#notes .hand{color:#ffb4a6;font-style:italic}
#notes .frag{color:#FFB01F;font-weight:700;margin-top:6px}
#notes .nav{display:flex;gap:10px;align-items:center;margin-top:12px;color:#b5afa6}
#notes button{font:600 16px Inter;background:#E8402A;color:#fff;border:0;border-radius:10px;padding:10px 16px;cursor:pointer}
#notes button:focus-visible{outline:3px solid #FFB01F;outline-offset:2px}
#notes .dstep{border-top:1px solid #3a3733;padding:8px 0}
#notes .dstep i{color:#9fb2ff}
#notes .dstep p{margin:4px 0;color:#fff}
#notes .dstep small{color:#ffb4a6}
#notes .status{color:#ff8a73;font-weight:600}
#notes .keys{color:#8a847b;font-size:13px;margin-top:14px}
body.notes{background:#1B1A18;overflow:auto}
`;

const markup = ({ talkStart }) => `<div id="notes" hidden>
  <header><div><div class="t1" id="n-label"></div><h1 id="n-title"></h1><div class="meta"><span id="n-meta"></span><span id="n-slot"></span></div></div>
  <div class="timers"><span>Clock<b id="n-clock"></b></span><span>On slide<b id="n-inslide"></b></span><span>Talk elapsed<b id="n-elapsed"></b></span><span>Pace vs plan<b id="n-delta"></b></span></div></header>
  <div class="script" id="n-script"></div>
  <div class="frag" id="n-frag"></div>
  <div class="box" id="n-cues-box"><h3>Cues</h3><ul id="n-cues"></ul></div>
  <div class="box"><h3>Hand-over / next line</h3><div class="hand" id="n-hand"></div></div>
  <div class="box" id="n-demo" hidden></div>
  <div class="nav"><button type="button" id="n-prev">◀ Back</button><button type="button" id="n-nextb">Next ▶</button><button type="button" id="n-timer">Start / reset talk timer (T)</button><span id="n-next"></span></div>
  <div class="status" id="n-status" role="status"></div>
  <div class="keys">Keys here: → / ← move the deck · T start or reset the talk timer · + / − text size. Pace compares the talk timer with the planned start of the current slide (${esc(talkStart)} = 0).</div>
</div>`;

module.exports = { CSS, markup };
