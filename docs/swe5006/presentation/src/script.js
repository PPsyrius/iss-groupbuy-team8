// SWE5006 speaker script and demo run book: the chapters of dist/swe5006-presentation-script.pdf.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
// Worked example and tone: docs/swe5001/presentation/src/script.js. The #566 run books (pipeline steps P1–P7,
// system demo steps 1–8, contingencies) are in git history (379af301:docs/swe5006/presentation/src/script.js) for reuse.
const { C, md, esc, markers, countM, box, tbl, pre, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

// Markers written directly in this file are counted here; slide cards and Q&A tables count their own.
// m(): plain text with markers (escaped); mh(): HTML that may contain markers (not escaped). Call each once per string.
const m = s => { countM(s); return md(s); };
const mh = s => { countM(s); return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\}/g, x => md(x)); };

const DEV = 'https://d1b3np88z657cp.cloudfront.net';
const REPO = 'PPsyrius/iss-groupbuy-team8';
const seg = n => C.SEGMENTS.find(s => s.n === n);
const speakersOf = list => [...new Set(list.map(s => C.SPEAKERS[s.speaker].full.split(' (')[0]))].join(', ');

// ---------- run sheet: coverage of every guideline item and every rubric row, derived from SLIDES ----------
const coverageRows = C.SEGMENTS.flatMap(s => Object.entries(s.items).map(([k, v]) => {
  const sl = C.SLIDES.filter(x => x.item === k);
  return [`<b>${k}</b>`, esc(v), sl.map(x => x.n).join(', ') || '—', speakersOf(sl)];
}));
const rubricRows = ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '2.1', '2.2', '2.3', '2.4', '2.5', '2.6', '3.1', '3.2', '3.3', '4.1', '4.2', '4.3', '4.4', '4.5', '4.6', '5.1', '5.2', '5.3', '5.4', '5.5']
  .map(r => { const sl = C.SLIDES.filter(x => (x.rubric || []).includes(r)); return [`<b>${r}</b>`, sl.map(x => x.n).join(', ') || '—', speakersOf(sl)]; });

// Tips: the four "Presentation Tips" on page 2 of the guidelines (verbatim); none conflicts with page 1 (rubric open decision 1).
const tips = [
  ['Summarize and highlight the significant achievements. Talk more about the significant and interesting parts of the solution and implementation. Your audience should remember the unique aspects of the solution and not similarities to other solutions.', 'Depth goes to what is unique: pay-first teams that refund themselves, money safety under concurrency and failure (UC4 design, statechart, design problems), and a pipeline from pull request to dev in minutes.'],
  ['Manage your time well. Do not present too many slides. Remember that the audience can read the text in the slide so refrain from reading them.', '36 main slides in 34 minutes, two of them live demos; slots checked at 130 wpm in the table above; sample wording adds to the slide instead of reading it; details sit in backups B1–B12.'],
  ['Explain your design in top-down manner. Make sure that your audience understands the requirements, the high-level design and the high-level decisions that you make before drilling into some details.', 'Problem, scope and quality attributes first (slides 2–3); then architecture (9), transition strategies (10–11), use cases (12), one use case from analysis to design (13–15), design problems (16–21), schema (22).'],
  ['Ensure that the contents on the slides are readable from the distance at which your audience is sitting. This is particular true for face-to-face presentation.', 'Rubric V1–V7: one idea per slide, diagrams cropped to the part discussed, labels checked on a projector or TV before 26 Oct.'],
];

const cueWords = [
  ['1 MIN', 'One minute of talk left in this segment', 'Lim'],
  ['TIME', 'Stop; finish the sentence and hand over', 'Lim'],
  ['Q: &lt;text&gt; → name', 'A question was parked; the named owner answers it in the buffer (17:04–17:10)', 'Lim'],
  ['BEHIND n / AHEAD n', 'The talk is n seconds behind or ahead of the run sheet; the next speaker applies the trims', 'Lim'],
  ['SHARE', 'Next person: start screen sharing now', 'Lim'],
  ['CLIP n', 'Live step n failed; play the recording of step n', 'Pan or Yorik'],
];

const handovers = C.SLIDES.filter((s, i) => i > 0 && s.speaker !== C.SLIDES[i - 1].speaker && s.slot !== null)
  .map(s => [String(s.n), `${C.SPEAKERS[C.SLIDES[s.n - 2].speaker].full} → ${C.SPEAKERS[s.speaker].full}`]);

const runSheetHtml = runSheet(`<p>34 minutes scripted, 16:30–17:04; the 6 minutes to 17:10 are the buffer for questions asked during the talk (rubric G1–G3). §1 runs 3 minutes and §2 6 minutes so that each member presents their own design problem (rubric G4, time budget).</p>
${box('warn', 'Interruption policy', `<ul style="margin:0;padding-left:12pt"><li>A lecturer may ask at any moment. The current speaker answers in <b>one or two sentences</b> (decision → reason → evidence or limitation), says “back to …” and continues from the same slide.</li><li>If the owner is someone else, the speaker says so and Lim posts <code>Q: &lt;question&gt; → owner</code> in the backchannel; parked questions are answered first in the buffer.</li><li>Hard checkpoints: 16:33 Eric, 16:39 Liu Jin, 16:42 Pan, 16:52 Yorik.</li></ul>`)}
<h2>Handovers (rubric G4: rehearse each one)</h2>
${tbl(['From slide', 'Speakers'], handovers)}
<h2>Every guideline item has slides and a speaker</h2>
${tbl(['Item', 'Guideline requirement (verbatim)', 'Slides', 'Speaker'], coverageRows)}
<h2>Every rubric row has a slide (rubric checklist)</h2>
${tbl(['Rubric row', 'Slides', 'Speaker'], rubricRows)}
<h2>Presentation tips checklist</h2>
${tbl(['Tip (guidelines page 2)', 'How this deck meets it'], tips)}
<h2>Cue words in the team backchannel (never the Zoom chat)</h2>
${tbl(['Message', 'Meaning', 'Sent by'], cueWords.map(r => [`<code>${r[0]}</code>`, r[1], r[2]]))}`);

// ---------- segments ----------
const segsHtml = [
  segment(seg(1), {
    tip: 'Lim opens at 16:30:00 sharp, with the deck shared and the notes window off the shared screen. Eight slides in three minutes: the tables carry the facts, the wording carries only the point of each slide.',
    warn: mh('Never present planned hours as actual effort (slide 5), and never say payments or refunds work on dev unless the 24 Oct dry run proved it (slide 4). {{CONFIRM:slide 4 wording after the 23 Oct go/no-go}}'),
  }),
  segment(seg(2), {
    tip: 'Eric presents 2.1–2.4 and his own design problem; then Yorik, Pan and Lim each have one 20-second slide on their own problem; Liu Jin closes with the full-form State problem and the schema, and carries straight on into §3. The next speaker unmutes during the previous speaker’s last sentence.',
    warn: mh('Segment 2 has no slack: answer design questions in one sentence and park the rest. {{FILL:segment 2 slides pending (slides 9–22)}}'),
  }),
  segment(seg(3), {
    tip: 'Liu Jin continues from slide 22 without a handover.',
    warn: mh('Never present a local or SWE5001 result as a SWE5006 dev result: say where each number ran. {{FILL:segment 3 slides pending (slides 23–24)}}'),
  }),
  segment(seg(4), {
    tip: 'Slides 25–29 take 270 s; the live pipeline run fills slide 30 (330 s).',
    warn: mh('{{FILL:pipeline run book (steps, preconditions, screen layout, fallback clips) pending}}'),
  }),
  segment(seg(5), {
    tip: 'Slide 31 opens the demo; slide 32 is the live two-user run (510 s); slides 33–35 cover performance, scalability and maintainability.',
    warn: mh('{{FILL:system demo run book (go/no-go table, steps, preconditions, fallback clips) pending}}'),
  }),
].join('');

// ---------- timeline ----------
const timeline = `<section class="chap"><h1>6 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Wed 21 Oct', 'Deck re-planned from the rubric; §1 drafted.', 'Lim'],
  ['Fri 23 Oct', '<b>Slides due</b> (#464). Each owner reviews their slides, cards and Q&amp;A rows; the Sprint 4 review and retro feed slides 5 and 7. <b>18:00 go/no-go</b> for the live payment steps, posted in #464.', 'All; Yorik decides with Lim'],
  ['Sat 24 Oct', '<b>Backup videos</b> of both demos recorded on whatever is deployed then, 1080p, one clip per step, stored in the cloud and on a USB drive (rubric G6). k6 pre-run on the SWE5001 ECS environment; keep its summary.', 'Pan (pipeline); Yorik, Eric (system)'],
  ['Sun 25 Oct', '<b>Timed rehearsal</b> in Zoom at the real clock with injected questions; every rubric checklist box ticked; every marker resolved; rebuild deck and script.', 'All; Lim times'],
])}
<h2>Presentation day (Mon 26 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 12:00', `Health and recent runs (A.1); ${m('{{FILL:demo preconditions}}')}`, 'Yorik, Pan'],
  ['14:30', 'Tech check in a test Zoom: mic, camera, sharing, Do Not Disturb.', 'All'],
  ['15:00', 'Merge freeze on <code>dev</code> until 17:15.', 'Lim'],
  ['16:30', 'Start; follow the run sheet. The scribe logs every remark verbatim (rubric G9).', 'Lim; scribe'],
  ['17:04 / 17:10', 'Slide 36; parked questions; end.', 'Lim'],
])}
<h2>After the presentation</h2>
<ul><li>The same evening: every lecturer remark goes into #463 as a report task (by 27 Oct, rubric G9).</li><li>Day log under <code>docs/agile/sprint-04/log/</code>: the demo PR, run links and any payment or refund ids shown (no secrets).</li></ul>
</section>`;

// ---------- open items (rendered after every counted chapter) ----------
const openItems = () => `<section class="chap"><h1>7 Open items before the 25 Oct rehearsal</h1>
<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers in this script. <b>FILL</b> is a value that does not exist yet, so never present a guess; <b>VERIFY</b> is a claim to check against the repository, the report or run logs; <b>CONFIRM</b> needs team agreement. All must be gone after the 25 Oct rehearsal.</p>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
</section>`;

// ---------- appendix ----------
const appendix = `<section class="chap"><h1>A Appendix — check commands</h1>
<p>Every command is read-only. Run them off the shared screen, from a checkout of <code>dev</code> at the repository root.</p>
<h3>A.1 Health and recent runs</h3>${pre(`curl -s ${DEV}/api/actuator/health          # expect "status":"UP"
gh run list --repo ${REPO} --workflow ci-frontend.yml --branch dev --limit 3
gh run list --repo ${REPO} --workflow ci-backend.yml --branch dev --limit 3
gh pr list --repo ${REPO} --base dev --state open      # what could still merge before the freeze`)}
</section>
<section class="chap"><h1>B Appendix — backup slides (questions only)</h1>
<p>Backups follow slide 36 in the HTML deck and the PDF. In the deck press <b>B</b> for the index (BX) and click a row, or open <code>#B6</code> directly.</p>
${tbl(['ID', 'Slide', 'Answers'], C.BACKUPS.map(b => b.map(esc)))}
</section>`;

// ---------- cover ----------
const coverHtml = cover({
  kicker: 'SWE5006 Practice Project · Graduate Certificate in Designing Modern Software Systems',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Monday 26 October 2026, 16:30–17:10 SGT (Zoom)',
  meta: [
    ['Responsible', 'Yu Qianshuo (Yorik), presentation lead and storyline (#464); Lim Jiechao, slide master.'],
    ['Specification', '<code>docs/swe5006/presentation/RUBRIC.md</code>: every slide records the rubric rows it covers; the coverage tables in the run sheet are derived from them.'],
    ['Companion files', '<code>swe5006-presentation.html</code> — the deck (36 slides + backups BX, B1–B12; Chrome or Edge; <b>S</b> notes, <b>T</b> timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5006-presentation.pdf</code> — the same deck for submission.'],
    ['Facts as of', '21 Oct 2026. Dev health <code>UP</code>; #223 and #517 open, so no payment has been confirmed and no refund proven on dev yet.'],
  ],
  after: `${box('', 'Markers', `${md('{{FILL:}}')} value that does not exist yet; never present a guess · ${md('{{VERIFY:}}')} claim to check against the repository, report or run logs · ${md('{{CONFIRM:}}')} needs team agreement. They must all be gone after the 25 Oct rehearsal.`)}`,
});

// Segment chapters render before openItems(), so its counts include their markers.
const body = segsHtml + timeline;
module.exports = coverHtml + '\n' + runSheetHtml + body + openItems() + appendix;
