// SWE5006 speaker script and demo run book: the chapters of dist/swe5006-presentation-script.html.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
// SCAFFOLD: docs/swe5001/presentation/src/script.js is the full worked example (demo cards, contingencies, timeline, appendix).
const { C, md, esc, markers, countM, box, tbl, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

const segs = C.SEGMENTS.map(seg => segment(seg));

// Markers written directly in this file are counted here; slide cards and Q&A tables count their own.
const m = s => { countM(s); return md(s); };

// #464 storyline; becomes step cards (see SWE5001 demoSteps/demoCard) once the demo script is agreed.
const demo = `<section class="chap"><h1>Demo plan (draft)</h1>
<p>Live on the deployed <code>dev</code> environment, with a fallback recording made by 25 Oct.</p>
${tbl(['Step', 'Shows', 'Fallback'], [
  ['1', 'Invite a buyer to a team', m('{{FILL:recording}}')],
  ['2', 'START or JOIN with Stripe Sandbox checkout', m('{{FILL:recording}}')],
  ['3', '<code>/account</code>: orders and blocs', m('{{FILL:recording}}')],
  ['4', 'Expiry refund', m('{{FILL:recording}}')],
])}
</section>`;

const coverHtml = cover({
  kicker: 'SWE5006 · Team 8',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Monday 26 October 2026, 16:30–17:15 SGT (Zoom)',
  meta: [
    ['Responsible', 'Yu Qianshuo (Yorik) (presentation lead, #464); slide master Lim Jiechao.'],
    ['Companion files', '<code>swe5006-presentation.html</code> — the deck (open in Chrome or Edge; <b>S</b> notes, <b>T</b> timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5006-presentation.pdf</code> — the same deck as a PDF for submission.'],
    ['Single source', '<code>src/content.js</code> holds wording, timings, cues and Q&amp;A; <code>src/slides-main.js</code> and <code>src/slides-backup.js</code> hold the slide layouts and <code>src/script.js</code> this document; shared UML diagrams come from <code>docs/presentation-kit/diagrams/</code>. Run <code>npm ci</code> once in <code>docs/presentation-kit/</code>, then <code>npm run all</code> in <code>docs/swe5006/presentation/</code>.'],
    ['Status', 'Scaffold of 19 Oct 2026: placeholders until the SWE5006 guideline is applied. Slides due 23 Oct; rehearsal 25 Oct.'],
  ],
  after: box('', 'Markers', `${md('{{FILL:}}')} value still to be supplied · ${md('{{VERIFY:}}')} claim to check against the repo or report · ${md('{{CONFIRM:}}')} needs team agreement. All must be gone after the 25 Oct rehearsal.`),
});

const openItems = () => `<section class="chap"><h1>Open items</h1>
<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers in this script.</p>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
</section>`;

module.exports = coverHtml + '\n' + runSheet('') + segs.join('') + demo + openItems();
