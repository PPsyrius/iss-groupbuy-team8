// SWE5006 speaker script and demo run book: the chapters of dist/swe5006-presentation-script.pdf.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
// Worked example and tone: docs/swe5001/presentation/src/script.js. The #566 run books (pipeline steps P1–P7,
// system demo steps 1–8, contingencies) are in git history (379af301:docs/swe5006/presentation/src/script.js) for reuse.
const { C, md, esc, markers, countM, box, tbl, pre, reqBox, slideCard, qaTable, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

// Markers written directly in this file are counted here; slide cards and Q&A tables count their own.
// m(): plain text with markers (escaped); mh(): HTML that may contain markers (not escaped). Call each once per string.
const m = s => { countM(s); return md(s); };
const mh = s => { countM(s); return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\}/g, x => md(x)); };

const DEV = C.DEV;
const REPO = 'PPsyrius/iss-groupbuy-team8';
const seg = n => C.SEGMENTS.find(s => s.n === n);
const speakersOf = list => [...new Set(list.map(s => C.SPEAKERS[s.speaker].full.split(' (')[0]))].join(', ');

// ---------- run sheet: coverage of every guideline item and every rubric row, derived from SLIDES ----------
const coverageRows = C.SEGMENTS.flatMap(s => Object.entries(s.items).map(([k, v]) => {
  const sl = C.SLIDES.filter(x => x.item === k || (x.alsoItems || []).includes(k));
  return [`<b>${k}</b>`, esc(v), sl.map(x => x.n).join(', ') || '—', speakersOf(sl)];
}));
const rubricRows = ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '2.1', '2.2', '2.3', '2.4', '2.5', '2.6', '3.1', '3.2', '3.3', '4.1', '4.2', '4.3', '4.4', '4.5', '4.6', '5.1', '5.2', '5.3', '5.4', '5.5']
  .map(r => { const sl = C.SLIDES.filter(x => (x.rubric || []).includes(r)); return [`<b>${r}</b>`, sl.map(x => x.n).join(', ') || '—', speakersOf(sl)]; });

// Tips: the four "Presentation Tips" on page 2 of the guidelines (verbatim); none conflicts with page 1 (rubric open decision 1).
const tips = [
  ['Summarize and highlight the significant achievements. Talk more about the significant and interesting parts of the solution and implementation. Your audience should remember the unique aspects of the solution and not similarities to other solutions.', 'Depth goes to what is unique: pay-first teams that refund themselves, money safety under concurrency and failure (UC4 design, statechart, design problems), and a pipeline from pull request to dev in minutes.'],
  ['Manage your time well. Do not present too many slides. Remember that the audience can read the text in the slide so refrain from reading them.', '33 main slides in 33 minutes, two of them live demos; slots checked at 130 wpm in the table above; sample wording adds to the slide instead of reading it; details sit in backups B1–B21.'],
  ['Explain your design in top-down manner. Make sure that your audience understands the requirements, the high-level design and the high-level decisions that you make before drilling into some details.', 'Problem, scope and quality attributes first (slides 2–3); then architecture (9), transition strategies (10–11), use cases (12), one use case from analysis to design (13–15; statechart B18), design problems (16–18; Factory Method B21), schema (19).'],
  ['Ensure that the contents on the slides are readable from the distance at which your audience is sitting. This is particular true for face-to-face presentation.', 'Rubric V1–V7: one idea per slide, diagrams cropped to the part discussed, labels checked on a projector or TV before 26 Oct.'],
];

const cueWords = [
  ['1 MIN', 'One minute of talk left in this segment', 'Lim'],
  ['TIME', 'Stop; finish the sentence and hand over', 'Lim'],
  ['Q: &lt;text&gt; → name', 'A question was parked; the named owner answers it in the buffer (17:03–17:05) or in questions to 17:10', 'Lim'],
  ['BEHIND n / AHEAD n', 'The talk is n seconds behind or ahead of the run sheet; the next speaker applies the trims', 'Lim'],
  ['SHARE', 'Next person: start screen sharing now', 'Lim'],
  ['CLIP n', 'Live step n failed; play the recording of step n', 'Pan or Yorik'],
  ['EXPIRY OK / REFUND OK / REFUND LATE', 'State of the 16:05 expiry bloc (16:10 and 16:50)', 'Yorik'],
];

const handovers = C.SLIDES.filter((s, i) => i > 0 && s.speaker !== C.SLIDES[i - 1].speaker && s.slot !== null)
  .map(s => [String(s.n), `${C.SPEAKERS[C.SLIDES[s.n - 2].speaker].full} → ${C.SPEAKERS[s.speaker].full}`]);

const runSheetHtml = runSheet(`<p>33 minutes scripted, 16:30–17:03, at the lower end of the course ranges, with §3’s third minute moved to §2 (rulings of 25 Oct, after the rehearsal ran 47 minutes); a 2-minute buffer to 17:05, then questions to the 17:10 hard stop (rubric G1–G3). Time each section separately at the next rehearsal.</p>
${box('warn', 'Interruption policy', `<ul style="margin:0;padding-left:12pt"><li>A lecturer may ask at any moment. The current speaker answers in <b>one or two sentences</b> (decision → reason → evidence or limitation), says “back to …” and continues from the same slide.</li><li>If the owner is someone else, the speaker says so and Lim posts <code>Q: &lt;question&gt; → owner</code> in the backchannel; parked questions are answered first in the buffer.</li><li>Hard checkpoints: 16:33 Eric, 16:38 Liu Jin, 16:41 Pan, 16:51 Yorik.</li></ul>`)}
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

// ---------- demo step cards ----------
const demoCard = d => `<div class="card demo"><div class="hd"><span>Step ${d.n} &nbsp; ${esc(d.t)}</span><small>${d.time}</small></div><div class="bd">
 <div class="row"><span class="lab">Proves</span><span>${d.proves}</span></div>
 <div class="row"><span class="lab cue">Operate</span><ol style="margin:0;padding-left:14pt">${d.op.map(o => `<li>${mh(o)}</li>`).join('')}</ol></div>
 <div class="row"><span class="lab say">Say</span><span class="say">${m(d.say)}</span></div>
 <div class="row"><span class="lab">Expect</span><span>${mh(d.exp)}</span></div>
 <div class="row"><span class="lab fb">Fallback</span><span>${mh(d.fb)}</span></div></div></div>`;

// ---------- segment 4: CI/CD pipeline demo (rubric 4.1–4.6) ----------
// Slides 22–27 run 16:41:00–16:45:30; the live part (slide 28) runs 16:45:30–16:51:00 (330 s).
const pipeSteps = C.DEMOS[28];

const seg4Html = () => {
  const s4 = seg(4);
  return `<section class="chap"><h1>4 ${esc(s4.name)} (${s4.talk} minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:41:00 – 16:51:00 (slides 22–27 until 16:45:30, then the live pipeline demo on slide 28 until 16:51:00)</td></tr><tr><td>Owner</td><td>${esc(s4.owner)}; Lim approves as the frontend code owner</td></tr><tr><td>Slides</td><td>${C.SLIDES.filter(s => s.seg === 4).map(s => s.n).join(', ')}</td></tr></table></div>
${reqBox(s4)}
${box('tip', 'Tip', 'Slides 22–27 take 270 s, each slide one topic, each concept explained once. The live demo is frontend only: Pan opens the demo pull request at ≈ 15:30 on the final dev, so its checks (median 4.8 min) finish before the talk. A backend change cannot finish its 16-minute PR checks inside the segment, so the demo shows a backend PR run and its deploy run that finished earlier, said as earlier runs (rubric 4.1).')}
${C.SLIDES.filter(s => s.seg === 4).map(slideCard).join('')}
<h2>Live pipeline demo (16:45:30 – 16:51:00, 330 s)</h2>
${tbl(['Step', 'Shows', 'Rubric'], pipeSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0]]))}
<h3>Preconditions (checked at 15:30 by Pan)</h3>
<ul><li>Demo branch (frontend only): the home page heading “Buy better together” → “Buy better, together” (one line, frontend/src/routes/index.tsx:114; no test asserts it; decided 24 Oct), author Pan, approver Lim (frontend code owner). After the 15:00 merge freeze, Pan rebases it onto the final <code>origin/dev</code>, pushes it and opens the PR at ≈ 15:30; its checks finish by ≈ 15:40. Lim does not approve before P2.</li>
<li>Merge freeze in the team chat: nobody merges into <code>dev</code> from 15:00 to 17:15 except the demo PR, on air. A moved <code>dev</code> makes the demo branch out of date and re-runs its checks (strict required checks); if that happens before 16:00, rebase and re-push, after 16:00 leave it and use <code>CLIP P3</code>.</li>
<li>Backend evidence, pinned in tabs 3 and 4: the latest green <code>ci-backend</code> pull-request run and the <code>ci-backend</code> push run on <code>dev</code> that deployed that change, both finished before the talk; they are said as earlier runs, never as this change.</li>
<li>Latest <code>ci-frontend</code> and <code>ci-backend</code> runs on <code>dev</code> green (A.1); deploy switches <code>ENABLE_S3_DEPLOY</code> and <code>ENABLE_EC2_DEPLOY</code> unchanged; GitHub Actions budget not exhausted (a budget stop hit run 39442654274 on 20 Oct).</li>
<li>Lim signed in to GitHub on his own machine with the PR list open; he is not sharing at 16:46.</li>
<li>Backup video (rubric G6, 4.6): <code>swe5006-ci-cd.mp4</code>, recorded on dev on Sat 24 Oct (4 min 18 s): finished <code>ci-frontend</code> and <code>ci-backend</code> runs with their job graphs, a merged PR, the <code>e2e-dev</code> smoke log and the dev site. It shows typical finished runs, not the live heading change, so say so if it is played. Shared in the team chat; open in a player on Pan’s machine and tested with Zoom “share sound” off. Never present it as the current live result.</li></ul>
<h3>Screen layout (Pan’s browser window, shared from 16:45:30; browser zoom 125–150 %, rubric V7)</h3>
${tbl(['Tab', 'Content', 'Prepared state'], [
  ['1', 'GitHub: the demo pull request', 'Opened ≈ 15:30, checks finished; Files changed tab ready'],
  ['2', 'GitHub Actions: <code>ci-frontend</code> filtered to branch <code>dev</code>', 'Refresh after the merge to see the new run'],
  ['3', 'GitHub Actions: a backend PR run that finished earlier', 'Run summary open; build-quality one click away'],
  ['4', 'GitHub Actions: that change’s push run on <code>dev</code> (deploy)', 'Run graph open; deploy job one click away'],
  ['5', `Dev site <code>${DEV}</code>`, 'On the page the change touches; DevTools closed'],
  ['Player', 'Clips P1–P6 (24 Oct)', 'Paused at the start of clip P1'],
])}
<h3>Step by step</h3>
${pipeSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Clicking Merge on anything other than the demo PR; “Update branch” on the demo PR; re-running a failed job; running <code>rollback-backend.yml</code> or any <code>workflow_dispatch</code>; opening Settings → Secrets and variables or the ruleset editor; pushing to <code>dev</code>; any AWS console or SSM session on the shared screen. If the PR is not merged by 16:48, leave it open and merge after 17:15, so a deploy never lands during the system demo.')}
${box('tip', 'Tip', 'Narrate results, not clicks: “checks green in four minutes fifty”, not “now I click the checks tab”. No troubleshooting on air.')}
${qaTable(4)}
</section>`;
};

// ---------- segment 5: system demo (rubric 5.1–5.5) ----------
// Slide 29 16:51:00–16:51:30; live demo (slide 30) 16:51:30–17:00:00 (510 s); slides 31–32 17:00:00–17:03:00.
// Eric shares one screen with two Chrome profiles and operates; Yorik speaks. US34-04 is a team of two with the
// shortest allowed window (2,400 s = PurchaseQuoteWindow.SECONDS; scripts/dev-demo-listings.json).
const demoSteps = C.DEMOS[30];

const seg5Html = () => {
  const s5 = seg(5);
  return `<section class="chap"><h1>5 ${esc(s5.name)} (${s5.talk} minutes, then ${s5.qa} minutes of buffer)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:51:00 – 17:03:00 (slide 29 to 16:51:30; live demo on slide 30 to 17:00:00; slides 31–32 to 17:03:00) · 17:03–17:05 buffer for parked questions, questions to 17:10</td></tr><tr><td>Owner</td><td>${esc(s5.owner)}; Lim chairs the buffer</td></tr><tr><td>Slides</td><td>${C.SLIDES.filter(s => s.seg === 5).map(s => s.n).join(', ')}</td></tr></table></div>
${reqBox(s5)}
${box('tip', 'Tip', 'Eric shares one screen at 16:51:00 (<code>SHARE</code> from Pan) with the deck in the Leader window, tab 1, and operates every window; Yorik speaks throughout. The expiry bloc is started at 16:05 so that D4 shows a refund that no one triggered by hand.')}
${slideCard(C.SLIDES.find(s => s.n === 29))}
<h2>How each step is shown (25 Oct rehearsal; no payment on air)</h2>
${tbl(['Step', 'Shown from', 'If it fails'], [
  ['D1', 'The purchase both buyers completed in Stripe Sandbox on dev on 24 Oct: the listing live, the Checkout form from the 24 Oct recording, the Leader’s saved receipt live', '<code>CLIP D1</code>, said as recorded'],
  ['D2', 'The teammate’s 24 Oct recording of the join and payment (#657), said as recorded on dev on 24 Oct', 'Say it in one sentence; D3 shows the result'],
  ['D3', 'The 24 Oct bloc on dev: “2 of 2 joined · Filled 24 Oct, 7:17 pm”, its order Confirmed', '<code>CLIP D3</code>'],
  ['D4', 'The bloc started at 16:05 that expired and refunded on dev before the talk', '<code>CLIP D4</code>; then B11'],
  ['D5', 'Live: one 403 on an admin API', 'Say what came back; move on'],
  ['D6', 'The saved green CI run of the last-seat test', 'The test file on GitHub'],
])}
${box('warn', 'Watch out', 'Yorik’s GO of 25 Oct (#605) covered a live START and JOIN; the 25 Oct rehearsal replaced it: nothing is paid during the presentation. Never start a bloc, open a new Checkout or pay on air. Evidence: START and the expiry refund on dev on 23 Oct (PR #664), the second buyer’s paid JOIN on 24 Oct (#657; #590, #517, #223 closed 25 Oct); crash recovery under replay (#224) is not shown.')}
<h2>Preconditions (checked at 15:30 by Eric and Yorik)</h2>
<ul><li>Health <code>UP</code> (A.1); latest runs on <code>dev</code> green; merge freeze in force; the §4 demo PR is the only change that may land.</li>
<li>Two Google buyer accounts signed in on Eric’s machine in two Chrome profiles, “Leader”, shown as <b>wood kik</b>, and “Joiner”, shown as <b>los gai</b> (as in the 24 Oct backup recording); the same two on Yorik’s machine as standby. Neither needs admin: the listings check uses the public catalogue, and the Joiner must <b>not</b> hold the admin role, or D5’s 403 fails; Lim checks it at the 25 Oct rehearsal.</li>
<li>At <b>≈ 16:05</b> (latest 16:07, so the refund lands before the 16:50 check and §5 starts at 16:51) Yorik, as account B, starts a bloc on <code>US34-04</code> and pays; nobody joins; receipt bookmarked in the Joiner window, tab 5. It is due ≈ 16:45; the sweep (every minute) expires it and the refund worker (every 30 s) refunds it ≈ 16:46–16:48. Post <code>EXPIRY OK</code> at 16:10 and <code>REFUND OK</code> / <code>REFUND LATE</code> at 16:50.</li>
<li>Listings converged: <code>scripts/seed-dev-demo-listings.sh --check</code> passes for the four <code>US34-*</code> listings (≤ 12:00, Yorik).</li>
<li>D5: the admin URL ready to paste; Joiner DevTools closed.</li>
<li>D1–D3 sources: the Leader’s 24 Oct receipt open in Leader tab 3; the Joiner’s <code>/account</code> showing the 24 Oct bloc Filled and its order Confirmed in Joiner tab 2; the player cued at D1’s Checkout form and at clip D2.</li>
<li>D6: the saved green <code>ci-backend</code> run of the last-seat test pinned in Leader tab 5. The IDE stays open on a local checkout for questions only (rubric G10).</li>
<li>Backup video (rubric G6, 5.5): <code>swe5006-system.mp4</code>, recorded on dev on Sat 24 Oct (8 min 27 s, #657): paid start, a second buyer’s paid join (“2 of 2 joined · Filled 24 Oct, 7:17 pm”), My orders and My blocs, the expiry refund (“Refund sent”), the admin API denial, then the code, a CI run and the k6 table. Shared in the team chat; open in a player on Eric’s machine (copy on Yorik’s), tested with Zoom “share sound” off. Never present it as the current live result.</li>
<li>Browser zoom 125–150 % (rubric V7); Do Not Disturb on; no password manager, email or bookmarks bar visible.</li></ul>
<h2>Screen layout (Eric’s shared screen)</h2>
${tbl(['Window · tab', 'Content', 'Prepared state'], [
  ['Leader · 1', 'Deck <code>swe5006-presentation.html#29</code>', 'Slide 29; slides 31–33 follow'],
  ['Leader · 2', `Listing <code>US34-04</code> on <code>${DEV}</code>`, 'Signed in as account A'],
  ['Leader · 3', 'The Leader’s 24 Oct receipt', '“Payment received”'],
  ['Leader · 5', 'Saved CI run of the D6 test', 'Log at the test; test file one click away'],
  ['Joiner · 2', '<code>/account</code> (account B)', 'The 24 Oct bloc Filled; the 16:05 bloc under Refunded'],
  ['Joiner · 5', 'Receipt of the 16:05 payment', 'Bookmarked at 16:10; reload before D4'],
  ['Player', 'The 24 Oct recording', 'Cued at D1’s Checkout form, then clip D2'],
])}
<h2>Live system demo (16:51:30 – 17:00:00, 510 s)</h2>
${tbl(['Step', 'Shows', 'Rubric'], demoSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0]]))}
${slideCard(C.SLIDES.find(s => s.n === 30))}
<h3>Step by step</h3>
${demoSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Editing any deadline, price or listing on shared dev; any SSM command, database client or AWS console on the shared screen; the Stripe dashboard (keys and webhook secrets live there); the DevTools Application or Network tab with cookies or headers visible; racing two buyers or replaying a webhook on dev; a second payment to “try again”; pointing k6 at dev.')}
${box('tip', 'Plan C', 'A step fails live and has no recording: show its local evidence (B11 for payment and refund, the D6 test for the race, B12 for load) and say: <i>“verified locally, not yet on dev.”</i> Never present a local result as a dev one.')}
<h2>After the demo (17:00:00 – 17:03:00)</h2>
<h3>k6 pre-run for slide 31 (rubric 5.3)</h3>
<ul><li>No pre-run (decided 24 Oct): slides 21 and 31 keep the 16 Oct ECS run (run 20261016-v2-monolith-repeat, #670); it is complete and evidenced, and a new run would need an ECS session for little gain.</li>
<li>Slide 31’s table shows the 16 Oct run (#670). On the day the report file is opened from Eric’s machine if asked. Never point k6 at the SWE5006 dev host.</li></ul>
${C.SLIDES.filter(s => s.n >= 31).map(slideCard).join('')}
${qaTable(5)}
</section>`;
};

// ---------- segments ----------
const segsHtml = [
  segment(seg(1), {
    tip: 'Lim opens at 16:30:00 sharp, with the deck shared and the notes window off the shared screen. Eight slides in three minutes: the tables carry the facts, the wording carries only the point of each slide.',
    warn: mh('Never present planned hours as actual effort (slide 5). Paid start and the expiry refund were shown on dev on 23 Oct (PR #664), a second buyer’s paid join on 24 Oct (system demo backup recording, #657); never claim crash recovery on dev (#224).'),
  }),
  segment(seg(2), {
    tip: 'Eric presents 2.1–2.4 and hands over after slide 15; then Yorik has one short slide on his problem (Eric’s, Pan’s and Lim’s are backups B21, B19 and B20 since the 25 Oct rehearsal); Liu Jin closes with the full-form State problem and the schema, and carries straight on into §3. The next speaker unmutes during the previous speaker’s last sentence.',
    warn: mh('Handover allowance: the two handovers inside §2 (slides 16 and 17) get about 15 s each, rehearsed short: the next speaker unmutes and starts with their first line, no greeting beyond their name. Beyond that, segment 2 has no slack: answer design questions in one sentence and park the rest.'),
  }),
  segment(seg(3), {
    tip: 'Liu Jin continues from slide 19 without a handover.',
    warn: mh('Never present a local or SWE5001 result as a SWE5006 dev result: say where each number ran.'),
  }),
  seg4Html(),
  seg5Html(),
].join('');

// ---------- timeline ----------
const timeline = `<section class="chap"><h1>6 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Wed 21 Oct', 'Deck re-planned from the rubric; §1 drafted.', 'Lim'],
  ['Fri 23 Oct', '<b>Slides due</b> (#464). Each owner reviews their slides, cards and Q&amp;A rows; the Sprint 4 review and retro feed slides 5 and 7.', 'All'],
  ['Sat 24 Oct', '<b>Full dry run on dev</b> of D1–D6 in order and timed: a <code>US34-04</code> bloc started ≈ 10:00 by account B expires and refunds (D4), START and JOIN on <code>US34-04</code> with both accounts (D1, D2); payment and refund ids in the day log (no secrets). <b>Backup videos</b> of both demos recorded on the final Sprint 4 build deployed to dev (Sprint 4 ended 23 Oct), 1080p, one clip per step, stored in the cloud and on a USB drive (rubric G6). Optional <b>k6 pre-run</b> on the SWE5001 ECS environment (slide 31 already shows the 16 Oct run, #670). <b>Go/no-go</b> for the live payment steps after the dry run, posted in #464 (superseded by the 25 Oct rehearsal: no payment on air).', 'Yorik, Eric (system); Pan (pipeline, k6); Yorik decides the go/no-go with Lim'],
  ['Sun 25 Oct', '<b>Timed rehearsal</b> in Zoom at the real clock with injected questions; every diagram slide and its crops checked on a projector or TV from across the room (rubric V7); every rubric checklist box ticked; every marker resolved; rebuild deck and script.', 'All; Lim times'],
])}
<h2>Presentation day (Mon 26 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 12:00', 'Health and recent runs (A.1); listings check; §5 preconditions list started. After 11:41, the scheduled <code>dast-baseline</code> run checked for slide 25 (24 Oct dispatch 40107924516 green).', 'Yorik, Pan'],
  ['14:30', 'Tech check in a test Zoom: mic, camera, sharing, Do Not Disturb.', 'All'],
  ['15:00', 'Merge freeze on <code>dev</code> until 17:15.', 'Lim'],
  ['15:30', 'Demo branch rebased onto the final <code>origin/dev</code> and its PR opened (checks done by ≈ 15:40; no approval yet); pin the backend PR and deploy runs in tabs 3 and 4. Check the §4 and §5 preconditions lists.', 'Pan (§4); Eric, Yorik (§5)'],
  ['≈ 16:05 (≤ 16:07)', 'Start the expiry bloc on <code>US34-04</code> as account B and pay; bookmark the receipt. <code>EXPIRY OK</code> at 16:10, <code>REFUND OK</code> / <code>REFUND LATE</code> at 16:50.', 'Yorik'],
  ['16:30', 'Start; follow the run sheet. All five note every lecturer question and remark verbatim (rubric G9).', 'Lim; all take notes'],
  ['17:03 / 17:10', 'Slide 33; parked questions; end.', 'Lim'],
])}
<h2>After the presentation</h2>
<ul><li>After the talk: the five members collate their notes into one GitHub issue; every lecturer remark in it becomes a #463 report task by 27 Oct (rubric G9).</li><li>Day log under <code>docs/agile/sprint-04/log/</code>: the demo PR, run links and any payment or refund ids shown (no secrets).</li></ul>
</section>`;

// ---------- open items (rendered after every counted chapter) ----------
const openItems = () => `<section class="chap"><h1>7 Open items before the talk (26 Oct)</h1>
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
<p>Backups follow slide 33 in the HTML deck and the PDF. In the deck press <b>B</b> for the index (BX) and click a row, or open <code>#B6</code> directly.</p>
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
    ['Companion files', '<code>swe5006-presentation.html</code> — the deck (33 slides + backups BX, B1–B21, the 1:1 diagram detail pages D9-1 … D26-3 (index DX) and the full-size diagrams F1–F21 (index FX); Chrome or Edge; <b>S</b> notes, <b>T</b> timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5006-presentation.pdf</code> — the same deck for submission.'],
    ['Facts as of', '25 Oct 2026. Dev health <code>UP</code> (23 Oct); a paid START and a natural expiry refund shown on dev on 23 Oct (PR #664, merged 24 Oct, bef39b72); a second buyer’s paid JOIN shown on dev on 24 Oct (system demo backup recording, #657); #590, #517 and #223 closed by Yorik on 25 Oct; #224 open.'],
  ],
  after: `${box('', 'Markers', `${md('{{FILL:}}')} value that does not exist yet; never present a guess · ${md('{{VERIFY:}}')} claim to check against the repository, report or run logs · ${md('{{CONFIRM:}}')} needs team agreement. All must be gone by the 25 Oct rehearsal.`)}`,
});

// Segment chapters render before openItems(), so its counts include their markers.
const body = segsHtml + timeline;
module.exports = coverHtml + '\n' + runSheetHtml + body + openItems() + appendix;
